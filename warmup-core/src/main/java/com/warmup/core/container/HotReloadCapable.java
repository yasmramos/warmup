package com.warmup.core.container;

/**
 * Capability interface for containers that support hot-reload of bean factories.
 * 
 * <p>Hot-reload allows recompiling and replacing a bean's factory at runtime, which is useful
 * for development-time iteration or dynamic behavior changes.</p>
 * 
 * <p><strong>Important limitation:</strong> Hot-reload guarantees that NEW resolutions will use
 * the reloaded factory, but existing references to previously resolved instances are NOT replaced.
 * If a bean instance has already been injected into other beans or held by application code,
 * those references will continue to point to the old instance. To fully replace live instances,
 * a proxy-based indirection layer would be required, which this container does not provide.</p>
 * 
 * <p>The reload process performs the following steps:</p>
 * <ol>
 *   <li>Evicts the cached singleton instance and applies destroy callbacks</li>
 *   <li>Invalidates factory caches ({@code compileTimeFactories} and {@code jitFactoryCache})</li>
 *   <li>Unloads the previous ASM-generated factory class and its ClassLoader</li>
 *   <li>Triggers background recompilation of the factory</li>
 * </ol>
 * 
 * @apiNote This is an experimental feature. The hot-reload mechanism uses generation-based
 * class naming to avoid {@link LinkageError}, but users must ensure that no long-lived
 * references to old instances prevent garbage collection of the previous ClassLoader.
 * 
 * @see HybridContainer#reload(String)
 */
public interface HotReloadCapable {

    /**
     * Hot-reloads a bean by invalidating all caches and recompiling its factory.
     * 
     * <p>This method performs the following steps atomically with respect to new resolutions:</p>
     * <ol>
     *   <li>Evicts the cached singleton instance and applies destroy callbacks</li>
     *   <li>Removes entries from factory caches</li>
     *   <li>Unloads the previous ASM factory to free ClassLoader and metaspace</li>
     *   <li>Triggers background recompilation</li>
     * </ol>
     * 
     * <p><strong>Note:</strong> Hot-reload guarantees that NEW resolutions will use the reloaded
     * factory, but existing references to previously resolved instances are NOT replaced.
     * If you need to replace live instances, you must manage that indirection yourself.</p>
     * 
     * @param name the bean name to reload
     * @return true if the bean existed and was reloaded, false if the bean was not found
     * @param <T> the bean type (inferred from the bean definition)
     * 
     * @apiNote Existing instances injected into other beans or held by application code
     * will NOT be automatically replaced. Only future calls to {@code resolve()} will
     * return instances created by the new factory.
     */
    <T> boolean reload(String name);
}
