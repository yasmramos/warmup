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
 * JMH benchmarks comparing Warmup compile-time vs JIT paths.
 *
 * Scenarios:
 * - Direct instantiation (baseline)
 * - Warmup compile-time factory (zero-overhead path)
 * - Warmup JIT-compiled factory (runtime compilation)
 * - Warmup resolve() for singleton (cached lookup)
 * - Warmup resolve() for prototype (creation overhead)
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

    public static class PrototypeBean {
        public PrototypeBean() {}
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

        // Register prototype bean to measure creation overhead
        BeanDefinition<PrototypeBean> prototypeDef = new BeanDefinition<>(
            PrototypeBean.class, "prototypeBean", Scope.PROTOTYPE
        );
        container.registerDynamic(prototypeDef);

        // Pre-compile factories for warmup
        simpleFactory = jitCompiler.compile(SimpleBean.class);
        prototypeFactory = jitCompiler.compile(PrototypeBean.class);

        // Pre-resolve singleton to ensure it's cached (fast-path test)
        container.resolve("simpleBean");
    }

    @Benchmark
    public Object directInstantiation() {
        return new SimpleBean();
    }

    /**
     * Measures resolve() overhead for a SINGLETON bean that is already cached.
     * This tests the fast-path lookup without bean creation.
     */
    @Benchmark
    public Object singletonCachedResolve() {
        return container.resolve("simpleBean");
    }

    /**
     * Measures resolve() for a PROTOTYPE bean, including full creation cost.
     * This measures the actual bean creation overhead via JIT factory.
     */
    @Benchmark
    public Object prototypeResolve() {
        return container.resolve("prototypeBean");
    }

    /**
     * Measures direct compiled factory creation without container overhead.
     * Baseline for JIT path.
     */
    @Benchmark
    public Object compiledFactoryCreate() {
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
