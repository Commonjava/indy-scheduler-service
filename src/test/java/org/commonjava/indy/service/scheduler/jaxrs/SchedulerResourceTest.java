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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;

@QuarkusTest
@TestProfile( MockTestProfile.class )
public class SchedulerResourceTest
{
    private final static String API = "/api/schedule";

    @Test
    public void testGet()
    {
        given().when().get( API ).then().statusCode( NOT_FOUND.getStatusCode() );
        given().when().param( "key", "testKey" ).get( API ).then().statusCode( OK.getStatusCode() );
    }

    @Test
    public void testSchedule()
    {
        final SchedulerInfo info = new SchedulerInfo().setJobName( "testJob" )
                                                      .setJobType( "testType" )
                                                      .setKey( "testKey" )
                                                      .setPayload(
                                                              Collections.singletonMap( "testPayKey", "testPayVal" ) )
                                                      .setTimeoutSeconds( 30 );
        given().when()
               .body( info )
               .contentType( APPLICATION_JSON )
               .post( API )
               .then()
               .statusCode( CREATED.getStatusCode() );
        info.setKey( "wrongKey" );
        given().when()
               .body( info )
               .contentType( APPLICATION_JSON )
               .post( API )
               .then()
               .statusCode( INTERNAL_SERVER_ERROR.getStatusCode() );
    }

    @Test
    public void testCancel()
    {
        given().when().delete( API ).then().statusCode( INTERNAL_SERVER_ERROR.getStatusCode() );
        given().when()
               .param( "key", "wrongKey" )
               .param( "job_name", "wrongName" )
               .delete( API )
               .then()
               .statusCode( INTERNAL_SERVER_ERROR.getStatusCode() );
        given().when()
               .param( "key", "testKey" )
               .param( "job_name", "testName" )
               .delete( API )
               .then()
               .statusCode( NO_CONTENT.getStatusCode() );
        given().when()
               .param( "key", "nullKey" )
               .param( "job_name", "nullName" )
               .delete( API )
               .then()
               .statusCode( NOT_FOUND.getStatusCode() );
    }
}
