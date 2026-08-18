package com.warmup.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

/**
 * Memory footprint benchmark measuring heap usage for different bean counts.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
public class MemoryFootprintBenchmark {

    @Param({"10", "100", "1000"})
    private int beanCount;

    private Runtime runtime;

    @Setup(Level.Trial)
    public void setup() {
        runtime = Runtime.getRuntime();
        // Force GC before measurements
        runtime.gc();
    }

    @Benchmark
    public long measureHeapUsage() {
        // Create beans and measure memory
        Object[] beans = new Object[beanCount];
        
        long before = runtime.totalMemory() - runtime.freeMemory();
        
        for (int i = 0; i < beanCount; i++) {
            beans[i] = new MemoryBean("bean-" + i);
        }
        
        long after = runtime.totalMemory() - runtime.freeMemory();
        
        // Cleanup
        beans = null;
        runtime.gc();
        
        return after - before;
    }

    /**
     * Bean with configurable memory footprint.
     */
    public static class MemoryBean {
        private final String id;
        private final byte[] data;
        
        public MemoryBean(String id) {
            this.id = id;
            // Each bean holds ~1KB of data
            this.data = new byte[1024];
        }
        
        public String getId() {
            return id;
        }
    }
}
