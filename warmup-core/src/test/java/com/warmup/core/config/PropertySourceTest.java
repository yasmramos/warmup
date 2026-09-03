package com.warmup.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PropertySource interface implementations.
 */
class PropertySourceTest {

    @Test
    void testSystemPropertiesPropertySource() {
        System.setProperty("test.property", "testValue");
        
        PropertySource source = new SystemPropertiesPropertySource();
        
        assertTrue(source.contains("test.property"));
        assertEquals("testValue", source.getProperty("test.property"));
        assertFalse(source.contains("nonexistent.property"));
        assertNull(source.getProperty("nonexistent.property"));
        
        System.clearProperty("test.property");
    }

    @Test
    void testSystemEnvironmentPropertySource() {
        PropertySource source = new SystemEnvironmentPropertySource();
        
        // PATH should exist in most systems
        boolean hasPath = source.contains("PATH");
        if (hasPath) {
            assertNotNull(source.getProperty("PATH"));
        }
        
        assertFalse(source.contains("NONEXISTENT_ENV_VAR_12345"));
        assertNull(source.getProperty("NONEXISTENT_ENV_VAR_12345"));
    }

    @Test
    void testPropertiesFilePropertySourceWithExistingFile() throws java.io.IOException {
        // Create a temporary properties file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".properties");
        try {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("key1", "value1");
            props.setProperty("key2", "value2");
            
            try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(tempFile)) {
                props.store(os, "test properties");
            }
            
            PropertySource source = new PropertiesFilePropertySource(tempFile);
            
            assertTrue(source.contains("key1"));
            assertEquals("value1", source.getProperty("key1"));
            assertTrue(source.contains("key2"));
            assertEquals("value2", source.getProperty("key2"));
            assertFalse(source.contains("nonexistent"));
            assertNull(source.getProperty("nonexistent"));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testPropertiesFilePropertySourceWithClasspathResource() {
        // Test with a non-existent resource to verify exception handling
        assertThrows(IllegalArgumentException.class, () -> {
            new PropertiesFilePropertySource("nonexistent.properties");
        });
    }
}
