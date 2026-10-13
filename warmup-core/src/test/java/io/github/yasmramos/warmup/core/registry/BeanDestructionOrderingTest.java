package io.github.yasmramos.warmup.core.registry;

import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for destruction semantics of {@link BeanRegistryImpl#clear()}.
 *
 * <p>Before the fix, {@code clear()} iterated the bean map in arbitrary order and applied every
 * destroy callback through {@code applyDestroyCallback} without a guard, so a single failing
 * {@code @PreDestroy} skipped all remaining shutdown work. The map was also iterated in
 * ConcurrentHashMap order, so beans could be destroyed before the beans they depend on.</p>
 */
class BeanDestructionOrderingTest {

    private static BeanDefinition<Object> definition(String name, LifecycleCallbacks<Object> lifecycle, Object... deps) {
        return new BeanDefinition<>(Object.class, name, Scope.SINGLETON, lifecycle, false, deps);
    }

    private static LifecycleCallbacks<Object> recording(String name, List<String> order) {
        return new LifecycleCallbacks<>(o -> {
        }, o -> order.add(name));
    }

    @Test
    void dependentBeanIsDestroyedBeforeItsDependency() {
        List<String> order = new ArrayList<>();
        BeanRegistryImpl registry = new BeanRegistryImpl();

        // controller depends on service: service must be destroyed AFTER controller.
        registry.register(definition("service", recording("service", order)));
        registry.register(definition("controller", recording("controller", order), "service"));

        registry.getInstance("service", () -> new Object());
        registry.getInstance("controller", () -> new Object());

        registry.clear();

        assertEquals(List.of("controller", "service"), order,
                "The dependent bean (controller) must be destroyed before the bean it depends on (service)");
    }

    @Test
    void chainOfDependenciesIsDestroyedInReverseOrder() {
        List<String> order = new ArrayList<>();
        BeanRegistryImpl registry = new BeanRegistryImpl();

        registry.register(definition("base", recording("base", order)));
        registry.register(definition("middle", recording("middle", order), "base"));
        registry.register(definition("top", recording("top", order), "middle"));

        registry.getInstance("base", () -> new Object());
        registry.getInstance("middle", () -> new Object());
        registry.getInstance("top", () -> new Object());

        registry.clear();

        assertEquals(List.of("top", "middle", "base"), order,
                "Longest-lived dependencies must be destroyed last");
    }

    @Test
    void oneFailingDestroyDoesNotSkipTheRemainingBeans() {
        List<String> order = new ArrayList<>();
        BeanRegistryImpl registry = new BeanRegistryImpl();

        registry.register(definition("ok", recording("ok", order)));
        registry.register(definition("boom",
                new LifecycleCallbacks<>(o -> {
                }, o -> {
                    throw new IllegalStateException("boom");
                })));

        registry.getInstance("ok", () -> new Object());
        registry.getInstance("boom", () -> new Object());

        IllegalStateException failure = assertThrows(IllegalStateException.class, registry::clear);

        assertEquals(1, failure.getSuppressed().length,
                "The failing bean's exception must be attached as suppressed");
        assertEquals(List.of("ok"), order,
                "The healthy bean's destroy callback must still run after a sibling threw");
    }

    @Test
    void dependencyCycleDoesNotHangAndDestroysBothBeans() {
        List<String> order = new ArrayList<>();
        BeanRegistryImpl registry = new BeanRegistryImpl();

        // A forward-reference cycle (only possible with lazy/forward wiring) must not
        // make the topological sort loop forever.
        registry.register(definition("a", recording("a", order), "b"));
        registry.register(definition("b", recording("b", order), "a"));

        registry.getInstance("a", () -> new Object());
        registry.getInstance("b", () -> new Object());

        registry.clear();

        assertTrue(order.contains("a") && order.contains("b"),
                "Both beans in the cycle must be destroyed: " + order);
        assertEquals(Set.copyOf(List.of("a", "b")), Set.copyOf(order));
    }
}