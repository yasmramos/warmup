package io.github.yasmramos.warmup.core.registry;

import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for AutoCloseable detection: a bean that implements
 * {@link AutoCloseable} but declares no {@code @PreDestroy} callback must be
 * closed when the registry evicts it, so resources are released on shutdown.
 *
 * <p>An explicit destroy callback always takes precedence: {@code close()} is
 * only used as a fallback when no callback is registered.</p>
 */
class AutoCloseableDetectionTest {

    private static BeanDefinition<Object> definition(String name, LifecycleCallbacks<Object> lifecycle) {
        return new BeanDefinition<>(Object.class, name, Scope.SINGLETON, lifecycle, false, new Object[0]);
    }

    @Test
    void clear_closesAutoCloseableBeanWithoutDestroyCallback() {
        BeanRegistryImpl registry = new BeanRegistryImpl();
        AtomicBoolean closed = new AtomicBoolean(false);
        registry.register(definition("resource", LifecycleCallbacks.empty()));
        registry.getInstance("resource", () -> new AutoCloseable() {
            @Override
            public void close() {
                closed.set(true);
            }
        });

        registry.clear();

        assertTrue(closed.get(), "An AutoCloseable singleton must be closed on clear()");
    }

    @Test
    void evictInstance_closesAutoCloseableBeanWithoutDestroyCallback() {
        BeanRegistryImpl registry = new BeanRegistryImpl();
        AtomicBoolean closed = new AtomicBoolean(false);
        registry.register(definition("resource", LifecycleCallbacks.empty()));
        registry.getInstance("resource", () -> new AutoCloseable() {
            @Override
            public void close() {
                closed.set(true);
            }
        });

        registry.evictInstance("resource");

        assertTrue(closed.get(), "An AutoCloseable singleton must be closed when evicted");
    }

    @Test
    void explicitDestroyCallbackTakesPrecedenceOverClose() {
        BeanRegistryImpl registry = new BeanRegistryImpl();
        List<String> events = new ArrayList<>();
        registry.register(definition("resource",
                new LifecycleCallbacks<>(o -> {
                }, o -> events.add("destroy"))));
        registry.getInstance("resource", () -> new AutoCloseable() {
            @Override
            public void close() {
                events.add("close");
            }
        });

        registry.clear();

        assertEquals(List.of("destroy"), events,
                "The @PreDestroy callback must run instead of close()");
        assertFalse(events.contains("close"),
                "close() must not be called when an explicit destroy callback exists");
    }

    @Test
    void nonCloseableBean_isLeftUntouched() {
        BeanRegistryImpl registry = new BeanRegistryImpl();
        registry.register(definition("plain", LifecycleCallbacks.empty()));
        Object instance = new Object();
        registry.getInstance("plain", () -> instance);

        // Must not throw and must simply drop the instance.
        registry.clear();

        assertFalse(registry.hasInstance("plain"));
    }
}
