package com.warmup.core.event;

import com.warmup.annotations.Bean;
import com.warmup.annotations.EventListener;
import com.warmup.annotations.Inject;
import com.warmup.annotations.Singleton;
import com.warmup.core.container.HybridContainer;
import com.warmup.core.container.HybridContainerConfig;
import com.warmup.core.jit.JITCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the publish/subscribe event mechanism.
 */
class EventPublisherTest {

    /**
     * Simple event class for testing.
     */
    public static class TestEvent {
        private final String message;
        private final int value;

        public TestEvent(String message, int value) {
            this.message = message;
            this.value = value;
        }

        public String getMessage() {
            return message;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Another event type for testing multiple event types.
     */
    public static class AnotherEvent {
        private final String data;

        public AnotherEvent(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }

    /**
     * Bean that listens to TestEvent.
     */
    @Singleton
    public static class TestEventListenerBean {
        private final List<TestEvent> receivedEvents = new ArrayList<>();

        @EventListener
        public void handleTestEvent(TestEvent event) {
            receivedEvents.add(event);
        }

        public List<TestEvent> getReceivedEvents() {
            return receivedEvents;
        }

        public int getEventCount() {
            return receivedEvents.size();
        }
    }

    /**
     * Bean that listens to multiple event types.
     */
    @Singleton
    public static class MultiEventListenerBean {
        private final List<TestEvent> testEvents = new ArrayList<>();
        private final List<AnotherEvent> anotherEvents = new ArrayList<>();

        @EventListener
        public void handleTestEvent(TestEvent event) {
            testEvents.add(event);
        }

        @EventListener
        public void handleAnotherEvent(AnotherEvent event) {
            anotherEvents.add(event);
        }

        public int getTestEventCount() {
            return testEvents.size();
        }

        public int getAnotherEventCount() {
            return anotherEvents.size();
        }
    }

    /**
     * Bean that publishes events.
     */
    @Singleton
    public static class EventPublisherBean {
        private final ApplicationEventPublisher publisher;

        @Inject
        public EventPublisherBean(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        public void publishTestEvent(String message, int value) {
            publisher.publishEvent(new TestEvent(message, value));
        }

        public void publishAnotherEvent(String data) {
            publisher.publishEvent(new AnotherEvent(data));
        }
    }

    /**
     * Bean for testing async event handling.
     */
    @Singleton
    public static class AsyncEventListenerBean {
        private final CountDownLatch latch = new CountDownLatch(1);
        private TestEvent receivedEvent;

        @EventListener
        public void handleTestEvent(TestEvent event) {
            receivedEvent = event;
            latch.countDown();
        }

        public boolean awaitEvent(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        public TestEvent getReceivedEvent() {
            return receivedEvent;
        }
    }

    private HybridContainer container;
    private JITCompiler jitCompiler;

    @BeforeEach
    void setUp() {
        jitCompiler = new JITCompiler();
    }

    @Test
    void testEventListenerReceivesPublishedEvent() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        // Register beans manually
        container.registerDynamic(TestEventListenerBean.class);
        container.registerDynamic(EventPublisherBean.class);

        // Get beans
        TestEventListenerBean listenerBean = container.resolve("testEventListenerBean");
        EventPublisherBean publisherBean = container.resolve("eventPublisherBean");

        assertNotNull(listenerBean);
        assertNotNull(publisherBean);

        // Publish an event
        publisherBean.publishTestEvent("Hello", 42);

        // Verify the listener received the event
        assertEquals(1, listenerBean.getEventCount());
        TestEvent event = listenerBean.getReceivedEvents().get(0);
        assertEquals("Hello", event.getMessage());
        assertEquals(42, event.getValue());
    }

    @Test
    void testMultipleListenersReceiveSameEvent() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        // Register multiple listener beans
        container.registerDynamic(TestEventListenerBean.class);
        container.registerDynamic(MultiEventListenerBean.class);
        container.registerDynamic(EventPublisherBean.class);

        // Get beans
        TestEventListenerBean listener1 = container.resolve("testEventListenerBean");
        MultiEventListenerBean listener2 = container.resolve("multiEventListenerBean");
        EventPublisherBean publisher = container.resolve("eventPublisherBean");

        // Publish an event
        publisher.publishTestEvent("Test", 100);

        // Verify both listeners received the event
        assertEquals(1, listener1.getEventCount());
        assertEquals(1, listener2.getTestEventCount());
    }

    @Test
    void testMultiEventListenerReceivesDifferentEventTypes() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        container.registerDynamic(MultiEventListenerBean.class);
        container.registerDynamic(EventPublisherBean.class);

        MultiEventListenerBean listener = container.resolve("multiEventListenerBean");
        EventPublisherBean publisher = container.resolve("eventPublisherBean");

        // Publish different event types
        publisher.publishTestEvent("Test", 1);
        publisher.publishAnotherEvent("Data");

        // Verify both event types were received
        assertEquals(1, listener.getTestEventCount());
        assertEquals(1, listener.getAnotherEventCount());
    }

    @Test
    void testDirectPublisherUsage() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        container.registerDynamic(TestEventListenerBean.class);

        // Get the publisher directly
        ApplicationEventPublisher publisher = container.resolve("applicationEventPublisher");
        TestEventListenerBean listener = container.resolve("testEventListenerBean");

        assertNotNull(publisher);

        // Publish event directly
        publisher.publishEvent(new TestEvent("Direct", 999));

        // Verify listener received it
        assertEquals(1, listener.getEventCount());
        assertEquals("Direct", listener.getReceivedEvents().get(0).getMessage());
        assertEquals(999, listener.getReceivedEvents().get(0).getValue());
    }

    @Test
    void testAsyncEventDispatching() throws InterruptedException {
        // Create publisher with async executor
        ExecutorService executor = Executors.newFixedThreadPool(2);
        SimpleApplicationEventPublisher asyncPublisher = new SimpleApplicationEventPublisher(executor);

        AsyncEventListenerBean listener = new AsyncEventListenerBean();
        
        // Register listener manually
        asyncPublisher.addListener(TestEvent.class, event -> {
            listener.handleTestEvent(event);
        });

        // Publish event
        asyncPublisher.publishEvent(new TestEvent("Async", 123));

        // Wait for async processing
        assertTrue(listener.awaitEvent(5, TimeUnit.SECONDS), "Event should be received within timeout");
        
        assertNotNull(listener.getReceivedEvent());
        assertEquals("Async", listener.getReceivedEvent().getMessage());
        assertEquals(123, listener.getReceivedEvent().getValue());

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testEventListenerWithNoMatchingListeners() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        // Only register publisher, no listeners
        container.registerDynamic(EventPublisherBean.class);

        EventPublisherBean publisher = container.resolve("eventPublisherBean");
        
        // Should not throw exception when no listeners are registered
        assertDoesNotThrow(() -> {
            publisher.publishTestEvent("No listeners", 0);
        });
    }

    @Test
    void testNullEventThrowsException() {
        SimpleApplicationEventPublisher publisher = new SimpleApplicationEventPublisher();
        
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishEvent(null);
        });
    }

    @Test
    void testEventListenerMethodValidation() {
        // This test verifies that the container properly validates @EventListener methods
        // The validation happens during container initialization
        
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        // Register a bean with valid @EventListener - should work fine
        assertDoesNotThrow(() -> {
            container.registerDynamic(TestEventListenerBean.class);
            container.resolve("testEventListenerBean");
        });
    }

    @Test
    void testMultipleEventsOfSameType() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        container.registerDynamic(TestEventListenerBean.class);
        container.registerDynamic(EventPublisherBean.class);

        TestEventListenerBean listener = container.resolve("testEventListenerBean");
        EventPublisherBean publisher = container.resolve("eventPublisherBean");

        // Publish multiple events
        publisher.publishTestEvent("First", 1);
        publisher.publishTestEvent("Second", 2);
        publisher.publishTestEvent("Third", 3);

        // Verify all events were received
        assertEquals(3, listener.getEventCount());
        assertEquals("First", listener.getReceivedEvents().get(0).getMessage());
        assertEquals("Second", listener.getReceivedEvents().get(1).getMessage());
        assertEquals("Third", listener.getReceivedEvents().get(2).getMessage());
    }

    @Test
    void testEventOrderPreserved() {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        container.registerDynamic(TestEventListenerBean.class);
        container.registerDynamic(EventPublisherBean.class);

        TestEventListenerBean listener = container.resolve("testEventListenerBean");
        EventPublisherBean publisher = container.resolve("eventPublisherBean");

        // Publish events in order
        for (int i = 0; i < 5; i++) {
            publisher.publishTestEvent("Event" + i, i);
        }

        // Verify order is preserved
        assertEquals(5, listener.getEventCount());
        for (int i = 0; i < 5; i++) {
            assertEquals("Event" + i, listener.getReceivedEvents().get(i).getMessage());
            assertEquals(i, listener.getReceivedEvents().get(i).getValue());
        }
    }

    @Test
    void testConcurrentEventPublishing() throws InterruptedException {
        HybridContainerConfig config = new HybridContainerConfig.Builder()
            .withAutoDiscoverFactories(false)
            .build();
        container = new HybridContainer(config, jitCompiler);

        container.registerDynamic(TestEventListenerBean.class);

        TestEventListenerBean listener = container.resolve("testEventListenerBean");
        ApplicationEventPublisher publisher = container.resolve("applicationEventPublisher");

        int threadCount = 10;
        int eventsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Publish events from multiple threads concurrently
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                for (int i = 0; i < eventsPerThread; i++) {
                    publisher.publishEvent(new TestEvent("Thread" + threadId + "-Event" + i, i));
                }
                latch.countDown();
            }).start();
        }

        // Wait for all threads to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // Verify all events were received (thread-safe counter)
        assertEquals(threadCount * eventsPerThread, listener.getEventCount());
    }
}
