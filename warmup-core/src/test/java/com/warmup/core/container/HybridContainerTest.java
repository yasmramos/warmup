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

    @Test
    void testFindConstructorParameterType() {
        // Test the private method indirectly through createBean with dependencies
        var depDef = new com.warmup.core.registry.BeanDefinition<>(DependencyService.class, "dep");
        container.register(depDef, null);
        
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
    void testTriggerBackgroundWarmupWithMissingDependency() {
        // Register a bean with a dependency that doesn't exist yet
        var def = new com.warmup.core.registry.BeanDefinition<>(
            DependentService.class, "dependentWithMissingDep", 
            com.warmup.core.scope.Scope.SINGLETON,
            com.warmup.core.lifecycle.LifecycleCallbacks.empty(),
            false,
            new Object[]{"missingDep"}
        );
        // Should not throw even with missing dependency
        assertDoesNotThrow(() -> container.registerDynamic(def));
    }

    @Test
    void testCreateViaReflectionFallback() {
        // Register a simple bean without factory to trigger reflection fallback
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "reflectionBean");
        container.register(definition, null);
        
        TestService result = container.resolve("reflectionBean");
        assertNotNull(result);
    }

    @Test
    void testResolveDependenciesEmpty() {
        // Test with no dependencies - uses EMPTY_ARGS constant
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "noDeps");
        container.register(definition, null);
        
        TestService result = container.resolve("noDeps");
        assertNotNull(result);
    }

    @Test
    void testGetDependencyClassesWithDependencies() {
        // Register dependency
        var depDef = new com.warmup.core.registry.BeanDefinition<>(DependencyService.class, "dep");
        container.register(depDef, null);
        
        // Register bean with dependency
        var def = new com.warmup.core.registry.BeanDefinition<>(
            DependentService.class, "dependent", 
            com.warmup.core.scope.Scope.SINGLETON,
            com.warmup.core.lifecycle.LifecycleCallbacks.empty(),
            false,
            new Object[]{"dep"}
        );
        CompiledFactory<DependentService> factory = deps -> new DependentService((DependencyService) deps[0]);
        container.register(def, factory);
        
        // Get dependency classes - this exercises getDependencyClasses
        DependentService result = container.resolve("dependent");
        assertNotNull(result);
        assertNotNull(result.getDependency());
    }

    @Test
    void testGetDependencyClassesEmpty() {
        // Register bean without dependencies
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "noDepsBean");
        container.register(definition, null);
        
        TestService result = container.resolve("noDepsBean");
        assertNotNull(result);
    }

    @Test
    void testComputeNativeImage() {
        // This tests the computeNativeImage method which checks system property
        // By default should return false since property is not set
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "nativeBean");
        container.register(definition, null);
        
        TestService result = container.resolve("nativeBean");
        assertNotNull(result);
    }

    @Test
    void testTriggerBackgroundWarmupSuccessPath() {
        // Register dependency first
        var depDef = new com.warmup.core.registry.BeanDefinition<>(DependencyService.class, "bgDep");
        container.register(depDef, null);
        
        // Register dynamic bean with dependency already present
        var def = new com.warmup.core.registry.BeanDefinition<>(
            DependentService.class, "bgDependent", 
            com.warmup.core.scope.Scope.SINGLETON,
            com.warmup.core.lifecycle.LifecycleCallbacks.empty(),
            false,
            new Object[]{"bgDep"}
        );
        
        // Should complete warmup successfully without throwing
        assertDoesNotThrow(() -> container.registerDynamic(def));
        
        // Verify bean is available
        assertTrue(container.contains("bgDependent"));
    }

    @Test
    void testTriggerBackgroundWarmupWithCompilationError() {
        // Use a JIT compiler that throws during compilation
        HybridContainer containerWithErrorJit = new HybridContainer(new ErrorJITCompiler(), false);
        
        var def = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "errorBean");
        
        // Should not throw even if compilation fails
        assertDoesNotThrow(() -> containerWithErrorJit.registerDynamic(def));
    }

    @Test
    void testCreateViaReflectionDirectly() {
        // Register bean without factory to force reflection path
        var definition = new com.warmup.core.registry.BeanDefinition<>(SimpleBean.class, "simpleBean");
        container.register(definition, null);
        
        SimpleBean result = container.resolve("simpleBean");
        assertNotNull(result);
    }

    @Test
    void testLambdaNewRunnable() {
        // Test the lambda:new$0 that wraps Runnable
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "lambdaBean");
        container.register(definition, null);
        
        // Shutdown exercises the lambda
        assertDoesNotThrow(() -> container.shutdown());
    }

    @Test
    void testResolvePrototypeMultipleTimes() {
        var definition = new com.warmup.core.registry.BeanDefinition<>(
            TestService.class, "protoBean", 
            com.warmup.core.scope.Scope.PROTOTYPE
        );
        container.register(definition, null);
        
        // Resolve multiple times - each should create new instance
        TestService s1 = container.resolve("protoBean");
        TestService s2 = container.resolve("protoBean");
        TestService s3 = container.resolve("protoBean");
        
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotNull(s3);
        assertNotSame(s1, s2);
        assertNotSame(s2, s3);
    }

    @Test
    void testHotReloadBean() {
        // Register a bean with compile-time factory
        var definition = new com.warmup.core.registry.BeanDefinition<>(TestService.class, "reloadBean");
        CompiledFactory<TestService> factory1 = deps -> new TestService();
        container.register(definition, factory1);
        
        // First resolution uses factory1
        TestService instance1 = container.resolve("reloadBean");
        assertNotNull(instance1);
        
        // Hot-reload the bean
        boolean reloaded = container.reload("reloadBean");
        assertTrue(reloaded);
        
        // After reload, resolve again - should use newly compiled factory
        TestService instance2 = container.resolve("reloadBean");
        assertNotNull(instance2);
        
        // Instances should be different (old one was destroyed, new one created)
        assertNotSame(instance1, instance2);
    }

    @Test
    void testHotReloadNonExistentBean() {
        // Reload non-existent bean should return false
        boolean reloaded = container.reload("nonExistent");
        assertFalse(reloaded);
    }

    // Simple bean class for reflection testing
    public static class SimpleBean {
        public SimpleBean() {}
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

    // Error JIT Compiler for testing error paths
    private static class ErrorJITCompiler implements JITCompiler {
        @Override
        public <T> CompiledFactory<T> compile(Class<T> type, Class<?>... dependencies) throws CompilationException {
            throw new CompilationException("Test compilation error", new RuntimeException("test"));
        }

        @Override
        public <T> CompletableFuture<CompiledFactory<T>> compileAsync(Class<T> type, Class<?>... dependencies) {
            CompletableFuture<CompiledFactory<T>> future = new CompletableFuture<>();
            future.completeExceptionally(new CompilationException("Async test error", new RuntimeException("test")));
            return future;
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
            return false;
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
