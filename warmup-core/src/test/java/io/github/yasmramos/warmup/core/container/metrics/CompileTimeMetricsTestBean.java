package io.github.yasmramos.warmup.core.container.metrics;

import io.github.yasmramos.warmup.core.jit.CompiledFactory;

/**
 * Simple bean used by {@link CompileTimePathMetricsTest} to verify that compile-time
 * factories discovered via ServiceLoader are accounted for in {@code ContainerMetrics}.
 */
public final class CompileTimeMetricsTestBean {

    private final String value;

    public CompileTimeMetricsTestBean() {
        this("compile-time");
    }

    public CompileTimeMetricsTestBean(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Hand-written stand-in for the bytecode that the annotation processor would emit
     * for this bean. {@code wire} is intentionally implemented so the container marks
     * this factory as wired, which is the state the regression test needs to exercise.
     */
    public static final class Factory implements CompiledFactory<CompileTimeMetricsTestBean> {

        @Override
        public CompileTimeMetricsTestBean create(Object... dependencies) {
            return new CompileTimeMetricsTestBean();
        }

        @Override
        public CompileTimeMetricsTestBean get() {
            return create();
        }

        @Override
        public Class<CompileTimeMetricsTestBean> getBeanType() {
            return CompileTimeMetricsTestBean.class;
        }
    }
}