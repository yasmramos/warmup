package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.core.Warmup;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javafx.application.Platform;

/**
 * JavaFX integration module for Warmup DI framework. Provides lazy loading of
 * controllers with dependency injection.
 *
 * Features: - Lazy initialization of controllers - Hot-reload support in
 * development mode (FXML and CSS) - Automatic dependency injection via
 * container (no manual reflection) - Circular dependency resolution for UI
 * frameworks - Auto-loading FXML from @FxController annotation - File
 * watching for automatic hot-reload of FXML/CSS changes
 */
public class FxLoader {

    private static final System.Logger LOG = System.getLogger(FxLoader.class.getName());
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

    // Per-FXML root replacers so several scenes can track different views
    private final Map<String, List<Consumer<Parent>>> rootReplacers = new ConcurrentHashMap<>();

    // Callback for stylesheet reload during hot-reload
    private volatile Consumer<List<String>> stylesheetReplacer;

    // Registered scenes for CSS hot-reload
    private final CopyOnWriteArrayList<Scene> registeredScenes;
    private final Map<String, Long> lastReloadTime = new ConcurrentHashMap();
    private static final long DEBOUNCE_MS = 200;

    // UI scopes (VIEW / WINDOW)
    private final ViewScopeHandler viewScopeHandler = new ViewScopeHandler();
    private final WindowScopeHandler windowScopeHandler = new WindowScopeHandler();

    /**
     * Holds information about a loaded FXML for potential hot-reload.
     */
    private static class LoadedFxmlInfo {

        final String fxmlPath;
        final ResourceBundle resourceBundle;
        final Class<?> controllerClass;
        final Window window;
        Parent lastLoadedRoot;
        ScopedBeanStore viewScope;

        LoadedFxmlInfo(String fxmlPath, ResourceBundle resourceBundle, Class<?> controllerClass, Window window) {
            this.fxmlPath = fxmlPath;
            this.resourceBundle = resourceBundle;
            this.controllerClass = controllerClass;
            this.window = window;
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
        this.registeredScenes = new CopyOnWriteArrayList<>();

        // Register the UI scope handlers so @ViewScoped/@WindowScoped beans can
        // be created while views are loaded.
        if (warmup != null) {
            warmup.registerScope(UiScopes.VIEW, viewScopeHandler);
            warmup.registerScope(UiScopes.WINDOW, windowScopeHandler);
        }
    }

    private boolean shouldReload(String key) {
        long now = System.currentTimeMillis();
        Long last = lastReloadTime.put(key, now);
        return last == null || (now - last) > DEBOUNCE_MS;
    }

    /**
     * Set whether development mode is enabled. Can be changed at runtime to
     * enable/disable hot-reload behavior.
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
     * Set a callback that will be invoked when hot-reload replaces the scene
     * root. This allows the application to update the displayed scene when FXML
     * is reloaded.
     *
     * @param replacer callback that receives the new root node
     */
    public void setSceneRootReplacer(Consumer<Parent> replacer) {
        this.sceneRootReplacer = replacer;
    }

    /**
     * Set a callback that will be invoked when CSS stylesheets are
     * hot-reloaded. The callback receives a list of CSS URLs to apply to the
     * scene.
     *
     * @param replacer callback that receives the list of CSS URLs
     */
    public void setStylesheetReplacer(Consumer<List<String>> replacer) {
        this.stylesheetReplacer = replacer;
    }

    /**
     * Register a Scene for CSS hot-reload support. When CSS files are reloaded,
     * the stylesheets will be refreshed on all registered scenes.
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
        return loadView(fxmlPath).root();
    }

    /**
     * Load FXML with a resource bundle.
     *
     * @param fxmlPath path to FXML file
     * @param resourceBundle optional resource bundle for localization
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     */
    public Parent loadFxml(String fxmlPath, ResourceBundle resourceBundle) throws IOException {
        return loadView(fxmlPath, resourceBundle).root();
    }

    /**
     * Load an FXML file and return both its root and the controller that the
     * container created.
     *
     * @param fxmlPath path to the FXML file
     * @return the loaded view
     * @throws IOException if FXML loading fails
     */
    public FxView<Object> loadView(String fxmlPath) throws IOException {
        return loadView(fxmlPath, (ResourceBundle) null, null);
    }

    /**
     * Load an FXML file with a resource bundle.
     *
     * @param fxmlPath path to the FXML file
     * @param resourceBundle optional resource bundle for localization
     * @return the loaded view
     * @throws IOException if FXML loading fails
     */
    public FxView<Object> loadView(String fxmlPath, ResourceBundle resourceBundle) throws IOException {
        return loadView(fxmlPath, resourceBundle, null);
    }

    /**
     * Load an FXML file associated with a window, so {@link WindowScoped} beans
     * can be resolved while the view is built.
     *
     * @param fxmlPath path to the FXML file
     * @param window the window that owns the view (may be {@code null})
     * @return the loaded view
     * @throws IOException if FXML loading fails
     */
    public FxView<Object> loadView(String fxmlPath, Window window) throws IOException {
        return loadView(fxmlPath, (ResourceBundle) null, window);
    }

    /**
     * Load an FXML file with a resource bundle and an owning window.
     *
     * @param fxmlPath path to the FXML file
     * @param resourceBundle optional resource bundle for localization
     * @param window the window that owns the view (may be {@code null})
     * @return the loaded view
     * @throws IOException if FXML loading fails
     */
    @SuppressWarnings("unchecked")
    public FxView<Object> loadView(String fxmlPath, ResourceBundle resourceBundle, Window window) throws IOException {
        return (FxView<Object>) loadViewInternal(null, fxmlPath, resourceBundle, window);
    }

    /**
     * Load FXML automatically from a {@link FxController} annotation. Reads the
     * {@code fxml()} attribute (and {@code bundle()}/{@code css()} when
     * present) and returns the root together with the container-created
     * controller.
     *
     * @param controllerClass the controller class annotated with {@link FxController}
     * @param <T> controller type
     * @return the loaded view
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if the controller is not annotated or
     * {@code fxml()} is empty
     */
    public <T> FxView<T> loadView(Class<T> controllerClass) throws IOException {
        return loadView(controllerClass, (ResourceBundle) null, null);
    }

    /**
     * Load FXML from a {@link FxController} annotation with an explicit bundle.
     *
     * @param controllerClass the controller class annotated with {@link FxController}
     * @param resourceBundle bundle to use instead of the annotation's
     * {@code bundle()}
     * @param <T> controller type
     * @return the loaded view
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if the controller is not annotated or
     * {@code fxml()} is empty
     */
    public <T> FxView<T> loadView(Class<T> controllerClass, ResourceBundle resourceBundle) throws IOException {
        return loadView(controllerClass, resourceBundle, null);
    }

    /**
     * Load FXML from a {@link FxController} annotation for a specific window,
     * so {@link WindowScoped} beans can be resolved while the view is built.
     *
     * @param controllerClass the controller class annotated with {@link FxController}
     * @param window the window that owns the view (may be {@code null})
     * @param <T> controller type
     * @return the loaded view
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if the controller is not annotated or
     * {@code fxml()} is empty
     */
    public <T> FxView<T> loadView(Class<T> controllerClass, Window window) throws IOException {
        return loadView(controllerClass, (ResourceBundle) null, window);
    }

    /**
     * Fully configurable view load: explicit bundle and owning window.
     *
     * @param controllerClass the controller class annotated with {@link FxController}
     * @param resourceBundle bundle to use instead of the annotation's
     * {@code bundle()}
     * @param window the window that owns the view (may be {@code null})
     * @param <T> controller type
     * @return the loaded view
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if the controller is not annotated or
     * {@code fxml()} is empty
     */
    @SuppressWarnings("unchecked")
    public <T> FxView<T> loadView(Class<T> controllerClass, ResourceBundle resourceBundle, Window window) throws IOException {
        FxController annotation = requireFxController(controllerClass);
        String fxmlPath = annotation.fxml();
        if (fxmlPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Controller class " + controllerClass.getName()
                    + " must declare a non-empty fxml() attribute in @FxController for auto-loading"
            );
        }

        ResourceBundle bundle = resourceBundle;
        if (bundle == null && !annotation.bundle().isEmpty()) {
            bundle = ResourceBundle.getBundle(annotation.bundle());
        }

        FxView<T> view = (FxView<T>) loadViewInternal(controllerClass, fxmlPath, bundle, window);
        // Idempotent: loadViewInternal also applies the stylesheets declared on
        // the actual controller instance (covers path-based loads).
        applyStylesheets(annotation.css(), view.root());
        return view;
    }
    /**
     * Load FXML automatically from a {@link FxController} annotation.
     *
     * @param controllerClass the controller class annotated with {@link FxController}
     * @param <T> controller type
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if the controller is not annotated or
     * {@code fxml()} is empty
     */
    public <T> Parent loadController(Class<T> controllerClass) throws IOException {
        return loadView(controllerClass).root();
    }

    /**
     * Load FXML automatically from a {@link FxController} annotation with a
     * ResourceBundle.
     *
     * @param controllerClass the controller class annotated with {@link FxController}
     * @param resourceBundle optional resource bundle for localization
     * @param <T> controller type
     * @return loaded Parent node
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if the controller is not annotated or
     * {@code fxml()} is empty
     */
    public <T> Parent loadController(Class<T> controllerClass, ResourceBundle resourceBundle) throws IOException {
        return loadView(controllerClass, resourceBundle).root();
    }

    private static FxController requireFxController(Class<?> controllerClass) {
        FxController annotation = controllerClass.getAnnotation(FxController.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Controller class " + controllerClass.getName()
                    + " must be annotated with @FxController to use auto-loading"
            );
        }
        return annotation;
    }

    private void applyStylesheets(String[] cssPaths, Parent root) {
        if (root == null || cssPaths == null) {
            return;
        }
        for (String cssPath : cssPaths) {
            if (cssPath == null || cssPath.isEmpty()) {
                continue;
            }
            String resolved = loadCss(cssPath);
            if (resolved != null && !root.getStylesheets().contains(resolved)) {
                root.getStylesheets().add(resolved);
            }
        }
    }

    /**
     * Internal method to load FXML, shared by the public load methods.
     *
     * @param controllerClass optional controller class for relative path
     * resolution (null for absolute paths)
     * @param fxmlPath path to FXML file (relative to controller class if
     * controllerClass provided, otherwise absolute)
     * @param resourceBundle optional resource bundle for localization
     * @param window optional owning window, used to activate the WINDOW scope
     * @return the loaded view (root + controller)
     * @throws IOException if FXML loading fails
     */
    private FxView<?> loadViewInternal(Class<?> controllerClass, String fxmlPath,
                                       ResourceBundle resourceBundle, Window window) throws IOException {
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
                    "FXML not found: " + fxmlPath
                    + (controllerClass != null ? " (relative to " + controllerClass.getName() + ")" : "")
                    + ". Ensure the FXML file exists in the classpath."
            );
        }

        loader.setLocation(fxmlUrl);

        // Set resource bundle for localization
        if (resourceBundle != null) {
            loader.setResources(resourceBundle);
        }

        // Set controller factory for DI - all controllers are resolved from container
        loader.setControllerFactory(this::createController);

        // A fresh view scope is created for every load (including reloads).
        // It must be active on the thread that runs FXMLLoader.load(), because
        // the container resolves controllers and their @ViewScoped dependencies
        // from the controller factory while parsing.
        ScopedBeanStore viewScope = viewScopeHandler.createScope();
        ScopedBeanStore windowScope = window != null ? windowScopeHandler.scopeFor(window) : null;

        Callable<Void> loadAction = () -> {
            viewScopeHandler.activate(viewScope);
            boolean activatedWindow = false;
            try {
                if (windowScope != null && !windowScopeHandler.isActive(windowScope)) {
                    windowScopeHandler.activate(windowScope);
                    activatedWindow = true;
                }
                loader.load();
                return null;
            } finally {
                if (activatedWindow) {
                    windowScopeHandler.deactivate(windowScope);
                }
                viewScopeHandler.deactivate(viewScope);
            }
        };

        // FXMLLoader creates scene nodes, so loading must run on the JavaFX
        // Application Thread. When we are already on it, load synchronously;
        // otherwise dispatch and wait so the caller still gets the loaded root
        // (previously this returned before load() had run, leaving root null).
        try {
            if (Platform.isFxApplicationThread()) {
                // Load FXML - let FXMLLoader handle the stream internally to avoid file handle leaks
                loadAction.call();
            } else {
                CompletableFuture<Void> loaded = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        loadAction.call();
                        loaded.complete(null);
                    } catch (Throwable t) {
                        loaded.completeExceptionally(t);
                    }
                });
                try {
                    loaded.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while loading FXML: " + fxmlPath, ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new IOException("Failed to load FXML: " + fxmlPath, cause);
                }
            }
        } catch (IOException | RuntimeException | Error e) {
            viewScopeHandler.destroyScope(viewScope);
            throw e;
        } catch (Exception e) {
            viewScopeHandler.destroyScope(viewScope);
            throw new IOException("Failed to load FXML: " + fxmlPath, e);
        }

        Parent root = loader.getRoot();
        Object controller = loader.getController();

        // Apply stylesheets declared on the controller, including path-based loads
        // where the controller type is only known after parsing the FXML.
        if (controller != null) {
            FxController controllerAnnotation = controller.getClass().getAnnotation(FxController.class);
            if (controllerAnnotation != null) {
                applyStylesheets(controllerAnnotation.css(), root);
            }
        }

        // Track this loaded FXML for potential hot-reload
        String trackingKey = buildTrackingKey(controllerClass, fxmlPath);
        LoadedFxmlInfo info = new LoadedFxmlInfo(fxmlPath, resourceBundle, controllerClass, window);
        info.lastLoadedRoot = root;
        info.viewScope = viewScope;
        loadedFxmlMap.put(trackingKey, info);

        return new FxView<>(root, controller, fxmlPath, controllerClass, resourceBundle,
                () -> viewScopeHandler.destroyScope(viewScope));
    }

    /**
     * Create controller with dependency injection. Uses prototype scope for
     * controllers (new instance per request). Controllers annotated with
     *
     * @FxController are registered as beans by the annotation processor
     * and resolved directly from the container with full dependency injection.
     *
     * @param clazz controller class
     * @return injected controller instance
     * @throws IllegalStateException if controller is not registered in the
     * container
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

        // Resolve from container - controllers must be registered as beans via @FxController
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
     * Check if controller should use prototype scope. Default is true for
     * JavaFX controllers.
     *
     * @param clazz controller class
     * @return true if prototype scope
     */
    private boolean isPrototypeController(Class<?> clazz) {
        FxController annotation = clazz.getAnnotation(FxController.class);
        if (annotation != null) {
            return annotation.scope() == io.github.yasmramos.warmup.core.scope.Scope.PROTOTYPE;
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
     * Hot-reload a previously loaded FXML file. This method: 1. Triggers
     * container.reload() for the controller bean to recompile its factory 2.
     * Re-loads the FXML to get a new Parent root with the new controller
     * instance 3. Invokes the sceneRootReplacer callback if set
     *
     * @param fxmlPath the path to the FXML file to reload
     * @return the newly loaded Parent root, or null if the FXML was not
     * previously loaded
     * @throws IOException if FXML loading fails
     */
    public Parent reloadFxml(String fxmlPath) throws IOException {
        LoadedFxmlInfo info = loadedFxmlMap.get(fxmlPath);
        if (info == null) {
            for (LoadedFxmlInfo candidate : loadedFxmlMap.values()) {
                if (candidate.fxmlPath.equals(fxmlPath)) {
                    info = candidate;
                    break;
                }
            }
        }
        if (info == null) {
            return loadViewInternal(null, fxmlPath, null, null).root();
        }

        // Step 1: Trigger hot-reload in the container for the controller bean
        if (info.controllerClass != null) {
            if (warmup != null) {
                String beanName = computeBeanName(info.controllerClass);
                if (beanName != null) {
                    warmup.hotReload().reload(beanName);
                }
            }
        }

        // Step 2: Clear controller cache to force new controller creation
        clearCache();

        // Step 3: Re-load the FXML to get new Parent and controller. The old
        // view scope is kept alive until the new root has been installed.
        ScopedBeanStore oldViewScope = info.viewScope;
        Parent newRoot = loadViewInternal(info.controllerClass, info.fxmlPath, info.resourceBundle, info.window).root();
        info.lastLoadedRoot = newRoot;

        // Step 4: Notify every scene tracking this FXML
        notifyRootReplacers(info.fxmlPath, newRoot);

        // Step 5: Destroy the beans of the previous view
        viewScopeHandler.destroyScope(oldViewScope);

        return newRoot;
    }

    /**
     * Register a callback that receives the new root whenever the given FXML is
     * hot-reloaded. Unlike {@link #setSceneRootReplacer(Consumer)}, this lets
     * several scenes track different FXML files independently.
     *
     * @param fxmlPath the FXML path to track
     * @param replacer callback invoked with the newly loaded root
     */
    public void registerRootReplacer(String fxmlPath, Consumer<Parent> replacer) {
        if (fxmlPath == null || replacer == null) {
            return;
        }
        rootReplacers.computeIfAbsent(fxmlPath, key -> new CopyOnWriteArrayList<>()).add(replacer);
    }

    /**
     * Remove a previously registered root replacer.
     *
     * @param fxmlPath the FXML path
     * @param replacer the callback to remove
     */
    public void unregisterRootReplacer(String fxmlPath, Consumer<Parent> replacer) {
        List<Consumer<Parent>> replacers = rootReplacers.get(fxmlPath);
        if (replacers != null) {
            replacers.remove(replacer);
            if (replacers.isEmpty()) {
                rootReplacers.remove(fxmlPath);
            }
        }
    }

    private void notifyRootReplacers(String fxmlPath, Parent newRoot) {
        Consumer<Parent> global = sceneRootReplacer;
        if (global != null) {
            global.accept(newRoot);
        }
        List<Consumer<Parent>> replacers = rootReplacers.get(fxmlPath);
        if (replacers != null) {
            for (Consumer<Parent> replacer : replacers) {
                replacer.accept(newRoot);
            }
        }
    }

    /**
     * Reload every FXML that has been loaded through this loader.
     *
     * @return the newly loaded roots, one per distinct FXML path
     * @throws IOException if any FXML fails to reload
     */
    public List<Parent> reloadAll() throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        for (LoadedFxmlInfo info : loadedFxmlMap.values()) {
            paths.add(info.fxmlPath);
        }
        List<Parent> roots = new ArrayList<>(paths.size());
        for (String path : paths) {
            roots.add(reloadFxml(path));
        }
        return roots;
    }

    /**
     * Hot-reload a controller by reloading its associated FXML.
     *
     * @param controllerClass the controller class annotated with
     * @FxController
     * @return the newly loaded Parent root, or null if the controller's FXML
     * was not previously loaded
     * @throws IOException if FXML loading fails
     * @throws IllegalArgumentException if controller is not annotated with
     * @FxController
     */
    public <T> Parent reloadController(Class<T> controllerClass) throws IOException {
        FxController annotation = controllerClass.getAnnotation(FxController.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Controller class " + controllerClass.getName()
                    + " must be annotated with @FxController to use hot-reload"
            );
        }

        String fxmlPath = annotation.fxml();
        if (fxmlPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Controller class " + controllerClass.getName()
                    + " must declare a non-empty fxml() attribute in @FxController for hot-reload"
            );
        }

        // Build the key used to track this FXML
        String trackingKey = buildTrackingKey(controllerClass, fxmlPath);
        return reloadFxml(trackingKey);
    }

    /**
     * Makes the scope of the given window current on the calling thread.
     * Window-scoped beans resolved after this call (until
     * {@link #endWindowScope()}) are cached per window.
     *
     * <p>Use it when a window is created outside {@code WarmupApplication} or
     * when resolving window-scoped beans from a background thread. Calls nest:
     * every {@code beginWindowScope} needs one {@code endWindowScope}.</p>
     *
     * @param window the window whose scope should be activated
     */
    public void beginWindowScope(Window window) {
        windowScopeHandler.activate(windowScopeHandler.scopeFor(window));
    }

    /**
     * Deactivates the window scope previously activated with
     * {@link #beginWindowScope(Window)} or {@link #withWindow(Window, Runnable)}.
     */
    public void endWindowScope() {
        ScopedBeanStore scope = windowScopeHandler.currentScope();
        if (scope != null) {
            windowScopeHandler.deactivate(scope);
        }
    }

    /**
     * Runs an action with the given window's scope active. If the scope is
     * already active (for example inside {@code WarmupApplication#onStart}),
     * it is reused and left active after the action returns.
     *
     * @param window the window whose scope should be active
     * @param action the action to run
     */
    public void withWindow(Window window, Runnable action) {
        if (action == null) {
            return;
        }
        ScopedBeanStore scope = windowScopeHandler.scopeFor(window);
        boolean alreadyActive = windowScopeHandler.isActive(scope);
        if (!alreadyActive) {
            windowScopeHandler.activate(scope);
        }
        try {
            action.run();
        } finally {
            if (!alreadyActive) {
                windowScopeHandler.deactivate(scope);
            }
        }
    }

    /**
     * Destroys the scope of a window and all of its {@code @WindowScoped}
     * beans. Called automatically by {@code WarmupApplication} when the
     * primary stage is hidden.
     *
     * @param window the window to close
     */
    public void closeWindow(Window window) {
        windowScopeHandler.close(window);
    }

    /**
     * Destroys every view scope and window scope created by this loader. Call
     * it when the application stops, before shutting down the container.
     */
    public void closeAll() {
        viewScopeHandler.destroyAll();
        windowScopeHandler.closeAll();
        loadedFxmlMap.clear();
    }

    /**
     * Hot-reload a CSS stylesheet. This method: 1. Re-resolves the CSS URL to
     * pick up any file changes 2. Updates registered scenes with the new
     * stylesheet URL (forces re-apply) 3. Invokes the stylesheetReplacer
     * callback if set
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
            Platform.runLater(() -> {
                for (Scene scene : registeredScenes) {
                    // Remove old stylesheet URL if present
                    ObservableList<String> stylesheets = scene.getStylesheets();
                    if (stylesheets.contains(info.resolvedUrl)) {
                        stylesheets.remove(info.resolvedUrl);
                    }
                    // Add new (or same) URL to force refresh
                    stylesheets.add(newResolvedUrl);
                }
            });

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
     * Start watching FXML and CSS files for changes in development mode. When a
     * watched file is modified, the corresponding FXML will be automatically
     * reloaded.
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
                    if (!shouldReload(fileName)) {
                        continue;
                    }
                    if (fileName.endsWith(".fxml")) {
                        // Find matching loaded FXML and trigger reload
                        for (LoadedFxmlInfo info : loadedFxmlMap.values()) {
                            if (info.fxmlPath.endsWith(changedFile.toString())) {
                                Platform.runLater(() -> {
                                    try {
                                        reloadFxml(info.fxmlPath);
                                    } catch (IOException e) {
                                        LOG.log(System.Logger.Level.ERROR, () -> "Failed to reload FXML: " + info.fxmlPath
                                                + " - " + e.getMessage());
                                    }
                                });
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
                                    LOG.log(System.Logger.Level.ERROR, () -> "Failed to reload CSS: " + info.cssPath
                                            + " - " + e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }

                if (!key.reset()) {
                    watching = false;
                    break;
                }
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
     * Compute the bean name for a controller class. Uses the simple class name
     * with first letter lowercased by default.
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
