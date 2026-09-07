package com.warmup.core.container;

import com.warmup.annotations.Component;
import com.warmup.annotations.Singleton;
import com.warmup.core.Warmup;
import com.warmup.core.jit.JITCompiler;
import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BeanHandle API - verifies that handles capture resolution once
 * and allow repeated resolutions without ClassValue lookup overhead.
 */
class BeanHandleTest {
    
    private HybridContainer container;
    private JITCompiler jitCompiler;
    
    @BeforeEach
    void setUp() {
        com.warmup.asm.AsmJITCompiler jitCompiler = new com.warmup.asm.AsmJITCompiler();
        HybridContainerConfig config = new HybridContainerConfig.Builder().build();
        container = new HybridContainer(config, jitCompiler);
    }
    
    @Component("testService")
    @Singleton
    public static class TestService {
        public String getValue() {
            return "test-value";
        }
    }
    
    @Component("dependentBean")
    @Singleton
    public static class DependentBean {
        private final TestService service;
        
        public DependentBean(TestService service) {
            this.service = service;
        }
        
        public String getServiceValue() {
            return service.getValue();
        }
    }
    
    @Test
    void testHandleReturnsSameInstanceForSingleton() {
        // Register bean definition
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, "testService");
        container.registerDynamic(definition);
        
        // Get handle
        BeanHandle<TestService> handle = container.handle(TestService.class);
        
        // First resolution
        TestService instance1 = handle.get();
        assertNotNull(instance1);
        assertEquals("test-value", instance1.getValue());
        
        // Second resolution - should return same instance (singleton)
        TestService instance2 = handle.get();
        assertSame(instance1, instance2, "Handle should return same singleton instance");
    }
    
    @Test
    void testHandleAvoidsClassValueLookup() {
        // Register bean definition
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, "testService");
        container.registerDynamic(definition);
        
        // Get handle - this does one ClassValue lookup
        BeanHandle<TestService> handle = container.handle(TestService.class);
        
        // Multiple gets should use cached resolution, not ClassValue
        TestService instance1 = handle.get();
        TestService instance2 = handle.get();
        TestService instance3 = handle.get();
        
        assertSame(instance1, instance2);
        assertSame(instance2, instance3);
    }
    
    @Test
    void testHandleWithDependencies() {
        // Register both beans - DependentBean needs TestService as dependency
        BeanDefinition<TestService> serviceDef = new BeanDefinition<>(TestService.class, "testService");
        // Provide dependency info: the constructor requires TestService
        BeanDefinition<DependentBean> dependentDef = new BeanDefinition<>(
            DependentBean.class, 
            "dependentBean",
            Scope.SINGLETON,
            LifecycleCallbacks.empty(),
            false,
            new Object[] { "testService" }  // Dependency on testService bean
        );
        
        container.registerDynamic(serviceDef);
        container.registerDynamic(dependentDef);
        
        // Get handle for dependent bean
        BeanHandle<DependentBean> handle = container.handle(DependentBean.class);
        
        DependentBean bean = handle.get();
        assertNotNull(bean);
        assertEquals("test-value", bean.getServiceValue());
    }
    
    @Test
    void testHandleDetectsReloadInvalidation() {
        // Register bean
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, "testService");
        container.registerDynamic(definition);
        
        // Get handle
        BeanHandle<TestService> handle = container.handle(TestService.class);
        TestService instance1 = handle.get();
        assertNotNull(instance1);
        
        // Reload the bean (this increments resolution version)
        assertTrue(container.reload("testService"));
        
        // Handle should detect invalidation and re-resolve
        TestService instance2 = handle.get();
        assertNotNull(instance2);
        // Should be a new instance after reload (old one was evicted)
        assertNotSame(instance1, instance2, "Should get new instance after reload");
    }
    
    @Test
    void testHandleIsValidMethod() {
        // Register bean
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, "testService");
        container.registerDynamic(definition);
        
        // Get handle
        BeanHandle<TestService> handle = container.handle(TestService.class);
        
        // Initially valid
        assertTrue(handle.isValid());
        
        // After reload, should be invalid
        container.reload("testService");
        assertFalse(handle.isValid());
    }
    
    @Test
    void testHandleGetTypeAndName() {
        // Register bean
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, "testService");
        container.registerDynamic(definition);
        
        // Get handle
        BeanHandle<TestService> handle = container.handle(TestService.class);
        
        assertEquals(TestService.class, handle.getType());
        assertEquals("testService", handle.getName());
    }
    
    @Test
    void testHandleThrowsOnNotFound() {
        assertThrows(IllegalStateException.class, () -> {
            container.handle(String.class); // Non-existent bean
        });
    }
    
    @Test
    void testHandleWithWarmupIntegration() {
        // Create Warmup instance using builder
        Warmup warmup = Warmup.builder()
            .autoDiscoverFactories(false)
            .build();
        
        // Register a test bean
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, "testService");
        warmup.registerDynamic(definition);
        
        // Get handle via Warmup API
        BeanHandle<TestService> handle = warmup.handle(TestService.class);
        
        TestService instance = handle.get();
        assertNotNull(instance);
        assertEquals("test-value", instance.getValue());
    }
}
