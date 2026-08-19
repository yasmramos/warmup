package com.warmup.core.jit;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NoOpJITCompiler class.
 */
class NoOpJITCompilerTest {

    private final NoOpJITCompiler compiler = new NoOpJITCompiler();

    @Test
    void testCompileThrowsCompilationException() {
        assertThrows(CompilationException.class, () -> {
            compiler.compile(String.class);
        });
    }

    @Test
    void testCompileAsyncReturnsExceptionalFuture() {
        CompletableFuture<CompiledFactory<String>> future = 
            compiler.compileAsync(String.class);
        
        assertTrue(future.isCompletedExceptionally());
        
        ExecutionException exception = assertThrows(
            ExecutionException.class, 
            () -> future.get()
        );
        
        assertTrue(exception.getCause() instanceof CompilationException);
    }

    @Test
    void testHasCompiledFactoryAlwaysFalse() {
        assertFalse(compiler.hasCompiledFactory(String.class));
        assertFalse(compiler.hasCompiledFactory(Object.class));
    }

    @Test
    void testGetCachedFactoryAlwaysEmpty() {
        Optional<CompiledFactory<String>> result = 
            compiler.getCachedFactory(String.class);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testUnloadFactoryAlwaysFalse() {
        assertFalse(compiler.unloadFactory(String.class));
        assertFalse(compiler.unloadFactory(Object.class));
    }

    @Test
    void testGetStatsReturnsZeros() {
        CompilationStats stats = compiler.getStats();
        
        assertEquals(0, stats.totalCompilations());
        assertEquals(0, stats.successfulCompilations());
        assertEquals(0, stats.failedCompilations());
        assertEquals(0, stats.totalCompilationTimeNs());
        assertEquals(0, stats.cachedFactories());
    }

    @Test
    void testClearDoesNothing() {
        // Clear should be a no-op, just verify it doesn't throw
        assertDoesNotThrow(() -> compiler.clear());
        
        // Stats should still be zeros after clear
        CompilationStats stats = compiler.getStats();
        assertEquals(0, stats.totalCompilations());
    }
}
