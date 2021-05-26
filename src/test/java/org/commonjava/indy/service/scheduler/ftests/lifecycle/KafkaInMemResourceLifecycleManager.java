package org.commonjava.indy.service.scheduler.ftests.lifecycle;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;

import java.util.Map;

import static org.commonjava.indy.service.scheduler.event.kafka.KafkaEventUtils.CHANNEL_STORE;

public class KafkaInMemResourceLifecycleManager
        implements QuarkusTestResourceLifecycleManager
{

    @Override
    public Map<String, String> start()
    {
        return InMemoryConnector.switchOutgoingChannelsToInMemory( CHANNEL_STORE );
    }

    @Override
    public void stop()
    {
        InMemoryConnector.clear();
    }
}