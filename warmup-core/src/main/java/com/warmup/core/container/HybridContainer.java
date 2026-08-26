package com.warmup.core.container;

import com.warmup.core.annotation.InternalApi;
import com.warmup.core.graph.DependencyGraph;
import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.jit.CompilationException;
import com.warmup.core.jit.CompilationStats;
import com.warmup.core.jit.FactoryRegistrar;
import com.warmup.core.jit.JITCompiler;
import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.registry.BeanRegistry;
import com.warmup.core.registry.BeanRegistryImpl;
import com.warmup.core.registry.ResolvedBeanDefinition;
import com.warmup.core.scope.Scope;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.ServiceLoader;

/**
 * Hybrid DI container combining compile-time and JIT compilation.
 * 
 * Architecture:
 * - Path A (compile-time): Uses pre-generated CompiledFactory implementations
 * - Path B (JIT runtime): Dynamically compiles factories using ASM
 * - Fallback: Transparent fallback when no factory exists
 * 
 * Thread Safety:
 * - All operations are thread-safe using lock-free data structures
 * - Background warmup uses dedicated executor with backpressure handling
 */
public class HybridContainer implements HotReloadCapable, AutoCloseable {

    private final BeanRegistry registry = new BeanRegistryImpl();
    private final DependencyGraph dependencyGraph = new DependencyGraph();
    private final JITCompiler jitCompiler;
    
    // Unified factory cache: combines both compile-time and JIT factories
    // Key: bean name, Value: factory with metadata about origin for metrics
    private final Map<String, CompiledFactory<?>> factoryCache = new ConcurrentHashMap<>();
    
    // Track which factories are from compile-time vs JIT for metrics
    private final Set<String> compileTimeFactoryNames = ConcurrentHashMap.newKeySet();
    
    // Cache for type-based resolution to avoid Optional allocation and double lookup
    // Maps bean type directly to ResolvedBeanDefinition for fast path in resolve(Class)
    // Note: resolvedDefinitions by name has been moved to BeanRegistryImpl for single-lookup optimization
    private final Map<Class<?>, ResolvedBeanDefinition<?>> resolvedDefinitionsByType = new ConcurrentHashMap<>();
    
    // Empty array constant to avoid allocation for beans without dependencies
    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    
    // Diagnostic mode flag
    private final boolean diagnosticMode;
    /**
     * Thread-safe list for collecting resolution diagnostics.
     * Uses CopyOnWriteArrayList to ensure thread safety during concurrent writes
     * when diagnosticMode is enabled. This has write overhead but is acceptable
     * since diagnostics are only collected in diagnostic mode (not production default).
     */
    private final List<ResolutionDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    
    // Metrics tracking using LongAdder for thread-safe increments
    // Only updated when metricsEnabled is true to avoid overhead on fast path
    private final LongAdder totalResolutions = new LongAdder();
    private final LongAdder compileTimeHits = new LongAdder();
    private final LongAdder jitHits = new LongAdder();
    private final LongAdder fallbackCount = new LongAdder();
    private final LongAdder resolutionTimeAccumulator = new LongAdder();
    
    /**
     * Flag to enable/disable metrics collection.
     * When false, the fast path avoids all timing, Optional allocations, and LongAdder updates.
     * Default is false for performance; enable explicitly for monitoring or diagnostics.
     */
    private final boolean metricsEnabled;
    
    // Background warmup executor with semaphore for backpressure
    // Lazily initialized to avoid allocation when not using dynamic registration
    private volatile ExecutorService warmupExecutor;
    private final Semaphore warmupSemaphore;
    
    // Track beans pending warmup for lazy compilation strategy
    // Beans are marked pending during registerDynamic and warmed up on first resolve
    // Using ConcurrentHashMap.newKeySet() for thread-safe set operations
    private final Set<String> pendingWarmupBeans = ConcurrentHashMap.newKeySet();
    
    /**
     * Flag to enable/disable auto-discovery of FactoryRegistrar via ServiceLoader.
     * Enabled by default for convenience, but can be disabled for minimal startup
     * or when using manual factory registration.
     */
    private final boolean autoDiscoverFactories;

    /**
     * Cached flag indicating if running in GraalVM native image mode.
     * Computed once at class load time to avoid repeated reflection overhead.
     */
    private static final boolean IS_NATIVE_IMAGE = computeNativeImage();

    /**
     * Computes whether running in GraalVM native image mode.
     * Uses reflection to check for GraalVM's ImageInfo class.
     * Returns false if GraalVM is not available or any error occurs.
     */
    private static boolean computeNativeImage() {
        try {
            Class<?> imageInfoClass = Class.forName("org.graalvm.nativeimage.ImageInfo");
            Object inImageCode = imageInfoClass.getMethod("inImageCode").invoke(null);
            return Boolean.TRUE.equals(inImageCode);
        } catch (ReflectiveOperationException e) {
            // Not running in GraalVM or ImageInfo not available
            return false;
        }
    }

    /**
     * Creates a new HybridContainer with default settings.
     * Auto-discovers FactoryRegistrar implementations via ServiceLoader.
     * Metrics are disabled by default for performance.
     * 
     * @param jitCompiler the JIT compiler for runtime factory generation
     * @param diagnosticMode if true, logs resolution path for each bean
     * @deprecated Use {@link #HybridContainer(HybridContainerConfig)} or {@link Warmup#builder()} for explicit configuration.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode) {
        this(jitCompiler, diagnosticMode, 10, true, false);
    }

    /**
     * Creates a new HybridContainer with custom warmup configuration.
     * Auto-discovers FactoryRegistrar implementations via ServiceLoader.
     * Metrics are disabled by default for performance.
     * 
     * @param jitCompiler the JIT compiler for runtime factory generation
     * @param diagnosticMode if true, logs resolution path for each bean
     * @param maxPendingCompilations maximum concurrent background compilations
     * @deprecated Use {@link #HybridContainer(HybridContainerConfig)} or {@link Warmup#builder()} for explicit configuration.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode, int maxPendingCompilations) {
        this(jitCompiler, diagnosticMode, maxPendingCompilations, true, false);
    }

    /**
     * Creates a new HybridContainer with full configuration.
     * Metrics are disabled by default for performance.
     * 
     * @param jitCompiler the JIT compiler for runtime factory generation
     * @param diagnosticMode if true, logs resolution path for each bean
     * @param maxPendingCompilations maximum concurrent background compilations
     * @param autoDiscoverFactories if true, automatically discovers and registers
     *        compile-time factories via ServiceLoader at startup (default: true)
     * @deprecated Use {@link #HybridContainer(HybridContainerConfig)} or {@link Warmup#builder()} for explicit configuration.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode, int maxPendingCompilations, boolean autoDiscoverFactories) {
        this(jitCompiler, diagnosticMode, maxPendingCompilations, autoDiscoverFactories, false);
    }

    /**
     * Creates a new HybridContainer with full configuration including metrics toggle.
     * 
     * @param jitCompiler the JIT compiler for runtime factory generation
     * @param diagnosticMode if true, logs resolution path for each bean
     * @param maxPendingCompilations maximum concurrent background compilations
     * @param autoDiscoverFactories if true, automatically discovers and registers
     *        compile-time factories via ServiceLoader at startup (default: true)
     * @param metricsEnabled if true, enables metrics collection (totalResolutions, timing, etc.);
     *        when false, the fast path avoids all timing, Optional allocations, and LongAdder updates
     * @deprecated Use {@link #HybridContainer(HybridContainerConfig)} or {@link Warmup#builder()} for explicit configuration.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode, int maxPendingCompilations, boolean autoDiscoverFactories, boolean metricsEnabled) {
        this(new HybridContainerConfig(diagnosticMode, maxPendingCompilations, autoDiscoverFactories, metricsEnabled), jitCompiler);
    }
    
    /**
     * Creates a new HybridContainer with the specified configuration.
     * 
     * @param config the configuration object containing all flags
     * @param jitCompiler the JIT compiler for runtime factory generation
     */
    public HybridContainer(HybridContainerConfig config, JITCompiler jitCompiler) {
        this.jitCompiler = jitCompiler;
        this.diagnosticMode = config.diagnosticMode();
        this.autoDiscoverFactories = config.autoDiscoverFactories();
        this.metricsEnabled = config.metricsEnabled();
        this.warmupSemaphore = new Semaphore(config.maxPendingCompilations());
        // Lazy initialization of warmupExecutor - created on first use
        this.warmupExecutor = null;
        
        // Auto-discover and register compile-time factories once at startup
        if (autoDiscoverFactories) {
            discoverAndRegisterFactories();
        }
    }
    
    /**
     * Discovers all FactoryRegistrar implementations via ServiceLoader and registers
     * their factories. This is a one-time startup cost, not on the resolution path.
     * 
     * <p>If no registrars are found (empty ServiceLoader), this method does nothing.</p>
     */
    private void discoverAndRegisterFactories() {
        ServiceLoader<FactoryRegistrar> loader = ServiceLoader.load(FactoryRegistrar.class);
        // First pass: register all factories
        for (FactoryRegistrar registrar : loader) {
            registrar.registerAll((definition, factory) -> {
                registry.register(definition);
                factoryCache.put(definition.name(), factory);
                // Mark as compile-time and get ResolvedBeanDefinition to set flags
                ResolvedBeanDefinition<?> resolvedDef = registry.getResolvedOrNull(definition.name());
                if (resolvedDef != null) {
                    resolvedDef.setCompileTime(true);
                }
            });
        }
        
        // Second pass: wire factories with their dependencies
        wireFactories();
    }
    
    /**
     * Wires all registered factories with their dependency factories.
     * Called after all factories are registered to enable direct factory-to-factory calls.
     */
    @SuppressWarnings("unchecked")
    private void wireFactories() {
        for (Map.Entry<String, CompiledFactory<?>> entry : factoryCache.entrySet()) {
            String beanName = entry.getKey();
            CompiledFactory<?> factory = entry.getValue();
            
            BeanDefinition<?> definition = registry.getDefinitionOrNull(beanName);
            if (definition == null) {
                continue;
            }
            
            ResolvedBeanDefinition<?> resolvedDef = registry.getResolvedOrNull(beanName);
            
            Object[] dependencies = definition.dependencies();
            if (dependencies.length == 0) {
                // No dependencies: mark as wired since get() is safe to call directly
                if (resolvedDef != null) {
                    resolvedDef.setWired(true);
                }
                continue;
            }
            
            // Collect dependency factories
            CompiledFactory<?>[] depFactories = new CompiledFactory<?>[dependencies.length];
            boolean allResolved = true;
            
            for (int i = 0; i < dependencies.length; i++) {
                Object dep = dependencies[i];
                if (dep instanceof String depName) {
                    CompiledFactory<?> depFactory = factoryCache.get(depName);
                    if (depFactory != null) {
                        depFactories[i] = depFactory;
                    } else {
                        // Dependency factory not yet available (forward reference or dynamic bean)
                        allResolved = false;
                        break;
                    }
                } else {
                    // Direct object reference (not a bean name) - skip wiring for this dependency
                    allResolved = false;
                    break;
                }
            }
            
            // Wire the factory if all dependencies are available
            if (allResolved) {
                try {
                    factory.wire(depFactories);
                    // Mark as successfully wired
                    if (resolvedDef != null) {
                        resolvedDef.setWired(true);
                    }
                } catch (Exception e) {
                    // Ignore wiring errors - fallback to create(Object...) will still work
                    // Keep wired=false so hot-path uses create() instead of get()
                }
            }
        }
    }

    /**
     * Registers a bean with compile-time factory support.
     * 
     * @param <T> the bean type
     * @param definition the bean definition
     * @param factory the compile-time generated factory (if available)
     */
    public <T> void register(BeanDefinition<T> definition, CompiledFactory<T> factory) {
        registry.register(definition);
        
        if (factory != null) {
            factoryCache.put(definition.name(), factory);
            compileTimeFactoryNames.add(definition.name());
        }
        
        // Register in dependency graph - use Object[] overload to avoid String[] allocation
        dependencyGraph.registerBean(definition.name(), definition.dependencies());
    }

    /**
     * Registers a dynamic bean for JIT compilation.
     * Marks the bean as pending for lazy warmup on first resolve.
     * 
     * @param <T> the bean type
     * @param definition the bean definition
     */
    public <T> void registerDynamic(BeanDefinition<T> definition) {
        registry.register(definition);
        
        // Register in dependency graph - use Object[] overload to avoid String[] allocation
        dependencyGraph.registerBean(definition.name(), definition.dependencies());
        
        // Mark bean as pending warmup - actual compilation happens lazily on first resolve
        // This avoids allocating CompletableFuture and lambda per bean during mass registration
        pendingWarmupBeans.add(definition.name());
    }

    /**
     * Internal method to resolve a bean by name.
     * Used internally for dependency wiring and cableado de dependencias.
     * The public API should use resolve(Class) for type-safe resolution.
     * 
     * @param <T> the bean type
     * @param name the bean name
     * @return the resolved bean instance
     */
    @SuppressWarnings("unchecked")
    private <T> T resolveByName(String name) {
        // Check if this bean has pending warmup and trigger compilation on first resolve
        if (pendingWarmupBeans.contains(name)) {
            BeanDefinition<T> definition = (BeanDefinition<T>) registry.getDefinition(name).orElse(null);
            if (definition != null) {
                // Remove from pending set atomically to avoid duplicate warmup
                if (pendingWarmupBeans.remove(name)) {
                    // Trigger background warmup only when the bean is actually needed
                    triggerBackgroundWarmup(definition);
                }
            }
        }
        
        // Single lookup: get pre-computed ResolvedBeanDefinition directly from registry
        ResolvedBeanDefinition<T> resolvedDef = registry.getResolvedOrNull(name);
        if (resolvedDef == null) {
            throw new IllegalStateException("Bean not found: " + name);
        }
        
        // OPTIMIZATION 1: For PROTOTYPE beans, skip singleton cache lookup entirely
        // since prototypes are never cached. This avoids unnecessary ConcurrentHashMap.get()
        if (resolvedDef.scope() == Scope.PROTOTYPE) {
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                CompiledFactory<T> factory = resolvedDef.getOrComputeFactory(factoryCache);
                T instance;
                if (factory != null) {
                    // Use optimized path for prototypes: skip diagnostic overhead in hot path
                    instance = createPrototypeBeanWithFactory(resolvedDef, factory);
                } else {
                    instance = createViaReflection(resolvedDef);
                }
                if (resolvedDef.lifecycle().onInit() != null) {
                    resolvedDef.lifecycle().onInit().onInit(instance);
                }
                recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
                return instance;
            } else {
                CompiledFactory<T> factory = resolvedDef.getOrComputeFactory(factoryCache);
                T instance;
                if (factory != null) {
                    // Use optimized path for prototypes: skip all overhead
                    instance = createPrototypeBeanWithFactory(resolvedDef, factory);
                } else {
                    instance = createViaReflection(resolvedDef);
                }
                if (resolvedDef.lifecycle().onInit() != null) {
                    resolvedDef.lifecycle().onInit().onInit(instance);
                }
                return instance;
            }
        }
        
        // OPTIMIZATION 2: Fast-path for SINGLETON/CUSTOM with cached instance
        // Skip index lookup and Map.get() if instance is already cached in ResolvedBeanDefinition
        T cachedInstance = resolvedDef.getCachedInstance();
        if (cachedInstance != null) {
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
            }
            return cachedInstance;
        }
        
        // SINGLETON/CUSTOM path: use indexed resolution if index is cached
        int index = resolvedDef.getOrComputeIndex(registry);
        if (index >= 0) {
            // Try fast indexed resolution first (avoids String hashing)
            T indexedInstance = (T) registry.getIfPresent(index);
            if (indexedInstance != null) {
                if (metricsEnabled) {
                    long startTime = System.nanoTime();
                    recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
                }
                return indexedInstance;
            }
        }
        
        // Fall back to name-based lookup for singletons not yet cached
        T nameBasedInstance = registry.getIfPresent(name);
        if (nameBasedInstance != null) {
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
            }
            return nameBasedInstance;
        }
        
        // Singleton not yet created, use registry.getInstance for thread-safe lazy init
        if (metricsEnabled) {
            long startTime = System.nanoTime();
            T instance = registry.getInstance(resolvedDef.getDefinition(), () -> createBean(resolvedDef));
            // Publish the created instance for fast-path on subsequent resolutions
            resolvedDef.setCachedInstance(instance);
            recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
            return instance;
        } else {
            T instance = registry.getInstance(resolvedDef.getDefinition(), () -> createBean(resolvedDef));
            // Publish the created instance for fast-path on subsequent resolutions
            resolvedDef.setCachedInstance(instance);
            return instance;
        }
    }

    /**
     * Resolves a bean by type.
     * This is the primary entry-point for type-based resolution, optimized to avoid Optional allocation.
     * 
     * @param <T> the bean type
     * @param type the bean class
     * @return the resolved bean instance
     */
    public <T> T resolve(Class<T> type) {
        // Fast path: check cache for pre-resolved definition by type
        @SuppressWarnings("unchecked")
        ResolvedBeanDefinition<T> resolvedDef = (ResolvedBeanDefinition<T>) resolvedDefinitionsByType.get(type);
        
        if (resolvedDef == null) {
            // Cache miss: resolve definition without Optional allocation
            BeanDefinition<T> definition = registry.getDefinitionByTypeOrNull(type);
            if (definition == null) {
                throw new IllegalStateException("Bean not found for type: " + type.getName());
            }
            
            // Wrap and cache the resolved definition for future lookups
            resolvedDef = wrapResolvedDefinition(definition);
            ResolvedBeanDefinition<T> existing = (ResolvedBeanDefinition<T>) resolvedDefinitionsByType.putIfAbsent(type, resolvedDef);
            if (existing != null) {
                // Another thread beat us to it, use the existing one
                resolvedDef = existing;
            }
        } else {
            // Check if this bean has pending warmup and trigger compilation on first resolve via type
            String beanName = resolvedDef.name();
            if (pendingWarmupBeans.contains(beanName)) {
                BeanDefinition<T> definition = resolvedDef.getDefinition();
                // Remove from pending set atomically to avoid duplicate warmup
                if (pendingWarmupBeans.remove(beanName)) {
                    // Trigger background warmup only when the bean is actually needed
                    triggerBackgroundWarmup(definition);
                }
            }
        }
        
        // Use the cached resolved definition to resolve the bean
        return resolve(resolvedDef);
    }

    /**
     * Internal method to resolve a bean using a pre-resolved definition.
     * This avoids double lookup and is used by resolve(Class) after caching.
     */
    @SuppressWarnings("unchecked")
    private <T> T resolve(ResolvedBeanDefinition<T> resolvedDef) {
        // OPTIMIZATION 1: For PROTOTYPE beans, skip singleton cache lookup entirely
        // since prototypes are never cached. This avoids unnecessary ConcurrentHashMap.get()
        if (resolvedDef.scope() == Scope.PROTOTYPE) {
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                CompiledFactory<T> factory = resolvedDef.getOrComputeFactory(factoryCache);
                T instance;
                if (factory != null) {
                    // Use optimized path for prototypes: skip diagnostic overhead in hot path
                    instance = createPrototypeBeanWithFactory(resolvedDef, factory);
                } else {
                    instance = createViaReflection(resolvedDef);
                }
                if (resolvedDef.lifecycle().onInit() != null) {
                    resolvedDef.lifecycle().onInit().onInit(instance);
                }
                recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
                return instance;
            } else {
                CompiledFactory<T> factory = resolvedDef.getOrComputeFactory(factoryCache);
                T instance;
                if (factory != null) {
                    // Use optimized path for prototypes: skip all overhead
                    instance = createPrototypeBeanWithFactory(resolvedDef, factory);
                } else {
                    instance = createViaReflection(resolvedDef);
                }
                if (resolvedDef.lifecycle().onInit() != null) {
                    resolvedDef.lifecycle().onInit().onInit(instance);
                }
                return instance;
            }
        }
        
        // OPTIMIZATION 2: Fast-path for SINGLETON/CUSTOM with cached instance
        // Skip index lookup and Map.get() if instance is already cached in ResolvedBeanDefinition
        T cachedInstance = resolvedDef.getCachedInstance();
        if (cachedInstance != null) {
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
            }
            return cachedInstance;
        }
        
        // SINGLETON/CUSTOM path: use indexed resolution if index is cached
        int index = resolvedDef.getOrComputeIndex(registry);
        if (index >= 0) {
            // Try fast indexed resolution first (avoids String hashing)
            T indexedInstance = (T) registry.getIfPresent(index);
            if (indexedInstance != null) {
                if (metricsEnabled) {
                    long startTime = System.nanoTime();
                    recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
                }
                return indexedInstance;
            }
        }
        
        // Fall back to name-based lookup for singletons not yet cached
        String name = resolvedDef.name();
        T nameBasedInstance = registry.getIfPresent(name);
        if (nameBasedInstance != null) {
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
            }
            return nameBasedInstance;
        }
        
        // Singleton not yet created, use registry.getInstance for thread-safe lazy init
        if (metricsEnabled) {
            long startTime = System.nanoTime();
            T instance = registry.getInstance(resolvedDef.getDefinition(), () -> createBean(resolvedDef));
            // Publish the created instance for fast-path on subsequent resolutions
            resolvedDef.setCachedInstance(instance);
            recordMetrics(resolvedDef.getDefinition(), System.nanoTime() - startTime);
            return instance;
        } else {
            T instance = registry.getInstance(resolvedDef.getDefinition(), () -> createBean(resolvedDef));
            // Publish the created instance for fast-path on subsequent resolutions
            resolvedDef.setCachedInstance(instance);
            return instance;
        }
    }

    /**
     * Resolves a singleton bean by integer index (experimental fast path).
     * This method bypasses String hashing and Map lookup, using direct array access.
     * Only works for cached singletons; returns null if the bean is not yet cached.
     * 
     * @param <T> the bean type
     * @param index the bean index (obtained via {@link #indexOf(String)})
     * @return the cached singleton instance, or null if not present
     * @experimental Internal API for performance-critical paths
     */
    @SuppressWarnings("unchecked")
    public <T> T resolveByIndex(int index) {
        // Fast-path: check if singleton is already cached via indexed array access
        T cachedInstance = (T) registry.getIfPresent(index);
        if (cachedInstance != null) {
            // When metrics enabled: record timing and resolution count
            // When metrics disabled: bare return with no overhead
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                // Note: We can't get the definition by index easily, so skip detailed metrics
                totalResolutions.add(1);
                // Assume compile-time hit for indexed path (typical use case)
                compileTimeHits.add(1);
            }
            return cachedInstance;
        }
        return null;
    }

    /**
     * Returns the integer index for a bean name (experimental).
     * 
     * @param name the bean name
     * @return the bean index, or -1 if not found
     * @experimental Internal API for performance-critical paths
     */
    public int indexOf(String name) {
        return registry.indexOf(name);
    }

    /**
     * Checks if a bean is registered.
     */
    public boolean contains(String name) {
        return registry.contains(name);
    }

    /**
     * Checks if a bean type is registered.
     */
    public boolean contains(Class<?> type) {
        return registry.getDefinitionByType(type).isPresent();
    }

    /**
     * Gets all registered bean names.
     */
    public Set<String> getBeanNames() {
        return registry.getBeanNames();
    }

    /**
     * Gets diagnostic information for all resolutions collected so far.
     * Returns an unmodifiable snapshot of the diagnostics list.
     * 
     * @apiNote Diagnostics are only collected when {@code diagnosticMode} is true.
     * In production (diagnosticMode=false), this method returns an empty list and
     * no data is collected, ensuring zero overhead on the resolution path.
     * The returned list is thread-safe and represents a consistent snapshot.
     */
    public List<ResolutionDiagnostic> getDiagnostics() {
        // CopyOnWriteArrayList already provides a safe snapshot via iterator
        // Return an unmodifiable wrapper to prevent external modification
        return Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    /**
     * Gets current container metrics.
     */
    public ContainerMetrics getMetrics() {
        long total = totalResolutions.sum();
        long compileHits = compileTimeHits.sum();
        long jitHitCount = jitHits.sum();
        long fallback = fallbackCount.sum();
        
        double hitRate = total > 0 
            ? ((compileHits + jitHitCount) * 100.0 / total) 
            : 0.0;
        
        long avgResolutionTimeNs = total > 0 
            ? resolutionTimeAccumulator.sum() / total 
            : 0L;
        
        return new ContainerMetrics(
            total,
            compileHits,
            jitHitCount,
            fallback,
            avgResolutionTimeNs,
            hitRate
        );
    }

    /**
     * Gets JIT compilation statistics.
     */
    public CompilationStats getCompilationStats() {
        return jitCompiler.getStats();
    }

    /**
     * Shuts down the container, applying destroy callbacks and terminating the warmup executor.
     * Implements AutoCloseable for try-with-resources support.
     */
    public void shutdown() {
        ExecutorService executor = warmupExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
        registry.clear();
        jitCompiler.clear();
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Hot-reloads a bean by invalidating all caches and recompiling its factory.
     * 
     * <p>This method performs the following steps atomically with respect to new resolutions:</p>
     * <ol>
     *   <li>Evicts the cached singleton instance and applies destroy callbacks</li>
     *   <li>Removes entries from {@code compileTimeFactories} and {@code jitFactoryCache}</li>
     *   <li>Unloads the previous ASM factory via {@code jitCompiler.unloadFactory()}</li>
     *   <li>Triggers background recompilation</li>
     * </ol>
     * 
     * <p><strong>Note:</strong> Hot-reload guarantees that NEW resolutions will use the reloaded factory.
     * However, existing instances of the bean that were already returned to callers will continue to exist
     * until they are garbage collected or re-resolved. This method does not introduce proxies.</p>
     * 
     * <p>Concurrent reloads of the same bean should be serialized externally if strict ordering is required.</p>
     * 
     * @param name the bean name to reload
     * @return true if the bean existed and was reloaded, false if the bean was not found
     * @param <T> the bean type
     */
    @SuppressWarnings("unchecked")
    public <T> boolean reload(String name) {
        BeanDefinition<T> definition = (BeanDefinition<T>) registry.getDefinition(name).orElse(null);
        if (definition == null) {
            return false;
        }
        
        // Step 1: Evict cached singleton instance and apply destroy callback
        registry.evictInstance(name);
        
        // Also invalidate the cached instance in ResolvedBeanDefinition to prevent stale references
        ResolvedBeanDefinition<T> resolvedDef = registry.getResolvedOrNull(name);
        if (resolvedDef != null) {
            resolvedDef.setCachedInstance(null);
        }
        
        // Step 2: Remove from unified factory cache and tracking set
        factoryCache.remove(name);
        compileTimeFactoryNames.remove(name);
        
        // Also remove from pending warmup set if present - reload takes precedence
        pendingWarmupBeans.remove(name);
        
        // Step 3: Unload the previous ASM factory to free ClassLoader and metaspace
        jitCompiler.unloadFactory(definition.type());
        
        // Step 4: Trigger background recompilation immediately (not lazy)
        triggerBackgroundWarmup(definition);
        
        return true;
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
     * @param name the bean name
     * @param type the expected bean type (used for validation)
     * @param factory the compiled factory
     * @throws IllegalStateException if no bean definition exists for this name,
     *         or if the factory's type is not assignable to the bean's declared type
     */
    public <T> void registerFactory(String name, Class<T> type, CompiledFactory<T> factory) {
        // Validate that a bean definition exists for this name
        BeanDefinition<?> definition = registry.getDefinition(name)
            .orElseThrow(() -> new IllegalStateException(
                "Cannot register factory for unknown bean: '" + name + "'. " +
                "Register the bean definition first."));
        
        // Validate type compatibility: the bean definition's type must be assignable from the factory's type
        // This ensures the factory produces instances compatible with what the container expects
        if (!definition.type().isAssignableFrom(type)) {
            throw new IllegalStateException(
                "Type mismatch for bean '" + name + "': " +
                "factory produces '" + type.getName() + "' but bean definition expects '" + 
                definition.type().getName() + "'. " +
                "The factory type must be assignable to the bean definition type.");
        }
        
        factoryCache.put(name, factory);
        compileTimeFactoryNames.add(name);
    }

    /**
     * Registers a compile-time factory for a bean.
     * Called by generated code from annotation processor.
     * 
     * @deprecated Use {@link #registerFactory(String, Class, CompiledFactory)} for type-safe registration.
     * This wildcard version does not validate types and may cause {@code ClassCastException}
     * at resolution time if the factory type doesn't match the bean definition.
     * Kept for backward compatibility with generated code.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public void registerFactory(String beanName, CompiledFactory<?> factory) {
        // If no BeanDefinition exists for this name, create one from the factory's bean type
        // This allows compile-time factories discovered via ServiceLoader to work without
        // explicit BeanDefinition registration.
        if (!registry.getDefinition(beanName).isPresent()) {
            Class<?> beanType = factory.getBeanType();
            if (beanType != null) {
                // Create a default BeanDefinition with SINGLETON scope and no dependencies
                // The actual scope and dependencies are encoded in the generated factory
                @SuppressWarnings("unchecked")
                BeanDefinition<Object> definition = new BeanDefinition<>(
                    (Class<Object>) beanType,
                    beanName,
                    com.warmup.core.scope.Scope.SINGLETON
                );
                registry.register(definition);
            }
        }
        
        factoryCache.put(beanName, factory);
        compileTimeFactoryNames.add(beanName);
    }

    // Internal methods

    /**
     * Creates a bean instance using its factory, avoiding redundant lookups.
     * This optimized version receives a ResolvedBeanDefinition with cached data.
     */
    @SuppressWarnings("unchecked")
    private <T> T createBean(ResolvedBeanDefinition<T> resolvedDef) {
        BeanDefinition<T> definition = resolvedDef.getDefinition();
        String name = definition.name();
        long compileTimeNs = 0;
        ResolutionDiagnostic.ResolutionPath path = null;
        
        // Check if running in GraalVM native image mode - disable JIT
        boolean nativeImage = IS_NATIVE_IMAGE;
        
        // Get cached factory from resolved definition to avoid redundant lookup
        CompiledFactory<T> factory = resolvedDef.getOrComputeFactory(factoryCache);
        if (factory != null) {
            // Hot path: factory already cached, determine origin for metrics/diagnostic only if enabled
            if (metricsEnabled || diagnosticMode) {
                if (resolvedDef.isCompileTime()) {
                    path = ResolutionDiagnostic.ResolutionPath.COMPILE_TIME;
                    if (metricsEnabled) {
                        compileTimeHits.add(1);
                    }
                } else {
                    path = ResolutionDiagnostic.ResolutionPath.JIT;
                    if (metricsEnabled) {
                        jitHits.add(1);
                    }
                }
            }
        } else if (!nativeImage) {
            // Try JIT compilation and cache the result
            try {
                factory = jitCompiler.compile(definition.type(), getDependencyClasses(definition));
                if (factory != null) {
                    factoryCache.put(name, factory);
                    resolvedDef.setResolvedFactory(factory);
                    path = ResolutionDiagnostic.ResolutionPath.JIT;
                    jitHits.add(1);
                } else {
                    // Factory is null (shouldn't happen, but be defensive)
                    path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
                    fallbackCount.add(1);
                    return createViaReflection(resolvedDef);
                }
            } catch (CompilationException e) {
                // Fallback to reflection
                path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
                fallbackCount.add(1);
                return createViaReflection(resolvedDef);
            }
        } else {
            // Native image mode: skip JIT, go directly to fallback
            path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
            fallbackCount.add(1);
            return createViaReflection(resolvedDef);
        }
        
        // Create instance using factory - use wired path when available (no Object[] allocation)
        T instance;
        if (resolvedDef.isCompileTime() && resolvedDef.isWired()) {
            // Wired compile-time factory: call get() directly without try/catch
            // Wiring was verified at startup, so get() is guaranteed safe
            instance = factory.get();
        } else if (resolvedDef.isCompileTime()) {
            // Compile-time but not wired (e.g., forward reference): fallback to create()
            Object[] deps = resolveDependencies(definition);
            instance = factory.create(deps);
        } else {
            // JIT or other factory: use traditional path
            Object[] deps = resolveDependencies(definition);
            instance = factory.create(deps);
        }
        
        // Record diagnostic if enabled
        if (diagnosticMode) {
            diagnostics.add(new ResolutionDiagnostic(name, definition.type(), path, compileTimeNs));
        }
        
        return instance;
    }
    
    /**
     * Overload for backward compatibility with existing call sites using BeanDefinition.
     */
    @SuppressWarnings("unchecked")
    private <T> T createBean(BeanDefinition<T> definition) {
        // Use registry's pre-computed ResolvedBeanDefinition for single lookup
        ResolvedBeanDefinition<T> resolvedDef = registry.getResolvedOrNull(definition.name());
        if (resolvedDef == null) {
            // Fallback to legacy path if not found (should not happen in normal operation)
            return createBean(getOrComputeResolvedDefinition(definition));
        }
        return createBean(resolvedDef);
    }

    /**
     * Gets or creates a CompiledFactory for the given bean definition.
     * Returns the factory directly to avoid redundant lookups in the calling code.
     * Returns null if factory creation fails and fallback is needed.
     */
    @SuppressWarnings("unchecked")
    private <T> CompiledFactory<T> getOrCreateFactory(BeanDefinition<T> definition) {
        String name = definition.name();
        
        // Check if running in GraalVM native image mode - disable JIT
        boolean nativeImage = IS_NATIVE_IMAGE;
        
        // Single lookup in unified factory cache
        CompiledFactory<T> factory = (CompiledFactory<T>) factoryCache.get(name);
        if (factory != null) {
            // Hot path: factory already cached
            if (metricsEnabled) {
                // Check if compile-time by looking up ResolvedBeanDefinition
                ResolvedBeanDefinition<?> resolvedDef = registry.getResolvedOrNull(name);
                if (resolvedDef != null && resolvedDef.isCompileTime()) {
                    compileTimeHits.add(1);
                } else {
                    jitHits.add(1);
                }
            }
            return factory;
        }
        
        if (!nativeImage) {
            // Try JIT compilation and cache the result
            try {
                factory = jitCompiler.compile(definition.type(), getDependencyClasses(definition));
                if (factory != null) {
                    factoryCache.put(name, factory);
                    if (metricsEnabled) {
                        jitHits.add(1);
                    }
                    return factory;
                }
            } catch (CompilationException e) {
                // Fall through to return null for fallback
            }
        }
        
        // Return null to signal fallback to reflection is needed
        return null;
    }

    /**
     * Gets or creates a ResolvedBeanDefinition wrapper with cached index and factory.
     * This method provides thread-safe lazy initialization of the resolved definition cache.
     * Note: Now delegates to registry.getResolvedOrNull() for single-lookup optimization.
     */
    @SuppressWarnings("unchecked")
    private <T> ResolvedBeanDefinition<T> getOrComputeResolvedDefinition(BeanDefinition<T> baseDefinition) {
        // Try to get pre-computed ResolvedBeanDefinition from registry first
        ResolvedBeanDefinition<T> resolved = registry.getResolvedOrNull(baseDefinition.name());
        if (resolved != null) {
            return resolved;
        }
        // Fallback: create new wrapper (should not happen in normal operation)
        return new ResolvedBeanDefinition<>(baseDefinition);
    }

    /**
     * Wraps a BeanDefinition into a ResolvedBeanDefinition with cached index and factory.
     * This is a helper method for populating the type-based cache in resolve(Class).
     * Uses registry's pre-computed ResolvedBeanDefinition when available.
     */
    @SuppressWarnings("unchecked")
    private <T> ResolvedBeanDefinition<T> wrapResolvedDefinition(BeanDefinition<T> baseDefinition) {
        // Try to get pre-computed ResolvedBeanDefinition from registry first
        ResolvedBeanDefinition<T> resolved = registry.getResolvedOrNull(baseDefinition.name());
        if (resolved != null) {
            return resolved;
        }
        // Fallback: create new wrapper (should not happen in normal operation)
        return new ResolvedBeanDefinition<>(baseDefinition);
    }

    /**
     * Optimized bean creation that receives the factory directly, avoiding map lookup by name.
     * Used in prototype resolution paths where the factory was already obtained.
     */
    @SuppressWarnings("unchecked")
    private <T> T createBeanWithFactory(ResolvedBeanDefinition<T> resolvedDef, CompiledFactory<T> factory) {
        String name = resolvedDef.name();
        
        // Use wired path when available (no Object[] allocation)
        T instance;
        if (resolvedDef.isCompileTime() && resolvedDef.isWired()) {
            // Wired compile-time factory: call get() directly without try/catch
            instance = factory.get();
        } else if (resolvedDef.isCompileTime()) {
            // Compile-time but not wired: fallback to create()
            Object[] deps = resolveDependencies(resolvedDef.getDefinition());
            instance = factory.create(deps);
        } else {
            // JIT or other factory: use traditional path
            Object[] deps = resolveDependencies(resolvedDef.getDefinition());
            instance = factory.create(deps);
        }
        
        // Record diagnostic if enabled (avoid work in hot path when disabled)
        if (diagnosticMode) {
            ResolutionDiagnostic.ResolutionPath path = resolvedDef.isCompileTime() 
                ? ResolutionDiagnostic.ResolutionPath.COMPILE_TIME 
                : ResolutionDiagnostic.ResolutionPath.JIT;
            diagnostics.add(new ResolutionDiagnostic(name, resolvedDef.type(), path, 0L));
        }
        
        return instance;
    }
    
    /**
     * Overload for backward compatibility with existing call sites using BeanDefinition.
     */
    @SuppressWarnings("unchecked")
    private <T> T createBeanWithFactory(BeanDefinition<T> definition, CompiledFactory<T> factory) {
        // Use registry's pre-computed ResolvedBeanDefinition for single lookup
        ResolvedBeanDefinition<T> resolvedDef = registry.getResolvedOrNull(definition.name());
        if (resolvedDef == null) {
            // Fallback to legacy path if not found (should not happen in normal operation)
            return createBeanWithFactory(getOrComputeResolvedDefinition(definition), factory);
        }
        return createBeanWithFactory(resolvedDef, factory);
    }

    /**
     * Fallback bean creation via reflection for when factory is unavailable.
     * Uses ResolvedBeanDefinition for consistency.
     */
    @SuppressWarnings("unchecked")
    private <T> T createViaReflection(ResolvedBeanDefinition<T> resolvedDef) {
        return createViaReflection(resolvedDef.getDefinition());
    }

    /**
     * Optimized bean creation via compiled factory for prototype beans.
     * Skips diagnostic and metrics overhead when disabled.
     */
    @SuppressWarnings("unchecked")
    private <T> T createPrototypeBeanWithFactory(ResolvedBeanDefinition<T> resolvedDef, CompiledFactory<T> factory) {
        String name = resolvedDef.name();
        
        // Fast path: use wired path when available (no Object[] allocation)
        T instance;
        if (resolvedDef.isCompileTime() && resolvedDef.isWired()) {
            // Wired compile-time factory: call get() directly without try/catch
            instance = factory.get();
        } else if (resolvedDef.isCompileTime()) {
            // Compile-time but not wired: fallback to create()
            Object[] deps = resolveDependencies(resolvedDef.getDefinition());
            instance = factory.create(deps);
        } else {
            // JIT or other factory: use traditional path
            Object[] deps = resolveDependencies(resolvedDef.getDefinition());
            instance = factory.create(deps);
        }
        
        // Record diagnostic if enabled (minimal overhead path)
        if (diagnosticMode) {
            ResolutionDiagnostic.ResolutionPath path = resolvedDef.isCompileTime() 
                ? ResolutionDiagnostic.ResolutionPath.COMPILE_TIME 
                : ResolutionDiagnostic.ResolutionPath.JIT;
            diagnostics.add(new ResolutionDiagnostic(name, resolvedDef.type(), path, 0L));
        }
        
        return instance;
    }

    private Class<?>[] getDependencyClasses(BeanDefinition<?> definition) {
        // Extract classes from dependencies by resolving each dependency name
        // and getting its type from the registry
        Object[] deps = definition.dependencies();
        Class<?>[] depClasses = new Class<?>[deps.length];
        
        for (int i = 0; i < deps.length; i++) {
            Object dep = deps[i];
            if (dep instanceof String depName) {
                // Resolve dependency name to get its type - use getDefinitionOrNull to avoid Optional allocation
                BeanDefinition<?> d = registry.getDefinitionOrNull(depName);
                if (d == null) {
                    throw new IllegalStateException(
                        "Unknown dependency '" + depName + "' in bean '" + definition.name() + 
                        "'. All dependencies must be registered before the dependent bean."
                    );
                }
                depClasses[i] = d.type();
            } else if (dep != null) {
                // Direct object reference - derive type from constructor parameter signature, not runtime class
                // This ensures the bytecode descriptor matches the actual constructor signature when interface/superType is used
                depClasses[i] = findConstructorParameterType(definition.type(), i, dep.getClass());
            } else {
                throw new IllegalStateException(
                    "Null dependency at index " + i + " in bean '" + definition.name() + "'"
                );
            }
        }
        
        return depClasses;
    }

    /**
     * Find the actual parameter type from the constructor signature.
     * Falls back to runtime class if constructor cannot be found.
     */
    private Class<?> findConstructorParameterType(Class<?> beanType, int paramIndex, Class<?> runtimeClass) {
        try {
            // Try to find a constructor that accepts this parameter at the given index
            for (var ctor : beanType.getDeclaredConstructors()) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                if (paramIndex < paramTypes.length) {
                    // Check if runtime class is assignable to this parameter type
                    if (paramTypes[paramIndex].isAssignableFrom(runtimeClass)) {
                        return paramTypes[paramIndex];
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to default behavior
        }
        // Fallback: use runtime class
        return runtimeClass;
    }

    private Object[] resolveDependencies(BeanDefinition<?> definition) {
        // Cache dependencies() accessor to avoid multiple invocations (record/class accessor call is not free)
        Object[] dependencies = definition.dependencies();
        
        // Return empty array constant if no dependencies to avoid allocation
        if (dependencies.length == 0) {
            return EMPTY_ARGS;
        }
        
        Object[] deps = new Object[dependencies.length];
        int[] depIndices = definition.dependencyIndices();
        
        for (int i = 0; i < dependencies.length; i++) {
            Object dep = dependencies[i];
            if (dep instanceof String depName) {
                // Check if we have a cached index for this dependency
                int cachedIdx = depIndices[i];
                
                // Lazy resolution: if index is -1, try to resolve it now
                if (cachedIdx == -1) {
                    cachedIdx = registry.indexOf(depName);
                    depIndices[i] = cachedIdx;
                }
                
                // If we have a valid cached index and the singleton is already instantiated,
                // use fast indexed access (avoids String hashing and map lookup)
                if (cachedIdx >= 0) {
                    Object indexedInstance = registry.getIfPresent(cachedIdx);
                    if (indexedInstance != null) {
                        deps[i] = indexedInstance;
                        continue;
                    }
                }
                
                // Fallback: resolve by name (handles prototypes, not-yet-cached, or forward references)
                deps[i] = resolveByName(depName);
            } else {
                // Direct object reference (not a bean name)
                deps[i] = dep;
            }
        }
        return deps;
    }

    @SuppressWarnings("unchecked")
    private <T> T createViaReflection(BeanDefinition<T> definition) {
        // Fallback implementation for native image or when JIT is unavailable
        // Resolves dependencies and invokes the appropriate constructor
        try {
            Object[] args = resolveDependencies(definition);
            
            // Validate no null dependencies before proceeding
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    throw new IllegalStateException(
                        "Null dependency at index " + i + " in bean '" + definition.name() + "'"
                    );
                }
            }
            
            if (args.length == 0) {
                // No dependencies: use no-arg constructor
                return definition.type().getDeclaredConstructor().newInstance();
            } else {
                // Has dependencies: find constructor matching dependency types
                // Use declared parameter types from constructor signature, not runtime classes
                // This supports constructors with interface/superclass parameters
                java.lang.reflect.Constructor<T> constructor = findMatchingConstructor(definition.type(), args);
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                "Failed to create bean via reflection: " + definition.type().getName() + 
                ". No constructor found matching dependencies: " + Arrays.toString(
                    Arrays.stream(definition.dependencies())
                        .map(d -> d instanceof String ? d : ((Object)d).getClass().getName())
                        .toArray()
                ), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create bean via reflection: " + definition.type().getName(), e);
        }
    }
    
    /**
     * Find a constructor whose parameter types are compatible with the provided arguments.
     * Matches constructors where each parameter type is assignable from the corresponding argument's runtime class.
     */
    private <T> java.lang.reflect.Constructor<T> findMatchingConstructor(Class<T> beanType, Object[] args) 
            throws NoSuchMethodException {
        int argCount = args.length;
        
        // Iterate over all declared constructors to find a compatible one
        for (java.lang.reflect.Constructor<?> ctor : beanType.getDeclaredConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            
            // Check if parameter count matches
            if (paramTypes.length != argCount) {
                continue;
            }
            
            // Check if each argument is assignable to the corresponding parameter type
            boolean compatible = true;
            for (int i = 0; i < argCount; i++) {
                if (args[i] == null) {
                    // Cannot determine compatibility for null; skip this constructor
                    // (nulls should have been caught earlier, but be defensive)
                    compatible = false;
                    break;
                }
                if (!paramTypes[i].isAssignableFrom(args[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            
            if (compatible) {
                // Found a compatible constructor
                @SuppressWarnings("unchecked")
                java.lang.reflect.Constructor<T> typedCtor = (java.lang.reflect.Constructor<T>) ctor;
                return typedCtor;
            }
        }
        
        // No compatible constructor found
        throw new NoSuchMethodException(
            "No constructor in " + beanType.getName() + 
            " compatible with argument types: " + 
            Arrays.toString(Arrays.stream(args)
                .map(arg -> arg.getClass().getName())
                .toArray())
        );
    }

    private <T> void triggerBackgroundWarmup(BeanDefinition<T> definition) {
        // Lazy initialization of warmup executor on first use
        ExecutorService executor = warmupExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = warmupExecutor;
                if (executor == null) {
                    warmupExecutor = executor = Executors.newFixedThreadPool(
                        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                        r -> {
                            Thread t = new Thread(r, "warmup-compiler");
                            t.setDaemon(true);
                            return t;
                        }
                    );
                }
            }
        }
        
        // Check if all dependencies are registered before attempting warmup
        for (Object dep : definition.dependencies()) {
            if (dep instanceof String depName && !registry.contains(depName)) {
                // Skip warmup if dependency is not yet registered - will be retried later
                return;
            }
        }
        
        // Try to acquire a permit for background compilation
        if (!warmupSemaphore.tryAcquire()) {
            // Backpressure: skip warmup if too many pending compilations
            return;
        }
        
        try {
            CompletableFuture<CompiledFactory<T>> future = jitCompiler.compileAsync(definition.type(), getDependencyClasses(definition));
            // Release semaphore when compilation completes (success or failure)
            future.whenComplete((r, e) -> warmupSemaphore.release());
        } catch (Exception e) {
            // If compileAsync or getDependencyClasses throws synchronously, release the semaphore immediately
            // to prevent permanent loss of warmup capacity
            warmupSemaphore.release();
            // Log at debug level - this is expected during rapid bean registration
            if (System.getProperty("warmup.debug") != null) {
                System.err.println("[Warmup] Background warmup failed for " + definition.name() + ": " + e.getMessage());
            }
        }
    }

    private void recordMetrics(BeanDefinition<?> definition, long resolutionTimeNs) {
        totalResolutions.add(1);
        resolutionTimeAccumulator.add(resolutionTimeNs);
    }
}
