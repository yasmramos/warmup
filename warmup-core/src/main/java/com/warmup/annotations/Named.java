package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier annotation to specify which bean should be injected by name.
 * 
 * <p>Use this annotation when multiple beans of the same type exist and you need
 * to explicitly select which one to inject. It can be applied to fields, constructor
 * parameters, and method parameters.</p>
 * 
 * <pre>
 * {@code
 * @Component
 * public class UserService {
 *     private final PaymentProcessor processor;
 *     
 *     @Inject
 *     public UserService(@Named("stripe") PaymentProcessor processor) {
 *         this.processor = processor;
 *     }
 * }
 * }
 * </pre>
 * 
 * @see Primary
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Named {
    /**
     * The name of the bean to inject.
     * @return the bean name
     */
    String value();
}
