package io.github.yasmramos.warmup.core.integration.p0;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression tests for beans registered through
 * {@link Warmup#register(String, Class, java.util.function.Supplier, Scope)}.
 *
 * <p>Such beans carry an explicit supplier factory, so the container must
 * never replace it with a JIT factory built from the bean's constructor. The
 * prototype case is the interesting one because prototypes are created on
 * every resolution, exposing any later factory replacement.</p>
 */
class RegisterSupplierFactoryTest {

    private Warmup warmup;

    @BeforeEach
    void setUp() {
        warmup = Warmup.builder().registerShutdownHook(false).build();
    }

    @AfterEach
    void tearDown() {
        warmup.shutdown();
    }

    /**
     * A prototype bean whose class has no no-arg constructor can only be
     * created through its registered supplier. If background warmup replaces
     * the supplier factory with a JIT factory, the second resolution fails
     * with {@code NoSuchMethodError: <init>()}.
     */
    @Test
    void supplierRegisteredPrototype_survivesBackgroundWarmup() throws Exception {
        warmup.register("dependency", Dependency.class, Dependency::new, Scope.SINGLETON);
        warmup.register("prototype", NoDefaultConstructor.class,
                () -> new NoDefaultConstructor(warmup.resolve(Dependency.class)), Scope.PROTOTYPE);

        NoDefaultConstructor first = warmup.resolve(NoDefaultConstructor.class);
        assertNotNull(first);
        assertNotNull(first.dependency);

        // Allow any background warmup compilation to complete and (incorrectly)
        // install its own factory before the second resolution.
        Thread.sleep(150);

        NoDefaultConstructor second = warmup.resolve(NoDefaultConstructor.class);
        assertNotNull(second);
        assertNotNull(second.dependency);
    }

    /**
     * Singleton beans keep their cached instance, but the registered supplier
     * must remain the source of truth for the factory.
     */
    @Test
    void supplierRegisteredSingleton_usesSupplier() throws Exception {
        warmup.register("singleton", Dependency.class, Dependency::new, Scope.SINGLETON);

        Dependency first = warmup.resolve(Dependency.class);
        Thread.sleep(150);
        Dependency second = warmup.resolve(Dependency.class);

        assertNotNull(first);
        assertSame(first, second, "Singleton scope must return the same instance");
    }

    public static class Dependency {
        public Dependency() {
        }
    }

    public static class NoDefaultConstructor {
        final Dependency dependency;

        public NoDefaultConstructor(Dependency dependency) {
            this.dependency = dependency;
        }
    }
}
