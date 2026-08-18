package com.warmup.javafx;

import com.warmup.annotations.WarmupInject;
import com.warmup.core.container.HybridContainer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
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
     * @param resourceBundle optional resource bundle
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     */
    public Parent loadFxml(String fxmlPath, URL resourceBundle) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        
        if (resourceBundle != null) {
            loader.setLocation(resourceBundle);
        }
        
        // Set controller factory for DI
        loader.setControllerFactory(this::createController);
        
        // Load FXML
        try (var stream = getClass().getResourceAsStream(fxmlPath)) {
            if (stream == null) {
                throw new IOException("FXML not found: " + fxmlPath);
            }
            loader.load(stream);
        }
        
        return loader.getRoot();
    }

    /**
     * Create controller with dependency injection.
     * Uses prototype scope for controllers (new instance per request).
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
        
        // Resolve from container (triggers JIT if needed)
        T controller = container.resolve(clazz);
        
        // Inject fields marked with @WarmupInject
        injectFields(controller);
        
        // Cache if not prototype
        if (!isPrototypeController(clazz)) {
            controllerCache.put(controllerKey, controller);
        }
        
        return controller;
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
