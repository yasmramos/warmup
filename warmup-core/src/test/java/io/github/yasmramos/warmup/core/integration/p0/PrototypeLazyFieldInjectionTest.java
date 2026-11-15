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
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * P0 regression (bug 7.1.2): a prototype bean with a deferred ({@code @Lazy}
 * field) dependency must have that dependency injected on every creation,
 * just like a singleton does. Before the fix the deferred dependency was
 * silently skipped on the prototype fast paths, leaving the field {@code null}.
 */
class PrototypeLazyFieldInjectionTest {

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
    void prototype_withLazyField_injectsDependency() {
        BeanDefinition<HolderDependency> dependencyDef =
                new BeanDefinition<>(HolderDependency.class, "holderDependency");
        container.register(dependencyDef, (CompiledFactory<HolderDependency>) deps -> new HolderDependency());

        BeanDefinition<LazyPrototype> prototypeDef = new BeanDefinition<>(
                LazyPrototype.class,
                "lazyPrototype",
                Scope.PROTOTYPE,
                LifecycleCallbacks.empty(),
                false,
                new Object[]{"holderDependency"},
                new String[0],
                new String[0],
                "",
                new boolean[]{true},   // deferredDependencies
                new boolean[]{true});  // fieldOrSetterDependencies
        container.register(prototypeDef, (CompiledFactory<LazyPrototype>) deps -> new LazyPrototype());

        container.wireFactories();

        LazyPrototype first = container.resolve("lazyPrototype");
        LazyPrototype second = container.resolve("lazyPrototype");

        assertNotSame(first, second, "Prototype scope must create a new instance per resolution");
        assertNotNull(first.dependency, "Deferred @Lazy dependency must be injected on first prototype");
        assertNotNull(second.dependency, "Deferred @Lazy dependency must be injected on second prototype");
    }

    public static class LazyPrototype {

        @io.github.yasmramos.warmup.annotations.Inject
        @io.github.yasmramos.warmup.annotations.Lazy
        HolderDependency dependency;
    }

    public static class HolderDependency {
    }
}
