/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/service-parent)
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
package org.commonjava.indy.service.scheduler.data.ispn;

import org.apache.commons.io.FileUtils;
import org.commonjava.indy.service.scheduler.config.InfinispanConfiguration;
import org.commonjava.indy.service.scheduler.data.metrics.DefaultMetricsManager;
import org.infinispan.Cache;
import org.infinispan.commons.marshall.MarshallableTypeHints;
import org.infinispan.configuration.ConfigurationManager;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.counter.api.StrongCounter;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Default
public class LocalCacheProducer
        extends AbstractCacheProducer
{
    Logger logger = LoggerFactory.getLogger( getClass() );

    private static final String ISPN_XML = "infinispan.xml";

    //    private static final String ISPN_CLUSTER_XML = "infinispan-cluster.xml";

    private EmbeddedCacheManager cacheManager;

    @Inject
    InfinispanConfiguration ispnConfig;

    @Inject
    DefaultMetricsManager metricsManager;

    //    @Inject
    //    MetricsConfiguration metricsConfig;

    //    @Inject
    //    ScheduleConfiguration scheduleConfig;

    //    private EmbeddedCacheManager clusterCacheManager;

    private final Map<String, CacheHandle> caches = new ConcurrentHashMap<>(); // hold embedded and remote caches

    private final Map<String, StrongCounter> counters = new ConcurrentHashMap<>();

    protected LocalCacheProducer()
    {
    }

    public LocalCacheProducer( EmbeddedCacheManager cacheManager, InfinispanConfiguration ispnConfig )
    {
        this.cacheManager = cacheManager;
        this.ispnConfig = ispnConfig;
    }

    @PostConstruct
    public void start()
    {
        startEmbeddedManager();
    }

    private void startEmbeddedManager()
    {
        cacheManager = startCacheManager( cacheManager, ISPN_XML, false );
    }

    //    private void startClusterManager()
    //    {
    //        if ( clusterConfiguration == null || !clusterConfiguration.isEnabled() )
    //        {
    //            logger.info( "Infinispan cluster configuration not enabled. Skip." );
    //            return;
    //        }
    //        clusterCacheManager = startCacheManager( clusterCacheManager, ISPN_CLUSTER_XML, true );
    //    }

    private EmbeddedCacheManager startCacheManager( EmbeddedCacheManager cacheMgr, String configFile,
                                                    Boolean isCluster )
    {
        // FIXME This is just here to trigger shutdown hook init for embedded log4j in infinispan-embedded-query.
        // FIXES:
        //
        // Thread-15 ERROR Unable to register shutdown hook because JVM is shutting down.
        // java.lang.IllegalStateException: Cannot add new shutdown hook as this is not started. Current state: STOPPED
        //
        new MarshallableTypeHints().getBufferSizePredictor( LocalCacheHandle.class );

        File confDir = ispnConfig.getInfinispanConfigDir();
        File ispnConf = new File( confDir, configFile );

        EmbeddedCacheManager mgr = cacheMgr;
        try (InputStream resouceStream = Thread.currentThread()
                                               .getContextClassLoader()
                                               .getResourceAsStream( configFile ))
        {

            String resourceStr = interpolateStrFromStream( resouceStream, "CLASSPATH:" + configFile );

            if ( ispnConf.exists() )
            {
                try (InputStream confStream = FileUtils.openInputStream( ispnConf ))
                {
                    String confStr = interpolateStrFromStream( confStream, ispnConf.getPath() );
                    mgr = mergedCachesFromConfig( mgr, confStr, "CUSTOMER" );
                    mgr = mergedCachesFromConfig( mgr, resourceStr, "CLASSPATH" );
                }
                catch ( IOException e )
                {
                    throw new RuntimeException( "Cannot read infinispan configuration from file: " + ispnConf, e );
                }
            }
            else
            {
                try
                {
                    logger.info( "Using CLASSPATH resource Infinispan configuration:\n\n{}\n\n", resourceStr );
                    if ( mgr == null )
                    {
                        mgr = new DefaultCacheManager(
                                new ByteArrayInputStream( resourceStr.getBytes( StandardCharsets.UTF_8 ) ) );
                    }

                }
                catch ( IOException e )
                {
                    throw new RuntimeException(
                            "Failed to construct ISPN cacheManger due to CLASSPATH xml stream read error.", e );
                }
            }

            if ( isCluster )
            {
                String[] cacheNames = mgr.getCacheNames().toArray( new String[] {} );
                logger.info( "Starting cluster caches to make sure they existed: {}", Arrays.toString( cacheNames ) );
                mgr.startCaches( cacheNames );
            }
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Failed to construct ISPN cacheManger due to CLASSPATH xml stream read error.",
                                        e );
        }

        return mgr;
    }

    /**
     * Get a BasicCache instance. If the remote cache is enabled, it will match the named with remote.patterns.
     * If matched, it will create/return a RemoteCache. If not matched, an embedded cache will be created/returned to the caller.
     */
    public synchronized <K, V> BasicCacheHandle<K, V> getBasicCache( String named )
    {
        return getCache( named );
    }

    /**
     * Get named cache and verify that the cache obeys our expectations for clustering.
     * There is no way to find out the runtime type of generic type parameters and we need to pass the k/v class types.
     */
    public synchronized <K, V> CacheHandle<K, V> getClusterizableCache( String named, Class<K> kClass, Class<V> vClass )
    {
        verifyClusterizable( kClass, vClass );
        return getCache( named );
    }

    private <K, V> void verifyClusterizable( Class<K> kClass, Class<V> vClass )
    {
        if ( !Serializable.class.isAssignableFrom( kClass ) && !Externalizable.class.isAssignableFrom( kClass )
                || !Serializable.class.isAssignableFrom( vClass ) && !Externalizable.class.isAssignableFrom( vClass ) )
        {
            throw new RuntimeException( kClass + " or " + vClass + " is not Serializable/Externalizable" );
        }
    }

    /**
     * Retrieve an embedded cache with a pre-defined configuration (from infinispan.xml) or the default cache configuration.
     */
    public synchronized <K, V> CacheHandle<K, V> getCache( String named )
    {
        logger.debug( "Get embedded cache, name: {}", named );
        return (CacheHandle<K, V>) caches.computeIfAbsent( named, ( k ) -> {
            Cache<K, V> cache = cacheManager.getCache( k );
            //            if ( clusterConfiguration != null && clusterConfiguration.isEnabled() && clusterCacheManager.cacheExists(
            //                    k ) )
            //            {
            //                cache = clusterCacheManager.getCache( k );
            //            }
            return new CacheHandle( k, cache, metricsManager, getCacheMetricPrefix( k ) );
        } );
    }

    @PreDestroy
    public synchronized void stop()
    {
        logger.info( "Stopping Infinispan caches." );
        caches.forEach( ( name, cacheHandle ) -> cacheHandle.stop() );

        if ( cacheManager != null )
        {
            cacheManager.stop();
            cacheManager = null;
        }

        //        if ( clusterCacheManager != null )
        //        {
        //            clusterCacheManager.stop();
        //            clusterCacheManager = null;
        //        }
    }

    /**
     * For the ISPN merging, we should involve at least two different xml config scopes here,
     * one is from indy self default resource xml, another one is from customer's config xml.
     *
     * To prevent the error of EmbeddedCacheManager instances configured with same JMX domain,
     * ISPN should enable 'allowDuplicateDomains' attribute for per GlobalConfigurationBuilder build,
     * that will cost more price there for DefaultCacheManager construct and ConfigurationBuilder build.
     *
     * Since what we need here is simply parsing xml inputStreams to the defined configurations that ISPN
     * could accept, then merging the two stream branches into a entire one.
     * What classes this method uses from ISPN are:
     * {@link ConfigurationBuilderHolder}
     * {@link ParserRegistry}
     * {@link ConfigurationManager}
     *
     * @param cacheMgr
     * @param config
     * @param path
     */
    private EmbeddedCacheManager mergedCachesFromConfig( EmbeddedCacheManager cacheMgr, String config, String path )
    {
        logger.debug( "[ISPN xml merge] cache config xml to merge:\n {}", config );
        // FIXME: here may cause ISPN000343 problem if your cache config has enabled distributed cache. Because distributed
        //       cache needs transport support, so if the cache manager does not enable it and then add this type of cache
        //       by defineConfiguration, it will report ISPN000343. So we should ensure the transport has been added by initialization.
        EmbeddedCacheManager mgr = cacheMgr;
        if ( mgr == null )
        {
            try
            {
                logger.info(
                        "Using {} resource Infinispan configuration to construct mergable cache configuration:\n\n{}\n\n",
                        path, config );
                mgr = new DefaultCacheManager( new ByteArrayInputStream( config.getBytes( StandardCharsets.UTF_8 ) ) );
            }
            catch ( IOException e )
            {
                throw new RuntimeException(
                        String.format( "Failed to construct ISPN cacheManger due to %s xml stream read error.", path ),
                        e );
            }
        }

        final ConfigurationBuilderHolder holder = ( new ParserRegistry() ).parse( config );
        final ConfigurationManager manager = new ConfigurationManager( holder );

        final Set<String> definedCaches = mgr.getCacheNames();

        for ( String name : manager.getDefinedCaches() )
        {
            if ( definedCaches.isEmpty() || !definedCaches.contains( name ) )
            {
                logger.info( "[ISPN xml merge] Define cache: {} from {} config.", name, path );
                mgr.defineConfiguration( name, manager.getConfiguration( name, false ) );
            }
        }

        return mgr;
    }

    public EmbeddedCacheManager getCacheManager()
    {
        return cacheManager;
    }

}
