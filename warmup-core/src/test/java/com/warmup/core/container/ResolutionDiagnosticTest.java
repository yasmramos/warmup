package com.warmup.core.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResolutionDiagnostic class.
 */
class ResolutionDiagnosticTest {

    @Test
    void testConstructor() {
        String beanName = "testBean";
        Class<?> beanType = String.class;
        ResolutionDiagnostic.ResolutionPath path = ResolutionDiagnostic.ResolutionPath.COMPILE_TIME;
        long compilationTimeNs = 1000L;
        
        ResolutionDiagnostic diagnostic = new ResolutionDiagnostic(
            beanName, beanType, path, compilationTimeNs
        );
        
        assertEquals(beanName, diagnostic.beanName());
        assertEquals(beanType, diagnostic.beanType());
        assertEquals(path, diagnostic.resolutionPath());
        assertEquals(compilationTimeNs, diagnostic.compilationTimeNs());
    }

    @Test
    void testResolutionPathEnumValues() {
        // Verify all enum values exist
        ResolutionDiagnostic.ResolutionPath[] paths = 
            ResolutionDiagnostic.ResolutionPath.values();
        
        assertEquals(3, paths.length);
        assertTrue(java.util.List.of(paths).contains(
            ResolutionDiagnostic.ResolutionPath.COMPILE_TIME));
        assertTrue(java.util.List.of(paths).contains(
            ResolutionDiagnostic.ResolutionPath.JIT));
        assertTrue(java.util.List.of(paths).contains(
            ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK));
    }

    @Test
    void testResolutionPathValueOf() {
        assertEquals(
            ResolutionDiagnostic.ResolutionPath.COMPILE_TIME,
            ResolutionDiagnostic.ResolutionPath.valueOf("COMPILE_TIME")
        );
        assertEquals(
            ResolutionDiagnostic.ResolutionPath.JIT,
            ResolutionDiagnostic.ResolutionPath.valueOf("JIT")
        );
        assertEquals(
            ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK,
            ResolutionDiagnostic.ResolutionPath.valueOf("REFLECTION_FALLBACK")
        );
    }

    @Test
    void testWithDifferentPaths() {
        ResolutionDiagnostic compileTimeDiagnostic = new ResolutionDiagnostic(
            "bean1", Object.class, 
            ResolutionDiagnostic.ResolutionPath.COMPILE_TIME, 
            0L
        );
        assertEquals(ResolutionDiagnostic.ResolutionPath.COMPILE_TIME, 
            compileTimeDiagnostic.resolutionPath());
        
        ResolutionDiagnostic jitDiagnostic = new ResolutionDiagnostic(
            "bean2", Object.class, 
            ResolutionDiagnostic.ResolutionPath.JIT, 
            5000L
        );
        assertEquals(ResolutionDiagnostic.ResolutionPath.JIT, 
            jitDiagnostic.resolutionPath());
        assertEquals(5000L, jitDiagnostic.compilationTimeNs());
        
        ResolutionDiagnostic fallbackDiagnostic = new ResolutionDiagnostic(
            "bean3", Object.class, 
            ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK, 
            0L
        );
        assertEquals(ResolutionDiagnostic.ResolutionPath.REFLECTION_FALLBACK, 
            fallbackDiagnostic.resolutionPath());
    }
}
