package org.commonjava.indy.service.scheduler.data.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.BuiltStatement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import io.quarkus.runtime.Startup;
import org.commonjava.indy.service.scheduler.config.CassandraConfiguration;
import org.commonjava.indy.service.scheduler.config.ScheduleConfiguration;
import org.commonjava.indy.service.scheduler.event.ScheduleTriggerEvent;
import org.commonjava.indy.service.scheduler.event.kafka.KafkaEventUtils;
import org.commonjava.indy.service.scheduler.model.cassandra.DtxExpiration;
import org.commonjava.indy.service.scheduler.model.cassandra.DtxSchedule;
import org.infinispan.counter.api.StrongCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;

@ApplicationScoped
@Startup
public class ScheduleDB
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    CassandraClient client;

    @Inject
    CassandraConfiguration cassandraConfig;

    @Inject
    ScheduleConfiguration scheduleConfig;

    @Inject
    ISPNRemoteCounter remoteCounter;

    @Inject
    KafkaEventUtils kafkaDispatcher;

    private Session session;

    private Mapper<DtxSchedule> scheduleMapper;

    private Mapper<DtxExpiration> expirationMapper;

    private PreparedStatement preparedExpiredQuery;

    private PreparedStatement preparedSingleScheduleQuery;

    private PreparedStatement preparedExpiredUpdate;

    private PreparedStatement preparedScheduleByTypeQuery;

    private PreparedStatement preparedScheduleByStoreKeyQuery;

    private PreparedStatement preparedScheduleByStoreKeyAndTypeQuery;

    ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

    public ScheduleDB()
    {
    }

    public ScheduleDB( CassandraClient client, ISPNRemoteCounter cacheProducer )
    {
        this.client = client;
        this.remoteCounter = cacheProducer;
        init();
    }

    @PostConstruct
    public void init()
    {
        if ( scheduleConfig.isClusterEnabled() && cassandraConfig.isEnabled() )
        {
            logger.info( "Cluster enabled. Cassandra DB initialization started." );
            initCassandra();
            initSchedulingService();
        }
    }

    private void initCassandra()
    {
        final String keyspace = cassandraConfig.getScheduleKeyspace();

        session = client.getSession( keyspace );

        session.execute( SchemaUtils.getSchemaCreateKeyspace( keyspace, cassandraConfig.getReplicationFactor() ) );
        session.execute( ScheduleDBUtil.getSchemaCreateTableSchedule( keyspace ) );
        session.execute( ScheduleDBUtil.getSchemaCreateTypeIndex4Schedule( keyspace ) );
        session.execute( ScheduleDBUtil.getSchemaCreateTableExpiration( keyspace ) );

        MappingManager manager = new MappingManager( session );

        scheduleMapper = manager.mapper( DtxSchedule.class, keyspace );
        expirationMapper = manager.mapper( DtxExpiration.class, keyspace );

        preparedExpiredQuery = session.prepare(
                "SELECT scheduleuid, expirationtime, storekey, jobname FROM " + keyspace + "."
                        + ScheduleDBUtil.TABLE_EXPIRATION + " WHERE expirationpid = ?" );

        preparedSingleScheduleQuery = session.prepare(
                "SELECT storekey, jobtype, jobname, scheduletime, scheduleuid, payload, lifespan, expired FROM "
                        + keyspace + "." + ScheduleDBUtil.TABLE_SCHEDULE + " WHERE storekey = ? and  jobname = ?" );

        preparedExpiredUpdate = session.prepare( "UPDATE " + keyspace + "." + ScheduleDBUtil.TABLE_SCHEDULE
                                                         + " SET expired = true WHERE  storekey = ? and  jobname = ?" );

        preparedScheduleByTypeQuery = session.prepare(
                "SELECT storekey, jobtype, jobname, scheduletime, scheduleuid, payload, lifespan, expired FROM "
                        + keyspace + "." + ScheduleDBUtil.TABLE_SCHEDULE + " WHERE jobtype = ? " );

        preparedScheduleByStoreKeyAndTypeQuery = session.prepare(
                "SELECT storekey, jobtype, jobname, scheduletime, scheduleuid, payload, lifespan, expired FROM "
                        + keyspace + "." + ScheduleDBUtil.TABLE_SCHEDULE + " WHERE storekey = ? and jobtype = ?" );

        preparedScheduleByStoreKeyQuery = session.prepare(
                "SELECT storekey, jobtype, jobname, scheduletime, scheduleuid, payload, lifespan, expired FROM "
                        + keyspace + "." + ScheduleDBUtil.TABLE_SCHEDULE + " WHERE storekey = ? " );
    }

    private void initSchedulingService()
    {
        final StrongCounter remoteCounter = this.remoteCounter.getStrongCounter( "scheduleCounter" );
        final AtomicLong localCounter = new AtomicLong( 0 );
        if ( remoteCounter != null )
        {
            try
            {
                localCounter.set( remoteCounter.getValue().get() );
            }
            catch ( InterruptedException | ExecutionException e )
            {
                logger.warn( " Get the value of the counter error. ", e );
            }
        }

        service.scheduleAtFixedRate( () -> {
            if ( remoteCounter != null )
            {
                logger.trace( "Thread scheduled task for Cassandra DB scan started." );
                try
                {
                    long expect = localCounter.get();
                    long update = localCounter.incrementAndGet();
                    // The compare-and-swap of StrongCounter is successful if the return value is the same as the expected,
                    // otherwise we think that other nodes had executed the schedule checking during this period.
                    if ( remoteCounter.compareAndSwap( expect, update ).get() != expect )
                    {
                        logger.trace(
                                "Some other nodes are executing task of cassandra DB scan and scheduling. Skip in this node." );
                        localCounter.set( remoteCounter.getValue().get() );
                        return;
                    }
                }
                catch ( InterruptedException | ExecutionException e )
                {
                    logger.warn( "Checking the counter error. ", e );
                }
            }
            // When the hour shift, the service need to check the remaining jobs expired in the last minutes of the last hour.
            LocalDateTime localDateTime = LocalDateTime.now();
            if ( localDateTime.getMinute() <= cassandraConfig.getScheduleRatePeriod() / 60 )
            {
                LocalDateTime offsetDateTime = localDateTime.minusHours( cassandraConfig.getOffsetHours() );
                queryAndSetExpiredSchedule( Date.from( offsetDateTime.atZone( ZoneId.systemDefault() ).toInstant() ) );
            }
            queryAndSetExpiredSchedule( Date.from( localDateTime.atZone( ZoneId.systemDefault() ).toInstant() ) );
        }, 10, cassandraConfig.getScheduleRatePeriod(), TimeUnit.SECONDS );

    }

    public void createSchedule( String storeKey, String jobType, String jobName, String payload, Long timeout )
    {

        UUID scheduleUID = UUID.randomUUID();
        Date scheduleTime = new Date();

        DtxSchedule schedule =
                new DtxSchedule( storeKey, jobType, jobName, scheduleUID, scheduleTime, payload, timeout );
        scheduleMapper.save( schedule );

        Date expirationTime = calculateExpirationTime( scheduleTime, timeout );
        DtxExpiration expiration =
                new DtxExpiration( calculateExpirationPID( expirationTime ), scheduleUID, expirationTime, storeKey,
                                   jobName );
        expirationMapper.save( expiration );

    }

    private Long calculateExpirationPID( Date date )
    {
        return date.getTime() / cassandraConfig.getPartitionKeyRange();
    }

    private Date calculateExpirationTime( Date scheduleTime, Long timeout )
    {
        return new Date( scheduleTime.getTime() + 1000 * timeout );
    }

    public DtxSchedule querySchedule( String key, String jobName )
    {
        BoundStatement bound = preparedSingleScheduleQuery.bind( key, jobName );
        ResultSet resultSet = session.execute( bound );

        Row row = resultSet.one();

        return toDtxSchedule( row );
    }

    public boolean deleteSchedule( String key, String jobName )
    {
        final String keyspace = cassandraConfig.getScheduleKeyspace();
        BuiltStatement deleteStmt = QueryBuilder.delete()
                                                .from( keyspace + "." + ScheduleDBUtil.TABLE_SCHEDULE )
                                                .where( eq( "storekey", key ) )
                                                .and( eq( "jobname", jobName ) );
        try
        {
            session.execute( deleteStmt );
            return true;
        }
        catch ( Exception e )
        {
            return false;
        }
    }

    public Collection<DtxExpiration> queryExpirations( Date date )
    {

        Long pid = calculateExpirationPID( date );

        Collection<DtxExpiration> expirations = new ArrayList<>();

        BoundStatement bound = preparedExpiredQuery.bind( pid );
        ResultSet resultSet = session.execute( bound );
        resultSet.forEach( row -> {
            expirations.add( toDtxExpiration( row ) );
        } );

        return expirations;
    }

    public void queryAndSetExpiredSchedule( Date date )
    {
        Collection<DtxExpiration> expirations = queryExpirations( date );
        expirations.forEach( expiration -> {
            if ( expiration.getExpirationTime().before( new Date() ) )
            {
                DtxSchedule schedule = querySchedule( expiration.getStorekey(), expiration.getJobName() );

                if ( schedule != null && !schedule.getExpired() && schedule.getScheduleUID()
                                                                           .equals( expiration.getScheduleUID() ) )
                {
                    BoundStatement boundU = preparedExpiredUpdate.bind( schedule.getStoreKey(), schedule.getJobName() );
                    session.execute( boundU );

                    logger.debug( "Expired entry: {}", schedule );
                    kafkaDispatcher.fireEvent( new ScheduleTriggerEvent( schedule.getJobType(), schedule.getJobName(),
                                                                         schedule.getPayload() ) );
                }
            }
        } );
    }

    public Collection<DtxSchedule> querySchedulesByJobType( String jobType )
    {
        Collection<DtxSchedule> schedules = new ArrayList<>();
        BoundStatement bound = preparedScheduleByTypeQuery.bind( jobType );
        ResultSet resultSet = session.execute( bound );
        resultSet.forEach( row -> {
            schedules.add( toDtxSchedule( row ) );
        } );
        return schedules;
    }

    public Collection<DtxSchedule> querySchedulesByStoreKey( String storeKey )
    {
        Collection<DtxSchedule> schedules = new ArrayList<>();
        BoundStatement bound = preparedScheduleByStoreKeyQuery.bind( storeKey );
        ResultSet resultSet = session.execute( bound );
        resultSet.forEach( row -> {
            schedules.add( toDtxSchedule( row ) );
        } );
        return schedules;
    }

    public Collection<DtxSchedule> querySchedules( String storeKey, String jobType, Boolean expired )
    {
        Collection<DtxSchedule> schedules = new ArrayList<>();
        BoundStatement bound = preparedScheduleByStoreKeyAndTypeQuery.bind( storeKey, jobType );
        ResultSet resultSet = session.execute( bound );
        resultSet.forEach( row -> {
            DtxSchedule schedule = toDtxSchedule( row );
            if ( !expired && !schedule.getExpired() )
            {
                schedules.add( schedule );
            }
            else if ( expired && schedule.getExpired() )
            {
                schedules.add( schedule );
            }
        } );
        return schedules;
    }

    private DtxSchedule toDtxSchedule( Row row )
    {
        if ( row != null )
        {
            DtxSchedule schedule = new DtxSchedule();
            schedule.setStoreKey( row.getString( "storekey" ) );
            schedule.setJobType( row.getString( "jobtype" ) );
            schedule.setJobName( row.getString( "jobname" ) );
            schedule.setExpired( row.getBool( "expired" ) );
            schedule.setScheduleTime( row.getTimestamp( "scheduletime" ) );
            schedule.setLifespan( row.getLong( "lifespan" ) );
            schedule.setScheduleUID( row.getUUID( "scheduleuid" ) );
            schedule.setPayload( row.getString( "payload" ) );
            return schedule;
        }
        return null;
    }

    private DtxExpiration toDtxExpiration( Row row )
    {
        if ( row != null )
        {
            DtxExpiration expiration = new DtxExpiration();
            expiration.setExpirationTime( row.getTimestamp( "expirationtime" ) );
            expiration.setStorekey( row.getString( "storekey" ) );
            expiration.setJobName( row.getString( "jobname" ) );
            expiration.setScheduleUID( row.getUUID( "scheduleuid" ) );
            return expiration;
        }
        return null;
    }

}
