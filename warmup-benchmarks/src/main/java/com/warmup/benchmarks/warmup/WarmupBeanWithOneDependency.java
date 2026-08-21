package com.warmup.benchmarks.warmup;

import com.warmup.annotations.Bean;
import com.warmup.annotations.Inject;

/**
 * Bean with one dependency for Warmup compile-time benchmark.
 * Equivalent to AvajeBeanWithOneDependency and ResolutionBenchmark.BeanWithOneDependency.
 * Used for fair comparison between Warmup compile-time path and Avaje.
 */
@Bean
public class WarmupBeanWithOneDependency {
    private final WarmupSimpleBean dependency;

    @Inject
    public WarmupBeanWithOneDependency(WarmupSimpleBean dependency) {
        this.dependency = dependency;
    }

    public WarmupSimpleBean getDependency() {
        return dependency;
    }
}
