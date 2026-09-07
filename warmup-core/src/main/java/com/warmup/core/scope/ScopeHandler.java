package com.warmup.core.scope;

/**
 * Interface for custom scope handlers.
 * Each scope handler is responsible for caching or creating bean instances
 * according to its specific semantics (e.g., request, session, thread-local).
 */
public interface ScopeHandler {

    /**
     * Retrieves an instance of the bean associated with the given name.
     * If the instance already exists in the scope, it returns the cached instance.
     * Otherwise, it uses the factory to create a new one and caches it.
     *
     * @param beanName the name of the bean
     * @param factory  the supplier to create a new instance if needed
     * @param <T>      the type of the bean
     * @return the bean instance
     */
    <T> T get(String beanName, java.util.function.Supplier<T> factory);

    /**
     * Removes a bean instance from the scope.
     *
     * @param beanName the name of the bean to remove
     */
    void remove(String beanName);

    /**
     * Destroys all bean instances managed by this scope handler.
     * This method is typically called when the scope context ends
     * (e.g., end of HTTP request, session invalidation).
     */
    void destroy();
}
