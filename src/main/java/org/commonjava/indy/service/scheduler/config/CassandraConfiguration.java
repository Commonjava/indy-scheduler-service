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
package org.commonjava.indy.service.scheduler.config;

import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

import static java.util.Optional.of;

@Startup
@ApplicationScoped
public class CassandraConfiguration
{
    @Inject
    @ConfigProperty( name = "cassandra.enabled", defaultValue = "false" )
    Boolean enabled;

    @Inject
    @ConfigProperty( name = "cassandra.host", defaultValue = "localhost" )
    String cassandraHost;

    @Inject
    @ConfigProperty( name = "cassandra.port", defaultValue = "9402" )
    int cassandraPort;

    @Inject
    @ConfigProperty( name = "cassandra.user" )
    Optional<String> cassandraUser;

    @Inject
    @ConfigProperty( name = "cassandra.pass" )
    Optional<String> cassandraPass;

    @Inject
    @ConfigProperty( name = "cassandra.timeoutMillis.connect", defaultValue = "60000" )
    int connectTimeoutMillis;

    @Inject
    @ConfigProperty( name = "cassandra.timeoutMillis.read", defaultValue = "60000" )
    int readTimeoutMillis;

    @Inject
    @ConfigProperty( name = "cassandra.retries.read", defaultValue = "3" )
    int readRetries;

    @Inject
    @ConfigProperty( name = "cassandra.retries.write", defaultValue = "3" )
    int writeRetries;

    @Inject
    @ConfigProperty( name = "cassandra.keyspace" )
    Optional<String> keyspace;

    @Inject
    @ConfigProperty( name = "cassandra.replicaFactor", defaultValue = "0" )
    int replicationFactor;

    @Inject
    @ConfigProperty( name = "cassandra.keyspaceReplicas", defaultValue = "0" )
    int keyspaceReplicas;

    @Inject
    @ConfigProperty( name = "cassandra.keyspace" )
    Optional<String> scheduleKeyspace;

    @Inject
    @ConfigProperty( name = "cassandra.partition.range", defaultValue = "1L" )
    long partitionKeyRange;

    @Inject
    @ConfigProperty( name = "cassandra.rate.period", defaultValue = "60L" )
    long scheduleRatePeriod;

    @Inject
    @ConfigProperty( name = "cassandra.hours.offset", defaultValue = "0" )
    int offsetHours;

    public Boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( Boolean enabled )
    {
        this.enabled = enabled;
    }

    public void setCassandraHost( String host )
    {
        cassandraHost = host;
    }

    public void setCassandraPort( Integer port )
    {
        cassandraPort = port;
    }

    public void setCassandraUser( String cassandraUser )
    {
        this.cassandraUser = of( cassandraUser );
    }

    public void setCassandraPass( String cassandraPass )
    {
        this.cassandraPass = of( cassandraPass );
    }

    public String getCassandraHost()
    {
        return cassandraHost;
    }

    public Integer getCassandraPort()
    {
        return cassandraPort;
    }

    public String getCassandraUser()
    {
        return cassandraUser.orElse( "" );
    }

    public String getCassandraPass()
    {
        return cassandraPass.orElse( "" );
    }

    public int getConnectTimeoutMillis()
    {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis( int connectTimeoutMillis )
    {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis()
    {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis( int readTimeoutMillis )
    {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getReadRetries()
    {
        return readRetries;
    }

    public void setReadRetries( int readRetries )
    {
        this.readRetries = readRetries;
    }

    public int getWriteRetries()
    {
        return writeRetries;
    }

    public void setWriteRetries( int writeRetries )
    {
        this.writeRetries = writeRetries;
    }

    public String getKeyspace()
    {
        return keyspace.orElse( "" );
    }

    public void setKeyspace( String keyspace )
    {
        this.keyspace = of( keyspace );
    }

    public int getReplicationFactor()
    {
        return replicationFactor;
    }

    public void setReplicationFactor( int replicationFactor )
    {
        this.replicationFactor = replicationFactor;
    }

    public int getKeyspaceReplicas()
    {
        return keyspaceReplicas;
    }

    public void setKeyspaceReplicas( int keyspaceReplicas )
    {
        this.keyspaceReplicas = keyspaceReplicas;
    }

    public String getScheduleKeyspace()
    {
        return scheduleKeyspace.orElse( "" );
    }

    public void setScheduleKeyspace( String scheduleKeyspace )
    {
        this.scheduleKeyspace = Optional.of( scheduleKeyspace );
    }

    public long getPartitionKeyRange()
    {
        return partitionKeyRange;
    }

    public void setPartitionKeyRange( long partitionKeyRange )
    {
        this.partitionKeyRange = partitionKeyRange;
    }

    public long getScheduleRatePeriod()
    {
        return scheduleRatePeriod;
    }

    public void setScheduleRatePeriod( long scheduleRatePeriod )
    {
        this.scheduleRatePeriod = scheduleRatePeriod;
    }

    public int getOffsetHours()
    {
        return offsetHours;
    }

    public void setOffsetHours( int offsetHours )
    {
        this.offsetHours = offsetHours;
    }
}
