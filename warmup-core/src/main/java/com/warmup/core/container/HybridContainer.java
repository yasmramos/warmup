package com.warmup.core.container;

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
public class HybridContainer {

    private final BeanRegistry registry = new BeanRegistryImpl();
    private final DependencyGraph dependencyGraph = new DependencyGraph();
    private final JITCompiler jitCompiler;
    
    // Compile-time factories (injected from annotation processor)
    private final Map<String, CompiledFactory<?>> compileTimeFactories = new ConcurrentHashMap<>();
    
    // JIT factory cache to avoid recompiling the same bean type
    private final Map<String, CompiledFactory<?>> jitFactoryCache = new ConcurrentHashMap<>();
    
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
            compileTimeFactories.put(definition.name(), factory);
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
        
        long startTime = System.nanoTime();
        T instance = registry.getInstance(definition, () -> createBean(definition));
        
        if (metricsEnabled) {
            recordMetrics(definition, System.nanoTime() - startTime);
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
     * Registers a compile-time factory for a bean.
     * Called by generated code from annotation processor.
     */
    public void registerFactory(String beanName, CompiledFactory<?> factory) {
        compileTimeFactories.put(beanName, factory);
    }

    // Internal methods

    @SuppressWarnings("unchecked")
    private <T> T createBean(BeanDefinition<T> definition) {
        String name = definition.name();
        long compileTimeNs = 0;
        ResolutionDiagnostic.ResolutionPath path;
        
        // Check if running in GraalVM native image mode - disable JIT
        boolean nativeImage = IS_NATIVE_IMAGE;
        
        // Try compile-time factory first (zero-overhead path)
        CompiledFactory<T> factory = (CompiledFactory<T>) compileTimeFactories.get(name);
        if (factory != null) {
            path = ResolutionDiagnostic.ResolutionPath.COMPILE_TIME;
            compileTimeHits.add(1);
        } else if (nativeImage) {
            // In native image, skip JIT and go directly to fallback
            path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
            fallbackCount.add(1);
            return createViaReflection(definition);
        } else {
            // Check JIT factory cache first
            factory = (CompiledFactory<T>) jitFactoryCache.get(name);
            if (factory != null) {
                path = ResolutionDiagnostic.ResolutionPath.JIT;
                jitHits.add(1);
            } else {
                // Try JIT compilation
                try {
                    factory = jitCompiler.compile(definition.type(), getDependencyClasses(definition));
                    // Cache the compiled factory for future resolutions
                    jitFactoryCache.put(name, factory);
                    path = ResolutionDiagnostic.ResolutionPath.JIT;
                    jitHits.add(1);
                } catch (CompilationException e) {
                    // Fallback (should not happen in production)
                    path = ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK;
                    fallbackCount.add(1);
                    return createViaReflection(definition);
                }
            }
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
        // Fallback implementation - should rarely be used
        try {
            return definition.type().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create bean via reflection: " + definition.type().getName(), e);
        }
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
