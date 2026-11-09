package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.scope.Scope;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Reproduces the lifecycle of {@link WarmupApplication#start(Stage)} on a real
 * JavaFX thread and verifies that the window scope is active while
 * {@code onStart} runs (the primary stage path).
 */
class WindowScopeLifecycleTest extends ApplicationTest {

    private static final AtomicReference<Thread> ON_START_THREAD = new AtomicReference<>();

    @Test
    void onStartRunsOnFxThreadAndSharesWindowScope() throws Exception {
        StartApp app = new StartApp();
        app.init();

        Stage[] holder = new Stage[1];
        AtomicReference<Thread> fxThread = new AtomicReference<>();
        interact(() -> {
            try {
                fxThread.set(Thread.currentThread());
                holder[0] = new Stage();
                app.start(holder[0]);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertSame(fxThread.get(), ON_START_THREAD.get(),
                "WarmupApplication.start must run on the JavaFX Application Thread");
        assertEquals(1, StartApp.CREATED.get(), "window-scoped bean should be created once");
        assertSame(StartApp.FIRST.get(), StartApp.SECOND.get(),
                "both resolutions inside start() must share the window scope");

        interact(() -> {
            try {
                app.stop();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            holder[0].close();
        });
    }

    static class StartApp extends WarmupApplication {

        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicReference<WindowScopedBean> FIRST = new AtomicReference<>();
        static final AtomicReference<WindowScopedBean> SECOND = new AtomicReference<>();

        @Override
        protected void registerBeans(Warmup warmup) {
            warmup.register("windowScopedBean", WindowScopedBean.class, () -> {
                CREATED.incrementAndGet();
                return new WindowScopedBean();
            }, Scope.CUSTOM, UiScopes.WINDOW);
        }

        @Override
        protected void onStart(Stage stage) {
            ON_START_THREAD.set(Thread.currentThread());
            FIRST.set(getWarmup().resolve(WindowScopedBean.class));
            SECOND.set(getWarmup().resolve(WindowScopedBean.class));
        }

        @Override
        protected void onStop() {
        }
    }

    public static class WindowScopedBean {
    }
}
