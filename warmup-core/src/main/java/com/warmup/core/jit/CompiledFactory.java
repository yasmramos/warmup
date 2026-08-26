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
    
    /**
     * Wires this factory with its dependency factories.
     * Called by the container after all factories are registered.
     * Default implementation does nothing (for factories without dependencies or legacy support).
     * 
     * @param dependencyFactories array of compiled factories for dependencies
     */
    default void wire(CompiledFactory<?>[] dependencyFactories) {
        // Default: no wiring support (legacy or no dependencies)
    }
    
    /**
     * Creates a bean instance using wired dependency factories.
     * This method avoids Object[] allocation by calling dependency factories directly.
     * Default implementation delegates to create() for backward compatibility.
     * Factories with wiring support should override this method.
     * 
     * @return a new bean instance
     */
    default T get() {
        // Default: delegate to create() with no dependencies
        // Factories with dependencies should override this method
        return create();
    }
}
