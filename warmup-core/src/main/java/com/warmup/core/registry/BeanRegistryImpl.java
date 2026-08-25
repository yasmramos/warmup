package com.warmup.core.registry;

import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.scope.Scope;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe implementation of BeanRegistry using lock-free data structures.
 * 
 * Features:
 * - O(1) lookup using ConcurrentHashMap
 * - Separate storage for definitions and instances (memory efficient)
 * - Supports singleton caching and prototype factories
 * - Experimental: indexed access for integer-based fast path (avoids String hashing)
 * - Pre-computed ResolvedBeanDefinition wrappers for single-lookup resolution
 */
public class BeanRegistryImpl implements BeanRegistry {

    // Bean definitions indexed by name
    private final ConcurrentMap<String, BeanDefinition<?>> definitionsByName = new ConcurrentHashMap<>();
    
    // Pre-computed resolved bean definitions indexed by name (for single-lookup resolution)
    private final ConcurrentMap<String, ResolvedBeanDefinition<?>> resolvedByName = new ConcurrentHashMap<>();
    
    // Bean definitions indexed by type for quick type-based lookup
    private final ConcurrentMap<Class<?>, BeanDefinition<?>> definitionsByType = new ConcurrentHashMap<>();
    
    // Cached singleton instances
    private final ConcurrentMap<String, Object> singletonInstances = new ConcurrentHashMap<>();
    
    // Track which singletons have had their init callback applied (to avoid deadlocks)
    private final java.util.Set<String> singletonInitCallbacksApplied = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // Type-to-name mapping for resolving conflicts
    private final ConcurrentMap<Class<?>, String> typeToNameMap = new ConcurrentHashMap<>();
    
    // Experimental: Index-based resolution support
    // Maps bean name to integer index for fast indexed access
    private final ConcurrentMap<String, Integer> nameToIndex = new ConcurrentHashMap<>();
    // Atomic counter for assigning unique indices
    private final AtomicInteger nextIndex = new AtomicInteger(0);
    // Array-backed storage for singleton instances indexed by integer
    // Using Object[] with VarHandle for cheaper reads than AtomicReferenceArray volatile access
    // VarHandle.getOpaque provides acquire semantics for safe publication without full volatile cost
    private volatile Object[] singletonInstancesByIndex = new Object[64];
    // VarHandle for array element access with acquire/release semantics
    private static final VarHandle ARRAY_ELEMENT_HANDLE;
    static {
        try {
            ARRAY_ELEMENT_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize VarHandle", e);
        }
    }

    @Override
    public <T> void register(BeanDefinition<T> definition) {
        String name = definition.name();
        
        // Check for duplicate registration
        if (definitionsByName.containsKey(name)) {
            throw new IllegalStateException("Bean already registered with name: " + name);
        }
        
        // Assign index for indexed resolution
        int index = nextIndex.getAndIncrement();
        nameToIndex.put(name, index);
        
        // Grow the index array if needed
        ensureCapacity(index + 1);
        
        // Pre-resolve dependency indices for fast path resolution
        // This caches the bean index for each String-named dependency
        Object[] dependencies = definition.dependencies();
        int[] depIndices = definition.dependencyIndices();
        for (int i = 0; i < dependencies.length; i++) {
            Object dep = dependencies[i];
            if (dep instanceof String depName) {
                // Try to resolve the index immediately
                Integer depIndex = nameToIndex.get(depName);
                if (depIndex != null) {
                    // Dependency already registered, cache its index
                    depIndices[i] = depIndex;
                } else {
                    // Dependency not yet registered, leave as -1 for lazy resolution
                    // Will be resolved on first creation if still -1
                    depIndices[i] = -1;
                }
            } else {
                // Direct object reference, mark with -2 (no index lookup needed)
                depIndices[i] = -2;
            }
        }
        
        // Register by name (with cached index)
        definitionsByName.put(name, definition);
        
        // Create and cache the ResolvedBeanDefinition with pre-computed index
        ResolvedBeanDefinition<T> resolvedDef = new ResolvedBeanDefinition<>(definition, index);
        resolvedByName.put(name, resolvedDef);
        
        // Register by type (primary beans override non-primary)
        definitionsByType.compute(definition.type(), (type, existing) -> {
            if (existing == null || definition.isPrimary()) {
                typeToNameMap.put(type, name);
                return definition;
            }
            return existing;
        });
    }
    
    /**
     * Ensures the singletonInstancesByIndex array can hold at least minCapacity elements.
     * Thread-safe growth using CAS on the array reference with acquire/release semantics.
     */
    private void ensureCapacity(int minCapacity) {
        while (singletonInstancesByIndex.length < minCapacity) {
            Object[] current = singletonInstancesByIndex;
            int newSize = Math.max(minCapacity, current.length * 2);
            Object[] newArray = new Object[newSize];
            
            // Copy existing elements using volatile read for safety
            for (int i = 0; i < current.length; i++) {
                Object value = ARRAY_ELEMENT_HANDLE.getOpaque(current, i);
                if (value != null) {
                    ARRAY_ELEMENT_HANDLE.setRelease(newArray, i, value);
                }
            }
            
            // CAS to replace the array reference
            synchronized (this) {
                if (singletonInstancesByIndex == current) {
                    singletonInstancesByIndex = newArray;
                    break;
                }
            }
            // Another thread updated it, retry with the new array
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanDefinition<T>> getDefinition(String name) {
        BeanDefinition<?> definition = definitionsByName.get(name);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of((BeanDefinition<T>) definition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> BeanDefinition<T> getDefinitionOrNull(String name) {
        return (BeanDefinition<T>) definitionsByName.get(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanDefinition<T>> getDefinitionByType(Class<T> type) {
        BeanDefinition<?> definition = definitionsByType.get(type);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of((BeanDefinition<T>) definition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> BeanDefinition<T> getDefinitionByTypeOrNull(Class<T> type) {
        return (BeanDefinition<T>) definitionsByType.get(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getInstance(String name, java.util.function.Supplier<T> factory) {
        BeanDefinition<?> definition = definitionsByName.get(name);
        if (definition == null) {
            throw new IllegalStateException("Bean not found: " + name);
        }

        return getInstance((BeanDefinition<T>) definition, factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getInstance(BeanDefinition<T> definition, java.util.function.Supplier<T> factory) {
        String name = definition.name();

        return switch (definition.scope()) {
            case SINGLETON -> {
                // Track if we created a new instance to apply init callback outside the lock
                boolean newInstanceCreated = false;
                T instance = (T) singletonInstances.get(name);
                
                // Fast path: instance already exists
                if (instance != null) {
                    yield instance;
                }
                
                // ComputeIfAbsent ensures thread-safe lazy initialization
                instance = (T) singletonInstances.computeIfAbsent(name, k -> {
                    T newInstance = factory.get();
                    // Also write to indexed array for fast indexed resolution using release semantics
                    Integer idx = nameToIndex.get(name);
                    if (idx != null && idx >= 0 && idx < singletonInstancesByIndex.length) {
                        ARRAY_ELEMENT_HANDLE.setRelease(singletonInstancesByIndex, idx, newInstance);
                    }
                    return newInstance;
                });
                
                // Apply init callback only if this thread created the instance
                // Check if instance was just created by verifying it's the same reference
                // and applying callback exactly once using a separate tracking set
                if (singletonInitCallbacksApplied.add(name)) {
                    applyInitCallback(instance, definition);
                }
                
                yield instance;
            }
            case PROTOTYPE -> {
                // Always create new instance for prototype scope
                T instance = factory.get();
                // Only apply init callback if the bean has lifecycle callbacks defined
                // Avoid the method call and lifecycle check overhead for beans without lifecycle
                if (definition.lifecycle().onInit() != null) {
                    definition.lifecycle().onInit().onInit(instance);
                }
                yield instance;
            }
            case CUSTOM -> {
                // Custom scopes handled by extensions
                T instance = factory.get();
                // Only apply init callback if the bean has lifecycle callbacks defined
                if (definition.lifecycle().onInit() != null) {
                    definition.lifecycle().onInit().onInit(instance);
                }
                yield instance;
            }
        };
    }

    @Override
    public boolean hasInstance(String name) {
        return singletonInstances.containsKey(name);
    }

    @Override
    public boolean remove(String name) {
        BeanDefinition<?> definition = definitionsByName.remove(name);
        if (definition != null) {
            // Apply destroy callback if exists
            if (definition.hasLifecycle() && definition.lifecycle().onDestroy() != null) {
                Object instance = singletonInstances.get(name);
                if (instance != null) {
                    applyDestroyCallback(instance, definition);
                }
            }
            
            // Remove cached instance from both maps
            singletonInstances.remove(name);
            Integer idx = nameToIndex.remove(name);
            if (idx != null && idx >= 0 && idx < singletonInstancesByIndex.length) {
                ARRAY_ELEMENT_HANDLE.setRelease(singletonInstancesByIndex, idx, null);
            }
            // Remove type mapping if this was the primary bean
            if (definition.isPrimary()) {
                typeToNameMap.remove(definition.type());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean evictInstance(String name) {
        Object instance = singletonInstances.remove(name);
        if (instance != null) {
            BeanDefinition<?> definition = definitionsByName.get(name);
            if (definition != null && definition.hasLifecycle() && definition.lifecycle().onDestroy() != null) {
                applyDestroyCallback(instance, definition);
            }
            // Also evict from indexed array
            Integer idx = nameToIndex.get(name);
            if (idx != null && idx >= 0 && idx < singletonInstancesByIndex.length) {
                ARRAY_ELEMENT_HANDLE.setRelease(singletonInstancesByIndex, idx, null);
            }
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        // Apply destroy callbacks before clearing
        definitionsByName.forEach((name, definition) -> {
            if (definition.hasLifecycle() && definition.lifecycle().onDestroy() != null) {
                Object instance = singletonInstances.get(name);
                if (instance != null) {
                    applyDestroyCallback(instance, definition);
                }
            }
        });
        
        singletonInstances.clear();
        // Clear indexed array using release semantics
        for (int i = 0; i < singletonInstancesByIndex.length; i++) {
            ARRAY_ELEMENT_HANDLE.setRelease(singletonInstancesByIndex, i, null);
        }
        definitionsByName.clear();
        resolvedByName.clear();
        definitionsByType.clear();
        typeToNameMap.clear();
        nameToIndex.clear();
        nextIndex.set(0);
    }

    @Override
    public int size() {
        return definitionsByName.size();
    }

    @Override
    public boolean contains(String name) {
        return definitionsByName.containsKey(name);
    }

    @Override
    public Set<String> getAllNames() {
        return definitionsByName.keySet();
    }

    @Override
    public Set<String> getBeanNames() {
        return definitionsByName.keySet();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getIfPresent(String name) {
        return (T) singletonInstances.get(name);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getIfPresent(int index) {
        // Read volatile reference once for consistent view
        Object[] arr = singletonInstancesByIndex;
        if (index < 0 || index >= arr.length) {
            return null;
        }
        // Use getOpaque for cheaper read than volatile get, with acquire semantics for safe publication
        return (T) ARRAY_ELEMENT_HANDLE.getOpaque(arr, index);
    }
    
    @Override
    public int indexOf(String name) {
        Integer index = nameToIndex.get(name);
        return index != null ? index : -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ResolvedBeanDefinition<T> getResolvedOrNull(String name) {
        return (ResolvedBeanDefinition<T>) resolvedByName.get(name);
    }

    /**
     * Applies initialization callback to a bean instance.
     */
    @SuppressWarnings("unchecked")
    private <T> void applyInitCallback(T instance, BeanDefinition<T> definition) {
        if (definition.hasLifecycle() && definition.lifecycle().onInit() != null) {
            definition.lifecycle().onInit().onInit(instance);
        }
    }

    /**
     * Applies destruction callback to a bean instance.
     */
    @SuppressWarnings("unchecked")
    private <T> void applyDestroyCallback(Object instance, BeanDefinition<?> definition) {
        if (definition.hasLifecycle() && definition.lifecycle().onDestroy() != null) {
            ((LifecycleCallbacks<Object>) definition.lifecycle()).onDestroy().onDestroy(instance);
        }
    }
}
