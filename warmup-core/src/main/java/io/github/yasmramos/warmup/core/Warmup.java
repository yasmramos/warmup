package io.github.yasmramos.warmup.core;

import io.github.yasmramos.warmup.asm.AsmJITCompiler;
import io.github.yasmramos.warmup.core.container.HotReloadCapable;
import io.github.yasmramos.warmup.core.container.Container;
import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.jit.JITCompiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.yasmramos.warmup.core.config.PropertiesFilePropertySource;
import io.github.yasmramos.warmup.core.config.PropertyResolver;
import io.github.yasmramos.warmup.core.config.PropertySource;
import io.github.yasmramos.warmup.core.config.SystemEnvironmentPropertySource;
import io.github.yasmramos.warmup.core.config.SystemPropertiesPropertySource;
import io.github.yasmramos.warmup.core.container.BeanHandle;
import io.github.yasmramos.warmup.core.container.ContainerMetrics;
import io.github.yasmramos.warmup.core.container.ResolutionDiagnostic;
import io.github.yasmramos.warmup.core.jit.CompilationStats;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Main entry point for the Warmup dependency injection container.
 *
 * This class provides an ergonomic API for creating and using the DI container,
 * wrapping HybridContainer with a simpler interface. The JIT compiler
 * (AsmJITCompiler) is instantiated directly from warmup-core since ASM is now
 * embedded in the core module.
 *
 * Usage:
 * <pre>{@code
 * // Simple usage with defaults
 * Warmup warmup = Warmup.builder().build();
 * UserService service = warmup.resolve(UserService.class);
 *
 * // Alternative using get (same behavior)
 * UserService service2 = warmup.get(UserService.class);
 *
 * // Advanced usage with builder
 * Warmup warmup = Warmup.builder()
 *     .diagnostic(true)
 *     .maxPendingCompilations(20)
 *     .build();
 * }</pre>
 *
 * Architecture note: The JITCompiler implementation (AsmJITCompiler) is now
 * instantiated directly from warmup-core. A NoOp fallback is available for
 * environments where ASM bytecode generation should not be used (e.g., GraalVM
 * native images).
 *
 * @see Container
 * @see JITCompiler
 */
public class Warmup implements AutoCloseable {

    private final AtomicBoolean shutdownComplete = new AtomicBoolean(false);
    private Thread shutdownHook;
    private final AsmJITCompiler jitCompiler;
    private final Container container;

    /**
     * Returns a builder for advanced configuration.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private Warmup(ContainerConfig config) {
        this.jitCompiler = new AsmJITCompiler();
        this.container = new Container(config, jitCompiler);
    }

    /**
     * Returns the hot-reload capability if available.
     *
     * <p>
     * This provides explicit opt-in access to hot-reload functionality without
     * exposing the full internal container API. The returned capability allows
     * reloading bean factories at runtime.</p>
     *
     * <p>
     * <strong>Important:</strong> Hot-reload only affects NEW resolutions.
     * Existing references to previously resolved instances are NOT replaced
     * automatically.</p>
     *
     * @return Optional containing the hot-reload capability (always present for
     * standard Warmup instances)
     * @see HotReloadCapable#reload(String)
     */
    public HotReloadCapable hotReload() {
        return container;
    }

    /**
     * Registers a bean definition with a pre-compiled factory.
     *
     * @param definition the bean definition
     * @param factory the compiled factory
     * @param <T> the bean type
     */
    public <T> void register(BeanDefinition definition, CompiledFactory<T> factory) {
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
     * Resolves a bean by type. This is the primary resolution method in the
     * public API.
     *
     * @param clazz the bean class
     * @return the bean instance
     * @throws IllegalStateException if bean not found
     */
    public <T> T resolve(Class<T> clazz) {
        return container.resolve(clazz);
    }

    /**
     * Resolves a bean by type. Alias for {@link #resolve(Class)} with
     * Avaje-style nomenclature.
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
     * Resolves a bean by type and returns a BeanHandle for efficient repeated
     * resolutions.
     *
     * <p>
     * The handle captures the resolution result once and allows subsequent
     * resolutions without ClassValue lookup overhead. It automatically detects
     * invalidation when the bean is reloaded or dynamically re-registered.</p>
     *
     * @param <T> the bean type
     * @param clazz the bean class
     * @return a BeanHandle for repeated resolution
     * @throws IllegalStateException if bean not found
     * @see #get(Class)
     */
    public <T> BeanHandle<T> handle(Class<T> clazz) {
        return container.handle(clazz);
    }

    /**
     * Resolves all beans of the given type as a List. This is used for
     * collection injection (List<T>, Set<T>).
     *
     * @param <T> the bean type
     * @param clazz the bean class
     * @return list of all bean instances of this type (may be empty)
     */
    public <T> List<T> resolveAll(Class<T> clazz) {
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
    public <T> Map<String, T> resolveAllAsMap(Class<T> clazz) {
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
    public ContainerMetrics getMetrics() {
        return container.getMetrics();
    }

    /**
     * Gets compilation statistics from the JIT compiler.
     *
     * @return compilation stats
     */
    public CompilationStats getCompilationStats() {
        return container.getCompilationStats();
    }

    /**
     * Gets resolution diagnostics if diagnostic mode is enabled.
     *
     * @return list of diagnostics
     */
    public List<ResolutionDiagnostic> getDiagnostics() {
        return container.getDiagnostics();
    }

    /**
     * Registers a compile-time factory for a bean with type-safe validation.
     * <p>
     * This method validates at registration time that the factory's type is
     * compatible with the bean definition's type, failing fast instead of
     * deferring the error to the first {@code resolve()} call.
     * </p>
     *
     * @param <T> the bean type
     * @param beanName the bean name
     * @param type the expected bean type (used for validation)
     * @param factory the compiled factory
     * @throws IllegalStateException if no bean definition exists for this name,
     * or if the factory's type is not assignable to the bean's declared type
     */
    public <T> void registerFactory(String beanName, Class<T> type, CompiledFactory<T> factory) {
        container.registerFactory(beanName, type, factory);
    }

    /**
     * Registers a bean with a Supplier and explicit scope.
     * <p>
     * This is a convenience method for registering beans without needing to
     * create a BeanDefinition manually. The bean will be instantiated on-demand
     * using the provided Supplier.
     * </p>
     *
     * @param <T> the bean type
     * @param name the bean name
     * @param type the bean type
     * @param supplier the supplier that creates bean instances
     * @param scope the scope for this bean (SINGLETON or PROTOTYPE)
     * @throws IllegalStateException if a bean with this name already exists
     */
    public <T> void register(String name, Class<T> type, Supplier<T> supplier, Scope scope) {
        LifecycleCallbacks<T> lifecycle = LifecycleCallbacks.empty();
        BeanDefinition<T> definition = new BeanDefinition<>(
                type, name, scope, lifecycle, false, new Object[0], new String[0], new String[0]
        );
        container.registerDynamic(definition);

        // Create a simple CompiledFactory wrapper for the supplier
        CompiledFactory<T> factory = new CompiledFactory<T>() {
            @Override
            public T create(Object... dependencies) {
                return supplier.get();
            }

            @Override
            public Class<T> getBeanType() {
                return type;
            }
        };
        container.registerFactory(name, type, factory);
    }

    public boolean isDevelopmentMode() {
        return container.isDevelopmentMode();
    }

    public void setDevelopmentMode(boolean enable) {
        container.setDevelopmentMode(enable);
    }

    public int indexOf(String name) {
        return container.indexOf(name);
    }

    /**
     * Resolves a bean by integer index (experimental fast path). Bypasses
     * String hashing and ClassValue lookup via direct array access. The
     * behavior is scope-aware: cached singletons are returned directly (null
     * if not yet created), while prototypes produce a new instance through the
     * container's compiled factory on every call.
     *
     * <p>
     * Intended for benchmarks and performance-critical internal paths, not
     * daily application code. Pair with {@link #indexOf(String)}.</p>
     *
     * @param <T> the bean type
     * @param index the bean index (obtained via {@link #indexOf(String)})
     * @return the cached singleton instance (or null if not present), or a
     *         newly created prototype instance
     * @experimental Internal API for performance-critical paths
     */
    public <T> T resolveByIndex(int index) {
        return container.resolveByIndex(index);
    }

    /**
     * Returns the compiled factory the container actually uses for the given
     * type (same instance as on the production resolution path). Forces JIT
     * compilation if the factory has not been computed yet; returns null if
     * no bean definition exists for the type.
     *
     * <p>
     * Intended for diagnostics and benchmarks that must measure the real
     * container-managed factory instead of a standalone compiler instance.</p>
     *
     * @param <T> the bean type
     * @param type the bean class
     * @return the container's compiled factory, or null if the type is unknown
     * @see Container#getCompiledFactory(Class)
     */
    public <T> CompiledFactory<T> getCompiledFactory(Class<T> type) {
        return container.getCompiledFactory(type);
    }

    /**
     * Returns the internal container backing this facade. Intended strictly for
     * benchmarks and diagnostics that need container-level APIs not exposed on
     * the public facade (e.g. name-based resolution of ambiguous types).
     *
     * <p><strong>Do not use in application code.</strong> Prefer the facade
     * methods ({@link #resolve(Class)}, {@link #handle(Class)}, etc.).</p>
     *
     * @return the internal container instance
     */
    public Container getContainer() {
        return container;
    }
    
    public CompletableFuture<?> warmupAsync(){
        throw new UnsupportedOperationException("Not supported yet");
    }
    
    public void preload(Class<?> type){
        throw new UnsupportedOperationException("Not supported yet");
    }
    
    public String describe(){
        throw new UnsupportedOperationException("Not supported yet");
    }

    /**
     * Shuts down the container, releasing resources. This method is idempotent:
     * calling it multiple times has no additional effect. It ensures that
     * destruction callbacks (@PreDestroy/onDestroy) are applied, the
     * warmupExecutor is shut down, and JIT compiler resources are released.
     *
     * <p>
     * If a shutdown hook was registered during construction, it will be
     * unregistered to prevent it from running after manual shutdown (unless the
     * JVM is already shutting down).</p>
     */
    public void shutdown() {
        if (!shutdownComplete.compareAndSet(false, true)) {
            return;
        }

        // Attempt to unregister the shutdown hook if it exists and we're not in the hook itself
        if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // JVM is already shutting down, ignore
            }
        }

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

        private boolean diagnostic = false;
        private int maxPendingCompilations = 10;
        private boolean autoDiscoverFactories = true;
        private boolean metricsEnabled = false;
        private PropertyResolver propertyResolver = null;
        private String[] activeProfiles = new String[0];
        private boolean registerShutdownHook = true;
        private boolean devMode = false;

        /**
         * Sets the active profiles for conditional bean registration
         * (@Profile).
         *
         * @param profiles array of profile names to activate
         * @return this builder
         */
        public Builder profiles(String... profiles) {
            this.activeProfiles = profiles != null ? profiles : new String[0];
            return this;
        }

        /**
         * Enables or disables diagnostic mode. When enabled, resolution paths
         * are logged for debugging.
         *
         * @param diagnostic true to enable diagnostics
         * @return this builder
         */
        public Builder diagnostic(boolean diagnostic) {
            this.diagnostic = diagnostic;
            return this;
        }

        /**
         * Sets the maximum number of pending background compilations. Used for
         * backpressure control during warmup.
         *
         * @param maxPendingCompilations maximum concurrent compilations
         * @return this builder
         */
        public Builder maxPendingCompilations(int maxPendingCompilations) {
            this.maxPendingCompilations = maxPendingCompilations;
            return this;
        }

        /**
         * Enables or disables auto-discovery of FactoryRegistrar via
         * ServiceLoader. Enabled by default for convenience, but can be
         * disabled for minimal startup or when using manual factory
         * registration.
         *
         * @param autoDiscoverFactories true to enable auto-discovery (default:
         * true)
         * @return this builder
         */
        public Builder autoDiscoverFactories(boolean autoDiscoverFactories) {
            this.autoDiscoverFactories = autoDiscoverFactories;
            return this;
        }

        /**
         * Enables or disables metrics collection.
         * <p>
         * When enabled, the container tracks total resolutions, compile-time
         * hits, JIT hits, fallback count, and average resolution time. This
         * incurs a small overhead on the resolution path (two System.nanoTime()
         * calls and LongAdder updates).
         * </p>
         * <p>
         * <strong>Disabled by default</strong> to avoid silent performance
         * impact in production. Enable explicitly for monitoring, profiling, or
         * troubleshooting.
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
        public Builder propertyResolver(PropertyResolver propertyResolver) {
            this.propertyResolver = propertyResolver;
            return this;
        }

        /**
         * Adds a PropertySource to the default PropertyResolver. If no resolver
         * exists yet, creates one with default sources plus the given one.
         *
         * @param source the property source to add
         * @return this builder
         */
        public Builder propertySource(PropertySource source) {
            if (this.propertyResolver == null) {
                this.propertyResolver = new PropertyResolver();
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
            PropertiesFilePropertySource source = new PropertiesFilePropertySource(path);
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
                    this.propertyResolver = new PropertyResolver();
                }
                this.propertyResolver.addSource(new SystemEnvironmentPropertySource());
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
                    this.propertyResolver = new PropertyResolver();
                }
                this.propertyResolver.addSource(new SystemPropertiesPropertySource());
            }
            return this;
        }

        /**
         * Configures whether to register a JVM shutdown hook that calls
         * {@link Warmup#shutdown()} when the JVM terminates.
         *
         * <p>
         * When enabled (default), a shutdown hook is registered during
         * {@link #build()} that ensures proper cleanup of container resources,
         * including:
         * <ul>
         * <li>Execution of @PreDestroy/onDestroy callbacks</li>
         * <li>Shutdown of the warmupExecutor thread pool</li>
         * <li>Release of JIT compiler resources</li>
         * </ul>
         * </p>
         *
         * <p>
         * The shutdown hook is automatically unregistered when
         * {@link Warmup#shutdown()} is called manually, preventing duplicate
         * cleanup. The hook runs only if the JVM terminates without explicit
         * shutdown.</p>
         *
         * <p>
         * <strong>Recommended usage:</strong> Keep enabled for
         * application-level containers. Disable for tests or environments where
         * multiple Warmup instances are created and destroyed frequently to
         * avoid accumulating shutdown hooks.</p>
         *
         * @param value true to register shutdown hook (default: true)
         * @return this builder
         */
        public Builder registerShutdownHook(boolean value) {
            this.registerShutdownHook = value;
            return this;
        }

        public Builder enableDevMode(boolean value) {
            this.devMode = value;
            return this;
        }

        /**
         * Builds the Warmup instance.
         *
         * Uses AsmJITCompiler by default for bytecode generation. If
         * {@link #registerShutdownHook} is true (default), registers a JVM
         * shutdown hook that calls {@link Warmup#shutdown()} on termination.
         *
         * @return new Warmup instance
         */
        public Warmup build() {
            // Set active profiles in property resolver if available
            if (propertyResolver != null && activeProfiles.length > 0) {
                // Store profiles as a special property for condition evaluation
                System.setProperty("warmup.profiles.active", String.join(",", activeProfiles));
            } else if (activeProfiles.length > 0) {
                propertyResolver = new PropertyResolver();
                System.setProperty("warmup.profiles.active", String.join(",", activeProfiles));
            }

            ContainerConfig config = ContainerConfig.builder()
                    .activeProfiles(activeProfiles)
                    .autoDiscoverFactories(autoDiscoverFactories)
                    .diagnosticMode(diagnostic)
                    .maxPendingCompilations(maxPendingCompilations)
                    .metricsEnabled(metricsEnabled)
                    .propertyResolver(propertyResolver)
                    .devMode(devMode)
                    .build();

            Warmup warmup = new Warmup(config);

            // Register shutdown hook if enabled
            if (registerShutdownHook) {
                warmup.shutdownHook = new Thread(warmup::shutdown, "warmup-shutdown-hook");
                Runtime.getRuntime().addShutdownHook(warmup.shutdownHook);
            }

            return warmup;
        }
    }
}
