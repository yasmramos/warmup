package com.warmup.processor;

import com.warmup.annotations.Bean;
import com.warmup.annotations.Factory;
import com.warmup.annotations.Singleton;
import com.warmup.annotations.Prototype;
import com.warmup.annotations.Component;
import com.warmup.annotations.Inject;
import com.warmup.annotations.Primary;
import com.warmup.annotations.Named;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
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
 * Generated factory structure (as bytecode):
 * ```java
 * public class UserService$$WarmupFactory implements CompiledFactory<UserService> {
 *     private Object dep0;  // wired dependency
 *     
 *     public void wire(CompiledFactory<?>[] deps) {
 *         this.dep0 = deps[0];
 *     }
 *     
 *     public UserService get() {
 *         return new UserService((DependencyType) dep0);
 *     }
 *     
 *     public Object create(Object... dependencies) { ... }
 *     public Class<UserService> getBeanType() { ... }
 *     public int getDependencyCount() { ... }
 * }
 * ```
 */
@SupportedAnnotationTypes({
    "com.warmup.annotations.Bean",
    "com.warmup.annotations.Factory",
    "com.warmup.annotations.Singleton",
    "com.warmup.annotations.Prototype",
    "com.warmup.annotations.Component",
    "com.warmup.annotations.Inject",
    "com.warmup.annotations.Primary",
    "com.warmup.annotations.Named"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class WarmupProcessor extends AbstractProcessor {

    private final List<BeanInfo> processedBeans = new ArrayList<>();
    private boolean processingOver = false;
    private FactoryBytecodeGenerator bytecodeGenerator;

    /**
     * Holds information about a processed bean for later registrar generation.
     */
    private static class BeanInfo {
        final String packageName;
        final String className;
        final String beanName;
        final String factoryClassName;
        final String scope;
        final List<String> dependencyNames;
        final boolean isPrimary;

        BeanInfo(String packageName, String className, String beanName, String factoryClassName, String scope, List<String> dependencyNames, boolean isPrimary) {
            this.packageName = packageName;
            this.className = className;
            this.beanName = beanName;
            this.factoryClassName = factoryClassName;
            this.scope = scope;
            this.dependencyNames = dependencyNames != null ? dependencyNames : new ArrayList<>();
            this.isPrimary = isPrimary;
        }
        
        BeanInfo(String packageName, String className, String beanName, String factoryClassName, String scope) {
            this(packageName, className, beanName, factoryClassName, scope, new ArrayList<>(), false);
        }
        
        BeanInfo(String packageName, String className, String beanName, String factoryClassName, String scope, List<String> dependencyNames) {
            this(packageName, className, beanName, factoryClassName, scope, dependencyNames, false);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();
        Messager messager = processingEnv.getMessager();
        
        // Initialize bytecode generator
        bytecodeGenerator = new FactoryBytecodeGenerator(new FactoryBytecodeGenerator.MessagerAdapter() {
            @Override
            public void printError(String message, Element element) {
                messager.printMessage(Diagnostic.Kind.ERROR, message, element);
            }
        });
        
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
        
        // Check if the bean is marked as @Primary
        boolean isPrimary = typeElement.getAnnotation(Primary.class) != null;
        
        // Extract dependency names from constructor and @Inject fields
        List<String> depNames = new ArrayList<>();
        
        // Constructor dependencies
        ExecutableElement constructor = findInjectableConstructor(typeElement);
        if (constructor != null) {
            for (VariableElement param : constructor.getParameters()) {
                String paramType = param.asType().toString();
                int lastDot = paramType.lastIndexOf('.');
                String simpleName = lastDot > 0 ? paramType.substring(lastDot + 1) : paramType;
                
                // Check for @Named annotation on parameter
                Named named = param.getAnnotation(Named.class);
                if (named != null) {
                    depNames.add(named.value());
                } else {
                    // Check for @Inject with value
                    Inject inject = param.getAnnotation(Inject.class);
                    if (inject != null && !inject.value().isEmpty()) {
                        depNames.add(inject.value());
                    } else {
                        depNames.add(simpleName);
                    }
                }
            }
        }
        
        // @Inject field dependencies
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                if (field.getAnnotation(Inject.class) != null) {
                    String fieldType = field.asType().toString();
                    int lastDot = fieldType.lastIndexOf('.');
                    String simpleName = lastDot > 0 ? fieldType.substring(lastDot + 1) : fieldType;
                    
                    // Check for @Named annotation on field
                    Named named = field.getAnnotation(Named.class);
                    if (named != null) {
                        depNames.add(named.value());
                    } else {
                        // Check for @Inject with value
                        Inject inject = field.getAnnotation(Inject.class);
                        if (inject != null && !inject.value().isEmpty()) {
                            depNames.add(inject.value());
                        } else {
                            depNames.add(simpleName);
                        }
                    }
                }
            }
        }
        
        processedBeans.add(new BeanInfo(packageName, className, beanName, factoryClassName, scope, depNames, isPrimary));
    }
    
    /**
     * Stores bean information for @Factory methods.
     */
    private void storeBeanInfoForMethod(TypeElement factoryClass, ExecutableElement method, Bean beanAnnotation, String scope, String factoryClassName) {
        String packageName = getPackageName(factoryClass);
        String factoryClassNameStr = factoryClass.getSimpleName().toString();
        String methodName = method.getSimpleName().toString();
        String beanName = beanAnnotation.value().isEmpty() ? methodName : beanAnnotation.value();
        
        // Check if the bean method is marked as @Primary
        boolean isPrimary = method.getAnnotation(Primary.class) != null;
        
        // Extract dependency names from method parameters
        List<String> depNames = new ArrayList<>();
        for (VariableElement param : method.getParameters()) {
            String paramType = param.asType().toString();
            int lastDot = paramType.lastIndexOf('.');
            String simpleName = lastDot > 0 ? paramType.substring(lastDot + 1) : paramType;
            
            // Check for @Named annotation on parameter
            Named named = param.getAnnotation(Named.class);
            if (named != null) {
                depNames.add(named.value());
            } else {
                // Check for @Inject with value
                Inject inject = param.getAnnotation(Inject.class);
                if (inject != null && !inject.value().isEmpty()) {
                    depNames.add(inject.value());
                } else {
                    depNames.add(simpleName);
                }
            }
        }
        
        // For method-based beans, the bean is registered in the same package as the factory class
        // The return type might be from any package (e.g., java.lang.String), but for registration
        // purposes we use the factory's package and store both the simple name and FQN
        TypeMirror returnTypeMirror = method.getReturnType();
        String returnTypeNameForCode;  // Used in generated code (e.g., "AppConfig.Service" for nested classes)
        String returnTypeFqn;          // Fully qualified name for BeanDefinition
        
        if (returnTypeMirror.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) returnTypeMirror;
            Element returnTypeElement = declaredType.asElement();
            if (returnTypeElement instanceof TypeElement) {
                TypeElement returnTypeElementTyped = (TypeElement) returnTypeElement;
                
                // Get the fully qualified name of the return type
                returnTypeFqn = getFullyQualifiedTypeName(returnTypeElementTyped);
                String returnTypePackage = getPackageName(returnTypeElementTyped);
                
                // For generated code, we need the name as it appears in Java source
                // e.g., test.AppConfig.Service -> className = "AppConfig.Service" (with dots, not $)
                if (returnTypePackage.isEmpty()) {
                    returnTypeNameForCode = returnTypeFqn;
                } else {
                    returnTypeNameForCode = returnTypeFqn.substring(returnTypePackage.length() + 1);
                }
            } else {
                // Fallback for unexpected element types
                String returnTypeStr = returnTypeMirror.toString();
                int lastDot = returnTypeStr.lastIndexOf('.');
                returnTypeNameForCode = lastDot > 0 ? returnTypeStr.substring(lastDot + 1) : returnTypeStr;
                returnTypeFqn = returnTypeStr;
            }
        } else {
            // Fallback for primitive types or other edge cases
            String returnType = returnTypeMirror.toString();
            int lastDot = returnType.lastIndexOf('.');
            returnTypeNameForCode = lastDot > 0 ? returnType.substring(lastDot + 1) : returnType;
            returnTypeFqn = returnType;
        }
        
        // Store both the simple name (for code generation) and FQN (for BeanDefinition)
        // The BeanInfo.className will hold the FQN when the return type is from a different package
        String classNameForRegistration = returnTypeFqn;
        
        processedBeans.add(new BeanInfo(packageName, classNameForRegistration, beanName, factoryClassName, scope, depNames, isPrimary));
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
        
        // Collect @Inject fields
        List<VariableElement> injectFields = new ArrayList<>();
        for (Element enclosed : beanClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                if (field.getAnnotation(Inject.class) != null) {
                    // Validate field is not private or final
                    if (field.getModifiers().contains(Modifier.PRIVATE)) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@Inject fields for compile-time wiring must not be private: " + 
                            field.getSimpleName() + " in " + beanClass.getQualifiedName(), field);
                        continue;
                    }
                    if (field.getModifiers().contains(Modifier.FINAL)) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@Inject fields for compile-time wiring must not be final: " + 
                            field.getSimpleName() + " in " + beanClass.getQualifiedName(), field);
                        continue;
                    }
                    injectFields.add(field);
                }
            }
        }
        
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
        
        // Generate fields for constructor dependency factories
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("    private CompiledFactory<").append(paramType)
                .append("> factory").append(i).append(";\n");
        }
        
        // Generate fields for @Inject field factories
        for (int i = 0; i < injectFields.size(); i++) {
            VariableElement field = injectFields.get(i);
            String fieldType = field.asType().toString();
            code.append("    private CompiledFactory<").append(fieldType)
                .append("> fieldFactory").append(i).append(";\n");
        }
        
        if (!parameters.isEmpty() || !injectFields.isEmpty()) {
            code.append("\n");
        }
        
        // Generate constructor
        code.append("    public ").append(factoryClassName).append("() {\n");
        code.append("        // Dependencies will be wired by container\n");
        for (int i = 0; i < parameters.size(); i++) {
            code.append("        this.factory").append(i).append(" = null;\n");
        }
        for (int i = 0; i < injectFields.size(); i++) {
            code.append("        this.fieldFactory").append(i).append(" = null;\n");
        }
        code.append("    }\n\n");
        
        // Generate wire method
        int totalDeps = parameters.size() + injectFields.size();
        if (totalDeps > 0) {
            code.append("    @Override\n");
            code.append("    public void wire(CompiledFactory<?>[] dependencyFactories) {\n");
            // Wire constructor dependencies first
            for (int i = 0; i < parameters.size(); i++) {
                VariableElement param = parameters.get(i);
                String paramType = param.asType().toString();
                code.append("        this.factory").append(i)
                    .append(" = (CompiledFactory<").append(paramType).append(">) dependencyFactories[").append(i).append("];\n");
            }
            // Then wire field dependencies
            for (int i = 0; i < injectFields.size(); i++) {
                VariableElement field = injectFields.get(i);
                String fieldType = field.asType().toString();
                int fieldIndex = parameters.size() + i;
                code.append("        this.fieldFactory").append(i)
                    .append(" = (CompiledFactory<").append(fieldType).append(">) dependencyFactories[").append(fieldIndex).append("];\n");
            }
            code.append("    }\n\n");
        }
        
        // Generate get method (wired path - no Object[] allocation)
        if (totalDeps > 0) {
            code.append("    @Override\n");
            code.append("    public ").append(className).append(" get() {\n");
            code.append("        ").append(className).append(" instance = new ").append(className).append("(");
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) code.append(", ");
                code.append("factory").append(i).append(".get()");
            }
            code.append(");\n");
            
            // Inject fields
            for (int i = 0; i < injectFields.size(); i++) {
                VariableElement field = injectFields.get(i);
                String fieldName = field.getSimpleName().toString();
                code.append("        instance.").append(fieldName).append(" = fieldFactory").append(i).append(".get();\n");
            }
            
            code.append("        return instance;\n");
            code.append("    }\n\n");
        }
        
        // Generate create method (fallback path for backward compatibility)
        code.append("    @Override\n");
        code.append("    public ").append(className).append(" create(Object... dependencies) {\n");
        
        // Cast dependencies for constructor
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("        ").append(paramType).append(" arg").append(i)
                .append(" = (").append(paramType).append(") dependencies[").append(i).append("];\n");
        }
        
        // Invoke constructor
        code.append("        ").append(className).append(" instance = new ").append(className).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) code.append(", ");
            code.append("arg").append(i);
        }
        code.append(");\n");
        
        // Inject fields from dependencies array (after constructor params)
        for (int i = 0; i < injectFields.size(); i++) {
            VariableElement field = injectFields.get(i);
            String fieldName = field.getSimpleName().toString();
            String fieldType = field.asType().toString();
            int fieldIndex = parameters.size() + i;
            code.append("        instance.").append(fieldName)
                .append(" = (").append(fieldType).append(") dependencies[").append(fieldIndex).append("];\n");
        }
        
        code.append("        return instance;\n");
        code.append("    }\n\n");
        
        // Generate getBeanType method
        code.append("    @Override\n");
        code.append("    public Class<").append(className).append("> getBeanType() {\n");
        code.append("        return ").append(className).append(".class;\n");
        code.append("    }\n\n");
        
        // Generate getDependencyCount method - return total deps (constructor + fields)
        code.append("    @Override\n");
        code.append("    public int getDependencyCount() {\n");
        code.append("        return ").append(totalDeps).append(";\n");
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
        
        // Generate fields for dependency factories (if any)
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramType = param.asType().toString();
            code.append("    private CompiledFactory<").append(paramType)
                .append("> factory").append(i).append(";\n");
        }
        
        if (!parameters.isEmpty()) {
            code.append("\n");
        }
        
        // Generate constructor
        code.append("    public ").append(generatedFactoryName).append("() {\n");
        code.append("        // Factory instance will be created on first call\n");
        code.append("        // Dependencies will be wired by container\n");
        for (int i = 0; i < parameters.size(); i++) {
            code.append("        this.factory").append(i).append(" = null;\n");
        }
        code.append("    }\n\n");
        
        // Generate wire method
        if (!parameters.isEmpty()) {
            code.append("    @Override\n");
            code.append("    public void wire(CompiledFactory<?>[] dependencyFactories) {\n");
            for (int i = 0; i < parameters.size(); i++) {
                VariableElement param = parameters.get(i);
                String paramType = param.asType().toString();
                code.append("        this.factory").append(i)
                    .append(" = (CompiledFactory<").append(paramType).append(">) dependencyFactories[").append(i).append("];\n");
            }
            code.append("    }\n\n");
        }
        
        // Generate get method (wired path - no Object[] allocation)
        if (!parameters.isEmpty()) {
            code.append("    @Override\n");
            code.append("    public ").append(returnType).append(" get() {\n");
            code.append("        if (factoryInstance == null) {\n");
            code.append("            factoryInstance = new ").append(factoryFullClassName).append("();\n");
            code.append("        }\n");
            code.append("        return factoryInstance.").append(methodName).append("(");
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) code.append(", ");
                code.append("factory").append(i).append(".get()");
            }
            code.append(");\n");
            code.append("    }\n\n");
        }
        
        // Generate create method (fallback path for backward compatibility)
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
        // For nested classes, getPackageOf returns the package of the outermost enclosing class
        String packageName = processingEnv.getElementUtils()
            .getPackageOf(type).getQualifiedName().toString();
        return packageName != null ? packageName : "";
    }
    
    /**
     * Gets the fully qualified name of a type element, handling nested classes correctly.
     */
    private String getFullyQualifiedTypeName(TypeElement type) {
        return type.getQualifiedName().toString();
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

        // Group beans by the bean type's package to generate registrars in the correct package
        // This ensures that the generated registrar can reference the bean classes without import issues
        Map<String, List<BeanInfo>> beansByPackage = new LinkedHashMap<>();
        for (BeanInfo beanInfo : processedBeans) {
            beansByPackage.computeIfAbsent(beanInfo.packageName, k -> new ArrayList<>()).add(beanInfo);
        }
        
        // Generate one registrar per bean package
        for (Map.Entry<String, List<BeanInfo>> entry : beansByPackage.entrySet()) {
            String registrarPackage = entry.getKey();
            List<BeanInfo> packageBeans = entry.getValue();
            
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
            code.append("import com.warmup.core.registry.BeanDefinition;\n");
            code.append("import com.warmup.core.scope.Scope;\n");
            code.append("import java.util.function.BiConsumer;\n");
            code.append("import javax.annotation.processing.Generated;\n\n");

            // Add imports for any external types referenced in BeanDefinition
            // Track which types we've already imported to avoid duplicates
            Set<String> importedTypes = new HashSet<>();
            
            code.append("/**\n");
            code.append(" * Auto-generated factory registrar for this module.\n");
            code.append(" * DO NOT MODIFY - generated by Warmup annotation processor\n");
            code.append(" */\n");
            code.append("@Generated(\"com.warmup.processor.WarmupProcessor\")\n");
            code.append("public class ").append(registrarClassName)
                .append(" implements FactoryRegistrar {\n\n");

            code.append("    @Override\n");
            code.append("    public void registerAll(BiConsumer<BeanDefinition<?>, CompiledFactory<?>> sink) {\n");

            // Register each factory with both simple name and FQN for robustness
            for (BeanInfo beanInfo : packageBeans) {
                String factoryRef = beanInfo.packageName.isEmpty()
                    ? beanInfo.factoryClassName
                    : beanInfo.packageName + "." + beanInfo.factoryClassName;
                
                // Build dependency names array for BeanDefinition
                String depsArray;
                if (beanInfo.dependencyNames.isEmpty()) {
                    depsArray = "new String[0]";
                } else {
                    depsArray = "new String[]{";
                    for (int i = 0; i < beanInfo.dependencyNames.size(); i++) {
                        if (i > 0) depsArray += ", ";
                        depsArray += "\"" + beanInfo.dependencyNames.get(i) + "\"";
                    }
                    depsArray += "}";
                }
                
                // Create BeanDefinition with type, name, scope, and dependencies
                // Build bean type reference for .class access
                // beanInfo.className already contains the FQN when the return type is from a different package
                // so we use it directly without prefixing with packageName
                String beanType = beanInfo.className;
                
                String scopeEnum = beanInfo.scope.equals("prototype") ? "Scope.PROTOTYPE" : "Scope.SINGLETON";
                
                code.append("        sink.accept(\n");
                code.append("            new BeanDefinition<>(").append(beanType).append(".class, \"")
                    .append(beanInfo.beanName).append("\", ").append(scopeEnum)
                    .append(", com.warmup.core.lifecycle.LifecycleCallbacks.empty(), ")
                    .append(beanInfo.isPrimary ? "true" : "false").append(", ")
                    .append(depsArray).append("),\n");
                code.append("            new ").append(factoryRef).append("()\n");
                code.append("        );\n");
            }

            code.append("    }\n");
            code.append("}\n");

            // Write the registrar file
            FileObject registrarFile = filer.createSourceFile(fullyQualifiedRegistrarName);
            try (Writer writer = registrarFile.openWriter()) {
                writer.write(code.toString());
            }
        }

        // Create the service file - register all registrars (one per package)
        // For simplicity, we'll just use the first registrar as the main entry point
        // In a real scenario, each module would have its own META-INF/services file
        String firstRegistrarPackage = beansByPackage.keySet().iterator().next();
        String firstRegistrarName = firstRegistrarPackage.isEmpty() 
            ? "GeneratedFactoryRegistrar" 
            : firstRegistrarPackage + ".GeneratedFactoryRegistrar";
        
        FileObject serviceFile = filer.createResource(
            StandardLocation.CLASS_OUTPUT,
            "",
            "META-INF/services/com.warmup.core.jit.FactoryRegistrar"
        );
        
        Writer serviceWriter = serviceFile.openWriter();
        try {
            serviceWriter.write(firstRegistrarName);
        } finally {
            serviceWriter.close();
        }
    }
}
