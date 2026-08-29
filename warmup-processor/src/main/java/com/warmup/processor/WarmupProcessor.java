package com.warmup.processor;

import com.warmup.annotations.Bean;
import com.warmup.annotations.Factory;
import com.warmup.annotations.Singleton;
import com.warmup.annotations.Prototype;
import com.warmup.annotations.Component;
import com.warmup.annotations.Inject;
import com.warmup.annotations.Primary;
import com.warmup.annotations.Named;
import com.warmup.annotations.Value;

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
    "com.warmup.annotations.Named",
    "com.warmup.annotations.Provider",
    "com.warmup.annotations.Lazy",
    "com.warmup.annotations.Value",
    "com.warmup.annotations.Profile",
    "com.warmup.annotations.Conditional"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class WarmupProcessor extends AbstractProcessor {

    private final List<BeanInfo> processedBeans = new ArrayList<>();
    private boolean processingOver = false;
    private FactoryBytecodeGenerator bytecodeGenerator;

    /**
     * Holds information about an injectable method for factory generation.
     */
    private static class InjectMethodInfo {
        final String methodName;
        final int paramCount;
        final List<String> paramTypes;
        final List<String> depNames;
        final List<Boolean> isProviderDependency;
        final List<Boolean> isValueDependency;
        final List<String> valueExpressions;
        
        InjectMethodInfo(String methodName, int paramCount, List<String> paramTypes, List<String> depNames, 
                        List<Boolean> isProviderDependency, List<Boolean> isValueDependency, List<String> valueExpressions) {
            this.methodName = methodName;
            this.paramCount = paramCount;
            this.paramTypes = paramTypes != null ? paramTypes : new ArrayList<>();
            this.depNames = depNames != null ? depNames : new ArrayList<>();
            this.isProviderDependency = isProviderDependency != null ? isProviderDependency : new ArrayList<>();
            this.isValueDependency = isValueDependency != null ? isValueDependency : new ArrayList<>();
            this.valueExpressions = valueExpressions != null ? valueExpressions : new ArrayList<>();
        }
    }
    
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
        final List<Boolean> isProviderDependency;
        final List<Boolean> isValueDependency;
        final List<String> valueExpressions;
        final boolean isPrimary;
        final List<InjectMethodInfo> injectMethods;
        final List<String> profiles;
        final List<String> conditionClassNames;

        BeanInfo(String packageName, String className, String beanName, String factoryClassName, String scope, 
                List<String> dependencyNames, List<Boolean> isProviderDependency, List<Boolean> isValueDependency, 
                List<String> valueExpressions, boolean isPrimary, List<InjectMethodInfo> injectMethods,
                List<String> profiles, List<String> conditionClassNames) {
            this.packageName = packageName;
            this.className = className;
            this.beanName = beanName;
            this.factoryClassName = factoryClassName;
            this.scope = scope;
            this.dependencyNames = dependencyNames != null ? dependencyNames : new ArrayList<>();
            this.isProviderDependency = isProviderDependency != null ? isProviderDependency : new ArrayList<>();
            this.isValueDependency = isValueDependency != null ? isValueDependency : new ArrayList<>();
            this.valueExpressions = valueExpressions != null ? valueExpressions : new ArrayList<>();
            this.isPrimary = isPrimary;
            this.injectMethods = injectMethods != null ? injectMethods : new ArrayList<>();
            this.profiles = profiles != null ? profiles : new ArrayList<>();
            this.conditionClassNames = conditionClassNames != null ? conditionClassNames : new ArrayList<>();
        }
        
        BeanInfo(String packageName, String className, String beanName, String factoryClassName, String scope) {
            this(packageName, className, beanName, factoryClassName, scope, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        
        BeanInfo(String packageName, String className, String beanName, String factoryClassName, String scope, List<String> dependencyNames) {
            this(packageName, className, beanName, factoryClassName, scope, dependencyNames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
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
                String factoryClassName = generateFactoryForClassBytecode(typeElement, "SINGLETON", singleton.value(), filer);
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
                String factoryClassName = generateFactoryForClassBytecode(typeElement, "PROTOTYPE", prototype.value(), filer);
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
                String factoryClassName = generateFactoryForClassBytecode(typeElement, "SINGLETON", component.value(), filer);
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
                    String factoryClassName = generateFactoryForMethodBytecode(factoryClass, method, beanAnnotation, filer);
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
        
        // Extract dependency names from constructor, @Inject fields, and @Inject methods
        List<String> depNames = new ArrayList<>();
        List<Boolean> providerFlags = new ArrayList<>();
        List<InjectMethodInfo> injectMethods = new ArrayList<>();
        
        // Constructor dependencies
        ExecutableElement constructor = findInjectableConstructor(typeElement);
        if (constructor != null) {
            for (VariableElement param : constructor.getParameters()) {
                extractDependencyInfo(param, depNames, providerFlags);
            }
        }
        
        // @Inject field dependencies
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                if (field.getAnnotation(Inject.class) != null) {
                    extractFieldDependencyInfo(field, depNames, providerFlags);
                }
            }
        }
        
        // @Inject method dependencies
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                if (method.getAnnotation(Inject.class) != null) {
                    InjectMethodInfo methodInfo = extractMethodDependencyInfo(method, depNames, providerFlags);
                    if (methodInfo != null) {
                        injectMethods.add(methodInfo);
                    }
                }
            }
        }
        
        processedBeans.add(new BeanInfo(packageName, className, beanName, factoryClassName, scope, depNames, providerFlags, new ArrayList<>(), new ArrayList<>(), isPrimary, injectMethods));
    }
    
    /**
     * Extracts dependency info from a parameter element.
     */
    private void extractDependencyInfo(VariableElement param, List<String> depNames, List<Boolean> providerFlags) {
        String paramType = param.asType().toString();
        int lastDot = paramType.lastIndexOf('.');
        String simpleName = lastDot > 0 ? paramType.substring(lastDot + 1) : paramType;
        
        // Check if parameter is a Provider<T>
        boolean isProvider = isProviderType(param.asType());
        
        // Check for @Value annotation on parameter (configuration value, not bean reference)
        Value value = param.getAnnotation(Value.class);
        if (value != null) {
            depNames.add(value.value()); // Store the expression as the "name"
            providerFlags.add(false);
            return;
        }
        
        // Check for @Named annotation on parameter
        Named named = param.getAnnotation(Named.class);
        if (named != null) {
            depNames.add(named.value());
        } else if (isProvider) {
            // For Provider, extract the generic type T
            String providerTypeName = extractProviderGenericType(param.asType());
            depNames.add(providerTypeName);
        } else {
            // Check for @Inject with value
            Inject inject = param.getAnnotation(Inject.class);
            if (inject != null && !inject.value().isEmpty()) {
                depNames.add(inject.value());
            } else {
                depNames.add(simpleName);
            }
        }
        providerFlags.add(isProvider);
    }
    
    /**
     * Extracts dependency info from a field element.
     */
    private void extractFieldDependencyInfo(VariableElement field, List<String> depNames, List<Boolean> providerFlags) {
        String fieldType = field.asType().toString();
        int lastDot = fieldType.lastIndexOf('.');
        String simpleName = lastDot > 0 ? fieldType.substring(lastDot + 1) : fieldType;
        
        // Check if field is a Provider<T>
        boolean isProvider = isProviderType(field.asType());
        
        // Check for @Value annotation on field (configuration value, not bean reference)
        Value value = field.getAnnotation(Value.class);
        if (value != null) {
            depNames.add(value.value()); // Store the expression as the "name"
            providerFlags.add(false);
            return;
        }
        
        // Check for @Named annotation on field
        Named named = field.getAnnotation(Named.class);
        if (named != null) {
            depNames.add(named.value());
        } else if (isProvider) {
            // For Provider, extract the generic type T
            String providerTypeName = extractProviderGenericType(field.asType());
            depNames.add(providerTypeName);
        } else {
            // Check for @Inject with value
            Inject inject = field.getAnnotation(Inject.class);
            if (inject != null && !inject.value().isEmpty()) {
                depNames.add(inject.value());
            } else {
                depNames.add(simpleName);
            }
        }
        providerFlags.add(isProvider);
    }
    
    /**
     * Extracts dependency info from an @Inject method and returns method info.
     * Returns null if the method has no parameters.
     */
    private InjectMethodInfo extractMethodDependencyInfo(ExecutableElement method, List<String> depNames, List<Boolean> providerFlags) {
        String methodName = method.getSimpleName().toString();
        int paramCount = method.getParameters().size();
        
        if (paramCount == 0) {
            return null; // No parameters to inject
        }
        
        List<String> paramTypes = new ArrayList<>();
        List<String> methodDepNames = new ArrayList<>();
        List<Boolean> methodProviderFlags = new ArrayList<>();
        List<Boolean> methodValueFlags = new ArrayList<>();
        List<String> methodValueExpressions = new ArrayList<>();
        
        for (VariableElement param : method.getParameters()) {
            String paramType = param.asType().toString();
            int lastDot = paramType.lastIndexOf('.');
            String simpleName = lastDot > 0 ? paramType.substring(lastDot + 1) : paramType;
            paramTypes.add(paramType);
            
            // Check if parameter is a Provider<T>
            boolean isProvider = isProviderType(param.asType());
            
            // Check for @Value annotation on parameter (configuration value, not bean reference)
            Value value = param.getAnnotation(Value.class);
            if (value != null) {
                methodDepNames.add(value.value());
                depNames.add(value.value());
                methodProviderFlags.add(false);
                providerFlags.add(false);
                methodValueFlags.add(true);
                methodValueExpressions.add(value.value());
                continue;
            }
            
            methodValueFlags.add(false);
            methodValueExpressions.add(null);
            
            // Check for @Named annotation on parameter
            Named named = param.getAnnotation(Named.class);
            if (named != null) {
                methodDepNames.add(named.value());
                depNames.add(named.value());
            } else if (isProvider) {
                // For Provider, extract the generic type T
                String providerTypeName = extractProviderGenericType(param.asType());
                methodDepNames.add(providerTypeName);
                depNames.add(providerTypeName);
            } else {
                // Check for @Inject with value
                Inject inject = param.getAnnotation(Inject.class);
                if (inject != null && !inject.value().isEmpty()) {
                    methodDepNames.add(inject.value());
                    depNames.add(inject.value());
                } else {
                    methodDepNames.add(simpleName);
                    depNames.add(simpleName);
                }
            }
            methodProviderFlags.add(isProvider);
            providerFlags.add(isProvider);
        }
        
        return new InjectMethodInfo(methodName, paramCount, paramTypes, methodDepNames, methodProviderFlags, methodValueFlags, methodValueExpressions);
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
        List<Boolean> providerFlags = new ArrayList<>();
        for (VariableElement param : method.getParameters()) {
            String paramType = param.asType().toString();
            int lastDot = paramType.lastIndexOf('.');
            String simpleName = lastDot > 0 ? paramType.substring(lastDot + 1) : paramType;
            
            // Check if parameter is a Provider<T>
            boolean isProvider = isProviderType(param.asType());
            
            // Check for @Named annotation on parameter
            Named named = param.getAnnotation(Named.class);
            if (named != null) {
                depNames.add(named.value());
            } else if (isProvider) {
                // For Provider, extract the generic type T
                String providerTypeName = extractProviderGenericType(param.asType());
                depNames.add(providerTypeName);
            } else {
                // Check for @Inject with value
                Inject inject = param.getAnnotation(Inject.class);
                if (inject != null && !inject.value().isEmpty()) {
                    depNames.add(inject.value());
                } else {
                    depNames.add(simpleName);
                }
            }
            providerFlags.add(isProvider);
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
        
        processedBeans.add(new BeanInfo(packageName, classNameForRegistration, beanName, factoryClassName, scope, depNames, providerFlags, new ArrayList<>(), new ArrayList<>(), isPrimary, new ArrayList<>()));
    }
    
    /**
     * Generates bytecode for a class-level bean factory and writes it as a .class file.
     * Returns the simple name of the generated factory class.
     */
    private String generateFactoryForClassBytecode(TypeElement beanClass, String scope, String explicitName, Filer filer) 
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
        
        // Collect @Inject methods
        List<ExecutableElement> injectMethods = new ArrayList<>();
        for (Element enclosed : beanClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                if (method.getAnnotation(Inject.class) != null) {
                    // Validate method has parameters
                    if (method.getParameters().isEmpty()) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                            "@Inject method without parameters will have no effect: " + 
                            method.getSimpleName() + " in " + beanClass.getQualifiedName(), method);
                        continue;
                    }
                    injectMethods.add(method);
                }
            }
        }
        
        // Generate bytecode using FactoryBytecodeGenerator
        byte[] bytecode = bytecodeGenerator.generateFactoryForClassBytecode(
            beanClass, scope, explicitName, constructor, injectFields, injectMethods);
        
        // Write the .class file
        FileObject classFile;
        if (packageName.isEmpty()) {
            classFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", factoryClassName + ".class");
        } else {
            classFile = filer.createResource(StandardLocation.CLASS_OUTPUT, packageName, factoryClassName + ".class");
        }
        try (OutputStream os = classFile.openOutputStream()) {
            os.write(bytecode);
        }
        
        return factoryClassName;
    }
    
    /**
     * Generates bytecode for a @Bean method factory and writes it as a .class file.
     * Returns the simple name of the generated factory class.
     */
    private String generateFactoryForMethodBytecode(TypeElement factoryClass, ExecutableElement method, Bean beanAnnotation, Filer filer) 
            throws IOException {
        
        String packageName = getPackageName(factoryClass);
        String factoryClassNameStr = factoryClass.getSimpleName().toString();
        String methodName = method.getSimpleName().toString();
        String generatedFactoryName = factoryClassNameStr + "$$" + methodName + "$$WarmupFactory";
        
        // Generate bytecode using FactoryBytecodeGenerator
        byte[] bytecode = bytecodeGenerator.generateFactoryForMethodBytecode(factoryClass, method, beanAnnotation);
        
        // Write the .class file
        FileObject classFile;
        if (packageName.isEmpty()) {
            classFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", generatedFactoryName + ".class");
        } else {
            classFile = filer.createResource(StandardLocation.CLASS_OUTPUT, packageName, generatedFactoryName + ".class");
        }
        try (OutputStream os = classFile.openOutputStream()) {
            os.write(bytecode);
        }
        
        return generatedFactoryName;
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

    /**
     * Checks if a type is a Provider<T>.
     */
    private boolean isProviderType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) type;
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        return "com.warmup.annotations.Provider".equals(typeElement.getQualifiedName().toString());
    }
    
    /**
     * Extracts the generic type T from a Provider<T>.
     */
    private String extractProviderGenericType(TypeMirror providerType) {
        if (providerType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        DeclaredType declaredType = (DeclaredType) providerType;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return null;
        }
        TypeMirror genericType = typeArguments.get(0);
        if (genericType.getKind() == TypeKind.DECLARED) {
            DeclaredType genericDeclaredType = (DeclaredType) genericType;
            Element element = genericDeclaredType.asElement();
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                String fqn = typeElement.getQualifiedName().toString();
                int lastDot = fqn.lastIndexOf('.');
                return lastDot > 0 ? fqn.substring(lastDot + 1) : fqn;
            }
        }
        return genericType.toString();
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
        
        // Generate one registrar per package and collect their fully qualified names
        List<String> allRegistrarNames = new ArrayList<>();
        for (Map.Entry<String, List<BeanInfo>> entry : beansByPackage.entrySet()) {
            String registrarPackage = entry.getKey();
            List<BeanInfo> packageBeans = entry.getValue();
            
            String registrarClassName = "GeneratedFactoryRegistrar";
            String fullyQualifiedRegistrarName = registrarPackage.isEmpty() 
                ? registrarClassName 
                : registrarPackage + "." + registrarClassName;
            
            allRegistrarNames.add(fullyQualifiedRegistrarName);
            
            // Generate registrar as bytecode
            byte[] registrarBytecode = generateRegistrarBytecode(registrarPackage, packageBeans);
            
            // Write the .class file
            String resourcePath = registrarPackage.replace('.', '/') + "/" + registrarClassName + ".class";
            if (registrarPackage.isEmpty()) {
                resourcePath = registrarClassName + ".class";
            }
            
            FileObject classFile = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                registrarPackage,
                registrarClassName + ".class"
            );
            try (OutputStream os = classFile.openOutputStream()) {
                os.write(registrarBytecode);
            }
        }

        // Create the service file - register ALL registrars (one per package)
        // ServiceLoader expects one FQN per line
        FileObject serviceFile = filer.createResource(
            StandardLocation.CLASS_OUTPUT,
            "",
            "META-INF/services/com.warmup.core.jit.FactoryRegistrar"
        );
        
        Writer serviceWriter = serviceFile.openWriter();
        try {
            // Write all registrar FQNs, one per line
            for (String registrarName : allRegistrarNames) {
                serviceWriter.write(registrarName);
                serviceWriter.write("\n");
            }
        } finally {
            serviceWriter.close();
        }
    }
    
    /**
     * Generates bytecode for the GeneratedFactoryRegistrar class.
     * 
     * @param packageName the package name for the registrar
     * @param beans the list of bean infos to register
     * @return the generated bytecode
     */
    private byte[] generateRegistrarBytecode(String packageName, List<BeanInfo> beans) {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
        
        String registrarClassName = "GeneratedFactoryRegistrar";
        String registrarInternalName = packageName.isEmpty() ? registrarClassName : packageName.replace('.', '/') + "/" + registrarClassName;
        String interfaceName = "com/warmup/core/jit/FactoryRegistrar";
        
        // Class declaration: public class GeneratedFactoryRegistrar implements FactoryRegistrar
        cw.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC, registrarInternalName, null,
                "java/lang/Object", new String[]{interfaceName});
        
        // Default constructor
        org.objectweb.asm.MethodVisitor ctor = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        
        // registerAll method: public void registerAll(BiConsumer<BeanDefinition<?>, CompiledFactory<?>> sink)
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "registerAll",
                "(Ljava/util/function/BiConsumer;)V",
                "(Ljava/util/function/BiConsumer<Lcom/warmup/core/registry/BeanDefinition<*>;Lcom/warmup/core/jit/CompiledFactory<*>;>;)V", null);
        mv.visitCode();
        
        // For each bean, create BeanDefinition and call sink.accept()
        for (BeanInfo beanInfo : beans) {
            // Load sink
            mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
            
            // Create new BeanDefinition
            String beanTypeInternal = beanInfo.className.replace('.', '/');
            String beanTypeFqn = beanInfo.className;
            
            // Push beanType.class onto stack
            mv.visitLdcInsn(org.objectweb.asm.Type.getType("L" + beanTypeInternal + ";"));
            
            // Push bean name
            mv.visitLdcInsn(beanInfo.beanName);
            
            // Push scope enum
            String scopeEnum = beanInfo.scope.equals("PROTOTYPE") ? "PROTOTYPE" : "SINGLETON";
            mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, "com/warmup/core/scope/Scope", scopeEnum, "Lcom/warmup/core/scope/Scope;");
            
            // Push LifecycleCallbacks.empty()
            mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "com/warmup/core/lifecycle/LifecycleCallbacks", "empty", 
                    "()Lcom/warmup/core/lifecycle/LifecycleCallbacks;", false);
            
            // Push isPrimary flag
            mv.visitLdcInsn(beanInfo.isPrimary);
            
            // Create dependency names array
            if (beanInfo.dependencyNames.isEmpty()) {
                mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
            } else {
                mv.visitLdcInsn(beanInfo.dependencyNames.size());
                mv.visitTypeInsn(org.objectweb.asm.Opcodes.ANEWARRAY, "java/lang/String");
                for (int i = 0; i < beanInfo.dependencyNames.size(); i++) {
                    mv.visitInsn(org.objectweb.asm.Opcodes.DUP);
                    mv.visitLdcInsn(i);
                    mv.visitLdcInsn(beanInfo.dependencyNames.get(i));
                    mv.visitInsn(org.objectweb.asm.Opcodes.AASTORE);
                }
            }
            
            // Invoke BeanDefinition constructor
            String beanDefInternal = "com/warmup/core/registry/BeanDefinition";
            StringBuilder beanDefCtorDesc = new StringBuilder("(Ljava/lang/Class;Ljava/lang/String;Lcom/warmup/core/scope/Scope;Lcom/warmup/core/lifecycle/LifecycleCallbacks;Z[Ljava/lang/String;)V");
            mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, beanDefInternal, "<init>", beanDefCtorDesc.toString(), false);
            
            // Create new factory instance
            String factoryInternal = beanInfo.factoryClassName.replace('.', '/');
            mv.visitTypeInsn(org.objectweb.asm.Opcodes.NEW, factoryInternal);
            mv.visitInsn(org.objectweb.asm.Opcodes.DUP);
            mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, factoryInternal, "<init>", "()V", false);
            
            // Call sink.accept(beanDef, factory)
            mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEINTERFACE, "java/util/function/BiConsumer", "accept", 
                    "(Ljava/lang/Object;Ljava/lang/Object;)V", true);
        }
        
        mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        mv.visitMaxs(10, 2);
        mv.visitEnd();
        
        cw.visitEnd();
        
        return cw.toByteArray();
    }
}
