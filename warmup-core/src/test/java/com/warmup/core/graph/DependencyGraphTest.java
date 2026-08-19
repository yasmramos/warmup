package com.warmup.core.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DependencyGraph class.
 */
class DependencyGraphTest {

    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        graph = new DependencyGraph();
    }

    @Test
    void testRegisterBeanWithNoDependencies() {
        graph.registerBean("bean1");
        
        assertTrue(graph.contains("bean1"));
        assertEquals(1, graph.size());
        assertTrue(graph.getDependencies("bean1").isEmpty());
        assertTrue(graph.getDependents("bean1").isEmpty());
    }

    @Test
    void testRegisterBeanWithDependencies() {
        graph.registerBean("service", "repository");
        graph.registerBean("repository");
        graph.registerBean("controller", "service");
        
        assertEquals(3, graph.size());
        
        Set<String> serviceDeps = graph.getDependencies("service");
        assertEquals(1, serviceDeps.size());
        assertTrue(serviceDeps.contains("repository"));
        
        Set<String> repositoryDependents = graph.getDependents("repository");
        assertEquals(1, repositoryDependents.size());
        assertTrue(repositoryDependents.contains("service"));
    }

    @Test
    void testRegisterBeanCreatesCycle() {
        // Create a simple chain: c -> b -> a
        // First register all beans without dependencies
        graph.registerBean("a");
        graph.registerBean("b");
        graph.registerBean("c");
        
        // Now add dependencies that create a cycle
        // b depends on a: b -> a
        graph.registerBean("b", "a");
        // c depends on b: c -> b -> a
        graph.registerBean("c", "b");
        
        // Now try to make a depend on c, which would create: a -> c -> b -> a
        assertThrows(CircularDependencyException.class, () -> {
            graph.registerBean("a", "c");
        });
    }

    @Test
    void testCircularDependencyExceptionMessage() {
        // Create a simple chain
        graph.registerBean("x");
        graph.registerBean("y");
        graph.registerBean("z");
        
        // y depends on x
        graph.registerBean("y", "x");
        // z depends on y
        graph.registerBean("z", "y");
        
        // Try to make x depend on z, creating cycle: x -> z -> y -> x
        CircularDependencyException exception = assertThrows(
            CircularDependencyException.class, 
            () -> graph.registerBean("x", "z")
        );
        
        String message = exception.getMessage();
        assertTrue(message.contains("Circular dependency detected"));
        assertTrue(message.contains("x"));
        assertTrue(message.contains("z"));
    }

    @Test
    void testGetResolutionOrder() {
        graph.registerBean("repository");
        graph.registerBean("service", "repository");
        graph.registerBean("controller", "service");
        
        List<String> order = graph.getResolutionOrder();
        
        assertEquals(3, order.size());
        // repository should come before service, service before controller
        assertTrue(order.indexOf("repository") < order.indexOf("service"));
        assertTrue(order.indexOf("service") < order.indexOf("controller"));
    }

    @Test
    void testGetResolutionOrderWithMultipleDependencies() {
        graph.registerBean("a");
        graph.registerBean("b");
        graph.registerBean("c", "a", "b");
        
        List<String> order = graph.getResolutionOrder();
        
        assertEquals(3, order.size());
        assertTrue(order.indexOf("a") < order.indexOf("c"));
        assertTrue(order.indexOf("b") < order.indexOf("c"));
    }

    @Test
    void testGetDependencies() {
        graph.registerBean("bean1");
        graph.registerBean("bean2");
        graph.registerBean("bean3", "bean1", "bean2");
        
        Set<String> deps = graph.getDependencies("bean3");
        assertEquals(2, deps.size());
        assertTrue(deps.contains("bean1"));
        assertTrue(deps.contains("bean2"));
    }

    @Test
    void testGetDependents() {
        graph.registerBean("base");
        graph.registerBean("derived1", "base");
        graph.registerBean("derived2", "base");
        
        Set<String> dependents = graph.getDependents("base");
        assertEquals(2, dependents.size());
        assertTrue(dependents.contains("derived1"));
        assertTrue(dependents.contains("derived2"));
    }

    @Test
    void testRemoveBean() {
        graph.registerBean("a");
        graph.registerBean("b", "a");
        
        graph.removeBean("a");
        
        assertFalse(graph.contains("a"));
        assertEquals(1, graph.size());
        
        // b should still exist but without its dependency on a
        Set<String> deps = graph.getDependencies("b");
        assertTrue(deps.isEmpty());
    }

    @Test
    void testClear() {
        graph.registerBean("a");
        graph.registerBean("b", "a");
        graph.registerBean("c", "b");
        
        graph.clear();
        
        assertEquals(0, graph.size());
        assertFalse(graph.contains("a"));
        assertFalse(graph.contains("b"));
        assertFalse(graph.contains("c"));
    }

    @Test
    void testSize() {
        assertEquals(0, graph.size());
        
        graph.registerBean("a");
        assertEquals(1, graph.size());
        
        graph.registerBean("b");
        assertEquals(2, graph.size());
    }

    @Test
    void testContains() {
        assertFalse(graph.contains("nonexistent"));
        
        graph.registerBean("existing");
        assertTrue(graph.contains("existing"));
    }

    @Test
    void testGetDependenciesForNonExistentBean() {
        Set<String> deps = graph.getDependencies("nonexistent");
        assertTrue(deps.isEmpty());
    }

    @Test
    void testGetDependentsForNonExistentBean() {
        Set<String> dependents = graph.getDependents("nonexistent");
        assertTrue(dependents.isEmpty());
    }
}
