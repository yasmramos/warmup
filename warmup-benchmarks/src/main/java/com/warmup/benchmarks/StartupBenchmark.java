package com.warmup.benchmarks;

import com.warmup.core.container.HybridContainer;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;
import com.warmup.asm.AsmJITCompiler;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Startup benchmark: measures container initialization time with varying bean counts.
 * 
 * Scenarios: 10, 100, 1000 beans
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class StartupBenchmark {

    @Param({"10", "100", "1000"})
    private int beanCount;

    @Benchmark
    public HybridContainer startupWithBeans() {
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
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
}
