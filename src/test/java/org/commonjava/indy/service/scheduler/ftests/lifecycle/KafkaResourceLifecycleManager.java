package org.commonjava.indy.service.scheduler.ftests.lifecycle;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;

import java.util.Collections;
import java.util.Map;

public class KafkaResourceLifecycleManager
        implements QuarkusTestResourceLifecycleManager
{

    private final KafkaContainer kafka = new KafkaContainer();

    @Override
    public Map<String, String> start() {
        kafka.start();
        return Collections.singletonMap( "kafka.bootstrap.servers", kafka.getBootstrapServers());
    }

    @Override
    public void stop() {
        kafka.close();
    }
}