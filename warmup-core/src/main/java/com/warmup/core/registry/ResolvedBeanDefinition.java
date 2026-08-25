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
    
    public ResolvedBeanDefinition(BeanDefinition<T> definition) {
        this.definition = definition;
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
}
