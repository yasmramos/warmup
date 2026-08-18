package com.warmup.core.scope;

/**
 * Defines the lifecycle scope of a bean.
 */
public enum Scope {
    /**
     * Single instance per container. Cached after first creation.
     */
    SINGLETON,
    
    /**
     * New instance on every resolve() call.
     */
    PROTOTYPE,
    
    /**
     * Custom scope managed by external strategy.
     */
    CUSTOM
}
