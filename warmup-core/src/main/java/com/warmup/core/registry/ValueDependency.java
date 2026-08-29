package com.warmup.core.registry;

/**
 * Represents a configuration value dependency to be resolved via PropertyResolver.
 * 
 * <p>This class is used as a marker in the dependencies array of BeanDefinition
 * to indicate that a particular dependency should be resolved as a configuration
 * value rather than as a bean reference.</p>
 * 
 * @param expression the placeholder expression (e.g., "${key}" or "${key:default}")
 * @param targetType the target type for type conversion (String, int, boolean, etc.)
 * @author yasmramos
 * @since 1.0
 */
public record ValueDependency(String expression, Class<?> targetType) {
    
    /**
     * Creates a ValueDependency with the given expression and target type.
     * 
     * @param expression the placeholder expression
     * @param targetType the target type for conversion
     */
    public ValueDependency {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression cannot be null or blank");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("Target type cannot be null");
        }
    }
}
