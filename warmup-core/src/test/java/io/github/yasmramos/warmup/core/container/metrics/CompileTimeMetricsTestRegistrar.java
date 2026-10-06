package io.github.yasmramos.warmup.core.container.metrics;

import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.jit.FactoryRegistrar;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;

import java.util.function.BiConsumer;

/**
 * Test-scoped {@link FactoryRegistrar} registered through
 * {@code META-INF/services/io.github.yasmramos.warmup.core.jit.FactoryRegistrar}.
 *
 * <p>Its purpose is to reproduce the real compile-time registration flow: a bean whose
 * factory is supplied up-front and therefore gets <em>wired</em> during container startup,
 * rather than falling back to JIT or reflection. Wired compile-time factories bypass the
 * bean-creation method that normally records the COMPILE_TIME path, which is exactly the
 * path that used to go missing from {@code ContainerMetrics}.</p>
 *
 * <p>Declared as a separate registrar (rather than reusing an existing one) so the bean it
 * contributes is easy to identify and cannot collide with other test fixtures.</p>
 */
public final class CompileTimeMetricsTestRegistrar implements FactoryRegistrar {

    public static final String BEAN_NAME = "compileTimeMetricsTestBean";

    @Override
    public void registerAll(BiConsumer<BeanDefinition<?>, CompiledFactory<?>> sink) {
        BeanDefinition<CompileTimeMetricsTestBean> definition = new BeanDefinition<>(
                CompileTimeMetricsTestBean.class,
                BEAN_NAME,
                Scope.SINGLETON,
                LifecycleCallbacks.empty(),
                false,
                new Object[0]);

        sink.accept(definition, new CompileTimeMetricsTestBean.Factory());
    }
}