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
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.connectors.InMemorySink;
import org.commonjava.indy.service.scheduler.event.ScheduleEvent;
import org.commonjava.indy.service.scheduler.event.kafka.KafkaEventUtils;
import org.commonjava.indy.service.scheduler.ftests.lifecycle.KafkaInMemResourceLifecycleManager;
import org.commonjava.indy.service.scheduler.ftests.profile.ISPNFunctionProfile;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.commonjava.indy.service.scheduler.event.ScheduleEventType.CANCEL;
import static org.commonjava.indy.service.scheduler.testutil.TestUtil.prepareCustomizedMapper;

@QuarkusTest
@TestProfile( ISPNFunctionProfile.class )
@QuarkusTestResource( KafkaInMemResourceLifecycleManager.class )
@Tag( "function" )
public class ScheduleCancelEventTest
        extends AbstractSchedulerTest
{
    protected final ObjectMapper mapper = prepareCustomizedMapper();

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    public void test()
            throws Exception
    {
        final String name = newName();
        final SchedulerInfo info = new SchedulerInfo().setKey( "testKey" )
                                                      .setJobType( "testType" )
                                                      .setJobName( name )
                                                      .setPayload(
                                                              Collections.singletonMap( "payload1", "payloadVal1" ) )
                                                      .setTimeoutSeconds( 30 );
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

        // Finally check the cancel event
        final InMemorySink<ScheduleEvent> eventsChannel = connector.sink( KafkaEventUtils.CHANNEL_STORE );
        List<? extends Message<ScheduleEvent>> events = eventsChannel.received();
        assertEvents( events, name, CANCEL, "testType", "" );
    }
}
