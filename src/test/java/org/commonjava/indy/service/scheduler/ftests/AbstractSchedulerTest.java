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
package org.commonjava.indy.service.scheduler.ftests;

import org.commonjava.indy.service.scheduler.event.ScheduleEvent;
import org.commonjava.indy.service.scheduler.event.ScheduleEventType;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.List;
import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public abstract class AbstractSchedulerTest
{
    private static final int NAME_LEN = 8;

    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    static final String API_BASE = "/api/schedule";

    protected String newName()
    {
        final Random rand = new Random();
        final StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < NAME_LEN; i++ )
        {
            sb.append( NAME_CHARS.charAt( ( Math.abs( rand.nextInt() ) % ( NAME_CHARS.length() - 1 ) ) ) );
        }

        return sb.toString();
    }

    protected void assertEvents( List<? extends Message<ScheduleEvent>> events, final String name,
                                 final ScheduleEventType eventType, final String jobType, final String payload )
    {
        assertThat( events.size(), greaterThan( 0 ) );
        boolean pass = false;

        for ( Message<ScheduleEvent> event : events )
        {
            ScheduleEvent scheduleEvent = event.getPayload();
            if ( scheduleEvent.getEventType() == eventType && jobType.equals( scheduleEvent.getJobType() )
                    && name.equals( scheduleEvent.getJobName() ) )
            {
                pass = true;
                assertThat( scheduleEvent.getPayload(), is( payload ) );
                break;
            }
        }
        assertThat( pass, is( true ) );
    }
}
