package io.github.yasmramos.warmup.processor.integration.p0;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.yasmramos.warmup.processor.ProcessorTestHarness;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the generated registrar is split into {@code initChunk_N} methods
 * once a package declares more beans than {@code REGISTRAR_CHUNK_SIZE}.
 *
 * <p>A single {@code registerAll} method for hundreds of beans would exceed the
 * JVM's 64KB bytecode limit and fail verification at class load time. The
 * processor must therefore emit one chunk method per group of beans and have
 * {@code registerAll} delegate to them.</p>
 */
class ChunkedRegistrarTest {

    private static final int BEAN_COUNT = 70;

    private final ProcessorTestHarness harness = new ProcessorTestHarness();

    private Compilation compileManyBeans() {
        List<JavaFileObject> sources = new ArrayList<>();
        for (int i = 0; i < BEAN_COUNT; i++) {
            sources.add(JavaFileObjects.forSourceLines(
                    "chunked.Bean" + i,
                    "package chunked;",
                    "import io.github.yasmramos.warmup.annotations.Singleton;",
                    "",
                    "@Singleton",
                    "public class Bean" + i + " {",
                    "    public Bean" + i + "() {}",
                    "}"));
        }
        return harness.compile(sources.toArray(new JavaFileObject[0]));
    }

    private static List<String> methodNames(byte[] classBytes) {
        List<String> names = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                names.add(name);
                return null;
            }
        }, 0);
        return names;
    }

    private static List<String> methodsCalledFrom(byte[] classBytes, String methodName) {
        List<String> called = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals(methodName)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String calledName,
                            String calledDescriptor, boolean isInterface) {
                        called.add(calledName);
                    }
                };
            }
        }, 0);
        return called;
    }

    @Test
    void registrar_splitsIntoChunkMethodsWhenBeansExceedChunkSize() throws IOException {
        Compilation compilation = compileManyBeans();
        assertTrue(compilation.generatedFile(
                        javax.tools.StandardLocation.CLASS_OUTPUT, "chunked/GeneratedFactoryRegistrar.class")
                .isPresent(), "The registrar must be generated for the chunked package");

        byte[] registrar = compilation.generatedFile(
                        javax.tools.StandardLocation.CLASS_OUTPUT, "chunked/GeneratedFactoryRegistrar.class")
                .orElseThrow().openInputStream().readAllBytes();

        List<String> methods = methodNames(registrar);
        // 70 beans / chunk size 64 -> 2 chunks
        assertTrue(methods.contains("initChunk0"), "Expected initChunk0, got " + methods);
        assertTrue(methods.contains("initChunk1"), "Expected initChunk1, got " + methods);

        List<String> delegated = methodsCalledFrom(registrar, "registerAll");
        assertTrue(delegated.contains("initChunk0"), "registerAll must call initChunk0, got " + delegated);
        assertTrue(delegated.contains("initChunk1"), "registerAll must call initChunk1, got " + delegated);
    }

    @Test
    void registrar_keepsSingleMethodWhenBeansFitInOneChunk() throws IOException {
        Compilation compilation = harness.compile(
                "small.Solo",
                "package small;",
                "import io.github.yasmramos.warmup.annotations.Singleton;",
                "",
                "@Singleton",
                "public class Solo {",
                "    public Solo() {}",
                "}");

        byte[] registrar = compilation.generatedFile(
                        javax.tools.StandardLocation.CLASS_OUTPUT, "small/GeneratedFactoryRegistrar.class")
                .orElseThrow().openInputStream().readAllBytes();

        List<String> methods = methodNames(registrar);
        assertTrue(methods.contains("registerAll"), "Expected registerAll, got " + methods);
        assertTrue(methods.stream().noneMatch(n -> n.startsWith("initChunk")),
                "A single chunk must not be split into chunk methods, got " + methods);
    }

    @Test
    void registrar_chunkMethodsLoadSuccessfully() throws IOException {
        Compilation compilation = compileManyBeans();
        // Loading the registrar runs the verifier, which rejects an oversized method.
        ProcessorTestHarness.CompiledClassLoader loader = harness.loaderFor(compilation);
        Class<?> registrar = assertDoesNotThrowLoad(loader, "chunked.GeneratedFactoryRegistrar");
        assertNotNull(registrar);
    }

    private static Class<?> assertDoesNotThrowLoad(ClassLoader loader, String name) {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Registrar must load: " + name, e);
        }
    }
}
