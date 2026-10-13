package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.condition.Condition;
import io.github.yasmramos.warmup.core.condition.ConditionContext;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test that {@code @Profile} and {@code @Conditional} constraints are evaluated on
 * every registration route and not only on the ServiceLoader discovery path.
 *
 * <p>Before the fix, {@code shouldRegisterBean} was only consulted in
 * {@code discoverAndRegisterFactories}; beans registered programmatically via
 * {@code register()}, {@code registerDynamic()} or the supplier shortcut were registered
 * unconditionally even when their profile or condition did not match.</p>
 */
class ConditionalRegistrationRoutesTest {

    public static final class AlwaysFalseCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return false;
        }
    }

    public static final class AlwaysTrueCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return true;
        }
    }

    private static BeanDefinition<String> definition(String name, String[] profiles, String[] conditions) {
        return new BeanDefinition<>(String.class, name, Scope.SINGLETON, LifecycleCallbacks.empty(), false,
                new Object[0], profiles, conditions);
    }

    @Test
    void registerHonorsActiveProfiles() {
        try (Warmup warmup = Warmup.builder().profiles("prod").build()) {
            warmup.register(definition("prodBean", new String[]{"prod"}, new String[0]), deps -> "p");
            warmup.register(definition("devBean", new String[]{"dev"}, new String[0]), deps -> "d");

            assertTrue(warmup.contains("prodBean"));
            assertFalse(warmup.contains("devBean"),
                    "Bean with non-matching @Profile must be skipped when registered via register()");
        }
    }

    @Test
    void registerHonorsConditions() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.register(definition("condFalse",
                    new String[0], new String[]{AlwaysFalseCondition.class.getName()}), deps -> "x");
            warmup.register(definition("condTrue",
                    new String[0], new String[]{AlwaysTrueCondition.class.getName()}), deps -> "y");

            assertFalse(warmup.contains("condFalse"),
                    "Bean with failing @Conditional must be skipped when registered via register()");
            assertTrue(warmup.contains("condTrue"));
        }
    }

    @Test
    void registerDynamicHonorsActiveProfiles() {
        try (Warmup warmup = Warmup.builder().profiles("prod").build()) {
            warmup.registerDynamic(definition("prodBean", new String[]{"prod"}, new String[0]));
            warmup.registerDynamic(definition("devBean", new String[]{"dev"}, new String[0]));

            assertTrue(warmup.contains("prodBean"));
            assertFalse(warmup.contains("devBean"),
                    "Bean with non-matching @Profile must be skipped when registered via registerDynamic()");
        }
    }

    @Test
    void registerDynamicHonorsConditions() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition("condFalse",
                    new String[0], new String[]{AlwaysFalseCondition.class.getName()}));
            warmup.registerDynamic(definition("condTrue",
                    new String[0], new String[]{AlwaysTrueCondition.class.getName()}));

            assertFalse(warmup.contains("condFalse"),
                    "Bean with failing @Conditional must be skipped when registered via registerDynamic()");
            assertTrue(warmup.contains("condTrue"));
        }
    }

    @Test
    void supplierRegistrationStillWorksWithoutConstraints() {
        try (Warmup warmup = Warmup.builder().build()) {
            // No profiles/conditions on this route: registration must be unaffected.
            warmup.register("plain", String.class, () -> "value", Scope.SINGLETON);

            assertTrue(warmup.contains("plain"));
            assertEquals("value", warmup.get(String.class));
        }
    }
}