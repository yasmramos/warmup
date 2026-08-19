package com.warmup.core.jit;

import java.util.function.BiConsumer;

/**
 * Service Provider Interface (SPI) for batch registration of compile-time factories.
 * 
 * <p>Implementations of this interface are discovered via {@code ServiceLoader} at container
 * startup. Each implementation registers all factories from a single module in one call,
 * minimizing the ServiceLoader overhead to one entry per module instead of one per bean.</p>
 * 
 * <p>The discovery cost is paid only once during container initialization, never on the
 * bean resolution path.</p>
 * 
 * @see java.util.ServiceLoader
 */
@FunctionalInterface
public interface FactoryRegistrar {
    
    /**
     * Registers all compile-time factories from this module.
     * 
     * @param sink a consumer that accepts bean name and factory pairs for registration
     */
    void registerAll(BiConsumer<String, CompiledFactory<?>> sink);
}
