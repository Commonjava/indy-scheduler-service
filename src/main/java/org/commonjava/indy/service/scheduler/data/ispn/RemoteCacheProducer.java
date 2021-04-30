/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/indy)
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
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.RemoteCounterManagerFactory;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.MarshallerUtil;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.infinispan.counter.api.CounterConfiguration;
import org.infinispan.counter.api.CounterManager;
import org.infinispan.counter.api.CounterType;
import org.infinispan.counter.api.Storage;
import org.infinispan.counter.api.StrongCounter;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.annotations.ProtoSchemaBuilder;
import org.infinispan.protostream.impl.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.infinispan.query.remote.client.ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME;

@ApplicationScoped
public class RemoteCacheProducer
        extends AbstractCacheProducer
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    InfinispanConfiguration ispnConfig;

    private RemoteCacheManager remoteCacheManager;

    private Map<String, BasicCacheHandle> caches = new ConcurrentHashMap<>(); // hold embedded and remote caches

    private Map<String, StrongCounter> counters = new ConcurrentHashMap<>();

    protected RemoteCacheProducer()
    {
    }

    @PostConstruct
    public void start()
    {
        startRemoteManager();
    }

    private void startRemoteManager()
    {
        if ( ispnConfig == null || !ispnConfig.isRemoteEnabled() )
        {
            logger.info( "Infinispan remote configuration not enabled. Skip." );
            return;
        }

        ConfigurationBuilder builder = new ConfigurationBuilder();
        Properties props = new Properties();
        try (Reader config = new FileReader( ispnConfig.getHotrodClientConfigPath() ))
        {
            props.load( config );
            builder.withProperties( props );
        }
        catch ( IOException e )
        {
            logger.error( "Load hotrod client properties failure.", e );
        }

        remoteCacheManager = new RemoteCacheManager( builder.build() );
        remoteCacheManager.start();

        logger.info( "Infinispan remote cache manager started." );
    }

    public synchronized <K, V> BasicCacheHandle<K, V> getCache( String named )
    {
        BasicCacheHandle handle = caches.computeIfAbsent( named, ( k ) -> {
            RemoteCache<K, V> cache;
            try
            {
                // For infinispan 9.x, it needs to load the specific cache configuration to create it
                // For infinispan 11.x, there is no need to load this configuration here, instead, declaring it
                // in hotrod-client.properties and get the cache by remoteCacheManager.getCache( "cacheName" )
                File confDir = ispnConfig.getInfinispanConfigDir();
                File cacheConf = new File( confDir, "caches/cache-" + named + ".xml" );
                if ( !cacheConf.exists() )
                {
                    logger.warn( "Invalid conf path, name: {}, path: {}", named, cacheConf );
                    return null;
                }
                String confStr;
                try (InputStream confStream = FileUtils.openInputStream( cacheConf ))
                {
                    confStr = interpolateStrFromStream( confStream, cacheConf.getPath() );
                }
                catch ( IOException e )
                {
                    throw new RuntimeException( "Cannot read cache configuration from file: " + cacheConf, e );
                }
                cache = remoteCacheManager.administration()
                                          .getOrCreateCache( named, new XMLStringConfiguration( confStr ) );
                if ( cache == null )
                {
                    logger.warn( "Can not get remote cache, name: {}", k );
                    return null;
                }
            }
            catch ( Exception e )
            {
                logger.warn( "Get remote cache failed", e );
                return null;
            }
            logger.info( "Get remote cache, name: {}", k );
            return new RemoteCacheHandle( k, cache, metricsManager, getCacheMetricPrefix( k ) );
        } );

        return handle;
    }

    public synchronized <K> void registerProtoSchema( Class<K> kClass, String packageName, String fileName )
    {
        SerializationContext ctx = MarshallerUtil.getSerializationContext( remoteCacheManager );
        // Use ProtoSchemaBuilder to define a Protobuf schema on the client
        ProtoSchemaBuilder protoSchemaBuilder = new ProtoSchemaBuilder();
        String protoFile;
        try
        {
            protoFile =
                    protoSchemaBuilder.fileName( fileName ).addClass( kClass ).packageName( packageName ).build( ctx );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( " Register proto schema error, schema: " + fileName, e );
        }

        // Retrieve metadata cache and register the new schema on the infinispan server too
        RemoteCache<String, String> metadataCache = remoteCacheManager.getCache( PROTOBUF_METADATA_CACHE_NAME );

        metadataCache.put( fileName, protoFile );
    }

    public synchronized void registerProtoAndMarshallers( String protofile, List<BaseMarshaller> marshallers )
    {
        SerializationContext ctx = MarshallerUtil.getSerializationContext( remoteCacheManager );
        try
        {
            ctx.registerProtoFiles( FileDescriptorSource.fromResources( protofile ) );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Register proto files error, protofile: " + protofile, e );
        }

        for ( BaseMarshaller marshaller : marshallers )
        {
            try
            {
                ctx.registerMarshaller( marshaller );
            }
            catch ( Exception e )
            {
                throw new RuntimeException( "Register the marshallers error.", e );
            }
        }

        // Retrieve metadata cache and register the new schema on the infinispan server too
        RemoteCache<String, String> metadataCache = remoteCacheManager.getCache( PROTOBUF_METADATA_CACHE_NAME );

        metadataCache.put( protofile, ResourceUtils.getResourceAsString( getClass(), "/" + protofile ) );
    }

    public synchronized StrongCounter getStrongCounter( String counter )
    {
        if ( ispnConfig == null )
        {
            return null;
        }
        return counters.computeIfAbsent( counter, ( k ) -> {
            CounterManager cm = RemoteCounterManagerFactory.asCounterManager( remoteCacheManager );
            if ( !cm.isDefined( k ) )
            {
                cm.defineCounter( k, CounterConfiguration.builder( CounterType.BOUNDED_STRONG )
                                                         .initialValue( 1 )
                                                         .lowerBound( 0 )
                                                         .storage( Storage.VOLATILE )
                                                         .build() );
            }
            return cm.getStrongCounter( k );
        } );

    }

}
