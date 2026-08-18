package com.warmup.core.lifecycle;

/**
 * Combined lifecycle callbacks for beans.
 * 
 * @param <T> the bean type
 */
public record LifecycleCallbacks<T>(
    InitCallback<T> onInit,
    DestroyCallback<T> onDestroy
) {
    /**
     * Creates lifecycle callbacks with only init handler.
     */
    public static <T> LifecycleCallbacks<T> initOnly(InitCallback<T> onInit) {
        return new LifecycleCallbacks<>(onInit, null);
    }

    /**
     * Creates lifecycle callbacks with only destroy handler.
     */
    public static <T> LifecycleCallbacks<T> destroyOnly(DestroyCallback<T> onDestroy) {
        return new LifecycleCallbacks<>(null, onDestroy);
    }

    /**
     * Creates empty lifecycle callbacks (no handlers).
     */
    public static <T> LifecycleCallbacks<T> empty() {
        return new LifecycleCallbacks<>(null, null);
    }
}
