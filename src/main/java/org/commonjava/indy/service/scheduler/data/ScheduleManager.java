package org.commonjava.indy.service.scheduler.data;

import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.model.Expiration;
import org.commonjava.indy.service.scheduler.model.ExpirationSet;
import org.commonjava.indy.service.scheduler.model.ScheduleKey;

import java.util.Map;
import java.util.Optional;

public interface ScheduleManager
{
    //    void init();

    //    void setProxyTimeouts( final String key, final String path )
    //            throws SchedulerException;
    //
    //    void setSnapshotTimeouts( final String key, final String path )
    //            throws SchedulerException;
    //
    //    void rescheduleSnapshotTimeouts( final HostedRepository deploy )
    //            throws SchedulerException;
    //
    //    void rescheduleProxyTimeouts( final RemoteRepository repo )
    //            throws SchedulerException;
    //
    //    void rescheduleDisableTimeout( final String key )
    //            throws SchedulerException;
    void schedule( final String key, final String jobType, final String jobName, final Map<String, Object> payload,
                   final int startSeconds )
            throws SchedulerException;

    Optional<ScheduleKey> cancel( final String key, final String jobType, final String jobName );

    Expiration findSingleExpiration( final String key, final String jobType );

    ExpirationSet findMatchingExpirations( final String jobType );

}
