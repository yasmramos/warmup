package com.warmup.core.jit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompiledFactory interface default methods.
 */
@DisplayName("CompiledFactory Tests")
class CompiledFactoryTest {

    /**
     * Test implementation of CompiledFactory for testing purposes.
     */
    private static class TestCompiledFactory<T> implements CompiledFactory<T> {
        private final T instance;
        private final Class<T> beanType;
        private final int dependencyCount;

        TestCompiledFactory(T instance, Class<T> beanType, int dependencyCount) {
            this.instance = instance;
            this.beanType = beanType;
            this.dependencyCount = dependencyCount;
        }

        @Override
        public T create(Object... dependencies) {
            return instance;
        }

        @Override
        public Class<T> getBeanType() {
            return beanType;
        }

        @Override
        public int getDependencyCount() {
            return dependencyCount;
        }
    }

    /**
     * Test implementation with custom wire method.
     */
    private static class WiringTestFactory<T> implements CompiledFactory<T> {
        private final T instance;
        private CompiledFactory<?>[] wiredDependencies;
        private boolean wireCalled = false;

        WiringTestFactory(T instance) {
            this.instance = instance;
        }

        @Override
        public T create(Object... dependencies) {
            return instance;
        }

        @Override
        public void wire(CompiledFactory<?>[] dependencyFactories) {
            this.wiredDependencies = dependencyFactories;
            this.wireCalled = true;
        }

        boolean isWireCalled() {
            return wireCalled;
        }

        CompiledFactory<?>[] getWiredDependencies() {
            return wiredDependencies;
        }
    }

    @Test
    @DisplayName("Default getBeanType returns null")
    void testDefaultGetBeanTypeReturnsNull() {
        CompiledFactory<String> factory = dependencies -> "test";

        assertNull(factory.getBeanType(), 
            "Default getBeanType() should return null");
    }

    @Test
    @DisplayName("Default getDependencyCount returns zero")
    void testDefaultGetDependencyCountReturnsZero() {
        CompiledFactory<String> factory = dependencies -> "test";

        assertEquals(0, factory.getDependencyCount(),
            "Default getDependencyCount() should return 0");
    }

    @Test
    @DisplayName("Default wire does nothing")
    void testDefaultWireDoesNothing() {
        CompiledFactory<String> factory = dependencies -> "test";
        CompiledFactory<?>[] mockDependencies = new CompiledFactory[0];

        assertDoesNotThrow(() -> factory.wire(mockDependencies),
            "Default wire() should not throw exceptions");
    }

    @Test
    @DisplayName("Default get delegates to create with no dependencies")
    void testDefaultGetDelegatesToCreate() {
        String expectedInstance = "test-instance";
        CompiledFactory<String> factory = new TestCompiledFactory<>(expectedInstance, String.class, 0);

        String result = factory.get();

        assertEquals(expectedInstance, result,
            "get() should return the same instance as create()");
    }

    @Test
    @DisplayName("Custom getBeanType returns correct type")
    void testCustomGetBeanType() {
        Class<String> beanType = String.class;
        CompiledFactory<String> factory = new TestCompiledFactory<>("test", beanType, 0);

        assertEquals(beanType, factory.getBeanType(),
            "getBeanType() should return the configured bean type");
    }

    @Test
    @DisplayName("Custom getDependencyCount returns correct count")
    void testCustomGetDependencyCount() {
        int expectedCount = 5;
        CompiledFactory<String> factory = new TestCompiledFactory<>("test", String.class, expectedCount);

        assertEquals(expectedCount, factory.getDependencyCount(),
            "getDependencyCount() should return the configured dependency count");
    }

    @Test
    @DisplayName("Wire method is called with dependencies")
    void testWireMethodWithDependencies() {
        WiringTestFactory<String> factory = new WiringTestFactory<>("test");
        CompiledFactory<?>[] dependencies = new CompiledFactory<?>[] {
            new TestCompiledFactory<>("dep1", String.class, 0),
            new TestCompiledFactory<>(Integer.valueOf(42), Integer.class, 0)
        };

        factory.wire(dependencies);

        assertTrue(factory.isWireCalled(),
            "wire() should be called on factories that support wiring");
        assertEquals(dependencies.length, factory.getWiredDependencies().length,
            "Wired dependencies should match provided dependencies");
    }

    @Test
    @DisplayName("Create method receives dependencies correctly")
    void testCreateReceivesDependencies() {
        final Object[][] capturedDependencies = new Object[1][];
        CompiledFactory<String> factory = dependencies -> {
            capturedDependencies[0] = dependencies;
            return "result";
        };

        Object dep1 = "dependency1";
        Object dep2 = Integer.valueOf(42);
        factory.create(dep1, dep2);

        assertNotNull(capturedDependencies[0],
            "Dependencies should be passed to create method");
        assertArrayEquals(new Object[]{dep1, dep2}, capturedDependencies[0],
            "Dependencies should be passed in correct order");
    }

    @Test
    @DisplayName("Multiple factories can be created independently")
    void testMultipleFactoriesIndependence() {
        CompiledFactory<String> factory1 = new TestCompiledFactory<>("instance1", String.class, 2);
        CompiledFactory<Integer> factory2 = new TestCompiledFactory<>(Integer.valueOf(42), Integer.class, 3);

        assertEquals("instance1", factory1.create());
        assertEquals(Integer.valueOf(42), factory2.create());
        assertEquals(2, factory1.getDependencyCount());
        assertEquals(3, factory2.getDependencyCount());
        assertEquals(String.class, factory1.getBeanType());
        assertEquals(Integer.class, factory2.getBeanType());
    }

    @Test
    @DisplayName("Factory with null instance returns null")
    void testFactoryWithNullInstance() {
        CompiledFactory<String> factory = new TestCompiledFactory<>(null, String.class, 0);

        assertNull(factory.create(),
            "Factory should be able to return null instances");
    }

    @Test
    @DisplayName("Empty dependencies array is handled correctly")
    void testEmptyDependenciesArray() {
        CompiledFactory<String> factory = dependencies -> {
            assertNotNull(dependencies, "Dependencies array should not be null");
            assertEquals(0, dependencies.length, "Dependencies array should be empty");
            return "success";
        };

        assertEquals("success", factory.create());
    }

    @Test
    @DisplayName("Wire with null dependencies is handled")
    void testWireWithNullDependencies() {
        WiringTestFactory<String> factory = new WiringTestFactory<>("test");

        assertDoesNotThrow(() -> factory.wire(null),
            "Wire should handle null dependencies gracefully");
    }

    @Test
    @DisplayName("Factory can be used as functional interface")
    void testFunctionalInterfaceUsage() {
        CompiledFactory<String> factory = deps -> "functional";

        assertEquals("functional", factory.create(),
            "Factory should work as functional interface");
    }

    @Test
    @DisplayName("Chaining default methods works correctly")
    void testChainingDefaultMethods() {
        CompiledFactory<String> factory = new TestCompiledFactory<>("test", String.class, 1);

        // Chain multiple default method calls
        Class<String> beanType = factory.getBeanType();
        int depCount = factory.getDependencyCount();
        String instance = factory.get();

        assertEquals(String.class, beanType);
        assertEquals(1, depCount);
        assertEquals("test", instance);
    }
}
