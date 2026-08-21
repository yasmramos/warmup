package com.warmup.benchmarks.java;

/**
 * Bean with one dependency for Java baseline benchmarks.
 * Equivalent to AvajeBeanWithOneDependency and WarmupBeanWithOneDependency.
 * Used for pure Java instantiation comparison (no DI framework).
 */
public class JavaBeanWithOneDependency {
    private final JavaSimpleBean dependency;

    public JavaBeanWithOneDependency(JavaSimpleBean dependency) {
        this.dependency = dependency;
    }

    public JavaSimpleBean getDependency() {
        return dependency;
    }
}
