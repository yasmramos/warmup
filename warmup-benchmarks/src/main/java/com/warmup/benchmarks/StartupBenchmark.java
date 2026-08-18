package com.warmup.benchmarks;

import com.warmup.core.container.HybridContainer;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Startup benchmark: measures container initialization time with varying bean counts.
 * 
 * Scenarios: 10, 100, 1000 beans
 * 
 * Note: This benchmark uses explicit HybridContainer construction to measure
 * raw startup performance. For production usage, prefer Warmup.create().
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@org.openjdk.jmh.annotations.Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class StartupBenchmark {

    @Param({"10", "100", "1000"})
    private int beanCount;

    @Benchmark
    public HybridContainer startupWithBeans() {
        // Using explicit constructor for benchmark measurement
        // In production, use: com.warmup.core.Warmup warmup = com.warmup.core.Warmup.create();
        com.warmup.asm.AsmJITCompiler jitCompiler = new com.warmup.asm.AsmJITCompiler();
        HybridContainer container = new HybridContainer(jitCompiler, false);
        
        // Register beans dynamically
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            BeanDefinition<Object> definition = new BeanDefinition<>(
                Object.class, beanName, Scope.SINGLETON
            );
            container.registerDynamic(definition);
        }
        
        return container;
    }
    
    /**
     * Alternative benchmark using the ergonomic Warmup facade.
     * Uncomment to compare performance between direct and facade approaches.
     */
    // @Benchmark
    public com.warmup.core.Warmup startupWithBeansFacade() {
        com.warmup.core.Warmup warmup = com.warmup.core.Warmup.builder()
                .maxPendingCompilations(10)
                .build();
        
        // Register beans dynamically
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            BeanDefinition<Object> definition = new BeanDefinition<>(
                Object.class, beanName, Scope.SINGLETON
            );
            warmup.container().registerDynamic(definition);
        }
        
        return warmup;
    }
}
