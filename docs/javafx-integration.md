# JavaFX Integration Guide

This document describes the JavaFX integration module (`warmup-javafx`) for the Warmup DI framework, including the hot-reload functionality for development.

## Overview

The `warmup-javafx` module provides seamless integration between JavaFX and Warmup's dependency injection system. It enables:

- **Automatic controller injection**: Controllers are resolved from the container with full dependency injection
- **FXML auto-loading**: Load FXML files with controllers automatically wired via `@WarmupFxController` annotation
- **Hot-reload support**: Real-time reloading of FXML/CSS files and controller factories during development

## Quick Start

### Basic Setup

Extend `WarmupApplication` instead of `javafx.application.Application`:

```java
public class MyApp extends WarmupApplication {
    @Override
    protected void configure(Warmup warmup) {
        // Register your beans
        warmup.register("myService", MyService.class, MyService::new, Scope.SINGLETON);
    }
    
    @Override
    protected void onStart(Stage stage) {
        // Set up your UI
        Parent root = fxLoader.loadFxml("/com/example/main.fxml");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
```

### Creating Controllers

Annotate your controller classes with `@WarmupFxController`:

```java
package com.example;

import com.warmup.javafx.WarmupFxController;
import com.warmup.annotations.Inject;
import javafx.fxml.Initializable;

@WarmupFxController(fxml = "/com/example/main.fxml")
public class MainController implements Initializable {
    
    @Inject
    private MyService myService;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Controller initialization
    }
}
```

The annotation processor will automatically generate a factory for this controller and register it with the container.

## Hot-Reload Functionality

### Enabling Hot-Reload

Call `enableHotReload()` in your application to enable development mode:

```java
public class MyApp extends WarmupApplication {
    @Override
    protected void onInit() {
        // Enable hot-reload for development
        enableHotReload();
    }
    
    @Override
    protected void onStart(Stage stage) {
        // Register the scene for automatic root replacement
        Parent root = fxLoader.loadController(MainController.class);
        registerSceneForHotReload(stage, root);
        stage.setScene(new Scene(root));
        stage.show();
    }
}
```

### How Hot-Reload Works

The hot-reload mechanism performs the following steps when triggered:

1. **Container Reload**: Calls `HybridContainer.reload(beanName)` to:
   - Evict cached singleton instances
   - Invalidate factory caches
   - Unload the previous ASM-generated factory class
   - Trigger background recompilation of the factory

2. **Controller Cache Clear**: Clears the `FxLoader` controller cache to force new controller creation

3. **FXML Re-load**: Re-loads the FXML file using `loadFxmlInternal()` to get:
   - A new `Parent` root node
   - A new controller instance from the recompiled factory

4. **Scene Root Replacement**: Invokes the registered `sceneRootReplacer` callback to update the displayed scene

### File Watching

Enable automatic file watching for FXML/CSS changes:

```java
@Override
protected void onInit() {
    enableHotReload();
    
    // Start watching directories for changes
    try {
        fxLoader.startFileWatching(
            "src/main/resources/com/example",
            "target/classes/com/example"
        );
    } catch (IOException e) {
        e.printStackTrace();
    }
}

@Override
protected void onStop() {
    // Stop watching on application shutdown
    fxLoader.stopFileWatching();
}
```

The `WatchService` monitors specified directories for file modifications and automatically triggers reload when `.fxml` or `.css` files change.

### CSS Hot-Reload

CSS stylesheets can also be hot-reloaded. When a CSS file changes:

1. The CSS URL is re-resolved to pick up file changes
2. All registered scenes have their stylesheets refreshed (removed and re-added)
3. The `stylesheetReplacer` callback is invoked with updated URLs

```java
// Load and track a CSS file
String cssUrl = fxLoader.loadCss("/com/example/styles.css");

// Register the scene for CSS updates
fxLoader.registerScene(scene);

// Optionally set a callback for stylesheet updates
fxLoader.setStylesheetReplacer(urls -> {
    // urls contains all tracked CSS URLs
    // You can apply them to scenes manually if needed
});

// Manual CSS reload
fxLoader.reloadCss("/com/example/styles.css");
```

### Manual Reload

You can also manually trigger reload:

```java
// Reload a specific FXML by path
fxLoader.reloadFxml("/com/example/main.fxml");

// Reload a controller by class
fxLoader.reloadController(MainController.class);

// Reload a CSS stylesheet
fxLoader.reloadCss("/com/example/styles.css");
```

## API Reference

### FxLoader

| Method | Description |
|--------|-------------|
| `loadFxml(String fxmlPath)` | Load FXML file by path |
| `loadFxml(String fxmlPath, ResourceBundle bundle)` | Load FXML with localization |
| `loadController(Class<T> controllerClass)` | Load FXML from annotated controller |
| `loadCss(String cssPath)` | Load and track a CSS stylesheet for hot-reload |
| `reloadFxml(String fxmlPath)` | Hot-reload a previously loaded FXML |
| `reloadController(Class<T> controllerClass)` | Hot-reload a controller's FXML |
| `reloadCss(String cssPath)` | Hot-reload a CSS stylesheet |
| `startFileWatching(String... directories)` | Start watching directories for changes |
| `stopFileWatching()` | Stop file watching |
| `setDevelopmentMode(boolean)` | Enable/disable development mode at runtime |
| `setSceneRootReplacer(Consumer<Parent>)` | Set callback for scene root replacement |
| `setStylesheetReplacer(Consumer<List<String>>)` | Set callback for stylesheet updates |
| `registerScene(Scene)` | Register a Scene for CSS hot-reload |
| `unregisterScene(Scene)` | Unregister a Scene from CSS hot-reload |

### WarmupApplication

| Method | Description |
|--------|-------------|
| `enableHotReload()` | Enable development mode and hot-reload |
| `registerSceneForHotReload(Stage, Parent)` | Register scene for automatic root replacement |
| `getFxLoader()` | Get the FxLoader instance |
| `getWarmup()` | Get the Warmup container instance |

## Development Mode Behavior

When `developmentMode` is enabled:

- **Controller Cache**: Cleared on each `createController()` call, ensuring fresh instances
- **Factory Recompilation**: Container triggers JIT recompilation on `reload()`
- **File Watching**: Active monitoring of FXML/CSS directories
- **Scene Updates**: Automatic root replacement via callback

## Important Notes

### Limitations

1. **Existing References**: Hot-reload creates new instances, but existing references held by your code are NOT updated. Only future `resolve()` calls return new instances.

2. **Singleton Controllers**: By default, controllers use `Scope.PROTOTYPE`. If you use `Scope.SINGLETON`, be aware that the singleton instance will be destroyed and recreated on reload.

3. **File Paths**: When using `startFileWatching()`, ensure paths point to both source and compiled resource directories for complete coverage.

### Best Practices

1. **Always call `stopFileWatching()`** in `onStop()` to prevent resource leaks
2. **Use `registerSceneForHotReload()`** to get automatic UI updates
3. **Keep controllers as PROTOTYPE** scope for predictable behavior
4. **Test without hot-reload** periodically to ensure production behavior is correct

## Troubleshooting

### FXML Not Found

Ensure FXML files are in the classpath. For annotated controllers, the path is relative to the controller's package unless it starts with `/`.

### Reload Not Triggering

1. Verify `developmentMode` is enabled: `fxLoader.isDevelopmentMode()`
2. Check that watched directories exist and are readable
3. Ensure the controller bean name matches what's being reloaded

### ClassLoader Leaks

If you notice increasing memory usage during development:

1. Ensure `stopFileWatching()` is called on shutdown
2. Avoid holding long-lived references to controller instances
3. The container handles ClassLoader cleanup automatically on `reload()`

## See Also

- [Annotations Reference](annotations.md) - Documentation for `@WarmupFxController` and other annotations
- [Architecture](architecture.md) - Overall framework architecture
- [Scopes and Lifecycle](scopes-and-lifecycle.md) - Understanding bean scopes
