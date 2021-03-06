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
package org.commonjava.indy.service.scheduler.data.ispn;

import org.commonjava.indy.service.scheduler.data.ispn.local.CacheHandle;
import org.commonjava.indy.service.scheduler.data.ispn.local.LocalCacheProducer;
import org.commonjava.indy.service.scheduler.model.ScheduleKey;
import org.commonjava.indy.service.scheduler.model.ScheduleValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * This ISPN cache producer is used to generate {@link ScheduleCache} and {@link ScheduleEventLockCache}.
 * <ul>
 * <li>{@link ScheduleCache} is used as a job control center to control the content expiration scheduling.</li>
 * <li>{@link ScheduleEventLockCache} is used for control clustered event scheduling synchronization.</li>
 * </ul>
 */
public class ScheduleCacheProducer
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    LocalCacheProducer cacheProducer;

    private static final String SCHEDULE_EXPIRE = "schedule-expire-cache";

    private static final String SCHEDULE_EVENT_LOCK = "schedule-event-lock-cache";

    @ScheduleCache
    @Produces
    @ApplicationScoped
    public CacheHandle<ScheduleKey, ScheduleValue> scheduleExpireCache()
    {
        return cacheProducer.getCache( SCHEDULE_EXPIRE );
    }

}
