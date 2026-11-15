package io.github.yasmramos.warmup.core.integration.p0;

import io.github.yasmramos.warmup.core.container.Container;
import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * P0 regression: resolving a bean by name must inject its deferred
 * ({@code @Lazy} field/setter) dependencies, exactly like type-based
 * resolution does.
 *
 * <p>{@code resolve(Class)} ends up in a path that calls
 * {@code injectDeferredDependencies}, but {@code resolve(String)} used to skip
 * that step whenever the compiled factory was already wired. Because the
 * factory becomes wired asynchronously (background warmup), the missing
 * injection surfaced only intermittently. This test wires the factories up
 * front so the wired fast path is always exercised.</p>
 */
class ResolveByNameDeferredInjectionTest {

    private Container container;

    @BeforeEach
    void setUp() {
        container = new Container(
                ContainerConfig.builder().autoDiscoverFactories(false).build(),
                new io.github.yasmramos.warmup.asm.AsmJITCompiler());
    }

    @AfterEach
    void tearDown() {
        container.shutdown();
    }

    @Test
    void resolveByName_injectsDeferredDependency() {
        BeanDefinition<HolderDependency> dependencyDef =
                new BeanDefinition<>(HolderDependency.class, "holderDependency");
        container.register(dependencyDef, (CompiledFactory<HolderDependency>) deps -> new HolderDependency());

        // Holder bean whose single dependency is a deferred (lazy) field
        // injection, mirroring what the annotation processor generates.
        BeanDefinition<LazyHolder> holderDef = new BeanDefinition<>(
                LazyHolder.class,
                "lazyHolder",
                Scope.SINGLETON,
                LifecycleCallbacks.empty(),
                false,
                new Object[]{"holderDependency"},
                new String[0],
                new String[0],
                "",
                new boolean[]{true},   // deferredDependencies
                new boolean[]{true});  // fieldOrSetterDependencies
        container.register(holderDef, (CompiledFactory<LazyHolder>) deps -> new LazyHolder());

        // Wire the factories so the wired fast path is used deterministically.
        container.wireFactories();

        LazyHolder holder = container.resolve("lazyHolder");

        assertNotNull(holder, "Bean should be resolved by name");
        assertNotNull(holder.dependency, "Deferred @Lazy dependency must be injected");
        assertSame(container.resolve(HolderDependency.class), holder.dependency,
                "Deferred dependency must be the container-managed singleton");
    }

    public static class LazyHolder {

        @io.github.yasmramos.warmup.annotations.Inject
        @io.github.yasmramos.warmup.annotations.Lazy
        HolderDependency dependency;
    }

    public static class HolderDependency {
    }
}
