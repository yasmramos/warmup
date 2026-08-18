package com.warmup.core.container;

import java.util.function.Consumer;

/**
 * Container metrics for performance monitoring.
 * 
 * @param totalResolutions total number of bean resolutions
 * @param compileTimeHits number of compile-time factory hits
 * @param jitHits number of JIT-compiled factory hits
 * @param fallbackCount number of reflection fallbacks
 * @param averageResolutionTimeNs average resolution time in nanoseconds
 * @param cacheHitRate percentage of cache hits (0.0 to 100.0)
 */
public record ContainerMetrics(
    long totalResolutions,
    long compileTimeHits,
    long jitHits,
    long fallbackCount,
    long averageResolutionTimeNs,
    double cacheHitRate
) {
    public static ContainerMetrics empty() {
        return new ContainerMetrics(0, 0, 0, 0, 0, 0.0);
    }
}
