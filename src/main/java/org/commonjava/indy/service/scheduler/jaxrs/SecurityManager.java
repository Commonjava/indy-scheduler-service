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

import org.commonjava.indy.service.scheduler.config.KeycloakConfiguration;
import org.jboss.resteasy.spi.HttpRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * Created by jdcasey on 9/3/15.
 */
@ApplicationScoped
public class SecurityManager
{

    @Inject
    KeycloakConfiguration config;

    public String getUser( SecurityContext context, HttpRequest request )
    {
        if ( !config.isEnabled() )
        {
            return request.getRemoteHost();
        }

        if ( context == null )
        {
            return request.getRemoteHost();
        }

        Principal userPrincipal = context.getUserPrincipal();
        if ( userPrincipal == null )
        {
            return request.getRemoteHost();
        }

        String user = userPrincipal.getName();
        return user == null ? request.getRemoteHost() : user;
    }
}
