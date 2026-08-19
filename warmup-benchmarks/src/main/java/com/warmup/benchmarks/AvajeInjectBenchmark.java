package com.warmup.benchmarks;

import com.warmup.benchmarks.avaje.AvajeSimpleBean;
import com.warmup.benchmarks.avaje.AvajeBeanWithOneDependency;
import com.warmup.benchmarks.avaje.AvajeBeanWithFiveDependencies;
import io.avaje.inject.BeanScope;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks comparing Avaje Inject performance against Warmup.
 * 
 * <p>This benchmark measures Avaje Inject's compile-time generated factory performance,
 * providing a fair comparison with Warmup's compile-time path (COMPILE_TIME).</p>
 * 
 * <p>Avaje Inject version: 11.4 (latest stable as of implementation)</p>
 * 
 * <h3>Comparison Notes:</h3>
 * <ul>
 *   <li>Both frameworks use compile-time generated factories for optimal performance</li>
 *   <li>Avaje Inject is singleton-first; prototype scope comparison may differ</li>
 *   <li>Bean resolution times are measured in nanoseconds (lower is better)</li>
 * </ul>
 * 
 * <h3>Benchmarks:</h3>
 * <ul>
 *   <li>{@code avajeSingletonResolve}: Resolves a cached singleton bean</li>
 *   <li>{@code avajeBeanWithOneDependency}: Resolves a bean with one dependency</li>
 *   <li>{@code avajeBeanWithFiveDependencies}: Resolves a bean with five dependencies</li>
 * </ul>
 * 
 * @see ResolutionBenchmark for Warmup compile-time and JIT path benchmarks
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@org.openjdk.jmh.annotations.Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class AvajeInjectBenchmark {

    private BeanScope beanScope;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // Build the Avaje Inject container with generated factories
        // This is equivalent to Warmup's auto-discovery of compile-time factories
        beanScope = BeanScope.builder().build();
    }

    /**
     * Measures Avaje Inject singleton resolution performance.
     * Equivalent to Warmup's {@code singletonCachedResolve} benchmark.
     */
    @Benchmark
    public AvajeSimpleBean avajeSingletonResolve() {
        return beanScope.get(AvajeSimpleBean.class);
    }

    /**
     * Measures Avaje Inject bean resolution with one dependency.
     * Tests the cost of injecting a single dependency at resolution time.
     */
    @Benchmark
    public AvajeBeanWithOneDependency avajeBeanWithOneDependency() {
        return beanScope.get(AvajeBeanWithOneDependency.class);
    }

    /**
     * Measures Avaje Inject bean resolution with five dependencies.
     * Tests the scalability of dependency injection with multiple dependencies.
     */
    @Benchmark
    public AvajeBeanWithFiveDependencies avajeBeanWithFiveDependencies() {
        return beanScope.get(AvajeBeanWithFiveDependencies.class);
    }
}
