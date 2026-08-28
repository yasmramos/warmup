package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean as the primary candidate for injection when multiple implementations of the same type exist.
 * 
 * When multiple beans of the same type are registered, the container will inject the primary bean
 * unless a specific bean is requested by name using {@link @Inject @Named(...)}.
 * 
 * This annotation can be applied to:
 * <ul>
 *   <li>Classes annotated with {@link @Singleton} or {@link @Component}</li>
 *   <li>Methods annotated with {@link @Bean} in a {@link @Factory} class</li>
 * </ul>
 * 
 * Example:
 * <pre>{@code
 * interface PaymentProcessor {
 *     void process();
 * }
 * 
 * @Primary
 * @Singleton
 * public class CreditCardProcessor implements PaymentProcessor {
 *     @Override
 *     public void process() {
 *         System.out.println("Processing credit card payment");
 *     }
 * }
 * 
 * @Singleton
 * public class PayPalProcessor implements PaymentProcessor {
 *     @Override
 *     public void process() {
 *         System.out.println("Processing PayPal payment");
 *     }
 * }
 * 
 * // When injecting PaymentProcessor, CreditCardProcessor will be chosen
 * @Singleton
 * public class OrderService {
 *     private final PaymentProcessor processor;
 *     
 *     @Inject
 *     public OrderService(PaymentProcessor processor) {
 *         this.processor = processor; // Injects CreditCardProcessor
 *     }
 * }
 * }</pre>
 * 
 * If no bean is marked as primary and multiple candidates exist, an ambiguity exception will be thrown
 * during resolution.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Primary {
}
