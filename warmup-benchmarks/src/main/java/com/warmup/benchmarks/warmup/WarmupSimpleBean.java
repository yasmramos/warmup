package com.warmup.benchmarks.warmup;

import com.warmup.annotations.Singleton;

/**
 * Simple singleton bean for Warmup compile-time benchmark.
 * Equivalent to AvajeSimpleBean and ResolutionBenchmark.SimpleBean.
 * Used for fair comparison between Warmup compile-time path and Avaje.
 */
@Singleton
public class WarmupSimpleBean {
    public WarmupSimpleBean() {
    }
}
