package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.core.scope.ScopeHandler;
import javafx.stage.Window;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Scope handler for beans bound to a JavaFX {@link Window} ({@link WindowScoped}).
 *
 * <p>Each window owns one context. The {@link FxLoader} makes the context of
 * the window current while its views are loaded and while
 * {@link FxLoader#withWindow(Window, Runnable)} runs. Contexts are destroyed
 * when the window is closed.</p>
 */
public final class WindowScopeHandler implements ScopeHandler {

    private final Map<Window, ScopedBeanStore> scopes = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<ScopedBeanStore>> activeScopes = new ThreadLocal<>();

    /**
     * Returns the context for the given window, creating it on first use.
     */
    ScopedBeanStore scopeFor(Window window) {
        if (window == null) {
            throw new IllegalArgumentException("window cannot be null");
        }
        return scopes.computeIfAbsent(window, key -> new ScopedBeanStore());
    }

    void activate(ScopedBeanStore scope) {
        if (scope == null) {
            return;
        }
        Deque<ScopedBeanStore> stack = activeScopes.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            activeScopes.set(stack);
        }
        stack.push(scope);
    }

    void deactivate(ScopedBeanStore scope) {
        Deque<ScopedBeanStore> stack = activeScopes.get();
        if (stack == null) {
            return;
        }
        if (!stack.isEmpty() && stack.peek() == scope) {
            stack.pop();
        } else {
            stack.remove(scope);
        }
        if (stack.isEmpty()) {
            activeScopes.remove();
        }
    }

    boolean isActive(ScopedBeanStore scope) {
        Deque<ScopedBeanStore> stack = activeScopes.get();
        return stack != null && stack.contains(scope);
    }

    ScopedBeanStore currentScope() {
        Deque<ScopedBeanStore> stack = activeScopes.get();
        return (stack == null || stack.isEmpty()) ? null : stack.peek();
    }

    /**
     * Destroys the context of a window. Called when the window is hidden.
     */
    void close(Window window) {
        ScopedBeanStore scope = scopes.remove(window);
        if (scope != null) {
            deactivate(scope);
            scope.close();
        }
    }

    /**
     * Destroys every window context created by this handler.
     */
    void closeAll() {
        for (Window window : scopes.keySet()) {
            close(window);
        }
    }

    int scopeCount() {
        return scopes.size();
    }

    @Override
    public <T> T get(String beanName, Supplier<T> factory) {
        ScopedBeanStore scope = currentScope();
        if (scope == null) {
            throw new IllegalStateException(
                    "No active WINDOW scope while resolving '" + beanName + "'. "
                    + "Load the view inside WarmupApplication#onStart, or wrap the resolution "
                    + "in FxLoader#withWindow(window, ...).");
        }
        return scope.get(beanName, factory);
    }

    @Override
    public void remove(String beanName) {
        ScopedBeanStore scope = currentScope();
        if (scope != null) {
            scope.remove(beanName);
        }
    }

    @Override
    public void destroy() {
        ScopedBeanStore scope = currentScope();
        if (scope != null) {
            deactivate(scope);
            scope.close();
            scopes.values().remove(scope);
        }
    }
}
