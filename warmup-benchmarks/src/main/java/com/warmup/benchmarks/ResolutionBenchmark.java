package com.warmup.benchmarks;

import com.warmup.core.Warmup;
import com.warmup.core.container.ContainerMetrics;
import com.warmup.core.container.HybridContainer;
import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks comparing Warmup JIT/ASM path performance.
 * 
 * <p>This benchmark uses {@code registerDynamic()} which triggers runtime JIT compilation
 * via ASM. This is NOT the compile-time path - for compile-time benchmarks, see
 * {@link WarmupCompileTimeBenchmark}.</p>
 * 
 * <p>Scenarios measured:</p>
 * <ul>
 *   <li>{@code javaDirectInstantiation}: Baseline - pure Java instantiation (no container overhead)</li>
 *   <li>{@code warmupSingletonResolve}: Warmup JIT-compiled singleton resolution via public facade API</li>
 *   <li>{@code warmupPrototypeResolve}: Warmup prototype bean creation via public facade API</li>
 *   <li>{@code warmupPrototypeResolveWithOneDependency}: Prototype with 1 dependency (measures DI cost)</li>
 *   <li>{@code warmupPrototypeResolveWithFiveDependencies}: Prototype with 5 dependencies (measures scalability)</li>
 *   <li>{@code warmupSingletonResolveIndexed}: <strong>Internal API</strong> - integer-indexed resolution (not comparable with Avaje)</li>
 *   <li>{@code warmupCompiledFactoryCreate}: <strong>Internal API</strong> - direct factory invocation (not comparable with Avaje)</li>
 * </ul>
 * 
 * <p><strong>Important:</strong> Benchmarks using the public API ({@code warmupSingletonResolve},
 * {@code warmupPrototypeResolve*}) now use the {@link Warmup} facade to match how end-users
 * interact with the framework. Internal benchmarks ({@code warmupSingletonResolveIndexed},
 * {@code warmupCompiledFactoryCreate}) are documented as lower-bound measurements not
 * comparable with Avaje Inject.</p>
 * 
 * <h3>Comparison Notes:</h3>
 * <ul>
 *   <li>{@code warmupSingletonResolve} is directly comparable to {@code avajeSingletonResolve}</li>
 *   <li>{@code warmupPrototypeResolve*} have no Avaje equivalent (Avaje is singleton-first)</li>
 *   <li>{@code warmupSingletonResolveIndexed} and {@code warmupCompiledFactoryCreate} are internal APIs
 *       that provide lower-bound measurements for Warmup's fastest paths</li>
 *   <li>{@code javaDirectInstantiation} is a pure JVM baseline, not a Warmup metric</li>
 * </ul>
 * 
 * <h3>Path Comparison:</h3>
 * <ul>
 *   <li>{@code ResolutionBenchmark}: JIT/ASM path (dynamic registration via public facade)</li>
 *   <li>{@link WarmupCompileTimeBenchmark}: Pure compile-time path (@Bean annotation processing)</li>
 *   <li>{@link AvajeInjectBenchmark}: Avaje's compile-time path (for comparison)</li>
 * </ul>
 * 
 * <h3>Asymmetry Note:</h3>
 * <p>Only {@code warmupSingletonResolve} is directly comparable 1:1 with Avaje benchmarks.
 * The following are Warmup-specific metrics with no direct Avaje equivalent:</p>
 * <ul>
 *   <li>{@code warmupPrototypeResolve*}: Avaje does not expose prototype scope</li>
 *   <li>{@code warmupCompiledFactoryCreate}: Avaje does not expose compiled factories</li>
 *   <li>{@code javaDirectInstantiation}: Pure JVM baseline</li>
 *   <li>{@code warmupSingletonResolveIndexed}: Internal experimental API</li>
 * </ul>
 * 
 * <h3>Indexed Resolution:</h3>
 * <p>The {@code warmupSingletonResolveIndexed} benchmark uses an experimental integer-indexed
 * resolution path that bypasses String hashing and Map lookup. It provides a lower-bound
 * measurement for singleton resolution overhead in Warmup.</p>
 * 
 * Metrics:
 * - Resolution time (single bean resolution)
 * - Throughput (resolutions per second)
 * 
 * Note: Public API benchmarks use {@code Warmup.create()} and {@code resolve()}.
 * Internal benchmarks access {@code HybridContainer} directly for maximum performance measurement.
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ResolutionBenchmark {

    // Test beans with varying complexity

    public static class SimpleBean {
        public SimpleBean() {}
    }

    public static class BeanWithOneDependency {
        private final SimpleBean dependency;
        public BeanWithOneDependency(SimpleBean dependency) {
            this.dependency = dependency;
        }
    }

    public static class BeanWithFiveDependencies {
        private final SimpleBean d1, d2, d3, d4, d5;
        public BeanWithFiveDependencies(SimpleBean d1, SimpleBean d2, SimpleBean d3, SimpleBean d4, SimpleBean d5) {
            this.d1 = d1; this.d2 = d2; this.d3 = d3; this.d4 = d4; this.d5 = d5;
        }
    }

    // Benchmark state
    private Warmup warmup;
    private HybridContainer internalContainer;  // For internal-only benchmarks
    private CompiledFactory<SimpleBean> simpleFactory;
    private CompiledFactory<PrototypeBean> prototypeFactory;
    private CompiledFactory<PrototypeBeanWithOneDependency> prototypeWithOneDepFactory;
    private CompiledFactory<PrototypeBeanWithFiveDependencies> prototypeWithFiveDepsFactory;
    private int simpleBeanIndex;

    public static class PrototypeBean {
        public PrototypeBean() {}
    }
    
    public static class PrototypeBeanWithOneDependency {
        private final PrototypeBean dependency;
        public PrototypeBeanWithOneDependency(PrototypeBean dependency) {
            this.dependency = dependency;
        }
    }
    
    public static class PrototypeBeanWithFiveDependencies {
        private final PrototypeBean d1, d2, d3, d4, d5;
        public PrototypeBeanWithFiveDependencies(
                PrototypeBean d1, PrototypeBean d2, PrototypeBean d3,
                PrototypeBean d4, PrototypeBean d5) {
            this.d1 = d1; this.d2 = d2; this.d3 = d3; this.d4 = d4; this.d5 = d5;
        }
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // Create Warmup instance using public facade API for comparable benchmarks
        // Note: metricsEnabled=false to measure bare fast-path overhead without instrumentation
        com.warmup.asm.AsmJITCompiler jitCompiler = new com.warmup.asm.AsmJITCompiler();
        warmup = Warmup.builder()
            .jitCompiler(jitCompiler)
            .diagnostic(false)
            .maxPendingCompilations(10)
            .autoDiscoverFactories(false)  // Manual registration for this benchmark
            .metrics(false)
            .build();
        
        // Get internal container for indexed resolution benchmark (internal API only)
        internalContainer = warmup.unsafeContainer();

        // Register simple bean with JIT compilation (SINGLETON) via public API
        BeanDefinition<SimpleBean> singletonDef = new BeanDefinition<>(
            SimpleBean.class, "simpleBean", Scope.SINGLETON
        );
        warmup.registerDynamic(singletonDef);
        
        // Get the index for indexed resolution benchmark (internal API)
        simpleBeanIndex = internalContainer.indexOf("simpleBean");

        // Register prototype bean to measure creation overhead via public API
        BeanDefinition<PrototypeBean> prototypeDef = new BeanDefinition<>(
            PrototypeBean.class, "prototypeBean", Scope.PROTOTYPE
        );
        warmup.registerDynamic(prototypeDef);
        
        // Register prototype with one dependency
        // Note: PrototypeBean dependency must be registered first (already done above)
        BeanDefinition<PrototypeBeanWithOneDependency> prototypeOneDepDef = new BeanDefinition<>(
            PrototypeBeanWithOneDependency.class, "prototypeBeanWithOneDep", Scope.PROTOTYPE,
            com.warmup.core.lifecycle.LifecycleCallbacks.empty(), false,
            new Object[]{"prototypeBean"}
        );
        warmup.registerDynamic(prototypeOneDepDef);
        
        // Register prototype with five dependencies
        // Note: All PrototypeBean dependencies must be registered first (already done above)
        BeanDefinition<PrototypeBeanWithFiveDependencies> prototypeFiveDepsDef = new BeanDefinition<>(
            PrototypeBeanWithFiveDependencies.class, "prototypeBeanWithFiveDeps", Scope.PROTOTYPE,
            com.warmup.core.lifecycle.LifecycleCallbacks.empty(), false,
            new Object[]{"prototypeBean", "prototypeBean", "prototypeBean", "prototypeBean", "prototypeBean"}
        );
        warmup.registerDynamic(prototypeFiveDepsDef);

        // Pre-compile factories for warmup
        simpleFactory = jitCompiler.compile(SimpleBean.class);
        prototypeFactory = jitCompiler.compile(PrototypeBean.class);
        prototypeWithOneDepFactory = jitCompiler.compile(PrototypeBeanWithOneDependency.class);
        prototypeWithFiveDepsFactory = jitCompiler.compile(PrototypeBeanWithFiveDependencies.class);

        // Pre-resolve singleton to ensure it's cached (fast-path test)
        warmup.resolve(SimpleBean.class);
        // Pre-resolve PrototypeBean to ensure it's available for dependent beans
        warmup.resolve(PrototypeBean.class);
    }

    @Benchmark
    public Object javaDirectInstantiation() {
        return new SimpleBean();
    }

    /**
     * Measures resolve() overhead for a SINGLETON bean that is already cached.
     * This tests the fast-path lookup using the public Warmup facade API.
     * Directly comparable to AvajeInjectBenchmark.avajeSingletonResolve().
     */
    @Benchmark
    public Object warmupSingletonResolve() {
        return warmup.resolve(SimpleBean.class);
    }

    /**
     * Measures resolveByIndex() overhead for a SINGLETON bean that is already cached.
     * This tests the experimental integer-indexed fast path (avoids String hashing).
     * 
     * <p><strong>INTERNAL API BENCHMARK:</strong> This measures an experimental feature
     * not exposed in the public API. Results are lower-bound measurements and NOT
     * directly comparable with Avaje Inject benchmarks.</p>
     */
    @Benchmark
    public Object warmupSingletonResolveIndexed() {
        Object result = internalContainer.resolveByIndex(simpleBeanIndex);
        // Fallback to name-based resolve if not yet cached (shouldn't happen after warmup)
        return result != null ? result : warmup.resolve(SimpleBean.class);
    }

    /**
     * Measures resolve() for a PROTOTYPE bean, including full creation cost.
     * This measures the actual bean creation overhead via JIT factory using public API.
     */
    @Benchmark
    public Object warmupPrototypeResolve() {
        return warmup.resolve(PrototypeBean.class);
    }
    
    /**
     * Measures resolve() for a PROTOTYPE bean with one dependency.
     * This measures the actual bean creation overhead with dependency injection.
     * The cost should scale with the number of dependencies.
     */
    @Benchmark
    public Object warmupPrototypeResolveWithOneDependency() {
        return warmup.resolve(PrototypeBeanWithOneDependency.class);
    }
    
    /**
     * Measures resolve() for a PROTOTYPE bean with five dependencies.
     * This measures the actual bean creation overhead with multiple dependencies.
     * The cost should scale with the number of dependencies.
     */
    @Benchmark
    public Object warmupPrototypeResolveWithFiveDependencies() {
        return warmup.resolve(PrototypeBeanWithFiveDependencies.class);
    }

    /**
     * Measures direct compiled factory creation without container overhead.
     * Baseline for JIT path.
     * 
     * <p><strong>INTERNAL API BENCHMARK:</strong> This measures direct factory invocation,
     * which is not exposed in Avaje Inject's public API. Results are lower-bound
     * measurements and NOT directly comparable with Avaje benchmarks.</p>
     */
    @Benchmark
    public Object warmupCompiledFactoryCreate() {
        return simpleFactory.create();
    }

    /**
     * TearDown method to print container metrics after each benchmark run.
     * Shows cache hit/miss rates and resolution counts to validate O(1) resolution claim.
     */
    @TearDown(Level.Iteration)
    public void tearDown() {
        if (internalContainer != null) {
            ContainerMetrics metrics = internalContainer.getMetrics();
            System.out.println("\n=== Container Metrics ===");
            System.out.println("Total Resolutions: " + metrics.totalResolutions());
            System.out.println("Compile-time Hits: " + metrics.compileTimeHits());
            System.out.println("JIT Hits: " + metrics.jitHits());
            System.out.println("Fallback Count: " + metrics.fallbackCount());
            System.out.printf("Hit Rate: %.2f%%%n", metrics.cacheHitRate());
            System.out.println("=========================\n");
        }
    }
}
