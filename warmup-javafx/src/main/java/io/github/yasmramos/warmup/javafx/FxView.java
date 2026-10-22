package io.github.yasmramos.warmup.javafx;

import javafx.scene.Parent;

import java.util.ResourceBundle;

/**
 * A loaded FXML view: the scene graph root together with the controller
 * instance that the Warmup container created and injected.
 *
 * <p>Prefer this over {@link FxLoader#getCachedController(Class)}: controllers
 * default to {@code PROTOTYPE} scope and are therefore never cached, so the
 * cache lookup would return {@code null}.</p>
 *
 * @param <T> controller type, or {@link Object} when loading by FXML path
 */
public final class FxView<T> {

    private final Parent root;
    private final T controller;
    private final String fxmlPath;
    private final Class<?> controllerClass;
    private final ResourceBundle resourceBundle;

    FxView(Parent root, T controller, String fxmlPath, Class<?> controllerClass, ResourceBundle resourceBundle) {
        this.root = root;
        this.controller = controller;
        this.fxmlPath = fxmlPath;
        this.controllerClass = controllerClass;
        this.resourceBundle = resourceBundle;
    }

    /**
     * The loaded scene graph root.
     *
     * @return the root node
     */
    public Parent root() {
        return root;
    }

    /**
     * The controller instance created and injected by the container, or
     * {@code null} when the FXML declares no controller.
     *
     * @return the controller instance
     */
    public T controller() {
        return controller;
    }

    /**
     * The FXML path this view was loaded from.
     *
     * @return the FXML path
     */
    public String fxmlPath() {
        return fxmlPath;
    }

    /**
     * The controller class used to resolve the FXML, or {@code null} for
     * path-based loads.
     *
     * @return the controller class
     */
    public Class<?> controllerClass() {
        return controllerClass;
    }

    /**
     * The resource bundle used for localization, or {@code null}.
     *
     * @return the resource bundle
     */
    public ResourceBundle resourceBundle() {
        return resourceBundle;
    }
}
