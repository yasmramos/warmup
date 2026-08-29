package com.warmup.annotations;

/**
 * A provider interface for lazy dependency injection.
 * 
 * <p>Implementations of this interface defer bean instantiation until {@link #get()} is called.
 * Each call to {@code get()} respects the scope of the underlying bean:
 * </p>
 * <ul>
 *   <li>Singleton: returns the same instance on every call</li>
 *   <li>Prototype: creates a new instance on every call</li>
 * </ul>
 * 
 * <p>This enables breaking circular dependencies and deferring expensive bean creation.</p>
 * 
 * @param <T> the type of bean provided
 */
@FunctionalInterface
public interface Provider<T> {
    
    /**
     * Returns the bean instance, creating it if necessary according to its scope.
     * 
     * @return the bean instance
     */
    T get();
}
