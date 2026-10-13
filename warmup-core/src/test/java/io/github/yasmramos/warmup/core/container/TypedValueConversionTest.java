package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.config.SystemPropertiesPropertySource;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.registry.TypedValueDependency;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for typed {@code @Value} collection conversion.
 *
 * <p>A {@code @Value List<Integer>} injection point cannot rely on reflection: the element type
 * is erased. The annotation processor therefore records it as a {@link TypedValueDependency}
 * marker carrying the declared raw type and the element type. These tests drive the container
 * marker parsing directly (the same marker the processor emits) and assert that every split
 * element is converted to the declared element type.</p>
 */
class TypedValueConversionTest {

    private static final String PORTS_KEY = "warmup.test.typed.ports";
    private static final String IDS_KEY = "warmup.test.typed.ids";
    private static final String RATIOS_KEY = "warmup.test.typed.ratios";

    enum Mode { FAST, SLOW }

    static class IntListConfig {
        final List<Integer> ports;

        IntListConfig(List<Integer> ports) {
            this.ports = ports;
        }
    }

    static class LongSetConfig {
        final Set<Long> ids;

        LongSetConfig(Set<Long> ids) {
            this.ids = ids;
        }
    }

    static class DoubleListConfig {
        final List<Double> ratios;

        DoubleListConfig(List<Double> ratios) {
            this.ratios = ratios;
        }
    }

    static class EnumListConfig {
        final List<Mode> modes;

        EnumListConfig(List<Mode> modes) {
            this.modes = modes;
        }
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty(PORTS_KEY);
        System.clearProperty(IDS_KEY);
        System.clearProperty(RATIOS_KEY);
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

    private static String marker(String declaredFqn, String elementFqn, String key) {
        return TypedValueDependency.marker(declaredFqn, elementFqn, "${" + key + "}");
    }

    @Test
    void listOfIntegerConvertsEveryElement() {
        System.setProperty(PORTS_KEY, "8080, 8081 ,8082");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(IntListConfig.class, "config",
                    marker("java.util.List", "java.lang.Integer", PORTS_KEY)));

            IntListConfig config = warmup.get(IntListConfig.class);
            assertNotNull(config.ports);
            assertEquals(List.of(8080, 8081, 8082), config.ports,
                    "Each split element must be converted to Integer");
        }
    }

    @Test
    void setOfLongConvertsAndDeduplicates() {
        System.setProperty(IDS_KEY, "10,20,10");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(LongSetConfig.class, "config",
                    marker("java.util.Set", "java.lang.Long", IDS_KEY)));

            LongSetConfig config = warmup.get(LongSetConfig.class);
            assertNotNull(config.ids);
            assertEquals(Set.of(10L, 20L), config.ids,
                    "Converted Long elements must deduplicate in a Set");
        }
    }

    @Test
    void listOfDoubleConvertsEveryElement() {
        System.setProperty(RATIOS_KEY, "0.5,1.25");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(DoubleListConfig.class, "config",
                    marker("java.util.List", "java.lang.Double", RATIOS_KEY)));

            DoubleListConfig config = warmup.get(DoubleListConfig.class);
            assertEquals(List.of(0.5, 1.25), config.ratios,
                    "Each split element must be converted to Double");
        }
    }

    @Test
    void listOfEnumConvertsToEnumConstants() {
        System.setProperty(RATIOS_KEY, "FAST,SLOW");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(EnumListConfig.class, "config",
                    marker("java.util.List", Mode.class.getName(), RATIOS_KEY)));

            EnumListConfig config = warmup.get(EnumListConfig.class);
            assertEquals(List.of(Mode.FAST, Mode.SLOW), config.modes,
                    "Each split element must be resolved to the enum constant");
        }
    }

    @Test
    void invalidElementValueFailsFast() {
        System.setProperty(PORTS_KEY, "not-a-number");
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(IntListConfig.class, "config",
                    marker("java.util.List", "java.lang.Integer", PORTS_KEY)));

            assertThrows(NumberFormatException.class, () -> warmup.get(IntListConfig.class),
                    "A malformed element must surface a conversion error");
        }
    }
}
