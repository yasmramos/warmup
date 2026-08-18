package com.warmup.benchmarks;

import com.warmup.asm.AsmJITCompiler;
import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.jit.JITCompiler;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring JIT compilation overhead and factory performance.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class JitCompilationBenchmark {

    private JITCompiler jitCompiler;
    private CompiledFactory<SimpleBean> compiledFactory;
    
    @State(Scope.Thread)
    public static class CompilerState {
        public JITCompiler compiler = new AsmJITCompiler();
        public CompiledFactory<SimpleBean> factory;
        
        @Setup(Level.Iteration)
        public void compileFactory() {
            factory = compiler.compileFactory(SimpleBean.class, "SimpleBeanFactory");
        }
    }

    @Setup(Level.Trial)
    public void setupTrial() {
        jitCompiler = new AsmJITCompiler();
    }

    @Setup(Level.Iteration)
    public void setupIteration(CompilerState state) {
        compiledFactory = state.factory;
    }

    @Benchmark
    public byte[] jitCompileFactory() {
        return jitCompiler.generateFactoryBytecode(SimpleBean.class, "TempFactory");
    }

    @Benchmark
    public Object useCompiledFactory() {
        return compiledFactory.create();
    }

    @Benchmark
    public Object directInstantiation() {
        return new SimpleBean();
    }

    @Benchmark
    public long measureCompilationTime(CompilerState state) {
        long start = System.nanoTime();
        state.compiler.compileFactory(SimpleBean.class, "MeasuredFactory");
        return System.nanoTime() - start;
    }

    /**
     * Simple bean for JIT compilation benchmarks.
     */
    public static class SimpleBean {
        private final String value = "jit-test";
        
        public String getValue() {
            return value;
        }
    }
}
