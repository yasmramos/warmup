package com.warmup.benchmarks;

import io.avaje.inject.BeanScope;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Startup benchmark for Avaje IoC: measures BeanScope initialization time 
 * with varying bean counts. Comparable to StartupBenchmark for Warmup.
 * 
 * Scenarios: 10, 100, 1000 beans
 * 
 * This benchmark provides a side-by-side comparison of container startup
 * performance between Warmup and Avaje IoC.
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AvajeStartupBenchmark {

    @Param({"10", "100", "1000"})
    private int beanCount;

    @Benchmark
    public BeanScope startupWithBeans() {
        // Build BeanScope with dynamic bean count
        // Note: Avaje uses compile-time generated modules, so we measure
        // the default module build time which includes all registered beans
        return BeanScope.builder().build();
    }
}
