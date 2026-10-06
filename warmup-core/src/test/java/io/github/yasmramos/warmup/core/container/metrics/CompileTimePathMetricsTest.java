package io.github.yasmramos.warmup.core.container.metrics;

import io.github.yasmramos.warmup.asm.AsmJITCompiler;
import io.github.yasmramos.warmup.core.container.ContainerMetrics;
import io.github.yasmramos.warmup.core.container.HybridContainer;
import io.github.yasmramos.warmup.core.container.HybridContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@code ContainerMetrics} accounting of the COMPILE_TIME path.
 *
 * <p>Beans registered through ServiceLoader discovery are wired at container startup, and the
 * wired branch of the singleton resolution path creates the instance directly from the factory.
 * That branch bypasses {@code createBeanWithoutDeferredInjection}, which is where the
 * COMPILE_TIME path is otherwise recorded, so {@code compileTimeHits} used to stay at 0 even
 * though the generated factory was genuinely being used. Downstream verification (including
 * the {@code WarmupCompileTimeBenchmark} compile-time check) could therefore never confirm
 * that Path A was active.</p>
 */
class CompileTimePathMetricsTest {

    private static HybridContainer containerWithMetrics() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
                .autoDiscoverFactories(true)
                .metricsEnabled(true)
                .build();
        return new HybridContainer(config, new AsmJITCompiler());
    }

    @Test
    @DisplayName("ServiceLoader-discovered compile-time factory is registered and wired")
    void serviceLoaderRegistrarIsDiscovered() {
        HybridContainer container = containerWithMetrics();

        assertTrue(container.getBeanNames().contains(CompileTimeMetricsTestRegistrar.BEAN_NAME),
                "Expected the test registrar to be discovered via ServiceLoader");
    }

    @Test
    @DisplayName("Resolving a compile-time bean records a COMPILE_TIME hit")
    void compileTimeHitIsRecordedForDiscoveredFactory() {
        HybridContainer container = containerWithMetrics();

        CompileTimeMetricsTestBean bean =
                container.resolve(CompileTimeMetricsTestBean.class);
        assertNotNull(bean);

        ContainerMetrics metrics = container.getMetrics();
        assertTrue(metrics.compileTimeHits() > 0,
                "compileTimeHits should be greater than 0 but was " + metrics.compileTimeHits()
                        + " - the wired compile-time path is not being accounted for");
    }

    @Test
    @DisplayName("Compile-time resolution does not fall back to JIT")
    void compileTimeHitDoesNotFallBackToJit() {
        HybridContainer container = containerWithMetrics();

        container.resolve(CompileTimeMetricsTestBean.class);

        ContainerMetrics metrics = container.getMetrics();
        assertEquals(0, metrics.jitHits(),
                "A compile-time registered bean must not be JIT-compiled");
        assertEquals(0, metrics.fallbackCount(),
                "A compile-time registered bean must not need the reflection fallback");
    }

    @Test
    @DisplayName("Singleton is created exactly once regardless of repeated resolutions")
    void singletonIsCachedAcrossResolutions() {
        HybridContainer container = containerWithMetrics();

        CompileTimeMetricsTestBean first = container.resolve(CompileTimeMetricsTestBean.class);
        CompileTimeMetricsTestBean second = container.resolve(CompileTimeMetricsTestBean.class);

        assertTrue(first == second, "Singleton should be cached and returned as the same instance");
    }
}