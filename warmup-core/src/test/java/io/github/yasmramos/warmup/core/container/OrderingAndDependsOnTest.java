package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.annotations.DependsOn;
import io.github.yasmramos.warmup.annotations.Order;
import io.github.yasmramos.warmup.core.Ordered;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code @Order} collection ordering and {@code @DependsOn} initialisation ordering.
 */
class OrderingAndDependsOnTest {

    interface Step {
        String name();
    }

    @Order(2)
    static class SecondStep implements Step {
        @Override
        public String name() {
            return "second";
        }
    }

    @Order(1)
    static class FirstStep implements Step {
        @Override
        public String name() {
            return "first";
        }
    }

    @Order(Order.HIGHEST_PRECEDENCE)
    static class HighestStep implements Step {
        @Override
        public String name() {
            return "highest";
        }
    }

    static class UnorderedStep implements Step {
        @Override
        public String name() {
            return "unordered";
        }
    }

    static class ProgrammaticStep implements Step, Ordered {
        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public String name() {
            return "programmatic";
        }
    }

    private static final List<String> creationLog = new ArrayList<>();

    static class Tracker {
        Tracker() {
            creationLog.add("tracker");
        }
    }

    @DependsOn("tracker")
    static class Client {
        Client() {
            creationLog.add("client");
        }
    }

    @DependsOn("cycleB")
    static class CycleA {
    }

    @DependsOn("cycleA")
    static class CycleB {
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinition<?> definition(Class<?> type, String name) {
        return new BeanDefinition(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(), false, new Object[0]);
    }

    private static Warmup warmup() {
        return Warmup.builder().build();
    }

    @Test
    void orderAnnotationSortsCollectionInjection() {
        try (Warmup warmup = warmup()) {
            // Register out of order on purpose; @Order must reorder them.
            warmup.registerDynamic(definition(SecondStep.class, "second"));
            warmup.registerDynamic(definition(FirstStep.class, "first"));
            warmup.registerDynamic(definition(HighestStep.class, "highest"));

            List<Step> steps = warmup.resolveAll(Step.class);
            assertEquals(List.of("highest", "first", "second"),
                    steps.stream().map(Step::name).toList(),
                    "Beans must be ordered by @Order, lowest value first");
        }
    }

    @Test
    void unorderedBeansKeepRegistrationOrderAfterOrderedOnes() {
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(UnorderedStep.class, "unordered"));
            warmup.registerDynamic(definition(FirstStep.class, "first"));
            warmup.registerDynamic(definition(SecondStep.class, "second"));

            List<Step> steps = warmup.resolveAll(Step.class);
            assertEquals(List.of("first", "second", "unordered"),
                    steps.stream().map(Step::name).toList(),
                    "Ordered beans come first; unordered beans keep registration order");
        }
    }

    @Test
    void orderedInterfaceTakesPrecedenceOverAnnotation() {
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(FirstStep.class, "first"));
            warmup.registerDynamic(definition(ProgrammaticStep.class, "programmatic"));

            List<Step> steps = warmup.resolveAll(Step.class);
            assertEquals(List.of("programmatic", "first"),
                    steps.stream().map(Step::name).toList(),
                    "The Ordered interface (order 0) must come before @Order(1)");
        }
    }

    @Test
    void dependsOnCreatesTargetBeforeDependent() {
        creationLog.clear();
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(Tracker.class, "tracker"));
            warmup.registerDynamic(definition(Client.class, "client"));

            warmup.get(Client.class);

            assertEquals(List.of("tracker", "client"), creationLog,
                    "@DependsOn(\"tracker\") must create tracker before client");
        }
    }

    @Test
    void dependsOnUnknownBeanFailsFast() {
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(Client.class, "client"));
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> warmup.get(Client.class));
            assertTrue(error.getMessage().contains("tracker"),
                    "Error should name the missing @DependsOn bean: " + error.getMessage());
        }
    }

    @Test
    void dependsOnCycleIsDetected() {
        try (Warmup warmup = warmup()) {
            warmup.registerDynamic(definition(CycleA.class, "cycleA"));
            warmup.registerDynamic(definition(CycleB.class, "cycleB"));
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> warmup.get(CycleA.class));
            assertTrue(error.getMessage().toLowerCase().contains("circular"),
                    "Error should report the @DependsOn cycle: " + error.getMessage());
        }
    }
}
