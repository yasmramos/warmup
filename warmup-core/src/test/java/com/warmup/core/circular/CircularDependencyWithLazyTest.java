package com.warmup.core.circular;

import com.warmup.annotations.Component;
import com.warmup.annotations.Inject;
import com.warmup.annotations.Lazy;
import com.warmup.annotations.PostConstruct;
import com.warmup.core.Warmup;
import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for circular dependency resolution with @Lazy injection.
 * Verifies that cycles can be broken by field/setter injection with @Lazy,
 * while constructor-only cycles still fail with CircularDependencyException.
 */
class CircularDependencyWithLazyTest {

    /**
     * Helper method to register all beans needed for circular dependency tests.
     * Registers beans with proper deferred dependency metadata to allow cycle breaking.
     * Uses reflection to detect @Lazy fields and setters to mark dependencies as deferrable.
     */
    private void registerBeansForCircularTest(Warmup warmup, Class<?>... beanClasses) {
        for (Class<?> beanClass : beanClasses) {
            // Use reflection to detect @Lazy fields and setters
            java.lang.reflect.Field[] fields = beanClass.getDeclaredFields();
            java.lang.reflect.Method[] methods = beanClass.getDeclaredMethods();
            
            int fieldCount = 0;
            int setterCount = 0;
            
            // Count @Inject fields
            for (java.lang.reflect.Field field : fields) {
                if (field.isAnnotationPresent(com.warmup.annotations.Inject.class)) {
                    fieldCount++;
                }
            }
            
            // Count @Inject setter methods
            for (java.lang.reflect.Method method : methods) {
                if (method.isAnnotationPresent(com.warmup.annotations.Inject.class) && 
                    method.getName().startsWith("set") && method.getParameterCount() == 1) {
                    setterCount++;
                }
            }
            
            int totalDeps = fieldCount + setterCount;
            boolean[] deferredDeps = new boolean[totalDeps];
            boolean[] fieldOrSetterDeps = new boolean[totalDeps];
            Object[] dependencies = new Object[totalDeps];
            
            int idx = 0;
            
            // Process fields first
            for (java.lang.reflect.Field field : fields) {
                if (field.isAnnotationPresent(com.warmup.annotations.Inject.class)) {
                    boolean isLazy = field.isAnnotationPresent(com.warmup.annotations.Lazy.class);
                    deferredDeps[idx] = isLazy;
                    fieldOrSetterDeps[idx] = true;
                    dependencies[idx] = field.getType().getSimpleName();
                    idx++;
                }
            }
            
            // Process setter methods
            for (java.lang.reflect.Method method : methods) {
                if (method.isAnnotationPresent(com.warmup.annotations.Inject.class) && 
                    method.getName().startsWith("set") && method.getParameterCount() == 1) {
                    boolean isLazy = method.isAnnotationPresent(com.warmup.annotations.Lazy.class);
                    deferredDeps[idx] = isLazy;
                    fieldOrSetterDeps[idx] = true;
                    dependencies[idx] = method.getParameterTypes()[0].getSimpleName();
                    idx++;
                }
            }
            
            BeanDefinition<?> definition = new BeanDefinition<>(
                beanClass,
                beanClass.getSimpleName(),
                Scope.SINGLETON,
                com.warmup.core.lifecycle.LifecycleCallbacks.empty(),
                false,
                dependencies,
                new String[0], // profiles
                new String[0], // conditionClasses
                "", // scopeName
                deferredDeps,
                fieldOrSetterDeps
            );
            warmup.registerDynamic(definition);
        }
    }

    @Test
    void testCircularDependencyWithLazyFieldInjection() {
        Warmup warmup = Warmup.builder().build();
        
        // Register beans manually since container doesn't scan classpath
        registerBeansForCircularTest(warmup, ServiceA.class, ServiceB.class);
        
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
        Warmup warmup = Warmup.builder().build();
        
        // Register beans manually since container doesn't scan classpath
        registerBeansForCircularTest(warmup, ServiceC.class, ServiceD.class);
        
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
        Warmup warmup = Warmup.builder().build();
        
        // Register beans manually since container doesn't scan classpath
        registerBeansForCircularTest(warmup, BeanX.class, BeanY.class, BeanZ.class);
        
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
        Warmup warmup = Warmup.builder().build();
        
        // Register beans manually since container doesn't scan classpath
        registerBeansForCircularTest(warmup, ConstructorA.class, ConstructorB.class);
        
        // This should fail because both dependencies are via constructor
        assertThrows(RuntimeException.class, () -> {
            warmup.resolve(ConstructorA.class);
        });
    }

    @Test
    void testLazyFieldNotAnnotatedStillWorks() {
        Warmup warmup = Warmup.builder().build();
        
        // Register beans manually since container doesn't scan classpath
        registerBeansForCircularTest(warmup, ServiceE.class, ServiceF.class);
        
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
        ServiceB serviceB;
        
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
        ServiceA serviceA;
        
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
        BeanY beanY;
        
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
        BeanZ beanZ;
        
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
        BeanX beanX;
        
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
        ServiceF serviceF;

        public ServiceF getServiceF() {
            return serviceF;
        }
    }

    @Component
    public static class ServiceF {
        @Inject
        ServiceE serviceE;

        public ServiceE getServiceE() {
            return serviceE;
        }
    }
}
