package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a singleton bean managed by the Warmup container.
 * 
 * This is a stereotype annotation that implies {@link @Bean} with SINGLETON scope.
 * The container will generate a factory for creating a single shared instance of this bean.
 * 
 * Example:
 * <pre>{@code
 * @Singleton
 * public class UserService {
 *     private final Repository repository;
 *     
 *     @Inject
 *     public UserService(Repository repository) {
 *         this.repository = repository;
 *     }
 * }
 * }</pre>
 * 
 * Note: This annotation has the same name as {@code jakarta.inject.Singleton} and 
 * {@code io.avaje.inject.Singleton}. Use fully qualified imports or explicit package 
 * references to avoid conflicts when using multiple DI frameworks.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Singleton {
    /**
     * Optional name for the bean. If not specified, the simple class name is used.
     */
    String value() default "";
}
