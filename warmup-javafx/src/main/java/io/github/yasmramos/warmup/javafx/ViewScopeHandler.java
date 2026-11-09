package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.core.scope.ScopeHandler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Scope handler for beans bound to a single FXML view ({@link ViewScoped}).
 *
 * <p>The handler keeps a stack of active view scopes per thread. The
 * {@link FxLoader} activates a fresh scope while an FXML is being loaded and
 * keeps it alive for the lifetime of the resulting {@link FxView}. Resolving a
 * view-scoped bean without an active scope fails fast with a descriptive
 * exception instead of silently creating a prototype.</p>
 */
public final class ViewScopeHandler implements ScopeHandler {

    private final ThreadLocal<Deque<ScopedBeanStore>> activeScopes = new ThreadLocal<>();
    private final Set<ScopedBeanStore> scopes = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new, detached view scope. It must be activated before use.
     */
    ScopedBeanStore createScope() {
        ScopedBeanStore scope = new ScopedBeanStore();
        scopes.add(scope);
        return scope;
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

    ScopedBeanStore currentScope() {
        Deque<ScopedBeanStore> stack = activeScopes.get();
        return (stack == null || stack.isEmpty()) ? null : stack.peek();
    }

    /**
     * Destroys one view scope and all beans it owns.
     */
    void destroyScope(ScopedBeanStore scope) {
        if (scope == null) {
            return;
        }
        scopes.remove(scope);
        scope.close();
    }

    /**
     * Destroys every view scope created by this handler.
     */
    void destroyAll() {
        for (ScopedBeanStore scope : scopes) {
            destroyScope(scope);
        }
        scopes.clear();
    }

    int scopeCount() {
        return scopes.size();
    }

    @Override
    public <T> T get(String beanName, Supplier<T> factory) {
        ScopedBeanStore scope = currentScope();
        if (scope == null) {
            throw new IllegalStateException(
                    "No active VIEW scope while resolving '" + beanName + "'. "
                    + "View-scoped beans can only be resolved while a view is being loaded "
                    + "(or by injecting them into a controller/view model).");
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
            destroyScope(scope);
        }
    }
}