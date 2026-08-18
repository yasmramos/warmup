package com.warmup.core.jit;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * JIT compiler interface for dynamic bytecode generation.
 * 
 * Implementations generate CompiledFactory bytecode at runtime using ASM.
 * Supports class unloading to prevent metaspace leaks.
 */
public interface JITCompiler {

    /**
     * Compiles a factory for the given bean type.
     * 
     * @param <T> the bean type
     * @param beanClass the class to create a factory for
     * @param dependencyClasses array of dependency classes (in injection order)
     * @return compiled factory instance
     * @throws CompilationException if bytecode generation fails
     */
    <T> CompiledFactory<T> compile(Class<T> beanClass, Class<?>... dependencyClasses)
        throws CompilationException;

    /**
     * Asynchronously compiles a factory in the background.
     * Used for warmup scenarios to minimize first-call latency.
     * 
     * @param <T> the bean type
     * @param beanClass the class to create a factory for
     * @param dependencyClasses array of dependency classes
     * @return CompletableFuture that completes with the compiled factory
     */
    <T> CompletableFuture<CompiledFactory<T>> compileAsync(
        Class<T> beanClass, 
        Class<?>... dependencyClasses
    );

    /**
     * Checks if a compiled factory exists for the given class.
     * 
     * @param beanClass the bean class
     * @return true if a compiled factory is cached
     */
    boolean hasCompiledFactory(Class<?> beanClass);

    /**
     * Gets a cached compiled factory if available.
     * 
     * @param <T> the bean type
     * @param beanClass the bean class
     * @return Optional containing the compiled factory
     */
    <T> Optional<CompiledFactory<T>> getCachedFactory(Class<T> beanClass);

    /**
     * Unloads a compiled factory and its associated ClassLoader.
     * Used to prevent metaspace leaks when beans are removed.
     * 
     * @param beanClass the bean class
     * @return true if the factory was unloaded
     */
    boolean unloadFactory(Class<?> beanClass);

    /**
     * Returns compilation statistics for diagnostics.
     */
    CompilationStats getStats();

    /**
     * Clears all cached factories and unloads all generated classes.
     * Should be called during container shutdown or test cleanup.
     */
    void clear();
}
