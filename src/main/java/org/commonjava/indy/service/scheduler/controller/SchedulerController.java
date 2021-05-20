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
package org.commonjava.indy.service.scheduler.controller;

import org.commonjava.indy.service.scheduler.data.ScheduleManager;
import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@ApplicationScoped
public class SchedulerController
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    ScheduleManager scheduleManager;

    protected SchedulerController()
    {
    }

    public SchedulerController( ScheduleManager scheduleManager )
    {
        this.scheduleManager = scheduleManager;
    }

    public Optional<SchedulerInfo> get( final String key, final String jobName, final String jobType )
    {
        final String type = getJobType( jobType );
        return scheduleManager.get( key, jobName, type );
    }

    public void schedule( final SchedulerInfo scheduleInfo )
            throws SchedulerException
    {
        scheduleInfo.setScheduleManager( scheduleManager ).createScheduler();
    }

    public Optional<SchedulerInfo> cancel( final String key, final String jobName, final String jobType )
            throws SchedulerException
    {
        assert isNotBlank( key );
        assert isNotBlank( jobName );
        final String type = getJobType( jobType );
        return new SchedulerInfo().setKey( key )
                                  .setJobName( jobName )
                                  .setJobType( type )
                                  .setScheduleManager( scheduleManager )
                                  .cancelScheduler();
    }

    private String getJobType( final String nullableType )
    {
        return isBlank( nullableType ) ? ScheduleManager.CONTENT_JOB_TYPE : nullableType;
    }

}
