package com.warmup.core.config;

/**
 * Property source that reads from system environment variables.
 * 
 * @author yasmramos
 * @since 1.0
 */
public class SystemEnvironmentPropertySource implements PropertySource {
    
    private static final String NAME = "SystemEnvironment";
    
    @Override
    public String getProperty(String key) {
        return System.getenv(key);
    }
    
    @Override
    public boolean contains(String key) {
        return System.getenv().containsKey(key);
    }
    
    @Override
    public String getName() {
        return NAME;
    }
}
