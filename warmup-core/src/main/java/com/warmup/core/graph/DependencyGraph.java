package com.warmup.core.graph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency graph manager with topological sorting and cycle detection.
 * 
 * Features:
 * - Kahn's algorithm for topological sort (O(V + E))
 * - DFS-based cycle detection with path reconstruction
 * - Thread-safe registration using ConcurrentHashMap
 * 
 * Trade-offs:
 * - Uses adjacency list representation for memory efficiency with sparse graphs
 * - Cycle detection runs at registration time to fail fast
 */
public class DependencyGraph {

    // Adjacency list: bean -> beans that depend on it
    private final Map<String, Set<String>> adjacencyList = new ConcurrentHashMap<>();
    
    // Reverse adjacency: bean -> beans it depends on
    private final Map<String, Set<String>> reverseAdjacency = new ConcurrentHashMap<>();
    
    // All registered nodes
    private final Set<String> nodes = ConcurrentHashMap.newKeySet();

    /**
     * Registers a bean and its dependencies in the graph.
     * Performs cycle detection before adding edges.
     * 
     * @param beanName the name of the bean being registered
     * @param dependencies names of beans this bean depends on
     * @throws CircularDependencyException if adding these dependencies creates a cycle
     */
    public void registerBean(String beanName, String... dependencies) {
        nodes.add(beanName);
        
        // Initialize adjacency lists if not present
        adjacencyList.computeIfAbsent(beanName, k -> ConcurrentHashMap.newKeySet());
        reverseAdjacency.computeIfAbsent(beanName, k -> ConcurrentHashMap.newKeySet());
        
        for (String dep : dependencies) {
            nodes.add(dep);
            
            // Check for cycle before adding edge
            if (wouldCreateCycle(beanName, dep)) {
                List<String> cycle = findCycle(beanName, dep);
                throw new CircularDependencyException(cycle);
            }
            
            // Add edge: dep -> beanName (beanName depends on dep)
            adjacencyList.computeIfAbsent(dep, k -> ConcurrentHashMap.newKeySet()).add(beanName);
            reverseAdjacency.get(beanName).add(dep);
        }
    }

    /**
     * Returns beans in topologically sorted order (dependencies before dependents).
     * Uses Kahn's algorithm for O(V + E) complexity.
     * 
     * @return list of bean names in resolution order
     */
    public List<String> getResolutionOrder() {
        // Calculate in-degrees
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : nodes) {
            inDegree.put(node, reverseAdjacency.getOrDefault(node, Set.of()).size());
        }
        
        // Start with nodes that have no dependencies
        Queue<String> queue = new LinkedList<>();
        for (String node : nodes) {
            if (inDegree.get(node) == 0) {
                queue.offer(node);
            }
        }
        
        List<String> result = new ArrayList<>(nodes.size());
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            
            // Reduce in-degree for dependent nodes
            for (String dependent : adjacencyList.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                
                if (newDegree == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        return result;
    }

    /**
     * Gets all dependencies for a bean (direct only).
     * 
     * @param beanName the bean name
     * @return set of dependency names
     */
    public Set<String> getDependencies(String beanName) {
        return Collections.unmodifiableSet(
            reverseAdjacency.getOrDefault(beanName, Set.of())
        );
    }

    /**
     * Gets all beans that depend on the given bean (direct dependents).
     * 
     * @param beanName the bean name
     * @return set of dependent bean names
     */
    public Set<String> getDependents(String beanName) {
        return Collections.unmodifiableSet(
            adjacencyList.getOrDefault(beanName, Set.of())
        );
    }

    /**
     * Removes a bean from the graph.
     * 
     * @param beanName the bean to remove
     */
    public void removeBean(String beanName) {
        nodes.remove(beanName);
        
        // Remove from adjacency lists
        Set<String> dependents = adjacencyList.remove(beanName);
        Set<String> dependencies = reverseAdjacency.remove(beanName);
        
        // Remove edges from other nodes
        if (dependents != null) {
            for (String dependent : dependents) {
                Set<String> deps = reverseAdjacency.get(dependent);
                if (deps != null) {
                    deps.remove(beanName);
                }
            }
        }
        
        if (dependencies != null) {
            for (String dep : dependencies) {
                Set<String> adj = adjacencyList.get(dep);
                if (adj != null) {
                    adj.remove(beanName);
                }
            }
        }
    }

    /**
     * Clears the entire graph.
     */
    public void clear() {
        nodes.clear();
        adjacencyList.clear();
        reverseAdjacency.clear();
    }

    /**
     * Checks if adding an edge would create a cycle.
     * When registering "beanName depends on dep", a cycle exists if
     * dep can already reach beanName by following existing dependencies.
     * Uses DFS on reverseAdjacency (bean -> its dependencies).
     */
    private boolean wouldCreateCycle(String beanName, String dep) {
        // Check if beanName is reachable from dep by following dependencies
        // This means: does dep (or anything dep depends on) eventually depend on beanName?
        return canReachViaDependencies(dep, beanName, new HashSet<>());
    }

    /**
     * DFS to check if destination can be reached from current by following dependencies.
     */
    private boolean canReachViaDependencies(String current, String destination, Set<String> visited) {
        if (current.equals(destination)) {
            return true;
        }
        
        if (!visited.add(current)) {
            return false; // Already visited
        }
        
        Set<String> dependencies = reverseAdjacency.getOrDefault(current, Set.of());
        for (String dependency : dependencies) {
            if (canReachViaDependencies(dependency, destination, visited)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Finds and returns the cycle path when a cycle is detected.
     * Returns a list representing the cycle: source -> target -> ... -> source
     */
    private List<String> findCycle(String source, String target) {
        List<String> path = new ArrayList<>();
        path.add(source);
        
        // Find path from source to target and back to source
        // Start by adding target to the path
        path.add(target);
        
        // Find path from target back to source using reverse edges (dependencies)
        findPath(target, source, new HashSet<>(), path);
        
        return path;
    }

    private boolean findPath(String current, String destination, Set<String> visited, List<String> path) {
        if (current.equals(destination)) {
            return true;
        }
        
        if (!visited.add(current)) {
            return false;
        }
        
        // Follow reverse edges (dependencies)
        Set<String> dependencies = reverseAdjacency.getOrDefault(current, Set.of());
        for (String dep : dependencies) {
            if (dep.equals(destination)) {
                path.add(dep);
                return true;
            }
            path.add(dep);
            if (findPath(dep, destination, visited, path)) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        
        return false;
    }

    /**
     * Returns the number of registered beans.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Checks if a bean exists in the graph.
     */
    public boolean contains(String beanName) {
        return nodes.contains(beanName);
    }
}
