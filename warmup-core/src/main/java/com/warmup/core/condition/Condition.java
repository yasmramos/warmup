package com.warmup.core.condition;

/**
 * Contract for conditional bean registration.
 * 
 * <p>Implement this interface to create custom conditions that determine whether
 * a bean should be registered in the container. Conditions are evaluated during
 * container initialization, before any beans are created.</p>
 * 
 * <p>A condition can check configuration properties, active profiles, classpath
 * resources, or any other environmental factor to decide if a bean should be
 * included.</p>
 * 
 * <pre>
 * {@code
 * public class DatabasePresentCondition implements Condition {
 *     &#64;Override
 *     public boolean matches(ConditionContext context) {
 *         // Check if a database URL is configured
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
 * <p>Multiple conditions can be specified on a bean. All conditions must return
 * {@code true} for the bean to be registered.</p>
 * 
 * @see Conditional
 * @see ConditionContext
 */
@FunctionalInterface
public interface Condition {
    
    /**
     * Evaluates whether this condition matches the given context.
     * 
     * @param context the condition context providing access to environment and properties
     * @return true if the condition matches and the bean should be registered, false otherwise
     */
    boolean matches(ConditionContext context);
}
