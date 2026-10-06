package io.github.yasmramos.warmup.benchmarks.warmup;

import io.github.yasmramos.warmup.annotations.Prototype;
import io.github.yasmramos.warmup.annotations.Inject;

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
