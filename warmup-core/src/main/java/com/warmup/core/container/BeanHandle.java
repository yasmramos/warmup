package com.warmup.core.container;

import com.warmup.core.registry.ResolvedBeanDefinition;

/**
 * A handle to a resolved bean that captures the resolution result once
 * and allows repeated resolutions without ClassValue lookup overhead.
 * 
 * <p>This class wraps a {@link ResolvedBeanDefinition} obtained during the first
 * resolution, enabling subsequent resolutions to bypass the {@code ClassValue}
 * lookup and directly use the cached index/instance path.</p>
 * 
 * <p>The handle automatically detects invalidation when a bean is reloaded
 * or dynamically re-registered, and will re-resolve if necessary.</p>
 * 
 * @param <T> the bean type
 * @author yasmramos
 */
public final class BeanHandle<T> {
    
    /**
     * The resolved bean definition captured at handle creation time.
     * This may become stale if the bean is reloaded or re-registered.
     */
    private volatile ResolvedBeanDefinition<T> resolvedDef;
    
    /**
     * The container used for resolution.
     */
    private final HybridContainer container;
    
    /**
     * Expected version counter at last successful resolution.
     * Used to detect invalidation due to reload/re-registration.
     */
    private volatile long expectedVersion;
    
    /**
     * Creates a new BeanHandle wrapping the given resolved definition.
     * 
     * @param resolvedDef the resolved bean definition
     * @param container the container for resolution
     */
    BeanHandle(ResolvedBeanDefinition<T> resolvedDef, HybridContainer container) {
        this.resolvedDef = resolvedDef;
        this.container = container;
        this.expectedVersion = container.getResolutionVersion();
    }
    
    /**
     * Returns the bean instance, reusing the cached resolution.
     * 
     * <p>If the cached {@link ResolvedBeanDefinition} has been invalidated
     * (e.g., due to reload or dynamic re-registration), this method will
     * detect it and re-resolve the bean transparently.</p>
     * 
     * @return the bean instance
     * @throws IllegalStateException if the bean cannot be resolved
     */
    public T get() {
        // Check if the resolution version has changed (indicates reload/re-registration)
        long currentVersion = container.getResolutionVersion();
        if (currentVersion != expectedVersion || resolvedDef == null) {
            // Re-resolve the bean by type to get fresh definition
            @SuppressWarnings("unchecked")
            ResolvedBeanDefinition<T> freshDef = (ResolvedBeanDefinition<T>) 
                container.resolveByTypeInternal(resolvedDef.getDefinition().type());
            
            if (freshDef.isNotFound()) {
                throw new IllegalStateException(
                    "Bean not found for type: " + resolvedDef.getDefinition().type().getName());
            }
            
            this.resolvedDef = freshDef;
            this.expectedVersion = currentVersion;
        }
        
        // Use the cached resolved definition for fast resolution
        return container.resolveInternal(resolvedDef);
    }
    
    /**
     * Returns true if this handle is still valid (bean not reloaded/re-registered).
     * 
     * @return true if valid, false if the handle needs re-resolution
     */
    public boolean isValid() {
        return container.getResolutionVersion() == expectedVersion && resolvedDef != null;
    }
    
    /**
     * Invalidates this handle, forcing re-resolution on next {@link #get()} call.
     */
    public void invalidate() {
        this.expectedVersion = -1;
    }
    
    /**
     * Returns the bean type this handle resolves.
     * 
     * @return the bean class
     */
    public Class<T> getType() {
        return resolvedDef.getDefinition().type();
    }
    
    /**
     * Returns the bean name this handle resolves.
     * 
     * @return the bean name
     */
    public String getName() {
        return resolvedDef.name();
    }
}
