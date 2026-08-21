package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a bean producer within a {@link @Factory} configuration class.
 * 
 * The container will invoke the annotated method to create bean instances.
 * Method parameters are resolved as dependencies from the container.
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
 *     @Bean(scope = Scope.PROTOTYPE)
 *     public Service service(Repository repo) {
 *         return new Service(repo);
 *     }
 * }
 * }</pre>
 * 
 * The scope of the bean is determined by the {@link #scope()} attribute.
 * If not specified, defaults to SINGLETON.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bean {
    /**
     * The name of the bean. If not specified, the method name is used.
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
