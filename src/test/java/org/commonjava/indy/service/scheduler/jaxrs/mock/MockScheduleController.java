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
package org.commonjava.indy.service.scheduler.jaxrs.mock;

import org.commonjava.indy.service.scheduler.controller.SchedulerController;
import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import java.util.Collections;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

@ApplicationScoped
@Alternative
public class MockScheduleController
        extends SchedulerController
{
    public Optional<SchedulerInfo> get( final String key, final String jobName, final String jobType )
    {
        final SchedulerInfo info = getMockSchedulerInfo();
        if ( info.getKey().equals( key ) )
        {
            return of( info );
        }
        return empty();
    }

    public void schedule( final SchedulerInfo scheduleInfo )
            throws SchedulerException
    {
        if ( scheduleInfo.getKey().equals( "wrongKey" ) )
        {
            throw new SchedulerException( "wrongKey" );
        }

    }

    public Optional<SchedulerInfo> cancel( final String key, final String jobName, final String jobType )
            throws SchedulerException
    {
        if ( key.equals( "wrongKey" ) )
        {
            throw new SchedulerException( "wrongKey" );
        }
        final SchedulerInfo info = getMockSchedulerInfo();
        if ( info.getKey().equals( key ) )
        {
            return of( info );
        }
        return empty();
    }

    private SchedulerInfo getMockSchedulerInfo()
    {
        return new SchedulerInfo().setJobName( "testJob" )
                                  .setJobType( "testType" )
                                  .setKey( "testKey" )
                                  .setPayload( Collections.singletonMap( "testPayKey", "testPayVal" ) )
                                  .setTimeoutSeconds( 30 );
    }
}
