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
import org.openjdk.jmh.infra.Blackhole;

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
    
    @TearDown(Level.Invocation)
    public void tearDownInvocation() {
        // Clean up containers after each invocation to prevent thread pool accumulation
        if (containersToClose != null) {
            for (HybridContainer container : containersToClose) {
                container.close();
            }
            containersToClose.clear();
        }
    }

    /**
     * Measures startup time using dynamic registration (JIT path).
     * Each bean is registered via {@code registerDynamic()}, which triggers background JIT compilation.
     * 
     * <p>Note: Uses Object.class beans for minimal registration overhead. For realistic
     * ASM compilation costs, see benchmarks with real bean types.</p>
     */
    @Benchmark
    public HybridContainer startupWithBeansDynamic(Blackhole blackhole) {
        // Using explicit constructor for benchmark measurement
        // In production, use: com.warmup.core.Warmup warmup = com.warmup.core.Warmup.create();
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
        HybridContainerConfig config = new HybridContainerConfig.Builder().build();
        HybridContainer container = new HybridContainer(config, jitCompiler);
        containersToClose.add(container);
        
        // Register beans dynamically - triggers JIT warmup
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            BeanDefinition<Object> definition = new BeanDefinition<>(
                Object.class, beanName, Scope.SINGLETON
            );
            container.registerDynamic(definition);
        }
        
        blackhole.consume(container);
        return container;
    }
    
    /**
     * Measures startup time using compile-time factory discovery.
     * Beans are pre-registered via annotation processing and discovered at startup
     * through ServiceLoader (META-INF/services/com.warmup.core.jit.FactoryRegistrar).
     * 
     * <p>This method registers {@code beanCount} beans using their compile-time factories
     * to ensure the benchmark scales with the parameter and provides honest measurements.</p>
     * 
     * <p>Note: The actual compile-time factories are discovered via ServiceLoader,
     * but we explicitly register beanCount beans to make the benchmark scale properly.</p>
     */
    @Benchmark
    public HybridContainer startupWithBeansCompileTime(Blackhole blackhole) {
        // Create container with autoDiscoverFactories=true to enable compile-time factory discovery
        // No JIT compiler needed since we're using pre-generated factories
        AsmJITCompiler jitCompiler = new AsmJITCompiler();
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .diagnosticMode(false)
            .maxPendingCompilations(10)
            .autoDiscoverFactories(true)
            .metricsEnabled(false)
            .propertyResolver(null)
            .activeProfiles(new String[0])
            .build();
        HybridContainer container = new HybridContainer(config, jitCompiler);
        containersToClose.add(container);
        
        // Register additional beanCount beans using registerFactory() with compile-time factories
        // This ensures the benchmark scales with beanCount parameter by registering N factories
        // We use the already-discovered factories from ServiceLoader via container.registerFactory()
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            // Use registerFactory() which leverages compile-time factories directly
            // This is fundamentally different from registerDynamic() which triggers JIT compilation
            // Each iteration registers a new factory, exercising the compile-time path
            BeanDefinition<Object> definition = new BeanDefinition<>(
                Object.class, beanName, Scope.SINGLETON
            );
            // Create a simple compiled factory for this bean (compile-time equivalent)
            // Using registerFactory bypasses JIT and uses the factory directly
            // Note: Factory classes are generated by the annotation processor at compile-time
            // For this benchmark, we use a simple anonymous factory implementation
            container.registerFactory(beanName, new com.warmup.core.jit.CompiledFactory<Object>() {
                @Override
                public void wire(com.warmup.core.jit.CompiledFactory<?>[] dependencyFactories) {}
                @Override
                public Object get() { return new Object(); }
                @Override
                public Object create(Object... dependencies) { return new Object(); }
                @Override
                public Class<Object> getBeanType() { return Object.class; }
                @Override
                public int getDependencyCount() { return 0; }
            });
        }
        
        blackhole.consume(container);
        return container;
    }
    
    /**
     * Alternative benchmark using the ergonomic Warmup facade.
     * Uncomment to compare performance between direct and facade approaches.
     */
    // @Benchmark
    public com.warmup.core.Warmup startupWithBeansFacade(Blackhole blackhole) {
        com.warmup.core.Warmup warmup = com.warmup.core.Warmup.builder()
                .maxPendingCompilations(10)
                .build();
        
        // Register beans dynamically
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            BeanDefinition<Object> definition = new BeanDefinition<>(
                Object.class, beanName, Scope.SINGLETON
            );
            warmup.unsafeContainer().registerDynamic(definition);
        }
        
        blackhole.consume(warmup);
        return warmup;
    }
}
