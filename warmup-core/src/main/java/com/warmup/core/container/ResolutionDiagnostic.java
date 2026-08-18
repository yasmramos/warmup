package com.warmup.core.container;

import com.warmup.core.jit.CompiledFactory;
import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;

/**
 * Diagnostic information about which resolution path was used for a bean.
 * 
 * @param beanName the name of the bean
 * @param beanType the class of the bean
 * @param resolutionPath the path used (COMPILE_TIME, JIT, REFLECTION_FALLBACK)
 * @param compilationTimeNs time spent compiling (if JIT), 0 otherwise
 */
public record ResolutionDiagnostic(
    String beanName,
    Class<?> beanType,
    ResolutionPath resolutionPath,
    long compilationTimeNs
) {
    public enum ResolutionPath {
        /**
         * Factory was generated at compile-time by annotation processor
         */
        COMPILE_TIME,
        
        /**
         * Factory was JIT-compiled at runtime using ASM
         */
        JIT,
        
        /**
         * Fallback to reflection (should be rare in production)
         */
        REFLECTION_FALLBACK
    }
}
