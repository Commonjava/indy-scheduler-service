package org.commonjava.indy.service.scheduler.data;

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * StandaloneManager is only used for local testing & dev mode purpose
 */
@Qualifier
@Target( { ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention( RetentionPolicy.RUNTIME)
@Documented
public @interface StandaloneScheduleManager
{
}
