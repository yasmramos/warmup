package com.warmup.benchmarks.avaje;

import jakarta.inject.Singleton;

/**
 * Bean with five dependencies, equivalent to ResolutionBenchmark.BeanWithFiveDependencies.
 * Used for Avaje Inject benchmark comparison.
 */
@Singleton
public class AvajeBeanWithFiveDependencies {
    private final AvajeSimpleBean d1, d2, d3, d4, d5;

    public AvajeBeanWithFiveDependencies(AvajeSimpleBean d1, AvajeSimpleBean d2, AvajeSimpleBean d3, AvajeSimpleBean d4, AvajeSimpleBean d5) {
        this.d1 = d1;
        this.d2 = d2;
        this.d3 = d3;
        this.d4 = d4;
        this.d5 = d5;
    }

    public AvajeSimpleBean getD1() { return d1; }
    public AvajeSimpleBean getD2() { return d2; }
    public AvajeSimpleBean getD3() { return d3; }
    public AvajeSimpleBean getD4() { return d4; }
    public AvajeSimpleBean getD5() { return d5; }
}
