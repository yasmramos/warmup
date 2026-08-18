package com.warmup.core.registry;

import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.scope.Scope;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 */
public class BeanRegistryImpl implements BeanRegistry {

    // Bean definitions indexed by name
    private final ConcurrentMap<String, BeanDefinition<?>> definitionsByName = new ConcurrentHashMap<>();
    
    // Bean definitions indexed by type for quick type-based lookup
    private final ConcurrentMap<Class<?>, BeanDefinition<?>> definitionsByType = new ConcurrentHashMap<>();
    
    // Cached singleton instances
    private final ConcurrentMap<String, Object> singletonInstances = new ConcurrentHashMap<>();
    
    // Type-to-name mapping for resolving conflicts
    private final ConcurrentMap<Class<?>, String> typeToNameMap = new ConcurrentHashMap<>();

    @Override
    public <T> void register(BeanDefinition<T> definition) {
        String name = definition.name();
        
        // Check for duplicate registration
        if (definitionsByName.containsKey(name)) {
            throw new IllegalStateException("Bean already registered with name: " + name);
        }
        
        // Register by name
        definitionsByName.put(name, definition);
        
        // Register by type (primary beans override non-primary)
        definitionsByType.compute(definition.type(), (type, existing) -> {
            if (existing == null || definition.isPrimary()) {
                typeToNameMap.put(type, name);
                return definition;
            }
            return existing;
        });
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
    public <T> Optional<BeanDefinition<T>> getDefinitionByType(Class<T> type) {
        BeanDefinition<?> definition = definitionsByType.get(type);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of((BeanDefinition<T>) definition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getInstance(String name, java.util.function.Supplier<T> factory) {
        BeanDefinition<?> definition = definitionsByName.get(name);
        if (definition == null) {
            throw new IllegalStateException("Bean not found: " + name);
        }

        return switch (definition.scope()) {
            case SINGLETON -> {
                // ComputeIfAbsent ensures thread-safe lazy initialization
                T instance = (T) singletonInstances.computeIfAbsent(name, k -> factory.get());
                
                // Apply init callback on first creation
                if (singletonInstances.size() == 1 || !hasInstance(name)) {
                    applyInitCallback((T) instance, (BeanDefinition<T>) definition);
                }
                
                yield instance;
            }
            case PROTOTYPE -> {
                // Always create new instance for prototype scope
                T instance = factory.get();
                applyInitCallback(instance, (BeanDefinition<T>) definition);
                yield instance;
            }
            case CUSTOM -> {
                // Custom scopes handled by extensions
                T instance = factory.get();
                applyInitCallback(instance, (BeanDefinition<T>) definition);
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
            
            // Remove cached instance
            singletonInstances.remove(name);
            // Remove type mapping if this was the primary bean
            if (definition.isPrimary()) {
                typeToNameMap.remove(definition.type());
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
        definitionsByName.clear();
        definitionsByType.clear();
        typeToNameMap.clear();
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
