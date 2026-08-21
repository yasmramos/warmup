package com.warmup.benchmarks.warmup;

import com.warmup.annotations.Bean;
import com.warmup.annotations.Inject;

/**
 * Bean with five dependencies for Warmup compile-time benchmark.
 * Equivalent to AvajeBeanWithFiveDependencies and ResolutionBenchmark.BeanWithFiveDependencies.
 * Used for fair comparison between Warmup compile-time path and Avaje.
 */
@Bean
public class WarmupBeanWithFiveDependencies {
    private final WarmupSimpleBean d1, d2, d3, d4, d5;

    @Inject
    public WarmupBeanWithFiveDependencies(
            WarmupSimpleBean d1, WarmupSimpleBean d2, WarmupSimpleBean d3,
            WarmupSimpleBean d4, WarmupSimpleBean d5) {
        this.d1 = d1;
        this.d2 = d2;
        this.d3 = d3;
        this.d4 = d4;
        this.d5 = d5;
    }

    public WarmupSimpleBean getD1() { return d1; }
    public WarmupSimpleBean getD2() { return d2; }
    public WarmupSimpleBean getD3() { return d3; }
    public WarmupSimpleBean getD4() { return d4; }
    public WarmupSimpleBean getD5() { return d5; }
}
