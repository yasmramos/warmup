package com.warmup.asm.loader;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WarmupClassLoader class.
 */
class WarmupClassLoaderTest {

    @Test
    void testDefineClass() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        // Generate simple bytecode for a class
        byte[] bytecode = generateSimpleClassBytecode("com.warmup.test.SimpleClass");
        
        Class<?> clazz = classLoader.defineClass("com.warmup.test.SimpleClass", bytecode);
        
        assertNotNull(clazz);
        assertEquals("com.warmup.test.SimpleClass", clazz.getName());
    }

    @Test
    void testGetLoadedClass() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        byte[] bytecode = generateSimpleClassBytecode("com.warmup.test.CachedClass");
        classLoader.defineClass("com.warmup.test.CachedClass", bytecode);
        
        Class<?> cached = classLoader.getLoadedClass("com.warmup.test.CachedClass");
        assertNotNull(cached);
        assertEquals("com.warmup.test.CachedClass", cached.getName());
    }

    @Test
    void testGetLoadedClassReturnsNullForNonExistent() {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        Class<?> result = classLoader.getLoadedClass("non.existent.Class");
        assertNull(result);
    }

    @Test
    void testUnloadClass() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        byte[] bytecode = generateSimpleClassBytecode("com.warmup.test.UnloadClass");
        classLoader.defineClass("com.warmup.test.UnloadClass", bytecode);
        
        assertNotNull(classLoader.getLoadedClass("com.warmup.test.UnloadClass"));
        
        classLoader.unloadClass("com.warmup.test.UnloadClass");
        
        assertNull(classLoader.getLoadedClass("com.warmup.test.UnloadClass"));
    }

    @Test
    void testUnloadAll() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        byte[] bytecode1 = generateSimpleClassBytecode("com.warmup.test.Class1");
        byte[] bytecode2 = generateSimpleClassBytecode("com.warmup.test.Class2");
        
        classLoader.defineClass("com.warmup.test.Class1", bytecode1);
        classLoader.defineClass("com.warmup.test.Class2", bytecode2);
        
        assertEquals(2, classLoader.getLoadedClassCount());
        
        classLoader.unloadAll();
        
        assertEquals(0, classLoader.getLoadedClassCount());
        assertNull(classLoader.getLoadedClass("com.warmup.test.Class1"));
        assertNull(classLoader.getLoadedClass("com.warmup.test.Class2"));
    }

    @Test
    void testGetLoadedClassCount() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        assertEquals(0, classLoader.getLoadedClassCount());
        
        byte[] bytecode1 = generateSimpleClassBytecode("com.warmup.test.CountClass1");
        byte[] bytecode2 = generateSimpleClassBytecode("com.warmup.test.CountClass2");
        
        classLoader.defineClass("com.warmup.test.CountClass1", bytecode1);
        assertEquals(1, classLoader.getLoadedClassCount());
        
        classLoader.defineClass("com.warmup.test.CountClass2", bytecode2);
        assertEquals(2, classLoader.getLoadedClassCount());
    }

    @Test
    void testGetBytecode() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        byte[] originalBytecode = generateSimpleClassBytecode("com.warmup.test.BytecodeClass");
        classLoader.defineClass("com.warmup.test.BytecodeClass", originalBytecode);
        
        byte[] retrievedBytecode = classLoader.getBytecode("com.warmup.test.BytecodeClass");
        
        assertNotNull(retrievedBytecode);
        assertArrayEquals(originalBytecode, retrievedBytecode);
    }

    @Test
    void testGetBytecodeReturnsNullForNonExistent() {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        byte[] result = classLoader.getBytecode("non.existent.Class");
        assertNull(result);
    }

    @Test
    void testFindClass() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        byte[] bytecode = generateSimpleClassBytecode("com.warmup.test.FindClass");
        classLoader.defineClass("com.warmup.test.FindClass", bytecode);
        
        Class<?> clazz = classLoader.findClass("com.warmup.test.FindClass");
        assertNotNull(clazz);
        assertEquals("com.warmup.test.FindClass", clazz.getName());
    }

    @Test
    void testFindClassThrowsClassNotFoundExceptionForNonExistent() {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        assertThrows(ClassNotFoundException.class, () -> {
            classLoader.findClass("non.existent.Class");
        });
    }

    @Test
    void testConstructorWithParentClassLoader() throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        WarmupClassLoader classLoader = new WarmupClassLoader(parent);
        
        byte[] bytecode = generateSimpleClassBytecode("com.warmup.test.ParentClass");
        Class<?> clazz = classLoader.defineClass("com.warmup.test.ParentClass", bytecode);
        
        assertNotNull(clazz);
        assertEquals(parent, clazz.getClassLoader().getParent());
    }

    @Test
    void testDefineClassReturnsCachedClass() throws Exception {
        WarmupClassLoader classLoader = new WarmupClassLoader();
        
        byte[] bytecode = generateSimpleClassBytecode("com.warmup.test.CacheClass");
        Class<?> first = classLoader.defineClass("com.warmup.test.CacheClass", bytecode);
        Class<?> second = classLoader.defineClass("com.warmup.test.CacheClass", bytecode);
        
        assertSame(first, second);
    }

    /**
     * Generates bytecode for a simple empty class.
     */
    private byte[] generateSimpleClassBytecode(String className) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internalName = className.replace('.', '/');
        
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, 
                 "java/lang/Object", null);
        
        // Default constructor
        var ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }
}
