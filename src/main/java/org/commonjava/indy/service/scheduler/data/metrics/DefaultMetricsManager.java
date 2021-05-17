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
package org.commonjava.indy.service.scheduler.data.metrics;

import javax.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

@ApplicationScoped
public class DefaultMetricsManager
{
    public static final String DEFAULT = "default";

    public static final String METER = "meter";

    public static final String INDY_METRIC_ISPN = "indy.ispn";

    //TODO: Note that this is just a mock metrics manager, will use o11yphant one later instead when metrics support added back
    @MetricWrapper
    public <T> T wrapWithStandardMetrics( final Supplier<T> method,
                                          @MetricWrapperNamed final Supplier<String> classifier )
    {
        //TODO: MOCKING
        return method.get();
    }

    public Meter getMeter( String name )
    {
        //TODO: MOCKING
        return new MockMeter();
    }

    private static class MockMeter implements Meter {
        @Override
        public void mark()
        {
            //TODO: Implement later
        }

        @Override
        public void mark( long n )
        {
            //TODO: Implement later
        }

        @Override
        public long getCount()
        {
            return 0;
        }

        @Override
        public double getFifteenMinuteRate()
        {
            return 0;
        }

        @Override
        public double getFiveMinuteRate()
        {
            return 0;
        }

        @Override
        public double getMeanRate()
        {
            return 0;
        }

        @Override
        public double getOneMinuteRate()
        {
            return 0;
        }
    }
}
