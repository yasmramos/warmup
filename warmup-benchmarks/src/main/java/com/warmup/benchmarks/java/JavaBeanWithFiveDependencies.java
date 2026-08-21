package com.warmup.benchmarks.java;

/**
 * Bean with five dependencies for Java baseline benchmarks.
 * Equivalent to AvajeBeanWithFiveDependencies and WarmupBeanWithFiveDependencies.
 * Used for pure Java instantiation comparison (no DI framework).
 */
public class JavaBeanWithFiveDependencies {
    private final JavaSimpleBean d1, d2, d3, d4, d5;

    public JavaBeanWithFiveDependencies(JavaSimpleBean d1, JavaSimpleBean d2, JavaSimpleBean d3, JavaSimpleBean d4, JavaSimpleBean d5) {
        this.d1 = d1;
        this.d2 = d2;
        this.d3 = d3;
        this.d4 = d4;
        this.d5 = d5;
    }

    public JavaSimpleBean getD1() { return d1; }
    public JavaSimpleBean getD2() { return d2; }
    public JavaSimpleBean getD3() { return d3; }
    public JavaSimpleBean getD4() { return d4; }
    public JavaSimpleBean getD5() { return d5; }
}
