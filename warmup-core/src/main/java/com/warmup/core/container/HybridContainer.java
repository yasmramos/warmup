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
import com.warmup.core.scope.Scope;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
public class HybridContainer implements HotReloadCapable {

    private final BeanRegistry registry = new BeanRegistryImpl();
    private final DependencyGraph dependencyGraph = new DependencyGraph();
    private final JITCompiler jitCompiler;
    
    // Unified factory cache: combines both compile-time and JIT factories
    // Key: bean name, Value: factory with metadata about origin for metrics
    private final Map<String, CompiledFactory<?>> factoryCache = new ConcurrentHashMap<>();
    
    // Track which factories are from compile-time vs JIT for metrics
    private final Set<String> compileTimeFactoryNames = ConcurrentHashMap.newKeySet();
    
    // Empty array constant to avoid allocation for beans without dependencies
    private static final Object[] EMPTY_ARGS = new Object[0];
    
    // Diagnostic mode flag
    private final boolean diagnosticMode;
    private final List<ResolutionDiagnostic> diagnostics = new ArrayList<>();
    
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
     * Default is true for backward compatibility.
     */
    private final boolean metricsEnabled;
    
    // Background warmup executor with semaphore for backpressure
    private final ExecutorService warmupExecutor;
    private final Semaphore warmupSemaphore;
    
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
     * Metrics are enabled by default for backward compatibility.
     * 
     * @param jitCompiler the JIT compiler for runtime factory generation
     * @param diagnosticMode if true, logs resolution path for each bean
     */
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode) {
        this(jitCompiler, diagnosticMode, 10, true, true);
    }

    /**
     * Creates a new HybridContainer with custom warmup configuration.
     * Auto-discovers FactoryRegistrar implementations via ServiceLoader.
     * Metrics are enabled by default for backward compatibility.
     * 
     * @param jitCompiler the JIT compiler for runtime factory generation
     * @param diagnosticMode if true, logs resolution path for each bean
     * @param maxPendingCompilations maximum concurrent background compilations
     */
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode, int maxPendingCompilations) {
        this(jitCompiler, diagnosticMode, maxPendingCompilations, true, true);
    }

    /**
     * Creates a new HybridContainer with full configuration.
     * Metrics are enabled by default for backward compatibility.
     * 
     * @param jitCompiler the JIT compiler for runtime factory generation
     * @param diagnosticMode if true, logs resolution path for each bean
     * @param maxPendingCompilations maximum concurrent background compilations
     * @param autoDiscoverFactories if true, automatically discovers and registers
     *        compile-time factories via ServiceLoader at startup (default: true)
     */
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode, int maxPendingCompilations, boolean autoDiscoverFactories) {
        this(jitCompiler, diagnosticMode, maxPendingCompilations, autoDiscoverFactories, true);
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
     */
    public HybridContainer(JITCompiler jitCompiler, boolean diagnosticMode, int maxPendingCompilations, boolean autoDiscoverFactories, boolean metricsEnabled) {
        this.jitCompiler = jitCompiler;
        this.diagnosticMode = diagnosticMode;
        this.autoDiscoverFactories = autoDiscoverFactories;
        this.metricsEnabled = metricsEnabled;
        this.warmupSemaphore = new Semaphore(maxPendingCompilations);
        this.warmupExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "warmup-compiler");
                t.setDaemon(true);
                return t;
            }
        );
        
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
        for (FactoryRegistrar registrar : loader) {
            registrar.registerAll((name, factory) -> registerFactory(name, factory));
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
        
        // Register in dependency graph
        String[] deps = Arrays.stream(definition.dependencies())
            .map(Object::toString)
            .toArray(String[]::new);
        dependencyGraph.registerBean(definition.name(), deps);
    }

    /**
     * Registers a dynamic bean for JIT compilation.
     * Triggers background warmup compilation.
     * 
     * @param <T> the bean type
     * @param definition the bean definition
     */
    public <T> void registerDynamic(BeanDefinition<T> definition) {
        registry.register(definition);
        
        // Register in dependency graph
        String[] deps = Arrays.stream(definition.dependencies())
            .map(Object::toString)
            .toArray(String[]::new);
        dependencyGraph.registerBean(definition.name(), deps);
        
        // Trigger background warmup
        triggerBackgroundWarmup(definition);
    }

    /**
     * Resolves a bean by name.
     * Uses compile-time factory if available, otherwise JIT-compiles or falls back.
     * When metrics are disabled, the fast path (cached singleton) avoids all timing,
     * Optional allocations, and LongAdder updates for minimal overhead.
     * 
     * @param <T> the bean type
     * @param name the bean name
     * @return the resolved bean instance
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(String name) {
        // Fast-path: check if singleton is already cached
        T cachedInstance = registry.getIfPresent(name);
        if (cachedInstance != null) {
            // When metrics enabled: record timing and resolution count
            // When metrics disabled: bare return with no overhead
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                BeanDefinition<T> definition = (BeanDefinition<T>) registry.getDefinition(name).orElseThrow(() -> new IllegalStateException("Bean not found: " + name));
                recordMetrics(definition, System.nanoTime() - startTime);
            }
            return cachedInstance;
        }
        
        // Slow path: bean not yet cached, need to create it
        BeanDefinition<T> definition = (BeanDefinition<T>) registry.getDefinition(name).orElse(null);
        if (definition == null) {
            throw new IllegalStateException("Bean not found: " + name);
        }
        
        // Optimize prototype path: avoid lambda allocation by calling createBean directly
        // Only use Supplier for SINGLETON scope which needs thread-safe lazy init
        T instance;
        if (definition.scope() == Scope.PROTOTYPE) {
            // Direct path for PROTOTYPE: no lambda, no Supplier, no Optional allocation
            // Apply init callback inline if lifecycle exists
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                instance = createBean(definition);
                // Apply init callback for prototype if lifecycle callbacks exist
                if (definition.lifecycle().onInit() != null) {
                    definition.lifecycle().onInit().onInit(instance);
                }
                recordMetrics(definition, System.nanoTime() - startTime);
            } else {
                instance = createBean(definition);
                // Apply init callback for prototype if lifecycle callbacks exist
                if (definition.lifecycle().onInit() != null) {
                    definition.lifecycle().onInit().onInit(instance);
                }
            }
        } else {
            // SINGLETON and CUSTOM scopes use registry.getInstance with Supplier
            if (metricsEnabled) {
                long startTime = System.nanoTime();
                instance = registry.getInstance(definition, () -> createBean(definition));
                recordMetrics(definition, System.nanoTime() - startTime);
            } else {
                instance = registry.getInstance(definition, () -> createBean(definition));
            }
        }
        
        return instance;
    }

    /**
     * Resolves a bean by type.
     * 
     * @param <T> the bean type
     * @param type the bean class
     * @return the resolved bean instance
     */
    public <T> T resolve(Class<T> type) {
        BeanDefinition<T> definition = registry.getDefinitionByType(type)
            .orElseThrow(() -> new IllegalStateException("Bean not found for type: " + type.getName()));
        
        return resolve(definition.name());
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
     * Gets diagnostic information for the last resolution.
     */
    public List<ResolutionDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
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
     * Shuts down the container, applying destroy callbacks.
     */
    public void shutdown() {
        warmupExecutor.shutdown();
        registry.clear();
        jitCompiler.clear();
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
        
        // Step 2: Remove from unified factory cache and tracking set
        factoryCache.remove(name);
        compileTimeFactoryNames.remove(name);
        
        // Step 3: Unload the previous ASM factory to free ClassLoader and metaspace
        jitCompiler.unloadFactory(definition.type());
        
        // Step 4: Trigger background recompilation
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
        factoryCache.put(beanName, factory);
        compileTimeFactoryNames.add(beanName);
    }

    // Internal methods

    @SuppressWarnings("unchecked")
    private <T> T createBean(BeanDefinition<T> definition) {
        String name = definition.name();
        long compileTimeNs = 0;
        ResolutionDiagnostic.ResolutionPath path;
        
        // Check if running in GraalVM native image mode - disable JIT
        boolean nativeImage = IS_NATIVE_IMAGE;
        
        // Single lookup in unified factory cache for hot path
        CompiledFactory<T> factory = (CompiledFactory<T>) factoryCache.get(name);
        if (factory != null) {
            // Hot path: factory already cached, determine origin for metrics
            if (compileTimeFactoryNames.contains(name)) {
                path = ResolutionDiagnostic.ResolutionPath.COMPILE_TIME;
                compileTimeHits.add(1);
            } else {
                path = ResolutionDiagnostic.ResolutionPath.JIT;
                jitHits.add(1);
            }
        } else if (!nativeImage) {
            // Try JIT compilation and cache the result
            try {
                factory = jitCompiler.compile(definition.type(), getDependencyClasses(definition));
                if (factory != null) {
                    factoryCache.put(name, factory);
                    path = ResolutionDiagnostic.ResolutionPath.JIT;
                    jitHits.add(1);
                } else {
                    // Factory is null (shouldn't happen, but be defensive)
                    path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
                    fallbackCount.add(1);
                    return createViaReflection(definition);
                }
            } catch (CompilationException e) {
                // Fallback to reflection
                path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
                fallbackCount.add(1);
                return createViaReflection(definition);
            }
        } else {
            // Native image mode: skip JIT, go directly to fallback
            path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
            fallbackCount.add(1);
            return createViaReflection(definition);
        }
        
        // Create instance using factory
        Object[] deps = resolveDependencies(definition);
        T instance = factory.create(deps);
        
        // Record diagnostic if enabled
        if (diagnosticMode) {
            diagnostics.add(new ResolutionDiagnostic(name, definition.type(), path, compileTimeNs));
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
                // Resolve dependency name to get its type
                BeanDefinition<?> d = registry.getDefinition(depName).orElse(null);
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
        // Return empty array constant if no dependencies to avoid allocation
        if (definition.dependencies().length == 0) {
            return EMPTY_ARGS;
        }
        
        Object[] deps = new Object[definition.dependencies().length];
        for (int i = 0; i < definition.dependencies().length; i++) {
            Object dep = definition.dependencies()[i];
            if (dep instanceof String depName) {
                deps[i] = resolve(depName);
            } else {
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
