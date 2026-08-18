package com.warmup.core.registry;

import java.util.Optional;
import java.util.Set;

/**
 * Thread-safe bean registry with O(1) lookup using ConcurrentHashMap.
 * Supports singleton caching and prototype factory storage.
 */
public interface BeanRegistry {
    
    /**
     * Registers a bean definition in the registry.
     * 
     * @param <T> the bean type
     * @param definition the bean definition
     * @throws IllegalStateException if a bean with the same name already exists
     */
    <T> void register(BeanDefinition<T> definition);

    /**
     * Retrieves a bean definition by name.
     * 
     * @param <T> the bean type
     * @param name the bean name
     * @return Optional containing the bean definition if found
     */
    <T> Optional<BeanDefinition<T>> getDefinition(String name);

    /**
     * Retrieves a bean definition by type.
     * 
     * @param <T> the bean type
     * @param type the bean class
     * @return Optional containing the bean definition if found
     */
    <T> Optional<BeanDefinition<T>> getDefinitionByType(Class<T> type);

    /**
     * Gets or creates a bean instance based on its scope.
     * - Singleton: returns cached instance (creates if not exists)
     * - Prototype: creates new instance every time
     * 
     * @param <T> the bean type
     * @param name the bean name
     * @param factory factory to create the instance if needed
     * @return the bean instance
     */
    <T> T getInstance(String name, java.util.function.Supplier<T> factory);

    /**
     * Checks if a singleton instance is already cached.
     * 
     * @param name the bean name
     * @return true if the singleton is cached
     */
    boolean hasInstance(String name);

    /**
     * Removes a bean from the registry (including cached instances).
     * Used for hot-reload and testing scenarios.
     * 
     * @param name the bean name
     * @return true if the bean was removed
     */
    boolean remove(String name);

    /**
     * Clears all beans from the registry.
     * Used primarily for testing container reset.
     */
    void clear();

    /**
     * Returns the number of registered beans.
     * 
     * @return the registry size
     */
    int size();

    /**
     * Checks if the registry contains a bean with the given name.
     * 
     * @param name the bean name
     * @return true if the bean exists
     */
    boolean contains(String name);
    
    /**
     * Returns all registered bean names.
     * 
     * @return set of bean names
     */
    Set<String> getAllNames();
    
    /**
     * Returns all registered bean names (alias for getAllNames).
     * Used by HybridContainer.getBeanNames().
     * 
     * @return set of bean names
     */
    Set<String> getBeanNames();
    
    /**
     * Gets a cached singleton instance if present, without triggering creation.
     * This is a fast-path method for hot resolution scenarios.
     * 
     * @param <T> the bean type
     * @param name the bean name
     * @return the cached instance or null if not present/not a singleton
     */
    @SuppressWarnings("unchecked")
    default <T> T getIfPresent(String name) {
        // Default implementation returns null - to be overridden by implementations
        return null;
    }
}
