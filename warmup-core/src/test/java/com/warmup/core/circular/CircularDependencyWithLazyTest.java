package com.warmup.core.circular;

import com.warmup.annotations.Component;
import com.warmup.annotations.Inject;
import com.warmup.annotations.Lazy;
import com.warmup.annotations.PostConstruct;
import com.warmup.asm.AsmJITCompiler;
import com.warmup.core.Warmup;
import com.warmup.core.jit.JITCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for circular dependency resolution with @Lazy injection.
 * Verifies that cycles can be broken by field/setter injection with @Lazy,
 * while constructor-only cycles still fail with CircularDependencyException.
 */
class CircularDependencyWithLazyTest {

    private JITCompiler jitCompiler = new AsmJITCompiler();

    @Test
    void testCircularDependencyWithLazyFieldInjection() {
        Warmup warmup = Warmup.create(jitCompiler);
        
        ServiceA serviceA = warmup.resolve(ServiceA.class);
        ServiceB serviceB = warmup.resolve(ServiceB.class);

        assertNotNull(serviceA);
        assertNotNull(serviceB);
        
        // Verify real instances, not proxies
        assertSame(serviceA, serviceB.getServiceA());
        assertSame(serviceB, serviceA.getServiceB());
        
        // Verify PostConstruct was called after full initialization
        assertTrue(serviceA.isPostConstructCalled());
        assertTrue(serviceB.isPostConstructCalled());
    }

    @Test
    void testCircularDependencyWithLazySetterInjection() {
        Warmup warmup = Warmup.create(jitCompiler);
        
        ServiceC serviceC = warmup.resolve(ServiceC.class);
        ServiceD serviceD = warmup.resolve(ServiceD.class);

        assertNotNull(serviceC);
        assertNotNull(serviceD);
        
        // Verify real instances, not proxies
        assertSame(serviceC, serviceD.getServiceC());
        assertSame(serviceD, serviceC.getServiceD());
        
        // Verify PostConstruct was called after full initialization
        assertTrue(serviceC.isPostConstructCalled());
        assertTrue(serviceD.isPostConstructCalled());
    }

    @Test
    void testMultipleBeansInCircularDependency() {
        Warmup warmup = Warmup.create(jitCompiler);
        
        BeanX beanX = warmup.resolve(BeanX.class);
        BeanY beanY = warmup.resolve(BeanY.class);
        BeanZ beanZ = warmup.resolve(BeanZ.class);

        assertNotNull(beanX);
        assertNotNull(beanY);
        assertNotNull(beanZ);
        
        // Verify the cycle is properly resolved
        assertSame(beanY, beanX.getBeanY());
        assertSame(beanZ, beanY.getBeanZ());
        assertSame(beanX, beanZ.getBeanX());
        
        // Verify all PostConstruct methods were called
        assertTrue(beanX.isPostConstructCalled());
        assertTrue(beanY.isPostConstructCalled());
        assertTrue(beanZ.isPostConstructCalled());
    }

    @Test
    void testConstructorOnlyCircularDependencyFails() {
        Warmup warmup = Warmup.create(jitCompiler);
        
        // This should fail because both dependencies are via constructor
        assertThrows(RuntimeException.class, () -> {
            warmup.resolve(ConstructorA.class);
        });
    }

    @Test
    void testLazyFieldNotAnnotatedStillWorks() {
        Warmup warmup = Warmup.create(jitCompiler);
        
        ServiceE serviceE = warmup.resolve(ServiceE.class);
        ServiceF serviceF = warmup.resolve(ServiceF.class);

        assertNotNull(serviceE);
        assertNotNull(serviceF);
        
        // Verify the relationship works
        assertSame(serviceF, serviceE.getServiceF());
        assertSame(serviceE, serviceF.getServiceE());
    }

    // Beans for lazy field injection test
    @Component
    public static class ServiceA {
        @Inject
        @Lazy
        private ServiceB serviceB;
        
        private boolean postConstructCalled = false;

        public ServiceB getServiceB() {
            return serviceB;
        }

        @PostConstruct
        public void init() {
            postConstructCalled = true;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }
    }

    @Component
    public static class ServiceB {
        @Inject
        @Lazy
        private ServiceA serviceA;
        
        private boolean postConstructCalled = false;

        public ServiceA getServiceA() {
            return serviceA;
        }

        @PostConstruct
        public void init() {
            postConstructCalled = true;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }
    }

    // Beans for lazy setter injection test
    @Component
    public static class ServiceC {
        private ServiceD serviceD;
        private boolean postConstructCalled = false;

        @Inject
        @Lazy
        public void setServiceD(ServiceD serviceD) {
            this.serviceD = serviceD;
        }

        public ServiceD getServiceD() {
            return serviceD;
        }

        @PostConstruct
        public void init() {
            postConstructCalled = true;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }
    }

    @Component
    public static class ServiceD {
        private ServiceC serviceC;
        private boolean postConstructCalled = false;

        @Inject
        @Lazy
        public void setServiceC(ServiceC serviceC) {
            this.serviceC = serviceC;
        }

        public ServiceC getServiceC() {
            return serviceC;
        }

        @PostConstruct
        public void init() {
            postConstructCalled = true;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }
    }

    // Beans for 3-bean circular dependency test
    @Component
    public static class BeanX {
        @Inject
        @Lazy
        private BeanY beanY;
        
        private boolean postConstructCalled = false;

        public BeanY getBeanY() {
            return beanY;
        }

        @PostConstruct
        public void init() {
            postConstructCalled = true;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }
    }

    @Component
    public static class BeanY {
        @Inject
        @Lazy
        private BeanZ beanZ;
        
        private boolean postConstructCalled = false;

        public BeanZ getBeanZ() {
            return beanZ;
        }

        @PostConstruct
        public void init() {
            postConstructCalled = true;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }
    }

    @Component
    public static class BeanZ {
        @Inject
        @Lazy
        private BeanX beanX;
        
        private boolean postConstructCalled = false;

        public BeanX getBeanX() {
            return beanX;
        }

        @PostConstruct
        public void init() {
            postConstructCalled = true;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }
    }

    // Beans for constructor-only circular dependency (should fail)
    @Component
    public static class ConstructorA {
        private final ConstructorB constructorB;

        @Inject
        public ConstructorA(ConstructorB constructorB) {
            this.constructorB = constructorB;
        }

        public ConstructorB getConstructorB() {
            return constructorB;
        }
    }

    @Component
    public static class ConstructorB {
        private final ConstructorA constructorA;

        @Inject
        public ConstructorB(ConstructorA constructorA) {
            this.constructorA = constructorA;
        }

        public ConstructorA getConstructorA() {
            return constructorA;
        }
    }

    // Beans for non-@Lazy field injection (should still work as it's field injection)
    @Component
    public static class ServiceE {
        @Inject
        private ServiceF serviceF;

        public ServiceF getServiceF() {
            return serviceF;
        }
    }

    @Component
    public static class ServiceF {
        @Inject
        private ServiceE serviceE;

        public ServiceE getServiceE() {
            return serviceE;
        }
    }
}
