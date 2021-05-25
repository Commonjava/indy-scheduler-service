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
package org.commonjava.indy.service.scheduler.data.cassandra;

import org.commonjava.indy.service.scheduler.config.InfinispanConfiguration;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.RemoteCounterManagerFactory;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.counter.api.CounterConfiguration;
import org.infinispan.counter.api.CounterManager;
import org.infinispan.counter.api.CounterType;
import org.infinispan.counter.api.Storage;
import org.infinispan.counter.api.StrongCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This RemoteCounter is working as a clusterable lock to orchestrate the expiration calculation between different nodes. As cassandra
 * based worker scheduling system in cluster mode, a distributed locker is used to make the expiration calculation happening in single node
 * during same time to avoid duplication.
 */
@ApplicationScoped
public class ISPNRemoteCounter
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    InfinispanConfiguration ispnConfig;

    private RemoteCacheManager remoteCacheManager;

    private final Map<String, StrongCounter> counters = new ConcurrentHashMap<>();

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

        logger.info( "Infinispan remote counter started." );
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
