package com.warmup.processor;

import com.warmup.annotations.Bean;
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
 * - @Bean: Marks a class as a DI bean
 * - @Inject: Marks constructor parameters for injection
 * 
 * Generated code example:
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
 */
@SupportedAnnotationTypes({
    "com.warmup.annotations.Bean",
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

        BeanInfo(String packageName, String className, String beanName, String factoryClassName) {
            this.packageName = packageName;
            this.className = className;
            this.beanName = beanName;
            this.factoryClassName = factoryClassName;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();
        Messager messager = processingEnv.getMessager();
        
        // Process @Bean annotated classes
        for (Element element : roundEnv.getElementsAnnotatedWith(Bean.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "@Bean only applies to classes", element);
                continue;
            }
            
            TypeElement typeElement = (TypeElement) element;
            Bean bean = typeElement.getAnnotation(Bean.class);
            
            try {
                String factoryClassName = generateFactory(typeElement, bean, filer);
                
                // Store bean info for registrar generation
                String packageName = getPackageName(typeElement);
                String className = typeElement.getSimpleName().toString();
                String beanName = deriveBeanName(typeElement, bean);
                processedBeans.add(new BeanInfo(packageName, className, beanName, factoryClassName));
                
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "Failed to generate factory: " + e.getMessage(), element);
            }
        }
        
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
     * Generates a CompiledFactory implementation for the given bean class.
     * Returns the simple name of the generated factory class.
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
        code.append("package ").append(packageName).append(";\n\n");
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
        
        // Write the file
        Writer writer = filer.createSourceFile(packageName + "." + factoryClassName).openWriter();
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
     * Uses the explicit name from @Bean if provided, otherwise uses the decapitalized class name.
     */
    private String deriveBeanName(TypeElement typeElement, Bean bean) {
        // If explicit name is provided in annotation, use it
        if (bean != null && !bean.value().isEmpty()) {
            return bean.value();
        }
        
        // Otherwise, use decapitalized simple class name (following JavaBeans convention)
        String className = typeElement.getSimpleName().toString();
        if (className.length() == 0) {
            return "";
        }
        
        // Decapitalize: first letter lowercase, rest unchanged
        char[] chars = className.toCharArray();
        if (chars.length > 1 && Character.isUpperCase(chars[0]) && Character.isUpperCase(chars[1])) {
            // Special case: if first two letters are uppercase (e.g., "URL"), keep as is
            return className;
        }
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Generates the FactoryRegistrar implementation and service file.
     * This creates a single registrar class that registers all factories from this module.
     */
    private void generateFactoryRegistrar(Filer filer) throws IOException {
        if (processedBeans.isEmpty()) {
            return;
        }

        // Use the package of the first bean for the registrar
        String registrarPackage = processedBeans.get(0).packageName;
        String registrarClassName = "GeneratedFactoryRegistrar";
        String fullyQualifiedRegistrarName = registrarPackage + "." + registrarClassName;

        StringBuilder code = new StringBuilder();
        code.append("package ").append(registrarPackage).append(";\n\n");
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

        // Register each factory
        for (BeanInfo beanInfo : processedBeans) {
            code.append("        sink.accept(\"").append(beanInfo.beanName)
                .append("\", new ").append(beanInfo.packageName)
                .append(".").append(beanInfo.factoryClassName).append("());\n");
        }

        code.append("    }\n");
        code.append("}\n");

        // Write the registrar class
        Writer writer = filer.createSourceFile(fullyQualifiedRegistrarName).openWriter();
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
