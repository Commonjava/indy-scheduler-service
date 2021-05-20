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
import org.apache.cassandra.utils.ConcurrentBiMap;
import org.commonjava.indy.service.scheduler.event.ScheduleEvent;
import org.commonjava.indy.service.scheduler.event.kafka.KafkaEventUtils;
import org.commonjava.indy.service.scheduler.ftests.profile.ISPNFunctionProfile;
import org.commonjava.indy.service.scheduler.jaxrs.SchedulerInfo;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static org.commonjava.indy.service.scheduler.event.ScheduleEventType.TRIGGER;
import static org.commonjava.indy.service.scheduler.testutil.TestUtil.prepareCustomizedMapper;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@QuarkusTest
@TestProfile( ISPNFunctionProfile.class )
//@QuarkusTestResource( KafkaResourceLifecycleManager.class )
@Tag( "function" )
@Disabled
public class ScheduleExpirationTest
        extends AbstractSchedulerTest
{
    protected final ObjectMapper mapper = prepareCustomizedMapper();

    private final static String EVENT_KEY = "event";

    private final Map<String, ScheduleEvent> eventMap = new ConcurrentBiMap<>();

    @Test
    public void test()
            throws Exception
    {
        final String name = newName();
        final SchedulerInfo info = new SchedulerInfo().setKey( "testKey" )
                                                      .setJobName( name )
                                                      .setPayload(
                                                              Collections.singletonMap( "payload1", "payloadVal1" ) )
                                                      .setTimeoutSeconds( 2 );
        final String json = mapper.writeValueAsString( info );
        given().body( json )
               .contentType( APPLICATION_JSON )
               .post( API_BASE )
               .then()
               .statusCode( CREATED.getStatusCode() );

        validateEvent( false );
        Thread.sleep( 4000 );
        validateEvent( true );
    }

    @Incoming( KafkaEventUtils.CHANNEL_STORE )
    @Acknowledgment( Acknowledgment.Strategy.PRE_PROCESSING )
    public void accept( final ScheduleEvent event )
    {
        eventMap.put( EVENT_KEY, event );
    }

    private void validateEvent( final boolean existed )
    {
        ScheduleEvent event = eventMap.get( EVENT_KEY );
        if ( existed )
        {
            assertThat( event, notNullValue() );
            assertThat( event.getEventType(), equalTo( TRIGGER ) );
        }
        else
        {
            assertThat( event, nullValue() );
        }
    }

}
