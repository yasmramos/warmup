package io.github.yasmramos.warmup.benchmarks;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.container.BeanHandle;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks measuring Warmup's JIT/ASM resolution paths.
 *
 * <p>This benchmark uses {@code registerDynamic()} which triggers runtime JIT compilation
 * via ASM. This is NOT the compile-time path - for compile-time benchmarks, see
 * {@link WarmupCompileTimeBenchmark}.</p>
 *
 * <p><strong>Honesty fixes applied:</strong></p>
 * <ul>
 *   <li>The compiled factory measured by {@code warmupCompiledFactoryCreate} now comes from
 *       the real container ({@link Warmup#getCompiledFactory(Class)}), not from a standalone
 *       {@code AsmJITCompiler} instance. A separate compiler would generate a different
 *       hidden class and would not represent what the container actually uses.</li>
 *   <li>{@code BeanHandle} is captured ONCE in {@code @Setup(Level.Trial)}, so
 *       {@code warmupSingletonResolveViaHandle} measures only {@code handle.get()},
 *       not handle creation.</li>
 *   <li>{@code warmupSingletonResolveIndexed} is re-enabled through the facade
 *       {@link Warmup#resolveByIndex(int)} (pure indexed fast path).</li>
 *   <li>The prototype-with-five-dependencies bean now depends on five DISTINCT prototype
 *       beans instead of the same bean name repeated five times.</li>
 *   <li>All registered beans are pre-resolved in setup so JIT compilation happens before
 *       measurement; teardown performs a single consistency check at trial level
 *       (no per-iteration metrics printing with metrics disabled).</li>
 * </ul>
 *
 * <p>Scenarios measured:</p>
 * <ul>
 *   <li>{@code javaDirectInstantiation}: Baseline - pure Java instantiation (no container overhead)</li>
 *   <li>{@code warmupSingletonResolve}: Warmup JIT-compiled singleton resolution via public facade API</li>
 *   <li>{@code warmupPrototypeResolve}: Warmup prototype bean creation via public facade API</li>
 *   <li>{@code warmupPrototypeResolveWithOneDependency}: Prototype with 1 dependency (measures DI cost)</li>
 *   <li>{@code warmupPrototypeResolveWithFiveDependencies}: Prototype with 5 distinct dependencies (scalability)</li>
 *   <li>{@code warmupSingletonResolveIndexed}: <strong>Internal API</strong> - integer-indexed resolution (not comparable with Avaje)</li>
 *   <li>{@code warmupSingletonResolveViaHandle}: Pre-captured BeanHandle fast path</li>
 *   <li>{@code warmupCompiledFactoryCreate}: <strong>Internal API</strong> - direct invocation of the container's real factory</li>
 * </ul>
 *
 * <h3>Comparison Notes:</h3>
 * <ul>
 *   <li>{@code warmupSingletonResolve} is directly comparable to {@code avajeSingletonResolve}</li>
 *   <li>{@code warmupPrototypeResolve*} have no Avaje equivalent (Avaje is singleton-first)</li>
 *   <li>{@code warmupSingletonResolveIndexed} and {@code warmupCompiledFactoryCreate} are internal APIs
 *       that provide lower-bound measurements for Warmup's fastest paths</li>
 *   <li>{@code javaDirectInstantiation} is a pure JVM baseline, not a Warmup metric</li>
 * </ul>
 *
 * <h3>Path Comparison:</h3>
 * <ul>
 *   <li>{@code ResolutionBenchmark}: JIT/ASM path (dynamic registration via public facade)</li>
 *   <li>{@link WarmupCompileTimeBenchmark}: Pure compile-time path (@Bean annotation processing)</li>
 *   <li>{@link AvajeInjectBenchmark}: Avaje's compile-time path (for comparison)</li>
 * </ul>
 */
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ResolutionBenchmark {

    // ---- Test beans ----

    public static class SimpleBean {
        public SimpleBean() {}
    }

    public static class PrototypeBean {
        public PrototypeBean() {}
    }

    public static class PrototypeBeanWithOneDependency {
        private final PrototypeBean dependency;
        public PrototypeBeanWithOneDependency(PrototypeBean dependency) {
            this.dependency = dependency;
        }
    }

    public static class PrototypeBeanWithFiveDependencies {
        private final PrototypeBean d1, d2, d3, d4, d5;
        public PrototypeBeanWithFiveDependencies(
                PrototypeBean d1, PrototypeBean d2, PrototypeBean d3,
                PrototypeBean d4, PrototypeBean d5) {
            this.d1 = d1; this.d2 = d2; this.d3 = d3; this.d4 = d4; this.d5 = d5;
        }
    }

    // ---- Benchmark state ----

    private Warmup warmup;
    private BeanHandle<SimpleBean> simpleBeanHandle;      // captured ONCE in setup
    private CompiledFactory<SimpleBean> simpleFactory;    // REAL container factory

    private int prototypeBean0Index;                      // indexed handle for prototype baseline
    private int simpleBeanIndex;

    @Setup(Level.Trial)
    public void setup() {
        warmup = Warmup.builder()
            .diagnostic(false)
            .maxPendingCompilations(10)
            .autoDiscoverFactories(false)  // Manual registration for this benchmark
            .metrics(false)
            .build();

        // 1. Register singleton
        BeanDefinition<SimpleBean> singletonDef = new BeanDefinition<>(
            SimpleBean.class, "simpleBean", Scope.SINGLETON
        );
        warmup.registerDynamic(singletonDef);

        // 2. Register five DISTINCT prototype base beans (used as dependencies)
        for (int i = 0; i < 5; i++) {
            BeanDefinition<PrototypeBean> def = new BeanDefinition<>(
                PrototypeBean.class, "prototypeBean" + i, Scope.PROTOTYPE
            );
            warmup.registerDynamic(def);
        }

        // 3. Register prototype with one dependency (uses prototypeBean0)
        BeanDefinition<PrototypeBeanWithOneDependency> prototypeOneDepDef = new BeanDefinition<>(
            PrototypeBeanWithOneDependency.class, "prototypeBeanWithOneDep", Scope.PROTOTYPE,
            LifecycleCallbacks.empty(), false,
            new Object[]{"prototypeBean0"}
        );
        warmup.registerDynamic(prototypeOneDepDef);

        // 4. Register prototype with five DISTINCT dependencies
        BeanDefinition<PrototypeBeanWithFiveDependencies> prototypeFiveDepsDef = new BeanDefinition<>(
            PrototypeBeanWithFiveDependencies.class, "prototypeBeanWithFiveDeps", Scope.PROTOTYPE,
            LifecycleCallbacks.empty(), false,
            new Object[]{"prototypeBean0", "prototypeBean1", "prototypeBean2",
                         "prototypeBean3", "prototypeBean4"}
        );
        warmup.registerDynamic(prototypeFiveDepsDef);

        // 5. Pre-resolve everything to force JIT compilation INSIDE the container
        //    before measurement starts. Note: all five prototypeBeanN definitions share
        //    PrototypeBean.class, so they are resolved by index (type lookup would be
        //    ambiguous). resolveByIndex is scope-aware: for prototypes it creates a new
        //    instance through the container's compiled factory instead of returning null.
        warmup.resolve(SimpleBean.class);
        for (int i = 0; i < 5; i++) {
            int idx = warmup.indexOf("prototypeBean" + i);
            if (idx < 0 || warmup.<PrototypeBean>resolveByIndex(idx) == null) {
                throw new IllegalStateException("prototypeBean" + i + " was not indexed/cached");
            }
        }
        prototypeBean0Index = warmup.indexOf("prototypeBean0");
        warmup.resolve(PrototypeBeanWithOneDependency.class);
        warmup.resolve(PrototypeBeanWithFiveDependencies.class);

        // 6. Capture the REAL factories used by the container (not a standalone AsmJITCompiler).
        simpleFactory = warmup.getCompiledFactory(SimpleBean.class);
        if (simpleFactory == null) {
            throw new IllegalStateException("Container did not produce a compiled factory for SimpleBean");
        }
        // NOTE: getCompiledFactory(PrototypeBean.class) is intentionally NOT used here:
        // all five prototypeBeanN definitions share PrototypeBean.class, so a type-based
        // lookup would throw AmbiguousBeanException. The indexed path above already
        // forces JIT compilation of the shared factory; the prototype benchmark relies
        // on resolveByIndex returning non-null (guarded below).
        if (warmup.<PrototypeBean>resolveByIndex(prototypeBean0Index) == null) {
            throw new IllegalStateException("Indexed prototype resolution returned null");
        }

        // 7. Capture the handle ONCE so the handle benchmark measures only get().
        simpleBeanHandle = warmup.handle(SimpleBean.class);

        // 8. Index for the indexed-resolution benchmark.
        simpleBeanIndex = warmup.indexOf("simpleBean");
        if (simpleBeanIndex < 0) {
            throw new IllegalStateException("simpleBean was not indexed");
        }
    }

    // ---- Benchmarks ----

    /**
     * Pure JVM baseline: direct instantiation without any container involvement.
     */
    @Benchmark
    public Object javaDirectInstantiation() {
        return new SimpleBean();
    }

    /**
     * Measures resolve() overhead for a SINGLETON bean that is already cached.
     * This tests the fast-path lookup using the public Warmup facade API.
     * Directly comparable to AvajeInjectBenchmark.avajeSingletonResolve().
     */
    @Benchmark
    public Object warmupSingletonResolve() {
        return warmup.resolve(SimpleBean.class);
    }

    /**
     * Measures resolveByIndex() overhead for a SINGLETON bean that is already cached.
     * This tests the experimental integer-indexed fast path (avoids String hashing
     * and ClassValue lookup entirely).
     *
     * <p><strong>INTERNAL API BENCHMARK:</strong> Results are lower-bound measurements
     * and NOT directly comparable with Avaje Inject benchmarks.</p>
     */
    @Benchmark
    public Object warmupSingletonResolveIndexed() {
        Object result = warmup.resolveByIndex(simpleBeanIndex);
        // Fallback to name-based resolve if not yet cached (shouldn't happen after warmup)
        return result != null ? result : warmup.resolve(SimpleBean.class);
    }

    /**
     * Measures repeated singleton resolution via a PRE-CAPTURED BeanHandle.
     * The handle is created once in @Setup, so this measures only handle.get():
     * the handle fast path that avoids ClassValue lookup on each resolution.
     */
    @Benchmark
    public Object warmupSingletonResolveViaHandle() {
        return simpleBeanHandle.get();
    }

    /**
     * Measures PROTOTYPE bean creation through the indexed fast path
     * (resolveByIndex). Because resolveByIndex is scope-aware, a prototype index
     * produces a NEW instance on every call via the container's compiled factory,
     * exercising the same wired path as name-based resolution but without String
     * hashing or Map lookups.
     *
     * <p>The benchmark guards in {@code @Setup} fail fast if the indexed path ever
     * returns null, so no silent fallback hides a regression.</p>
     */
    @Benchmark
    public Object warmupPrototypeResolve() {
        return warmup.resolveByIndex(prototypeBean0Index);
    }

    /**
     * Measures resolve() for a PROTOTYPE bean with one dependency.
     * The cost should scale with the number of dependencies.
     */
    @Benchmark
    public Object warmupPrototypeResolveWithOneDependency() {
        return warmup.resolve(PrototypeBeanWithOneDependency.class);
    }

    /**
     * Measures resolve() for a PROTOTYPE bean with five DISTINCT dependencies.
     * The cost should scale with the number of dependencies.
     */
    @Benchmark
    public Object warmupPrototypeResolveWithFiveDependencies() {
        return warmup.resolve(PrototypeBeanWithFiveDependencies.class);
    }

    /**
     * Measures direct compiled factory creation without container overhead.
     * Baseline for the JIT path. The factory is the REAL instance the container
     * uses on its production resolution path (obtained via
     * {@link Warmup#getCompiledFactory(Class)}), so this measurement is honest.
     *
     * <p><strong>INTERNAL API BENCHMARK:</strong> This measures direct factory invocation,
     * which is not exposed in Avaje Inject's public API. Results are lower-bound
     * measurements and NOT directly comparable with Avaje benchmarks.</p>
     */
    @Benchmark
    public Object warmupCompiledFactoryCreate() {
        return simpleFactory.create();
    }

    /**
     * Single consistency check at trial end: verifies the pre-captured handle and
     * resolve() return the same cached singleton. No per-iteration metrics printing
     * (metrics are disabled for bare fast-path measurement).
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (warmup == null) {
            return;
        }
        SimpleBean viaResolve = warmup.resolve(SimpleBean.class);
        SimpleBean viaHandle = simpleBeanHandle.get();
        if (viaResolve != viaHandle) {
            throw new IllegalStateException("BeanHandle returned a different instance than resolve()");
        }
        warmup.close();
    }
}
