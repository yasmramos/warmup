package io.github.yasmramos.warmup.processor.integration.p0;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.yasmramos.warmup.core.config.PropertyResolver;
import io.github.yasmramos.warmup.core.config.SystemPropertiesPropertySource;
import io.github.yasmramos.warmup.core.container.Container;
import io.github.yasmramos.warmup.core.container.ContainerConfig;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.core.jit.FactoryRegistrar;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.processor.ProcessorTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression for accessor classes: private {@code @Inject}/{@code @Value}
 * fields and private {@code @Inject} methods must be injected.
 *
 * <p>The JVM forbids one generated class from writing another class's private
 * members directly, so the processor emits a {@code Bean$$WarmupAccessor} that
 * performs the write reflectively. Before accessor support the processor
 * rejected private fields outright, forcing every injection point to be
 * public.</p>
 */
class PrivateFieldInjectionTest {

    private static final String NAME_KEY = "warmup.test.p0.private.name";
    private static final String COUNT_KEY = "warmup.test.p0.private.count";

    private final ProcessorTestHarness harness = new ProcessorTestHarness();

    @AfterEach
    void clearProperties() {
        System.clearProperty(NAME_KEY);
        System.clearProperty(COUNT_KEY);
    }

    @Test
    void privateInjectAndValueFields_injectedThroughAccessor() throws Exception {
        System.setProperty(NAME_KEY, "private-name");
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.PrivDep",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton class PrivDep {}"),
            JavaFileObjects.forSourceLines("test.PrivateBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class PrivateBean {",
                "  @Inject private PrivDep dep;",
                "  @Value(\"${" + NAME_KEY + "}\") private String name;",
                "  public PrivateBean() {}",
                "  public PrivDep dep() { return dep; }",
                "  public String name() { return name; }",
                "}"));

        assertTrue(compilation.status() == Compilation.Status.SUCCESS,
                "Compilation must succeed, errors: " + compilation.errors());

        Container container = newContainer(compilation);
        Object bean = container.resolve("PrivateBean");

        assertNotNull(bean.getClass().getMethod("dep").invoke(bean),
                "private @Inject field must be populated");
        assertEquals("private-name", bean.getClass().getMethod("name").invoke(bean),
                "private @Value field must be populated");

        container.shutdown();
    }

    @Test
    void privateInjectMethod_invokedThroughAccessor() throws Exception {
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.PrivDep",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton class PrivDep {}"),
            JavaFileObjects.forSourceLines("test.PrivateSetterBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class PrivateSetterBean {",
                "  private PrivDep dep;",
                "  public PrivateSetterBean() {}",
                "  @Inject private void setDep(PrivDep dep) { this.dep = dep; }",
                "  public PrivDep dep() { return dep; }",
                "}"));

        assertTrue(compilation.status() == Compilation.Status.SUCCESS,
                "Compilation must succeed, errors: " + compilation.errors());

        Container container = newContainer(compilation);
        Object bean = container.resolve("PrivateSetterBean");

        assertNotNull(bean.getClass().getMethod("dep").invoke(bean),
                "private @Inject method must be invoked");

        container.shutdown();
    }

    @Test
    void privatePrimitiveValueField_boxedThroughAccessor() throws Exception {
        System.setProperty(COUNT_KEY, "42");
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.PrimitiveBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class PrimitiveBean {",
                "  @Value(\"${" + COUNT_KEY + "}\") private int count;",
                "  public PrimitiveBean() {}",
                "  public int count() { return count; }",
                "}"));

        assertTrue(compilation.status() == Compilation.Status.SUCCESS,
                "Compilation must succeed, errors: " + compilation.errors());

        Container container = newContainer(compilation);
        Object bean = container.resolve("PrimitiveBean");
        assertEquals(42, bean.getClass().getMethod("count").invoke(bean),
                "private primitive @Value field must be boxed and injected");

        container.shutdown();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Container newContainer(Compilation compilation) throws Exception {
        ProcessorTestHarness.CompiledClassLoader classLoader = harness.loaderFor(compilation);
        FactoryRegistrar registrar = (FactoryRegistrar) classLoader
                .loadClass("test.GeneratedFactoryRegistrar").getDeclaredConstructor().newInstance();

        PropertyResolver propertyResolver =
                new PropertyResolver().addPropertySource(new SystemPropertiesPropertySource());
        Container container = new Container(
                ContainerConfig.builder().autoDiscoverFactories(false).propertyResolver(propertyResolver).build(),
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
