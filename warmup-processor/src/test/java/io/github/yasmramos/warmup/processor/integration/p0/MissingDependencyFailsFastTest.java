package io.github.yasmramos.warmup.processor.integration.p0;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.yasmramos.warmup.core.jit.CompiledFactory;
import io.github.yasmramos.warmup.processor.ProcessorTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0/P1 feature: generated factories must validate non-deferred dependencies
 * and fail fast with a descriptive message (the {@code requireDependency}
 * contract), instead of letting a {@code null} dependency surface later as an
 * obscure {@code NullPointerException} inside the bean.
 *
 * <p>The factory's {@code create(Object...)} path is the fallback used when the
 * wired {@code get()} path is unavailable, and it is also what a container
 * passes the resolved dependency array to. A {@code null} slot there means a
 * required dependency was missing; the factory must say so.</p>
 */
class MissingDependencyFailsFastTest {

    private final ProcessorTestHarness harness = new ProcessorTestHarness();

    @Test
    void createWithNullDependency_throwsDescriptiveIllegalState() throws Exception {
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.NeedsDep",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class NeedsDep {",
                "  public NeedsDep(String greeting) {}",
                "}"));

        assertEquals(Compilation.Status.SUCCESS, compilation.status(),
                "Compilation must succeed, errors: " + compilation.errors());

        ProcessorTestHarness.CompiledClassLoader classLoader = harness.loaderFor(compilation);
        CompiledFactory<?> factory = (CompiledFactory<?>) classLoader
                .loadClass("test.NeedsDep$$WarmupFactory").getDeclaredConstructor().newInstance();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> factory.create(new Object[]{null}),
                "A null required dependency must fail fast with IllegalStateException");

        assertTrue(failure.getMessage().contains("greeting"),
                "The failure must name the missing dependency, got: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("NeedsDep"),
                "The failure must name the affected bean, got: " + failure.getMessage());
    }
}
