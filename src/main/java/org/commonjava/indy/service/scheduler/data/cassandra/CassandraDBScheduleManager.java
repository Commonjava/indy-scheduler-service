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
    public void schedule( final String key, final String jobType, final String jobName,
                          final Map<String, Object> payload, final int startSeconds )
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
        return scheduleDB.deleteSchedule( key, jobName ) ?
                Optional.of( new ScheduleKey( key, jobType, jobName ) ) :
                Optional.empty();
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

    private Expiration toExpiration( final DtxSchedule dtxSchedule )
    {
        return new Expiration( ScheduleManagerUtils.groupName( dtxSchedule.getStoreKey(), dtxSchedule.getJobType() ),
                               dtxSchedule.getJobName(), getNextExpireTime( dtxSchedule ) );
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

    @Deprecated
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
    
}
