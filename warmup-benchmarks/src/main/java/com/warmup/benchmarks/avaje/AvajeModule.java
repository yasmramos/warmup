package com.warmup.benchmarks.avaje;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

/**
 * Avaje Inject factory to trigger module generation.
 * This factory is required for Avaje Inject to generate the AvajeInjectModule class.
 */
@Factory
public class AvajeModule {

    @Bean
    public AvajeSimpleBean avajeSimpleBean() {
        return new AvajeSimpleBean();
    }

    @Bean
    public AvajeBeanWithOneDependency avajeBeanWithOneDependency(AvajeSimpleBean simpleBean) {
        return new AvajeBeanWithOneDependency(simpleBean);
    }

    @Bean
    public AvajeBeanWithFiveDependencies avajeBeanWithFiveDependencies(
            AvajeSimpleBean bean1,
            AvajeSimpleBean bean2,
            AvajeSimpleBean bean3,
            AvajeSimpleBean bean4,
            AvajeSimpleBean bean5) {
        return new AvajeBeanWithFiveDependencies(bean1, bean2, bean3, bean4, bean5);
    }
}
