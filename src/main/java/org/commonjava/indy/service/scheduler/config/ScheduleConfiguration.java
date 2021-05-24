/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/service-parent)
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
package org.commonjava.indy.service.scheduler.config;

import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

@Startup
@ApplicationScoped
public class ScheduleConfiguration
{
    public static final String STORAGE_INFINISPAN = "infinispan";

    public static final String STORAGE_CASSANDRA = "cassandra";

    private static final String CLUSTER_LOCK_EXPIRATION = "scheduler.cluster.lock.expiration.sec";

    @Inject
    @ConfigProperty( name = CLUSTER_LOCK_EXPIRATION, defaultValue = "3600" )
    Integer clusterLockExpiration;

    @Inject
    @ConfigProperty( name = "scheduler.data-storage" )
    Optional<String> storageType;



    public int getClusterLockExpiration()
    {
        return clusterLockExpiration;
    }

    public void setClusterLockExpiration( Integer clusterLockExpiration )
    {
        this.clusterLockExpiration = clusterLockExpiration;
    }

    public String getStorageType()
    {
        return storageType.orElse( "" );
    }

    public void setStorageType( String storageType )
    {
        this.storageType = Optional.of( storageType );
    }



}
