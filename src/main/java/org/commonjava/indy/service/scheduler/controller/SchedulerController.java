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
import org.commonjava.indy.service.scheduler.model.ScheduleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

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

    public void schedule( final SchedulerInfo scheduleInfo )
            throws SchedulerException
    {
        try
        {
            scheduleManager.schedule( scheduleInfo.getKey(), scheduleInfo.getJobType(), scheduleInfo.getJobName(),
                                      scheduleInfo.getPayload(), scheduleInfo.getTimeoutSeconds() );
        }
        catch ( SchedulerException e )
        {
            logger.error( "Some errors happened during scheduling: {}", e.getMessage(), e );
            throw e;
        }
    }

    public Optional<ScheduleKey> cancel( final ScheduleKey key )
    {
        return scheduleManager.cancel( key.getKey(), key.getType(), key.getName() );
    }

}
