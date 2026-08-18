package com.warmup.core.graph;

import java.util.List;

/**
 * Exception thrown when a circular dependency is detected in the bean graph.
 */
public class CircularDependencyException extends RuntimeException {
    
    /**
     * Creates a new circular dependency exception with the cycle path.
     * 
     * @param cycle the list of bean names forming the cycle
     */
    public CircularDependencyException(List<String> cycle) {
        super("Circular dependency detected: " + String.join(" -> ", cycle));
    }
}
