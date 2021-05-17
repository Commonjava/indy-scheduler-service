/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/indy)
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
package org.commonjava.indy.service.scheduler.ftests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.commonjava.indy.service.scheduler.ftests.profile.ISPNFunctionProfile;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.commonjava.indy.service.scheduler.testutil.TestUtil.prepareCustomizedMapper;

@QuarkusTest
@TestProfile( ISPNFunctionProfile.class )
@Tag( "function" )
public class ScheduleCancelTest
        extends AbstractSchedulerTest
{
    protected final ObjectMapper mapper = prepareCustomizedMapper();

    @Test
    public void test()
            throws Exception
    {
        final String name = newName();
        final SchedulerInfo info = new SchedulerInfo();
        info.setKey( "testKey" );
        info.setJobType( "testType" );
        info.setJobName( name );
        info.setPayload( Collections.singletonMap( "payload1", "payloadVal1" ) );
        info.setTimeoutSeconds( 30 );
        final String json = mapper.writeValueAsString( info );
        //First to create the expiration content
        given().body( json )
               .contentType( APPLICATION_JSON )
               .post( API_BASE )
               .then()
               .statusCode( CREATED.getStatusCode() );
        //Then cancel and validate
        given().queryParam( "key", "testKey" )
               .queryParam( "job_type", "testType" )
               .queryParam( "job_name", name )
               .delete( API_BASE )
               .then()
               .statusCode( NO_CONTENT.getStatusCode() );
    }
}
