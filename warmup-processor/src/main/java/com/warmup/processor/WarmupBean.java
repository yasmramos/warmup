package com.warmup.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a DI bean managed by Warmup container.
 * 
 * The annotation processor generates a CompiledFactory implementation
 * for zero-overhead instantiation at runtime.
 * 
 * Example:
 * ```java
 * @WarmupBean
 * public class UserService {
 *     private final UserRepository repository;
 *     
 *     @WarmupInject
 *     public UserService(UserRepository repository) {
 *         this.repository = repository;
 *     }
 * }
 * ```
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface WarmupBean {
    /**
     * Optional bean name. If not specified, uses the class simple name
     * with first letter lowercased (e.g., UserService -> userService).
     */
    String name() default "";
    
    /**
     * Bean scope. Defaults to singleton.
     */
    String scope() default "SINGLETON";
    
    /**
     * Whether this bean is primary (preferred when multiple candidates exist).
     */
    boolean primary() default false;
}
