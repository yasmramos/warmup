package com.warmup.annotations;

import com.warmup.annotations.condition.Condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify one or more conditions for bean registration.
 * 
 * <p>Use this annotation on stereotype classes ({@code @Singleton}, {@code @Component}, 
 * {@code @Prototype}) or on {@code @Bean} methods to specify that the bean should only 
 * be registered in the container if all specified conditions match.</p>
 * 
 * <p>Each condition class must implement the {@link Condition} interface and will be 
 * instantiated via reflection (using a no-arg constructor) during container initialization.
 * All conditions must return {@code true} for the bean to be registered.</p>
 * 
 * <pre>
 * {@code
 * public class DatabasePresentCondition implements Condition {
 *     &#64;Override
 *     public boolean matches(ConditionContext context) {
 *         return context.getPropertyResolver().getProperty("database.url") != null;
 *     }
 * }
 * 
 * @Singleton
 * @Conditional(DatabasePresentCondition.class)
 * public class DatabaseService {
 *     // Only registered if database.url property is set
 * }
 * }
 * </pre>
 * 
 * <p>Conditions can access configuration properties, active profiles, and other 
 * environmental factors through the {@link ConditionContext} provided to the 
 * {@link Condition#matches} method.</p>
 * 
 * @see Condition
 * @see ConditionContext
 * @see Profile
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Conditional {
    /**
     * The condition classes to evaluate.
     * All conditions must match (return true) for the bean to be registered.
     * @return array of condition classes
     */
    Class<? extends Condition>[] value();
}
