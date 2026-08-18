package com.warmup.benchmarks;

import com.warmup.core.container.HybridContainer;
import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;
import com.warmup.asm.AsmJITCompiler;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks comparing Warmup compile-time vs JIT paths.
 * 
 * Scenarios:
 * - Direct instantiation (baseline)
 * - Warmup compile-time factory (zero-overhead path)
 * - Warmup JIT-compiled factory (runtime compilation)
 * 
 * Metrics:
 * - Resolution time (single bean resolution)
 * - Throughput (resolutions per second)
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ResolutionBenchmark {

    // Test beans with varying complexity
    
    public static class SimpleBean {
        public SimpleBean() {}
    }
    
    public static class BeanWithOneDependency {
        private final SimpleBean dependency;
        public BeanWithOneDependency(SimpleBean dependency) {
            this.dependency = dependency;
        }
    }
    
    public static class BeanWithFiveDependencies {
        private final SimpleBean d1, d2, d3, d4, d5;
        public BeanWithFiveDependencies(SimpleBean d1, SimpleBean d2, SimpleBean d3, SimpleBean d4, SimpleBean d5) {
            this.d1 = d1; this.d2 = d2; this.d3 = d3; this.d4 = d4; this.d5 = d5;
        }
    }

    // Benchmark state
    private HybridContainer container;
    private CompiledFactory<SimpleBean> simpleFactory;
    
    @Setup(Level.Trial)
    public void setup() throws Exception {
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
        container = new HybridContainer(jitCompiler, false);
        
        // Register simple bean with JIT compilation
        BeanDefinition<SimpleBean> definition = new BeanDefinition<>(
            SimpleBean.class, "simpleBean", Scope.SINGLETON
        );
        container.registerDynamic(definition);
        
        // Pre-compile factory for warmup
        simpleFactory = jitCompiler.compile(SimpleBean.class);
    }

    @Benchmark
    public Object directInstantiation() {
        return new SimpleBean();
    }

    @Benchmark
    public Object warmupJITResolution() {
        return container.resolve("simpleBean");
    }

    @Benchmark
    public Object compiledFactoryCreate() {
        return simpleFactory.create();
    }
}
