package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a prototype bean managed by the Warmup container.
 * 
 * This is a stereotype annotation that implies {@link @Bean} with PROTOTYPE scope.
 * The container will generate a factory for creating a new instance of this bean
 * on each resolution request.
 * 
 * Example:
 * <pre>{@code
 * @Prototype
 * public class RequestHandler {
 *     private final Context context;
 *     
 *     @Inject
 *     public RequestHandler(Context context) {
 *         this.context = context;
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prototype {
    /**
     * Optional name for the bean. If not specified, the simple class name is used.
     */
    String value() default "";
}
