package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.core.Warmup;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Base application class for JavaFX applications using Warmup DI. Automatically
 * initializes the container and provides lifecycle hooks.
 *
 * Usage:
 * <pre>
 * public class MyApp extends WarmupApplication {
 *     {@code @Override}
    protected void registerBeans(Warmup warmup) {
        warmup.register("service", MyService.class, MyService::new, Scope.SINGLETON);
    }

    {@code @Override}
 *     protected void onStart(Stage stage) {
 *         // Initialize UI
 *     }
 * }
 * </pre>
 *
 * Note: The default implementation uses Warmup.builder().build() for simple
 * setup. Override createWarmup() for custom configuration using
 * Warmup.builder().
 */
public abstract class WarmupApplication extends Application {

    private Warmup warmup;
    private FxLoader fxLoader;

    @Override
    public void init() throws Exception {
        // Initialize container via Warmup facade (or custom override)
        warmup = createWarmup().build();

        // Configure beans (implemented by subclass)
        registerBeans(warmup);

        // Initialize FxLoader
        boolean devMode = warmup.isDevelopmentMode() || Boolean.getBoolean("warmup.dev.mode");
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
        try {
            // Call custom stop hook
            onStop();
        } finally {
            try {
                // Shutdown container
                if (warmup != null) {
                    warmup.shutdown();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Configure beans in the Warmup container. Must be implemented by subclass.
     *
     * @param warmup the Warmup instance to registerBeans
     */
    protected abstract void registerBeans(Warmup warmup);

    /**
     * Called after container initialization but before UI start. Override for
     * custom initialization logic.
     *
     * @throws java.lang.Exception
     */
    protected void onInit() throws Exception {
        // Default: do nothing
    }

    /**
     * Called when the application starts. Override to set up the UI.
     *
     * @param stage primary stage
     * @throws java.lang.Exception
     */
    protected abstract void onStart(Stage stage) throws Exception;

    /**
     * Called when the application stops. Override for cleanup logic.
     *
     * @throws java.lang.Exception
     */
    protected abstract void onStop() throws Exception;

    /**
     * Get the Warmup instance.
     *
     * @return the Warmup instance
     */
    public Warmup getWarmup() {
        return warmup;
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
     * Enable hot-reload mode for development. This method: 1. Sets the
     * warmup.dev.mode system property 2. Updates the FxLoader's developmentMode
     * flag at runtime via setter 3. Clears the controller cache to force fresh
     * controller creation
     *
     * Call this during development to enable full hot-reload functionality.
     */
    protected void enableHotReload() {
        warmup.setDevelopmentMode(true);
        if (fxLoader != null) {
            fxLoader.setDevelopmentMode(true);
            fxLoader.clearCache();
        }
    }

    /**
     * Register a loaded view for hot-reload. When the view's FXML changes, the
     * stage's scene root is replaced with the newly loaded root.
     *
     * <p>Several stages/views can be tracked independently with this overload;
     * prefer it over {@link #registerSceneForHotReload(Stage, Parent)}.</p>
     *
     * @param stage the stage displaying the view
     * @param view the loaded view (from {@code fxLoader.loadView(...)})
     */
    protected void registerSceneForHotReload(Stage stage, FxView<?> view) {
        if (fxLoader == null || stage == null || view == null) {
            return;
        }
        fxLoader.registerRootReplacer(view.fxmlPath(), newRoot -> Platform.runLater(() -> {
            if (stage.getScene() == null) {
                stage.setScene(new Scene(newRoot));
            } else {
                stage.getScene().setRoot(newRoot);
            }
        }));
    }

    /**
     * Register the current scene root for hot-reload support. This allows the
     * FxLoader to replace the scene root when FXML is reloaded.
     *
     * <p>This registers a global replacer: only the last call wins. Use
     * {@link #registerSceneForHotReload(Stage, FxView)} to track several views
     * independently.</p>
     *
     * @param stage the primary stage containing the scene
     * @param root the current root node of the scene
     */
    protected void registerSceneForHotReload(Stage stage, Parent root) {
        if (fxLoader == null) {
            return;
        }
        if (root == null) {
            throw new IllegalArgumentException("root cannot be null");
        }
        fxLoader.setSceneRootReplacer(newRoot -> Platform.runLater(() -> {
            if (stage.getScene() == null) {
                stage.setScene(new Scene(newRoot));
            } else {
                stage.getScene().setRoot(newRoot);
            }
        }));
    }

    public Warmup.Builder createWarmup() {
        return Warmup.builder();
    }

    protected boolean isDevelopmentMode() {
        return warmup.isDevelopmentMode();
    }
}
