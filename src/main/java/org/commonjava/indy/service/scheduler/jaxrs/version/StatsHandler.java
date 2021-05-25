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

import org.commonjava.indy.service.scheduler.jaxrs.ResponseHelper;
import org.commonjava.indy.service.scheduler.model.version.Versioning;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag( name = "Generic Infrastructure Queries (UI Support)",
      description = "Various read-only operations for retrieving information about the system." )
@Path( "/stats" )
public class StatsHandler
{
    private final Logger logger = LoggerFactory.getLogger( this.getClass() );

    @Inject
    Versioning versioning;

    @Inject
    ResponseHelper responseHelper;

    @Operation( summary = "Retrieve versioning information about this APP instance" )
    @APIResponse( responseCode = "200", content = @Content( schema = @Schema( implementation = Versioning.class ) ),
                  description = "The version info of the APIs" )
    @Path( "/version-info" )
    @GET
    @Produces( APPLICATION_JSON )
    public Response getAppVersion()
    {
        return responseHelper.formatOkResponseWithJsonEntity( versioning );
    }

}
