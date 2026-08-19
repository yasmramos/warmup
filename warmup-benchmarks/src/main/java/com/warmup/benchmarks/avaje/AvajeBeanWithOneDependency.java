package com.warmup.benchmarks.avaje;

import jakarta.inject.Singleton;

/**
 * Bean with one dependency, equivalent to ResolutionBenchmark.BeanWithOneDependency.
 * Used for Avaje Inject benchmark comparison.
 */
@Singleton
public class AvajeBeanWithOneDependency {
    private final AvajeSimpleBean dependency;

    public AvajeBeanWithOneDependency(AvajeSimpleBean dependency) {
        this.dependency = dependency;
    }

    public AvajeSimpleBean getDependency() {
        return dependency;
    }
}
