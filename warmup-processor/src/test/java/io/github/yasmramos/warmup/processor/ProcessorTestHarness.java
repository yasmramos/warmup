package io.github.yasmramos.warmup.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.testing.compile.Compiler.javac;

/**
 * Compiles in-memory sources with {@link WarmupProcessor}, then loads every generated
 * {@code .class} into a throwaway class loader so a test can drive the real container
 * against the generated factories and registrar.
 */
public final class ProcessorTestHarness {

    private final Compiler compiler = javac().withProcessors(new WarmupProcessor()).withOptions("-parameters");

    public Compilation compile(String className, String... lines) {
        JavaFileObject source = JavaFileObjects.forSourceLines(className, lines);
        return compiler.compile(source);
    }

    public Compilation compile(JavaFileObject... sources) {
        return compiler.compile(sources);
    }

    public CompiledClassLoader loaderFor(Compilation compilation) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        for (JavaFileObject file : compilation.generatedFiles()) {
            if (file.getKind() != JavaFileObject.Kind.CLASS) {
                continue;
            }
            String path = file.getName();
            String prefix = "/" + StandardLocation.CLASS_OUTPUT.getName() + "/";
            if (!path.startsWith(prefix)) {
                continue;
            }
            String binaryName = path.substring(prefix.length());
            binaryName = binaryName.substring(0, binaryName.length() - ".class".length()).replace('/', '.');
            classes.put(binaryName, file.openInputStream().readAllBytes());
        }
        return new CompiledClassLoader(classes);
    }

    public static final class CompiledClassLoader extends ClassLoader {

        private final Map<String, byte[]> classes;

        CompiledClassLoader(Map<String, byte[]> classes) {
            super(ProcessorTestHarness.class.getClassLoader());
            this.classes = classes;
        }

        public List<String> loadedNames() {
            return new ArrayList<>(classes.keySet());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
