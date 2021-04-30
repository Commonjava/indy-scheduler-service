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
package org.commonjava.indy.service.scheduler.data.ispn.cpool;

import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import static java.lang.Boolean.TRUE;

@Startup
@ApplicationScoped
public class ConnectionPoolConfig
{
    public static final String DS_PROPERTY_PREFIX = "datasource.";

    private static final String METRICS_SUBKEY = "metrics";

    private static final String POOL_NAME = "schedule-data-pool";

    @Inject
    @ConfigProperty( name = "ispn." + POOL_NAME )
    Optional<String> poolJndiName;

    //    private static final String HEALTH_CHECKS_SUBKEY = "healthChecks";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final Map<String, ConnectionPoolInfo> pools = new HashMap<>();

    public Map<String, ConnectionPoolInfo> getPools()
    {
        return pools;
    }

    @PostConstruct
    public void init()
    {

        if ( poolJndiName.isPresent() )
        {
            final String value = poolJndiName.get();
            // don't pass the param through to the background map, consume it here.
            logger.info( "{}: Parsing connection pool from: '{}'", this, value );
            Map<String, String> valueMap = toMap( value );

            boolean metrics = TRUE.toString().equals( valueMap.remove( METRICS_SUBKEY ) );
            //        boolean healthChecks = TRUE.toString().equals( valueMap.remove( HEALTH_CHECKS_SUBKEY ) );

            final ConnectionPoolInfo
                    cp = new ConnectionPoolInfo( POOL_NAME, toProperties( valueMap ), metrics );

            logger.info( "{}: Adding: {}", this, cp );
            pools.put( POOL_NAME, cp );
        }

    }

    private Properties toProperties( final Map<String, String> valueMap )
    {
        Properties props = new Properties();
        valueMap.forEach( ( k, v ) -> {
            //TODO remove log
            logger.info( "Add datasource property: {}->{}", k, v );
            props.setProperty( k, v );
        } );

        return props;
    }

    private Map<String, String> toMap( final String value )
    {
        Map<String, String> result = new HashMap<>();
        Stream.of( value.split( "\\s*,\\s*" ) ).forEach( ( s ) -> {
            String[] parts = s.trim().split( "\\s*=\\s*" );
            if ( parts.length < 1 )
            {
                result.put( parts[0], Boolean.toString( TRUE ) );
            }
            else
            {
                result.put( parts[0], parts[1] );
            }
        } );

        return result;
    }

}
