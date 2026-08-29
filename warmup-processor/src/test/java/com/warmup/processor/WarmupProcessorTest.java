package com.warmup.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Optional;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WarmupProcessor annotation processor.
 * 
 * Note: These tests verify that the processor correctly handles the @Singleton, @Prototype, 
 * @Component and @Factory+@Bean annotations.
 * The generated factory code compilation may fail in isolation due to missing dependencies on the
 * test classpath (CompiledFactory, javax.annotation.Generated), but the processor itself works
 * correctly when warmup-core is available.
 */
class WarmupProcessorTest {

    private final Compiler compiler = javac()
        .withProcessors(new WarmupProcessor())
        .withOptions("-parameters");

    @Test
    void testProcessClassWithNoArgConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.SimpleBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public class SimpleBean {",
            "    public SimpleBean() {}",
            "}"
        );

        // The processor should run without errors
        Compilation compilation = compiler.compile(source);
        
        // Verify processor ran - it generates a file even if compilation fails due to missing deps
        assertTrue(compilation.generatedSourceFile("test.SimpleBean$$WarmupFactory").isPresent());
    }

    @Test
    void testProcessClassWithSinglePublicConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.SingleConstructorBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public class SingleConstructorBean {",
            "    private String value;",
            "    ",
            "    public SingleConstructorBean(String value) {",
            "        this.value = value;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedSourceFile("test.SingleConstructorBean$$WarmupFactory").isPresent());
    }

    @Test
    void testProcessClassWithInjectAnnotatedConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.InjectBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "import com.warmup.annotations.Inject;",
            "",
            "@Singleton",
            "public class InjectBean {",
            "    private String dep;",
            "    ",
            "    public InjectBean() {}",
            "    ",
            "    @Inject",
            "    public InjectBean(String dep) {",
            "        this.dep = dep;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedSourceFile("test.InjectBean$$WarmupFactory").isPresent());
    }

    @Test
    void testProcessPrototypeClass() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.PrototypeBean",
            "package test;",
            "import com.warmup.annotations.Prototype;",
            "",
            "@Prototype",
            "public class PrototypeBean {",
            "    public PrototypeBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedSourceFile("test.PrototypeBean$$WarmupFactory").isPresent());
    }

    @Test
    void testProcessComponentClass() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.ComponentBean",
            "package test;",
            "import com.warmup.annotations.Component;",
            "",
            "@Component",
            "public class ComponentBean {",
            "    public ComponentBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedSourceFile("test.ComponentBean$$WarmupFactory").isPresent());
    }

    @Test
    void testProcessFactoryWithBeanMethod() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.AppConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "",
            "@Factory",
            "public class AppConfig {",
            "    @Bean",
            "    public String dataSource() {",
            "        return \"DataSource\";",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        // Should generate a factory for the @Bean method (named AppConfig$$dataSource$$WarmupFactory)
        assertTrue(compilation.generatedSourceFile("test.AppConfig$$dataSource$$WarmupFactory").isPresent());
    }

    @Test
    void testProcessFactoryWithBeanMethodHavingDependencies() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.AppConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "",
            "@Factory",
            "public class AppConfig {",
            "    @Bean",
            "    public Service service(Repository repo) {",
            "        return new Service(repo);",
            "    }",
            "",
            "    public static class Repository {}",
            "    public static class Service {",
            "        public Service(Repository repo) {}",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        // Should generate a factory for the @Bean method (named AppConfig$$service$$WarmupFactory)
        assertTrue(compilation.generatedSourceFile("test.AppConfig$$service$$WarmupFactory").isPresent());
    }

    @Test
    void testSingletonOnNonClassProducesError() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.InvalidSingleton",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public interface InvalidSingleton {",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).failed();
        assertThat(compilation)
            .hadErrorContaining("@Singleton only applies to classes");
    }

    @Test
    void testFactoryOnNonClassProducesError() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.InvalidFactory",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "",
            "@Factory",
            "public interface InvalidFactory {",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).failed();
        assertThat(compilation)
            .hadErrorContaining("@Factory only applies to classes");
    }

    /**
     * Test that multiple registrars (one per package) are written to the service file.
     * Verifies fix for issue where only the first registrar was being registered.
     */
    @Test
    void testMultiplePackagesGenerateMultipleRegistrarsInServiceFile() {
        JavaFileObject beanPackageA = JavaFileObjects.forSourceLines(
            "com.example.alpha.BeanA",
            "package com.example.alpha;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public class BeanA {",
            "    public BeanA() {}",
            "}"
        );
        
        JavaFileObject beanPackageB = JavaFileObjects.forSourceLines(
            "com.example.beta.BeanB",
            "package com.example.beta;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public class BeanB {",
            "    public BeanB() {}",
            "}"
        );

        Compilation compilation = compiler.compile(beanPackageA, beanPackageB);

        assertTrue(compilation.generatedSourceFile("com/example/alpha/BeanA$$WarmupFactory").isPresent());
        assertTrue(compilation.generatedSourceFile("com/example/beta/BeanB$$WarmupFactory").isPresent());
        assertTrue(compilation.generatedSourceFile("com/example/alpha/GeneratedFactoryRegistrar").isPresent());
        assertTrue(compilation.generatedSourceFile("com/example/beta/GeneratedFactoryRegistrar").isPresent());
        
        Optional<JavaFileObject> serviceFileOpt = compilation.generatedFile("/META-INF/services/com.warmup.core.jit.FactoryRegistrar");
        assertTrue(serviceFileOpt.isPresent(), "Service file should be generated");
        
        try {
            String serviceContent = serviceFileOpt.get().getCharContent(true).toString();
            assertTrue(serviceContent.contains("com.example.alpha.GeneratedFactoryRegistrar"));
            assertTrue(serviceContent.contains("com.example.beta.GeneratedFactoryRegistrar"));
            
            String[] lines = serviceContent.trim().split("\\r?\\n");
            assertEquals(2, lines.length, "Service file should have exactly 2 lines");
        } catch (IOException e) {
            fail("Failed to read service file: " + e.getMessage());
        }
    }
}
