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
package org.commonjava.indy.service.scheduler.jaxrs.version;

import org.commonjava.indy.service.scheduler.model.version.DeprecatedApis;
import org.commonjava.indy.service.scheduler.model.version.Versioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.Response.Status.GONE;
import static org.commonjava.indy.service.scheduler.model.version.Versioning.HEADER_INDY_API_VERSION;
import static org.commonjava.indy.service.scheduler.model.version.Versioning.HEADER_INDY_CUR_API_VERSION;
import static org.commonjava.indy.service.scheduler.model.version.Versioning.HEADER_INDY_MIN_API_VERSION;

/**
 * We do the following steps when we release a new apiVersion ( e.g. 1 to 2 )
 *
 *  1) Update '<apiVersion>' in indy-parent pom.xml. It will update apiVersion in version.properties in
 *  indy-model-core-java during build. This apiVersion will be released along with the client jars.
 *
 *  2) If an api is going to be changed in terms of req/resp message formats or so and we want to support both v1
 *  and v2 users, we need to copy the method to a new one and annotate it with '@Produce "application/indy-v1+json"'.
 *  This method serves old v1 client users. The new method could be placed in a new resource class.
 *
 *  3) We continue working on the v2 version via the original method. This method serves new v2 client users.
 *
 *  4) If no changes to an api, we just leave it as is. All v1 and v2 clients will access it by default.
 *
 *
 *  ## Something behind this versioning strategy ##
 *
 *  When client sends a request to Indy, it loads the apiVersion in the 'version.properties' in the
 *  client jar and add a header 'Indy-API-Version' (e.g., 'Indy-API-Version: 1'). When server receives the request,
 *  it goes through ApiVersioningFilter to adjust 'Accept' headers with the most acceptable type being prioritised.
 *  The servlet container respect the priority and choose the right method to use.
 */
@ApplicationScoped
@Provider
public class ApiVersioningFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    Versioning indyVersioning;

    @Inject
    DeprecatedApis indyDeprecatedApis;

    public ApiVersioningFilter()
    {
    }

    public ApiVersioningFilter( Versioning versioning )
    {
        this.indyVersioning = versioning;
    }

    @Override
    public void filter( ContainerRequestContext requestContext )
            throws IOException
    {
        String reqApiVersion = requestContext.getHeaderString( HEADER_INDY_API_VERSION );
        Optional<DeprecatedApis.DeprecatedApiEntry> deprecatedApiEntry =
                indyDeprecatedApis.getDeprecated( reqApiVersion );

        String deprecatedApiVersion = null;

        if ( deprecatedApiEntry.isPresent() )
        {
            if ( !deprecatedApiEntry.get().isOff() )
            {
                deprecatedApiVersion = deprecatedApiEntry.get().getValue();
            }
        }
        makeRequestVersioned( requestContext, reqApiVersion, deprecatedApiVersion );
    }

    @Override
    public void filter( ContainerRequestContext requestContext, ContainerResponseContext responseContext )
            throws IOException
    {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();

        String reqApiVersion = requestContext.getHeaderString( HEADER_INDY_API_VERSION );

        // Insert 3 headers into outgoing responses
        if ( reqApiVersion != null )
        {
            headers.add( HEADER_INDY_API_VERSION, reqApiVersion );
        }
        headers.add( HEADER_INDY_CUR_API_VERSION, indyVersioning.getApiVersion() );
        headers.add( HEADER_INDY_MIN_API_VERSION, indyDeprecatedApis.getMinApiVersion() );

        Optional<DeprecatedApis.DeprecatedApiEntry> deprecatedApiEntry =
                indyDeprecatedApis.getDeprecated( reqApiVersion );

        if ( deprecatedApiEntry.isPresent() && deprecatedApiEntry.get().isOff() )
        {
            responseContext.setStatus( GONE.getStatusCode() ); // Return 410
        }

    }

    private final static String APPLICATION = "application";

    /**
     * This method is used to adjust
     *
     * If the user requests with header 'Indy-API-Version', we will insert "application/indy-v[X]+json"
     * to accept values where X is the deprecated version most suitable to serve this request, and make it the most
     * acceptable value via ';q=' suffix. Or if no deprecated version were found, we just leave the header as is.
     */
    private void makeRequestVersioned( ContainerRequestContext requestContext, String reqApiVersion,
                                       String deprecatedApiVersion )
    {
        // only care about the "Accept" request header
        List<String> eu = requestContext.getHeaders().get( ACCEPT );
        logger.trace( "Adjust header {}, value: {}", ACCEPT, eu );
        if ( eu == null || eu.isEmpty() )
        {
            logger.trace( "header {} is empty", ACCEPT );
            return;
        }

        String acceptApp = eu.get( 0 );

        String[] tokens = acceptApp.split( "," );
        if ( tokens.length == 0 )
        {
            logger.trace( "header {} is empty", ACCEPT );
            return;
        }

        List<String> values = new ArrayList<>();
        for ( String tok : tokens )
        {
            if ( tok.startsWith( APPLICATION ) ) // adjust, e.g., application/json -> application/indy-v1+json
            {
                logger.trace( "Adjust value {}", tok );
                float priority = 1f;
                String[] kv = tok.split( "/" );
                values.add( APPLICATION + "/indy-v" + deprecatedApiVersion + "+" + kv[1] + ";q=" + priority );
                values.add( tok + ";q=" + getNextPriority( priority ) ); // keep the original value
            }
            else
            {
                logger.trace( "Not adjust value {}", tok );
                values.add( tok );
            }
        }

        logger.trace( "Adjust complete, new values: {}", values );
        requestContext.getHeaders().put( ACCEPT, values );
    }

    private float getNextPriority( float priority )
    {
        return priority - 0.1f;
    }
}
