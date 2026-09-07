package com.warmup.core.scope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scope handler that caches bean instances per request context.
 * Uses a ThreadLocal to store beans for the current request thread.
 * Beans are created once per request and reused within the same request.
 */
public class RequestScopeHandler implements ScopeHandler {

    private static final ThreadLocal<Map<String, Object>> REQUEST_CONTEXT =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * Retrieves a bean instance from the current request context.
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
        Map<String, Object> context = REQUEST_CONTEXT.get();
        return (T) context.computeIfAbsent(beanName, k -> factory.get());
    }

    /**
     * Removes a bean instance from the current request context.
     *
     * @param beanName the name of the bean to remove
     */
    @Override
    public void remove(String beanName) {
        Map<String, Object> context = REQUEST_CONTEXT.get();
        context.remove(beanName);
    }

    /**
     * Destroys all bean instances in the current request context.
     * Clears the ThreadLocal map for the current thread.
     */
    @Override
    public void destroy() {
        REQUEST_CONTEXT.remove();
    }

    /**
     * Starts a new request context for the current thread.
     * This should be called at the beginning of a request.
     */
    public static void beginRequest() {
        REQUEST_CONTEXT.set(new ConcurrentHashMap<>());
    }

    /**
     * Ends the request context for the current thread.
     * This should be called at the end of a request.
     */
    public static void endRequest() {
        REQUEST_CONTEXT.remove();
    }
}
