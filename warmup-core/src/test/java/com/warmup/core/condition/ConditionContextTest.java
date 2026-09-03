package com.warmup.core.condition;

import com.warmup.core.config.PropertyResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConditionContext class.
 */
class ConditionContextTest {

    @Test
    void testGetPropertyResolver() {
        PropertyResolver resolver = new PropertyResolver();
        ConditionContext context = new ConditionContext(resolver, new String[]{"dev"});
        
        assertSame(resolver, context.getPropertyResolver());
    }

    @Test
    void testGetActiveProfiles() {
        String[] profiles = {"dev", "test"};
        ConditionContext context = new ConditionContext(new PropertyResolver(), profiles);
        
        assertArrayEquals(profiles, context.getActiveProfiles());
    }

    @Test
    void testHasProfileWithExistingProfile() {
        String[] profiles = {"dev", "test", "production"};
        ConditionContext context = new ConditionContext(new PropertyResolver(), profiles);
        
        assertTrue(context.hasProfile("dev"));
        assertTrue(context.hasProfile("test"));
        assertTrue(context.hasProfile("production"));
    }

    @Test
    void testHasProfileWithNonExistingProfile() {
        String[] profiles = {"dev", "test"};
        ConditionContext context = new ConditionContext(new PropertyResolver(), profiles);
        
        assertFalse(context.hasProfile("production"));
        assertFalse(context.hasProfile("staging"));
    }

    @Test
    void testHasProfileWithNullProfiles() {
        ConditionContext context = new ConditionContext(new PropertyResolver(), null);
        
        assertFalse(context.hasProfile("any"));
        assertArrayEquals(new String[0], context.getActiveProfiles());
    }

    @Test
    void testHasProfileWithEmptyProfiles() {
        ConditionContext context = new ConditionContext(new PropertyResolver(), new String[0]);
        
        assertFalse(context.hasProfile("any"));
    }

    @Test
    void testConditionWithPropertyCheck() {
        System.setProperty("feature.enabled", "true");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new com.warmup.core.config.SystemPropertiesPropertySource());
        
        ConditionContext context = new ConditionContext(resolver, new String[]{"dev"});
        
        // Create a condition that checks for a property
        Condition condition = ctx -> ctx.getPropertyResolver().getProperty("feature.enabled") != null;
        
        assertTrue(condition.matches(context));
        
        System.clearProperty("feature.enabled");
    }

    @Test
    void testConditionWithProfileCheck() {
        ConditionContext context = new ConditionContext(new PropertyResolver(), new String[]{"test", "ci"});
        
        // Create a condition that checks for a specific profile
        Condition condition = ctx -> ctx.hasProfile("test");
        
        assertTrue(condition.matches(context));
        
        // Check for non-existing profile
        Condition otherCondition = ctx -> ctx.hasProfile("production");
        assertFalse(otherCondition.matches(context));
    }

    @Test
    void testConditionCombiningPropertyAndProfile() {
        System.setProperty("database.url", "jdbc:h2:mem:test");
        
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new com.warmup.core.config.SystemPropertiesPropertySource());
        
        ConditionContext context = new ConditionContext(resolver, new String[]{"dev", "database"});
        
        // Condition that requires both property and profile
        Condition condition = ctx -> 
            ctx.getPropertyResolver().getProperty("database.url") != null &&
            ctx.hasProfile("database");
        
        assertTrue(condition.matches(context));
        
        System.clearProperty("database.url");
    }
}
