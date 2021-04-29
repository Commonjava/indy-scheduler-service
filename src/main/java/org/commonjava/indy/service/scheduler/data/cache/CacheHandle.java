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
package org.commonjava.indy.service.scheduler.data.cache;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.indy.service.scheduler.data.metrics.DefaultMetricsManager;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Holder class that helps manage the shutdown process for things that use Infinispan.
 */
public class CacheHandle<K, V>
        extends BasicCacheHandle<K, V>
{
    protected CacheHandle()
    {
    }

    public CacheHandle( String named, Cache<K, V> cache, DefaultMetricsManager metricsManager, String metricPrefix )
    {
        super( named, cache, metricsManager, metricPrefix );
    }

    public CacheHandle(String named, Cache<K, V> cache )
    {
        super( named, cache, null, null );
    }

    /**
     * This is needed to pass the raw cache to lower level components, like Partyline.
     * @return
     */
    public Cache<K, V> getCache()
    {
        return (Cache) this.cache;
    }

    public <R> R executeCache( Function<Cache<K, V>, R> operation )
    {
        return doExecuteCache( "execute", operation );
    }

    public <R> R executeCache( Function<Cache<K, V>, R> operation, String metric )
    {
        if ( StringUtils.isBlank( metric ) )
        {
            return executeCache( operation );
        }
        return doExecuteCache( metric, operation );
    }

    // TODO: Why is this separate from {@link BasicCacheHandle#execute(Function)}?
    protected <R> R doExecuteCache( String metricName, Function<Cache<K, V>, R> operation )
    {
        Supplier<R> execution = executeFor( operation );
        //        if ( metricsManager != null )
        //        {
        //            return metricsManager.wrapWithStandardMetrics( execution, () -> getMetricName( metricName ) );
        //        }

        return execution.get();
    }

    private <R> Supplier<R> executeFor( final Function<Cache<K, V>, R> operation )
    {
        Logger logger = LoggerFactory.getLogger( getClass() );

        return () -> {
            if ( !isStopped() )
            {
                try
                {
                    return (R) operation.apply( (Cache) cache );
                }
                catch ( RuntimeException e )
                {
                    logger.error( "Failed to complete operation: " + e.getMessage(), e );
                }
            }
            else
            {
                logger.error( "Cannot complete operation. Cache {} is shutting down.", getName() );
            }

            return null;
        };
    }

}
