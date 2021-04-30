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

import java.util.Properties;

public class ConnectionPoolInfo
{
    private final String name;

    private final boolean useMetrics;
//
//    private boolean useHealthChecks;

    private final Properties properties;

    public ConnectionPoolInfo( final String name, final Properties properties, final boolean useMetrics )
    {
        this.name = name;
        this.properties = properties;
        this.useMetrics = useMetrics;
//        this.useHealthChecks = useHealthChecks;
    }

    public boolean isUseMetrics()
    {
        return useMetrics;
    }

//    public boolean isUseHealthChecks()
//    {
//        return useHealthChecks;
//    }

    public String getName()
    {
        return name;
    }

    public Properties getProperties()
    {
        return properties;
    }

    @Override
    public String toString()
    {
        return "ConnectionPoolInfo{" + "name='" + name + "'}";
    }
}
