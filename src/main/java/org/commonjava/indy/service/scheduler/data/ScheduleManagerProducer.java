/**
 * Copyright (C) 2020 Red Hat, Inc.
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
package org.commonjava.indy.service.scheduler.data;

import org.commonjava.indy.service.scheduler.config.ScheduleConfiguration;
import org.commonjava.indy.service.scheduler.data.cassandra.CassandraDBScheduleManager;
import org.commonjava.indy.service.scheduler.data.ispn.ISPNScheduleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

@ApplicationScoped
public class ScheduleManagerProducer
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    ScheduleConfiguration scheduleConfig;

    @Produces
    @ApplicationScoped
    @Default
    public ScheduleManager produces( @StandaloneScheduleManager ISPNScheduleManager ispnManager,
                                     @ClusterScheduleManager CassandraDBScheduleManager cassandraManager )
    {
        if ( scheduleConfig.isClusterEnabled() )
        {
            logger.info( "Using Cassandra DB Schedule Manager for job scheduling." );
            return cassandraManager;
        }
        logger.info( "Using Infinispan Schedule Manager for job scheduling." );
        return ispnManager;
    }

}
