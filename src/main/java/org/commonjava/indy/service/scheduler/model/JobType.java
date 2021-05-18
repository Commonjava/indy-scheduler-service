package org.commonjava.indy.service.scheduler.model;

/**
 * @deprecated As new scheduler service will accept job_type as a parameter from other services, it is just a simple string type which can accept anything. So this enum will be deprecated.
 */
@Deprecated
public enum JobType
{

    CONTENT( "CONTENT" ),
    DISABLED_TIMEOUT( "Diabled-timeout" );

    private final String jobType;

    JobType( String jobType )
    {
        this.jobType = jobType;
    }

    public String getJobType()
    {
        return this.jobType;
    }

}
