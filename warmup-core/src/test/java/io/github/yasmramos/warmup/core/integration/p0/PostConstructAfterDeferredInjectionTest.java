package io.github.yasmramos.warmup.core.integration.p0;

import io.github.yasmramos.warmup.annotations.Inject;
import io.github.yasmramos.warmup.annotations.Lazy;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression (bug 7.1.3): when a bean is resolved by type and carries a
 * deferred ({@code @Lazy}) dependency, its {@code @PostConstruct} callback must
 * run AFTER the deferred dependency has been injected, so the callback sees a
 * fully initialized bean.
 */
class PostConstructAfterDeferredInjectionTest {

    private Warmup warmup;

    @BeforeEach
    void setUp() {
        warmup = Warmup.builder().registerShutdownHook(false).build();
    }

    @AfterEach
    void tearDown() {
        warmup.shutdown();
    }

    @Test
    void postConstruct_runsAfterDeferredInjection() {
        BeanDefinition<LazyDependency> dependencyDef =
                new BeanDefinition<>(LazyDependency.class, "lazyDependency", Scope.SINGLETON,
                        LifecycleCallbacks.empty(), false, new Object[0]);
        warmup.registerDynamic(dependencyDef);

        @SuppressWarnings("unchecked")
        LifecycleCallbacks<Holder> lifecycle = (LifecycleCallbacks<Holder>) (LifecycleCallbacks<?>)
                LifecycleCallbacks.initOnly(instance -> ((Holder) instance).init());

        BeanDefinition<Holder> holderDef = new BeanDefinition<>(
                Holder.class, "holder", Scope.SINGLETON, lifecycle, false,
                new Object[]{"lazyDependency"},
                new String[0],
                new String[0],
                "",
                new boolean[]{true},   // deferredDependencies
                new boolean[]{true});  // fieldOrSetterDependencies
        warmup.registerDynamic(holderDef);

        Holder holder = warmup.get(Holder.class);

        assertTrue(holder.postConstructRan,
                "@PostConstruct must run when the bean is resolved by type");
        assertNotNull(holder.dependency,
                "@Lazy dependency must be injected");
        assertTrue(holder.dependencyPresentInPostConstruct,
                "@PostConstruct must observe the injected @Lazy dependency");
    }

    public static class Holder {

        @Inject
        @Lazy
        LazyDependency dependency;

        boolean postConstructRan;
        boolean dependencyPresentInPostConstruct;

        void init() {
            postConstructRan = true;
            dependencyPresentInPostConstruct = dependency != null;
        }
    }

    public static class LazyDependency {
    }
}
