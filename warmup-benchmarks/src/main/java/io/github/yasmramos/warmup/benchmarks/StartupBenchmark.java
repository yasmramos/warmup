package io.github.yasmramos.warmup.benchmarks;

import io.github.yasmramos.warmup.asm.AsmJITCompiler;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.container.Container;
import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
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
 * This benchmark compares two registration strategies using honest, symmetric
 * paths:</p>
 * <ul>
 * <li>{@code startupWithBeansDynamic}: Uses {@code registerDynamic()}, which
 * marks each bean for lazy JIT compilation. All beans are then resolved inside
 * the measured region so the ASM compilation cost is paid within the benchmark
 * (full startup), not deferred to container shutdown.</li>
 * <li>{@code startupWithBeansCompileTime}: Registers the same number of beans
 * through the compile-time path only: a {@link BeanDefinition} plus a
 * pre-compiled {@link CompiledFactory} wired via
 * {@code Container.register(definition, factory)} - the exact mechanism the
 * annotation processor uses through ServiceLoader-discovered
 * {@code FactoryRegistrar}s. No JIT compilation and no background warmup ever
 * occur on this path.</li>
 * </ul>
 *
 * <p>
 * Both benchmarks force resolution of every registered bean inside the
 * measured region, so they measure "registration + instantiation" on their
 * respective paths, making the per-bean comparison fair. Divide the reported
 * average by {@code beanCount} to obtain the per-bean startup cost.</p>
 *
 * <p>
 * Fixes applied relative to previous versions of this benchmark:</p>
 * <ul>
 * <li>The compile-time benchmark previously called {@code registerDynamic()}
 * (the dynamic/JIT path), which made the "compile-time vs dynamic" comparison
 * tautological and produced flat numbers regardless of {@code beanCount}. It
 * now exercises only the real compile-time registration API.</li>
 * <li>{@code diagnostic(true)} was previously enabled only on the compile-time
 * benchmark, penalizing it unfairly against the dynamic path. Diagnostic mode
 * is now disabled in both paths (production-representative).</li>
 * <li>Neither benchmark resolved any bean, so JIT/ASM compilation costs were
 * either never paid or silently deferred to {@code close()}. Resolution of all
 * beans is now forced inside the measured region.</li>
 * <li>Containers are closed at {@code Level.Invocation} because each invocation
 * builds a fresh container; leaving them open would accumulate thread pools and
 * saturate GC between iterations. {@code close()} is idempotent, so
 * double-closing is safe.</li>
 * </ul>
 *
 * <p>
 * Note: This benchmark uses explicit {@link Container} construction (with an
 * isolated {@link AsmJITCompiler} per container) to measure raw startup
 * performance. For production usage, prefer {@code Warmup.builder().build()}.</p>
 *
 * <p>
 * Scenarios: 10, 100, 1000 beans.</p>
 *
 * @see AvajeStartupBenchmark for Avaje Inject startup comparison
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class StartupBenchmark {

    /**
     * Trivial bean type used to isolate registration/startup overhead from
     * constructor cost. The compile-time factory below instantiates it directly,
     * mirroring what the annotation processor generates for a dependency-free
     * bean. Using a dedicated type (instead of {@code Object.class}) avoids
     * ambiguous-by-type lookups and reflects a realistic generated factory.
     */
    public static final class StartupBean {
        public StartupBean() {
        }
    }

    /**
     * Pre-compiled factory representing the compile-time (annotation-processor)
     * path: creates the bean directly, without reflection or JIT compilation.
     */
    private static final class StartupBeanFactory implements CompiledFactory<StartupBean> {
        @Override
        public StartupBean create(Object... dependencies) {
            return new StartupBean();
        }

        @Override
        public Class<StartupBean> getBeanType() {
            return StartupBean.class;
        }
    }

    @Param({"10", "100", "1000"})
    private int beanCount;

    private List<Container> containersToClose;

    @Setup(Level.Iteration)
    public void setupIteration() {
        containersToClose = new ArrayList<>();
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() {
        // Each invocation creates its own container; close it immediately to
        // prevent thread-pool accumulation across invocations. close() is
        // idempotent, so closing twice is harmless.
        if (containersToClose != null) {
            for (Container container : containersToClose) {
                container.close();
            }
            containersToClose.clear();
        }
    }

    /**
     * Builds a fresh container with production-representative defaults:
     * auto-discovery of compile-time factories enabled, diagnostics and metrics
     * disabled (identical configuration for both benchmark paths so the
     * comparison is fair).
     */
    private Container newContainer() {
        ContainerConfig config = ContainerConfig.builder()
                .autoDiscoverFactories(true)
                .diagnosticMode(false)
                .metricsEnabled(false)
                .build();
        // A fresh JIT compiler per container keeps ASM state isolated across
        // invocations, matching what Warmup.builder().build() does internally.
        return new Container(config, new AsmJITCompiler());
    }

    /**
     * Measures startup time using dynamic registration (JIT path). Each bean is
     * registered via {@code registerDynamic()}, which marks it for lazy JIT
     * compilation. Every bean is then resolved inside the measured region so
     * the ASM compilation cost is paid during startup measurement rather than
     * being deferred to {@code close()}.
     */
    @Benchmark
    public Container startupWithBeansDynamic(Blackhole blackhole) {

        Container container = newContainer();

        containersToClose.add(container);

        // Register beans dynamically - marks each bean for lazy JIT warmup
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            BeanDefinition<Object> definition = new BeanDefinition<>(
                    Object.class, beanName, Scope.SINGLETON
            );
            container.registerDynamic(definition);
        }

        // Force resolution of every bean so JIT/ASM compilation happens here,
        // inside the measured region (true full startup cost).
        for (int i = 0; i < beanCount; i++) {
            blackhole.consume(container.resolve("bean" + i));
        }

        blackhole.consume(container);
        return container;
    }

    /**
     * Measures startup time using the compile-time factory path. Beans are
     * registered with an explicit pre-compiled {@link CompiledFactory} (the same
     * mechanism the annotation processor uses via ServiceLoader-discovered
     * {@code FactoryRegistrar}s), so no JIT compilation or background warmup
     * ever occurs. Every bean is resolved inside the measured region, exactly
     * like the dynamic benchmark, so both paths pay instantiation cost.
     *
     * <p>
     * Unlike the previous version of this benchmark, this method does NOT call
     * {@code registerDynamic()}; it exercises only the compile-time registration
     * API.</p>
     */
    @Benchmark
    public Container startupWithBeansCompileTime(Blackhole blackhole) {

        Container container = newContainer();

        containersToClose.add(container);

        // Reusable pre-compiled factory: one instance is enough since it is
        // stateless, exactly like a generated per-class factory.
        CompiledFactory<StartupBean> factory = new StartupBeanFactory();

        // Compile-time registration path: definition + pre-compiled factory.
        // register() wires the factory immediately (no pendingWarmup, no JIT).
        for (int i = 0; i < beanCount; i++) {
            String beanName = "bean" + i;
            BeanDefinition<StartupBean> definition = new BeanDefinition<>(
                    StartupBean.class, beanName, Scope.SINGLETON
            );
            container.register(definition, factory);
        }

        // Explicitly wire the freshly registered factories, replicating the
        // second pass that the ServiceLoader discovery path performs at build
        // time (Container.discoverAndRegisterFactories -> wireFactories()).
        // Without this, beans registered after construction would fall back to
        // the non-wired create(Object...) path instead of the wired fast path.
        container.wireFactories();

        // Force resolution of every bean so instantiation happens inside the
        // measured region, symmetric with the dynamic benchmark.
        for (int i = 0; i < beanCount; i++) {
            blackhole.consume(container.resolve("bean" + i));
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
