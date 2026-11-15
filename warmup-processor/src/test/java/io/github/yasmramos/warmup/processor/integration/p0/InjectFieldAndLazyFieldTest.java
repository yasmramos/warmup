package io.github.yasmramos.warmup.processor.integration.p0;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.yasmramos.warmup.core.container.Container;
import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.jit.FactoryRegistrar;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.processor.ProcessorTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression (bug 7.2.2): a bean mixing a {@code @Lazy} field with a plain
 * {@code @Inject} field must receive both dependencies, whichever order they are
 * declared in.
 *
 * <p>The generated factory indexes its wired slots by the compacted
 * non-deferred position, while the container used to hand the factory the full
 * declaration-order array. When the lazy field came first the plain field
 * received the lazy dependency's factory, producing a
 * {@code ClassCastException}; when the plain field came first the lazy field
 * stayed {@code null}.</p>
 */
class InjectFieldAndLazyFieldTest {

    private final ProcessorTestHarness harness = new ProcessorTestHarness();

    @Test
    void injectFieldAndLazyField_bothInjectedRegardlessOfOrder() throws Exception {
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.DepA",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton class DepA {}"),
            JavaFileObjects.forSourceLines("test.DepB",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton class DepB {}"),
            JavaFileObjects.forSourceLines("test.LazyThenPlainBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class LazyThenPlainBean {",
                "  @Inject @Lazy public DepA lazyA;",
                "  @Inject public DepB plainB;",
                "  public LazyThenPlainBean() {}",
                "}"),
            JavaFileObjects.forSourceLines("test.PlainThenLazyBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class PlainThenLazyBean {",
                "  @Inject public DepB plainB;",
                "  @Inject @Lazy public DepA lazyA;",
                "  public PlainThenLazyBean() {}",
                "}"));

        assertTrue(compilation.status() == Compilation.Status.SUCCESS,
                "Compilation must succeed, errors: " + compilation.errors());

        Container container = newContainer(compilation);

        Object lazyThenPlain = container.resolve("LazyThenPlainBean");
        assertNotNull(field(lazyThenPlain, "lazyA"),
                "@Lazy field declared before @Inject must be injected");
        assertNotNull(field(lazyThenPlain, "plainB"),
                "@Inject field declared after @Lazy must be injected");

        Object plainThenLazy = container.resolve("PlainThenLazyBean");
        assertNotNull(field(plainThenLazy, "plainB"),
                "@Inject field declared before @Lazy must be injected");
        assertNotNull(field(plainThenLazy, "lazyA"),
                "@Lazy field declared after @Inject must be injected");

        assertSame(container.resolve("DepA"), field(lazyThenPlain, "lazyA"),
                "Deferred dependency must be the container-managed singleton");
        assertSame(container.resolve("DepB"), field(lazyThenPlain, "plainB"),
                "Plain dependency must be the container-managed singleton");

        container.shutdown();
    }

    private static Object field(Object bean, String name) throws Exception {
        return bean.getClass().getField(name).get(bean);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Container newContainer(Compilation compilation) throws Exception {
        ProcessorTestHarness.CompiledClassLoader classLoader = harness.loaderFor(compilation);
        FactoryRegistrar registrar = (FactoryRegistrar) classLoader
                .loadClass("test.GeneratedFactoryRegistrar").getDeclaredConstructor().newInstance();
        Container container = new Container(
                ContainerConfig.builder().autoDiscoverFactories(false).build(),
                new io.github.yasmramos.warmup.asm.AsmJITCompiler());
        registrar.registerAll((definition, factory) -> registerInto(container, definition, factory));
        container.wireFactories();
        return container;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerInto(Container container, BeanDefinition definition, CompiledFactory factory) {
        container.register(definition, factory);
    }
}
