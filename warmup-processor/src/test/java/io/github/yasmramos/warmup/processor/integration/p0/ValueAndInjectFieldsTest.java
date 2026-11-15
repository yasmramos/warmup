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
 * P0 regression (bug 7.2.1): a {@code @Value} field and an {@code @Inject}
 * field in the same bean must be injected into their own targets.
 *
 * <p>The compile-time processor used to skip {@code @Value} fields entirely, so
 * the value field stayed {@code null} and the inject field received the
 * configuration value (or vice versa) depending on declaration order. Both
 * targets must receive the correct dependency regardless of order.</p>
 */
class ValueAndInjectFieldsTest {

    private static final String NAME_KEY = "warmup.test.p0.name";

    private final ProcessorTestHarness harness = new ProcessorTestHarness();

    @AfterEach
    void clearProperties() {
        System.clearProperty(NAME_KEY);
    }

    @Test
    void valueAndInjectFields_injectedInCorrectTargets() throws Exception {
        System.setProperty(NAME_KEY, "configured-name");
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.Deps",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton class DepA {}"),
            JavaFileObjects.forSourceLines("test.ValueFirstBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class ValueFirstBean {",
                "  @Value(\"${" + NAME_KEY + "}\") String configuredName;",
                "  @Inject DepA injected;",
                "  public ValueFirstBean() {}",
                "  public String configuredName() { return configuredName; }",
                "  public DepA injected() { return injected; }",
                "}"),
            JavaFileObjects.forSourceLines("test.InjectFirstBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class InjectFirstBean {",
                "  @Inject DepA injected;",
                "  @Value(\"${" + NAME_KEY + "}\") String configuredName;",
                "  public InjectFirstBean() {}",
                "  public DepA injected() { return injected; }",
                "  public String configuredName() { return configuredName; }",
                "}"));

        assertTrue(compilation.status() == Compilation.Status.SUCCESS,
                "Compilation must succeed, errors: " + compilation.errors());

        Container container = newContainer(compilation);

        Object valueFirst = container.resolve("ValueFirstBean");
        assertEquals("configured-name", valueFirst.getClass().getMethod("configuredName").invoke(valueFirst),
                "@Value field declared before @Inject must hold the configuration value");
        assertNotNull(valueFirst.getClass().getMethod("injected").invoke(valueFirst),
                "@Inject field declared after @Value must hold the bean");

        Object injectFirst = container.resolve("InjectFirstBean");
        assertNotNull(injectFirst.getClass().getMethod("injected").invoke(injectFirst),
                "@Inject field declared before @Value must hold the bean");
        assertEquals("configured-name", injectFirst.getClass().getMethod("configuredName").invoke(injectFirst),
                "@Value field declared after @Inject must hold the configuration value");

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
