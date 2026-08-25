package com.warmup.core.registry;

import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.scope.Scope;

/**
 * Bean definition containing metadata for instantiation and lifecycle management.
 * 
 * @param <T> the bean type
 */
public class BeanDefinition<T> {
    private final Class<T> type;
    private final String name;
    private final Scope scope;
    private final LifecycleCallbacks<T> lifecycle;
    private final boolean isPrimary;
    private final Object[] dependencies;
    /** Cached index for fast indexed singleton resolution (-1 if not yet assigned) */
    private int cachedIndex = -1;
    
    /**
     * Creates a bean definition with default values.
     */
    public BeanDefinition(Class<T> type, String name) {
        this(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(), false, new Object[0]);
    }

    /**
     * Creates a bean definition with custom scope.
     */
    public BeanDefinition(Class<T> type, String name, Scope scope) {
        this(type, name, scope, LifecycleCallbacks.empty(), false, new Object[0]);
    }
    
    /**
     * Creates a bean definition with all parameters.
     */
    public BeanDefinition(Class<T> type, String name, Scope scope, LifecycleCallbacks<T> lifecycle, 
                          boolean isPrimary, Object[] dependencies) {
        this.type = type;
        this.name = name;
        this.scope = scope;
        this.lifecycle = lifecycle;
        this.isPrimary = isPrimary;
        this.dependencies = dependencies;
    }
    
    public Class<T> type() {
        return type;
    }
    
    public String name() {
        return name;
    }
    
    public Scope scope() {
        return scope;
    }
    
    public LifecycleCallbacks<T> lifecycle() {
        return lifecycle;
    }
    
    public boolean isPrimary() {
        return isPrimary;
    }
    
    public Object[] dependencies() {
        return dependencies;
    }
    
    /**
     * Checks if this bean requires lifecycle management.
     */
    public boolean hasLifecycle() {
        return lifecycle.onInit() != null || lifecycle.onDestroy() != null;
    }
    
    /**
     * Gets the cached index for fast indexed resolution.
     * @return the cached index, or -1 if not yet assigned
     */
    public int getCachedIndex() {
        return cachedIndex;
    }
    
    /**
     * Sets the cached index for fast indexed resolution.
     * @param index the index to cache
     */
    public void setCachedIndex(int index) {
        this.cachedIndex = index;
    }
}
