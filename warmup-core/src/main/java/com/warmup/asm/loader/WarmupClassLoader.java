package com.warmup.asm.loader;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom ClassLoader that supports class unloading to prevent metaspace leaks.
 * Uses a ConcurrentHashMap to track loaded classes for explicit unloading.
 * 
 * Technical Trade-offs:
 * - Memory vs Performance: Tracking classes adds minimal overhead but enables cleanup
 * - Isolation: Each container can use its own ClassLoader for bean isolation
 * - Unloading: Classes are only unloaded when ClassLoader is dereferenced (JVM behavior)
 */
public class WarmupClassLoader extends ClassLoader {
    
    private final ConcurrentHashMap<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> bytecodeCache = new ConcurrentHashMap<>();
    
    public WarmupClassLoader() {
        super(WarmupClassLoader.class.getClassLoader());
    }
    
    public WarmupClassLoader(ClassLoader parent) {
        super(parent);
    }
    
    /**
     * Defines a class from bytecode and caches it atomically.
     * Uses computeIfAbsent to prevent duplicate class definition under concurrency.
     * 
     * @param name Fully qualified class name
     * @param bytecode Class bytecode
     * @return The defined Class object
     */
    public Class<?> defineClass(String name, byte[] bytecode) {
        return loadedClasses.computeIfAbsent(name, n -> {
            Class<?> clazz = defineClass(n, bytecode, 0, bytecode.length,
                                        WarmupClassLoader.class.getProtectionDomain());
            bytecodeCache.put(n, bytecode);
            return clazz;
        });
    }
    
    /**
     * Gets a previously loaded class.
     * 
     * @param name Class name
     * @return The Class object or null if not found
     */
    public Class<?> getLoadedClass(String name) {
        return loadedClasses.get(name);
    }
    
    /**
     * Removes a class from the tracking map.
     * Note: Actual unloading happens when ClassLoader is GC'd.
     * 
     * @param name Class name to remove from tracking
     */
    public void unloadClass(String name) {
        loadedClasses.remove(name);
        bytecodeCache.remove(name);
    }
    
    /**
     * Clears all tracked classes.
     * Used for container reset or hot-reload scenarios.
     */
    public void unloadAll() {
        loadedClasses.clear();
        bytecodeCache.clear();
    }
    
    /**
     * Gets the number of loaded classes.
     * 
     * @return Count of tracked classes
     */
    public int getLoadedClassCount() {
        return loadedClasses.size();
    }
    
    /**
     * Gets bytecode for a loaded class.
     * Useful for debugging or redefinition.
     * 
     * @param name Class name
     * @return Bytecode array or null
     */
    public byte[] getBytecode(String name) {
        return bytecodeCache.get(name);
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> cached = loadedClasses.get(name);
        if (cached != null) {
            return cached;
        }
        return super.findClass(name);
    }
}
