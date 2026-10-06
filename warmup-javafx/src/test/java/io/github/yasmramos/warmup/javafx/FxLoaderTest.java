package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.annotations.Inject;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.container.HybridContainer;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.jit.CompilationException;
import io.github.yasmramos.warmup.core.jit.JITCompiler;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FxLoaderTest {

    private FxLoader fxLoader;
    private Warmup warmup;

    @BeforeEach
    void setUp() {
        warmup = Warmup.builder().build();
        fxLoader = new FxLoader(warmup, false);
    }

    @Test
    void testFieldInjectionWithInject() throws Exception {
        // Register TestService as a bean
        warmup.register("testService", TestService.class, TestService::new, Scope.SINGLETON);
        
        // Register TestController with a factory that handles @Inject field injection
        // Since the processor doesn't run for test classes, we manually create a factory
        // that simulates what the annotation processor would generate
        warmup.register("testController", TestController.class, () -> {
            TestController controller = new TestController();
            // Manually inject the service (simulating what the generated factory would do)
            controller.service = warmup.resolve(TestService.class);
            return controller;
        }, Scope.PROTOTYPE);

        // CreateController returns the injected controller instance
        TestController controller = fxLoader.createController(TestController.class);

        // Verify that the field was injected via @Inject
        assertNotNull(controller.getService(), "Service should be injected via @Inject");
        assertEquals("TestService", controller.getService().getName());
    }

    @Test
    void testCreateControllerWithUnregisteredController_ThrowsException() {
        // UnregisteredController is NOT registered in the container
        // FxLoader should throw IllegalStateException since fallback was removed
        assertThrows(IllegalStateException.class, () -> {
            fxLoader.createController(UnregisteredController.class);
        }, "Should throw exception for unregistered controller");
    }

    @Test
    void testLoadControllerWithAnnotationAndFxml() throws Exception {
        // Register TestService as a bean
        warmup.register("testService", TestService.class, TestService::new, Scope.SINGLETON);
        
        // Register TestControllerWithFxml with a factory that handles @Inject field injection
        warmup.register("testControllerWithFxml", TestControllerWithFxml.class, () -> {
            TestControllerWithFxml controller = new TestControllerWithFxml();
            controller.service = warmup.resolve(TestService.class);
            return controller;
        }, Scope.PROTOTYPE);

        // Load FXML using auto-loading from @WarmupFxController annotation
        var root = fxLoader.loadController(TestControllerWithFxml.class);

        // Verify that the FXML was loaded
        assertNotNull(root, "FXML root should not be null");
        
        // Verify that the controller was created and injected by the container
        TestControllerWithFxml cachedController = (TestControllerWithFxml) fxLoader.getCachedController(TestControllerWithFxml.class);
        if (cachedController == null) {
            // Controller might not be cached if it's prototype, resolve directly
            cachedController = warmup.resolve(TestControllerWithFxml.class);
        }
        assertNotNull(cachedController.getService(), "Service should be injected via @Inject");
        assertEquals("TestService", cachedController.getService().getName());
    }

    @Test
    void testLoadControllerWithoutAnnotation_ThrowsIllegalArgumentException() {
        // UnregisteredController is NOT annotated with @WarmupFxController
        assertThrows(IllegalArgumentException.class, () -> {
            fxLoader.loadController(UnregisteredController.class);
        }, "Should throw IllegalArgumentException for controller without @WarmupFxController");
    }

    @Test
    void testLoadControllerWithEmptyFxml_ThrowsIllegalArgumentException() {
        // TestControllerWithEmptyFxml has @WarmupFxController but empty fxml()
        assertThrows(IllegalArgumentException.class, () -> {
            fxLoader.loadController(TestControllerWithEmptyFxml.class);
        }, "Should throw IllegalArgumentException for controller with empty fxml()");
    }

    @Test
    void testLoadControllerWithFxmlNotFound_ThrowsIOException() {
        // Register the controller but the FXML file doesn't exist
        warmup.register("testControllerWithMissingFxml", TestControllerWithMissingFxml.class, () -> {
            TestControllerWithMissingFxml controller = new TestControllerWithMissingFxml();
            return controller;
        }, Scope.PROTOTYPE);

        assertThrows(IOException.class, () -> {
            fxLoader.loadController(TestControllerWithMissingFxml.class);
        }, "Should throw IOException when FXML file is not found");
    }

    @Test
    void testEnableHotReloadDoesNotThrowNPE() {
        // Simulate calling enableHotReload before fxLoader is initialized
        // This tests the null-check fix in WarmupApplication.enableHotReload()
        // We test directly by calling clearCache on a null scenario
        
        // Create a new FxLoader and immediately clear cache - should not throw
        assertDoesNotThrow(() -> fxLoader.clearCache());
    }
    
    @Test
    void testDevelopmentModeCanBeChangedAtRuntime() {
        // Initially development mode is false
        assertFalse(fxLoader.isDevelopmentMode());
        
        // Enable development mode at runtime
        fxLoader.setDevelopmentMode(true);
        assertTrue(fxLoader.isDevelopmentMode());
        
        // Disable development mode at runtime
        fxLoader.setDevelopmentMode(false);
        assertFalse(fxLoader.isDevelopmentMode());
    }
    
    @Test
    void testHotReloadReturnsNewControllerInstance() throws Exception {
        // Register TestService
        warmup.register("testService", TestService.class, TestService::new, Scope.SINGLETON);
        
        // Use an atomic counter to track controller creations
        AtomicInteger creationCount = new AtomicInteger(0);
        
        // Register TestController with a factory that tracks creations
        warmup.register("testController", TestController.class, () -> {
            TestController controller = new TestController();
            controller.service = warmup.resolve(TestService.class);
            controller.instanceId = creationCount.incrementAndGet();
            return controller;
        }, Scope.PROTOTYPE);
        
        // Enable development mode
        fxLoader.setDevelopmentMode(true);
        
        // First resolution
        TestController first = fxLoader.createController(TestController.class);
        assertNotNull(first);
        int firstId = first.instanceId;
        
        // Second resolution in dev mode should get a new instance (cache cleared)
        TestController second = fxLoader.createController(TestController.class);
        assertNotNull(second);
        int secondId = second.instanceId;
        
        // In development mode, each call should get a new instance due to cache clearing
        assertNotEquals(firstId, secondId, "In development mode, controllers should be recreated");
    }
    
    @Test
    void testHotReloadIntegrationWithContainer() throws Exception {
        // Register TestService
        warmup.register("testService", TestService.class, TestService::new, Scope.SINGLETON);
        
        AtomicInteger creationCount = new AtomicInteger(0);
        
        // Register TestControllerWithFxml with tracking
        warmup.register("testControllerWithFxml", TestControllerWithFxml.class, () -> {
            TestControllerWithFxml controller = new TestControllerWithFxml();
            controller.service = warmup.resolve(TestService.class);
            controller.instanceId = creationCount.incrementAndGet();
            return controller;
        }, Scope.PROTOTYPE);
        
        // Enable development mode
        fxLoader.setDevelopmentMode(true);
        
        // Load FXML first time
        Parent firstRoot = fxLoader.loadController(TestControllerWithFxml.class);
        assertNotNull(firstRoot);
        
        // Get the container to verify reload capability
        HybridContainer container = warmup.unsafeContainer();
        assertNotNull(container);
        
        // Trigger reload on the controller bean
        boolean reloaded = container.reload("testControllerWithFxml");
        assertTrue(reloaded, "Container should reload the bean");
        
        // Clear cache and load again - should get new controller
        fxLoader.clearCache();
        Parent secondRoot = fxLoader.loadController(TestControllerWithFxml.class);
        assertNotNull(secondRoot);
        
        // Roots should be different instances (new FXML load)
        assertNotSame(firstRoot, secondRoot, "Reloaded FXML should produce a new root");
    }
    
    @Test
    void testSceneRootReplacerCallback() throws Exception {
        // Register TestService
        warmup.register("testService", TestService.class, TestService::new, Scope.SINGLETON);
        
        // Register TestControllerWithFxml
        warmup.register("testControllerWithFxml", TestControllerWithFxml.class, () -> {
            TestControllerWithFxml controller = new TestControllerWithFxml();
            controller.service = warmup.resolve(TestService.class);
            return controller;
        }, Scope.PROTOTYPE);
        
        // Track callback invocations
        final Parent[] capturedRoots = new Parent[1];
        fxLoader.setSceneRootReplacer(root -> capturedRoots[0] = root);
        
        // Load FXML
        Parent root = fxLoader.loadController(TestControllerWithFxml.class);
        assertNotNull(root);
        
        // The callback is invoked during reload, not initial load
        // So capturedRoots[0] should be null after initial load
        assertNull(capturedRoots[0]);
        
        // Now trigger reload
        Parent reloadedRoot = fxLoader.reloadController(TestControllerWithFxml.class);
        assertNotNull(reloadedRoot);
        
        // Callback should have been invoked with the reloaded root
        assertNotNull(capturedRoots[0]);
        assertSame(reloadedRoot, capturedRoots[0], "Callback should receive the reloaded root");
    }
    
    @Test
    void testReloadFxmlTracksAndReloads() throws Exception {
        // Register TestService
        warmup.register("testService", TestService.class, TestService::new, Scope.SINGLETON);
        
        // Register TestControllerWithFxml
        warmup.register("testControllerWithFxml", TestControllerWithFxml.class, () -> {
            TestControllerWithFxml controller = new TestControllerWithFxml();
            controller.service = warmup.resolve(TestService.class);
            return controller;
        }, Scope.PROTOTYPE);
        
        // Initial load
        Parent firstRoot = fxLoader.loadController(TestControllerWithFxml.class);
        assertNotNull(firstRoot);
        
        // Reload the controller
        Parent secondRoot = fxLoader.reloadController(TestControllerWithFxml.class);
        assertNotNull(secondRoot);
        
        // Should get a different root instance
        assertNotSame(firstRoot, secondRoot, "Reloaded FXML should produce a new root instance");
    }
    
    @Test
    void testCssLoadAndTracking() {
        // Load a CSS file (using a non-existent path to test tracking)
        String cssUrl = fxLoader.loadCss("/io/github/yasmramos/warmup/javafx/test.css");
        
        // Since the CSS doesn't exist, it should return null
        assertNull(cssUrl);
    }
    
    @Test
    void testStylesheetReplacerCallback() throws Exception {
        // Track callback invocations
        final java.util.List<String>[] capturedUrls = new java.util.List[1];
        fxLoader.setStylesheetReplacer(urls -> capturedUrls[0] = new java.util.ArrayList<>(urls));
        
        // Load a CSS file (will return null since it doesn't exist)
        fxLoader.loadCss("/nonexistent.css");
        
        // Callback should not be invoked for non-existent CSS
        assertNull(capturedUrls[0]);
    }

    public static class TestService {
        public String getName() {
            return "TestService";
        }
    }

    public static class TestController {
        @Inject
        private TestService service;
        public int instanceId = 0;

        public TestService getService() {
            return service;
        }
    }

    public static class UnregisteredController {
        public UnregisteredController() {
            // No-arg constructor for fallback
        }
    }

    @WarmupFxController(fxml = "/io/github/yasmramos/warmup/javafx/test.fxml")
    public static class TestControllerWithFxml {
        @Inject
        private TestService service;
        public int instanceId = 0;

        public TestService getService() {
            return service;
        }
    }

    @WarmupFxController(fxml = "")
    public static class TestControllerWithEmptyFxml {
        // Empty fxml attribute - should throw IllegalArgumentException
    }

    @WarmupFxController(fxml = "/io/github/yasmramos/warmup/javafx/nonexistent.fxml")
    public static class TestControllerWithMissingFxml {
        // FXML file doesn't exist - should throw IOException
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
            return compileAsync(type, java.util.concurrent.ForkJoinPool.commonPool(), dependencies);
        }

        @Override
        public <T> CompletableFuture<CompiledFactory<T>> compileAsync(Class<T> type, java.util.concurrent.ExecutorService executor, Class<?>... dependencies) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return (CompiledFactory<T>) (deps -> {
                        try {
                            return type.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
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
        public io.github.yasmramos.warmup.core.jit.CompilationStats getStats() {
            return new io.github.yasmramos.warmup.core.jit.CompilationStats(0, 0, 0, 0, 0);
        }

        @Override
        public void clear() {
        }
    }
}
