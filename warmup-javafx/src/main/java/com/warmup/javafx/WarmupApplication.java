package com.warmup.javafx;

import com.warmup.core.container.HybridContainer;
import com.warmup.core.scope.Scope;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Base application class for JavaFX applications using Warmup DI.
 * Automatically initializes the container and provides lifecycle hooks.
 * 
 * Usage:
 * <pre>
 * public class MyApp extends WarmupApplication {
 *     {@code @Override}
 *     protected void configure(HybridContainer container) {
 *         container.register("service", MyService.class, MyService::new, Scope.SINGLETON);
 *     }
 *     
 *     {@code @Override}
 *     protected void onStart(Stage stage) {
 *         // Initialize UI
 *     }
 * }
 * </pre>
 */
public abstract class WarmupApplication extends Application {

    protected HybridContainer container;
    protected FxLoader fxLoader;

    @Override
    public void init() throws Exception {
        // Initialize container
        container = createContainer();
        
        // Configure beans (implemented by subclass)
        configure(container);
        
        // Initialize FxLoader
        boolean devMode = Boolean.getBoolean("warmup.dev.mode");
        fxLoader = new FxLoader(container, devMode);
        
        // Call custom init hook
        onInit();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Call custom start hook
        onStart(primaryStage);
    }

    @Override
    public void stop() throws Exception {
        // Call custom stop hook
        onStop();
        
        // Shutdown container
        if (container != null) {
            container.shutdown();
        }
    }

    /**
     * Creates and configures the HybridContainer.
     * Override for custom container configuration.
     *
     * @return configured HybridContainer
     */
    protected HybridContainer createContainer() {
        com.warmup.asm.AsmJITCompiler jitCompiler = new com.warmup.asm.AsmJITCompiler();
        return new HybridContainer(jitCompiler, false);
    }

    /**
     * Configure beans in the container.
     * Must be implemented by subclass.
     * 
     * @param container the HybridContainer to configure
     */
    protected abstract void configure(HybridContainer container);

    /**
     * Called after container initialization but before UI start.
     * Override for custom initialization logic.
     */
    protected void onInit() throws Exception {
        // Default: do nothing
    }

    /**
     * Called when the application starts.
     * Override to set up the UI.
     * 
     * @param stage primary stage
     */
    protected void onStart(Stage stage) throws Exception {
        // Default: do nothing
    }

    /**
     * Called when the application stops.
     * Override for cleanup logic.
     */
    protected void onStop() throws Exception {
        // Default: do nothing
    }

    /**
     * Get the HybridContainer instance.
     * 
     * @return the container
     */
    public HybridContainer getContainer() {
        return container;
    }

    /**
     * Get the FxLoader instance for loading FXML views.
     * 
     * @return the FxLoader
     */
    public FxLoader getFxLoader() {
        return fxLoader;
    }

    /**
     * Enable hot-reload mode for development.
     * Call this in your configure method during development.
     * 
     * @param container the container
     */
    protected void enableHotReload(HybridContainer container) {
        System.setProperty("warmup.dev.mode", "true");
        if (fxLoader != null) {
            fxLoader.clearCache();
        }
    }
}
