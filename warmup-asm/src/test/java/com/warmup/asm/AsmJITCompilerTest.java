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
