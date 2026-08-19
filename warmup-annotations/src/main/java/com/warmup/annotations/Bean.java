package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a bean managed by the Warmup container.
 * The container will generate a factory for creating instances of this bean.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bean {
    /**
     * The name of the bean. If not specified, the simple class name is used.
     */
    String value() default "";

    /**
     * The scope of the bean. Defaults to SINGLETON.
     */
    Scope scope() default Scope.SINGLETON;

    enum Scope {
        SINGLETON,
        PROTOTYPE,
        CUSTOM
    }
}
