package com.warmup.benchmarks;

import com.warmup.asm.AsmJITCompiler;
import com.warmup.benchmarks.warmup.WarmupBeanWithFiveDependencies;
import com.warmup.benchmarks.warmup.WarmupBeanWithOneDependency;
import com.warmup.benchmarks.warmup.WarmupSimpleBean;
import com.warmup.core.Warmup;
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
 *   <li>Path verification is done in a separate container during trial setup to avoid
 *       instrumenting the measured hot path</li>
 * </ul>
 * 
 * <p><strong>Important:</strong> This benchmark uses the public {@link Warmup} facade API
 * to measure performance exactly as end-users would experience it, matching how
 * {@link AvajeInjectBenchmark} measures {@code io.avaje.inject.BeanScope}.</p>
 * 
 * @see AvajeInjectBenchmark for Avaje's compile-time benchmark
 * @see ResolutionBenchmark for Warmup's JIT path benchmark
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class WarmupCompileTimeBenchmark {

    private Warmup warmup;
    private HybridContainer verificationContainer;

    @Setup(Level.Trial)
    public void setup() {
        // Create Warmup instance using the public facade API with autoDiscoverFactories=true
        // to enable ServiceLoader discovery of GeneratedFactoryRegistrar, which registers
        // all compile-time factories.
        // CRITICAL: metricsEnabled=false to measure bare fast-path overhead without
        // System.nanoTime() instrumentation (~50ns per call) that would dominate and mask
        // the actual resolution cost, making comparison with Avaje unfair.
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
        warmup = Warmup.builder()
            .jitCompiler(jitCompiler)
            .diagnostic(false)
            .maxPendingCompilations(10)
            .autoDiscoverFactories(true)
            .metrics(false)  // Disabled for fair performance measurement
            .build();

        // Note: We do NOT call registerDynamic() here, as that would trigger JIT compilation.
        // The compile-time factories are already registered via auto-discovery from
        // META-INF/services/com.warmup.core.jit.FactoryRegistrar.
        
        // Pre-resolve singletons to ensure they're cached in the registry
        warmup.resolve(WarmupSimpleBean.class);
        warmup.resolve(WarmupBeanWithOneDependency.class);
        warmup.resolve(WarmupBeanWithFiveDependencies.class);
        
        // Separate verification container: verify COMPILE_TIME path is used without
        // contaminating the measured container's metrics. This container has
        // metricsEnabled=true solely to validate we're hitting the expected code path.
        verifyCompileTimePath(jitCompiler);
    }

    /**
     * Verifies that the compile-time path is actually being used.
     * Creates a separate container with metrics enabled, resolves each bean once,
     * and validates that compileTimeHits > 0 and jitHits == 0.
     * This verification does NOT affect the measured container's performance.
     */
    private void verifyCompileTimePath(AsmJITCompiler jitCompiler) {
        HybridContainerConfig verifyConfig = new HybridContainerConfig(
            false,  // diagnosticMode
            10,     // maxPendingCompilations
            true,   // autoDiscoverFactories
            true    // metricsEnabled=true ONLY for path verification
        );
        verificationContainer = new HybridContainer(verifyConfig, jitCompiler);
        
        // Resolve each bean type once to populate metrics
        verificationContainer.resolve(WarmupSimpleBean.class);
        verificationContainer.resolve(WarmupBeanWithOneDependency.class);
        verificationContainer.resolve(WarmupBeanWithFiveDependencies.class);
        
        ContainerMetrics metrics = verificationContainer.getMetrics();
        
        // Verify we're using the compile-time path
        if (metrics.compileTimeHits() == 0) {
            throw new IllegalStateException(
                "VERIFICATION FAILED: No compile-time hits detected! " +
                "Expected compileTimeHits > 0, got " + metrics.compileTimeHits() + ". " +
                "Check that bean classes are annotated with @Singleton/@Component/@Prototype and " +
                "processed by the Warmup annotation processor. Ensure the processor is declared as " +
                "annotationProcessor in the build configuration (Gradle/Maven) for this module."
            );
        }
        
        if (metrics.jitHits() > 0) {
            throw new IllegalStateException(
                "VERIFICATION FAILED: JIT hits detected in compile-time benchmark! " +
                "Expected jitHits = 0, got " + metrics.jitHits() + ". " +
                "This indicates misconfiguration or fallback to JIT path."
            );
        }
        
        System.out.println("\n=== Compile-Time Path Verification PASSED ===");
        System.out.println("Total Resolutions: " + metrics.totalResolutions());
        System.out.println("Compile-Time Hits: " + metrics.compileTimeHits());
        System.out.println("JIT Hits: " + metrics.jitHits());
        System.out.println("============================================\n");
    }

    /**
     * Measures Warmup compile-time singleton resolution performance.
     * Equivalent to AvajeInjectBenchmark.avajeSingletonResolve().
     * Verifies this hits the COMPILE_TIME path (not JIT).
     */
    @Benchmark
    public WarmupSimpleBean warmupCompileTimeSingletonResolve() {
        return warmup.get(WarmupSimpleBean.class);
    }

    /**
     * Measures Warmup compile-time bean resolution with one dependency.
     * Tests the cost of resolving a bean with one compile-time injected dependency.
     * Equivalent to AvajeInjectBenchmark.avajeBeanWithOneDependency().
     */
    @Benchmark
    public WarmupBeanWithOneDependency warmupCompileTimeBeanWithOneDependency() {
        return warmup.get(WarmupBeanWithOneDependency.class);
    }

    /**
     * Measures Warmup compile-time bean resolution with five dependencies.
     * Tests the scalability of dependency injection with multiple compile-time dependencies.
     * Equivalent to AvajeInjectBenchmark.avajeBeanWithFiveDependencies().
     */
    @Benchmark
    public WarmupBeanWithFiveDependencies warmupCompileTimeBeanWithFiveDependencies() {
        return warmup.get(WarmupBeanWithFiveDependencies.class);
    }

    /**
     * TearDown method called after each benchmark iteration.
     * Note: The measured container has metricsEnabled=false to avoid instrumentation
     * overhead (~50ns per resolve). Path verification is performed once during trial
     * setup using a separate verification container.
     */
    @TearDown(Level.Iteration)
    public void tearDown() {
        // Metrics are disabled on the measured container for fair performance comparison.
        // Path verification was already performed during @Setup(Level.Trial) using
        // a separate verification container with metricsEnabled=true.
        // See verifyCompileTimePath() for verification logic.
    }
}
