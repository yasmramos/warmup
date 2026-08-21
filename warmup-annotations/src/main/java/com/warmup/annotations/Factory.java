package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a configuration class that contains bean producer methods.
 * 
 * A factory class is instantiated once by the container, and its methods annotated
 * with {@link @Bean} produce beans that are managed by the container.
 * 
 * Example:
 * <pre>{@code
 * @Factory
 * public class AppConfig {
 *     
 *     @Bean
 *     public DataSource dataSource() {
 *         return new DataSource();
 *     }
 *     
 *     @Bean
 *     public Service service(Repository repo) {
 *         return new Service(repo);
 *     }
 * }
 * }</pre>
 * 
 * The factory instance is created once and cached (singleton scope by default).
 * Bean methods can have parameters which are resolved as dependencies.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Factory {
    /**
     * Optional name for the factory bean. If not specified, the simple class name is used.
     */
    String value() default "";
}
