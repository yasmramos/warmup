package com.warmup.javafx;

import com.warmup.core.Warmup;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JavaFX integration module for Warmup DI framework.
 * Provides lazy loading of controllers with dependency injection.
 * 
 * Features:
 * - Lazy initialization of controllers
 * - Hot-reload support in development mode
 * - Automatic dependency injection via container (no manual reflection)
 * - Circular dependency resolution for UI frameworks
 * - Auto-loading FXML from @WarmupFxController annotation
 */
public class FxLoader {

    private final Warmup warmup;
    private final ConcurrentHashMap<String, Object> controllerCache;
    private final boolean developmentMode;

    public FxLoader(Warmup warmup) {
        this(warmup, false);
    }

    public FxLoader(Warmup warmup, boolean developmentMode) {
        this.warmup = warmup;
        this.controllerCache = new ConcurrentHashMap<>();
        this.developmentMode = developmentMode;
    }

    /**
     * Load FXML with automatic controller injection.
     * 
     * @param fxmlPath path to FXML file
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     */
    public Parent loadFxml(String fxmlPath) throws IOException {
        return loadFxml(fxmlPath, null);
    }

    /**
     * Load FXML with custom controller factory.
     * 
     * @param fxmlPath path to FXML file
     * @param resourceBundle optional resource bundle for localization
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     */
    public Parent loadFxml(String fxmlPath, ResourceBundle resourceBundle) throws IOException {
        return loadFxmlInternal(null, fxmlPath, resourceBundle);
    }

    /**
     * Load FXML automatically from @WarmupFxController annotation.
     * Reads the fxml() attribute from the annotation and loads the associated FXML file.
     * The controller is constructed and injected by the container.
     * 
     * @param controllerClass the controller class annotated with @WarmupFxController
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if controller is not annotated or fxml() is empty
     */
    public <T> Parent loadController(Class<T> controllerClass) throws IOException {
        return loadController(controllerClass, null);
    }

    /**
     * Load FXML automatically from @WarmupFxController annotation with ResourceBundle.
     * Reads the fxml() attribute from the annotation and loads the associated FXML file.
     * The controller is constructed and injected by the container.
     * 
     * @param controllerClass the controller class annotated with @WarmupFxController
     * @param resourceBundle optional resource bundle for localization
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if controller is not annotated or fxml() is empty
     */
    public <T> Parent loadController(Class<T> controllerClass, ResourceBundle resourceBundle) throws IOException {
        WarmupFxController annotation = controllerClass.getAnnotation(WarmupFxController.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Controller class " + controllerClass.getName() + 
                " must be annotated with @WarmupFxController to use auto-loading"
            );
        }
        
        String fxmlPath = annotation.fxml();
        if (fxmlPath.isEmpty()) {
            throw new IllegalArgumentException(
                "Controller class " + controllerClass.getName() + 
                " must declare a non-empty fxml() attribute in @WarmupFxController for auto-loading"
            );
        }
        
        return loadFxmlInternal(controllerClass, fxmlPath, resourceBundle);
    }

    /**
     * Internal method to load FXML, shared by loadFxml and loadController.
     * 
     * @param controllerClass optional controller class for relative path resolution (null for absolute paths)
     * @param fxmlPath path to FXML file (relative to controller class if controllerClass provided, otherwise absolute)
     * @param resourceBundle optional resource bundle for localization
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     */
    private Parent loadFxmlInternal(Class<?> controllerClass, String fxmlPath, ResourceBundle resourceBundle) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        
        // Resolve FXML URL: try relative to controller class first, then as absolute resource
        URL fxmlUrl = null;
        if (controllerClass != null) {
            // Try relative to controller class package
            fxmlUrl = controllerClass.getResource(fxmlPath);
        }
        
        if (fxmlUrl == null) {
            // Try as absolute resource from classpath
            fxmlUrl = getClass().getResource(fxmlPath);
        }
        
        if (fxmlUrl == null) {
            throw new IOException(
                "FXML not found: " + fxmlPath + 
                (controllerClass != null ? " (relative to " + controllerClass.getName() + ")" : "") +
                ". Ensure the FXML file exists in the classpath."
            );
        }
        
        loader.setLocation(fxmlUrl);
        
        // Set resource bundle for localization
        if (resourceBundle != null) {
            loader.setResources(resourceBundle);
        }
        
        // Set controller factory for DI - all controllers are resolved from container
        loader.setControllerFactory(this::createController);
        
        // Load FXML - let FXMLLoader handle the stream internally to avoid file handle leaks
        loader.load();
        
        return loader.getRoot();
    }

    /**
     * Create controller with dependency injection.
     * Uses prototype scope for controllers (new instance per request).
     * Controllers annotated with @WarmupFxController are registered as beans by the annotation processor
     * and resolved directly from the container with full dependency injection.
     * 
     * @param clazz controller class
     * @return injected controller instance
     * @throws IllegalStateException if controller is not registered in the container
     */
    @SuppressWarnings("unchecked")
    public <T> T createController(Class<T> clazz) {
        if (developmentMode) {
            // Clear cache for hot-reload
            controllerCache.clear();
        }
        
        String controllerKey = clazz.getName();
        
        // Check cache for singleton controllers
        if (!isPrototypeController(clazz)) {
            T cached = (T) controllerCache.get(controllerKey);
            if (cached != null) {
                return cached;
            }
        }
        
        // Resolve from container - controllers must be registered as beans via @WarmupFxController
        // The annotation processor generates the factory and registers it, so the container
        // handles all construction and dependency injection automatically.
        T controller = warmup.resolve(clazz);
        
        // Cache if not prototype
        if (!isPrototypeController(clazz)) {
            controllerCache.put(controllerKey, controller);
        }
        
        return controller;
    }

    /**
     * Check if controller should use prototype scope.
     * Default is true for JavaFX controllers.
     * 
     * @param clazz controller class
     * @return true if prototype scope
     */
    private boolean isPrototypeController(Class<?> clazz) {
        WarmupFxController annotation = clazz.getAnnotation(WarmupFxController.class);
        if (annotation != null) {
            return annotation.scope() == com.warmup.core.scope.Scope.PROTOTYPE;
        }
        // Default to prototype for controllers
        return true;
    }

    /**
     * Clear controller cache (useful for hot-reload).
     */
    public void clearCache() {
        controllerCache.clear();
    }

    /**
     * Get cached controller by class.
     * 
     * @param clazz controller class
     * @return cached instance or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedController(Class<T> clazz) {
        return (T) controllerCache.get(clazz.getName());
    }
}
