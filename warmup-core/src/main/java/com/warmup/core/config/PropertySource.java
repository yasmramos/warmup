package com.warmup.core.config;

/**
 * Source of configuration properties.
 * 
 * <p>Implementations provide property values from different sources such as
 * system environment, system properties, or properties files.</p>
 * 
 * @author yasmramos
 * @since 1.0
 */
public interface PropertySource {
    
    /**
     * Gets the value of the specified property.
     * 
     * @param key the property key
     * @return the property value, or {@code null} if not found
     */
    String getProperty(String key);
    
    /**
     * Checks if this source contains the specified property.
     * 
     * @param key the property key
     * @return {@code true} if the property exists, {@code false} otherwise
     */
    boolean contains(String key);
    
    /**
     * Gets the name of this property source (for debugging and ordering).
     * 
     * @return the source name
     */
    String getName();
}
