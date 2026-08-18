package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a managed bean in the Warmup container.
 * The annotation processor will generate a CompiledFactory for compile-time resolution.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface WarmupBean {
    /**
     * Optional bean name. If not specified, uses the simple class name.
     */
    String value() default "";
    
    /**
     * Scope of the bean. Default is SINGLETON.
     */
    Scope scope() default Scope.SINGLETON;
    
    enum Scope {
        SINGLETON,
        PROTOTYPE,
        CUSTOM
    }
}
