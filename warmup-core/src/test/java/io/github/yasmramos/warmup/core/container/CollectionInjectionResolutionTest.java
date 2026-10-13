package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.registry.CollectionDependency;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for collection injection: an injection point such as {@code List<T>},
 * {@code Deque<T>} or {@code Map<String, T>} must resolve <em>every</em> bean of the
 * element type, keyed by bean name for maps.
 *
 * <p>Before the fix, the annotation processor recorded the raw name {@code PaymentProcessor>}
 * (the trailing bracket survived simple-name extraction) and resolution failed at runtime with
 * {@code Bean not found: PaymentProcessor>}. The dependency name is now a
 * {@link CollectionDependency} marker; this test builds those markers by hand to exercise the
 * container side of the contract without needing a full compile-time factory pipeline.</p>
 */
class CollectionInjectionResolutionTest {

    interface Greeting {
        String greet();
    }

    static class HelloGreeting implements Greeting {
        @Override
        public String greet() {
            return "hello";
        }
    }

    static class HolaGreeting implements Greeting {
        @Override
        public String greet() {
            return "hola";
        }
    }

    static class GreetingRegistry {
        final List<Greeting> list;
        final java.util.Deque<Greeting> deque;
        final Map<String, Greeting> byName;

        GreetingRegistry(List<Greeting> list, java.util.Deque<Greeting> deque, Map<String, Greeting> byName) {
            this.list = list;
            this.deque = deque;
            this.byName = byName;
        }
    }

    static class HolderOfSet {
        final Set<Greeting> values;

        HolderOfSet(Set<Greeting> values) {
            this.values = values;
        }
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinition<?> definition(Class<?> type, String name, Object... dependencies) {
        return new BeanDefinition(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(), false, dependencies);
    }

    @Test
    void listDequeAndMapMarkersResolveEveryBean() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeting.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeting.class, "hola"));

            warmup.registerDynamic(definition(
                    GreetingRegistry.class,
                    "registry",
                    CollectionDependency.marker(CollectionDependency.Kind.LIST, List.class.getName(), Greeting.class.getName()),
                    CollectionDependency.marker(CollectionDependency.Kind.COLLECTION, java.util.Deque.class.getName(), Greeting.class.getName()),
                    CollectionDependency.marker(CollectionDependency.Kind.MAP, Map.class.getName(), Greeting.class.getName())));

            GreetingRegistry registry = warmup.get(GreetingRegistry.class);
            assertNotNull(registry);
            assertEquals(2, registry.list.size(),
                    "List<Greeting> should contain both implementations");
            assertTrue(registry.list.stream().map(Greeting::greet).anyMatch("hello"::equals));
            assertTrue(registry.list.stream().map(Greeting::greet).anyMatch("hola"::equals));
            assertEquals(2, registry.deque.size(), "Deque<Greeting> should contain both implementations");
            assertEquals(Set.of("hello", "hola"), new HashSet<>(registry.byName.keySet()),
                    "Map<String, Greeting> should be keyed by bean name");
        }
    }

    @Test
    void setMarkerReturnsSetMaterialisedForDeclaredType() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeting.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeting.class, "hola"));

            warmup.registerDynamic(definition(
                    HolderOfSet.class,
                    "holder",
                    CollectionDependency.marker(CollectionDependency.Kind.SET, Set.class.getName(), Greeting.class.getName())));

            HolderOfSet holder = warmup.get(HolderOfSet.class);
            assertNotNull(holder.values);
            assertEquals(2, holder.values.size());
            assertTrue(holder.values instanceof java.util.LinkedHashSet,
                    "Set injection point should be materialised as a Set, not a List");
        }
    }

    @Test
    void concreteDeclaredTypeIsMaterialisedNotErased() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeting.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeting.class, "hola"));

            warmup.registerDynamic(definition(
                    LinkedListHolder.class,
                    "holder",
                    CollectionDependency.marker(CollectionDependency.Kind.LIST, java.util.LinkedList.class.getName(), Greeting.class.getName())));

            LinkedListHolder holder = warmup.get(LinkedListHolder.class);
            assertNotNull(holder.values);
            assertTrue(holder.values instanceof java.util.LinkedList,
                    "Concrete declared type LinkedList<Greeting> must stay a LinkedList");
            assertEquals(2, holder.values.size());
        }
    }

    static class LinkedListHolder {
        final java.util.LinkedList<Greeting> values;

        LinkedListHolder(java.util.LinkedList<Greeting> values) {
            this.values = values;
        }
    }
}