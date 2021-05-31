package org.commonjava.indy.service.scheduler.jaxrs;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.commonjava.indy.service.scheduler.jaxrs.mock.MockScheduleController;
import org.commonjava.indy.service.scheduler.jaxrs.mock.MockSecurityManager;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MockTestProfile
        implements QuarkusTestProfile
{
    @Override
    public Set<Class<?>> getEnabledAlternatives()
    {
        return Stream.of( MockScheduleController.class, MockSecurityManager.class ).collect( Collectors.toSet() );
    }
}
