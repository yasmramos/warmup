package io.github.yasmramos.warmup.javafx;

import javafx.scene.Parent;

import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A loaded FXML view: the scene graph root together with the controller
 * instance that the Warmup container created and injected.
 *
 * <p>Prefer this over {@link FxLoader#getCachedController(Class)}: controllers
 * default to {@code PROTOTYPE} scope and are therefore never cached, so the
 * cache lookup would return {@code null}.</p>
 *
 * <p>A view owns its view scope. Call {@link #close()} when the view is no
 * longer displayed to destroy its {@code @ViewScoped} beans (invoking
 * {@code @PreDestroy} or {@link AutoCloseable#close()}).</p>
 *
 * @param <T> controller type, or {@link Object} when loading by FXML path
 */
public final class FxView<T> implements AutoCloseable {

    private final Parent root;
    private final T controller;
    private final String fxmlPath;
    private final Class<?> controllerClass;
    private final ResourceBundle resourceBundle;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();

    FxView(Parent root, T controller, String fxmlPath, Class<?> controllerClass,
           ResourceBundle resourceBundle, Runnable onClose) {
        this.root = root;
        this.controller = controller;
        this.fxmlPath = fxmlPath;
        this.controllerClass = controllerClass;
        this.resourceBundle = resourceBundle;
        this.onClose = onClose;
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

    /**
     * Whether this view has already been closed.
     *
     * @return {@code true} after {@link #close()}
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Destroys the view scope owned by this view. Calling it more than once has
     * no additional effect.
     *
     * <p>This is idempotent and safe to call after a hot-reload already
     * destroyed the scope.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && onClose != null) {
            onClose.run();
        }
    }
}
