package com.warmup.core.registry;

import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.scope.Scope;

/**
 * Extended bean definition with cached resolution data for performance optimization.
 * 
 * This class wraps a BeanDefinition and adds mutable fields for caching:
 * - resolvedIndex: cached index for fast indexed singleton resolution
 * - resolvedFactory: cached CompiledFactory to avoid repeated factoryCache lookups
 * 
 * Thread Safety:
 * - Fields use volatile for safe publication across threads
 * - CAS operations ensure only one thread sets the cached values
 * 
 * @param <T> the bean type
 */
public class ResolvedBeanDefinition<T> {
    
    /** The original immutable bean definition */
    private final BeanDefinition<T> definition;
    
    /** Cached index for fast indexed singleton resolution (-1 if not yet resolved) */
    private volatile int resolvedIndex = -1;
    
    /** Cached CompiledFactory to avoid repeated factoryCache lookups */
    private volatile CompiledFactory<T> resolvedFactory;
    
    /** Indicates if this bean comes from a compile-time generated factory */
    private volatile boolean compileTime = false;
    
    /** Indicates that factory.wire() was successfully applied and factory.get() is safe to call */
    private volatile boolean wired = false;
    
    /** Pre-computed flag: true if this bean is PROTOTYPE scope, false otherwise */
    private final boolean isPrototype;
    
    /** Pre-computed flag: true if this bean has pending warmup, false otherwise */
    private volatile boolean hasPendingWarmup = false;
    
    /** Cached singleton instance for fast-path resolution (null for PROTOTYPE scope) */
    private volatile T cachedInstance;
    
    public ResolvedBeanDefinition(BeanDefinition<T> definition) {
        this.definition = definition;
        this.isPrototype = definition != null && definition.scope() == Scope.PROTOTYPE;
    }
    
    /**
     * Creates a ResolvedBeanDefinition with pre-cached index.
     * This constructor avoids the lazy index computation overhead.
     * 
     * @param definition the original bean definition
     * @param index the pre-computed bean index
     */
    public ResolvedBeanDefinition(BeanDefinition<T> definition, int index) {
        this.definition = definition;
        this.resolvedIndex = index;
        this.isPrototype = definition.scope() == Scope.PROTOTYPE;
    }
    
    /**
     * Gets the original bean definition.
     */
    public BeanDefinition<T> getDefinition() {
        return definition;
    }
    
    /**
     * Gets or computes the cached index using CAS for thread-safe lazy initialization.
     */
    public int getOrComputeIndex(BeanRegistry registry) {
        int index = resolvedIndex;
        if (index == -1) {
            // Compute index and attempt to cache it
            int computedIndex = registry.indexOf(definition.name());
            // Use compareAndSet equivalent via VarHandle or synchronized for atomic update
            // Since we can't use CAS on volatile int directly, use synchronized for safety
            synchronized (this) {
                index = resolvedIndex;
                if (index == -1) {
                    resolvedIndex = computedIndex;
                    index = computedIndex;
                }
            }
        }
        return index;
    }
    
    /**
     * Gets or computes the cached factory using CAS for thread-safe lazy initialization.
     */
    @SuppressWarnings("unchecked")
    public CompiledFactory<T> getOrComputeFactory(java.util.Map<String, CompiledFactory<?>> factoryCache) {
        CompiledFactory<T> factory = resolvedFactory;
        if (factory == null) {
            // Compute factory lookup and attempt to cache it
            CompiledFactory<?> computedFactory = factoryCache.get(definition.name());
            // Use synchronized for thread-safe publication
            synchronized (this) {
                factory = resolvedFactory;
                if (factory == null) {
                    resolvedFactory = (CompiledFactory<T>) computedFactory;
                    factory = resolvedFactory;
                }
            }
        }
        return factory;
    }
    
    /**
     * Directly sets the cached factory (used when factory is first discovered).
     */
    public void setResolvedFactory(CompiledFactory<T> factory) {
        this.resolvedFactory = factory;
    }
    
    /**
     * Checks if this bean comes from a compile-time generated factory.
     * @return true if compile-time, false otherwise
     */
    public boolean isCompileTime() {
        return compileTime;
    }
    
    /**
     * Sets whether this bean comes from a compile-time generated factory.
     * @param compileTime true if compile-time, false otherwise
     */
    public void setCompileTime(boolean compileTime) {
        this.compileTime = compileTime;
    }
    
    /**
     * Checks if the factory has been successfully wired and factory.get() is safe to call.
     * @return true if wired, false otherwise
     */
    public boolean isWired() {
        return wired;
    }
    
    /**
     * Sets whether the factory has been successfully wired.
     * @param wired true if wired, false otherwise
     */
    public void setWired(boolean wired) {
        this.wired = wired;
    }
    
    /**
     * Gets the cached singleton instance for fast-path resolution.
     * Returns null if not yet cached or if this is a PROTOTYPE bean.
     * 
     * @return the cached instance, or null if not available
     */
    public T getCachedInstance() {
        return cachedInstance;
    }
    
    /**
     * Sets the cached singleton instance after first creation.
     * Only called for SINGLETON/CUSTOM scope beans.
     * 
     * @param instance the created singleton instance
     */
    public void setCachedInstance(T instance) {
        this.cachedInstance = instance;
    }
    
    // Delegate all BeanDefinition methods for transparency
    
    public Class<T> type() {
        return definition.type();
    }
    
    public String name() {
        return definition.name();
    }
    
    public Scope scope() {
        return definition.scope();
    }
    
    public LifecycleCallbacks<T> lifecycle() {
        return definition.lifecycle();
    }
    
    public boolean isPrimary() {
        return definition.isPrimary();
    }
    
    public Object[] dependencies() {
        return definition.dependencies();
    }
    
    public boolean hasLifecycle() {
        return definition.hasLifecycle();
    }
    
    /**
     * Checks if this bean is PROTOTYPE scope.
     * This is a pre-computed flag to avoid enum comparison in hot paths.
     * 
     * @return true if PROTOTYPE, false otherwise
     */
    public boolean isPrototype() {
        return isPrototype;
    }
    
    /**
     * Checks if this bean has pending warmup compilation.
     * This is a pre-computed flag to avoid Set lookups in hot paths.
     * 
     * @return true if has pending warmup, false otherwise
     */
    public boolean hasPendingWarmup() {
        return hasPendingWarmup;
    }
    
    /**
     * Sets whether this bean has pending warmup compilation.
     * Called when registering a dynamic bean to mark it for lazy compilation.
     * 
     * @param hasPendingWarmup true if the bean has pending warmup, false otherwise
     */
    public void setHasPendingWarmup(boolean hasPendingWarmup) {
        this.hasPendingWarmup = hasPendingWarmup;
    }
    
    /**
     * Returns a sentinel instance representing a not-found bean definition.
     * Used by ClassValue to avoid null semantics which would cause recomputation.
     * 
     * @return the NOT_FOUND sentinel instance
     */
    @SuppressWarnings("unchecked")
    public static <T> ResolvedBeanDefinition<T> notFound() {
        return (ResolvedBeanDefinition<T>) ResolvedBeanDefinitionSentinel.NOT_FOUND_SENTINEL;
    }
    
    /**
     * Checks if this is the NOT_FOUND sentinel instance.
     * 
     * @return true if this is the sentinel, false otherwise
     */
    public boolean isNotFound() {
        return this.definition == null;
    }
}

// Package-private sentinel class to hold the NOT_FOUND instance
final class ResolvedBeanDefinitionSentinel {
    static final ResolvedBeanDefinition<?> NOT_FOUND_SENTINEL = new ResolvedBeanDefinition<>(null);
}
