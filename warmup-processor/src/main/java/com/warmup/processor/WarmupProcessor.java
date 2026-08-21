package com.warmup.processor;

import com.warmup.annotations.Bean;
import com.warmup.annotations.Factory;
import com.warmup.annotations.Singleton;
import com.warmup.annotations.Prototype;
import com.warmup.annotations.Component;
import com.warmup.annotations.Inject;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Annotation processor for generating compile-time CompiledFactory implementations.
 * 
 * Generates zero-overhead factories that are registered automatically with the container.
 * 
 * Supported annotations:
 * - @Singleton, @Prototype, @Component: Class-level stereotypes that imply @Bean with scope
 * - @Factory: Class-level marker for configuration classes with @Bean methods
 * - @Bean: Method-level annotation within @Factory classes to mark producer methods
 * - @Inject: Marks constructor parameters or method parameters for injection
 * 
 * Generated code example for class-level bean:
 * ```java
 * public class UserService$$WarmupFactory implements CompiledFactory<UserService> {
 *     private final CompiledFactory<Repository> repoFactory;
 *     
 *     public UserService$$WarmupFactory() {
 *         // Constructor
 *     }
 *     
 *     @Override
 *     public UserService create(Object... dependencies) {
 *         Repository repo = (Repository) dependencies[0];
 *         return new UserService(repo);
 *     }
 *     
 *     @Override
 *     public Class<UserService> getBeanType() {
 *         return UserService.class;
 *     }
 * }
 * ```
 * 
 * Generated code example for @Factory method:
 * ```java
 * public class AppConfig$$WarmupFactory implements CompiledFactory<AppConfig> {
 *     private AppConfig factoryInstance;
 *     
 *     @Override
 *     public DataSource create(Object... dependencies) {
 *         if (factoryInstance == null) {
 *             factoryInstance = new AppConfig();
 *         }
 *         return factoryInstance.dataSource();
 *     }
 * }
 * ```
 */
@SupportedAnnotationTypes({
    "com.warmup.annotations.Bean",
    "com.warmup.annotations.Factory",
    "com.warmup.annotations.Singleton",
    "com.warmup.annotations.Prototype",
    "com.warmup.annotations.Component",
    "com.warmup.annotations.Inject"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class WarmupProcessor extends AbstractProcessor {

    private final List<BeanInfo> processedBeans = new ArrayList<>();
    private boolean processingOver = false;

    /**
     * Holds information about a processed bean for later registrar generation.
     */
    private static class BeanInfo {
        final String packageName;
        final String className;
        final String beanName;
        final String factoryClassName;
        final String scope;

        BeanInfo(String packageName, String className, String beanName, String factoryClassName, String scope) {
            this.packageName = packageName;
            this.className = className;
            this.beanName = beanName;
            this.factoryClassName = factoryClassName;
            this.scope = scope;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();
        Messager messager = processingEnv.getMessager();
        
        // Process class-level stereotype annotations: @Singleton, @Prototype, @Component
        // These imply @Bean with a specific scope
        processClassStereotypes(roundEnv, filer, messager);
        
        // Process @Factory classes with @Bean methods
        processFactoryClasses(roundEnv, filer, messager);
        
        // Generate registrar when processing is complete
        if (roundEnv.processingOver() && !processingOver && !processedBeans.isEmpty()) {
            processingOver = true;
            try {
                generateFactoryRegistrar(filer);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate FactoryRegistrar: " + e.getMessage());
            }
        }
        
        return true;
    }
    
    /**
     * Processes classes annotated with @Singleton, @Prototype, or @Component.
     * These are treated as beans with constructor-based injection.
     */
    private void processClassStereotypes(RoundEnvironment roundEnv, Filer filer, Messager messager) {
        // Process @Singleton
        for (Element element : roundEnv.getElementsAnnotatedWith(Singleton.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "@Singleton only applies to classes", element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            Singleton singleton = typeElement.getAnnotation(Singleton.class);
            try {
                String factoryClassName = generateFactoryForClass(typeElement, "SINGLETON", singleton.value(), filer);
                storeBeanInfo(typeElement, singleton.value(), "SINGLETON", factoryClassName);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "Failed to generate factory: " + e.getMessage(), element);
            }
        }
        
        // Process @Prototype
        for (Element element : roundEnv.getElementsAnnotatedWith(Prototype.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "@Prototype only applies to classes", element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            Prototype prototype = typeElement.getAnnotation(Prototype.class);
            try {
                String factoryClassName = generateFactoryForClass(typeElement, "PROTOTYPE", prototype.value(), filer);
                storeBeanInfo(typeElement, prototype.value(), "PROTOTYPE", factoryClassName);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "Failed to generate factory: " + e.getMessage(), element);
            }
        }
        
        // Process @Component (treated as SINGLETON)
        for (Element element : roundEnv.getElementsAnnotatedWith(Component.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "@Component only applies to classes", element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            Component component = typeElement.getAnnotation(Component.class);
            try {
                String factoryClassName = generateFactoryForClass(typeElement, "SINGLETON", component.value(), filer);
                storeBeanInfo(typeElement, component.value(), "SINGLETON", factoryClassName);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "Failed to generate factory: " + e.getMessage(), element);
            }
        }
    }
    
    /**
     * Processes @Factory classes and their @Bean methods.
     */
    private void processFactoryClasses(RoundEnvironment roundEnv, Filer filer, Messager messager) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Factory.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "@Factory only applies to classes", element);
                continue;
            }
            
            TypeElement factoryClass = (TypeElement) element;
            
            // Process each @Bean method in the factory class
            for (Element enclosed : factoryClass.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.METHOD) {
                    continue;
                }
                
                ExecutableElement method = (ExecutableElement) enclosed;
                if (method.getAnnotation(Bean.class) == null) {
                    continue;
                }
                
                Bean beanAnnotation = method.getAnnotation(Bean.class);
                String scope = beanAnnotation.scope().name();
                
                try {
                    String factoryClassName = generateFactoryForMethod(factoryClass, method, beanAnnotation, filer);
                    storeBeanInfoForMethod(factoryClass, method, beanAnnotation, scope, factoryClassName);
                } catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, 
                        "Failed to generate factory for method " + method.getSimpleName() + ": " + e.getMessage(), 
                        method);
                }
            }
        }
    }
    
    /**
     * Stores bean information for class-level stereotypes.
     */
    private void storeBeanInfo(TypeElement typeElement, String explicitName, String scope, String factoryClassName) {
        String packageName = getPackageName(typeElement);
        String className = typeElement.getSimpleName().toString();
        String beanName = explicitName.isEmpty() ? className : explicitName;
        processedBeans.add(new BeanInfo(packageName, className, beanName, factoryClassName, scope));
    }
    
    /**
     * Stores bean information for @Factory methods.
     */
    private void storeBeanInfoForMethod(TypeElement factoryClass, ExecutableElement method, Bean beanAnnotation, String scope, String factoryClassName) {
        String packageName = getPackageName(factoryClass);
        String factoryClassNameStr = factoryClass.getSimpleName().toString();
        String methodName = method.getSimpleName().toString();
        String beanName = beanAnnotation.value().isEmpty() ? methodName : beanAnnotation.value();
        
        // For method-based beans, we use a composite class name to distinguish from class beans
        String fullBeanClassName = factoryClassNameStr + "." + methodName;
        processedBeans.add(new BeanInfo(packageName, fullBeanClassName, beanName, factoryClassName, scope));
    }
    
    /**
     * Generates a CompiledFactory implementation for a class-level stereotype bean.
     * This is similar to the old generateFactory but without the Bean annotation parameter.
     */
    private String generateFactoryForClass(TypeElement beanClass, String scope, String explicitName, Filer filer) 
            throws IOException {
        
        String packageName = getPackageName(beanClass);
        String className = beanClass.getSimpleName().toString();
        String factoryClassName = className + "$$WarmupFactory";
        
        // Find constructor and dependencies
        ExecutableElement constructor = findInjectableConstructor(beanClass);
        List<? extends VariableElement> parameters = constructor != null 
            ? constructor.getParameters() 
            : Collections.emptyList();
        
        StringBuilder code = new StringBuilder();
        
        // Only add package declaration if not in default package
        if (!packageName.isEmpty()) {
            code.append("package ").append(packageName).append(";\n\n");
        }
        
        code.append("import com.warmup.core.jit.CompiledFactory;\n");
        code.append("import java.lang.Class;\n\n");
        
        // Generate factory class
        code.append("/**\n");
        code.append(" * Auto-generated factory for {@link ").append(className).append("}.\n");
        code.append(" * Scope: ").append(scope).append("\n");
        code.append(" * DO NOT MODIFY - generated by Warmup annotation processor\n");
        code.append(" */\n");
        code.append("@javax.annotation.processing.Generated(\"com.warmup.processor.WarmupProcessor\")\n");
        code.append("public final class ").append(factoryClassName)
            .append(" implements CompiledFactory<").append(className).append("> {\n\n");
        
        // Generate fields for dependency factories (if any)
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("    private final CompiledFactory<").append(paramType)
                .append("> factory").append(i).append(";\n");
        }
        
        if (!parameters.isEmpty()) {
            code.append("\n");
        }
        
        // Generate constructor
        code.append("    public ").append(factoryClassName).append("() {\n");
        code.append("        // Dependencies resolved at runtime\n");
        for (int i = 0; i < parameters.size(); i++) {
            code.append("        this.factory").append(i).append(" = null;\n");
        }
        code.append("    }\n\n");
        
        // Generate create method
        code.append("    @Override\n");
        code.append("    public ").append(className).append(" create(Object... dependencies) {\n");
        
        // Cast dependencies
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("        ").append(paramType).append(" arg").append(i)
                .append(" = (").append(paramType).append(") dependencies[").append(i).append("];\n");
        }
        
        // Invoke constructor
        code.append("        return new ").append(className).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) code.append(", ");
            code.append("arg").append(i);
        }
        code.append(");\n");
        code.append("    }\n\n");
        
        // Generate getBeanType method
        code.append("    @Override\n");
        code.append("    public Class<").append(className).append("> getBeanType() {\n");
        code.append("        return ").append(className).append(".class;\n");
        code.append("    }\n\n");
        
        // Generate getDependencyCount method
        code.append("    @Override\n");
        code.append("    public int getDependencyCount() {\n");
        code.append("        return ").append(parameters.size()).append(";\n");
        code.append("    }\n");
        
        code.append("}\n");
        
        // Write the file - handle default package
        Writer writer;
        if (packageName.isEmpty()) {
            writer = filer.createSourceFile(factoryClassName).openWriter();
        } else {
            writer = filer.createSourceFile(packageName + "." + factoryClassName).openWriter();
        }
        try {
            writer.write(code.toString());
        } finally {
            writer.close();
        }
        
        return factoryClassName;
    }
    
    /**
     * Generates a CompiledFactory implementation for a @Bean method within a @Factory class.
     * The factory invokes the producer method on a cached instance of the factory class.
     */
    private String generateFactoryForMethod(TypeElement factoryClass, ExecutableElement method, Bean beanAnnotation, Filer filer) 
            throws IOException {
        
        String packageName = getPackageName(factoryClass);
        String factoryClassNameStr = factoryClass.getSimpleName().toString();
        String methodName = method.getSimpleName().toString();
        String returnType = method.getReturnType().toString();
        String factoryFullClassName = packageName.isEmpty() ? factoryClassNameStr : packageName + "." + factoryClassNameStr;
        
        // Get method parameters (dependencies)
        List<? extends VariableElement> parameters = method.getParameters();
        
        StringBuilder code = new StringBuilder();
        
        // Only add package declaration if not in default package
        if (!packageName.isEmpty()) {
            code.append("package ").append(packageName).append(";\n\n");
        }
        
        code.append("import com.warmup.core.jit.CompiledFactory;\n");
        code.append("import java.lang.Class;\n\n");
        
        // Generate factory class
        String generatedFactoryName = factoryClassNameStr + "$$" + methodName + "$$WarmupFactory";
        code.append("/**\n");
        code.append(" * Auto-generated factory for {@link ").append(factoryClassNameStr).append("#").append(methodName).append("()}.\n");
        code.append(" * Produces beans of type {@link ").append(returnType).append("}.\n");
        code.append(" * Scope: ").append(beanAnnotation.scope().name()).append("\n");
        code.append(" * DO NOT MODIFY - generated by Warmup annotation processor\n");
        code.append(" */\n");
        code.append("@javax.annotation.processing.Generated(\"com.warmup.processor.WarmupProcessor\")\n");
        code.append("public final class ").append(generatedFactoryName)
            .append(" implements CompiledFactory<").append(returnType).append("> {\n\n");
        
        // Field for caching the factory instance (singleton pattern for the factory itself)
        code.append("    private ").append(factoryFullClassName).append(" factoryInstance;\n\n");
        
        // Generate constructor
        code.append("    public ").append(generatedFactoryName).append("() {\n");
        code.append("        // Factory instance will be created on first call\n");
        code.append("    }\n\n");
        
        // Generate create method
        code.append("    @Override\n");
        code.append("    public ").append(returnType).append(" create(Object... dependencies) {\n");
        code.append("        if (factoryInstance == null) {\n");
        code.append("            factoryInstance = new ").append(factoryFullClassName).append("();\n");
        code.append("        }\n");
        
        // Cast dependencies and invoke method
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("        ").append(paramType).append(" arg").append(i)
                .append(" = (").append(paramType).append(") dependencies[").append(i).append("];\n");
        }
        
        code.append("        return factoryInstance.").append(methodName).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) code.append(", ");
            code.append("arg").append(i);
        }
        code.append(");\n");
        code.append("    }\n\n");
        
        // Generate getBeanType method
        code.append("    @Override\n");
        code.append("    public Class<").append(returnType).append("> getBeanType() {\n");
        code.append("        return ").append(returnType).append(".class;\n");
        code.append("    }\n\n");
        
        // Generate getDependencyCount method
        code.append("    @Override\n");
        code.append("    public int getDependencyCount() {\n");
        code.append("        return ").append(parameters.size()).append(";\n");
        code.append("    }\n");
        
        code.append("}\n");
        
        // Write the file
        Writer writer;
        if (packageName.isEmpty()) {
            writer = filer.createSourceFile(generatedFactoryName).openWriter();
        } else {
            writer = filer.createSourceFile(packageName + "." + generatedFactoryName).openWriter();
        }
        try {
            writer.write(code.toString());
        } finally {
            writer.close();
        }
        
        return generatedFactoryName;
    }

    /**
     * Generates a CompiledFactory implementation for the given bean class.
     * Returns the simple name of the generated factory class.
     * 
     * Handles default package: if packageName is empty, omits the package declaration
     * and creates the source file without a package prefix.
     */
    private String generateFactory(TypeElement beanClass, Bean annotation, Filer filer) 
            throws IOException {
        
        String packageName = getPackageName(beanClass);
        String className = beanClass.getSimpleName().toString();
        String factoryClassName = className + "$$WarmupFactory";
        
        // Find constructor and dependencies
        ExecutableElement constructor = findInjectableConstructor(beanClass);
        List<? extends VariableElement> parameters = constructor != null 
            ? constructor.getParameters() 
            : Collections.emptyList();
        
        StringBuilder code = new StringBuilder();
        
        // Only add package declaration if not in default package
        if (!packageName.isEmpty()) {
            code.append("package ").append(packageName).append(";\n\n");
        }
        
        code.append("import com.warmup.core.jit.CompiledFactory;\n");
        code.append("import java.lang.Class;\n\n");
        
        // Generate factory class
        code.append("/**\n");
        code.append(" * Auto-generated factory for {@link ").append(className).append("}.\n");
        code.append(" * DO NOT MODIFY - generated by Warmup annotation processor\n");
        code.append(" */\n");
        code.append("@javax.annotation.processing.Generated(\"com.warmup.processor.WarmupProcessor\")\n");
        code.append("public final class ").append(factoryClassName)
            .append(" implements CompiledFactory<").append(className).append("> {\n\n");
        
        // Generate fields for dependency factories (if any)
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("    private final CompiledFactory<").append(paramType)
                .append("> factory").append(i).append(";\n");
        }
        
        if (!parameters.isEmpty()) {
            code.append("\n");
        }
        
        // Generate constructor
        code.append("    public ").append(factoryClassName).append("() {\n");
        // In a full implementation, we would inject dependency factories here
        code.append("        // Dependencies resolved at runtime\n");
        for (int i = 0; i < parameters.size(); i++) {
            code.append("        this.factory").append(i).append(" = null;\n");
        }
        code.append("    }\n\n");
        
        // Generate create method
        code.append("    @Override\n");
        code.append("    public ").append(className).append(" create(Object... dependencies) {\n");
        
        // Cast dependencies
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("        ").append(paramType).append(" arg").append(i)
                .append(" = (").append(paramType).append(") dependencies[").append(i).append("];\n");
        }
        
        // Invoke constructor
        code.append("        return new ").append(className).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) code.append(", ");
            code.append("arg").append(i);
        }
        code.append(");\n");
        code.append("    }\n\n");
        
        // Generate getBeanType method
        code.append("    @Override\n");
        code.append("    public Class<").append(className).append("> getBeanType() {\n");
        code.append("        return ").append(className).append(".class;\n");
        code.append("    }\n\n");
        
        // Generate getDependencyCount method
        code.append("    @Override\n");
        code.append("    public int getDependencyCount() {\n");
        code.append("        return ").append(parameters.size()).append(";\n");
        code.append("    }\n");
        
        code.append("}\n");
        
        // Write the file - handle default package
        Writer writer;
        if (packageName.isEmpty()) {
            writer = filer.createSourceFile(factoryClassName).openWriter();
        } else {
            writer = filer.createSourceFile(packageName + "." + factoryClassName).openWriter();
        }
        try {
            writer.write(code.toString());
        } finally {
            writer.close();
        }
        
        return factoryClassName;
    }

    /**
     * Finds the constructor to use for injection.
     * Prioritizes: @Inject constructor > single public constructor > no-arg constructor
     */
    private ExecutableElement findInjectableConstructor(TypeElement beanClass) {
        ExecutableElement injectableConstructor = null;
        int publicConstructors = 0;
        ExecutableElement publicConstructor = null;
        
        for (Element enclosed : beanClass.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            
            ExecutableElement constructor = (ExecutableElement) enclosed;
            
            // Check for @Inject annotation
            if (constructor.getAnnotation(Inject.class) != null) {
                return constructor;
            }
            
            // Track public constructors
            if (constructor.getModifiers().contains(Modifier.PUBLIC)) {
                publicConstructors++;
                publicConstructor = constructor;
            }
        }
        
        // Return single public constructor or no-arg constructor
        if (publicConstructors == 1 && publicConstructor != null) {
            return publicConstructor;
        }
        
        // Try to find no-arg constructor
        for (Element enclosed : beanClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructor = (ExecutableElement) enclosed;
                if (constructor.getParameters().isEmpty()) {
                    return constructor;
                }
            }
        }
        
        return null;
    }

    private String getPackageName(TypeElement type) {
        return processingEnv.getElementUtils()
            .getPackageOf(type).getQualifiedName().toString();
    }

    /**
     * Derives the bean name from the type element and annotation.
     * 
     * Convention: If @Bean.value() is empty, uses the simple class name as-is.
     * This matches the documentation in Bean.java: "if not specified, the simple class name is used".
     * 
     * For users building BeanDefinition manually: both the simple class name and the fully
     * qualified name are registered as keys, so resolution works regardless of which name was used.
     */
    private String deriveBeanName(TypeElement typeElement, Bean bean) {
        // If explicit name is provided in annotation, use it
        if (bean != null && !bean.value().isEmpty()) {
            return bean.value();
        }
        
        // Otherwise, use simple class name as-is (no decapitalization)
        return typeElement.getSimpleName().toString();
    }

    /**
     * Generates the FactoryRegistrar implementation and service file.
     * This creates a single registrar class that registers all factories from this module.
     * 
     * Handles default package: if packageName is empty, omits the package declaration
     * and uses simple class names for factory references.
     */
    private void generateFactoryRegistrar(Filer filer) throws IOException {
        if (processedBeans.isEmpty()) {
            return;
        }

        // Use the package of the first bean for the registrar
        String registrarPackage = processedBeans.get(0).packageName;
        String registrarClassName = "GeneratedFactoryRegistrar";
        String fullyQualifiedRegistrarName = registrarPackage.isEmpty() 
            ? registrarClassName 
            : registrarPackage + "." + registrarClassName;

        StringBuilder code = new StringBuilder();
        
        // Only add package declaration if not in default package
        if (!registrarPackage.isEmpty()) {
            code.append("package ").append(registrarPackage).append(";\n\n");
        }
        
        code.append("import com.warmup.core.jit.FactoryRegistrar;\n");
        code.append("import com.warmup.core.jit.CompiledFactory;\n");
        code.append("import java.util.function.BiConsumer;\n");
        code.append("import javax.annotation.processing.Generated;\n\n");

        code.append("/**\n");
        code.append(" * Auto-generated factory registrar for this module.\n");
        code.append(" * DO NOT MODIFY - generated by Warmup annotation processor\n");
        code.append(" */\n");
        code.append("@Generated(\"com.warmup.processor.WarmupProcessor\")\n");
        code.append("public class ").append(registrarClassName)
            .append(" implements FactoryRegistrar {\n\n");

        code.append("    @Override\n");
        code.append("    public void registerAll(BiConsumer<String, CompiledFactory<?>> sink) {\n");

        // Register each factory with both simple name and FQN for robustness
        for (BeanInfo beanInfo : processedBeans) {
            String factoryRef = beanInfo.packageName.isEmpty()
                ? beanInfo.factoryClassName
                : beanInfo.packageName + "." + beanInfo.factoryClassName;
            
            // Register with simple class name as primary key
            code.append("        sink.accept(\"").append(beanInfo.beanName)
                .append("\", new ").append(factoryRef).append("());\n");
            
            // Also register with fully qualified name for robustness
            String fqnKey = beanInfo.packageName.isEmpty()
                ? beanInfo.className
                : beanInfo.packageName + "." + beanInfo.className;
            if (!fqnKey.equals(beanInfo.beanName)) {
                code.append("        sink.accept(\"").append(fqnKey)
                    .append("\", new ").append(factoryRef).append("());\n");
            }
        }

        code.append("    }\n");
        code.append("}\n");

        // Write the registrar class
        Writer writer;
        if (registrarPackage.isEmpty()) {
            writer = filer.createSourceFile(registrarClassName).openWriter();
        } else {
            writer = filer.createSourceFile(fullyQualifiedRegistrarName).openWriter();
        }
        try {
            writer.write(code.toString());
        } finally {
            writer.close();
        }

        // Create the service file
        FileObject serviceFile = filer.createResource(
            StandardLocation.CLASS_OUTPUT,
            "",
            "META-INF/services/com.warmup.core.jit.FactoryRegistrar"
        );
        
        Writer serviceWriter = serviceFile.openWriter();
        try {
            serviceWriter.write(fullyQualifiedRegistrarName);
        } finally {
            serviceWriter.close();
        }
    }
}
