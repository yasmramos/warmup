package com.warmup.core.container;

import com.warmup.core.jit.JITCompiler;
import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.jit.CompilationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HybridContainerTest {

    private HybridContainer container;
    private TestJITCompiler jitCompiler;

    @BeforeEach
    void setUp() {
        jitCompiler = new TestJITCompiler();
        container = new HybridContainer(jitCompiler, false);
    }

    @Test
    void testResolveWithCompileTimeFactory() {
        // Register a bean
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "testBean");
        container.register(definition, null);

        TestService result = container.resolve("testBean");
        assertNotNull(result);
    }

    @Test
    void testResolveByType() {
        // Register a bean
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "testService");
        container.register(definition, null);

        TestService result = container.resolve(TestService.class);
        assertNotNull(result);
    }

    @Test
    void testContainsByName() {
        assertFalse(container.contains("nonexistent"));

        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "existing");
        container.register(definition, null);

        assertTrue(container.contains("existing"));
    }

    @Test
    void testContainsByType() {
        assertFalse(container.contains(TestService.class));

        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "testService");
        container.register(definition, null);

        assertTrue(container.contains(TestService.class));
    }

    @Test
    void testRegisterDynamic() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "dynamicBean");
        container.registerDynamic(definition);

        // Should trigger background warmup without throwing
        assertTrue(container.contains("dynamicBean"));
    }

    @Test
    void testShutdown() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "testBean");
        container.register(definition, null);
        
        // Shutdown should not throw exceptions
        assertDoesNotThrow(() -> container.shutdown());
    }

    @Test
    void testGetMetrics() {
        var metrics = container.getMetrics();
        assertNotNull(metrics);
        assertEquals(0, metrics.totalResolutions());
    }

    @Test
    void testGetCompilationStats() {
        var stats = container.getCompilationStats();
        assertNotNull(stats);
    }

    @Test
    void testGetBeanNames() {
        container.register(new com.warmup.core.registry.BeanDefinition<>(TestService.class, "bean1"), null);
        container.register(new com.warmup.core.registry.BeanDefinition<>(TestService.class, "bean2"), null);
        
        var names = container.getBeanNames();
        assertTrue(names.contains("bean1"));
        assertTrue(names.contains("bean2"));
    }

    @Test
    void testRegisterFactory() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "factoryBean");
        CompiledFactory<TestService> factory = deps -> new TestService();
        
        container.register(definition, null);
        container.registerFactory("factoryBean", factory);
        
        // Should use the registered factory
        TestService result = container.resolve("factoryBean");
        assertNotNull(result);
    }

    @Test
    void testGetDiagnostics() {
        // Enable diagnostic mode
        HybridContainer diagnosticContainer = new HybridContainer(jitCompiler, true);
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "diagBean");
        diagnosticContainer.register(definition, null);
        
        diagnosticContainer.resolve("diagBean");
        
        var diagnostics = diagnosticContainer.getDiagnostics();
        assertNotNull(diagnostics);
        assertFalse(diagnostics.isEmpty());
    }

    @Test
    void testResolvePrototypeScope() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(
            TestService.class, "prototypeBean", 
            com.warmup.core.scope.Scope.PROTOTYPE
        );
        container.register(definition, null);
        
        // Resolve twice - should return different instances for prototype
        TestService instance1 = container.resolve("prototypeBean");
        TestService instance2 = container.resolve("prototypeBean");
        
        assertNotNull(instance1);
        assertNotNull(instance2);
        assertNotSame(instance1, instance2);
    }

    @Test
    void testResolveWithDependencies() {
        // Register dependency first
        var depDef = new com.warmup.core.registry.BeanDefinition<>(DependencyService.class, "dep");
        container.register(depDef, null);
        
        // Register bean with dependency - use compile-time factory to avoid reflection issues
        var def = new com.warmup.core.registry.BeanDefinition<>(
            DependentService.class, "dependent", 
            com.warmup.core.scope.Scope.SINGLETON,
            com.warmup.core.lifecycle.LifecycleCallbacks.empty(),
            false,
            new Object[]{"dep"}
        );
        CompiledFactory<DependentService> factory = deps -> new DependentService((DependencyService) deps[0]);
        container.register(def, factory);
        
        DependentService result = container.resolve("dependent");
        assertNotNull(result);
        assertNotNull(result.getDependency());
    }

    @Test
    void testResolveNonExistentBean() {
        assertThrows(IllegalStateException.class, () -> container.resolve("nonExistent"));
    }

    @Test
    void testResolveByTypeNonExistent() {
        assertThrows(IllegalStateException.class, () -> container.resolve(TestService.class));
    }

    @Test
    void testResolveCachedInstance() {
        // First resolve to cache the singleton
        container.register(new com.warmup.core.registry.BeanDefinition<>(TestService.class, "cached"), null);
        TestService instance1 = container.resolve("cached");
        
        // Second resolve should return cached instance (fast path)
        TestService instance2 = container.resolve("cached");
        
        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    @Test
    void testGetMetricsAfterResolutions() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "metricsBean");
        container.register(definition, null);
        
        container.resolve("metricsBean");
        container.resolve("metricsBean");
        
        var metrics = container.getMetrics();
        assertEquals(2, metrics.totalResolutions());
        assertTrue(metrics.averageResolutionTimeNs() >= 0);
        assertTrue(metrics.cacheHitRate() >= 0);
    }

    @Test
    void testRegisterWithCompileTimeFactory() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "factoryBean");
        CompiledFactory<TestService> factory = deps -> new TestService();
        
        container.register(definition, factory);
        
        TestService result = container.resolve("factoryBean");
        assertNotNull(result);
    }

    @Test
    void testRegisterDynamicTriggersWarmup() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "dynamicBean");
        container.registerDynamic(definition);
        
        // Should be registered and warmup triggered without throwing
        assertTrue(container.contains("dynamicBean"));
    }

    public static class TestService {
        private final String name = "test";

        public TestService() {
        }

        public String getName() {
            return name;
        }
    }

    public static class DependencyService {
        public DependencyService() {}
    }

    public static class DependentService {
        private final DependencyService dependency;

        public DependentService(DependencyService dependency) {
            this.dependency = dependency;
        }

        public DependencyService getDependency() {
            return dependency;
        }
    }

    // Test JIT Compiler implementation
    private static class TestJITCompiler implements JITCompiler {
        @Override
        public <T> CompiledFactory<T> compile(Class<T> type, Class<?>... dependencies) throws CompilationException {
            return deps -> {
                try {
                    return type.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }

        @Override
        public <T> CompletableFuture<CompiledFactory<T>> compileAsync(Class<T> type, Class<?>... dependencies) {
            return CompletableFuture.completedFuture(deps -> {
                try {
                    return type.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public boolean hasCompiledFactory(Class<?> beanClass) {
            return false;
        }

        @Override
        public <T> java.util.Optional<CompiledFactory<T>> getCachedFactory(Class<T> beanClass) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean unloadFactory(Class<?> beanClass) {
            return true;
        }

        @Override
        public com.warmup.core.jit.CompilationStats getStats() {
            return new com.warmup.core.jit.CompilationStats(0, 0, 0, 0, 0);
        }

        @Override
        public void clear() {
        }
    }
}
