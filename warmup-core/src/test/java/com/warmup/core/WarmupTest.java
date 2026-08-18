package com.warmup.core;

import com.warmup.core.jit.NoOpJITCompiler;
import com.warmup.core.jit.JITCompiler;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Warmup facade class.
 * Verifies ServiceLoader discovery and fallback behavior.
 */
public class WarmupTest {

    @Test
    public void testCreateWithDefaultSettings() {
        // Should work with ServiceLoader-discovered JITCompiler (AsmJITCompiler if on classpath)
        try (Warmup warmup = Warmup.create()) {
            assertNotNull(warmup);
            assertNotNull(warmup.container());
        }
    }

    @Test
    public void testBuilderWithCustomSettings() {
        Warmup warmup = Warmup.builder()
                .diagnostic(true)
                .maxPendingCompilations(20)
                .build();
        
        assertNotNull(warmup);
        assertNotNull(warmup.container());
        warmup.shutdown();
    }

    @Test
    public void testBuilderWithExplicitJITCompiler() {
        JITCompiler customCompiler = new NoOpJITCompiler();
        
        Warmup warmup = Warmup.builder()
                .jitCompiler(customCompiler)
                .build();
        
        assertNotNull(warmup);
        warmup.shutdown();
    }

    @Test
    public void testCreateWithExplicitJITCompiler() {
        JITCompiler compiler = new NoOpJITCompiler();
        
        try (Warmup warmup = Warmup.create(compiler)) {
            assertNotNull(warmup);
        }
    }

    @Test
    public void testCreateWithFullConfiguration() {
        JITCompiler compiler = new NoOpJITCompiler();
        
        try (Warmup warmup = Warmup.create(compiler, true, 15)) {
            assertNotNull(warmup);
            assertNotNull(warmup.getMetrics());
        }
    }

    @Test
    public void testNoOpFallbackWhenNoProvider() {
        // This test verifies NoOpJITCompiler works correctly
        NoOpJITCompiler noOp = new NoOpJITCompiler();
        
        assertThrows(Exception.class, () -> noOp.compile(String.class));
        assertFalse(noOp.hasCompiledFactory(String.class));
        assertTrue(noOp.getCachedFactory(String.class).isEmpty());
        assertEquals(0, noOp.getStats().totalCompilations());
    }
}
