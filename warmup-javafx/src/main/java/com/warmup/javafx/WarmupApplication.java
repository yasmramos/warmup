package com.warmup.javafx;

import com.warmup.core.Warmup;
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
 *     protected void configure(Warmup warmup) {
 *         warmup.register("service", MyService.class, MyService::new, Scope.SINGLETON);
 *     }
 *     
 *     {@code @Override}
 *     protected void onStart(Stage stage) {
 *         // Initialize UI
 *     }
 * }
 * </pre>
 * 
 * Note: The default implementation uses Warmup.create() for simple setup.
 * Override createWarmup() for custom configuration using Warmup.builder().
 */
public abstract class WarmupApplication extends Application {

    private Warmup warmup;
    protected FxLoader fxLoader;

    @Override
    public void init() throws Exception {
        // Initialize container via Warmup facade (or custom override)
        warmup = createWarmup();
        
        // Configure beans (implemented by subclass)
        configure(warmup);
        
        // Initialize FxLoader
        boolean devMode = Boolean.getBoolean("warmup.dev.mode");
        fxLoader = new FxLoader(warmup, devMode);
        
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
        if (warmup != null) {
            warmup.shutdown();
        }
    }

    /**
     * Creates and configures the Warmup container.
     * Override for custom container configuration using the ergonomic API.
     * 
     * Example:
     * <pre>{@code
     * @Override
     * protected Warmup createWarmup() {
     *     return Warmup.builder()
     *         .diagnostic(true)
     *         .maxPendingCompilations(20)
     *         .build();
     * }
     * }</pre>
     *
     * @return configured Warmup instance
     */
    protected Warmup createWarmup() {
        // Default: use Warmup.create() for simple setup
        // ASM is now embedded in core, so no explicit JIT compiler construction needed
        return Warmup.create();
    }

    /**
     * Configure beans in the Warmup container.
     * Must be implemented by subclass.
     * 
     * @param warmup the Warmup instance to configure
     */
    protected abstract void configure(Warmup warmup);

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
     * Get the Warmup instance.
     * 
     * @return the Warmup instance
     */
    public Warmup getWarmup() {
        return warmup;
    }

    /**
     * Get the underlying HybridContainer for advanced operations.
     * Package-private for testing purposes.
     * 
     * @return the HybridContainer
     */
    com.warmup.core.container.HybridContainer getContainer() {
        return warmup != null ? warmup.unsafeContainer() : null;
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
     * Call this during development to enable controller cache clearing.
     */
    protected void enableHotReload() {
        System.setProperty("warmup.dev.mode", "true");
        if (fxLoader != null) {
            fxLoader.clearCache();
        }
    }
}
