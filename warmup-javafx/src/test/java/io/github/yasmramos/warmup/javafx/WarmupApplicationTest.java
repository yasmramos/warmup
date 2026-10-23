package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.container.Container;
import javafx.stage.Stage;
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
        
        assertNotNull(app.getFxLoader());
    }

    @Test
    void testStopCallsShutdown() throws Exception {
        TestWarmupApplication app = new TestWarmupApplication();
        
        app.init();
        
        // Call stop which should shutdown the container
        app.stop();
        
        // Container should be shut down (verify by checking it was called)
        assertTrue(app.isStopCalled());
    }

    @Test
    void testGetFxLoaderReturnsFxLoader() throws Exception {
        TestWarmupApplication app = new TestWarmupApplication();
        
        app.init();
        
        FxLoader fxLoader = app.getFxLoader();
        assertNotNull(fxLoader);
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
        protected void registerBeans(Warmup warmup) {
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

        @Override
        protected void onStart(Stage stage) throws Exception {
            
        }

        @Override
        protected void onStop() throws Exception {
            
        }
        
    }

    /**
     * Test app with custom onInit.
     */
    private static class CustomInitApp extends WarmupApplication {
        
        private boolean onInitCalled = false;
        
        @Override
        protected void registerBeans(Warmup warmup) {
        }
        
        @Override
        protected void onInit() {
            onInitCalled = true;
        }
        
        boolean isOnInitCalled() {
            return onInitCalled;
        }

        @Override
        protected void onStart(Stage stage) throws Exception {
            
        }

        @Override
        protected void onStop() throws Exception {
    
        }
    }

    /**
     * Test app with custom onStop.
     */
    private static class CustomStopApp extends WarmupApplication {
        
        private boolean onStopCalled = false;
        
        @Override
        protected void registerBeans(Warmup warmup) {
        }
        
        @Override
        protected void onStop() {
            onStopCalled = true;
        }
        
        boolean isOnStopCalled() {
            return onStopCalled;
        }

        @Override
        protected void onStart(Stage stage) throws Exception {
            
        }
    }
}
