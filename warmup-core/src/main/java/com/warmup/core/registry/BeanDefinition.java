package com.warmup.core.registry;

import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.scope.BeanScope;

/**
 * Bean definition containing metadata for instantiation and lifecycle management.
 * 
 * @param <T> the bean type
 */
public record BeanDefinition<T>(
    Class<T> type,
    String name,
    BeanScope scope,
    LifecycleCallbacks<T> lifecycle,
    boolean isPrimary,
    Object[] dependencies
) {
    /**
     * Creates a bean definition with default values.
     */
    public BeanDefinition(Class<T> type, String name) {
        this(type, name, BeanScope.SINGLETON, LifecycleCallbacks.empty(), false, new Object[0]);
    }

    /**
     * Creates a bean definition with custom scope.
     */
    public BeanDefinition(Class<T> type, String name, BeanScope scope) {
        this(type, name, scope, LifecycleCallbacks.empty(), false, new Object[0]);
    }

    /**
     * Checks if this bean requires lifecycle management.
     */
    public boolean hasLifecycle() {
        return lifecycle.onInit() != null || lifecycle.onDestroy() != null;
    }
}
