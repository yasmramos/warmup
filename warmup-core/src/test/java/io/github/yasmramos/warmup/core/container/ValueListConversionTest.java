package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.config.SystemPropertiesPropertySource;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.registry.ValueDependency;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@code @Value} resolution.
 *
 * <p>The annotation processor records a {@code @Value} injection point as the raw placeholder
 * expression (e.g. {@code ${app.ports}}). The container used to treat that string as a bean
 * name and failed with {@code Bean not found: ${app.ports}}. Values are now resolved through
 * the {@code PropertyResolver} using the declared injection type, which additionally enables
 * string&rarr;collection conversion for {@code @Value List<String>}.</p>
 *
 * <p>These tests drive the dynamic registration path, which JIT-compiles the factory from the
 * declared constructor parameter types (the same metadata the compile-time processor emits).</p>
 */
class ValueListConversionTest {

    private static final String PORTS_KEY = "warmup.test.ports";
    private static final String TAGS_KEY = "warmup.test.tags";
    private static final String NAME_KEY = "warmup.test.name";
    private static final String PORT_KEY = "warmup.test.port";

    static class ListConfig {
        final List<String> ports;

        ListConfig(List<String> ports) {
            this.ports = ports;
        }
    }

    static class SetConfig {
        final Set<String> tags;

        SetConfig(Set<String> tags) {
            this.tags = tags;
        }
    }

    static class LinkedListConfig {
        final java.util.LinkedList<String> values;

        LinkedListConfig(java.util.LinkedList<String> values) {
            this.values = values;
        }
    }

    static class ScalarConfig {
        final String name;
        final int port;

        ScalarConfig(String name, int port) {
            this.name = name;
            this.port = port;
        }
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty(PORTS_KEY);
        System.clearProperty(TAGS_KEY);
        System.clearProperty(NAME_KEY);
        System.clearProperty(PORT_KEY);
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinition<?> definition(Class<?> type, String name, Object... dependencies) {
        return new BeanDefinition(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(),
                false, dependencies);
    }

    private static Warmup warmup() {
        return Warmup.builder()
                .propertySource(new SystemPropertiesPropertySource())
                .build();
    }

    @Test
    void valueOnListIsSplitOnCommas() {
        System.setProperty(PORTS_KEY, "8080, 8081 ,8082");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(ListConfig.class, "config", "${" + PORTS_KEY + "}"));

            ListConfig config = warmup.get(ListConfig.class);
            assertNotNull(config.ports);
            assertEquals(List.of("8080", "8081", "8082"), config.ports,
                    "Comma separated value must be split and trimmed into a List<String>");
        }
    }

    @Test
    void valueOnSetIsMaterialisedAsSet() {
        System.setProperty(TAGS_KEY, "a,b,a");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(SetConfig.class, "config", "${" + TAGS_KEY + "}"));

            SetConfig config = warmup.get(SetConfig.class);
            assertNotNull(config.tags);
            assertEquals(Set.of("a", "b"), config.tags,
                    "Set<String> must deduplicate converted elements");
        }
    }

    @Test
    void valueOnConcreteListTypeIsMaterialisedAsThatType() {
        System.setProperty(PORTS_KEY, "1,2");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(LinkedListConfig.class, "config", "${" + PORTS_KEY + "}"));

            LinkedListConfig config = warmup.get(LinkedListConfig.class);
            assertNotNull(config.values);
            assertTrue(config.values instanceof java.util.LinkedList,
                    "Declared concrete LinkedList<String> must stay a LinkedList");
            assertEquals(List.of("1", "2"), config.values);
        }
    }

    @Test
    void scalarValueStillResolves() {
        System.setProperty(NAME_KEY, "warmup");
        System.setProperty(PORT_KEY, "9090");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(ScalarConfig.class, "config",
                    "${" + NAME_KEY + "}", "${" + PORT_KEY + "}"));

            ScalarConfig config = warmup.get(ScalarConfig.class);
            assertEquals("warmup", config.name);
            assertEquals(9090, config.port);
        }
    }

    @Test
    void explicitValueDependencyObjectSupportsCollections() {
        System.setProperty(PORTS_KEY, "x,y");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(ListConfig.class, "config",
                    new ValueDependency("${" + PORTS_KEY + "}", List.class)));

            ListConfig config = warmup.get(ListConfig.class);
            assertEquals(List.of("x", "y"), config.ports,
                    "A hand built ValueDependency with a collection target type must convert");
        }
    }
}
