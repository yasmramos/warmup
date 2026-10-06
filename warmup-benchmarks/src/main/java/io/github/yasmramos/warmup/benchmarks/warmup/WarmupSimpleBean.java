package io.github.yasmramos.warmup.benchmarks.warmup;

import io.github.yasmramos.warmup.annotations.Singleton;

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
