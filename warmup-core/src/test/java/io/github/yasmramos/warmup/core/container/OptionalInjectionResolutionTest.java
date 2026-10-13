package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.exception.AmbiguousBeanException;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.registry.OptionalDependency;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@code Optional<T>} injection points.
 *
 * <p>An injection point declared as {@code Optional<PaymentProcessor>} used to be recorded by
 * the annotation processor as the raw name {@code Optional}, which failed at runtime with
 * {@code Bean not found: Optional}. The dependency is now an {@link OptionalDependency} marker;
 * this test builds those markers by hand to exercise the container side of the contract
 * without a full compile-time factory pipeline.</p>
 */
class OptionalInjectionResolutionTest {

    interface Greeter {
        String greet();
    }

    static class HelloGreeter implements Greeter {
        @Override
        public String greet() {
            return "hello";
        }
    }

    static class HolaGreeter implements Greeter {
        @Override
        public String greet() {
            return "hola";
        }
    }

    static class OptionalHolder {
        final Optional<Greeter> greeter;

        OptionalHolder(Optional<Greeter> greeter) {
            this.greeter = greeter;
        }
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinition<?> definition(Class<?> type, String name, Object... dependencies) {
        return new BeanDefinition(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(),
                false, dependencies);
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinition<?> primaryDefinition(Class<?> type, String name, Object... dependencies) {
        return new BeanDefinition(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(),
                true, dependencies);
    }

    @Test
    void optionalResolvesTheSingleCandidate() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeter.class, "hello"));
            warmup.registerDynamic(definition(OptionalHolder.class, "holder",
                    OptionalDependency.marker(Greeter.class.getName())));

            OptionalHolder holder = warmup.get(OptionalHolder.class);
            assertNotNull(holder.greeter);
            assertTrue(holder.greeter.isPresent());
            assertEquals("hello", holder.greeter.get().greet());
        }
    }

    @Test
    void optionalWithNoCandidateIsEmpty() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(OptionalHolder.class, "holder",
                    OptionalDependency.marker(Greeter.class.getName())));

            OptionalHolder holder = warmup.get(OptionalHolder.class);
            assertNotNull(holder.greeter,
                    "Optional injection point must never receive null");
            assertFalse(holder.greeter.isPresent());
        }
    }

    @Test
    void primaryCandidateWinsWhenSeveralExist() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(primaryDefinition(HelloGreeter.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeter.class, "hola"));
            warmup.registerDynamic(definition(OptionalHolder.class, "holder",
                    OptionalDependency.marker(Greeter.class.getName())));

            OptionalHolder holder = warmup.get(OptionalHolder.class);
            assertTrue(holder.greeter.isPresent());
            assertEquals("hello", holder.greeter.get().greet(),
                    "Optional<T> must honour @Primary when several candidates exist");
        }
    }

    @Test
    void severalCandidatesWithoutPrimaryIsAmbiguous() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition(HelloGreeter.class, "hello"));
            warmup.registerDynamic(definition(HolaGreeter.class, "hola"));
            warmup.registerDynamic(definition(OptionalHolder.class, "holder",
                    OptionalDependency.marker(Greeter.class.getName())));

            assertThrows(AmbiguousBeanException.class, () -> warmup.get(OptionalHolder.class));
        }
    }
}
