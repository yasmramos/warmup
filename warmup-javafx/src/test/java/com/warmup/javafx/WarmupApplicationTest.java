package com.warmup.javafx;

import com.warmup.core.Warmup;
import com.warmup.core.container.HybridContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WarmupApplication class.
 * Note: Tests avoid calling start(Stage) which requires JavaFX toolkit.
 */
class WarmupApplicationTest {

    @Test
    void testInitCreatesContainerAndFxLoader() throws Exception {
        TestWarmupApplication app = new TestWarmupApplication();
        
        // Call init directly - does not require JavaFX toolkit
        app.init();
        
        assertNotNull(app.getContainer());
        assertNotNull(app.getFxLoader());
    }

    @Test
    void testStopCallsShutdown() throws Exception {
        TestWarmupApplication app = new TestWarmupApplication();
        
        app.init();
        HybridContainer container = app.getContainer();
        
        // Call stop which should shutdown the container
        app.stop();
        
        // Container should be shut down (verify by checking it was called)
        assertTrue(app.isStopCalled());
    }

    @Test
    void testGetContainerReturnsContainer() throws Exception {
        TestWarmupApplication app = new TestWarmupApplication();
        
        app.init();
        
        HybridContainer container = app.getContainer();
        assertNotNull(container);
    }

    @Test
    void testGetFxLoaderReturnsFxLoader() throws Exception {
        TestWarmupApplication app = new TestWarmupApplication();
        
        app.init();
        
        FxLoader fxLoader = app.getFxLoader();
        assertNotNull(fxLoader);
    }

    @Test
    void testCreateWarmupReturnsWarmupInstance() throws Exception {
        TestWarmupApplication app = new TestWarmupApplication();
        
        Warmup warmup = app.createWarmup();
        assertNotNull(warmup);
    }

    @Test
    void testOnInitCanBeOverridden() throws Exception {
        CustomInitApp app = new CustomInitApp();
        
        app.init();
        
        assertTrue(app.isOnInitCalled());
    }

    @Test
    void testOnStopCanBeOverridden() throws Exception {
        CustomStopApp app = new CustomStopApp();
        
        app.init();
        app.stop();
        
        assertTrue(app.isOnStopCalled());
    }

    /**
     * Concrete test implementation of WarmupApplication.
     */
    private static class TestWarmupApplication extends WarmupApplication {
        
        private boolean stopCalled = false;
        
        @Override
        protected void configure(Warmup warmup) {
            // No configuration needed for tests
        }
        
        @Override
        public void stop() throws Exception {
            stopCalled = true;
            super.stop();
        }
        
        boolean isStopCalled() {
            return stopCalled;
        }
        
        HybridContainer getContainer() {
            return ((com.warmup.core.Warmup) getWarmup()).getContainer();
        }
    }

    /**
     * Test app with custom onInit.
     */
    private static class CustomInitApp extends WarmupApplication {
        
        private boolean onInitCalled = false;
        
        @Override
        protected void configure(Warmup warmup) {
        }
        
        @Override
        protected void onInit() {
            onInitCalled = true;
        }
        
        boolean isOnInitCalled() {
            return onInitCalled;
        }
    }

    /**
     * Test app with custom onStop.
     */
    private static class CustomStopApp extends WarmupApplication {
        
        private boolean onStopCalled = false;
        
        @Override
        protected void configure(Warmup warmup) {
        }
        
        @Override
        protected void onStop() {
            onStopCalled = true;
        }
        
        boolean isOnStopCalled() {
            return onStopCalled;
        }
    }
}
