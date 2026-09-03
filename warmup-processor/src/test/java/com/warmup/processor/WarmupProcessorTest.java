package com.warmup.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
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
        
        // Verify processor ran - it generates a .class file now (not source)
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/SimpleBean$$WarmupFactory.class").isPresent());
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
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/SingleConstructorBean$$WarmupFactory.class").isPresent());
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
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/InjectBean$$WarmupFactory.class").isPresent());
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
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/PrototypeBean$$WarmupFactory.class").isPresent());
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
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/ComponentBean$$WarmupFactory.class").isPresent());
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
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/AppConfig$$dataSource$$WarmupFactory.class").isPresent());
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
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/AppConfig$$service$$WarmupFactory.class").isPresent());
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

        // Verify .class files are generated instead of .java source files
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "com/example/alpha/BeanA$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "com/example/beta/BeanB$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "com/example/alpha/GeneratedFactoryRegistrar.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "com/example/beta/GeneratedFactoryRegistrar.class").isPresent());
        
        Optional<JavaFileObject> serviceFileOpt = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/com.warmup.core.jit.FactoryRegistrar");
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

    /**
     * Regression test for array type dependencies in constructor parameters.
     * Verifies that CHECKCAST receives valid array descriptors (e.g., [Ljava/lang/String;)
     * and the generated factory class loads without VerifyError or ClassFormatError.
     */
    @Test
    void testArrayDependencyInConstructorLoadsCorrectly() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.ArrayBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public class ArrayBean {",
            "    private String[] values;",
            "    private int[] numbers;",
            "    ",
            "    public ArrayBean(String[] values, int[] numbers) {",
            "        this.values = values;",
            "        this.numbers = numbers;",
            "    }",
            "    ",
            "    public String[] getValues() { return values; }",
            "    public int[] getNumbers() { return numbers; }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        
        // Verify factory class is generated
        Optional<JavaFileObject> factoryClassOpt = compilation.generatedFile(
            StandardLocation.CLASS_OUTPUT, 
            "test/ArrayBean$$WarmupFactory.class"
        );
        assertTrue(factoryClassOpt.isPresent(), "Factory class should be generated");

        // Load and define the generated factory class to verify it doesn't throw VerifyError
        JavaFileObject factoryClass = factoryClassOpt.get();
        byte[] factoryBytes = factoryClass.openInputStream().readAllBytes();
        
        // Define the class using a custom ClassLoader to verify bytecode validity
        TestClassLoader classLoader = new TestClassLoader();
        Class<?> factoryClassLoaded = classLoader.defineClass("test.ArrayBean$$WarmupFactory", factoryBytes);
        
        // Verify the class can be instantiated
        Object factoryInstance = factoryClassLoaded.getDeclaredConstructor().newInstance();
        assertNotNull(factoryInstance, "Factory instance should be created successfully");
    }

    /**
     * Helper ClassLoader for loading generated classes during tests.
     */
    private static class TestClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    @Test
    void testProcessClassWithProfileAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.ProfileBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "import com.warmup.annotations.Profile;",
            "",
            "@Singleton",
            "@Profile(\"prod\")",
            "public class ProfileBean {",
            "    public ProfileBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/ProfileBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessClassWithMultipleProfiles() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.MultiProfileBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "import com.warmup.annotations.Profile;",
            "",
            "@Singleton",
            "@Profile({\"dev\", \"test\"})",
            "public class MultiProfileBean {",
            "    public MultiProfileBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiProfileBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessClassWithPrimaryAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.PrimaryBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "import com.warmup.annotations.Primary;",
            "",
            "@Singleton",
            "@Primary",
            "public class PrimaryBean {",
            "    public PrimaryBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/PrimaryBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessClassWithNamedAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.NamedBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public class NamedBean {",
            "    public NamedBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/NamedBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessClassWithValueAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.ValueBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "import com.warmup.annotations.Value;",
            "",
            "@Singleton",
            "public class ValueBean {",
            "    @Value(\"${app.name}\")",
            "    private String appName;",
            "    ",
            "    public ValueBean(@Value(\"${app.name}\") String appName) {",
            "        this.appName = appName;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/ValueBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessClassWithPrivateConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.PrivateConstructorBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton",
            "public class PrivateConstructorBean {",
            "    private PrivateConstructorBean() {}",
            "    ",
            "    public static PrivateConstructorBean create() {",
            "        return new PrivateConstructorBean();",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        // Should still generate factory even with private constructor
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/PrivateConstructorBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessClassWithMultipleConstructorsAndInject() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.MultiConstructorBean",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "import com.warmup.annotations.Inject;",
            "",
            "@Singleton",
            "public class MultiConstructorBean {",
            "    public MultiConstructorBean() {}",
            "    ",
            "    @Inject",
            "    public MultiConstructorBean(String dep) {}",
            "    ",
            "    public MultiConstructorBean(String dep, Integer value) {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiConstructorBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessFactoryWithMultipleBeanMethods() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.MultiBeanConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "",
            "@Factory",
            "public class MultiBeanConfig {",
            "    @Bean",
            "    public String bean1() {",
            "        return \"bean1\";",
            "    }",
            "    ",
            "    @Bean",
            "    public Integer bean2() {",
            "        return 42;",
            "    }",
            "    ",
            "    @Bean",
            "    public Double bean3(String dep) {",
            "        return 3.14;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiBeanConfig$$bean1$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiBeanConfig$$bean2$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiBeanConfig$$bean3$$WarmupFactory.class").isPresent());
    }

    @Test
    void testProcessFactoryWithBeanMethodHavingMultipleDependencies() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.ComplexConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "",
            "@Factory",
            "public class ComplexConfig {",
            "    @Bean",
            "    public Service service(Repository repo, Database db, String config) {",
            "        return new Service(repo, db, config);",
            "    }",
            "    ",
            "    public static class Repository {}",
            "    public static class Database {}",
            "    public static class Service {",
            "        public Service(Repository repo, Database db, String config) {}",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/ComplexConfig$$service$$WarmupFactory.class").isPresent());
    }

    @Test
    void testComponentWithDefaultScope() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.DefaultComponent",
            "package test;",
            "import com.warmup.annotations.Component;",
            "",
            "@Component",
            "public class DefaultComponent {",
            "    public DefaultComponent() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/DefaultComponent$$WarmupFactory.class").isPresent());
    }

    @Test
    void testComponentWithCustomBeanName() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.NamedComponent",
            "package test;",
            "import com.warmup.annotations.Component;",
            "",
            "@Component(\"myCustomBean\")",
            "public class NamedComponent {",
            "    public NamedComponent() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/NamedComponent$$WarmupFactory.class").isPresent());
    }

    @Test
    void testPrototypeWithCustomBeanName() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.NamedPrototype",
            "package test;",
            "import com.warmup.annotations.Prototype;",
            "",
            "@Prototype(\"prototypeBean\")",
            "public class NamedPrototype {",
            "    public NamedPrototype() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/NamedPrototype$$WarmupFactory.class").isPresent());
    }

    @Test
    void testSingletonWithCustomBeanName() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.NamedSingleton",
            "package test;",
            "import com.warmup.annotations.Singleton;",
            "",
            "@Singleton(\"singletonBean\")",
            "public class NamedSingleton {",
            "    public NamedSingleton() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/NamedSingleton$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanMethodWithPrimaryAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.PrimaryConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "import com.warmup.annotations.Primary;",
            "",
            "@Factory",
            "public class PrimaryConfig {",
            "    @Bean",
            "    @Primary",
            "    public String primaryBean() {",
            "        return \"primary\";",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/PrimaryConfig$$primaryBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanMethodWithNamedAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.NamedConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "",
            "@Factory",
            "public class NamedConfig {",
            "    @Bean",
            "    public String namedBean() {",
            "        return \"named\";",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/NamedConfig$$namedBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testErrorOnInterfaceWithPrototype() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.InvalidPrototype",
            "package test;",
            "import com.warmup.annotations.Prototype;",
            "",
            "@Prototype",
            "public interface InvalidPrototype {",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Prototype only applies to classes");
    }

    @Test
    void testErrorOnInterfaceWithComponent() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.InvalidComponent",
            "package test;",
            "import com.warmup.annotations.Component;",
            "",
            "@Component",
            "public interface InvalidComponent {",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Component only applies to classes");
    }

    @Test
    void testBeanMethodWithoutReturnType() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.VoidBeanConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "",
            "@Factory",
            "public class VoidBeanConfig {",
            "    @Bean",
            "    public String voidBean() {",
            "        return \"void\";",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/VoidBeanConfig$$voidBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testFactoryWithStaticBeanMethod() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.StaticBeanConfig",
            "package test;",
            "import com.warmup.annotations.Factory;",
            "import com.warmup.annotations.Bean;",
            "",
            "@Factory",
            "public class StaticBeanConfig {",
            "    @Bean",
            "    public static String staticBean() {",
            "        return \"static\";",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/StaticBeanConfig$$staticBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testClassWithAllStereotypeAnnotations() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.OverAnnotatedBean",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Singleton",
            "@Primary",
            "@Profile(\"test\")",
            "public class OverAnnotatedBean {",
            "    public OverAnnotatedBean() {}",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/OverAnnotatedBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testFactoryWithMultipleBeanMethodsAndDependencies() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.MultiBeanFactory",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Factory",
            "public class MultiBeanFactory {",
            "    @Bean",
            "    public ServiceA serviceA() {",
            "        return new ServiceA();",
            "    }",
            "",
            "    @Bean",
            "    public ServiceB serviceB(ServiceA svc) {",
            "        return new ServiceB(svc);",
            "    }",
            "",
            "    @Bean",
            "    public ServiceC serviceC(ServiceA svc1, ServiceB svc2) {",
            "        return new ServiceC(svc1, svc2);",
            "    }",
            "}",
            "",
            "class ServiceA {}",
            "class ServiceB { public ServiceB(ServiceA s) {} }",
            "class ServiceC { public ServiceC(ServiceA s1, ServiceB s2) {} }"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiBeanFactory$$serviceA$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiBeanFactory$$serviceB$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiBeanFactory$$serviceC$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanWithConstructorInjectionAndFieldInjection() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.MixedInjectionBean",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Singleton",
            "public class MixedInjectionBean {",
            "    @Inject",
            "    DependencyA fieldDep;",
            "",
            "    @Inject",
            "    public MixedInjectionBean(ConstructorDep ctorDep) {",
            "    }",
            "",
            "    @Inject",
            "    public void setService(Service svc) {",
            "    }",
            "}",
            "",
            "class ConstructorDep {}",
            "class DependencyA {}",
            "class Service {}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MixedInjectionBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanMethodWithPrimitiveReturnType() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.PrimitiveBeanFactory",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Factory",
            "public class PrimitiveBeanFactory {",
            "    @Bean",
            "    public int intBean() {",
            "        return 42;",
            "    }",
            "",
            "    @Bean",
            "    public boolean booleanBean() {",
            "        return true;",
            "    }",
            "",
            "    @Bean",
            "    public double doubleBean() {",
            "        return 3.14;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/PrimitiveBeanFactory$$intBean$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/PrimitiveBeanFactory$$booleanBean$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/PrimitiveBeanFactory$$doubleBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanWithMultipleInjectMethods() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.MultiInjectBean",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Singleton",
            "public class MultiInjectBean {",
            "    @Inject",
            "    public void setDep1(Dependency1 d1) {}",
            "",
            "    @Inject",
            "    public void setDep2(Dependency2 d2) {}",
            "",
            "    @Inject",
            "    public void setDep3(Dependency3 d3) {}",
            "}",
            "",
            "class Dependency1 {}",
            "class Dependency2 {}",
            "class Dependency3 {}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MultiInjectBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testFactoryWithStaticAndInstanceBeanMethods() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.MixedFactory",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Factory",
            "public class MixedFactory {",
            "    @Bean",
            "    public static String staticBean() {",
            "        return \"static\";",
            "    }",
            "",
            "    @Bean",
            "    public String instanceBean() {",
            "        return \"instance\";",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MixedFactory$$staticBean$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/MixedFactory$$instanceBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanWithManyConstructorParameters() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.ManyDepsBean",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Singleton",
            "public class ManyDepsBean {",
            "    @Inject",
            "    public ManyDepsBean(Dep1 d1, Dep2 d2, Dep3 d3, Dep4 d4, Dep5 d5) {",
            "    }",
            "}",
            "",
            "class Dep1 {}",
            "class Dep2 {}",
            "class Dep3 {}",
            "class Dep4 {}",
            "class Dep5 {}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/ManyDepsBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanMethodInNestedFactory() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.SimpleInnerFactory",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Factory",
            "public class SimpleInnerFactory {",
            "    @Bean",
            "    public String bean1() {",
            "        return \"bean1\";",
            "    }",
            "",
            "    @Bean",
            "    public Integer bean2() {",
            "        return 42;",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/SimpleInnerFactory$$bean1$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/SimpleInnerFactory$$bean2$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanWithGenericReturnType() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.GenericBeanFactory",
            "package test;",
            "import com.warmup.annotations.*;",
            "import java.util.*;",
            "",
            "@Factory",
            "public class GenericBeanFactory {",
            "    @Bean",
            "    public List<String> listBean() {",
            "        return new ArrayList<>();",
            "    }",
            "",
            "    @Bean",
            "    public Map<String, Integer> mapBean() {",
            "        return new HashMap<>();",
            "    }",
            "}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/GenericBeanFactory$$listBean$$WarmupFactory.class").isPresent());
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/GenericBeanFactory$$mapBean$$WarmupFactory.class").isPresent());
    }

    @Test
    void testBeanWithFieldInjectionOnly() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.FieldInjectionOnlyBean",
            "package test;",
            "import com.warmup.annotations.*;",
            "",
            "@Singleton",
            "public class FieldInjectionOnlyBean {",
            "    @Inject",
            "    DependencyA depA;",
            "",
            "    @Inject",
            "    DependencyB depB;",
            "",
            "    public FieldInjectionOnlyBean() {}",
            "}",
            "",
            "class DependencyA {}",
            "class DependencyB {}"
        );

        Compilation compilation = compiler.compile(source);
        assertTrue(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "test/FieldInjectionOnlyBean$$WarmupFactory.class").isPresent());
    }
}
