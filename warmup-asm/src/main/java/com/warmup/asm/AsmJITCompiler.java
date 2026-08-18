package com.warmup.asm;

import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.jit.CompilationException;
import com.warmup.core.jit.CompilationStats;
import com.warmup.core.jit.JITCompiler;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ASM-based JIT compiler for dynamic factory generation.
 * 
 * Generates bytecode for CompiledFactory implementations at runtime.
 * Features:
 * - Custom ClassLoader with unload support (prevents metaspace leaks)
 * - Thread-safe compilation cache
 * - Background compilation support via CompletableFuture
 * 
 * Trade-offs:
 * - Uses per-bean ClassLoaders for unload capability (memory overhead)
 * - Cache stratification: L1 (compile-time), L2 (JIT), L3 (pending)
 */
public class AsmJITCompiler implements JITCompiler {

    // Factory cache: bean class -> compiled factory
    private final ConcurrentHashMap<Class<?>, CompiledFactory<?>> factoryCache = new ConcurrentHashMap<>();
    
    // Pending compilations for async tracking
    private final ConcurrentHashMap<Class<?>, CompletableFuture<CompiledFactory<?>>> pendingCompilations = new ConcurrentHashMap<>();
    
    // ClassLoader references for unloading (weak references allow GC)
    private final ConcurrentHashMap<Class<?>, CustomClassLoader> classLoaders = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong totalCompilations = new AtomicLong(0);
    private final AtomicLong successfulCompilations = new AtomicLong(0);
    private final AtomicLong failedCompilations = new AtomicLong(0);
    private final AtomicLong totalCompilationTimeNs = new AtomicLong(0);

    @Override
    public <T> CompiledFactory<T> compile(Class<T> beanClass, Class<?>... dependencyClasses) throws CompilationException {
        // Check cache first
        @SuppressWarnings("unchecked")
        CompiledFactory<T> cached = (CompiledFactory<T>) factoryCache.get(beanClass);
        if (cached != null) {
            return cached;
        }

        long startTime = System.nanoTime();
        totalCompilations.incrementAndGet();

        try {
            // Generate bytecode
            byte[] bytecode = generateFactoryBytecode(beanClass, dependencyClasses);
            
            // Create custom ClassLoader for this bean (allows unloading)
            CustomClassLoader classLoader = new CustomClassLoader(beanClass);
            
            // Define the class
            @SuppressWarnings("unchecked")
            Class<? extends CompiledFactory<T>> factoryClass = (Class<? extends CompiledFactory<T>>) 
                classLoader.defineClass(beanClass.getName() + "$$WarmupFactory", bytecode);
            
            // Instantiate the factory
            CompiledFactory<T> factory = factoryClass.getDeclaredConstructor().newInstance();
            
            // Cache factory and ClassLoader
            factoryCache.put(beanClass, factory);
            classLoaders.put(beanClass, classLoader);
            
            successfulCompilations.incrementAndGet();
            totalCompilationTimeNs.addAndGet(System.nanoTime() - startTime);
            
            return factory;
        } catch (Exception e) {
            failedCompilations.incrementAndGet();
            throw new CompilationException("Failed to compile factory for " + beanClass.getName(), e);
        }
    }

    @Override
    public <T> CompletableFuture<CompiledFactory<T>> compileAsync(Class<T> beanClass, Class<?>... dependencyClasses) {
        // Check if already compiled or compiling
        @SuppressWarnings("unchecked")
        CompiledFactory<T> cached = (CompiledFactory<T>) factoryCache.get(beanClass);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Check if already pending
        @SuppressWarnings("unchecked")
        CompletableFuture<CompiledFactory<T>> pending = 
            (CompletableFuture<CompiledFactory<T>>) pendingCompilations.get(beanClass);
        if (pending != null) {
            return pending;
        }

        // Start new async compilation
        CompletableFuture<CompiledFactory<T>> future = new CompletableFuture<>();
        pendingCompilations.put(beanClass, future);

        CompletableFuture.supplyAsync(() -> {
            try {
                CompiledFactory<T> factory = compile(beanClass, dependencyClasses);
                future.complete(factory);
                return factory;
            } catch (Exception e) {
                future.completeExceptionally(e);
                throw new CompletionException(e);
            } finally {
                pendingCompilations.remove(beanClass);
            }
        });

        return future;
    }

    @Override
    public boolean hasCompiledFactory(Class<?> beanClass) {
        return factoryCache.containsKey(beanClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<CompiledFactory<T>> getCachedFactory(Class<T> beanClass) {
        return Optional.ofNullable((CompiledFactory<T>) factoryCache.get(beanClass));
    }

    @Override
    public boolean unloadFactory(Class<?> beanClass) {
        CompiledFactory<?> removed = factoryCache.remove(beanClass);
        CustomClassLoader classLoader = classLoaders.remove(beanClass);
        
        if (classLoader != null) {
            classLoader.unload();
            return true;
        }
        
        return removed != null;
    }

    @Override
    public CompilationStats getStats() {
        return new CompilationStats(
            totalCompilations.get(),
            successfulCompilations.get(),
            failedCompilations.get(),
            totalCompilationTimeNs.get(),
            factoryCache.size()
        );
    }

    @Override
    public void clear() {
        // Unload all factories
        classLoaders.forEach((beanClass, loader) -> loader.unload());
        classLoaders.clear();
        factoryCache.clear();
        pendingCompilations.clear();
    }

    /**
     * Generates bytecode for a CompiledFactory implementation using ASM.
     * 
     * Generated class structure:
     * ```
     * public class BeanType$$WarmupFactory implements CompiledFactory<BeanType> {
     *     public Object create(Object... dependencies) {
     *         return new BeanType(
     *             (DependencyType1) dependencies[0],
     *             (DependencyType2) dependencies[1],
     *             ...
     *         );
     *     }
     * }
     * ```
     * 
     * @param <T> the bean type
     * @param beanClass the bean class
     * @param dependencyClasses array of dependency classes
     * @return generated bytecode
     */
    public <T> byte[] generateFactoryBytecode(Class<T> beanClass, Class<?>[] dependencyClasses) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        
        String className = beanClass.getName().replace('.', '/') + "$$WarmupFactory";
        String interfaceName = Type.getInternalName(CompiledFactory.class);
        String beanInternalName = Type.getInternalName(beanClass);
        
        // Class declaration: implements CompiledFactory<T>
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, className, null, 
                 "java/lang/Object", new String[]{interfaceName});
        
        // No fields needed - stateless factory
        
        // Default constructor
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxStack(1, 1);
        ctor.visitEnd();
        
        // create method: public T create(Object... dependencies)
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "create", 
                                          "([Ljava/lang/Object;)Ljava/lang/Object;", 
                                          null, null);
        mv.visitCode();
        
        // Load 'this' not needed for static-like operation
        
        // Create new instance of bean
        mv.visitTypeInsn(Opcodes.NEW, beanInternalName);
        mv.visitInsn(Opcodes.DUP);
        
        // Push constructor arguments from dependencies array
        for (int i = 0; i < dependencyClasses.length; i++) {
            // Load dependencies array
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            
            // Push index
            mv.visitLdcInsn(i);
            
            // Load element: dependencies[i]
            mv.visitInsn(Opcodes.AALOAD);
            
            // Cast to dependency type
            String depInternalName = Type.getInternalName(dependencyClasses[i]);
            mv.visitTypeInsn(Opcodes.CHECKCAST, depInternalName);
        }
        
        // Invoke constructor
        String constructorDescriptor = Type.getMethodDescriptor(
            Type.VOID_TYPE, 
            Arrays.stream(dependencyClasses).map(Type::getType).toArray(Type[]::new)
        );
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, beanInternalName, "<init>", constructorDescriptor, false);
        
        // Return the created instance
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxStack(2 + dependencyClasses.length, 2 + dependencyClasses.length);
        mv.visitEnd();
        
        // getBeanType method (default in interface, but we override for efficiency)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getBeanType", 
                            "()Ljava/lang/Class;", 
                            "()Ljava/lang/Class<T>;", null);
        mv.visitCode();
        mv.visitLdcInsn(Type.getType("L" + beanInternalName + ";"));
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxStack(1, 1);
        mv.visitEnd();
        
        // getDependencyCount method
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDependencyCount", 
                            "()I", null, null);
        mv.visitCode();
        mv.visitLdcInsn(dependencyClasses.length);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxStack(1, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        
        return cw.toByteArray();
    }

    /**
     * Custom ClassLoader that supports unloading of generated classes.
     * By clearing the reference and allowing GC, the classes can be unloaded.
     */
    private static class CustomClassLoader extends ClassLoader {
        
        private volatile boolean unloaded = false;

        CustomClassLoader(Class<?> beanClass) {
            // Use bean's ClassLoader as parent for visibility
            super(beanClass.getClassLoader());
        }

        /**
         * Defines a class from bytecode.
         */
        Class<?> defineClass(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }

        /**
         * Marks this ClassLoader as unloaded, allowing GC.
         */
        void unload() {
            unloaded = true;
            // Clear any internal caches if needed
        }
    }
}
