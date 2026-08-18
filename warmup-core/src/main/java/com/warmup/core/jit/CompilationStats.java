package com.warmup.core.jit;

/**
 * Compilation statistics for diagnostics and monitoring.
 * 
 * @param totalCompilations total number of compilations performed
 * @param successfulCompilations number of successful compilations
 * @param failedCompilations number of failed compilations
 * @param totalCompilationTimeNs total time spent compiling (nanoseconds)
 * @param cachedFactories number of currently cached factories
 */
public record CompilationStats(
    long totalCompilations,
    long successfulCompilations,
    long failedCompilations,
    long totalCompilationTimeNs,
    int cachedFactories
) {
    /**
     * Creates stats with zero values.
     */
    public static CompilationStats empty() {
        return new CompilationStats(0, 0, 0, 0, 0);
    }

    /**
     * Returns average compilation time in milliseconds.
     */
    public double getAverageCompilationTimeMs() {
        if (successfulCompilations == 0) {
            return 0.0;
        }
        return (totalCompilationTimeNs / (double) successfulCompilations) / 1_000_000.0;
    }

    /**
     * Returns success rate as a percentage.
     */
    public double getSuccessRate() {
        if (totalCompilations == 0) {
            return 100.0;
        }
        return (successfulCompilations / (double) totalCompilations) * 100.0;
    }
}
