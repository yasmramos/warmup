package com.warmup.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PropertyResolver class.
 */
class PropertyResolverTest {

    @Test
    void testResolveWithSystemProperties() {
        System.setProperty("test.key", "testValue");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertEquals("testValue", resolver.resolve("${test.key}"));
        
        System.clearProperty("test.key");
    }

    @Test
    void testResolveWithDefaultValue() {
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertEquals("defaultValue", resolver.resolve("${nonexistent.key:defaultValue}"));
    }

    @Test
    void testResolveWithoutDefaultValue() {
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertNull(resolver.resolve("${nonexistent.key}"));
    }

    @Test
    void testResolveString() {
        System.setProperty("test.string", "hello");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertEquals("hello", resolver.resolveString("${test.string}"));
        
        System.clearProperty("test.string");
    }

    @Test
    void testResolveInt() {
        System.setProperty("test.int", "42");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertEquals(42, resolver.resolveInt("${test.int}"));
        
        System.clearProperty("test.int");
    }

    @Test
    void testResolveLong() {
        System.setProperty("test.long", "1234567890");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertEquals(1234567890L, resolver.resolveLong("${test.long}"));
        
        System.clearProperty("test.long");
    }

    @Test
    void testResolveBoolean() {
        System.setProperty("test.bool.true", "true");
        System.setProperty("test.bool.false", "false");
        System.setProperty("test.bool.yes", "yes");
        System.setProperty("test.bool.no", "no");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertTrue(resolver.resolveBoolean("${test.bool.true}"));
        assertFalse(resolver.resolveBoolean("${test.bool.false}"));
        assertTrue(resolver.resolveBoolean("${test.bool.yes}"));
        assertFalse(resolver.resolveBoolean("${test.bool.no}"));
        
        System.clearProperty("test.bool.true");
        System.clearProperty("test.bool.false");
        System.clearProperty("test.bool.yes");
        System.clearProperty("test.bool.no");
    }

    @Test
    void testResolveDouble() {
        System.setProperty("test.double", "3.14159");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertEquals(3.14159, resolver.resolveDouble("${test.double}"), 0.00001);
        
        System.clearProperty("test.double");
    }

    @Test
    void testResolveEnum() {
        System.setProperty("test.enum", "MONDAY");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertEquals(java.time.DayOfWeek.MONDAY, resolver.resolveEnum("${test.enum}", java.time.DayOfWeek.class));
        
        System.clearProperty("test.enum");
    }

    @Test
    void testCanResolve() {
        System.setProperty("test.exists", "value");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        
        assertTrue(resolver.canResolve("${test.exists}"));
        assertTrue(resolver.canResolve("${nonexistent:default}"));
        assertFalse(resolver.canResolve("${nonexistent}"));
        
        System.clearProperty("test.exists");
    }

    @Test
    void testAddSourceAlias() {
        PropertyResolver resolver = new PropertyResolver();
        resolver.addSource(new SystemPropertiesPropertySource());
        
        System.setProperty("test.alias", "aliasValue");
        assertEquals("aliasValue", resolver.resolve("${test.alias}"));
        
        System.clearProperty("test.alias");
    }

    @Test
    void testNullPropertySourceThrowsException() {
        PropertyResolver resolver = new PropertyResolver();
        
        assertThrows(NullPointerException.class, () -> {
            resolver.addPropertySource(null);
        });
    }

    @Test
    void testInvalidPlaceholderFormat() {
        PropertyResolver resolver = new PropertyResolver();
        
        assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolve("invalid format");
        });
    }

    @Test
    void testMultiplePropertySourcesPriority() throws java.io.IOException {
        // Create temporary properties files
        java.nio.file.Path tempFile1 = java.nio.file.Files.createTempFile("test1", ".properties");
        java.nio.file.Path tempFile2 = java.nio.file.Files.createTempFile("test2", ".properties");
        try {
            java.util.Properties props1 = new java.util.Properties();
            props1.setProperty("key", "value1");
            
            java.util.Properties props2 = new java.util.Properties();
            props2.setProperty("key", "value2");
            props2.setProperty("key2", "value2only");
            
            try (java.io.OutputStream os1 = java.nio.file.Files.newOutputStream(tempFile1)) {
                props1.store(os1, "test properties 1");
            }
            try (java.io.OutputStream os2 = java.nio.file.Files.newOutputStream(tempFile2)) {
                props2.store(os2, "test properties 2");
            }
            
            PropertyResolver resolver = new PropertyResolver();
            resolver.addPropertySource(new PropertiesFilePropertySource(tempFile1));
            resolver.addPropertySource(new PropertiesFilePropertySource(tempFile2));
            
            // First source has priority
            assertEquals("value1", resolver.resolve("${key}"));
            // Second source provides fallback for key2
            assertEquals("value2only", resolver.resolve("${key2}"));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile1);
            java.nio.file.Files.deleteIfExists(tempFile2);
        }
    }
}
