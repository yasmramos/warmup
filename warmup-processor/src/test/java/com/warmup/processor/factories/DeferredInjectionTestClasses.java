package com.warmup.processor.factories;

import com.warmup.annotations.Component;
import com.warmup.annotations.Inject;
import com.warmup.annotations.Lazy;

/**
 * Test classes for verifying factory generation with deferred dependencies.
 */
public class DeferredInjectionTestClasses {

    @Component
    public static class LazyFieldBean {
        @Inject
        @Lazy
        private DependencyBean dependency;

        public DependencyBean getDependency() {
            return dependency;
        }
    }

    @Component
    public static class LazySetterBean {
        private DependencyBean dependency;

        @Inject
        @Lazy
        public void setDependency(DependencyBean dependency) {
            this.dependency = dependency;
        }

        public DependencyBean getDependency() {
            return dependency;
        }
    }

    @Component
    public static class MixedBean {
        private final ConstructorDependency constructorDep;
        
        @Inject
        @Lazy
        private LazyDependency lazyFieldDep;
        
        private SetterDependency setterDep;

        @Inject
        public MixedBean(ConstructorDependency constructorDep) {
            this.constructorDep = constructorDep;
        }

        @Inject
        @Lazy
        public void setSetterDep(SetterDependency setterDep) {
            this.setterDep = setterDep;
        }

        public ConstructorDependency getConstructorDep() {
            return constructorDep;
        }

        public LazyDependency getLazyFieldDep() {
            return lazyFieldDep;
        }

        public SetterDependency getSetterDep() {
            return setterDep;
        }
    }

    @Component
    public static class NoDeferredBean {
        private final DependencyBean dependency;

        @Inject
        public NoDeferredBean(DependencyBean dependency) {
            this.dependency = dependency;
        }

        public DependencyBean getDependency() {
            return dependency;
        }
    }

    @Component
    public static class DependencyBean {}

    @Component
    public static class ConstructorDependency {}

    @Component
    public static class LazyDependency {}

    @Component
    public static class SetterDependency {}
}
