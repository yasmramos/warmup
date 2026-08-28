package com.warmup.benchmarks.warmup;

import com.warmup.annotations.Prototype;
import com.warmup.annotations.Inject;

/**
 * Prototype bean with one dependency for Warmup compile-time benchmark.
 * Used to measure prototype resolution cost with dependencies.
 */
@Prototype
public class WarmupPrototypeBeanWithOneDependency {
    private final WarmupSimpleBean dependency;

    @Inject
    public WarmupPrototypeBeanWithOneDependency(WarmupSimpleBean dependency) {
        this.dependency = dependency;
    }

    public WarmupSimpleBean getDependency() {
        return dependency;
    }
}
