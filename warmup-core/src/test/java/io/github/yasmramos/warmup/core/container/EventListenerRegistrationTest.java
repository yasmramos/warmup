package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.annotations.EventListener;
import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.core.event.ApplicationEventPublisher;
import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.registry.BeanDefinition;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test that {@code @EventListener} methods are also registered when the bean is
 * added <em>after</em> container startup.
 *
 * <p>Before the fix, {@code registerEventListeners()} ran exactly once during container
 * construction; beans registered later via {@code register()}, {@code registerDynamic()} or
 * the supplier shortcut never had their listeners attached, so their events were silently
 * dropped.</p>
 */
class EventListenerRegistrationTest {

    static final class OrderPlaced {
        final String id;

        OrderPlaced(String id) {
            this.id = id;
        }
    }

    static class OrderNotifications {
        static final AtomicInteger COUNT = new AtomicInteger();
        static volatile String lastEventId;

        @EventListener
        public void onOrderPlaced(OrderPlaced event) {
            COUNT.incrementAndGet();
            lastEventId = event.id;
        }
    }

    static class AuditLog {
        static final AtomicInteger COUNT = new AtomicInteger();

        @EventListener
        public void onOrderPlaced(OrderPlaced event) {
            COUNT.incrementAndGet();
        }
    }

    private static BeanDefinition<OrderNotifications> definition() {
        return new BeanDefinition<>(OrderNotifications.class, "orderNotifications", Scope.SINGLETON,
                LifecycleCallbacks.empty(), false, new Object[0]);
    }

    private static BeanDefinition<AuditLog> auditLogDefinition() {
        return new BeanDefinition<>(AuditLog.class, "auditLog", Scope.SINGLETON,
                LifecycleCallbacks.empty(), false, new Object[0]);
    }

    @BeforeEach
    void resetCounters() {
        OrderNotifications.COUNT.set(0);
        OrderNotifications.lastEventId = null;
        AuditLog.COUNT.set(0);
    }

    @Test
    void eventListenerOnDynamicallyRegisteredBeanIsAttached() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition());

            warmup.get(ApplicationEventPublisher.class).publishEvent(new OrderPlaced("A1"));

            assertEquals(1, OrderNotifications.COUNT.get(),
                    "@EventListener method must run for a bean registered via registerDynamic()");
            assertEquals("A1", OrderNotifications.lastEventId);
        }
    }

    @Test
    void eventListenerOnFactoryRegisteredBeanIsAttached() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.register(definition(), deps -> new OrderNotifications());

            warmup.get(ApplicationEventPublisher.class).publishEvent(new OrderPlaced("B2"));

            assertEquals(1, OrderNotifications.COUNT.get(),
                    "@EventListener method must run for a bean registered via register()");
            assertEquals("B2", OrderNotifications.lastEventId);
        }
    }

    @Test
    void everyDynamicallyRegisteredBeanAttachesItsOwnListener() {
        try (Warmup warmup = Warmup.builder().build()) {
            warmup.registerDynamic(definition());
            warmup.registerDynamic(auditLogDefinition());

            warmup.get(ApplicationEventPublisher.class).publishEvent(new OrderPlaced("C3"));

            assertEquals(1, OrderNotifications.COUNT.get(),
                    "Each registered bean's listener must fire exactly once for the event");
            assertEquals("C3", OrderNotifications.lastEventId);
            assertEquals(1, AuditLog.COUNT.get(),
                    "The second bean's @EventListener method must be attached too");
        }
    }
}