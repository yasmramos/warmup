package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.annotations.PreDestroy;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.scope.Scope;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the VIEW and WINDOW custom scopes.
 */
class UiScopesTest extends ApplicationTest {

    private static final AtomicInteger VIEW_CREATED = new AtomicInteger();
    private static final AtomicInteger VIEW_CLOSED = new AtomicInteger();
    private static final AtomicInteger PRE_DESTROYED = new AtomicInteger();
    private static final AtomicInteger WINDOW_CREATED = new AtomicInteger();
    private static final AtomicInteger WINDOW_CLOSED = new AtomicInteger();

    private Warmup warmup;
    private FxLoader fxLoader;

    @BeforeEach
    void setUp() {
        VIEW_CREATED.set(0);
        VIEW_CLOSED.set(0);
        PRE_DESTROYED.set(0);
        WINDOW_CREATED.set(0);
        WINDOW_CLOSED.set(0);

        warmup = Warmup.builder().build();
        fxLoader = new FxLoader(warmup, false);

        warmup.register("viewScopedBean", ViewScopedBean.class, () -> {
            VIEW_CREATED.incrementAndGet();
            return new ViewScopedBean();
        }, Scope.CUSTOM, UiScopes.VIEW);

        warmup.register("preDestroyBean", PreDestroyBean.class, () -> {
            return new PreDestroyBean();
        }, Scope.CUSTOM, UiScopes.VIEW);

        warmup.register("windowScopedBean", WindowScopedBean.class, () -> {
            WINDOW_CREATED.incrementAndGet();
            return new WindowScopedBean();
        }, Scope.CUSTOM, UiScopes.WINDOW);

        warmup.register("viewScopeController", ViewScopeController.class,
                () -> new ViewScopeController(warmup), Scope.PROTOTYPE);

        warmup.register("windowScopeController", WindowScopeController.class,
                () -> new WindowScopeController(warmup), Scope.PROTOTYPE);
    }

    @Test
    void sameViewScopedInstanceIsReusedWithinOneView() throws Exception {
        FxView<ViewScopeController> view = fxLoader.loadView(ViewScopeController.class);

        assertSame(view.controller().first, view.controller().second,
                "Both resolutions inside one view must return the same instance");
        assertEquals(1, VIEW_CREATED.get());
        assertFalse(view.isClosed());

        view.close();
        assertTrue(view.isClosed());
    }

    @Test
    void eachViewGetsItsOwnScope() throws Exception {
        FxView<ViewScopeController> first = fxLoader.loadView(ViewScopeController.class);
        FxView<ViewScopeController> second = fxLoader.loadView(ViewScopeController.class);

        assertNotSame(first.controller().first, second.controller().first,
                "Different views must not share view-scoped beans");
        assertEquals(2, VIEW_CREATED.get());

        first.close();
        second.close();
    }

    @Test
    void closingViewInvokesPreDestroyAndAutoCloseable() throws Exception {
        FxView<ViewScopeController> view = fxLoader.loadView(ViewScopeController.class);

        assertEquals(0, VIEW_CLOSED.get());
        assertEquals(0, PRE_DESTROYED.get());

        view.close();

        assertEquals(1, VIEW_CLOSED.get(), "AutoCloseable.close() should be called");
        assertEquals(1, PRE_DESTROYED.get(), "@PreDestroy should be called");

        // Idempotent
        view.close();
        assertEquals(1, VIEW_CLOSED.get());
        assertEquals(1, PRE_DESTROYED.get());
    }

    @Test
    void resolvingViewScopedBeanOutsideViewFails() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> warmup.resolve(ViewScopedBean.class));
        assertTrue(error.getMessage().contains("VIEW"), error.getMessage());
    }

    @Test
    void hotReloadDestroysPreviousViewScope() throws Exception {
        FxView<ViewScopeController> view = fxLoader.loadView(ViewScopeController.class);
        assertEquals(1, VIEW_CREATED.get());
        assertEquals(0, VIEW_CLOSED.get());

        Parent reloaded = fxLoader.reloadFxml(view.fxmlPath());
        assertNotSame(view.root(), reloaded);

        assertEquals(1, VIEW_CLOSED.get(), "Old view scope should be destroyed on reload");
        assertEquals(2, VIEW_CREATED.get(), "Reloaded view should get a fresh view scope");

        // The old FxView is already closed, so closing it again is a no-op.
        view.close();
        assertEquals(1, VIEW_CLOSED.get());
    }

    @Test
    void windowScopeIsSharedByViewsOfTheSameWindow() throws Exception {
        Stage[] holder = new Stage[1];
        interact(() -> holder[0] = new Stage());
        Stage stage = holder[0];

        try {
            FxView<WindowScopeController> first = fxLoader.loadView(WindowScopeController.class, stage);
            FxView<WindowScopeController> second = fxLoader.loadView(WindowScopeController.class, stage);

            assertSame(first.controller().windowBean, second.controller().windowBean,
                    "Window-scoped beans must be shared by views of the same window");
            assertNotSame(first.controller().viewBean, second.controller().viewBean,
                    "View scopes remain independent inside one window");
            assertEquals(1, WINDOW_CREATED.get());

            fxLoader.closeWindow(stage);
            assertEquals(1, WINDOW_CLOSED.get(), "Window scope should be destroyed with the window");

            first.close();
            second.close();
            assertEquals(2, VIEW_CLOSED.get(),
                    "Each view owns and destroys its own view scope");
        } finally {
            interact(stage::close);
        }
    }

    @Test
    void withWindowActivationResolvesAndCachesWindowScopedBeans() {
        Stage[] holder = new Stage[1];
        interact(() -> holder[0] = new Stage());
        Stage stage = holder[0];

        try {
            WindowScopedBean[] first = new WindowScopedBean[1];
            WindowScopedBean[] second = new WindowScopedBean[1];

            fxLoader.withWindow(stage, () -> first[0] = warmup.resolve(WindowScopedBean.class));
            fxLoader.withWindow(stage, () -> second[0] = warmup.resolve(WindowScopedBean.class));

            assertSame(first[0], second[0]);
            assertEquals(1, WINDOW_CREATED.get());

            fxLoader.closeWindow(stage);
            assertEquals(1, WINDOW_CLOSED.get());
        } finally {
            interact(stage::close);
        }
    }

    public static class ViewScopedBean implements AutoCloseable {

        @Override
        public void close() {
            VIEW_CLOSED.incrementAndGet();
        }
    }

    public static class PreDestroyBean {

        @PreDestroy
        void destroy() {
            PRE_DESTROYED.incrementAndGet();
        }
    }

    public static class WindowScopedBean implements AutoCloseable {

        @Override
        public void close() {
            WINDOW_CLOSED.incrementAndGet();
        }
    }

    @FxController(fxml = "/io/github/yasmramos/warmup/javafx/view-scope.fxml")
    public static class ViewScopeController {

        final ViewScopedBean first;
        final ViewScopedBean second;
        final PreDestroyBean preDestroyBean;

        ViewScopeController(Warmup warmup) {
            this.first = warmup.resolve(ViewScopedBean.class);
            this.second = warmup.resolve(ViewScopedBean.class);
            this.preDestroyBean = warmup.resolve(PreDestroyBean.class);
        }
    }

    @FxController(fxml = "/io/github/yasmramos/warmup/javafx/window-scope.fxml")
    public static class WindowScopeController {

        final ViewScopedBean viewBean;
        final WindowScopedBean windowBean;

        WindowScopeController(Warmup warmup) {
            this.viewBean = warmup.resolve(ViewScopedBean.class);
            this.windowBean = warmup.resolve(WindowScopedBean.class);
        }
    }
}
