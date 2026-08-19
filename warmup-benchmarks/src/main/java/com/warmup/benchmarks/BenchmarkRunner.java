package com.warmup.benchmarks;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Automatic benchmark runner that discovers and executes all benchmarks.
 * New benchmarks ending with "Benchmark" are automatically included.
 * 
 * GCProfiler is registered to report allocation rates (gc.alloc.rate.norm) for
 * validating the "zero allocations on hot path" claim.
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*Benchmark") // Automatically includes all classes ending with "Benchmark"
                .forks(3)
                .warmupIterations(5)
                .measurementIterations(10)
                .warmupTime(TimeValue.seconds(2))
                .measurementTime(TimeValue.seconds(3))
                .addProfiler(GCProfiler.class) // Report allocation rates (bytes/op)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .build();

        new Runner(opt).run();
    }
}
