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
package org.commonjava.indy.service.scheduler.jaxrs;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SchedulerInfo
{
    @JsonProperty( "key" )
    private String key;

    @JsonProperty( "job_type" )
    private String jobType;

    @JsonProperty( "job_name" )
    private String jobName;

    @JsonProperty( "payload" )
    private Map<String, Object> payload;

    @JsonProperty( "timeout_seconds" )
    private int timeoutSeconds;

    public String getKey()
    {
        return key;
    }

    public void setKey( String key )
    {
        this.key = key;
    }

    public String getJobType()
    {
        return jobType;
    }

    public void setJobType( String jobType )
    {
        this.jobType = jobType;
    }

    public String getJobName()
    {
        return jobName;
    }

    public void setJobName( String jobName )
    {
        this.jobName = jobName;
    }

    public Map<String, Object> getPayload()
    {
        return payload;
    }

    public void setPayload( Map<String, Object> payload )
    {
        this.payload = payload;
    }

    public int getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds( int timeoutSeconds )
    {
        this.timeoutSeconds = timeoutSeconds;
    }
}
