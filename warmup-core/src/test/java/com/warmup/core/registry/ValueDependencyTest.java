package com.warmup.core.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValueDependency class.
 * 
 * @author yasmramos
 * @since 1.0
 */
class ValueDependencyTest {

    @Test
    void testValidValueDependency() {
        ValueDependency dep = new ValueDependency("${app.name}", String.class);
        
        assertEquals("${app.name}", dep.expression());
        assertEquals(String.class, dep.targetType());
    }

    @Test
    void testValidValueDependencyWithIntegerType() {
        ValueDependency dep = new ValueDependency("${app.port:8080}", int.class);
        
        assertEquals("${app.port:8080}", dep.expression());
        assertEquals(int.class, dep.targetType());
    }

    @Test
    void testValidValueDependencyWithBooleanType() {
        ValueDependency dep = new ValueDependency("${app.enabled:true}", boolean.class);
        
        assertEquals("${app.enabled:true}", dep.expression());
        assertEquals(boolean.class, dep.targetType());
    }

    @Test
    void testNullExpressionThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new ValueDependency(null, String.class)
        );
        
        assertEquals("Expression cannot be null or blank", exception.getMessage());
    }

    @Test
    void testBlankExpressionThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new ValueDependency("   ", String.class)
        );
        
        assertEquals("Expression cannot be null or blank", exception.getMessage());
    }

    @Test
    void testEmptyExpressionThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new ValueDependency("", String.class)
        );
        
        assertEquals("Expression cannot be null or blank", exception.getMessage());
    }

    @Test
    void testNullTargetTypeThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new ValueDependency("${app.name}", null)
        );
        
        assertEquals("Target type cannot be null", exception.getMessage());
    }

    @Test
    void testEqualsAndHashCode() {
        ValueDependency dep1 = new ValueDependency("${app.name}", String.class);
        ValueDependency dep2 = new ValueDependency("${app.name}", String.class);
        ValueDependency dep3 = new ValueDependency("${app.version}", String.class);
        
        assertEquals(dep1, dep2);
        assertEquals(dep1.hashCode(), dep2.hashCode());
        assertNotEquals(dep1, dep3);
    }

    @Test
    void testToString() {
        ValueDependency dep = new ValueDependency("${app.name}", String.class);
        String toString = dep.toString();
        
        assertTrue(toString.contains("ValueDependency"));
        assertTrue(toString.contains("${app.name}"));
        assertTrue(toString.contains("String"));
    }

    @Test
    void testValueDependencyInBeanDefinition() {
        // Test that ValueDependency can be used as a dependency in BeanDefinition
        ValueDependency valueDep = new ValueDependency("${app.timeout:30}", int.class);
        
        BeanDefinition<TestService> definition = new BeanDefinition<>(
            TestService.class, 
            "testBean",
            com.warmup.core.scope.Scope.SINGLETON,
            com.warmup.core.lifecycle.LifecycleCallbacks.empty(),
            false,
            new Object[] { valueDep }
        );
        
        assertEquals(1, definition.dependencies().length);
        assertTrue(definition.dependencies()[0] instanceof ValueDependency);
        
        ValueDependency retrieved = (ValueDependency) definition.dependencies()[0];
        assertEquals("${app.timeout:30}", retrieved.expression());
        assertEquals(int.class, retrieved.targetType());
    }

    /**
     * Simple test service class.
     */
    static class TestService {
        private final String name;
        
        TestService(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
    }
}
