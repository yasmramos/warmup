package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a component bean managed by the Warmup container.
 * 
 * This is a stereotype annotation that implies {@link @Bean} with SINGLETON scope.
 * It is semantically equivalent to {@link @Singleton} but uses a different name
 * to avoid conflicts with other DI frameworks.
 * 
 * The container will generate a factory for creating a single shared instance of this bean.
 * 
 * Example:
 * <pre>{@code
 * @Component
 * public class OrderService {
 *     private final PaymentProcessor processor;
 *     
 *     @Inject
 *     public OrderService(PaymentProcessor processor) {
 *         this.processor = processor;
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Component {
    /**
     * Optional name for the bean. If not specified, the simple class name is used.
     */
    String value() default "";
}
