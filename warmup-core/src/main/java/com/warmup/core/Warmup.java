package com.warmup.core;

import com.warmup.core.annotation.InternalApi;
import com.warmup.core.container.HotReloadCapable;
import com.warmup.core.container.HybridContainer;
import com.warmup.core.container.HybridContainerConfig;
import com.warmup.core.jit.JITCompiler;
import com.warmup.core.jit.NoOpJITCompiler;
import com.warmup.core.registry.BeanDefinition;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

/**
 * Main entry point for the Warmup dependency injection container.
 * 
 * This class provides an ergonomic API for creating and using the DI container,
 * wrapping HybridContainer with a simpler interface. JIT compiler discovery
 * is done via ServiceLoader at runtime to maintain proper module dependencies
 * (warmup-core does not depend on warmup-asm).
 * 
 * Usage:
 * <pre>{@code
 * // Simple usage with defaults
 * Warmup warmup = Warmup.create();
 * UserService service = warmup.get(UserService.class);
 * 
 * // Alternative using resolve (same behavior)
 * UserService service2 = warmup.resolve(UserService.class);
 * 
 * // Advanced usage with builder
 * Warmup warmup = Warmup.builder()
 *     .diagnostic(true)
 *     .maxPendingCompilations(20)
 *     .build();
 * }</pre>
 * 
 * Architecture note: The JITCompiler implementation is discovered via
 * {@link ServiceLoader}. If no provider is found (e.g., in GraalVM native
 * images or when warmup-asm is not on classpath), a NoOp fallback is used
 * that delegates to reflection-based bean creation.
 * 
 * @see HybridContainer
 * @see JITCompiler
 */
public class Warmup implements AutoCloseable {

    private final HybridContainer container;

    /**
     * Creates a new Warmup instance with default settings.
     * 
     * Uses ServiceLoader to discover JITCompiler implementation.
     * Falls back to NoOpJITCompiler if no provider is found.
     * 
     * @return new Warmup instance
     */
    public static Warmup create() {
        return builder().build();
    }

    /**
     * Returns a builder for advanced configuration.
     * 
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a Warmup instance with explicit JITCompiler.
     * Useful for testing or custom configurations.
     * 
     * @param jitCompiler the JIT compiler to use
     * @return new Warmup instance
     */
    public static Warmup create(JITCompiler jitCompiler) {
        return new Warmup(new HybridContainer(jitCompiler, false));
    }

    /**
     * Creates a Warmup instance with custom settings.
     * 
     * @param jitCompiler the JIT compiler to use
     * @param diagnostic enable diagnostic mode
     * @param maxPendingCompilations maximum concurrent background compilations
     * @return new Warmup instance
     */
    public static Warmup create(JITCompiler jitCompiler, boolean diagnostic, int maxPendingCompilations) {
        return new Warmup(new HybridContainer(jitCompiler, diagnostic, maxPendingCompilations));
    }

    /**
     * Internal constructor wrapping a HybridContainer.
     * 
     * @param container the underlying container
     */
    private Warmup(HybridContainer container) {
        this.container = container;
    }

    /**
     * Returns the underlying HybridContainer for advanced operations.
     * 
     * <p><strong>This method is deprecated.</strong> The {@code HybridContainer} exposes internal
     * and potentially dangerous operations that should not be part of the stable public API.
     * For hot-reload capabilities, use {@link #hotReload()} instead. For other advanced needs,
     * consider that this escape hatch may change or be removed in future versions.</p>
     * 
     * @return the wrapped container
     * @deprecated Use specific capability methods like {@link #hotReload()} instead.
     *             This method will be removed in a future version.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public HybridContainer container() {
        return container;
    }

    /**
     * Returns the hot-reload capability if available.
     * 
     * <p>This provides explicit opt-in access to hot-reload functionality without exposing
     * the full internal container API. The returned capability allows reloading bean factories
     * at runtime.</p>
     * 
     * <p><strong>Important:</strong> Hot-reload only affects NEW resolutions. Existing references
     * to previously resolved instances are NOT replaced automatically.</p>
     * 
     * @return Optional containing the hot-reload capability (always present for standard Warmup instances)
     * @see HotReloadCapable#reload(String)
     */
    public HotReloadCapable hotReload() {
        return container;
    }

    /**
     * Returns the underlying HybridContainer for internal/advanced operations.
     * 
     * <p><strong>Warning:</strong> This method exposes internal API that may change without notice.
     * Use only for testing or advanced integration scenarios where no other option exists.</p>
     * 
     * @return the wrapped container
     * @apiNote This is an internal escape hatch. Prefer using the stable API methods on {@code Warmup}
     *          or specific capabilities like {@link #hotReload()} whenever possible.
     */
    @InternalApi
    public HybridContainer unsafeContainer() {
        return container;
    }

    /**
     * Registers a bean definition with a pre-compiled factory.
     * 
     * @param definition the bean definition
     * @param factory the compiled factory
     * @param <T> the bean type
     */
    public <T> void register(BeanDefinition definition, com.warmup.core.jit.CompiledFactory<T> factory) {
        container.register(definition, factory);
    }

    /**
     * Registers a bean definition for dynamic resolution.
     * 
     * @param definition the bean definition
     */
    public void registerDynamic(BeanDefinition definition) {
        container.registerDynamic(definition);
    }

    /**
     * Resolves a bean by type.
     * This is the primary resolution method in the public API.
     * 
     * @param clazz the bean class
     * @return the bean instance
     * @throws IllegalStateException if bean not found
     */
    public <T> T resolve(Class<T> clazz) {
        return container.resolve(clazz);
    }

    /**
     * Resolves a bean by type. Alias for {@link #resolve(Class)} with Avaje-style nomenclature.
     * 
     * @param clazz the bean class
     * @return the bean instance
     * @throws IllegalStateException if bean not found
     * @see #resolve(Class)
     */
    public <T> T get(Class<T> clazz) {
        return container.resolve(clazz);
    }

    /**
     * Resolves all beans of the given type as a List.
     * This is used for collection injection (List<T>, Set<T>).
     * 
     * @param <T> the bean type
     * @param clazz the bean class
     * @return list of all bean instances of this type (may be empty)
     */
    public <T> java.util.List<T> resolveAll(Class<T> clazz) {
        return container.resolveAll(clazz);
    }

    /**
     * Resolves all beans of the given type as a Map with bean names as keys.
     * This is used for Map<String, T> injection.
     * 
     * @param <T> the bean type
     * @param clazz the bean class
     * @return map of bean name to instance (may be empty)
     */
    public <T> java.util.Map<String, T> resolveAllAsMap(Class<T> clazz) {
        return container.resolveAllAsMap(clazz);
    }

    /**
     * Checks if a bean exists by name.
     * 
     * @param name the bean name
     * @return true if bean exists
     */
    public boolean contains(String name) {
        return container.contains(name);
    }

    /**
     * Checks if a bean exists by type.
     * 
     * @param clazz the bean class
     * @return true if bean exists
     */
    public boolean contains(Class<?> clazz) {
        return container.contains(clazz);
    }

    /**
     * Gets all registered bean names.
     * 
     * @return set of bean names
     */
    public Set<String> getBeanNames() {
        return container.getBeanNames();
    }

    /**
     * Gets container metrics.
     * 
     * @return container metrics
     */
    public com.warmup.core.container.ContainerMetrics getMetrics() {
        return container.getMetrics();
    }

    /**
     * Gets compilation statistics from the JIT compiler.
     * 
     * @return compilation stats
     */
    public com.warmup.core.jit.CompilationStats getCompilationStats() {
        return container.getCompilationStats();
    }

    /**
     * Gets resolution diagnostics if diagnostic mode is enabled.
     * 
     * @return list of diagnostics
     */
    public java.util.List<com.warmup.core.container.ResolutionDiagnostic> getDiagnostics() {
        return container.getDiagnostics();
    }

    /**
     * Registers a compile-time factory for a bean with type-safe validation.
     * <p>
     * This method validates at registration time that the factory's type is compatible
     * with the bean definition's type, failing fast instead of deferring the error
     * to the first {@code resolve()} call.
     * </p>
     * 
     * @param <T> the bean type
     * @param beanName the bean name
     * @param type the expected bean type (used for validation)
     * @param factory the compiled factory
     * @throws IllegalStateException if no bean definition exists for this name,
     *         or if the factory's type is not assignable to the bean's declared type
     */
    public <T> void registerFactory(String beanName, Class<T> type, com.warmup.core.jit.CompiledFactory<T> factory) {
        container.registerFactory(beanName, type, factory);
    }

    /**
     * Registers a compile-time factory for a bean by name.
     * 
     * @param beanName the bean name
     * @param factory the compiled factory
     * @deprecated Use {@link #registerFactory(String, Class, CompiledFactory)} for type-safe registration.
     * This wildcard version does not validate types and may cause {@code ClassCastException}
     * at resolution time if the factory type doesn't match the bean definition.
     * Kept for backward compatibility with generated code.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public void registerFactory(String beanName, com.warmup.core.jit.CompiledFactory<?> factory) {
        container.registerFactory(beanName, factory);
    }

    /**
     * Shuts down the container, releasing resources.
     */
    public void shutdown() {
        container.shutdown();
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Builder for advanced Warmup configuration.
     */
    public static class Builder {
        private JITCompiler jitCompiler;
        private boolean diagnostic = false;
        private int maxPendingCompilations = 10;
        private boolean autoDiscoverFactories = true;
        private boolean metricsEnabled = false;
        private com.warmup.core.config.PropertyResolver propertyResolver = null;
        private String[] activeProfiles = new String[0];

        /**
         * Sets the active profiles for conditional bean registration (@Profile).
         * 
         * @param profiles array of profile names to activate
         * @return this builder
         */
        public Builder profiles(String... profiles) {
            this.activeProfiles = profiles != null ? profiles : new String[0];
            return this;
        }

        /**
         * Enables or disables diagnostic mode.
         * When enabled, resolution paths are logged for debugging.
         * 
         * @param diagnostic true to enable diagnostics
         * @return this builder
         */
        public Builder diagnostic(boolean diagnostic) {
            this.diagnostic = diagnostic;
            return this;
        }

        /**
         * Sets the maximum number of pending background compilations.
         * Used for backpressure control during warmup.
         * 
         * @param maxPendingCompilations maximum concurrent compilations
         * @return this builder
         */
        public Builder maxPendingCompilations(int maxPendingCompilations) {
            this.maxPendingCompilations = maxPendingCompilations;
            return this;
        }

        /**
         * Sets an explicit JIT compiler implementation.
         * Bypasses ServiceLoader discovery.
         * 
         * @param jitCompiler the JIT compiler to use
         * @return this builder
         */
        public Builder jitCompiler(JITCompiler jitCompiler) {
            this.jitCompiler = jitCompiler;
            return this;
        }

        /**
         * Enables or disables auto-discovery of FactoryRegistrar via ServiceLoader.
         * Enabled by default for convenience, but can be disabled for minimal startup
         * or when using manual factory registration.
         * 
         * @param autoDiscoverFactories true to enable auto-discovery (default: true)
         * @return this builder
         */
        public Builder autoDiscoverFactories(boolean autoDiscoverFactories) {
            this.autoDiscoverFactories = autoDiscoverFactories;
            return this;
        }

        /**
         * Enables or disables metrics collection.
         * <p>
         * When enabled, the container tracks total resolutions, compile-time hits,
         * JIT hits, fallback count, and average resolution time. This incurs a small
         * overhead on the resolution path (two System.nanoTime() calls and LongAdder updates).
         * </p>
         * <p>
         * <strong>Disabled by default</strong> to avoid silent performance impact in production.
         * Enable explicitly for monitoring, profiling, or troubleshooting.
         * </p>
         * 
         * @param metricsEnabled true to enable metrics (default: false)
         * @return this builder
         */
        public Builder metrics(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        /**
         * Sets the PropertyResolver for resolving @Value expressions.
         * 
         * @param propertyResolver the property resolver to use
         * @return this builder
         */
        public Builder propertyResolver(com.warmup.core.config.PropertyResolver propertyResolver) {
            this.propertyResolver = propertyResolver;
            return this;
        }

        /**
         * Adds a PropertySource to the default PropertyResolver.
         * If no resolver exists yet, creates one with default sources plus the given one.
         * 
         * @param source the property source to add
         * @return this builder
         */
        public Builder propertySource(com.warmup.core.config.PropertySource source) {
            if (this.propertyResolver == null) {
                this.propertyResolver = new com.warmup.core.config.PropertyResolver();
            }
            this.propertyResolver.addSource(source);
            return this;
        }

        /**
         * Loads properties from a file path and adds them as a PropertySource.
         * 
         * @param path the path to the properties file
         * @return this builder
         */
        public Builder propertiesFile(String path) {
            com.warmup.core.config.PropertiesFilePropertySource source = 
                new com.warmup.core.config.PropertiesFilePropertySource(path);
            return propertySource(source);
        }

        /**
         * Enables system environment variables as a PropertySource.
         * 
         * @param enabled true to enable environment variables (default: true)
         * @return this builder
         */
        public Builder enableEnvironment(boolean enabled) {
            if (enabled) {
                if (this.propertyResolver == null) {
                    this.propertyResolver = new com.warmup.core.config.PropertyResolver();
                }
                this.propertyResolver.addSource(new com.warmup.core.config.SystemEnvironmentPropertySource());
            }
            return this;
        }

        /**
         * Enables system properties as a PropertySource.
         * 
         * @param enabled true to enable system properties (default: true)
         * @return this builder
         */
        public Builder enableSystemProperties(boolean enabled) {
            if (enabled) {
                if (this.propertyResolver == null) {
                    this.propertyResolver = new com.warmup.core.config.PropertyResolver();
                }
                this.propertyResolver.addSource(new com.warmup.core.config.SystemPropertiesPropertySource());
            }
            return this;
        }

        /**
         * Builds the Warmup instance.
         * 
         * Discovers JITCompiler via ServiceLoader if not explicitly set.
         * Falls back to NoOpJITCompiler if no provider is found.
         * 
         * @return new Warmup instance
         */
        public Warmup build() {
            JITCompiler compiler = jitCompiler;
            if (compiler == null) {
                compiler = discoverJITCompiler();
            }
            // Set active profiles in property resolver if available
            if (propertyResolver != null && activeProfiles.length > 0) {
                // Store profiles as a special property for condition evaluation
                System.setProperty("warmup.profiles.active", String.join(",", activeProfiles));
            } else if (activeProfiles.length > 0) {
                propertyResolver = new com.warmup.core.config.PropertyResolver();
                System.setProperty("warmup.profiles.active", String.join(",", activeProfiles));
            }
            HybridContainerConfig config = new HybridContainerConfig(
                diagnostic,
                maxPendingCompilations,
                autoDiscoverFactories,
                metricsEnabled,
                propertyResolver,
                activeProfiles
            );
            return new Warmup(new HybridContainer(config, compiler));
        }

        /**
         * Discovers JITCompiler implementation via ServiceLoader.
         * Falls back to NoOpJITCompiler if no provider is found.
         * 
         * @return discovered or fallback JITCompiler
         */
        private JITCompiler discoverJITCompiler() {
            return ServiceLoader.load(JITCompiler.class)
                    .findFirst()
                    .orElseGet(NoOpJITCompiler::new);
        }
    }
}
