/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/indy)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.service.scheduler.data.ispn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import org.commonjava.indy.service.scheduler.config.ScheduleConfiguration;
import org.commonjava.indy.service.scheduler.data.ScheduleManager;
import org.commonjava.indy.service.scheduler.data.StandaloneScheduleManager;
import org.commonjava.indy.service.scheduler.data.ispn.local.CacheHandle;
import org.commonjava.indy.service.scheduler.event.ScheduleTriggerEvent;
import org.commonjava.indy.service.scheduler.event.kafka.KafkaEventUtils;
import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;
import org.commonjava.indy.service.scheduler.model.Expiration;
import org.commonjava.indy.service.scheduler.model.ExpirationSet;
import org.commonjava.indy.service.scheduler.model.ScheduleKey;
import org.commonjava.indy.service.scheduler.model.ScheduleValue;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.metadata.Metadata;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryExpiredEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * This ISPNScheduleManager is only used for local dev mode. It uses Infinispan expiration mechanism to implement the scheduler
 * function.
 */
@SuppressWarnings( "RedundantThrows" )
@ApplicationScoped
@StandaloneScheduleManager
@Startup
@Listener( clustered = true )
public class ISPNScheduleManager
        implements ScheduleManager
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private static final String JOB_TYPE = "JOB_TYPE";

    private static final String PAYLOAD = "payload";

    private static final String ANY = "__ANY__";

    private static final String SCHEDULE_TIME = "SCHEDULE_TIME";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @ScheduleCache
    CacheHandle<ScheduleKey, ScheduleValue> scheduleCache;

    @Inject
    KafkaEventUtils kafkaEvent;

    @Inject
    ScheduleConfiguration scheduleConfig;

    @PostConstruct
    public void init()
    {
        if ( !isEnabled() )
        {
            logger.info( "Infinispan scheduler disabled. Skipping initialization" );
            return;
        }

        // register this producer as schedule cache listener
        registerCacheListener( scheduleCache );
    }

    @PreDestroy
    public void stop()
    {
        if ( !isEnabled() )
        {
            return;
        }

        scheduleCache.stop();
    }

    private <K, V> void registerCacheListener( BasicCacheHandle<K, V> cache )
    {
        cache.execute( c -> {
            if ( c instanceof Cache )
            {
                ( (Cache) c ).addListener( ISPNScheduleManager.this );
            }
            if ( c instanceof RemoteCache )
            {
                ( (RemoteCache) c ).addClientListener( ISPNScheduleManager.this );
            }
            return null;
        } );
    }

    @Override
    public Optional<SchedulerInfo> get( final String key, final String jobName, final String jobType )
    {
        ScheduleValue v = scheduleCache.get( new ScheduleKey( key, jobType, jobName ) );
        if ( v == null )
        {
            return Optional.empty();
        }
        Map<String, Object> payload;
        try
        {
            final String payloadStr = (String) v.getDataPayload().get( PAYLOAD );
            payload = objectMapper.readValue( payloadStr, Map.class );
        }
        catch ( JsonProcessingException | ClassCastException e )
        {
            throw new RuntimeException(
                    String.format( "Cannot get payload for scheduler info. Key: %s, JobName: %s", key, jobName ), e );
        }
        return Optional.of( new SchedulerInfo().setKey( key )
                                               .setJobType( jobType )
                                               .setJobName( jobName )
                                               .setPayload( payload ) );
    }

    @Override
    public void schedule( final String key, final String jobType, final String jobName,
                          final Map<String, Object> payload, final int timeoutInSecs )
            throws SchedulerException
    {
        if ( !isEnabled() )
        {
            return;
        }

        final Map<String, Object> dataMap = new HashMap<>( 3 );
        dataMap.put( JOB_TYPE, jobType );
        try
        {
            dataMap.put( PAYLOAD, objectMapper.writeValueAsString( payload ) );
        }
        catch ( final JsonProcessingException e )
        {
            throw new SchedulerException( "Failed to serialize JSON payload: " + payload, e );
        }

        dataMap.put( SCHEDULE_TIME, System.currentTimeMillis() );

        final ScheduleKey cacheKey = new ScheduleKey( key, jobType, jobName );

        final ScheduleValue val = new ScheduleValue( cacheKey, dataMap );
        val.setTimeoutSeconds( timeoutInSecs );
        scheduleCache.execute( cache -> cache.put( cacheKey, val, timeoutInSecs, TimeUnit.SECONDS ) );
        logger.debug( "Scheduled for the key {} with timeout: {} seconds", cacheKey, timeoutInSecs );
    }

    @Override
    public Optional<ScheduleKey> cancel( final String key, final String jobType, final String jobName )
    {
        if ( !isEnabled() )
        {
            return empty();
        }
        final ScheduleKey scheduleKey = new ScheduleKey( key, jobType, jobName );
        return removeCache( scheduleKey ) == null ? empty() : of( scheduleKey );
    }

    @Override
    public Expiration findSingleExpiration( final String key, final String jobType )
    {
        return findSingleExpiration( new KeyMatcher( key, jobType ) );
    }

    @Override
    public ExpirationSet findMatchingExpirations( final String jobType )
    {
        return findMatchingExpirations( cacheHandle -> cacheHandle.execute( BasicCache::keySet )
                                                                  .stream()
                                                                  .filter( key -> key.getType().equals( jobType ) )
                                                                  .collect( Collectors.toSet() ) );
    }

    private Expiration findSingleExpiration( final KeyMatcher matcher )
    {
        if ( !isEnabled() )
        {
            return null;
        }

        final Set<ScheduleKey> keys = matcher.matches( scheduleCache );
        if ( keys != null && !keys.isEmpty() )
        {
            ScheduleKey triggerKey = keys.iterator().next();
            return toExpiration( triggerKey );
        }

        return null;
    }

    public ExpirationSet findMatchingExpirations( final CacheKeyMatcher<ScheduleKey> matcher )
    {
        if ( !isEnabled() )
        {
            return null;
        }

        final Set<ScheduleKey> keys = matcher.matches( scheduleCache );
        Set<Expiration> expirations = new HashSet<>( keys.size() );
        if ( !keys.isEmpty() )
        {
            for ( ScheduleKey key : keys )
            {
                expirations.add( toExpiration( key ) );
            }
        }

        return new ExpirationSet( expirations );
    }

    private Expiration toExpiration( final ScheduleKey cacheKey )
    {
        return new Expiration( cacheKey.getGroupName(), cacheKey.getName(), getNextExpireTime( cacheKey ) );
    }

    private Date getNextExpireTime( final ScheduleKey cacheKey )
    {

        return scheduleCache.executeCache( cache -> {
            final CacheEntry<ScheduleKey, ScheduleValue> entry = cache.getAdvancedCache().getCacheEntry( cacheKey );
            if ( entry != null )
            {
                final Metadata metadata = entry.getMetadata();
                long expire = metadata.lifespan();
                final long startTimeInMillis =
                        (Long) scheduleCache.get( cacheKey ).getDataPayload().get( SCHEDULE_TIME );
                return calculateNextExpireTime( expire, startTimeInMillis );
            }
            return null;
        } );

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

    @Deprecated
    public ScheduleKey findFirstMatchingTrigger( final CacheKeyMatcher<ScheduleKey> matcher )
    {
        if ( !isEnabled() )
        {
            return null;
        }

        final Set<ScheduleKey> keys = matcher.matches( scheduleCache );
        if ( keys != null && !keys.isEmpty() )
        {
            return keys.iterator().next();
        }

        return null;
    }

    private ScheduleValue removeCache( final ScheduleKey cacheKey )
    {
        return scheduleCache.remove( cacheKey );
    }

    @CacheEntryCreated
    public void scheduled( final CacheEntryCreatedEvent<ScheduleKey, ScheduleValue> e )
    {
        if ( e == null )
        {
            logger.error( "[FATAL]The infinispan cache created event for indy schedule manager is null.",
                          new NullPointerException( "CacheEntryCreatedEvent is null" ) );
            return;
        }

        if ( !e.isPre() )
        {
            final ScheduleKey expiredKey = e.getKey();
            final ScheduleValue value = e.getValue();
            if ( value == null )
            {
                logger.warn( "The cache value for ISPN creation event of schedule expiration is null, key is {} .",
                             expiredKey );
                return;
            }
            final Map<String, Object> expiredContent = value.getDataPayload();
            if ( expiredKey != null && expiredContent != null )
            {
                logger.debug( "Expiration Created: {}", expiredKey );
                //TODO: Implement later using kafka
                //                final String type = (String) expiredContent.get( ISPNScheduleManager.JOB_TYPE );
                //                final String data = (String) expiredContent.get( ISPNScheduleManager.PAYLOAD );
                //                fireEvent( eventDispatcher, new SchedulerScheduleEvent( type, data ) );
            }
        }
    }

    @CacheEntryExpired
    public void expired( CacheEntryExpiredEvent<ScheduleKey, ScheduleValue> e )
    {
        if ( e == null )
        {
            logger.error( "[FATAL]The infinispan cache expired event for indy schedule manager is null.",
                          new NullPointerException( "CacheEntryExpiredEvent is null" ) );
            return;
        }

        if ( !e.isPre() )
        {
            final ScheduleKey expiredKey = e.getKey();
            final ScheduleValue value = e.getValue();
            if ( value == null )
            {
                logger.warn( "The cache value for ISPN expired event of schedule expiration is null, key is {} .",
                             expiredKey );
                return;
            }
            final Map<String, Object> expiredContent = value.getDataPayload();
            if ( expiredKey != null && expiredContent != null )
            {
                logger.debug( "EXPIRED: {}", expiredKey );
                final String type = (String) expiredContent.get( ISPNScheduleManager.JOB_TYPE );
                final String data = (String) expiredContent.get( ISPNScheduleManager.PAYLOAD );
                if ( logger.isInfoEnabled() && ISPNScheduleManager.CONTENT_JOB_TYPE.equals( type ) )
                {
                    Map<String, Object> expiration;
                    try
                    {
                        expiration = objectMapper.readValue( data, Map.class );
                        logger.info( "Expiring: {} at: {}.", expiration, new Date( System.currentTimeMillis() ) );
                    }
                    catch ( final IOException ioe )
                    {
                        // ignore
                    }
                }
                // Implement using kafka
                kafkaEvent.fireEvent( new ScheduleTriggerEvent( type, expiredKey.getName(), data ) );
            }
        }
    }

    @CacheEntryRemoved
    public void cancelled( CacheEntryRemovedEvent<ScheduleKey, ScheduleValue> e )
    {
        if ( e == null )
        {
            logger.error( "[FATAL]The infinispan cache removed event for indy schedule manager is null.",
                          new NullPointerException( "CacheEntryRemovedEvent is null" ) );
            return;
        }
        logger.trace( "Cache removed to cancel scheduling, Key is {}, Value is {}", e.getKey(), e.getValue() );
    }

    // This method is only used to check clustered schedule expire cache nodes topology changing
    @ViewChanged
    public void checkClusterChange( ViewChangedEvent event )
    {
        logger.debug( "Schedule cache cluster members changed, old members: {}; new members: {}", event.getOldMembers(),
                      event.getNewMembers() );
    }

    private Boolean isEnabled()
    {
        if ( scheduleConfig.isClusterEnabled() )
        {
            logger.debug( "Infinispan Scheduler disabled." );
            return false;
        }
        return true;
    }

    @Deprecated
    public Set<ScheduleKey> cancel( final CacheKeyMatcher<ScheduleKey> matcher, final String name )
            throws SchedulerException
    {
        if ( !isEnabled() )
        {
            return Collections.emptySet();
        }

        Set<ScheduleKey> canceled = new HashSet<>();
        final Set<ScheduleKey> keys = matcher.matches( scheduleCache );
        if ( keys != null && !keys.isEmpty() )
        {
            Set<ScheduleKey> unscheduled = null;
            if ( ANY.equals( name ) )
            {
                for ( final ScheduleKey k : keys )
                {
                    removeCache( k );
                }
                unscheduled = keys;
            }
            else
            {
                for ( final ScheduleKey k : keys )
                {
                    if ( k.getName().equals( name ) )
                    {
                        removeCache( k );
                        unscheduled = Collections.singleton( k );
                        break;
                    }
                }
            }

            if ( unscheduled != null )
            {
                canceled = unscheduled;
            }
        }

        return canceled;
    }

    @Deprecated
    public Set<ScheduleKey> cancelAllBefore( final CacheKeyMatcher<ScheduleKey> matcher, final long timeout )
            throws SchedulerException
    {
        if ( !isEnabled() )
        {
            return Collections.emptySet();
        }

        final Set<ScheduleKey> canceled = new HashSet<>();

        final Date to = new Date( System.currentTimeMillis() + ( timeout * 1000 ) );
        matcher.matches( scheduleCache ).forEach( key -> {
            final Date nextFire = getNextExpireTime( key );
            if ( nextFire == null || !nextFire.after( to ) )
            {
                // former impl uses quartz and here use the unscheduleJob method, but ISPN does not have similar
                // op, so directly did a remove here.
                removeCache( key );
                logger.debug( "Removed cache job for key: {}, before {}", key, to );
                canceled.add( key );
            }
        } );

        return canceled;
    }

    @Deprecated
    public Set<ScheduleKey> cancelAll( final CacheKeyMatcher<ScheduleKey> matcher )
            throws SchedulerException
    {
        return cancel( matcher, ANY );
    }

}
