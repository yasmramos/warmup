package com.warmup.benchmarks;

import com.warmup.core.container.HybridContainer;
import com.warmup.core.container.impl.HybridContainerImpl;
import com.warmup.core.scope.Scope;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring startup time for different bean counts.
 * Compares Warmup initialization vs Spring context loading.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
public class StartupBenchmark {

    @Param({"10", "100", "1000"})
    private int beanCount;

    private HybridContainer container;

    @Setup(Level.Invocation)
    public void setup() {
        container = new HybridContainerImpl();
    }

    @Benchmark
    public HybridContainer warmupStartup() {
        HybridContainer freshContainer = new HybridContainerImpl();
        
        // Register beans simulating application startup
        for (int i = 0; i < beanCount; i++) {
            String name = "startupBean" + i;
            freshContainer.register(name, ServiceBean.class, ServiceBean::new, Scope.SINGLETON);
        }
        
        // Trigger background warmup
        freshContainer.startBackgroundWarmup();
        
        return freshContainer;
    }

    @Benchmark
    public int warmupRegistrationOnly() {
        int count = 0;
        for (int i = 0; i < beanCount; i++) {
            String name = "regBean" + i;
            container.register(name, ServiceBean.class, ServiceBean::new, Scope.SINGLETON);
            count++;
        }
        return count;
    }

    /**
     * Service bean with dependencies for realistic startup simulation.
     */
    public static class ServiceBean {
        private final String id = "service-" + System.nanoTime();
        private final List<String> data = new ArrayList<>();
        
        public ServiceBean() {
            data.add("initialized");
        }
        
        public String getId() {
            return id;
        }
    }
}
