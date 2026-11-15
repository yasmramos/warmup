package io.github.yasmramos.warmup.core.integration.p0;

import io.github.yasmramos.warmup.annotations.DependsOn;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression (bug 7.1.4): the {@code @DependsOn} cycle guard must be
 * per-thread. With a global in-progress set, two threads resolving the same
 * bean concurrently both try to add its name, and the second one is wrongly
 * reported as a circular {@code @DependsOn}.
 */
class DependsOnParallelResolveTest {

    private static final CountDownLatch trackerEntered = new CountDownLatch(1);
    private static final CountDownLatch releaseTracker = new CountDownLatch(1);

    private Warmup warmup;

    @BeforeEach
    void setUp() {
        warmup = Warmup.builder().registerShutdownHook(false).build();
    }

    @AfterEach
    void tearDown() {
        releaseTracker.countDown();
        warmup.shutdown();
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinition<?> definition(Class<?> type, String name) {
        return new BeanDefinition(type, name, Scope.SINGLETON, LifecycleCallbacks.empty(),
                false, new Object[0]);
    }

    @Test
    void dependsOn_sameBeanResolvedConcurrently_noFalseCycle() throws Exception {
        warmup.registerDynamic(definition(SlowTracker.class, "slowTracker"));
        warmup.registerDynamic(definition(DependsOnClient.class, "dependsOnClient"));

        AtomicReference<Object> firstResult = new AtomicReference<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // Thread A: enters ensureDependsOn (adds "dependsOnClient"), then blocks
        // inside the @DependsOn target's constructor.
        Thread first = new Thread(() -> {
            try {
                firstResult.set(warmup.get(DependsOnClient.class));
            } catch (Throwable t) {
                failures.add(t);
            }
        }, "depends-on-first");
        first.start();

        // Wait until thread A is inside the tracker, holding the in-progress entry.
        assertTrue(trackerEntered.await(5, TimeUnit.SECONDS),
                "Thread A should reach the @DependsOn target");

        // Thread B: resolves the very same bean while thread A still holds it.
        AtomicReference<Object> secondResult = new AtomicReference<>();
        Thread second = new Thread(() -> {
            try {
                secondResult.set(warmup.get(DependsOnClient.class));
            } catch (Throwable t) {
                failures.add(t);
            }
        }, "depends-on-second");
        second.start();

        releaseTracker.countDown();
        first.join(5_000);
        second.join(5_000);

        assertTrue(failures.isEmpty(),
                "Concurrent resolution of the same @DependsOn bean must not raise a false cycle: " + failures);
        assertNotNull(firstResult.get());
        assertNotNull(secondResult.get());
    }

    static class SlowTracker {
        SlowTracker() {
            trackerEntered.countDown();
            try {
                releaseTracker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @DependsOn("slowTracker")
    static class DependsOnClient {
    }
}
