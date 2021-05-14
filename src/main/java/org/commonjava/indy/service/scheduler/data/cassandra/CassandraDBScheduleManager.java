/**
 * Copyright (C) 2011-2020 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.service.scheduler.data.cassandra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import org.commonjava.indy.service.scheduler.config.CassandraConfiguration;
import org.commonjava.indy.service.scheduler.data.ClusterScheduleManager;
import org.commonjava.indy.service.scheduler.data.ScheduleManager;
import org.commonjava.indy.service.scheduler.data.ScheduleManagerUtils;
import org.commonjava.indy.service.scheduler.event.ScheduleEvent;
import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.model.Expiration;
import org.commonjava.indy.service.scheduler.model.ExpirationSet;
import org.commonjava.indy.service.scheduler.model.ScheduleKey;
import org.commonjava.indy.service.scheduler.model.cassandra.DtxSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A CassandraDBScheduleManager is used to do the schedule time out jobs to do some time-related jobs, like
 * removing useless artifacts. It used Cassandra to store the schedule info and regularly to check the status to implement this
 * type of function.
 */
@SuppressWarnings( "RedundantThrows" )
@ApplicationScoped
@Startup
@ClusterScheduleManager
public class CassandraDBScheduleManager
        implements ScheduleManager
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    public static final String ANY = "__ANY__";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CassandraConfiguration cassandraConfig;

    @Inject
    ScheduleDB scheduleDB;

//    @Inject
//    @Any
//    Instance<ContentAdvisor> contentAdvisor;

    @Override
    public void schedule( final String key, final String jobType, final String jobName, final Map<String, Object> payload,
                          final int startSeconds )
            throws SchedulerException
    {
        if ( !isEnabled() )
        {
            return;
        }

        String payloadStr;
        try
        {
            payloadStr = objectMapper.writeValueAsString( payload );
        }
        catch ( final JsonProcessingException e )
        {
            throw new SchedulerException( "Failed to serialize JSON payload: " + payload, e );
        }

        scheduleDB.createSchedule( key, jobType, jobName, payloadStr, (long) startSeconds );
        logger.debug( "Scheduled for the key {} with timeout: {} seconds", key, startSeconds );
    }

    @Override
    public Optional<ScheduleKey> cancel( final String key, final String jobType, final String jobName )
    {
        if ( !isEnabled() )
        {
            return Optional.empty();
        }
        //TODO: Not implement!
        return Optional.empty();
    }


    @Deprecated
    public Set<DtxSchedule> rescheduleAllBefore( final Collection<DtxSchedule> schedules, final long timeout )
    {

        if ( !isEnabled() )
        {
            return Collections.emptySet();
        }

        final Set<DtxSchedule> rescheduled = new HashSet<>();

        final Date to = new Date( System.currentTimeMillis() + ( timeout * 1000 ) );
        schedules.forEach( schedule -> {
            final Date nextFire = getNextExpireTime( schedule );
            if ( nextFire == null || !nextFire.after( to ) )
            {
                rescheduled.add( schedule );
            }
        } );

        return rescheduled;

    }

    @Override
    public Expiration findSingleExpiration( final String key, final String jobType )
    {
        if ( !isEnabled() )
        {
            return null;
        }

        final Collection<DtxSchedule> schedules = scheduleDB.querySchedules( key, jobType, Boolean.FALSE );

        if ( schedules != null && !schedules.isEmpty() )
        {
            DtxSchedule schedule = schedules.iterator().next();
            return toExpiration( schedule );

        }

        return null;
    }

    @Override
    public ExpirationSet findMatchingExpirations( String jobType )
    {
        if ( !isEnabled() )
        {
            return null;
        }

        final Collection<DtxSchedule> schedules = scheduleDB.querySchedulesByJobType( jobType );
        Set<Expiration> expirations = new HashSet<>( schedules.size() );
        if ( !schedules.isEmpty() )
        {
            for ( DtxSchedule schedule : schedules )
            {
                expirations.add( toExpiration( schedule ) );
            }
        }

        return new ExpirationSet( expirations );
    }

    public ExpirationSet findMatchingExpirations( final String key, final String jobType )
    {
        if ( !cassandraConfig.isEnabled() )
        {
            logger.debug( "Cassandra scheduler disabled." );
            return null;
        }

        final Collection<DtxSchedule> schedules = scheduleDB.querySchedules( key, jobType, Boolean.FALSE );
        Set<Expiration> expirations = new HashSet<>( schedules.size() );
        if ( !schedules.isEmpty() )
        {
            for ( DtxSchedule schedule : schedules )
            {
                expirations.add( toExpiration( schedule ) );
            }
        }

        return new ExpirationSet( expirations );
    }

    private Expiration toExpiration( final DtxSchedule schedule )
    {
        return new Expiration( ScheduleManagerUtils.groupName( schedule.getStoreKey(), schedule.getJobType() ),
                               schedule.getJobName(), getNextExpireTime( schedule ) );
    }

    private Date getNextExpireTime( final DtxSchedule dtxSchedule )
    {

        if ( !dtxSchedule.getExpired() )
        {
            Long lifespan = dtxSchedule.getLifespan();
            final long startTimeInMillis = dtxSchedule.getScheduleTime().getTime();
            return calculateNextExpireTime( lifespan, startTimeInMillis );
        }

        return null;
    }

    static Date calculateNextExpireTime( final long expire, final long start )
    {
        if ( expire > 1 )
        {
            final long duration = System.currentTimeMillis() - start;
            if ( duration < expire )
            {
                final long nextTimeInMillis = expire - duration + System.currentTimeMillis();
                final LocalDateTime time =
                        Instant.ofEpochMilli( nextTimeInMillis ).atZone( ZoneId.systemDefault() ).toLocalDateTime();
                return Date.from( time.atZone( ZoneId.systemDefault() ).toInstant() );
            }
        }
        return null;
    }

    private boolean isEnabled()
    {
        if ( !cassandraConfig.isEnabled() )
        {
            logger.debug( "Cassandra scheduler disabled." );
            return false;
        }
        return true;
    }

    //    @Override
    //    public String getId()
    //    {
    //        return "Indy ScheduleDB";
    //    }
    //
    //    @Override
    //    public int getShutdownPriority()
    //    {
    //        return 95;
    //    }

    //    public void rescheduleSnapshotTimeouts( final HostedRepository deploy )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            return;
    //        }
    //
    //        int timeout = -1;
    //        if ( deploy.isAllowSnapshots() && deploy.getSnapshotTimeoutSeconds() > 0 )
    //        {
    //            timeout = deploy.getSnapshotTimeoutSeconds();
    //        }
    //
    //        if ( timeout > 0 )
    //        {
    //
    //            final Collection<DtxSchedule> schedules =
    //                    scheduleDB.querySchedules( deploy.getKey().toString(), JobType.CONTENT.getJobType(),
    //                                               Boolean.FALSE );
    //
    //            final Set<DtxSchedule> rescheduled = rescheduleAllBefore( schedules, timeout );
    //
    //            for ( DtxSchedule schedule : rescheduled )
    //            {
    //                scheduleContentExpiration( StoreKey.fromString( schedule.getStoreKey() ), schedule.getJobName(),
    //                                           timeout );
    //            }
    //        }
    //    }
    //
    //    public void rescheduleProxyTimeouts( final RemoteRepository repo )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            return;
    //        }
    //
    //        int timeout = -1;
    //        if ( !repo.isPassthrough() && repo.getCacheTimeoutSeconds() > 0 )
    //        {
    //            timeout = repo.getCacheTimeoutSeconds();
    //        }
    //        else if ( repo.isPassthrough() )
    //        {
    //            timeout = config.getPassthroughTimeoutSeconds();
    //        }
    //
    //        if ( timeout > 0 )
    //        {
    //
    //            final Collection<DtxSchedule> schedules =
    //                    scheduleDB.querySchedules( repo.getKey().toString(), JobType.CONTENT.getJobType(), Boolean.FALSE );
    //
    //            final Set<DtxSchedule> rescheduled = rescheduleAllBefore( schedules, timeout );
    //
    //            for ( DtxSchedule schedule : rescheduled )
    //            {
    //                scheduleContentExpiration( StoreKey.fromString( schedule.getStoreKey() ), schedule.getJobName(),
    //                                           timeout );
    //            }
    //
    //        }
    //    }
    //
    //    public void setProxyTimeouts( final StoreKey key, final String path )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            return;
    //        }
    //
    //        RemoteRepository repo = null;
    //        try
    //        {
    //            repo = (RemoteRepository) dataManager.getArtifactStore( key );
    //        }
    //        catch ( final IndyDataException e )
    //        {
    //            logger.error( String.format( "Failed to retrieve store for: %s. Reason: %s", key, e.getMessage() ), e );
    //        }
    //
    //        if ( repo == null )
    //        {
    //            return;
    //        }
    //
    //        int timeout = config.getPassthroughTimeoutSeconds();
    //        final ConcreteResource resource = new ConcreteResource( LocationUtils.toLocation( repo ), path );
    //        final SpecialPathInfo info = specialPathManager.getSpecialPathInfo( resource, key.getPackageType() );
    //        if ( !repo.isPassthrough() )
    //        {
    //            if ( ( info != null && info.isMetadata() ) && repo.getMetadataTimeoutSeconds() >= 0 )
    //            {
    //                if ( repo.getMetadataTimeoutSeconds() == 0 )
    //                {
    //                    logger.debug( "Using default metadata timeout for: {}", resource );
    //                    timeout = config.getRemoteMetadataTimeoutSeconds();
    //                }
    //                else
    //                {
    //                    logger.debug( "Using metadata timeout for: {}", resource );
    //                    timeout = repo.getMetadataTimeoutSeconds();
    //                }
    //            }
    //            else
    //            {
    //                if ( info == null )
    //                {
    //                    logger.debug( "No special path info for: {}", resource );
    //                }
    //                else
    //                {
    //                    logger.debug( "{} is a special path, but not metadata.", resource );
    //                }
    //
    //                timeout = repo.getCacheTimeoutSeconds();
    //            }
    //        }
    //
    //        if ( timeout > 0 )
    //        {
    //            //            logger.info( "[PROXY TIMEOUT SET] {}/{}; {}", repo.getKey(), path, new Date( System.currentTimeMillis()
    //            //                + timeout ) );
    //
    //            scheduleContentExpiration( key, path, timeout );
    //        }
    //    }

    //    public void setSnapshotTimeouts( final String key, final String path )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            return;
    //        }
    //
    //        HostedRepository deploy = null;
    //        try
    //        {
    //            final ArtifactStore store = dataManager.getArtifactStore( key );
    //            if ( store == null )
    //            {
    //                return;
    //            }
    //
    //            if ( store instanceof HostedRepository )
    //            {
    //                deploy = (HostedRepository) store;
    //            }
    //            else if ( store instanceof Group )
    //            {
    //                final Group group = (Group) store;
    //                deploy = findDeployPoint( group );
    //            }
    //        }
    //        catch ( final IndyDataException e )
    //        {
    //            logger.error( String.format( "Failed to retrieve deploy point for: %s. Reason: %s", key, e.getMessage() ),
    //                          e );
    //        }
    //
    //        if ( deploy == null )
    //        {
    //            return;
    //        }
    //
    //        final ContentAdvisor advisor = StreamSupport.stream(
    //                Spliterators.spliteratorUnknownSize( contentAdvisor.iterator(), Spliterator.ORDERED ), false )
    //                                                    .filter( Objects::nonNull )
    //                                                    .findFirst()
    //                                                    .orElse( null );
    //        final ContentQuality quality = advisor == null ? null : advisor.getContentQuality( path );
    //        if ( quality == null )
    //        {
    //            return;
    //        }
    //
    //        if ( ContentQuality.SNAPSHOT == quality && deploy.getSnapshotTimeoutSeconds() > 0 )
    //        {
    //            final int timeout = deploy.getSnapshotTimeoutSeconds();
    //
    //            //            //            logger.info( "[SNAPSHOT TIMEOUT SET] {}/{}; {}", deploy.getKey(), path, new Date( timeout ) );
    //            //            cancel( new StoreKeyMatcher( key, CONTENT_JOB_TYPE ), path );
    //
    //            scheduleContentExpiration( key, path, timeout );
    //        }
    //    }
    //
    //    public void rescheduleDisableTimeout( final StoreKey key )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            return;
    //        }
    //
    //        ArtifactStore store = null;
    //        try
    //        {
    //            store = dataManager.getArtifactStore( key );
    //        }
    //        catch ( final IndyDataException e )
    //        {
    //            logger.error( String.format( "Failed to retrieve store for: %s. Reason: %s", key, e.getMessage() ), e );
    //        }
    //
    //        if ( store == null )
    //        {
    //            return;
    //        }
    //
    //        int timeout = store.getDisableTimeout();
    //        if ( timeout == TIMEOUT_USE_DEFAULT )
    //        {
    //            // case TIMEOUT_USE_DEFAULT: will use default timeout configuration
    //            timeout = config.getStoreDisableTimeoutSeconds();
    //        }
    //
    //        if ( timeout > TIMEOUT_USE_DEFAULT && store.isDisabled() )
    //        {
    //            final StoreKey sk = store.getKey();
    //            logger.debug( "Set/Reschedule disable timeout for store:{}", sk );
    //            scheduleForStore( sk, DISABLE_TIMEOUT, sk.toString() + "#" + DISABLE_TIMEOUT, sk, timeout );
    //        }
    //    }
    //
    //    private HostedRepository findDeployPoint( final Group group )
    //            throws IndyDataException
    //    {
    //        for ( final StoreKey key : group.getConstituents() )
    //        {
    //            if ( StoreType.hosted == key.getType() )
    //            {
    //                return (HostedRepository) dataManager.getArtifactStore( key );
    //            }
    //            else if ( StoreType.group == key.getType() )
    //            {
    //                final Group grp = (Group) dataManager.getArtifactStore( key );
    //                final HostedRepository dp = findDeployPoint( grp );
    //                if ( dp != null )
    //                {
    //                    return dp;
    //                }
    //            }
    //        }
    //
    //        return null;
    //    }

    //    @Deprecated
    //    public void scheduleContentExpiration( final String key, final String path, final int timeoutSeconds )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            return;
    //        }
    //
    //        logger.info( "Scheduling timeout for: {} in: {} in: {} seconds (at: {}).", path, key, timeoutSeconds,
    //                     new Date( System.currentTimeMillis() + ( timeoutSeconds * 1000 ) ) );
    //
    //        schedule( key, JobType.CONTENT.getJobType(), path, new ContentExpiration( key, path ), timeoutSeconds );
    //    }

}
