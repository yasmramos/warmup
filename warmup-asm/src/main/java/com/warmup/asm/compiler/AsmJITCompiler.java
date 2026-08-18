package com.warmup.asm.compiler;

import com.warmup.asm.loader.WarmupClassLoader;
import com.warmup.core.factory.CompiledFactory;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.objectweb.asm.Opcodes.*;

/**
 * ASM-based JIT compiler that generates CompiledFactory implementations at runtime.
 * 
 * Architecture:
 * - Uses ClassWriter with COMPUTE_FRAMES for automatic stack map generation
 * - Generates bytecode that directly instantiates beans via constructor calls
 * - Zero reflection in the generated factory's create() method
 * 
 * Thread Safety:
 * - ConcurrentHashMap for tracking compilation times
 * - AtomicLong for compilation counters
 * - WarmupClassLoader is single-threaded per compilation
 * 
 * Memory Management:
 * - Bytecode is cached to enable potential class redefinition
 * - Classes can be unloaded via WarmupClassLoader.unloadClass()
 * - Metaspace usage should be monitored in high-churn scenarios
 */
public class AsmJITCompiler implements JITCompiler {
    
    private final WarmupClassLoader classLoader = new WarmupClassLoader();
    private final ConcurrentHashMap<String, Long> compilationTimes = new ConcurrentHashMap<>();
    private final AtomicLong totalCompilationTime = new AtomicLong(0);
    private final AtomicLong compilationCount = new AtomicLong(0);
    
    @Override
    public <T> byte[] generateFactoryBytecode(Class<T> beanType, String factoryClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String internalClassName = factoryClassName.replace('.', '/');
        String internalBeanName = Type.getInternalName(beanType);
        
        // Generate class declaration
        cw.visit(V17, ACC_PUBLIC | ACC_SUPER, internalClassName,
                null, "java/lang/Object", new String[]{Type.getInternalName(CompiledFactory.class)});
        
        // Generate default constructor
        generateConstructor(cw);
        
        // Generate create() method
        generateCreateMethod(cw, beanType, internalBeanName);
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    /**
     * Generates a default constructor for the factory class.
     */
    private void generateConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }
    
    /**
     * Generates the create() method that instantiates the bean.
     * Handles both default constructors and constructor injection.
     */
    private <T> void generateCreateMethod(ClassWriter cw, Class<T> beanType, String internalBeanName) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "create", "()Ljava/lang/Object;",
                                         "()TT;", null);
        mv.visitCode();
        
        try {
            // Try default constructor first (most common case)
            Constructor<?> constructor = beanType.getConstructor();
            
            // NEW beanType
            mv.visitTypeInsn(NEW, internalBeanName);
            // DUP
            mv.visitInsn(DUP);
            // INVOKESPECIAL beanType.<init>()V
            mv.visitMethodInsn(INVOKESPECIAL, internalBeanName, "<init>", "()V", false);
            // ARETURN
            mv.visitInsn(ARETURN);
            
        } catch (NoSuchMethodException e) {
            // No default constructor - need constructor injection
            // For simplicity, throw RuntimeException in generated code
            generateConstructorInjectionStub(cw, mv, beanType);
        }
        
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }
    
    /**
     * Generates stub for constructor injection scenario.
     * Full implementation requires dependency resolution at compile time.
     */
    private <T> void generateConstructorInjectionStub(ClassWriter cw, MethodVisitor mv, Class<T> beanType) {
        // Generate code to throw IllegalStateException
        mv.visitTypeInsn(NEW, "java/lang/IllegalStateException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Constructor injection not supported for: " + beanType.getName());
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException",
                          "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
    }
    
    @Override
    public <T> CompiledFactory<T> compileFactory(Class<T> beanType, String factoryName) {
        long startTime = System.nanoTime();
        
        // Generate unique factory class name
        String factoryClassName = "com.warmup.asm.generated.WarmupFactory_" +
                                  beanType.getSimpleName() + "_" +
                                  System.nanoTime() + "_" +
                                  Thread.currentThread().getId();
        
        // Generate bytecode
        byte[] bytecode = generateFactoryBytecode(beanType, factoryClassName);
        
        // Define class
        Class<?> factoryClass = classLoader.defineClass(factoryClassName, bytecode);
        
        // Instantiate factory
        try {
            @SuppressWarnings("unchecked")
            CompiledFactory<T> factory = (CompiledFactory<T>)
                factoryClass.getDeclaredConstructor().newInstance();
            
            // Record metrics
            long elapsed = System.nanoTime() - startTime;
            compilationTimes.put(factoryName, elapsed);
            totalCompilationTime.addAndGet(elapsed);
            compilationCount.incrementAndGet();
            
            return factory;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate compiled factory: " + factoryClassName, e);
        }
    }
    
    @Override
    public void unloadFactory(String factoryName) {
        classLoader.unloadClass(factoryName);
        compilationTimes.remove(factoryName);
    }
    
    @Override
    public long getTotalCompilationTime() {
        return totalCompilationTime.get();
    }
    
    @Override
    public int getActiveFactoryCount() {
        return classLoader.getLoadedClassCount();
    }
    
    /**
     * Gets average compilation time per factory.
     * 
     * @return Average time in nanoseconds, or 0 if no compilations
     */
    public long getAverageCompilationTime() {
        long count = compilationCount.get();
        return count > 0 ? totalCompilationTime.get() / count : 0;
    }
    
    /**
     * Gets the WarmupClassLoader for advanced operations.
     * 
     * @return The class loader instance
     */
    public WarmupClassLoader getClassLoader() {
        return classLoader;
    }
}
