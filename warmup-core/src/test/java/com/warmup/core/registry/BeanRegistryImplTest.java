package com.warmup.core.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BeanRegistryImplTest {

    private BeanRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BeanRegistryImpl();
    }

    @Test
    void testRegisterAndGetDefinition() {
        String beanName = "testBean";
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, beanName);

        registry.register(definition);

        Optional<BeanDefinition<TestService>> result = registry.getDefinition(beanName);
        assertTrue(result.isPresent());
        assertEquals(TestService.class, result.get().type());
    }

    @Test
    void testGetInstanceSingleton() {
        String beanName = "singletonBean";
        TestService expected = new TestService("singleton");
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, beanName);

        registry.register(definition);

        TestService result1 = registry.getInstance(beanName, () -> expected);
        TestService result2 = registry.getInstance(beanName, () -> expected);

        assertSame(expected, result1);
        assertSame(expected, result2);
        assertSame(result1, result2);
    }

    @Test
    void testGetInstancePrototype() {
        String beanName = "prototypeBean";
        AtomicInteger counter = new AtomicInteger(0);
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, beanName, com.warmup.core.scope.Scope.PROTOTYPE);

        registry.register(definition);

        TestService result1 = registry.getInstance(beanName, () -> new TestService("proto-" + counter.incrementAndGet()));
        TestService result2 = registry.getInstance(beanName, () -> new TestService("proto-" + counter.incrementAndGet()));

        assertNotNull(result1);
        assertNotNull(result2);
        assertNotSame(result1, result2);
    }

    @Test
    void testContains() {
        assertFalse(registry.contains("nonexistent"));

        registry.register(new BeanDefinition<>(TestService.class, "existing"));

        assertTrue(registry.contains("existing"));
    }

    @Test
    void testRemove() {
        registry.register(new BeanDefinition<>(TestService.class, "toRemove"));
        assertTrue(registry.contains("toRemove"));

        registry.remove("toRemove");
        assertFalse(registry.contains("toRemove"));
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        String beanName = "concurrentBean";
        TestService sharedInstance = new TestService("concurrent");
        registry.register(new BeanDefinition<>(TestService.class, beanName));

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    TestService bean = registry.getInstance(beanName, () -> sharedInstance);
                    if (bean != null) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(threadCount, successCount.get());
        executor.shutdown();
    }

    @Test
    void testClear() {
        registry.register(new BeanDefinition<>(TestService.class, "bean1"));
        registry.register(new BeanDefinition<>(TestService.class, "bean2"));
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
        assertFalse(registry.contains("bean1"));
    }

    @Test
    void testGetDefinitionByType() {
        registry.register(new BeanDefinition<>(TestService.class, "testService"));

        Optional<BeanDefinition<TestService>> result = registry.getDefinitionByType(TestService.class);
        assertTrue(result.isPresent());
        assertEquals("testService", result.get().name());
    }

    @Test
    void testGetInstanceWithBeanDefinition() {
        String beanName = "definitionBean";
        TestService expected = new TestService("definition");
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, beanName);

        registry.register(definition);

        // Use the overload that takes BeanDefinition directly
        TestService result1 = registry.getInstance(definition, () -> expected);
        TestService result2 = registry.getInstance(definition, () -> expected);

        assertSame(expected, result1);
        assertSame(expected, result2);
        assertSame(result1, result2);
    }

    @Test
    void testGetInstancePrototypeWithDefinition() {
        String beanName = "prototypeDefBean";
        AtomicInteger counter = new AtomicInteger(0);
        BeanDefinition<TestService> definition = new BeanDefinition<>(TestService.class, beanName, com.warmup.core.scope.Scope.PROTOTYPE);

        registry.register(definition);

        // Use the overload that takes BeanDefinition directly
        TestService result1 = registry.getInstance(definition, () -> new TestService("proto-def-" + counter.incrementAndGet()));
        TestService result2 = registry.getInstance(definition, () -> new TestService("proto-def-" + counter.incrementAndGet()));

        assertNotNull(result1);
        assertNotNull(result2);
        assertNotSame(result1, result2);
    }

    static class TestService {
        private final String name;

        TestService(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
