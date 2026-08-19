package com.warmup.core.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContainerMetrics class.
 */
class ContainerMetricsTest {

    @Test
    void testEmpty() {
        ContainerMetrics metrics = ContainerMetrics.empty();
        
        assertEquals(0, metrics.totalResolutions());
        assertEquals(0, metrics.compileTimeHits());
        assertEquals(0, metrics.jitHits());
        assertEquals(0, metrics.fallbackCount());
        assertEquals(0, metrics.averageResolutionTimeNs());
        assertEquals(0.0, metrics.cacheHitRate());
    }

    @Test
    void testConstructor() {
        ContainerMetrics metrics = new ContainerMetrics(
            100L, 50L, 30L, 20L, 5000L, 75.5
        );
        
        assertEquals(100L, metrics.totalResolutions());
        assertEquals(50L, metrics.compileTimeHits());
        assertEquals(30L, metrics.jitHits());
        assertEquals(20L, metrics.fallbackCount());
        assertEquals(5000L, metrics.averageResolutionTimeNs());
        assertEquals(75.5, metrics.cacheHitRate());
    }

    @Test
    void testRecordAccessors() {
        long totalResolutions = 200L;
        long compileTimeHits = 100L;
        long jitHits = 80L;
        long fallbackCount = 20L;
        long averageResolutionTimeNs = 10000L;
        double cacheHitRate = 90.0;
        
        ContainerMetrics metrics = new ContainerMetrics(
            totalResolutions, compileTimeHits, jitHits, 
            fallbackCount, averageResolutionTimeNs, cacheHitRate
        );
        
        assertEquals(totalResolutions, metrics.totalResolutions());
        assertEquals(compileTimeHits, metrics.compileTimeHits());
        assertEquals(jitHits, metrics.jitHits());
        assertEquals(fallbackCount, metrics.fallbackCount());
        assertEquals(averageResolutionTimeNs, metrics.averageResolutionTimeNs());
        assertEquals(cacheHitRate, metrics.cacheHitRate());
    }
}
