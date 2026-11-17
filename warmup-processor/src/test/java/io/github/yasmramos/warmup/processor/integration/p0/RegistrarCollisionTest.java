package io.github.yasmramos.warmup.processor.integration.p0;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.yasmramos.warmup.processor.ProcessorTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression (bug 7.2.4): a name collision on the generated
 * {@code GeneratedFactoryRegistrar} must not break the build.
 *
 * <p>When the registrar resource cannot be created because a file of that
 * name already exists (a user class with the same name, or a repeated
 * processing round), the processor used to let the {@code FilerException}
 * escape and fail the whole compilation. It must instead skip the registrar
 * with a note, mirroring the generated-factory path.</p>
 */
class RegistrarCollisionTest {

    private final ProcessorTestHarness harness = new ProcessorTestHarness();

    @Test
    void existingRegistrarClass_doesNotFailCompilation() throws Exception {
        Compilation compilation = harness.compile(
            JavaFileObjects.forSourceLines("test.GeneratedFactoryRegistrar",
                "package test;",
                "public final class GeneratedFactoryRegistrar {",
                "  private GeneratedFactoryRegistrar() {}",
                "}"),
            JavaFileObjects.forSourceLines("test.CollisionBean",
                "package test;",
                "import io.github.yasmramos.warmup.annotations.*;",
                "@Singleton public class CollisionBean {}"));

        assertEquals(Compilation.Status.SUCCESS, compilation.status(),
                "A registrar name collision must not fail the build, got: " + compilation.errors());

        // The user's class must be preserved (the processor must not clobber it),
        // and the service file must point at a working registrar.
        ProcessorTestHarness.CompiledClassLoader classLoader = harness.loaderFor(compilation);
        Class<?> userClass = classLoader.loadClass("test.GeneratedFactoryRegistrar");
        assertFalse(io.github.yasmramos.warmup.core.jit.FactoryRegistrar.class.isAssignableFrom(userClass),
                "The user's GeneratedFactoryRegistrar must not be overwritten by a generated registrar");

        String registrarName = serviceRegistrarName(compilation);
        assertNotNull(registrarName, "The service file must list a registrar");
        Class<?> registrarClass = classLoader.loadClass(registrarName);
        assertTrue(io.github.yasmramos.warmup.core.jit.FactoryRegistrar.class.isAssignableFrom(registrarClass),
                "The service file must point at a real FactoryRegistrar, got: " + registrarName);
    }

    private static String serviceRegistrarName(Compilation compilation) {
        return compilation.generatedFiles().stream()
                .filter(f -> f.getName().contains("META-INF/services"))
                .findFirst()
                .map(f -> {
                    try (var in = f.openInputStream()) {
                        return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                })
                .orElse(null);
    }
}
