package com.warmup.benchmarks;

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
 *   <li>{@code warmupSingletonResolve}: Warmup JIT-compiled singleton resolution (cached lookup by name)</li>
 *   <li>{@code warmupSingletonResolveIndexed}: Warmup singleton resolution by integer index (experimental fast path)</li>
 *   <li>{@code warmupPrototypeResolve}: Warmup prototype bean creation via JIT factory</li>
 *   <li>{@code warmupCompiledFactoryCreate}: Direct compiled factory invocation (Warmup-specific)</li>
 * </ul>
 * 
 * <p>Important: This benchmark measures the JIT path, which is slower than the pure
 * compile-time path. For fair comparison with Avaje Inject's compile-time factories,
 * use {@link WarmupCompileTimeBenchmark} instead.</p>
 * 
 * <h3>Path Comparison:</h3>
 * <ul>
 *   <li>{@code ResolutionBenchmark}: JIT/ASM path (dynamic registration)</li>
 *   <li>{@link WarmupCompileTimeBenchmark}: Pure compile-time path (@Bean annotation processing)</li>
 *   <li>{@link AvajeInjectBenchmark}: Avaje's compile-time path (for comparison)</li>
 * </ul>
 * 
 * <h3>Asymmetry Note:</h3>
 * <p>Only {@code warmupSingletonResolve}, {@code warmupSingletonResolveIndexed}, and beans with 
 * dependencies (not shown here, see {@link WarmupCompileTimeBenchmark}) are directly comparable 1:1 
 * with Avaje benchmarks. The following are Warmup-specific metrics with no direct Avaje equivalent:</p>
 * <ul>
 *   <li>{@code warmupPrototypeResolve}: Avaje is singleton-first and does not expose prototype scope</li>
 *   <li>{@code warmupCompiledFactoryCreate}: Avaje does not expose compiled factories for direct invocation</li>
 *   <li>{@code javaDirectInstantiation}: Pure JVM baseline, not a Warmup metric</li>
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
 * Note: This benchmark uses explicit HybridContainer construction for precise
 * performance measurement. For production usage, prefer Warmup.create().
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
    private HybridContainer container;
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
        // Using explicit constructor for benchmark measurement
        // In production, use: com.warmup.core.Warmup warmup = com.warmup.core.Warmup.create();
        // Note: metricsEnabled=false to measure bare fast-path overhead without instrumentation
        com.warmup.asm.AsmJITCompiler jitCompiler = new com.warmup.asm.AsmJITCompiler();
        container = new HybridContainer(jitCompiler, false, 10, true, false);

        // Register simple bean with JIT compilation (SINGLETON)
        BeanDefinition<SimpleBean> singletonDef = new BeanDefinition<>(
            SimpleBean.class, "simpleBean", Scope.SINGLETON
        );
        container.registerDynamic(singletonDef);
        
        // Get the index for indexed resolution benchmark
        simpleBeanIndex = container.indexOf("simpleBean");

        // Register prototype bean to measure creation overhead
        BeanDefinition<PrototypeBean> prototypeDef = new BeanDefinition<>(
            PrototypeBean.class, "prototypeBean", Scope.PROTOTYPE
        );
        container.registerDynamic(prototypeDef);
        
        // Register prototype with one dependency
        BeanDefinition<PrototypeBeanWithOneDependency> prototypeOneDepDef = new BeanDefinition<>(
            PrototypeBeanWithOneDependency.class, "prototypeBeanWithOneDep", Scope.PROTOTYPE
        );
        container.registerDynamic(prototypeOneDepDef);
        
        // Register prototype with five dependencies
        BeanDefinition<PrototypeBeanWithFiveDependencies> prototypeFiveDepsDef = new BeanDefinition<>(
            PrototypeBeanWithFiveDependencies.class, "prototypeBeanWithFiveDeps", Scope.PROTOTYPE
        );
        container.registerDynamic(prototypeFiveDepsDef);

        // Pre-compile factories for warmup
        simpleFactory = jitCompiler.compile(SimpleBean.class);
        prototypeFactory = jitCompiler.compile(PrototypeBean.class);
        prototypeWithOneDepFactory = jitCompiler.compile(PrototypeBeanWithOneDependency.class);
        prototypeWithFiveDepsFactory = jitCompiler.compile(PrototypeBeanWithFiveDependencies.class);

        // Pre-resolve singleton to ensure it's cached (fast-path test)
        container.resolve(SimpleBean.class);
    }

    @Benchmark
    public Object javaDirectInstantiation() {
        return new SimpleBean();
    }

    /**
     * Measures resolve() overhead for a SINGLETON bean that is already cached.
     * This tests the fast-path lookup without bean creation.
     */
    @Benchmark
    public Object warmupSingletonResolve() {
        return container.resolve(SimpleBean.class);
    }

    /**
     * Measures resolveByIndex() overhead for a SINGLETON bean that is already cached.
     * This tests the experimental integer-indexed fast path (avoids String hashing).
     */
    @Benchmark
    public Object warmupSingletonResolveIndexed() {
        Object result = container.resolveByIndex(simpleBeanIndex);
        // Fallback to name-based resolve if not yet cached (shouldn't happen after warmup)
        return result != null ? result : container.resolve(SimpleBean.class);
    }

    /**
     * Measures resolve() for a PROTOTYPE bean, including full creation cost.
     * This measures the actual bean creation overhead via JIT factory.
     */
    @Benchmark
    public Object warmupPrototypeResolve() {
        return container.resolve(PrototypeBean.class);
    }
    
    /**
     * Measures resolve() for a PROTOTYPE bean with one dependency.
     * This measures the actual bean creation overhead with dependency injection.
     * The cost should scale with the number of dependencies.
     */
    @Benchmark
    public Object warmupPrototypeResolveWithOneDependency() {
        return container.resolve(PrototypeBeanWithOneDependency.class);
    }
    
    /**
     * Measures resolve() for a PROTOTYPE bean with five dependencies.
     * This measures the actual bean creation overhead with multiple dependencies.
     * The cost should scale with the number of dependencies.
     */
    @Benchmark
    public Object warmupPrototypeResolveWithFiveDependencies() {
        return container.resolve(PrototypeBeanWithFiveDependencies.class);
    }

    /**
     * Measures direct compiled factory creation without container overhead.
     * Baseline for JIT path.
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
        if (container != null) {
            ContainerMetrics metrics = container.getMetrics();
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
