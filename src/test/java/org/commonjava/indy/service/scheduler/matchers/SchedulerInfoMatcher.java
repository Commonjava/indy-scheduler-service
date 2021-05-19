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
package org.commonjava.indy.service.scheduler.matchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class SchedulerInfoMatcher
        extends BaseMatcher<SchedulerInfo>
{
    private final ObjectMapper objectMapper;

    private final SchedulerInfo expect;

    public SchedulerInfoMatcher( final ObjectMapper objectMapper, final SchedulerInfo expect )
    {
        this.objectMapper = objectMapper;
        this.expect = expect;
    }

    @Override
    public boolean matches( Object actual )
    {
        try
        {
            final SchedulerInfo actualInfo = objectMapper.readValue( actual.toString(), SchedulerInfo.class );
            return expect.equals( actualInfo ) && expect.getPayload().equals( actualInfo.getPayload() );
        }
        catch ( JsonProcessingException e )
        {
            throw new RuntimeException( e );
        }
    }

    @Override
    public void describeTo( Description description )
    {
        try
        {
            description.appendText( String.format( "equals to: %s ", objectMapper.writeValueAsString( expect ) ) );
        }
        catch ( JsonProcessingException e )
        {
            // Ignore
        }

    }
}
