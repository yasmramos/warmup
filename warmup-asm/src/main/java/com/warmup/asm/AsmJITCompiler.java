package com.warmup.asm;

import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.jit.CompilationException;
import com.warmup.core.jit.CompilationStats;
import com.warmup.core.jit.JITCompiler;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandles;
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
 * - Uses MethodHandles.Lookup.defineHiddenClass for efficient class definition without per-bean ClassLoader
 * - Thread-safe compilation cache
 * - Background compilation support via CompletableFuture
 * 
 * Trade-offs:
 * - Hidden classes allow individual unloading without ClassLoader overhead
 * - Cache stratification: L1 (compile-time), L2 (JIT), L3 (pending)
 */
public class AsmJITCompiler implements JITCompiler {

    // Factory cache: bean class -> compiled factory
    private final ConcurrentHashMap<Class<?>, CompiledFactory<?>> factoryCache = new ConcurrentHashMap<>();
    
    // Pending compilations for async tracking
    private final ConcurrentHashMap<Class<?>, CompletableFuture<CompiledFactory<?>>> pendingCompilations = new ConcurrentHashMap<>();
    
    // Track hidden class holders for unloading (weak references allow GC)
    // Key: bean class, Value: the hidden class instance (held to prevent premature GC)
    private final ConcurrentHashMap<Class<?>, Class<?>> hiddenClasses = new ConcurrentHashMap<>();
    
    // Generation counter for hot-reload: avoids LinkageError by using unique class names per reload
    private final ConcurrentHashMap<Class<?>, AtomicLong> generationCounters = new ConcurrentHashMap<>();
    
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
            // Get or create generation counter for this bean class (for hot-reload support)
            AtomicLong generationCounter = generationCounters.computeIfAbsent(beanClass, k -> new AtomicLong(0));
            long generation = generationCounter.incrementAndGet();
            
            // Generate bytecode with unique class name per generation to avoid LinkageError on reload
            byte[] bytecode = generateFactoryBytecode(beanClass, dependencyClasses, generation);
            
            // Use MethodHandles.Lookup.defineHiddenClass to define the factory without a custom ClassLoader
            // This allows individual unloading when the Lookup/hidden class becomes unreachable
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(beanClass, MethodHandles.lookup());
            
            // Define as hidden class - no need for custom ClassLoader
            // Hidden classes are unloaded when their Lookup and all instances become unreachable
            // Note: defineHiddenClass doesn't use the class name directly; it's inferred from bytecode
            Class<?> factoryClass = lookup.defineHiddenClass(bytecode, true, MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
            
            // Instantiate the factory
            @SuppressWarnings("unchecked")
            CompiledFactory<T> factory = (CompiledFactory<T>) factoryClass.getDeclaredConstructor().newInstance();
            
            // Cache factory and track hidden class reference for potential unload tracking
            factoryCache.put(beanClass, factory);
            hiddenClasses.put(beanClass, factoryClass);
            
            successfulCompilations.incrementAndGet();
            totalCompilationTimeNs.addAndGet(System.nanoTime() - startTime);
            
            return factory;
        } catch (Exception e) {
            failedCompilations.incrementAndGet();
            throw new CompilationException("Failed to compile factory for " + beanClass.getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<CompiledFactory<T>> compileAsync(Class<T> beanClass, Class<?>... dependencyClasses) {
        // Check if already compiled or compiling
        CompiledFactory<T> cached = (CompiledFactory<T>) factoryCache.get(beanClass);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Check if already pending
        CompletableFuture<CompiledFactory<?>> pending = pendingCompilations.get(beanClass);
        if (pending != null) {
            return (CompletableFuture)(pending);
        }

        // Start new async compilation
        CompletableFuture<CompiledFactory<?>> future = new CompletableFuture<>();
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

        return (CompletableFuture)(future);
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
        Class<?> hiddenClass = hiddenClasses.remove(beanClass);
        
        // Hidden classes are unloaded when the Lookup and all instances become unreachable
        // Removing from the map allows GC to collect them
        return removed != null || hiddenClass != null;
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
        // Clear all caches - hidden classes will be GC'd when no longer referenced
        hiddenClasses.clear();
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
        return generateFactoryBytecode(beanClass, dependencyClasses, 0);
    }
    
    /**
     * Generates bytecode for a CompiledFactory implementation using ASM.
     * 
     * Generated class structure:
     * ```
     * public class BeanType$$WarmupFactory$N implements CompiledFactory<BeanType> {
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
     * @param generation the generation number (used in class name to avoid LinkageError on reload)
     * @return generated bytecode
     */
    public <T> byte[] generateFactoryBytecode(Class<T> beanClass, Class<?>[] dependencyClasses, long generation) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        
        String className = beanClass.getName().replace('.', '/') + "$$WarmupFactory$" + generation;
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
        ctor.visitMaxs(1, 1);
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
        mv.visitMaxs(2 + dependencyClasses.length, 2 + dependencyClasses.length);
        mv.visitEnd();
        
        // getBeanType method (default in interface, but we override for efficiency)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getBeanType", 
                            "()Ljava/lang/Class;", 
                            "()Ljava/lang/Class<T>;", null);
        mv.visitCode();
        mv.visitLdcInsn(Type.getType("L" + beanInternalName + ";"));
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        // getDependencyCount method
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDependencyCount", 
                            "()I", null, null);
        mv.visitCode();
        mv.visitLdcInsn(dependencyClasses.length);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        
        return cw.toByteArray();
    }
}
