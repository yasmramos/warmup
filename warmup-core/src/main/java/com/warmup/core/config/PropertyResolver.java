package com.warmup.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Property resolver that aggregates multiple {@link PropertySource}s and resolves
 * placeholder expressions with support for default values and type conversion.
 * 
 * <p>Placeholder syntax: {@code ${key}} or {@code ${key:defaultValue}}.</p>
 * 
 * @author yasmramos
 * @since 1.0
 */
public class PropertyResolver {
    
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\{([^}:]+)(?::(.*))?\\}$");
    
    private final List<PropertySource> propertySources = new ArrayList<>();
    
    /**
     * Adds a property source to the end of the chain (lowest priority).
     * 
     * @param source the property source to add
     * @return this resolver for chaining
     */
    public PropertyResolver addPropertySource(PropertySource source) {
        Objects.requireNonNull(source, "PropertySource cannot be null");
        this.propertySources.add(source);
        return this;
    }
    
    /**
     * Alias for addPropertySource for convenience.
     * 
     * @param source the property source to add
     * @return this resolver for chaining
     */
    public PropertyResolver addSource(PropertySource source) {
        return addPropertySource(source);
    }
    
    /**
     * Gets the raw string value for a placeholder expression.
     * 
     * @param expression the placeholder expression (e.g., "${key}" or "${key:default}")
     * @return the resolved value, or null if not found and no default is specified
     * @throws IllegalArgumentException if the expression format is invalid
     */
    public String resolve(String expression) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid placeholder expression: " + expression);
        }
        
        String key = matcher.group(1);
        String defaultValue = matcher.group(2);
        
        // Try to resolve from each property source in order
        for (PropertySource source : propertySources) {
            String value = source.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        
        // Return default value if provided
        return defaultValue;
    }
    
    /**
     * Resolves a placeholder expression to a String.
     * 
     * @param expression the placeholder expression
     * @return the resolved String value
     * @throws IllegalStateException if the value cannot be resolved and no default is provided
     */
    public String resolveString(String expression) {
        String value = resolve(expression);
        if (value == null) {
            throw new IllegalStateException("Cannot resolve property: " + expression);
        }
        return value;
    }
    
    /**
     * Resolves a placeholder expression to an int.
     * 
     * @param expression the placeholder expression
     * @return the resolved int value
     * @throws IllegalStateException if the value cannot be resolved
     * @throws NumberFormatException if the value cannot be parsed as int
     */
    public int resolveInt(String expression) {
        String value = resolveString(expression);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Cannot convert '" + value + "' to int for expression: " + expression);
        }
    }
    
    /**
     * Resolves a placeholder expression to a long.
     * 
     * @param expression the placeholder expression
     * @return the resolved long value
     * @throws IllegalStateException if the value cannot be resolved
     * @throws NumberFormatException if the value cannot be parsed as long
     */
    public long resolveLong(String expression) {
        String value = resolveString(expression);
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Cannot convert '" + value + "' to long for expression: " + expression);
        }
    }
    
    /**
     * Resolves a placeholder expression to a boolean.
     * 
     * @param expression the placeholder expression
     * @return the resolved boolean value
     * @throws IllegalStateException if the value cannot be resolved
     */
    public boolean resolveBoolean(String expression) {
        String value = resolveString(expression);
        String trimmed = value.trim().toLowerCase();
        if ("true".equals(trimmed) || "yes".equals(trimmed) || "1".equals(trimmed)) {
            return true;
        }
        if ("false".equals(trimmed) || "no".equals(trimmed) || "0".equals(trimmed)) {
            return false;
        }
        throw new IllegalArgumentException("Cannot convert '" + value + "' to boolean for expression: " + expression);
    }
    
    /**
     * Resolves a placeholder expression to a double.
     * 
     * @param expression the placeholder expression
     * @return the resolved double value
     * @throws IllegalStateException if the value cannot be resolved
     * @throws NumberFormatException if the value cannot be parsed as double
     */
    public double resolveDouble(String expression) {
        String value = resolveString(expression);
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Cannot convert '" + value + "' to double for expression: " + expression);
        }
    }
    
    /**
     * Resolves a placeholder expression to an enum value.
     * 
     * @param expression the placeholder expression
     * @param enumType the enum type
     * @param <T> the enum type
     * @return the resolved enum value
     * @throws IllegalStateException if the value cannot be resolved
     * @throws IllegalArgumentException if the value is not a valid enum constant
     */
    public <T extends Enum<T>> T resolveEnum(String expression, Class<T> enumType) {
        String value = resolveString(expression);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Value '" + value + "' is not a valid " + enumType.getSimpleName() + 
                " for expression: " + expression, e);
        }
    }
    
    /**
     * Checks if a placeholder expression can be resolved.
     * 
     * @param expression the placeholder expression
     * @return true if the property exists in any source or has a default value
     */
    public boolean canResolve(String expression) {
        try {
            return resolve(expression) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Gets the raw string value for a property key directly (without placeholder syntax).
     * 
     * @param key the property key
     * @return the resolved value, or null if not found
     */
    public String getProperty(String key) {
        for (PropertySource source : propertySources) {
            String value = source.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
