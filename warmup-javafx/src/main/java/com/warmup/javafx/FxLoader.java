package com.warmup.javafx;

import com.warmup.annotations.WarmupInject;
import com.warmup.core.container.HybridContainer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.lang.reflect.Field;
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
 * - Automatic field injection with @WarmupInject
 * - Circular dependency resolution for UI frameworks
 */
public class FxLoader {

    private final HybridContainer container;
    private final ConcurrentHashMap<String, Object> controllerCache;
    private final boolean developmentMode;

    public FxLoader(HybridContainer container) {
        this(container, false);
    }

    public FxLoader(HybridContainer container, boolean developmentMode) {
        this.container = container;
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
        FXMLLoader loader = new FXMLLoader();
        
        // Resolve FXML URL for proper relative reference resolution
        URL fxmlUrl = getClass().getResource(fxmlPath);
        if (fxmlUrl == null) {
            throw new IOException("FXML not found: " + fxmlPath);
        }
        loader.setLocation(fxmlUrl);
        
        // Set resource bundle for localization (not location!)
        if (resourceBundle != null) {
            loader.setResources(resourceBundle);
        }
        
        // Set controller factory for DI
        loader.setControllerFactory(this::createController);
        
        // Load FXML - setLocation already configured, so use no-arg load() which handles stream internally
        loader.load();
        
        return loader.getRoot();
    }

    /**
     * Create controller with dependency injection.
     * Uses prototype scope for controllers (new instance per request).
     * Falls back to reflective instantiation if controller is not registered in container.
     * 
     * @param clazz controller class
     * @return injected controller instance
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
        
        T controller;
        try {
            // Try to resolve from container (triggers JIT if needed)
            controller = container.resolve(clazz);
        } catch (IllegalStateException e) {
            // Controller not registered - fall back to reflective no-arg instantiation
            controller = createControllerReflectively(clazz);
        }
        
        // Inject fields marked with @WarmupInject
        injectFields(controller);
        
        // Cache if not prototype
        if (!isPrototypeController(clazz)) {
            controllerCache.put(controllerKey, controller);
        }
        
        return controller;
    }

    /**
     * Create controller instance via reflection using no-arg constructor.
     * Used as fallback when controller is not registered in the container.
     * 
     * @param clazz controller class
     * @return new controller instance
     */
    private <T> T createControllerReflectively(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to create controller via reflection: " + clazz.getName() + 
                ". Controller must either be registered in the container or have a public no-arg constructor.",
                e
            );
        }
    }

    /**
     * Inject dependencies into controller fields.
     * 
     * @param controller controller instance
     */
    private void injectFields(Object controller) {
        if (controller == null) {
            return;
        }
        
        Class<?> clazz = controller.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(WarmupInject.class)) {
                    injectField(controller, field);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Inject a single field with dependency resolution.
     * 
     * @param controller owner instance
     * @param field field to inject
     */
    private void injectField(Object controller, Field field) {
        field.setAccessible(true);
        try {
            Object dependency = container.resolve(field.getType());
            field.set(controller, dependency);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to inject field " + field.getName() + " in " + controller.getClass(), 
                e
            );
        }
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
