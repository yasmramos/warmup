package com.warmup.benchmarks;

import com.warmup.asm.AsmJITCompiler;
import com.warmup.core.container.HybridContainer;
import com.warmup.core.container.HybridContainerConfig;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Startup benchmark: measures container initialization time with varying bean counts.
 * 
 * <p>This benchmark compares two registration strategies:</p>
 * <ul>
 *   <li>{@code startupWithBeansDynamic}: Uses {@code registerDynamic()} which triggers JIT compilation</li>
 *   <li>{@code startupWithBeansCompileTime}: Uses compile-time factories via auto-discovery (no JIT overhead)</li>
 * </ul>
 * 
 * <p>Scenarios: 10, 100, 1000 beans</p>
 * 
 * <p>Note: This benchmark uses explicit HybridContainer construction to measure
 * raw startup performance. For production usage, prefer Warmup.create().</p>
 * 
 * @see AvajeStartupBenchmark for Avaje Inject startup comparison
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class StartupBenchmark {

    @Param({"10", "100", "1000"})
    private int beanCount;
    
    private List<HybridContainer> containersToClose;

    @Setup(Level.Iteration)
    public void setupIteration() {
        containersToClose = new ArrayList<>();
    }
    
    @TearDown(Level.Iteration)
    public void tearDownIteration() {
        // Clean up containers if needed
        containersToClose.clear();
    }

    /**
     * Measures startup time using dynamic registration (JIT path).
     * Each bean is registered via {@code registerDynamic()}, which triggers background JIT compilation.
     */
    @Benchmark
    public HybridContainer startupWithBeansDynamic() {
        // Using explicit constructor for benchmark measurement
        // In production, use: com.warmup.core.Warmup warmup = com.warmup.core.Warmup.create();
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
        HybridContainer container = new HybridContainer(jitCompiler, false);
        containersToClose.add(container);
        
        // Register beans dynamically - triggers JIT warmup
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
     * Measures startup time using compile-time factory discovery.
     * Beans are pre-registered via annotation processing and discovered at startup
     * through ServiceLoader (META-INF/services/com.warmup.core.jit.FactoryRegistrar).
     * 
     * <p>This provides a fair comparison against Avaje's compile-time startup,
     * as both frameworks use pre-generated factories discovered at runtime.</p>
     */
    @Benchmark
    public HybridContainer startupWithBeansCompileTime() {
        // Create container with autoDiscoverFactories=true to enable compile-time factory discovery
        // No JIT compiler needed since we're using pre-generated factories
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
        HybridContainerConfig config = new HybridContainerConfig(
            false,  // diagnosticMode
            10,     // maxPendingCompilations
            true,   // autoDiscoverFactories - discovers compile-time factories
            false   // metricsEnabled - disabled for pure startup measurement
        );
        HybridContainer container = new HybridContainer(config, jitCompiler);
        containersToClose.add(container);
        
        // Note: We do NOT call registerDynamic() here.
        // Compile-time factories are auto-registered via ServiceLoader discovery.
        // The startup cost is just the ServiceLoader iteration and factory instantiation.
        
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
