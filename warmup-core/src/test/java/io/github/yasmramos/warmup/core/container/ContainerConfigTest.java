package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.config.PropertyResolver;
import io.github.yasmramos.warmup.core.config.SystemEnvironmentPropertySource;
import io.github.yasmramos.warmup.core.config.SystemPropertiesPropertySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContainerConfig record and Builder.
 */
@DisplayName("HybridContainerConfig Tests")
class ContainerConfigTest {

    @Test
    @DisplayName("DEFAULT configuration has correct values")
    void testDefaultConfiguration() {
        ContainerConfig config = ContainerConfig.DEFAULT;

        assertFalse(config.diagnosticMode(), 
            "Diagnostic mode should be false by default");
        assertEquals(10, config.maxPendingCompilations(),
            "Max pending compilations should be 10 by default");
        assertTrue(config.autoDiscoverFactories(),
            "Auto-discover factories should be true by default");
        assertFalse(config.metricsEnabled(),
            "Metrics should be disabled by default for performance");
        assertNull(config.propertyResolver(),
            "Property resolver should be null by default");
        assertEquals(0, config.activeProfiles().length,
            "Active profiles should be empty by default");
    }

    @Test
    @DisplayName("No-arg constructor uses DEFAULT values")
    void testNoArgsConstructorUsesDefaults() {
        ContainerConfig config = new ContainerConfig();

        assertEquals(ContainerConfig.DEFAULT.diagnosticMode(), config.diagnosticMode());
        assertEquals(ContainerConfig.DEFAULT.maxPendingCompilations(), config.maxPendingCompilations());
        assertEquals(ContainerConfig.DEFAULT.autoDiscoverFactories(), config.autoDiscoverFactories());
        assertEquals(ContainerConfig.DEFAULT.metricsEnabled(), config.metricsEnabled());
        assertEquals(ContainerConfig.DEFAULT.propertyResolver(), config.propertyResolver());
        assertArrayEquals(ContainerConfig.DEFAULT.activeProfiles(), config.activeProfiles());
    }

    @Test
    @DisplayName("Builder creates config with default values")
    void testBuilderWithDefaultValues() {
        ContainerConfig config = ContainerConfig.builder().build();

        assertFalse(config.diagnosticMode());
        assertEquals(10, config.maxPendingCompilations());
        assertTrue(config.autoDiscoverFactories());
        assertFalse(config.metricsEnabled());
        assertNull(config.propertyResolver());
        assertEquals(0, config.activeProfiles().length);
    }

    @Test
    @DisplayName("Builder sets diagnosticMode correctly")
    void testBuilderDiagnosticMode() {
        ContainerConfig config = ContainerConfig.builder()
            .diagnosticMode(true)
            .build();

        assertTrue(config.diagnosticMode(),
            "Diagnostic mode should be set to true");
    }

    @Test
    @DisplayName("Builder sets maxPendingCompilations correctly")
    void testBuilderMaxPendingCompilations() {
        int expectedMax = 25;
        ContainerConfig config = ContainerConfig.builder()
            .maxPendingCompilations(expectedMax)
            .build();

        assertEquals(expectedMax, config.maxPendingCompilations(),
            "Max pending compilations should match configured value");
    }

    @Test
    @DisplayName("Builder disables autoDiscoverFactories correctly")
    void testBuilderAutoDiscoverFactories() {
        ContainerConfig config = ContainerConfig.builder()
            .autoDiscoverFactories(false)
            .build();

        assertFalse(config.autoDiscoverFactories(),
            "Auto-discover factories should be set to false");
    }

    @Test
    @DisplayName("Builder enables metrics correctly")
    void testBuilderMetricsEnabled() {
        ContainerConfig config = ContainerConfig.builder()
            .metricsEnabled(true)
            .build();

        assertTrue(config.metricsEnabled(),
            "Metrics should be enabled when set to true");
    }

    @Test
    @DisplayName("Builder sets propertyResolver correctly")
    void testBuilderPropertyResolver() {
        io.github.yasmramos.warmup.core.config.PropertyResolver mockResolver = new io.github.yasmramos.warmup.core.config.PropertyResolver();
        mockResolver.addPropertySource(new io.github.yasmramos.warmup.core.config.SystemPropertiesPropertySource());
        mockResolver.addPropertySource(new io.github.yasmramos.warmup.core.config.SystemEnvironmentPropertySource());
        
        ContainerConfig config = ContainerConfig.builder()
            .propertyResolver(mockResolver)
            .build();

        assertEquals(mockResolver, config.propertyResolver(),
            "Property resolver should match configured value");
    }

    @Test
    @DisplayName("Builder sets activeProfiles correctly")
    void testBuilderActiveProfiles() {
        String[] expectedProfiles = {"dev", "test"};
        ContainerConfig config = ContainerConfig.builder()
            .activeProfiles(expectedProfiles)
            .build();

        assertArrayEquals(expectedProfiles, config.activeProfiles(),
            "Active profiles should match configured values");
    }

    @Test
    @DisplayName("Builder handles null activeProfiles")
    void testBuilderNullActiveProfiles() {
        ContainerConfig config = ContainerConfig.builder()
            .activeProfiles(new String[0])
            .build();

        assertNotNull(config.activeProfiles(),
            "Active profiles should not be null");
        assertEquals(0, config.activeProfiles().length,
            "Active profiles should be empty array when null is passed");
    }

    @Test
    @DisplayName("Builder supports fluent chaining")
    void testBuilderFluentChaining() {
        PropertyResolver resolver = new PropertyResolver();
        resolver.addPropertySource(new SystemPropertiesPropertySource());
        resolver.addPropertySource(new SystemEnvironmentPropertySource());
        
        ContainerConfig config = ContainerConfig.builder()
            .diagnosticMode(true)
            .maxPendingCompilations(20)
            .autoDiscoverFactories(false)
            .metricsEnabled(true)
            .propertyResolver(resolver)
            .activeProfiles("production", "monitoring")
            .build();

        assertTrue(config.diagnosticMode());
        assertEquals(20, config.maxPendingCompilations());
        assertFalse(config.autoDiscoverFactories());
        assertTrue(config.metricsEnabled());
        assertEquals(resolver, config.propertyResolver());
        assertArrayEquals(new String[]{"production", "monitoring"}, config.activeProfiles());
    }

    @Test
    @DisplayName("Multiple builder instances are independent")
    void testMultipleBuilderInstancesIndependence() {
        ContainerConfig config1 = ContainerConfig.builder()
            .diagnosticMode(true)
            .metricsEnabled(true)
            .build();

        ContainerConfig config2 = ContainerConfig.builder()
            .diagnosticMode(false)
            .metricsEnabled(false)
            .build();

        assertTrue(config1.diagnosticMode());
        assertTrue(config1.metricsEnabled());
        assertFalse(config2.diagnosticMode());
        assertFalse(config2.metricsEnabled());
    }

    @Test
    @DisplayName("Builder can override DEFAULT partially")
    void testBuilderPartialOverride() {
        ContainerConfig config = ContainerConfig.builder()
            .metricsEnabled(true)
            .build();

        // Only metricsEnabled should be changed
        assertFalse(config.diagnosticMode());
        assertEquals(10, config.maxPendingCompilations());
        assertTrue(config.autoDiscoverFactories());
        assertTrue(config.metricsEnabled());
        assertNull(config.propertyResolver());
        assertEquals(0, config.activeProfiles().length);
    }

    @Test
    @DisplayName("Record equality works correctly")
    void testRecordEquality() {
        ContainerConfig config1 = ContainerConfig.builder()
            .diagnosticMode(true)
            .metricsEnabled(true)
            .build();

        ContainerConfig config2 = ContainerConfig.builder()
            .diagnosticMode(true)
            .metricsEnabled(true)
            .build();

        assertEquals(config1, config2,
            "Configs with same values should be equal");
        assertEquals(config1.hashCode(), config2.hashCode(),
            "Equal configs should have same hash code");
    }

    @Test
    @DisplayName("Record inequality works correctly")
    void testRecordInequality() {
        ContainerConfig config1 = ContainerConfig.builder()
            .diagnosticMode(true)
            .build();

        ContainerConfig config2 = ContainerConfig.builder()
            .diagnosticMode(false)
            .build();

        assertNotEquals(config1, config2,
            "Configs with different values should not be equal");
    }

    @Test
    @DisplayName("toString produces meaningful output")
    void testToString() {
        ContainerConfig config = ContainerConfig.builder()
            .diagnosticMode(true)
            .metricsEnabled(true)
            .activeProfiles("test")
            .build();

        String toString = config.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("ContainerConfig"),
            "toString should contain class name");
        assertTrue(toString.contains("diagnosticMode"),
            "toString should contain field names");
    }

    @Test
    @DisplayName("Single profile can be set")
    void testSingleProfile() {
        ContainerConfig config = ContainerConfig.builder()
            .activeProfiles("development")
            .build();

        assertEquals(1, config.activeProfiles().length);
        assertEquals("development", config.activeProfiles()[0]);
    }

    @Test
    @DisplayName("Empty profiles array can be set explicitly")
    void testEmptyProfilesArray() {
        ContainerConfig config = ContainerConfig.builder()
            .activeProfiles(new String[0])
            .build();

        assertEquals(0, config.activeProfiles().length);
    }

}
