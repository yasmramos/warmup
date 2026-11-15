package io.github.yasmramos.warmup.processor.integration.p0;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.yasmramos.warmup.processor.ProcessorTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression (bug 7.2.3): a bean whose constructor cannot be chosen
 * unambiguously must fail at compile time.
 *
 * <p>{@code findInjectableConstructor} used to return {@code null} silently
 * when a class declared several public constructors and none was annotated
 * {@code @Inject}. The generated factory then assumed a no-arg constructor and
 * blew up at runtime with a confusing error. The processor must instead report
 * a clear compile-time error.</p>
 */
class AmbiguousConstructorTest {

    private final ProcessorTestHarness harness = new ProcessorTestHarness();

    @Test
    void noInjectableConstructor_failsCompilation() {
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.AmbiguousBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class AmbiguousBean {",
                "  public AmbiguousBean(int port) {}",
                "  public AmbiguousBean(String name) {}",
                "}"));

        assertEquals(Compilation.Status.FAILURE, compilation.status(),
                "An ambiguous constructor must fail compilation, got: " + compilation.diagnostics());
        assertTrue(compilation.errors().stream()
                        .anyMatch(d -> d.getMessage(null).contains("constructor")),
                "The error must mention the constructor, got: " + compilation.errors());
    }
}
