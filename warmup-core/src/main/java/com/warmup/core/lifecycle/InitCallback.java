package com.warmup.core.lifecycle;

/**
 * Lifecycle callback interface for beans requiring initialization or cleanup.
 * 
 * @param <T> the bean type
 */
@FunctionalInterface
public interface InitCallback<T> {
    /**
     * Called after bean instantiation and dependency injection.
     * 
     * @param instance the bean instance to initialize
     */
    void onInit(T instance);
}
