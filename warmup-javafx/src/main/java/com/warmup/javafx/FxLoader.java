package com.warmup.javafx;

import com.warmup.core.Warmup;
import com.warmup.core.container.HybridContainer;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * JavaFX integration module for Warmup DI framework.
 * Provides lazy loading of controllers with dependency injection.
 * 
 * Features:
 * - Lazy initialization of controllers
 * - Hot-reload support in development mode (FXML and CSS)
 * - Automatic dependency injection via container (no manual reflection)
 * - Circular dependency resolution for UI frameworks
 * - Auto-loading FXML from @WarmupFxController annotation
 * - File watching for automatic hot-reload of FXML/CSS changes
 */
public class FxLoader {

    private final Warmup warmup;
    private final ConcurrentHashMap<String, Object> controllerCache;
    private volatile boolean developmentMode;
    
    // Track loaded FXML paths for hot-reload
    private final ConcurrentHashMap<String, LoadedFxmlInfo> loadedFxmlMap;
    
    // Track loaded CSS stylesheets for hot-reload
    private final ConcurrentHashMap<String, LoadedCssInfo> loadedCssMap;
    
    // WatchService for monitoring FXML/CSS file changes
    private volatile WatchService watchService;
    private volatile ExecutorService watchExecutor;
    private volatile boolean watching = false;
    
    // Callback for scene root replacement during hot-reload
    private volatile Consumer<Parent> sceneRootReplacer;
    
    // Callback for stylesheet reload during hot-reload
    private volatile Consumer<List<String>> stylesheetReplacer;
    
    // Registered scenes for CSS hot-reload
    private final List<Scene> registeredScenes;
    
    /**
     * Holds information about a loaded FXML for potential hot-reload.
     */
    private static class LoadedFxmlInfo {
        final String fxmlPath;
        final ResourceBundle resourceBundle;
        final Class<?> controllerClass;
        Parent lastLoadedRoot;
        
        LoadedFxmlInfo(String fxmlPath, ResourceBundle resourceBundle, Class<?> controllerClass) {
            this.fxmlPath = fxmlPath;
            this.resourceBundle = resourceBundle;
            this.controllerClass = controllerClass;
        }
    }
    
    /**
     * Holds information about a loaded CSS stylesheet for potential hot-reload.
     */
    private static class LoadedCssInfo {
        final String cssPath;
        final String resolvedUrl;
        
        LoadedCssInfo(String cssPath, String resolvedUrl) {
            this.cssPath = cssPath;
            this.resolvedUrl = resolvedUrl;
        }
    }

    public FxLoader(Warmup warmup) {
        this(warmup, false);
    }

    public FxLoader(Warmup warmup, boolean developmentMode) {
        this.warmup = warmup;
        this.controllerCache = new ConcurrentHashMap<>();
        this.developmentMode = developmentMode;
        this.loadedFxmlMap = new ConcurrentHashMap<>();
        this.loadedCssMap = new ConcurrentHashMap<>();
        this.registeredScenes = new ArrayList<>();
    }
    
    /**
     * Set whether development mode is enabled.
     * Can be changed at runtime to enable/disable hot-reload behavior.
     * 
     * @param developmentMode true to enable development mode
     */
    public void setDevelopmentMode(boolean developmentMode) {
        this.developmentMode = developmentMode;
    }
    
    /**
     * Check if development mode is enabled.
     * 
     * @return true if in development mode
     */
    public boolean isDevelopmentMode() {
        return developmentMode;
    }
    
    /**
     * Set a callback that will be invoked when hot-reload replaces the scene root.
     * This allows the application to update the displayed scene when FXML is reloaded.
     * 
     * @param replacer callback that receives the new root node
     */
    public void setSceneRootReplacer(Consumer<Parent> replacer) {
        this.sceneRootReplacer = replacer;
    }
    
    /**
     * Set a callback that will be invoked when CSS stylesheets are hot-reloaded.
     * The callback receives a list of CSS URLs to apply to the scene.
     * 
     * @param replacer callback that receives the list of CSS URLs
     */
    public void setStylesheetReplacer(Consumer<List<String>> replacer) {
        this.stylesheetReplacer = replacer;
    }
    
    /**
     * Register a Scene for CSS hot-reload support.
     * When CSS files are reloaded, the stylesheets will be refreshed on all registered scenes.
     * 
     * @param scene the JavaFX Scene to register
     */
    public void registerScene(Scene scene) {
        if (scene != null && !registeredScenes.contains(scene)) {
            registeredScenes.add(scene);
        }
    }
    
    /**
     * Unregister a Scene from CSS hot-reload support.
     * 
     * @param scene the JavaFX Scene to unregister
     */
    public void unregisterScene(Scene scene) {
        registeredScenes.remove(scene);
    }

    /**
     * Load a CSS stylesheet and track it for hot-reload.
     * 
     * @param cssPath path to CSS file
     * @return resolved URL of the CSS file, or null if not found
     */
    public String loadCss(String cssPath) {
        URL cssUrl = getClass().getResource(cssPath);
        if (cssUrl == null) {
            // Try as absolute path
            cssUrl = Thread.currentThread().getContextClassLoader().getResource(cssPath);
        }
        
        if (cssUrl != null) {
            String resolvedUrl = cssUrl.toExternalForm();
            loadedCssMap.put(cssPath, new LoadedCssInfo(cssPath, resolvedUrl));
            return resolvedUrl;
        }
        return null;
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
        
        Parent root = loader.getRoot();
        
        // Track this loaded FXML for potential hot-reload
        String trackingKey = buildTrackingKey(controllerClass, fxmlPath);
        LoadedFxmlInfo info = new LoadedFxmlInfo(fxmlPath, resourceBundle, controllerClass);
        info.lastLoadedRoot = root;
        loadedFxmlMap.put(trackingKey, info);
        
        return root;
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
    
    /**
     * Hot-reload a previously loaded FXML file.
     * This method:
     * 1. Triggers container.reload() for the controller bean to recompile its factory
     * 2. Re-loads the FXML to get a new Parent root with the new controller instance
     * 3. Invokes the sceneRootReplacer callback if set
     * 
     * @param fxmlPath the path to the FXML file to reload
     * @return the newly loaded Parent root, or null if the FXML was not previously loaded
     * @throws IOException if FXML loading fails
     */
    public Parent reloadFxml(String fxmlPath) throws IOException {
        LoadedFxmlInfo info = loadedFxmlMap.get(fxmlPath);
        if (info == null) {
            // FXML not tracked, just do a fresh load
            return loadFxmlInternal(info != null ? info.controllerClass : null, fxmlPath, 
                                    info != null ? info.resourceBundle : null);
        }
        
        // Step 1: Trigger hot-reload in the container for the controller bean
        if (info.controllerClass != null) {
            HybridContainer container = warmup.unsafeContainer();
            if (container != null) {
                String beanName = computeBeanName(info.controllerClass);
                if (beanName != null) {
                    container.reload(beanName);
                }
            }
        }
        
        // Step 2: Clear controller cache to force new controller creation
        clearCache();
        
        // Step 3: Re-load the FXML to get new Parent and controller
        Parent newRoot = loadFxmlInternal(info.controllerClass, info.fxmlPath, info.resourceBundle);
        info.lastLoadedRoot = newRoot;
        
        // Step 4: Invoke scene root replacer callback if set
        if (sceneRootReplacer != null) {
            sceneRootReplacer.accept(newRoot);
        }
        
        return newRoot;
    }
    
    /**
     * Hot-reload a controller by reloading its associated FXML.
     * 
     * @param controllerClass the controller class annotated with @WarmupFxController
     * @return the newly loaded Parent root, or null if the controller's FXML was not previously loaded
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if controller is not annotated with @WarmupFxController
     */
    public <T> Parent reloadController(Class<T> controllerClass) throws IOException {
        WarmupFxController annotation = controllerClass.getAnnotation(WarmupFxController.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Controller class " + controllerClass.getName() + 
                " must be annotated with @WarmupFxController to use hot-reload"
            );
        }
        
        String fxmlPath = annotation.fxml();
        if (fxmlPath.isEmpty()) {
            throw new IllegalArgumentException(
                "Controller class " + controllerClass.getName() + 
                " must declare a non-empty fxml() attribute in @WarmupFxController for hot-reload"
            );
        }
        
        // Build the key used to track this FXML
        String trackingKey = buildTrackingKey(controllerClass, fxmlPath);
        return reloadFxml(trackingKey);
    }
    
    /**
     * Hot-reload a CSS stylesheet.
     * This method:
     * 1. Re-resolves the CSS URL to pick up any file changes
     * 2. Updates registered scenes with the new stylesheet URL (forces re-apply)
     * 3. Invokes the stylesheetReplacer callback if set
     * 
     * @param cssPath the path to the CSS file to reload
     * @throws Exception if CSS reload fails
     */
    public void reloadCss(String cssPath) throws Exception {
        LoadedCssInfo info = loadedCssMap.get(cssPath);
        if (info == null) {
            // CSS not tracked, try to load it fresh
            String resolvedUrl = loadCss(cssPath);
            if (resolvedUrl != null && stylesheetReplacer != null) {
                List<String> urls = new ArrayList<>();
                urls.add(resolvedUrl);
                stylesheetReplacer.accept(urls);
            }
            return;
        }
        
        // Re-resolve the CSS URL to pick up changes
        URL cssUrl = getClass().getResource(info.cssPath);
        if (cssUrl == null) {
            cssUrl = Thread.currentThread().getContextClassLoader().getResource(info.cssPath);
        }
        
        if (cssUrl != null) {
            String newResolvedUrl = cssUrl.toExternalForm();
            
            // Update all registered scenes by removing and re-adding the stylesheet
            // This forces JavaFX to re-apply the styles
            for (Scene scene : registeredScenes) {
                // Remove old stylesheet URL if present
                ObservableList<String> stylesheets = scene.getStylesheets();
                if (stylesheets.contains(info.resolvedUrl)) {
                    stylesheets.remove(info.resolvedUrl);
                }
                // Add new (or same) URL to force refresh
                stylesheets.add(newResolvedUrl);
            }
            
            // Update the tracked URL
            loadedCssMap.put(cssPath, new LoadedCssInfo(info.cssPath, newResolvedUrl));
            
            // Invoke stylesheet replacer callback if set
            if (stylesheetReplacer != null) {
                List<String> allUrls = new ArrayList<>();
                for (LoadedCssInfo cssInfo : loadedCssMap.values()) {
                    allUrls.add(cssInfo.resolvedUrl);
                }
                stylesheetReplacer.accept(allUrls);
            }
        }
    }
    
    /**
     * Start watching FXML and CSS files for changes in development mode.
     * When a watched file is modified, the corresponding FXML will be automatically reloaded.
     * 
     * @param directoriesToWatch paths to directories containing FXML/CSS files
     * @throws IOException if WatchService cannot be created
     */
    public void startFileWatching(String... directoriesToWatch) throws IOException {
        if (!developmentMode || watching) {
            return;
        }
        
        watchService = FileSystems.getDefault().newWatchService();
        watchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FxLoader-WatchService");
            t.setDaemon(true);
            return t;
        });
        
        // Register each directory for modification events
        for (String dirPath : directoriesToWatch) {
            Path dir = Paths.get(dirPath);
            if (Files.isDirectory(dir)) {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, 
                             StandardWatchEventKinds.ENTRY_CREATE);
            }
        }
        
        watching = true;
        
        // Start the watch loop
        watchExecutor.submit(this::watchLoop);
    }
    
    /**
     * Stop watching files and cleanup resources.
     */
    public void stopFileWatching() {
        watching = false;
        
        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
            watchExecutor = null;
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                // Ignore close errors
            }
            watchService = null;
        }
    }
    
    /**
     * Background loop that processes WatchService events.
     */
    private void watchLoop() {
        while (watching && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path changedFile = (Path) event.context();
                    
                    String fileName = changedFile.toString().toLowerCase();
                    if (fileName.endsWith(".fxml")) {
                        // Find matching loaded FXML and trigger reload
                        for (LoadedFxmlInfo info : loadedFxmlMap.values()) {
                            if (info.fxmlPath.endsWith(changedFile.toString())) {
                                try {
                                    reloadFxml(info.fxmlPath);
                                } catch (IOException e) {
                                    System.err.println("Failed to reload FXML: " + info.fxmlPath + 
                                                       " - " + e.getMessage());
                                }
                                break;
                            }
                        }
                    } else if (fileName.endsWith(".css")) {
                        // Find matching loaded CSS and trigger reload
                        for (LoadedCssInfo info : loadedCssMap.values()) {
                            if (info.cssPath.endsWith(changedFile.toString())) {
                                try {
                                    reloadCss(info.cssPath);
                                } catch (Exception e) {
                                    System.err.println("Failed to reload CSS: " + info.cssPath + 
                                                       " - " + e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }
                
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Build a tracking key for an FXML based on controller class and path.
     */
    private String buildTrackingKey(Class<?> controllerClass, String fxmlPath) {
        if (controllerClass != null) {
            return controllerClass.getName() + "#" + fxmlPath;
        }
        return fxmlPath;
    }
    
    /**
     * Compute the bean name for a controller class.
     * Uses the simple class name with first letter lowercased by default.
     */
    private String computeBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        if (simpleName.isEmpty()) {
            return null;
        }
        // Lowercase first letter
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
