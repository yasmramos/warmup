package com.warmup.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WarmupProcessor annotation processor.
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
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public class SimpleBean {",
            "    public SimpleBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("test.SimpleBean$$WarmupFactory")
            .exists();
    }

    @Test
    void testProcessClassWithSinglePublicConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.SingleConstructorBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public class SingleConstructorBean {",
            "    private String value;",
            "    ",
            "    public SingleConstructorBean(String value) {",
            "        this.value = value;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("test.SingleConstructorBean$$WarmupFactory")
            .exists();
    }

    @Test
    void testProcessClassWithInjectAnnotatedConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.InjectBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "import com.warmup.annotations.WarmupInject;",
            "",
            "@WarmupBean",
            "public class InjectBean {",
            "    private String dep;",
            "    ",
            "    public InjectBean() {}",
            "    ",
            "    @WarmupInject",
            "    public InjectBean(String dep) {",
            "        this.dep = dep;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("test.InjectBean$$WarmupFactory")
            .exists();
    }

    @Test
    void testGeneratedFactoryHasCreateMethod() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.FactoryBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public class FactoryBean {",
            "    public FactoryBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        
        // Verify the generated factory contains expected methods
        assertThat(compilation)
            .generatedSourceFile("test.FactoryBean$$WarmupFactory")
            .contentsAsUtf8String()
            .contains("create");
    }

    @Test
    void testGeneratedFactoryHasGetBeanTypeMethod() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.BeanTypeBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public class BeanTypeBean {",
            "    public BeanTypeBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        
        assertThat(compilation)
            .generatedSourceFile("test.BeanTypeBean$$WarmupFactory")
            .contentsAsUtf8String()
            .contains("getBeanType");
    }

    @Test
    void testGeneratedFactoryHasGetDependencyCountMethod() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.DepCountBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public class DepCountBean {",
            "    public DepCountBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        
        assertThat(compilation)
            .generatedSourceFile("test.DepCountBean$$WarmupFactory")
            .contentsAsUtf8String()
            .contains("getDependencyCount");
    }

    @Test
    void testWarmupBeanOnNonClassProducesError() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.InvalidBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public interface InvalidBean {",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).failed();
        assertThat(compilation)
            .hadErrorContaining("@WarmupBean only applies to classes");
    }

    @Test
    void testWarmupBeanOnEnumProducesError() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.EnumBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public enum EnumBean {",
            "    INSTANCE;",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).failed();
        assertThat(compilation)
            .hadErrorContaining("@WarmupBean only applies to classes");
    }

    @Test
    void testGeneratedFactoryImplementsCompiledFactory() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.ImplementsBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public class ImplementsBean {",
            "    public ImplementsBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        
        String generatedContent = compilation
            .generatedSourceFile("test.ImplementsBean$$WarmupFactory")
            .contentsAsUtf8String();
        
        assertTrue(generatedContent.contains("implements CompiledFactory"));
    }

    @Test
    void testGeneratedFactoryHasGeneratedAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.GeneratedBean",
            "package test;",
            "import com.warmup.annotations.WarmupBean;",
            "",
            "@WarmupBean",
            "public class GeneratedBean {",
            "    public GeneratedBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);

        assertThat(compilation).succeeded();
        
        String generatedContent = compilation
            .generatedSourceFile("test.GeneratedBean$$WarmupFactory")
            .contentsAsUtf8String();
        
        assertTrue(generatedContent.contains("@javax.annotation.Generated"));
    }
}
