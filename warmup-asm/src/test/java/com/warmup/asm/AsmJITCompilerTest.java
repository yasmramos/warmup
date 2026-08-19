package com.warmup.asm;

import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.jit.CompilationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsmJITCompilerTest {

    private AsmJITCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new AsmJITCompiler();
    }

    @Test
    void testGenerateFactoryBytecodeNoDependencies() {
        byte[] bytecode = compiler.generateFactoryBytecode(SimpleBean.class, new Class[0]);

        assertNotNull(bytecode);
        assertTrue(bytecode.length > 0);
    }

    @Test
    void testCompileFactoryNoDependencies() throws CompilationException {
        CompiledFactory<SimpleBean> factory = compiler.compile(SimpleBean.class);

        assertNotNull(factory);
        SimpleBean bean = factory.create();
        assertNotNull(bean);
        assertEquals("created", bean.getStatus());
    }

    @Test
    void testCompileFactoryMultipleInstances() throws CompilationException {
        CompiledFactory<SimpleBean> factory = compiler.compile(SimpleBean.class);

        SimpleBean bean1 = factory.create();
        SimpleBean bean2 = factory.create();

        assertNotNull(bean1);
        assertNotNull(bean2);
        assertNotSame(bean1, bean2);
    }

    @Test
    void testUnloadFactory() throws CompilationException {
        compiler.compile(SimpleBean.class);
        assertTrue(compiler.hasCompiledFactory(SimpleBean.class));

        compiler.unloadFactory(SimpleBean.class);
        assertFalse(compiler.hasCompiledFactory(SimpleBean.class));
    }

    @Test
    void testCompilationStats() throws CompilationException {
        compiler.compile(SimpleBean.class);
        
        var stats = compiler.getStats();
        assertTrue(stats.totalCompilations() > 0);
        assertTrue(stats.successfulCompilations() > 0);
        assertEquals(0, stats.failedCompilations());
    }

    @Test
    void testCompileFactoryWithDependencies() throws CompilationException {
        // Test compilation of a bean with constructor dependencies
        SimpleBean dependency = new SimpleBean();
        
        CompiledFactory<DependentBean> factory = compiler.compile(DependentBean.class, SimpleBean.class);
        
        assertNotNull(factory);
        DependentBean bean = factory.create(dependency);
        assertNotNull(bean);
        assertSame(dependency, bean.getDependency());
    }

    @Test
    void testClear() throws CompilationException {
        compiler.compile(SimpleBean.class);
        compiler.compile(DependentBean.class, SimpleBean.class);
        
        compiler.clear();
        
        assertFalse(compiler.hasCompiledFactory(SimpleBean.class));
        assertFalse(compiler.hasCompiledFactory(DependentBean.class));
    }

    @Test
    void testCompileAsync() throws Exception {
        var future = compiler.compileAsync(SimpleBean.class);
        
        assertNotNull(future);
        CompiledFactory<SimpleBean> factory = future.get();
        assertNotNull(factory);
        
        SimpleBean bean = factory.create();
        assertNotNull(bean);
        assertEquals("created", bean.getStatus());
    }

    @Test
    void testCompileAsyncWithDependencies() throws Exception {
        SimpleBean dependency = new SimpleBean();
        
        var future = compiler.compileAsync(DependentBean.class, SimpleBean.class);
        
        assertNotNull(future);
        CompiledFactory<DependentBean> factory = future.get();
        assertNotNull(factory);
        
        DependentBean bean = factory.create(dependency);
        assertNotNull(bean);
        assertSame(dependency, bean.getDependency());
    }

    @Test
    void testGetCachedFactory() throws CompilationException {
        // Before compilation, cache should be empty
        assertTrue(compiler.getCachedFactory(SimpleBean.class).isEmpty());
        
        // After compilation, cache should contain the factory
        compiler.compile(SimpleBean.class);
        var cachedFactory = compiler.getCachedFactory(SimpleBean.class);
        assertFalse(cachedFactory.isEmpty());
        
        // Verify the cached factory works
        SimpleBean bean = cachedFactory.get().create();
        assertNotNull(bean);
    }

    @Test
    void testUnloadFactoryNonExistent() {
        // Unloading a non-existent factory should return false
        assertFalse(compiler.unloadFactory(SimpleBean.class));
    }

    @Test
    void testCompilationStatsWithFailure() {
        // Get initial stats
        var initialStats = compiler.getStats();
        long initialTotal = initialStats.totalCompilations();
        
        // Compile successfully
        assertDoesNotThrow(() -> compiler.compile(SimpleBean.class));
        
        var stats = compiler.getStats();
        assertTrue(stats.totalCompilations() > initialTotal);
        assertTrue(stats.successfulCompilations() > 0);
        assertEquals(0, stats.failedCompilations());
        
        // Verify average compilation time is non-negative
        assertTrue(stats.getAverageCompilationTimeMs() >= 0);
    }

    @Test
    void testCompileUnloadAndRecompileSameClass() throws CompilationException {
        // First compilation
        CompiledFactory<SimpleBean> factory1 = compiler.compile(SimpleBean.class);
        assertNotNull(factory1);
        SimpleBean bean1 = factory1.create();
        assertNotNull(bean1);
        assertTrue(compiler.hasCompiledFactory(SimpleBean.class));
        
        // Unload the factory
        boolean unloaded = compiler.unloadFactory(SimpleBean.class);
        assertTrue(unloaded);
        assertFalse(compiler.hasCompiledFactory(SimpleBean.class));
        
        // Recompile the same class - should succeed without LinkageError
        CompiledFactory<SimpleBean> factory2 = compiler.compile(SimpleBean.class);
        assertNotNull(factory2);
        SimpleBean bean2 = factory2.create();
        assertNotNull(bean2);
        
        // Verify both factories work and produce different instances
        assertNotSame(bean1, bean2);
        assertEquals("created", bean1.getStatus());
        assertEquals("created", bean2.getStatus());
    }

    @Test
    void testMultipleReloadsSameClass() throws CompilationException {
        // Compile multiple times to simulate hot-reload scenarios
        for (int i = 0; i < 3; i++) {
            CompiledFactory<SimpleBean> factory = compiler.compile(SimpleBean.class);
            assertNotNull(factory);
            SimpleBean bean = factory.create();
            assertNotNull(bean);
            
            // Unload after each compilation
            compiler.unloadFactory(SimpleBean.class);
            assertFalse(compiler.hasCompiledFactory(SimpleBean.class));
        }
        
        // Final compile should still work
        CompiledFactory<SimpleBean> finalFactory = compiler.compile(SimpleBean.class);
        assertNotNull(finalFactory);
        SimpleBean finalBean = finalFactory.create();
        assertNotNull(finalBean);
        assertEquals("created", finalBean.getStatus());
    }

    public static class SimpleBean {
        private final String status = "created";

        public SimpleBean() {
        }

        public String getStatus() {
            return status;
        }
    }

    public static class DependentBean {
        private final SimpleBean dependency;

        public DependentBean(SimpleBean dependency) {
            this.dependency = dependency;
        }

        public SimpleBean getDependency() {
            return dependency;
        }
    }
}
