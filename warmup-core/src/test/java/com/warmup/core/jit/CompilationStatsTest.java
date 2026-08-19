package com.warmup.core.jit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CompilationStats class.
 */
class CompilationStatsTest {

    @Test
    void testEmpty() {
        CompilationStats stats = CompilationStats.empty();
        
        assertEquals(0, stats.totalCompilations());
        assertEquals(0, stats.successfulCompilations());
        assertEquals(0, stats.failedCompilations());
        assertEquals(0, stats.totalCompilationTimeNs());
        assertEquals(0, stats.cachedFactories());
    }

    @Test
    void testConstructor() {
        CompilationStats stats = new CompilationStats(
            10L, 8L, 2L, 50000L, 5
        );
        
        assertEquals(10L, stats.totalCompilations());
        assertEquals(8L, stats.successfulCompilations());
        assertEquals(2L, stats.failedCompilations());
        assertEquals(50000L, stats.totalCompilationTimeNs());
        assertEquals(5, stats.cachedFactories());
    }

    @Test
    void testGetAverageCompilationTimeMs() {
        // With successful compilations
        CompilationStats stats = new CompilationStats(
            10L, 5L, 5L, 1000000000L, 3
        );
        double avgTime = stats.getAverageCompilationTimeMs();
        // 1000000000 ns / 5 successful = 200000000 ns = 200 ms
        assertEquals(200.0, avgTime, 0.001);
    }

    @Test
    void testGetAverageCompilationTimeMsWhenNoSuccessfulCompilations() {
        // Edge case: no successful compilations
        CompilationStats stats = new CompilationStats(
            10L, 0L, 10L, 500000000L, 0
        );
        double avgTime = stats.getAverageCompilationTimeMs();
        assertEquals(0.0, avgTime, 0.001);
    }

    @Test
    void testGetSuccessRate() {
        CompilationStats stats = new CompilationStats(
            10L, 8L, 2L, 1000000000L, 3
        );
        double successRate = stats.getSuccessRate();
        // 8 / 10 * 100 = 80%
        assertEquals(80.0, successRate, 0.001);
    }

    @Test
    void testGetSuccessRateWhenNoCompilations() {
        // Edge case: no compilations at all
        CompilationStats stats = CompilationStats.empty();
        double successRate = stats.getSuccessRate();
        assertEquals(100.0, successRate, 0.001);
    }

    @Test
    void testGetSuccessRateAllFailures() {
        CompilationStats stats = new CompilationStats(
            10L, 0L, 10L, 0L, 0
        );
        double successRate = stats.getSuccessRate();
        assertEquals(0.0, successRate, 0.001);
    }

    @Test
    void testGetSuccessRateAllSuccesses() {
        CompilationStats stats = new CompilationStats(
            10L, 10L, 0L, 1000000000L, 5
        );
        double successRate = stats.getSuccessRate();
        assertEquals(100.0, successRate, 0.001);
    }
}
