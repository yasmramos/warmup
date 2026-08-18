package com.warmup.core;

/**
 * Bean scope definitions for dependency injection lifecycle management.
 */
public enum BeanScope {
    /**
     * Singleton: One shared instance per container
     */
    SINGLETON,
    
    /**
     * Prototype: New instance on every resolution
     */
    PROTOTYPE,
    
    /**
     * Custom scope (e.g., request, session, thread-local)
     */
    CUSTOM
}
