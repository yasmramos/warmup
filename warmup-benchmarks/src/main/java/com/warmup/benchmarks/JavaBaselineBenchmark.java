package com.warmup.benchmarks;

import com.warmup.benchmarks.java.JavaSimpleBean;
import com.warmup.benchmarks.java.JavaBeanWithOneDependency;
import com.warmup.benchmarks.java.JavaBeanWithFiveDependencies;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for pure Java baseline (no DI framework).
 * 
 * <p>This benchmark provides a baseline comparison for Warmup and Avaje Inject,
 * measuring the raw performance of manual Java instantiation without any
 * dependency injection framework overhead.</p>
 * 
 * <h3>Comparison Notes:</h3>
 * <ul>
 *   <li>These benchmarks represent the theoretical minimum cost of bean creation</li>
 *   <li>Any DI framework will have some overhead compared to these baselines</li>
 *   <li>The difference between framework and baseline shows the "DI tax"</li>
 * </ul>
 * 
 * <h3>Benchmarks:</h3>
 * <ul>
 *   <li>{@code javaSingletonResolve}: Returns a cached singleton instance (equivalent to avajeSingletonResolve / warmupSingletonResolve)</li>
 *   <li>{@code javaBeanWithOneDependency}: Manually constructs new BeanWithOneDependency(dep)</li>
 *   <li>{@code javaBeanWithFiveDependencies}: Manually constructs new BeanWithFiveDependencies(d1..d5)</li>
 * </ul>
 * 
 * @see AvajeInjectBenchmark for Avaje Inject compile-time benchmarks
 * @see ResolutionBenchmark for Warmup compile-time and JIT path benchmarks
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class JavaBaselineBenchmark {

    private JavaSimpleBean singletonInstance;
    private JavaSimpleBean dep1, dep2, dep3, dep4, dep5;

    @Setup(Level.Trial)
    public void setup() {
        // Pre-create singleton instances for fair comparison with DI frameworks
        // This simulates the cached singleton that DI frameworks return
        singletonInstance = new JavaSimpleBean();
        
        // Pre-create dependencies for beans with multiple dependencies
        dep1 = new JavaSimpleBean();
        dep2 = new JavaSimpleBean();
        dep3 = new JavaSimpleBean();
        dep4 = new JavaSimpleBean();
        dep5 = new JavaSimpleBean();
    }

    /**
     * Measures pure Java singleton resolution performance.
     * Equivalent to avajeSingletonResolve and warmupSingletonResolve.
     * Simply returns a pre-cached instance (fast-path).
     */
    @Benchmark
    public JavaSimpleBean javaSingletonResolve() {
        return singletonInstance;
    }

    /**
     * Measures pure Java bean resolution with one dependency.
     * Tests the raw cost of manual construction with a single dependency.
     * Equivalent to avajeBeanWithOneDependency and warmupBeanWithOneDependency.
     */
    @Benchmark
    public JavaBeanWithOneDependency javaBeanWithOneDependency() {
        return new JavaBeanWithOneDependency(dep1);
    }

    /**
     * Measures pure Java bean resolution with five dependencies.
     * Tests the raw cost of manual construction with multiple dependencies.
     * Equivalent to avajeBeanWithFiveDependencies and warmupBeanWithFiveDependencies.
     */
    @Benchmark
    public JavaBeanWithFiveDependencies javaBeanWithFiveDependencies() {
        return new JavaBeanWithFiveDependencies(dep1, dep2, dep3, dep4, dep5);
    }
}
