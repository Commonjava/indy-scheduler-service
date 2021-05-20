package org.commonjava.indy.service.scheduler.data;

import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;
import org.commonjava.indy.service.scheduler.model.Expiration;
import org.commonjava.indy.service.scheduler.model.ExpirationSet;
import org.commonjava.indy.service.scheduler.model.ScheduleKey;

import java.util.Map;
import java.util.Optional;

public interface ScheduleManager
{
    String CONTENT_JOB_TYPE = "CONTENT";

    Optional<SchedulerInfo> get( final String key, final String jobName, final String jobType );

    void schedule( final String key, final String jobType, final String jobName, final Map<String, Object> payload,
                   final int startSeconds )
            throws SchedulerException;

    Optional<ScheduleKey> cancel( final String key, final String jobType, final String jobName );

    Expiration findSingleExpiration( final String key, final String jobType );

    ExpirationSet findMatchingExpirations( final String jobType );

}
