package org.commonjava.indy.service.scheduler.data;

import org.commonjava.indy.service.scheduler.exception.SchedulerException;
import org.commonjava.indy.service.scheduler.model.Expiration;
import org.commonjava.indy.service.scheduler.model.ExpirationSet;

public interface ScheduleManager
{
    void init();

    void setProxyTimeouts( final String key, final String path )
            throws SchedulerException;

    void setSnapshotTimeouts( final String key, final String path )
            throws SchedulerException;

    void rescheduleSnapshotTimeouts( final HostedRepository deploy )
            throws SchedulerException;

    void rescheduleProxyTimeouts( final RemoteRepository repo )
            throws SchedulerException;

    void rescheduleDisableTimeout( final String key )
            throws SchedulerException;

    Expiration findSingleExpiration( final String key, final String jobType );

    ExpirationSet findMatchingExpirations( final String jobType );
}
