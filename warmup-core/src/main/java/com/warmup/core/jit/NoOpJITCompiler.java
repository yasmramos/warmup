package com.warmup.core.jit;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * No-op JIT compiler implementation used as fallback when no JIT provider is available.
 * 
 * This implementation throws CompilationException on compile() calls, causing
 * HybridContainer to fall back to reflection-based bean creation (Path A).
 * 
 * Use cases:
 * - GraalVM native image where dynamic bytecode generation is not supported
 * - Classpath without warmup-asm module
 * - Testing scenarios where JIT compilation should be disabled
 */
public class NoOpJITCompiler implements JITCompiler {

    private static final CompilationException NOT_SUPPORTED = 
        new CompilationException("JIT compilation not available - no provider found");

    @Override
    public <T> CompiledFactory<T> compile(Class<T> beanClass, Class<?>... dependencyClasses) 
            throws CompilationException {
        throw NOT_SUPPORTED;
    }

    @Override
    public <T> CompletableFuture<CompiledFactory<T>> compileAsync(
            Class<T> beanClass, Class<?>... dependencyClasses) {
        return compileAsync(beanClass, null, dependencyClasses);
    }

    @Override
    public <T> CompletableFuture<CompiledFactory<T>> compileAsync(
            Class<T> beanClass, java.util.concurrent.ExecutorService executor, Class<?>... dependencyClasses) {
        CompletableFuture<CompiledFactory<T>> future = new CompletableFuture<>();
        future.completeExceptionally(NOT_SUPPORTED);
        return future;
    }

    @Override
    public boolean hasCompiledFactory(Class<?> beanClass) {
        return false;
    }

    @Override
    public <T> Optional<CompiledFactory<T>> getCachedFactory(Class<T> beanClass) {
        return Optional.empty();
    }

    @Override
    public boolean unloadFactory(Class<?> beanClass) {
        return false;
    }

    @Override
    public CompilationStats getStats() {
        return new CompilationStats(0, 0, 0, 0, 0);
    }

    @Override
    public void clear() {
        // No-op - nothing to clear
    }
}
