package com.warmup.benchmarks;

import io.avaje.inject.BeanScope;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Startup benchmark for Avaje IoC: measures BeanScope initialization time.
 *
 * IMPORTANT: Avaje uses compile-time generated modules, so the number of beans
 * is fixed at compile time (currently 3 beans in AvajeModule). This benchmark
 * measures the time to build the default Avaje module, which includes all
 * registered beans defined via @Factory and @Bean annotations.
 *
 * Unlike Warmup's StartupBenchmark which dynamically registers N beans at runtime,
 * Avaje's bean count cannot be parametrized at runtime. Therefore, this benchmark
 * provides a single measurement point for Avaje's startup performance with its
 * default module configuration.
 *
 * For a fair comparison:
 * - Warmup: Measures dynamic bean registration + container initialization
 * - Avaje: Measures BeanScope.build() with pre-compiled module (compile-time beans)
 *
 * These are fundamentally different approaches and should not be compared as
 * "X is faster than Y" without understanding the architectural differences.
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AvajeStartupBenchmark {

    @Benchmark
    public BeanScope startupWithBeans() {
        // Build BeanScope with the default Avaje module.
        // The module contains 3 beans defined in AvajeModule:
        // - AvajeSimpleBean
        // - AvajeBeanWithOneDependency
        // - AvajeBeanWithFiveDependencies
        //
        // Note: Avaje uses compile-time annotation processing, so bean count
        // cannot be parametrized at runtime. This measures the fixed module build time.
        return BeanScope.builder().build();
    }
}
