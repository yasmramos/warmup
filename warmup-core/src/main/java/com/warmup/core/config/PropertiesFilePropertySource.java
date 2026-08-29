package com.warmup.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Property source that reads from a .properties file.
 * 
 * <p>Supports loading from classpath or file system path.</p>
 * 
 * @author yasmramos
 * @since 1.0
 */
public class PropertiesFilePropertySource implements PropertySource {
    
    private final String name;
    private final Properties properties = new Properties();
    
    /**
     * Loads properties from a file on the classpath.
     * 
     * @param classpathResource the classpath resource name (e.g., "config/app.properties")
     * @throws IllegalArgumentException if the resource cannot be found
     */
    public PropertiesFilePropertySource(String classpathResource) {
        this.name = "PropertiesFile:" + classpathResource;
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IllegalArgumentException("Properties file not found on classpath: " + classpathResource);
            }
            properties.load(is);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load properties file: " + classpathResource, e);
        }
    }
    
    /**
     * Loads properties from a file system path.
     * 
     * @param path the file system path
     * @throws IllegalArgumentException if the file cannot be read
     */
    public PropertiesFilePropertySource(java.nio.file.Path path) {
        this.name = "PropertiesFile:" + path.toString();
        try (InputStream is = Files.newInputStream(path)) {
            properties.load(is);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load properties file: " + path, e);
        }
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
        return name;
    }
}
