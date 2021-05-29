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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.mutiny.Uni;
import org.commonjava.indy.service.scheduler.controller.SchedulerController;
import org.commonjava.indy.service.scheduler.exception.SchedulerException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@ApplicationScoped
@Path( "/schedule" )
public class SchedulerResource
{
    @Inject
    SchedulerController controller;

    @GET
    @Produces( APPLICATION_JSON )
    public Uni<Response> get( @QueryParam( "key" ) final String key, @QueryParam( "job_name" ) final String jobName,
                              @QueryParam( "job_type" ) final String jobType,
                              final @Context SecurityContext securityContext )
    {
        Optional<SchedulerInfo> info = controller.get( key, jobName, jobType );
        Response response;
        if ( info.isEmpty() )
        {
            response = Response.status( NOT_FOUND ).build();
        }
        else
        {
            response = Response.ok( info ).build();
        }
        return Uni.createFrom().item( response );
    }

    @GET
    @Path( "/all" )
    @Produces( APPLICATION_JSON )
    public Uni<Response> getAll( final @Context SecurityContext securityContext )
    {
        //TODO: Not implement yet!
        return Uni.createFrom().item( Response.status( Response.Status.METHOD_NOT_ALLOWED ).build() );
    }

    @POST
    @Consumes( APPLICATION_JSON )
    @Produces( APPLICATION_JSON )
    public Uni<Response> schedule( final SchedulerInfo schedulerInfo, final @Context SecurityContext securityContext )
    {
        return doSchedule( schedulerInfo, true );
    }

    @PUT
    @Consumes( APPLICATION_JSON )
    @Produces( APPLICATION_JSON )
    public Uni<Response> reschedule( final SchedulerInfo schedulerInfo, final @Context SecurityContext securityContext )
    {
        return doSchedule( schedulerInfo, false );
    }

    private Uni<Response> doSchedule( final SchedulerInfo schedulerInfo, final boolean isNew )
    {
        Response response;
        try
        {
            controller.schedule( schedulerInfo );
            response = Response.status( isNew ? Response.Status.CREATED : Response.Status.OK ).build();
        }
        catch ( SchedulerException e )
        {
            response = Response.status( INTERNAL_SERVER_ERROR )
                               .entity( new ErrorResponseInfo( String.valueOf( INTERNAL_SERVER_ERROR.getStatusCode() ),
                                                               e.getMessage() ) )
                               .build();
        }
        return Uni.createFrom().item( response );
    }

    @DELETE
    @Produces( APPLICATION_JSON )
    public Uni<Response> cancel( @QueryParam( "key" ) final String key, @QueryParam( "job_name" ) final String jobName,
                                 @QueryParam( "job_type" ) final String jobType,
                                 final @Context SecurityContext securityContext )
    {
        Response response;
        try
        {
            if ( controller.cancel( key, jobName, jobType ).isPresent() )
            {
                response = Response.noContent().build();
            }
            else
            {
                response = Response.status( NOT_FOUND ).build();
            }
        }
        catch ( Exception e )
        {
            response = Response.status( INTERNAL_SERVER_ERROR )
                               .entity( new ErrorResponseInfo( String.valueOf( INTERNAL_SERVER_ERROR.getStatusCode() ),
                                                               e.getMessage() ) )
                               .build();
        }

        return Uni.createFrom().item( response );
    }

    private static class ErrorResponseInfo
    {
        @JsonProperty( "status_code" )
        private final String statusCode;

        @JsonProperty( "reason" )
        private final String reason;

        @JsonCreator
        ErrorResponseInfo( @JsonProperty( "status_code" ) final String statusCode,
                           @JsonProperty( "reason" ) final String reason )
        {
            this.statusCode = statusCode;
            this.reason = reason;
        }

        public String getStatusCode()
        {
            return statusCode;
        }

        public String getReason()
        {
            return reason;
        }
    }

}
