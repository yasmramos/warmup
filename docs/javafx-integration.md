# JavaFX Integration Guide

`warmup-javafx` brings dependency injection, typed FXML loading and hot-reload
to JavaFX applications. Controllers are regular Warmup beans: they are created
by the container (constructor/field injection included) and then handed to
`FXMLLoader`, which performs the usual `@FXML` injection and calls
`initialize()`.

## Overview

The module provides:

- **Automatic controller registration**: `@FxController` classes are discovered
  by the annotation processor and registered as beans.
- **Full DI for controllers**: constructor and field injection, `@Value`,
  scopes, profiles, conditions, ordering… everything the core container offers.
- **Typed FXML loading**: `FxView<T>` returns the root node *and* the controller
  instance that the container created.
- **Annotation-driven resources**: `bundle` and `css` attributes are resolved
  automatically when a view is loaded.
- **Hot-reload**: FXML, CSS and controller factories can be reloaded while the
  application is running, with per-scene tracking.
- **`WarmupApplication`**: a ready-to-use `Application` base class that boots
  the container and exposes the loader.

A complete, runnable example lives in [`warmup-javafx-demo`](../warmup-javafx-demo).

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.yasmramos</groupId>
    <artifactId>warmup-javafx</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Make sure `warmup-processor` is configured as an annotation processor (see the
[compile-time processing guide](compile-time-processing.md)).

### 2. Annotate your controller

```java
package com.example;

import io.github.yasmramos.warmup.annotations.Inject;
import io.github.yasmramos.warmup.javafx.FxController;

@FxController(
        fxml = "/com/example/main.fxml",
        bundle = "com.example.messages",      // optional
        css = "/com/example/styles.css")      // optional
public class MainController {

    // Package-private: the generated factory writes this field directly.
    @Inject
    GreetingService service;

    @FXML private Label greeting;
}
```

`@FxController` attributes:

| Attribute | Default | Description |
|-----------|---------|-------------|
| `fxml` | `""` | FXML path used by `loadView(Class)`. |
| `bundle` | `""` | Base name of a `ResourceBundle` used when none is passed to the loader. |
| `css` | `{}` | Stylesheet paths applied to the loaded root. |
| `scope` | `PROTOTYPE` | Bean scope. `PROTOTYPE` gives a fresh controller per view. |

> **Note:** `@Inject` fields must not be `private`. The processor generates a
> factory that writes them directly; make them package-private, protected or
> public. `@FXML` fields may stay private because JavaFX injects those.

### 3. Load the view

```java
public class MyApp extends WarmupApplication {

    @Override
    protected void registerBeans(Warmup warmup) {
        // Optional: @Singleton/@FxController beans are auto-registered by the
        // generated FactoryRegistrar. Register extra beans here if needed.
    }

    @Override
    protected void onStart(Stage stage) throws Exception {
        FxView<MainController> view = getFxLoader().loadView(MainController.class);

        Scene scene = new Scene(view.root(), 800, 600);
        getFxLoader().registerScene(scene);
        registerSceneForHotReload(stage, view);

        stage.setTitle("My App");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    protected void onStop() {
        getFxLoader().stopFileWatching();
    }
}
```

## Loading Views

`FxLoader` offers two flavors: path-based and annotation-based.

```java
// Path-based: controller type is whatever the FXML declares (Object)
FxView<Object> view1 = fxLoader.loadView("/com/example/main.fxml");

// Annotation-based: strongly typed controller
FxView<MainController> view2 = fxLoader.loadView(MainController.class);

// With an explicit resource bundle (overrides @FxController(bundle = ...))
FxView<MainController> view3 = fxLoader.loadView(MainController.class, bundle);
```

`FxView<T>` exposes:

| Method | Description |
|--------|-------------|
| `root()` | The loaded scene graph root. |
| `controller()` | The container-created controller (never `null` when the FXML declares one). |
| `fxmlPath()` | The FXML path used. |
| `controllerClass()` | The controller class for annotation-based loads, otherwise `null`. |
| `resourceBundle()` | The bundle used, or `null`. |

The older `loadFxml(...)` and `loadController(...)` methods still exist and are
equivalent to `loadView(...).root()`.

> **Why `FxView`?** Controllers default to `PROTOTYPE`, so they are never
> cached. `getCachedController(...)` therefore returns `null` for the common
> case; `FxView.controller()` is the reliable way to reach the instance.

## Hot-Reload

### Enabling it

Hot-reload is enabled when the container runs in development mode:

```java
@Override
protected void onInit() {
    enableHotReload(); // sets warmup.dev.mode and updates the loader
}
```

You can also start it with `-Dwarmup.dev.mode=true`.

### Watching files

```java
@Override
protected void onInit() throws Exception {
    enableHotReload();
    getFxLoader().startFileWatching(
            "src/main/resources/com/example",
            "target/classes/com/example");
}

@Override
protected void onStop() throws Exception {
    getFxLoader().stopFileWatching();
}
```

A `WatchService` monitors the directories and reloads `.fxml` and `.css` files
(after a short debounce) when they change.

### How a reload works

1. **Container reload** — the controller bean's factory is evicted and
   recompiled (`Warmup#hotReload()`).
2. **Cache clear** — `FxLoader` drops cached controllers.
3. **FXML re-load** — a new root and a new controller are created.
4. **Scene update** — every scene registered for that FXML receives the new
   root.

### Registering scenes

The typed overload tracks **each view independently**, so multi-window
applications work correctly:

```java
FxView<MainController> main = fxLoader.loadView(MainController.class);
FxView<SettingsController> settings = fxLoader.loadView(SettingsController.class);

registerSceneForHotReload(mainStage, main);
registerSceneForHotReload(settingsStage, settings);
```

At the `FxLoader` level you can register arbitrary callbacks:

```java
fxLoader.registerRootReplacer("/com/example/main.fxml", newRoot -> {
    mainStage.getScene().setRoot(newRoot);
});

// Later, when the view is gone:
fxLoader.unregisterRootReplacer("/com/example/main.fxml", callback);
```

`setSceneRootReplacer(Consumer<Parent>)` remains available as a **global**
fallback (last registration wins) but is not suitable for multiple scenes.

### Manual reloads

```java
fxLoader.reloadFxml("/com/example/main.fxml");
fxLoader.reloadController(MainController.class);
fxLoader.reloadCss("/com/example/styles.css");
fxLoader.reloadAll(); // every FXML loaded through this loader
```

### CSS hot-reload

```java
String cssUrl = fxLoader.loadCss("/com/example/styles.css");

fxLoader.registerScene(scene);
fxLoader.setStylesheetReplacer(urls -> {
    // Called with every tracked stylesheet URL after a CSS reload
});

fxLoader.reloadCss("/com/example/styles.css");
```

Stylesheets declared with `@FxController(css = ...)` are loaded and tracked
automatically when the view is loaded.

## API Reference

### `FxLoader`

| Method | Description |
|--------|-------------|
| `loadView(String fxmlPath)` | Load a view by path; controller is `Object`. |
| `loadView(String, ResourceBundle)` | Same, with localization. |
| `loadView(Class<T>)` | Load the FXML declared on `@FxController`. |
| `loadView(Class<T>, ResourceBundle)` | Same, with an explicit bundle. |
| `loadFxml(...)` / `loadController(...)` | Shortcuts returning only the root. |
| `loadCss(String cssPath)` | Resolve and track a stylesheet. |
| `reloadFxml(String fxmlPath)` | Reload a previously loaded FXML. |
| `reloadController(Class<T>)` | Reload a controller's FXML and factory. |
| `reloadCss(String cssPath)` | Reload a stylesheet. |
| `reloadAll()` | Reload every tracked FXML. |
| `registerRootReplacer(String, Consumer<Parent>)` | Track one scene per FXML. |
| `unregisterRootReplacer(String, Consumer<Parent>)` | Stop tracking. |
| `startFileWatching(String...)` | Watch directories for changes. |
| `stopFileWatching()` | Stop watching and release resources. |
| `setDevelopmentMode(boolean)` | Toggle development mode at runtime. |
| `setSceneRootReplacer(Consumer<Parent>)` | Global root callback (last wins). |
| `setStylesheetReplacer(Consumer<List<String>>)` | Global stylesheet callback. |
| `registerScene(Scene)` / `unregisterScene(Scene)` | Track scenes for CSS reload. |
| `getCachedController(Class<T>)` | Cache lookup (only useful for `SINGLETON` controllers). |
| `clearCache()` | Clear the controller cache. |

### `WarmupApplication`

| Method | Description |
|--------|-------------|
| `registerBeans(Warmup)` | Hook to register additional beans (required override). |
| `onInit()` | Called after the container is ready. |
| `onStart(Stage)` | Called when JavaFX starts. |
| `onStop()` | Called on shutdown; override to release resources. |
| `enableHotReload()` | Enable development mode and hot-reload. |
| `registerSceneForHotReload(Stage, FxView<?>)` | Track a view (recommended). |
| `registerSceneForHotReload(Stage, Parent)` | Global fallback (last wins). |
| `getFxLoader()` | Access the `FxLoader`. |
| `getWarmup()` | Access the container. |
| `createWarmup()` | Override to customize the `Warmup.Builder`. |

## Development Mode Behavior

When development mode is enabled:

- Controller caches are cleared on each controller creation.
- Factory reloads trigger JIT recompilation in the container.
- Watched directories are polled for changes.
- Registered scenes receive new roots automatically.

## Limitations

1. **Existing references are not rewritten.** Hot-reload creates new instances;
   code holding the old controller keeps the old instance.
2. **Singleton controllers** are recreated on reload. Prefer `PROTOTYPE`.
3. **File paths** should include both source and compiled resource directories
   while developing.
4. **Errors during reload** are propagated to the caller (or logged by the
   watcher); the currently displayed root is left untouched.
5. **State is not preserved** across FXML reloads yet (planned for hot-reload
   2.0).

## Best Practices

1. Call `stopFileWatching()` from `onStop()`.
2. Use `registerSceneForHotReload(Stage, FxView)` for multi-window apps.
3. Keep controllers as `PROTOTYPE`.
4. Run without hot-reload periodically to validate production behavior.

## Running the Demo

```bash
mvn -pl warmup-javafx-demo javafx:run
```

The demo shows a `@Singleton` service injected into an `@FxController`,
annotation-driven CSS and a stage registered for hot-reload. Its
`DemoWiringTest` verifies the processor → registrar → container pipeline
without starting the JavaFX toolkit, so it also runs on headless CI.

## Troubleshooting

### FXML not found

Ensure the FXML is on the classpath. Absolute paths start with `/`; relative
paths are resolved against the controller's package.

### Controller not registered

- The class must be annotated with `@FxController` (or registered manually).
- The annotation processor must be on the annotation processor path.
- Check the controller's bean name if you registered it manually.

### `@Inject` field is null

- The field must not be `private` (the processor rejects private fields).
- The processor must have generated the controller's factory.
- The FXML must declare `fx:controller` (or you must call `loadView(Class)`).

### Reload not triggering

1. Confirm `fxLoader.isDevelopmentMode()`.
2. Confirm the watched directories exist and are readable.
3. Ensure the bean name matches the reloaded controller.

### ClassLoader leaks

1. Always call `stopFileWatching()`.
2. Avoid long-lived references to controller instances.
3. Unregister root replacers when views are disposed.

## See Also

- [Annotations Reference](annotations.md)
- [Architecture](architecture.md)
- [Scopes and Lifecycle](scopes-and-lifecycle.md)
- [Compile-Time Processing](compile-time-processing.md)
