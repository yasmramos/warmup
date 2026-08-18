package com.warmup.core.lifecycle;

/**
 * Lifecycle callback interface for beans requiring cleanup before disposal.
 * 
 * @param <T> the bean type
 */
@FunctionalInterface
public interface DestroyCallback<T> {
    /**
     * Called before bean destruction (e.g., container shutdown).
     * 
     * @param instance the bean instance to destroy
     */
    void onDestroy(T instance);
}
