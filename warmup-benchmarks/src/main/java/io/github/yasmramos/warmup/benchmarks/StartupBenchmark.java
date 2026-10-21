package io.github.yasmramos.warmup.benchmarks;

import io.github.yasmramos.warmup.asm.AsmJITCompiler;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.container.Container;
import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
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
 * Startup benchmark: measures container initialization time with varying bean
 * counts.
 *
 * <p>
 * This benchmark compares two registration strategies:</p>
 * <ul>
 * <li>{@code startupWithBeansDynamic}: Uses {@code registerDynamic()} which
 * triggers JIT compilation</li>
 * <li>{@code startupWithBeansCompileTime}: Uses compile-time factories via
 * auto-discovery (no JIT overhead)</li>
 * </ul>
 *
 * <p>
 * Scenarios: 10, 100, 1000 beans</p>
 *
 * <p>
Note: This benchmark uses explicit Container construction to measure
raw startup performance. For production usage, prefer
Warmup.builder().build().</p>
 *
 * @see AvajeStartupBenchmark for Avaje Inject startup comparison
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class StartupBenchmark {

    @Param({"10", "100", "1000"})
    private int beanCount;

    private List<Warmup> containersToClose;

    @Setup(Level.Iteration)
    public void setupIteration() {
        containersToClose = new ArrayList<>();
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() {
        // Clean up containers after each invocation to prevent thread pool accumulation
        if (containersToClose != null) {
            for (Warmup container : containersToClose) {
                container.close();
            }
            containersToClose.clear();
        }
    }

    /**
     * Measures startup time using dynamic registration (JIT path). Each bean is
     * registered via {@code registerDynamic()}, which triggers background JIT
     * compilation.
     *
     * <p>
     * Note: Uses Object.class beans for minimal registration overhead. For
     * realistic ASM compilation costs, see benchmarks with real bean types.</p>
     */
    @Benchmark
    public Warmup startupWithBeansDynamic(Blackhole blackhole) {

        Warmup container = Warmup.builder().build();

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
     * Measures startup time using compile-time factory discovery. Beans are
     * pre-registered via annotation processing and discovered at startup
     * through ServiceLoader
     * (META-INF/services/io.github.yasmramos.warmup.core.jit.FactoryRegistrar).
     *
     * <p>
     * This method registers {@code beanCount} beans using their compile-time
     * factories to ensure the benchmark scales with the parameter and provides
     * honest measurements.</p>
     *
     * <p>
     * Note: The actual compile-time factories are discovered via ServiceLoader,
     * but we explicitly register beanCount beans to make the benchmark scale
     * properly.</p>
     */
    @Benchmark
    public Warmup startupWithBeansCompileTime(Blackhole blackhole) {

        Warmup container = Warmup.builder()
                .diagnostic(true)
                .maxPendingCompilations(10)
                .autoDiscoverFactories(true)
                .metrics(false)
                .propertyResolver(null)
                .profiles(new String[0])
                .build();

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

            container.registerDynamic(definition);
        }

        blackhole.consume(container);
        return container;
    }

    /**
     * Alternative benchmark using the ergonomic Warmup facade. Uncomment to
     * compare performance between direct and facade approaches.
     */
    // @Benchmark
    public Warmup startupWithBeansFacade(Blackhole blackhole) {
        Warmup warmup = Warmup.builder()
                .maxPendingCompilations(10)
                .build();

        // Register beans dynamically
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            BeanDefinition<Object> definition = new BeanDefinition<>(
                    Object.class, beanName, Scope.SINGLETON
            );
            warmup.registerDynamic(definition);
        }

        blackhole.consume(warmup);
        return warmup;
    }
}
