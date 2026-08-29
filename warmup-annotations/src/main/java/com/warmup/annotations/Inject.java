package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor, field, method, or parameter for injection by the Warmup container.
 * 
 * When applied to a method, the container will invoke the method after constructing
 * the bean instance, resolving all method parameters as dependencies. This supports
 * setter injection and other method-based injection patterns.
 */
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    /**
     * The name of the dependency to inject. If not specified, the type is used.
     */
    String value() default "";
}
