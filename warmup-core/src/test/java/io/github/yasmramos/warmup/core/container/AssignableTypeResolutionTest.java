package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.exception.AmbiguousBeanException;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for type-indexing by assignability.
 *
 * <p>Before the fix the registry indexed beans under their exact declared type only, so
 * {@code resolveAll(PaymentProcessor.class)} never found beans registered as
 * {@code CardProcessor.class} and interface-based lookup / collection injection were broken.
 * Beans are now also indexed under every implemented interface and superclass.</p>
 */
class AssignableTypeResolutionTest {

    interface Greeting {
    }

    static class HelloGreeting implements Greeting {
    }

    static class HolaGreeting implements Greeting {
    }

    static class ParentGreeting implements Greeting {
    }

    static class ChildGreeting extends ParentGreeting {
    }

    private static BeanDefinition<?> definition(Class<?> type, String name) {
        return new BeanDefinition(type, name);
    }

    private static BeanDefinition<?> primaryDefinition(Class<?> type, String name) {
        return new BeanDefinition(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(), true, new Object[0]);
    }

    @Test
    void resolveAllFindsBeansByImplementedInterface() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeting.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeting.class, "hola"));

            List<Greeting> all = warmup.resolveAll(Greeting.class);
            assertEquals(2, all.size());
        }
    }

    @Test
    void resolveAllAsMapFindsBeansByInterfaceKeyedByName() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeting.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeting.class, "hola"));

            Map<String, Greeting> byName = warmup.resolveAllAsMap(Greeting.class);
            assertEquals(2, byName.size());
            assertTrue(byName.containsKey("hello"));
            assertTrue(byName.containsKey("hola"));
        }
    }

    @Test
    void resolveAllFindsBeansBySuperclass() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(ChildGreeting.class, "child"));
            warmup.registerDynamic(definition(ParentGreeting.class, "parent"));

            assertEquals(2, warmup.resolveAll(ParentGreeting.class).size());
        }
    }

    @Test
    void getByInterfaceThrowsWhenAmbiguousWithoutPrimary() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeting.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeting.class, "hola"));

            assertThrows(AmbiguousBeanException.class, () -> warmup.get(Greeting.class));
        }
    }

    @Test
    void getByInterfaceResolvesSingleImplementation() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeting.class, "hello"));

            Greeting greeting = warmup.get(Greeting.class);
            assertTrue(greeting instanceof HelloGreeting);
        }
    }

    @Test
    void getByInterfacePrefersPrimaryImplementation() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(primaryDefinition(HelloGreeting.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeting.class, "hola"));

            Greeting greeting = warmup.get(Greeting.class);
            assertTrue(greeting instanceof HelloGreeting,
                    "The @Primary bean should win when several beans implement the interface");
        }
    }
}