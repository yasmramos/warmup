package com.warmup.annotations.config;

/**
 * Interface for resolving configuration properties.
 * 
 * <p>This interface provides methods to access configuration values from various sources.</p>
 */
public interface PropertyResolver {
    
    /**
     * Gets a property value by key.
     * 
     * @param key the property key
     * @return the property value, or null if not found
     */
    String getProperty(String key);
    
    /**
     * Gets a property value by key with a default fallback.
     * 
     * @param key the property key
     * @param defaultValue the default value if the property is not found
     * @return the property value, or the default value if not found
     */
    String getProperty(String key, String defaultValue);
    
    /**
     * Gets a property as a specific type.
     * 
     * @param key the property key
     * @param targetType the target type
     * @return the property value converted to the target type, or null if not found
     */
    <T> T getProperty(String key, Class<T> targetType);
    
    /**
     * Checks if a property exists.
     * 
     * @param key the property key
     * @return true if the property exists, false otherwise
     */
    boolean containsProperty(String key);
}
