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

import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.commonjava.indy.service.scheduler.config.MetricsConfiguration;
import org.commonjava.indy.service.scheduler.data.metrics.DefaultMetricsManager;
import org.commonjava.indy.service.scheduler.data.metrics.NameUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.commonjava.indy.service.scheduler.data.metrics.DefaultMetricsManager.INDY_METRIC_ISPN;

public abstract class AbstractCacheProducer
{
    @Inject
    DefaultMetricsManager metricsManager;

    @Inject
    MetricsConfiguration metricsConfig;

    protected String getCacheMetricPrefix( String named )
    {
        return metricsManager == null ?
                null :
                NameUtils.getSupername( metricsConfig.getNodePrefix(), INDY_METRIC_ISPN, named );
    }

    protected String interpolateStrFromStream( InputStream inputStream, String path )
    {
        String configuration;
        try
        {
            configuration = IOUtils.toString( inputStream, Charset.defaultCharset() );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Cannot read infinispan configuration from : " + path, e );
        }

        StringSearchInterpolator interpolator = new StringSearchInterpolator();
        interpolator.addValueSource( new PropertiesBasedValueSource( System.getProperties() ) );

        try
        {
            configuration = interpolator.interpolate( configuration );
        }
        catch ( InterpolationException e )
        {
            throw new RuntimeException( "Cannot resolve expressions in infinispan configuration from: " + path, e );
        }
        return configuration;
    }
}
