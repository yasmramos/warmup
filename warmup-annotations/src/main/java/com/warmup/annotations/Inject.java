package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor or field for injection by the Warmup container.
 */
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    /**
     * The name of the dependency to inject. If not specified, the type is used.
     */
    String value() default "";
}
