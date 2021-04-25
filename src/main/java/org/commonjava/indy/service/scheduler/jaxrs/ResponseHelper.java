/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/service-parent)
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.commonjava.indy.service.scheduler.config.MetricsConfiguration;
import org.commonjava.indy.service.scheduler.data.metrics.DefaultMetricsManager;
import org.commonjava.indy.service.scheduler.exception.IndyWorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.function.Consumer;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@ApplicationScoped
public class ResponseHelper
{

    private final static Logger LOGGER = LoggerFactory.getLogger( ResponseHelper.class );

    @Inject
    ObjectMapper mapper;

    @Inject
    DefaultMetricsManager metricsManager;

    @Inject
    MetricsConfiguration metricsConfig;

    public Response formatRedirect( final URI uri )
    {
        ResponseBuilder builder = Response.status( Status.MOVED_PERMANENTLY ).location( uri );

        return builder.build();
    }

    public Response formatCreatedResponseWithJsonEntity( final URI location, final Object dto )
    {
        return formatCreatedResponseWithJsonEntity( location, dto, null );
    }

    public Response formatCreatedResponseWithJsonEntity( final URI location, final Object dto,
                                                         final Consumer<ResponseBuilder> builderModifier )
    {
        ResponseBuilder builder;
        if ( dto == null )
        {
            builder = Response.noContent();
        }
        else
        {
            builder = Response.created( location )
                              .entity( new DTOStreamingOutput( mapper, dto, metricsManager, metricsConfig ) )
                              .type( APPLICATION_JSON );
        }

        if ( builderModifier != null )
        {
            builderModifier.accept( builder );
        }

        return builder.build();
    }

    public Response formatOkResponseWithJsonEntity( final Object dto )
    {
        return formatOkResponseWithJsonEntity( dto, null );
    }

    public Response formatOkResponseWithJsonEntity( final Object dto, final Consumer<ResponseBuilder> builderModifier )
    {
        if ( dto == null )
        {
            return Response.noContent().build();
        }

        ResponseBuilder builder =
                Response.ok( new DTOStreamingOutput( mapper, dto, metricsManager, metricsConfig ), APPLICATION_JSON );

        if ( builderModifier != null )
        {
            builderModifier.accept( builder );
        }

        return builder.build();
    }

    public Response formatOkResponseWithEntity( final Object output, final String contentType,
                                                final Consumer<ResponseBuilder> builderModifier )
    {
        ResponseBuilder builder = Response.ok( output ).type( contentType );
        if ( builderModifier != null )
        {
            builderModifier.accept( builder );
        }

        return builder.build();
    }

    public Response formatBadRequestResponse( final String error, final Consumer<ResponseBuilder> builderModifier )
    {
        final String msg = "{\"error\": \"" + error + "\"}\n";
        ResponseBuilder builder = Response.status( Status.BAD_REQUEST ).type( APPLICATION_JSON ).entity( msg );
        if ( builderModifier != null )
        {
            builderModifier.accept( builder );
        }

        return builder.build();
    }

    public Response formatBadRequestResponse( final String error )
    {
        return formatBadRequestResponse( error, null );
    }

    public Response formatResponse( final Throwable error )
    {
        return formulateResponse( 0, error, null, false, null );
    }

    public Response formatResponse( final Throwable error, final String message )
    {
        return formulateResponse( 0, error, message, false, null );
    }

    private Response formulateResponse( final int statusCode, final Throwable error, final String message,
                                        final boolean throwIt, Consumer<ResponseBuilder> builderModifier )
    {
        final String id = generateErrorId();
        final String msg = formatEntity( id, error, message ).toString();
        Status code = Status.INTERNAL_SERVER_ERROR;

        if ( statusCode > 0 )
        {
            code = Status.fromStatusCode( statusCode );
            LOGGER.debug( "got error code from parameter: {}", code );
        }
        else if ( ( error instanceof IndyWorkflowException ) && ( (IndyWorkflowException) error ).getStatus() > 0 )
        {
            final int sc = ( (IndyWorkflowException) error ).getStatus();
            LOGGER.debug( "got error code from exception: {}", sc );
            code = Status.fromStatusCode( sc );
        }

        // if this is a server error, let's promote the log level. Otherwise, keep it in the background.
        if ( code.getStatusCode() > 499 )
        {
            LOGGER.error( "Sending error response: {} {}\n{}", code.getStatusCode(), code.getReasonPhrase(), msg );
        }
        else
        {
            LOGGER.debug( "Sending response: {} {}\n{}", code.getStatusCode(), code.getReasonPhrase(), msg );
        }

        //TODO: this is used for mdc tracking, think about this later
        //        setContext( HTTP_STATUS, String.valueOf( code.getStatusCode() ) );

        ResponseBuilder builder = Response.status( code ).type( MediaType.TEXT_PLAIN ).entity( msg );

        if ( builderModifier != null )
        {
            builderModifier.accept( builder );
        }

        Response response = builder.build();

        if ( throwIt )
        {
            throw new WebApplicationException( error, response );
        }

        return response;
    }

    public String generateErrorId()
    {
        return DigestUtils.sha256Hex( Thread.currentThread().getName() );

        //+ "@" + new SimpleDateFormat( "yyyy-MM-ddThhmmss.nnnZ" ).format( new Date() );
    }

    public CharSequence formatEntity( final Throwable error )
    {
        return formatEntity( generateErrorId(), error, null );
    }

    public CharSequence formatEntity( final String id, final Throwable error )
    {
        return formatEntity( id, error, null );
    }

    public CharSequence formatEntity( final Throwable error, final String message )
    {
        return formatEntity( generateErrorId(), error, message );
    }

    public CharSequence formatEntity( final String id, final Throwable error, final String message )
    {
        final StringWriter sw = new StringWriter();
        sw.append( "Id: " ).append( id ).append( "\n" );
        if ( message != null )
        {
            sw.append( "Message: " ).append( message ).append( "\n" );
        }

        if ( error != null )
        {
            sw.append( error.getMessage() );

            final Throwable cause = error.getCause();
            if ( cause != null )
            {
                sw.append( "Error:\n\n" );
                cause.printStackTrace( new PrintWriter( sw ) );
            }

            sw.write( '\n' );
        }

        return sw.toString();
    }

}
