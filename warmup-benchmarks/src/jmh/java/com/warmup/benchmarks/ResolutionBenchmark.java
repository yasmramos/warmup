package com.warmup.benchmarks;

import com.warmup.core.container.HybridContainer;
import com.warmup.core.container.impl.HybridContainerImpl;
import com.warmup.core.scope.Scope;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks comparing Warmup compile-time path vs JIT path vs direct instantiation.
 * 
 * Scenarios: 10, 100, 1000, 10000 beans
 * Metrics: startup time, resolution time, memory footprint
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class ResolutionBenchmark {

    private HybridContainer container;
    private TestBean directBean;
    
    @Param({"10", "100", "1000"})
    private int beanCount;

    @Setup(Level.Trial)
    public void setupTrial() {
        container = new HybridContainerImpl();
        
        // Register beans with different scopes
        for (int i = 0; i < beanCount; i++) {
            String name = "bean" + i;
            container.register(name, TestBean.class, TestBean::new, Scope.SINGLETON);
        }
        
        directBean = new TestBean();
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Pre-warm some beans to test JIT path
        if (beanCount > 0) {
            container.resolve("bean0", TestBean.class);
        }
    }

    @Benchmark
    public Object directInstantiation() {
        return new TestBean();
    }

    @Benchmark
    public Object directReference() {
        return directBean;
    }

    @Benchmark
    public Object warmupCompileTimePath() {
        return container.resolve("bean0", TestBean.class);
    }

    @Benchmark
    public Object warmupJitPath() {
        String jitBeanName = "jitBean" + System.nanoTime();
        container.registerDynamic(jitBeanName, TestBean.class, TestBean::new, Scope.PROTOTYPE);
        return container.resolve(jitBeanName, TestBean.class);
    }

    @Benchmark
    public Object warmupResolveByType() {
        return container.resolve(TestBean.class);
    }

    /**
     * Simple test bean for benchmarks.
     */
    public static class TestBean {
        private final String data = "benchmark-data";
        private final long timestamp = System.currentTimeMillis();
        
        public String getData() {
            return data;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}
