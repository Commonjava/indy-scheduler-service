/**
 * Copyright (C) 2011-2020 Red Hat, Inc. (https://github.com/Commonjava/indy)
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
package org.commonjava.indy.service.scheduler.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.commonjava.event.common.IndyEvent;

public abstract class ScheduleEvent
        extends IndyEvent
{

    @JsonProperty( "job_type" )
    private final String jobType;

    @JsonProperty( "job_name" )
    private final String jobName;

    @JsonProperty( "payload" )
    private final String payload;

    @JsonCreator
    public ScheduleEvent( @JsonProperty( "job_type" ) final String jobType,
                          @JsonProperty( "job_name" ) final String jobName,
                          @JsonProperty( "payload" ) final String payload )
    {
        this.jobType = jobType;
        this.jobName = jobName;
        this.payload = payload;
    }

    public String getJobType()
    {
        return jobType;
    }

    public String getJobName()
    {
        return jobName;
    }

    public String getPayload()
    {
        return payload;
    }

    @JsonProperty( "eventType" )
    public abstract ScheduleEventType getEventType();

    @Override
    public String toString()
    {
        return String.format( "SchedulerEvent [eventType=%s, jobType=%s, payload=%s]", getClass().getName(), jobType,
                              payload );
    }

}
