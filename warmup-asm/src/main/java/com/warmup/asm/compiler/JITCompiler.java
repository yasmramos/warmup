package com.warmup.asm.compiler;

import com.warmup.asm.loader.WarmupClassLoader;
import com.warmup.core.factory.CompiledFactory;

/**
 * Interface for JIT bytecode compilation of bean factories.
 * 
 * Implementation uses ASM to generate CompiledFactory implementations at runtime.
 */
public interface JITCompiler {
    
    /**
     * Generates bytecode for a CompiledFactory implementation.
     * 
     * @param <T> Bean type
     * @param beanType The bean class to instantiate
     * @param factoryClassName Fully qualified name for the generated factory class
     * @return Bytecode array ready for class definition
     */
    <T> byte[] generateFactoryBytecode(Class<T> beanType, String factoryClassName);
    
    /**
     * Compiles and instantiates a CompiledFactory from bytecode.
     * 
     * @param <T> Bean type
     * @param beanType The bean class to instantiate
     * @param factoryName Name for the generated factory
     * @return CompiledFactory instance ready to create beans
     */
    <T> CompiledFactory<T> compileFactory(Class<T> beanType, String factoryName);
    
    /**
     * Unloads a previously compiled factory.
     * Helps prevent metaspace leaks in long-running applications.
     * 
     * @param factoryName Name of the factory to unload
     */
    void unloadFactory(String factoryName);
    
    /**
     * Gets the total time spent in compilation.
     * 
     * @return Compilation time in nanoseconds
     */
    long getTotalCompilationTime();
    
    /**
     * Gets the number of active compiled factories.
     * 
     * @return Count of loaded factories
     */
    int getActiveFactoryCount();
}
