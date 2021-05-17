package org.commonjava.indy.service.scheduler.model;

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
