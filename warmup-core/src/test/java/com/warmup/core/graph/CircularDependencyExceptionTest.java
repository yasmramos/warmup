package com.warmup.core.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CircularDependencyException class.
 */
class CircularDependencyExceptionTest {

    @Test
    void testExceptionMessageWithCycle() {
        List<String> cycle = List.of("A", "B", "C", "A");
        CircularDependencyException exception = new CircularDependencyException(cycle);
        
        String message = exception.getMessage();
        assertTrue(message.contains("Circular dependency detected"));
        assertTrue(message.contains("A"));
        assertTrue(message.contains("B"));
        assertTrue(message.contains("C"));
        assertTrue(message.contains(" -> "));
    }

    @Test
    void testExceptionMessageWithSingleElement() {
        List<String> cycle = List.of("X", "X");
        CircularDependencyException exception = new CircularDependencyException(cycle);
        
        String message = exception.getMessage();
        assertTrue(message.contains("Circular dependency detected"));
        assertTrue(message.contains("X"));
    }

    @Test
    void testExceptionIsRuntimeException() {
        List<String> cycle = List.of("A", "B", "A");
        CircularDependencyException exception = new CircularDependencyException(cycle);
        
        assertTrue(exception instanceof RuntimeException);
    }
}
