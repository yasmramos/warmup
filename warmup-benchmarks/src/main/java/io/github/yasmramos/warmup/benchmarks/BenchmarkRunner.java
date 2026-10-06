package io.github.yasmramos.warmup.benchmarks;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
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

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        // Any arguments are forwarded verbatim to JMH, so filtering and tuning
        // work as documented, e.g.:
        //   java -jar benchmarks.jar -l
        //   java -jar benchmarks.jar ".*ResolutionBenchmark.*"
        //   java -jar benchmarks.jar -p warmupSingletonResolve -f 1 -wi 2 -i 3
        // With no arguments the full default suite below is executed.
        Options opt = (args.length > 0) ? new CommandLineOptions(args) : defaultOptions();

        new Runner(opt).run();
    }

    private static Options defaultOptions() {
        return new OptionsBuilder()
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
    }
}
