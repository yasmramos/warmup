package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a dependency injection point for lazy resolution.
 * 
 * <p>When applied to a field or parameter, the bean will not be instantiated
 * until it is first accessed. This enables breaking circular dependencies
 * and deferring expensive bean creation.</p>
 * 
 * <p>Alternatively, use {@link Provider} for explicit lazy injection.</p>
 * 
 * @see Provider
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Lazy {
}
