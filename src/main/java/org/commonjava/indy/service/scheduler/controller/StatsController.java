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
package org.commonjava.indy.service.scheduler.controller;

import org.commonjava.indy.service.scheduler.model.version.Versioning;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class StatsController
{

    private static final String ADDONS_LOGIC = "addonsLogic";

    @Inject
    Versioning versioning;

    protected StatsController()
    {
    }

    public StatsController( final Versioning versioning )
    {
        this.versioning = versioning;
    }

    @PostConstruct
    public void init()
    {
    }

    public Versioning getVersionInfo()
    {
        return versioning;
    }

}
