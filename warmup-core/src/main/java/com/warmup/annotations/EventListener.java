package com.warmup.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as an event listener.
 * 
 * <p>Methods annotated with {@code @EventListener} will be automatically registered
 * to receive events published by the {@link com.warmup.core.event.ApplicationEventPublisher}.
 * The method must have exactly one parameter, which determines the type of event it listens for.</p>
 * 
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * public class MyBean {
 *     
 *     @EventListener
 *     public void handleUserCreated(UserCreatedEvent event) {
 *         // Handle the event
 *     }
 * }
 * }</pre>
 * 
 * @see com.warmup.core.event.ApplicationEventPublisher
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {
}
