package com.warmup.benchmarks;

import com.warmup.asm.AsmJITCompiler;
import com.warmup.benchmarks.warmup.WarmupBeanWithFiveDependencies;
import com.warmup.benchmarks.warmup.WarmupBeanWithOneDependency;
import com.warmup.benchmarks.warmup.WarmupSimpleBean;
import com.warmup.core.container.ContainerMetrics;
import com.warmup.core.container.HybridContainer;
import com.warmup.core.container.HybridContainerConfig;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks measuring Warmup's pure compile-time path (COMPILE_TIME).
 * 
 * <p>This benchmark provides a fair comparison against Avaje Inject, which uses
 * its pure compile-time generated factory path. Both frameworks use annotation
 * processing to generate zero-overhead factories at compile time.</p>
 * 
 * <p>Key differences from {@link ResolutionBenchmark}:</p>
 * <ul>
 *   <li>Uses beans annotated with {@code @Bean} instead of dynamic registration</li>
 *   <li>Relies on auto-discovered {@code GeneratedFactoryRegistrar} via ServiceLoader</li>
 *   <li>Does NOT use {@code registerDynamic()}, which triggers JIT/ASM compilation</li>
 *   <li>Verifies via metrics that resolutions hit the COMPILE_TIME path (compileTimeHits)</li>
 * </ul>
 * 
 * <h3>Comparison Notes:</h3>
 * <ul>
 *   <li>Both Warmup and Avaje use compile-time generated factories for optimal performance</li>
 *   <li>Resolution times are measured in nanoseconds (lower is better)</li>
 *   <li>Use {@code ContainerMetrics} in tearDown to verify compileTimeHits > 0 and jitHits = 0</li>
 * </ul>
 * 
 * @see AvajeInjectBenchmark for Avaje's compile-time benchmark
 * @see ResolutionBenchmark for Warmup's JIT path benchmark
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class WarmupCompileTimeBenchmark {

    private HybridContainer container;

    @Setup(Level.Trial)
    public void setup() {
        // Create container with autoDiscoverFactories=true to enable ServiceLoader discovery
        // of GeneratedFactoryRegistrar, which registers all compile-time factories.
        // metricsEnabled=true to verify we're hitting the COMPILE_TIME path.
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
        HybridContainerConfig config = new HybridContainerConfig(
            false,  // diagnosticMode
            10,     // maxPendingCompilations
            true,   // autoDiscoverFactories - enables compile-time factory discovery
            true    // metricsEnabled - verify compileTimeHits vs jitHits
        );
        container = new HybridContainer(config, jitCompiler);

        // Note: We do NOT call registerDynamic() here, as that would trigger JIT compilation.
        // The compile-time factories are already registered via auto-discovery from
        // META-INF/services/com.warmup.core.jit.FactoryRegistrar.
        
        // Pre-resolve singletons to ensure they're cached in the registry
        container.resolve("WarmupSimpleBean");
        container.resolve("WarmupBeanWithOneDependency");
        container.resolve("WarmupBeanWithFiveDependencies");
    }

    /**
     * Measures Warmup compile-time singleton resolution performance.
     * Equivalent to AvajeInjectBenchmark.avajeSingletonResolve().
     * Verifies this hits the COMPILE_TIME path (not JIT).
     */
    @Benchmark
    public WarmupSimpleBean warmupCompileTimeSingletonResolve() {
        return container.resolve("WarmupSimpleBean");
    }

    /**
     * Measures Warmup compile-time bean resolution with one dependency.
     * Tests the cost of resolving a bean with one compile-time injected dependency.
     * Equivalent to AvajeInjectBenchmark.avajeBeanWithOneDependency().
     */
    @Benchmark
    public WarmupBeanWithOneDependency warmupCompileTimeBeanWithOneDependency() {
        return container.resolve("WarmupBeanWithOneDependency");
    }

    /**
     * Measures Warmup compile-time bean resolution with five dependencies.
     * Tests the scalability of dependency injection with multiple compile-time dependencies.
     * Equivalent to AvajeInjectBenchmark.avajeBeanWithFiveDependencies().
     */
    @Benchmark
    public WarmupBeanWithFiveDependencies warmupCompileTimeBeanWithFiveDependencies() {
        return container.resolve("WarmupBeanWithFiveDependencies");
    }

    /**
     * TearDown method to print container metrics after each benchmark run.
     * Verifies that resolutions are hitting the COMPILE_TIME path (compileTimeHits)
     * and NOT the JIT path (jitHits should be 0 or very low).
     */
    @TearDown(Level.Iteration)
    public void tearDown() {
        if (container != null) {
            ContainerMetrics metrics = container.getMetrics();
            System.out.println("\n=== Warmup Compile-Time Path Metrics ===");
            System.out.println("Total Resolutions: " + metrics.totalResolutions());
            System.out.println("Compile-Time Hits: " + metrics.compileTimeHits());
            System.out.println("JIT Hits: " + metrics.jitHits());
            System.out.println("Fallback Count: " + metrics.fallbackCount());
            System.out.printf("Hit Rate: %.2f%%%n", metrics.cacheHitRate());
            
            // Verify we're actually using the compile-time path
            if (metrics.compileTimeHits() == 0 && metrics.totalResolutions() > 0) {
                System.err.println("WARNING: No compile-time hits detected! Check that @Bean classes are processed.");
            }
            if (metrics.jitHits() > 0) {
                System.err.println("WARNING: JIT hits detected in compile-time benchmark. This may indicate misconfiguration.");
            }
            System.out.println("==========================================\n");
        }
    }
}
