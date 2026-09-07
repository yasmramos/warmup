package com.warmup.core.scope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scope handler that caches bean instances per session context.
 * Uses a ThreadLocal to simulate session storage for the current thread.
 * Beans are created once per session and reused within the same session.
 */
public class SessionScopeHandler implements ScopeHandler {

    private static final ThreadLocal<Map<String, Object>> SESSION_CONTEXT =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * Retrieves a bean instance from the current session context.
     * If not present, creates a new one using the factory and caches it.
     *
     * @param beanName the name of the bean
     * @param factory  the supplier to create a new instance if needed
     * @param <T>      the type of the bean
     * @return the bean instance
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String beanName, java.util.function.Supplier<T> factory) {
        Map<String, Object> context = SESSION_CONTEXT.get();
        return (T) context.computeIfAbsent(beanName, k -> factory.get());
    }

    /**
     * Removes a bean instance from the current session context.
     *
     * @param beanName the name of the bean to remove
     */
    @Override
    public void remove(String beanName) {
        Map<String, Object> context = SESSION_CONTEXT.get();
        context.remove(beanName);
    }

    /**
     * Destroys all bean instances in the current session context.
     * Clears the ThreadLocal map for the current thread.
     */
    @Override
    public void destroy() {
        SESSION_CONTEXT.remove();
    }

    /**
     * Starts a new session context for the current thread.
     * This should be called when a session begins.
     */
    public static void beginSession() {
        SESSION_CONTEXT.set(new ConcurrentHashMap<>());
    }

    /**
     * Ends the session context for the current thread.
     * This should be called when a session ends or is invalidated.
     */
    public static void endSession() {
        SESSION_CONTEXT.remove();
    }
}
