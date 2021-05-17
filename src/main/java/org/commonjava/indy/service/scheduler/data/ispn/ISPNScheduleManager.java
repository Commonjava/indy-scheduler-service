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
import org.commonjava.indy.service.scheduler.data.ScheduleManager;
import org.commonjava.indy.service.scheduler.data.StandaloneScheduleManager;
import org.commonjava.indy.service.scheduler.data.ispn.local.CacheHandle;
import org.commonjava.indy.service.scheduler.event.ScheduleTriggerEvent;
import org.commonjava.indy.service.scheduler.event.kafka.KafkaEventUtils;
import org.commonjava.indy.service.scheduler.exception.SchedulerException;
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

@SuppressWarnings( "RedundantThrows" )
@ApplicationScoped
@StandaloneScheduleManager
@Startup
@Listener( clustered = true )
public class ISPNScheduleManager
        implements ScheduleManager
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    public static final String PAYLOAD = "payload";

    public static final String ANY = "__ANY__";

    public static final String CONTENT_JOB_TYPE = "CONTENT";

    public static final String JOB_TYPE = "JOB_TYPE";

    public static final String SCHEDULE_TIME = "SCHEDULE_TIME";

    public static final String SCHEDULE_UUID = "SCHEDULE_UUID";

    @Inject
    ObjectMapper objectMapper;


    @Inject
    @ScheduleCache
    CacheHandle<ScheduleKey, ScheduleValue> scheduleCache;

    @Inject
    KafkaEventUtils kafkaEvent;

    @PostConstruct
    public void init()
    {
        if ( !isEnabled() )
        {
            logger.info( "Scheduler disabled. Skipping initialization" );
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
            logger.debug( "Scheduler disabled." );
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
    public void schedule( final String key, final String jobType, final String jobName,
                          final Map<String, Object> payload, final int startSeconds )
            throws SchedulerException
    {
        if ( !isEnabled() )
        {
            logger.debug( "Scheduler disabled." );
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

        scheduleCache.execute( cache -> cache.put( cacheKey, new ScheduleValue( cacheKey, dataMap ), startSeconds,
                                                   TimeUnit.SECONDS ) );
        logger.debug( "Scheduled for the key {} with timeout: {} seconds", cacheKey, startSeconds );
    }

    @Override
    public Optional<ScheduleKey> cancel( final String key, final String jobType, final String jobName )
    {
        if ( !isEnabled() )
        {
            logger.debug( "Scheduler disabled." );
            return empty();
        }
        ScheduleKey scheduleKey = new ScheduleKey( key, jobType, jobName );
        return removeCache( scheduleKey ) == null ? empty() : of( scheduleKey );
    }

    public Set<ScheduleKey> cancelAllBefore( final CacheKeyMatcher<ScheduleKey> matcher, final long timeout )
            throws SchedulerException
    {
        if ( !isEnabled() )
        {
            logger.debug( "Scheduler disabled." );
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

    public Set<ScheduleKey> cancelAll( final CacheKeyMatcher<ScheduleKey> matcher )
            throws SchedulerException
    {
        return cancel( matcher, ANY );
    }

    public Set<ScheduleKey> cancel( final CacheKeyMatcher<ScheduleKey> matcher, final String name )
            throws SchedulerException
    {
        if ( !isEnabled() )
        {
            logger.debug( "Scheduler disabled." );
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

    @Override
    public Expiration findSingleExpiration( final String storeKey, final String jobType )
    {
        return findSingleExpiration( new StoreKeyMatcher( storeKey, jobType ) );
    }

    @Override
    public ExpirationSet findMatchingExpirations( final String jobType )
    {
        return findMatchingExpirations( cacheHandle -> cacheHandle.execute( BasicCache::keySet )
                                                                  .stream()
                                                                  .filter( key -> key.getType().equals( jobType ) )
                                                                  .collect( Collectors.toSet() ) );
    }

    private Expiration findSingleExpiration( final StoreKeyMatcher matcher )
    {
        if ( !isEnabled() )
        {
            logger.debug( "Scheduler disabled." );
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
            logger.debug( "Scheduler disabled." );
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

    public ScheduleKey findFirstMatchingTrigger( final CacheKeyMatcher<ScheduleKey> matcher )
    {
        if ( !isEnabled() )
        {
            logger.debug( "Scheduler disabled." );
            return null;
        }

        final Set<ScheduleKey> keys = matcher.matches( scheduleCache );
        if ( keys != null && !keys.isEmpty() )
        {
            return keys.iterator().next();
        }

        return null;
    }

    public static String groupName( final String storeKey, final String jobType )
    {
        return storeKey + groupNameSuffix( jobType );
    }

    public static String groupNameSuffix( final String jobType )
    {
        return "#" + jobType;
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
        //        return schedulerConfig.isCassandraEnabled();
        //TODO: need this isEnabled method?
        return true;
    }

    //    public void setSnapshotTimeouts( final String storeKey, final String path )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            logger.debug( "Scheduler disabled." );
    //            return;
    //        }
    //
    //        HostedRepository deploy = null;
    //        try
    //        {
    //            final ArtifactStore store = dataManager.getArtifactStore( storeKey );
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
    //            logger.error(
    //                    String.format( "Failed to retrieve deploy point for: %s. Reason: %s", storeKey, e.getMessage() ),
    //                    e );
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
    //            scheduleContentExpiration( storeKey, path, timeout );
    //        }
    //    }
    //
    //    public void rescheduleDisableTimeout( final String storeKey )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            logger.debug( "Scheduler disabled." );
    //            return;
    //        }
    //
    //        ArtifactStore store = null;
    //        try
    //        {
    //            store = dataManager.getArtifactStore( storeKey );
    //        }
    //        catch ( final IndyDataException e )
    //        {
    //            logger.error( String.format( "Failed to retrieve store for: %s. Reason: %s", storeKey, e.getMessage() ),
    //                          e );
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
    //        // No need to cancel as the job will be cancelled immediately after the re-enable in StoreEnablementManager
    //        //        final Set<ScheduleKey> canceled =
    //        //                cancelAllBefore( new StoreKeyMatcher( store.getKey(), DISABLE_TIMEOUT ),
    //        //                                 timeout );
    //        //        logger.info( "Cancel disable timeout for stores:{}", canceled );
    //
    //        if ( timeout > TIMEOUT_USE_DEFAULT && store.isDisabled() )
    //        {
    //            final String sk = store.getKey();
    //            logger.debug( "Set/Reschedule disable timeout for store:{}", sk );
    //            scheduleForStore( sk, DISABLE_TIMEOUT, DISABLE_TIMEOUT, sk, timeout );
    //        }
    //        // Will never consider the TIMEOUT_NEVER_DISABLE case here, will consider this in the calling object(StoreEnablementManager)
    //    }

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

    //    public void rescheduleSnapshotTimeouts( final HostedRepository deploy )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            logger.debug( "Scheduler disabled." );
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
    //            final Set<ScheduleKey> canceled =
    //                    cancelAllBefore( new StoreKeyMatcher( deploy.getKey(), CONTENT_JOB_TYPE ), timeout );
    //
    //            for ( final ScheduleKey key : canceled )
    //            {
    //                final String path = key.getName();
    //                final String sk = storeKeyFrom( key.getGroupName() );
    //
    //                scheduleContentExpiration( sk, path, timeout );
    //            }
    //        }
    //    }
    //
    //    public void rescheduleProxyTimeouts( final RemoteRepository repo )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            logger.debug( "Scheduler disabled." );
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
    //            final Set<ScheduleKey> canceled =
    //                    cancelAllBefore( new StoreKeyMatcher( repo.getKey(), CONTENT_JOB_TYPE ), timeout );
    //            for ( final ScheduleKey key : canceled )
    //            {
    //                final String path = key.getName();
    //                final String sk = storeKeyFrom( key.getGroupName() );
    //
    //                scheduleContentExpiration( sk, path, timeout );
    //            }
    //        }
    //    }
    //
    //    public void setProxyTimeouts( final String storeKey, final String path )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            logger.debug( "Scheduler disabled." );
    //            return;
    //        }
    //
    //        RemoteRepository repo = null;
    //        try
    //        {
    //            repo = (RemoteRepository) dataManager.getArtifactStore( storeKey );
    //        }
    //        catch ( final IndyDataException e )
    //        {
    //            logger.error( String.format( "Failed to retrieve store for: %s. Reason: %s", storeKey, e.getMessage() ),
    //                          e );
    //        }
    //
    //        if ( repo == null )
    //        {
    //            return;
    //        }
    //
    //        int timeout = config.getPassthroughTimeoutSeconds();
    //        final ConcreteResource resource = new ConcreteResource( LocationUtils.toLocation( repo ), path );
    //        final SpecialPathInfo info = specialPathManager.getSpecialPathInfo( resource, storeKey.getPackageType() );
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
    //            removeCache( new ScheduleKey( storeKey, CONTENT_JOB_TYPE, path ) );
    //            scheduleContentExpiration( storeKey, path, timeout );
    //        }
    //    }

    //    @Deprecated
    //    public void scheduleContentExpiration( final String storeKey, final String path, final int timeoutSeconds )
    //            throws SchedulerException
    //    {
    //        if ( !isEnabled() )
    //        {
    //            logger.debug( "Scheduler disabled." );
    //            return;
    //        }
    //
    //        logger.info( "Scheduling timeout for: {} in: {} in: {} seconds (at: {}).", path, storeKey, timeoutSeconds,
    //                     new Date( System.currentTimeMillis() + ( timeoutSeconds * 1000L ) ) );
    //
    //        schedule( storeKey, CONTENT_JOB_TYPE, path, new ContentExpiration( storeKey, path ), timeoutSeconds );
    //    }
}
