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
     * Cached indices for dependencies that are String bean names.
     * For each dependency: if it's a String name, stores the resolved bean index;
     * if it's a direct object reference, stores -2 (special marker).
     * Array length matches dependencies.length. -1 means not yet resolved.
     */
    private final int[] dependencyIndices;
    /**
     * Profile names for conditional registration. Empty array means no profile restriction.
     */
    private final String[] profiles;
    /**
     * Fully qualified class names of Condition implementations for conditional registration.
     * Empty array means no condition restriction.
     */
    private final String[] conditionClasses;
    
    /**
     * Creates a bean definition with default values.
     */
    public BeanDefinition(Class<T> type, String name) {
        this(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(), false, new Object[0], new String[0], new String[0]);
    }

    /**
     * Creates a bean definition with custom scope.
     */
    public BeanDefinition(Class<T> type, String name, Scope scope) {
        this(type, name, scope, LifecycleCallbacks.empty(), false, new Object[0], new String[0], new String[0]);
    }
    
    /**
     * Creates a bean definition with all parameters.
     */
    public BeanDefinition(Class<T> type, String name, Scope scope, LifecycleCallbacks<T> lifecycle, 
                          boolean isPrimary, Object[] dependencies) {
        this(type, name, scope, lifecycle, isPrimary, dependencies, new String[0], new String[0]);
    }
    
    /**
     * Creates a bean definition with all parameters including conditional registration metadata.
     * 
     * @param type the bean type
     * @param name the bean name
     * @param scope the bean scope
     * @param lifecycle lifecycle callbacks
     * @param isPrimary whether this is a primary bean
     * @param dependencies array of dependencies (bean names or ValueDependency objects)
     * @param profiles array of profile names for conditional registration
     * @param conditionClasses array of fully qualified condition class names
     */
    public BeanDefinition(Class<T> type, String name, Scope scope, LifecycleCallbacks<T> lifecycle, 
                          boolean isPrimary, Object[] dependencies, String[] profiles, String[] conditionClasses) {
        this.type = type;
        this.name = name;
        this.scope = scope;
        this.lifecycle = lifecycle;
        this.isPrimary = isPrimary;
        this.dependencies = dependencies;
        this.profiles = profiles != null ? profiles : new String[0];
        this.conditionClasses = conditionClasses != null ? conditionClasses : new String[0];
        this.dependencyIndices = new int[dependencies.length];
        // Initialize all indices to -1 (not yet resolved)
        java.util.Arrays.fill(this.dependencyIndices, -1);
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
    
    /**
     * Gets the array of cached dependency indices.
     * @return the dependency indices array (mutable for lazy resolution)
     */
    public int[] dependencyIndices() {
        return dependencyIndices;
    }
    
    /**
     * Checks if a dependency at the given index is a String name that needs resolution.
     * @param depIndex the index in the dependencies array
     * @return true if the dependency is a String name, false if it's a direct object reference
     */
    public boolean isDependencyName(int depIndex) {
        return depIndex >= 0 && depIndex < dependencies.length && dependencies[depIndex] instanceof String;
    }
    
    /**
     * Returns the array of profile names for conditional registration.
     * @return the profiles array (empty if no profile restriction)
     */
    public String[] profiles() {
        return profiles;
    }
    
    /**
     * Returns the array of fully qualified condition class names for conditional registration.
     * @return the condition classes array (empty if no condition restriction)
     */
    public String[] conditionClasses() {
        return conditionClasses;
    }
}
