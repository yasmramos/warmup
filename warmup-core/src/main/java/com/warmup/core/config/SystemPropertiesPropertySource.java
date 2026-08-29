package com.warmup.core.config;

import java.util.Properties;

/**
 * Property source that reads from system properties.
 * 
 * @author yasmramos
 * @since 1.0
 */
public class SystemPropertiesPropertySource implements PropertySource {
    
    private static final String NAME = "SystemProperties";
    private final Properties properties;
    
    public SystemPropertiesPropertySource() {
        this.properties = System.getProperties();
    }
    
    @Override
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    @Override
    public boolean contains(String key) {
        return properties.containsKey(key);
    }
    
    @Override
    public String getName() {
        return NAME;
    }
}
