package com.warmup.core.jit;

/**
 * Compiled factory interface for zero-overhead bean instantiation.
 * 
 * This interface is implemented by both:
 * 1. Compile-time generated factories (annotation processor)
 * 2. JIT-compiled factories (ASM bytecode generation)
 * 
 * @param <T> the bean type
 */
@FunctionalInterface
public interface CompiledFactory<T> {
    /**
     * Creates a new instance of the bean.
     * For singleton beans, the container caches the result.
     * For prototype beans, this is called on every resolution.
     * 
     * @param dependencies array of resolved dependencies (order matches registration)
     * @return a new bean instance
     */
    T create(Object... dependencies);

    /**
     * Returns the bean class this factory creates.
     * Used for validation and diagnostics.
     */
    default Class<T> getBeanType() {
        return null; // Optional implementation
    }

    /**
     * Returns the number of dependencies this factory expects.
     * Used for validation before invocation.
     */
    default int getDependencyCount() {
        return 0; // Optional implementation
    }
}
