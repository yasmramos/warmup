package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.annotations.PreDestroy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the bean instances of one UI scope (one view or one window).
 *
 * <p>Instances are cached by bean name, so repeated resolutions inside the
 * same scope return the same object. On {@link #close()} every instance is
 * destroyed in reverse creation order: {@code @PreDestroy} methods are invoked
 * first; if the class declares none, {@link AutoCloseable#close()} is used.</p>
 */
final class ScopedBeanStore implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ScopedBeanStore.class.getName());
    private static final Map<Class<?>, List<Method>> PRE_DESTROY_METHODS = new ConcurrentHashMap<>();

    private final Map<String, Object> beans = new LinkedHashMap<>();
    private final AtomicBoolean destroyed = new AtomicBoolean();

    /**
     * Returns the bean with the given name, creating it on first use.
     *
     * <p>The store is synchronized because a factory may recursively resolve
     * its own dependencies. Java's intrinsic locks are reentrant, so nested
     * resolutions on the same thread work as expected.</p>
     */
    @SuppressWarnings("unchecked")
    synchronized <T> T get(String beanName, java.util.function.Supplier<T> factory) {
        if (destroyed.get()) {
            throw new IllegalStateException(
                    "Cannot resolve '" + beanName + "': this UI scope has already been closed");
        }
        Object existing = beans.get(beanName);
        if (existing != null) {
            return (T) existing;
        }
        T created = factory.get();
        beans.put(beanName, created);
        return created;
    }

    synchronized void remove(String beanName) {
        beans.remove(beanName);
    }

    synchronized boolean isEmpty() {
        return beans.isEmpty();
    }

    @Override
    public void close() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }

        List<Object> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(beans.values());
            beans.clear();
        }

        // Reverse creation order: dependents are destroyed before their dependencies.
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            destroyBean(snapshot.get(i));
        }
    }

    private static void destroyBean(Object bean) {
        if (bean == null) {
            return;
        }

        List<Method> methods = PRE_DESTROY_METHODS.computeIfAbsent(
                bean.getClass(), ScopedBeanStore::findPreDestroyMethods);

        if (methods.isEmpty()) {
            if (bean instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Exception while closing scoped bean " + bean.getClass().getName(), e);
                }
            }
            return;
        }

        for (Method method : methods) {
            try {
                method.invoke(bean);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Exception while invoking @PreDestroy on " + bean.getClass().getName(), e);
            }
        }
    }

    private static List<Method> findPreDestroyMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PreDestroy.class) && method.getParameterCount() == 0) {
                    if (!method.trySetAccessible()) {
                        LOG.log(System.Logger.Level.WARNING,
                                "Cannot access @PreDestroy method " + method + "; skipping");
                        continue;
                    }
                    methods.add(method);
                }
            }
        }
        return Collections.unmodifiableList(methods);
    }
}
