package io.github.yasmramos.warmup.core.integration.p0;

import io.github.yasmramos.warmup.asm.AsmJITCompiler;
import io.github.yasmramos.warmup.core.container.Container;
import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P0 regression (bug 7.1.1): resolving an unregistered type caches a
 * {@code NOT_FOUND} sentinel in the {@code ClassValue}. Registering that type
 * afterwards must invalidate the cache, otherwise a later resolution keeps
 * failing with "Bean not found" even though the bean now exists.
 */
class RegisterAfterFailedResolveTest {

    private Container container;

    @BeforeEach
    void setUp() {
        container = new Container(
                ContainerConfig.builder().autoDiscoverFactories(false).build(),
                new AsmJITCompiler());
    }

    @AfterEach
    void tearDown() {
        container.shutdown();
    }

    @Test
    void register_afterFailedResolve_findsBean() {
        // Cache the NOT_FOUND sentinel for this type.
        assertThrows(IllegalStateException.class, () -> container.resolve(LateBean.class),
                "Resolving an unregistered type must fail");

        // Register the bean afterwards.
        BeanDefinition<LateBean> definition = new BeanDefinition<>(LateBean.class, "lateBean", Scope.SINGLETON);
        container.register(definition, (CompiledFactory<LateBean>) deps -> new LateBean());
        container.wireFactories();

        LateBean resolved = container.resolve(LateBean.class);

        assertNotNull(resolved, "register() must invalidate the cached NOT_FOUND so the bean is found");
    }

    @Test
    void registerDynamic_afterFailedResolve_findsBean() {
        assertThrows(IllegalStateException.class, () -> container.resolve(DynamicLateBean.class),
                "Resolving an unregistered type must fail");

        BeanDefinition<DynamicLateBean> definition =
                new BeanDefinition<>(DynamicLateBean.class, "dynamicLateBean", Scope.PROTOTYPE);
        container.registerDynamic(definition);

        DynamicLateBean resolved = container.resolve(DynamicLateBean.class);

        assertNotNull(resolved, "registerDynamic() must invalidate the cached NOT_FOUND so the bean is found");
    }

    public static class LateBean {
    }

    public static class DynamicLateBean {
    }
}
