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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.commonjava.indy.service.scheduler.data.ScheduleManager;
import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.model.ScheduleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class SchedulerInfo
{
    private final Logger logger = LoggerFactory.getLogger( this.getClass() );

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

    @JsonIgnore
    private transient ScheduleManager scheduleManager;

    @JsonIgnore
    private transient ScheduleKey scheduleKey;

    public SchedulerInfo buildKey()
    {
        this.scheduleKey = new ScheduleKey( this.getKey(), this.getJobType(), this.jobName );
        return this;
    }

    public SchedulerInfo setScheduleManager( final ScheduleManager scheduleManager )
    {
        assert scheduleManager != null;
        this.scheduleManager = scheduleManager;
        return this;
    }

    public SchedulerInfo createScheduler()
            throws SchedulerException
    {
        if ( scheduleManager == null )
        {
            throw new SchedulerException( "Cannot create scheduler for {}: There is no scheduler manager bounded!" );
        }
        try
        {
            scheduleManager.schedule( this.getKey(), this.getJobType(), this.getJobName(), this.getPayload(),
                                      this.getTimeoutSeconds() );
        }
        catch ( SchedulerException e )
        {
            logger.error( "Some errors happened during scheduling: {}", e.getMessage(), e );
            throw e;
        }
        return this;
    }

    public Optional<SchedulerInfo> cancelScheduler()
            throws SchedulerException
    {
        if ( scheduleManager == null )
        {
            throw new SchedulerException( "Cannot create scheduler for {}: There is no scheduler manager bounded!" );
        }
        Optional<ScheduleKey> scheduleKey =
                scheduleManager.cancel( this.getKey(), this.getJobType(), this.getJobName() );
        return scheduleKey.isEmpty() ? Optional.empty() : Optional.of( this );
    }

    public String getKey()
    {
        return key;
    }

    public SchedulerInfo setKey( String key )
    {
        this.key = key;
        return this;
    }

    public String getJobType()
    {
        return jobType;
    }

    public SchedulerInfo setJobType( String jobType )
    {
        this.jobType = jobType;
        return this;
    }

    public String getJobName()
    {
        return jobName;
    }

    public SchedulerInfo setJobName( String jobName )
    {
        this.jobName = jobName;
        return this;
    }

    public Map<String, Object> getPayload()
    {
        return payload;
    }

    public SchedulerInfo setPayload( Map<String, Object> payload )
    {
        this.payload = payload;
        return this;
    }

    public int getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public SchedulerInfo setTimeoutSeconds( int timeoutSeconds )
    {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

}
