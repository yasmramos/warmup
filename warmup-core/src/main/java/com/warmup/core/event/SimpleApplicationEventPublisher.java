package com.warmup.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Simple implementation of {@link ApplicationEventPublisher}.
 * 
 * <p>Maintains a map of event type to list of listeners and dispatches events
 * either synchronously (default) or asynchronously when configured with an executor.</p>
 */
public class SimpleApplicationEventPublisher implements ApplicationEventPublisher {

    /**
     * Map of event type to list of listeners for that event type.
     * Uses ConcurrentHashMap for thread-safe access during registration and publishing.
     */
    private final Map<Class<?>, List<Consumer<Object>>> listenersByType = new ConcurrentHashMap<>();

    /**
     * Optional executor for asynchronous event dispatching.
     * If null, events are dispatched synchronously.
     */
    private final ExecutorService executor;

    /**
     * Creates a new publisher with synchronous event dispatching.
     */
    public SimpleApplicationEventPublisher() {
        this.executor = null;
    }

    /**
     * Creates a new publisher with optional asynchronous event dispatching.
     * 
     * @param executor the executor for async dispatching, or null for synchronous
     */
    public SimpleApplicationEventPublisher(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Registers a listener for a specific event type.
     * 
     * @param eventType the type of event to listen for
     * @param listener the listener to invoke when events of this type are published
     * @throws NullPointerException if eventType or listener is null
     */
    @SuppressWarnings("unchecked")
    public <T> void addListener(Class<T> eventType, Consumer<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        
        listenersByType.computeIfAbsent(eventType, k -> new ArrayList<>())
            .add((Consumer<Object>) listener);
    }

    /**
     * Removes a listener for a specific event type.
     * 
     * @param eventType the type of event
     * @param listener the listener to remove
     * @return true if the listener was registered and removed, false otherwise
     */
    @SuppressWarnings("unchecked")
    public <T> boolean removeListener(Class<T> eventType, Consumer<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        
        List<Consumer<Object>> listeners = listenersByType.get(eventType);
        if (listeners == null) {
            return false;
        }
        return listeners.remove((Consumer<Object>) listener);
    }

    @Override
    public void publishEvent(Object event) {
        Objects.requireNonNull(event, "event must not be null");
        
        Class<?> eventType = event.getClass();
        List<Consumer<Object>> listeners = listenersByType.get(eventType);
        
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        // Create a copy to avoid concurrent modification issues
        List<Consumer<Object>> listenersToInvoke = new ArrayList<>(listeners);

        if (executor != null) {
            // Asynchronous dispatch
            for (Consumer<Object> listener : listenersToInvoke) {
                executor.submit(() -> {
                    try {
                        listener.accept(event);
                    } catch (Exception e) {
                        // Log but don't propagate exceptions from listeners
                        System.err.println("Error in event listener: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        } else {
            // Synchronous dispatch
            for (Consumer<Object> listener : listenersToInvoke) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    // Log but don't propagate exceptions from listeners
                    System.err.println("Error in event listener: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Returns the number of listeners registered for a specific event type.
     * 
     * @param eventType the event type
     * @return the number of listeners for this type
     */
    public int getListenerCount(Class<?> eventType) {
        List<Consumer<Object>> listeners = listenersByType.get(eventType);
        return listeners != null ? listeners.size() : 0;
    }

    /**
     * Returns the total number of registered listeners across all event types.
     * 
     * @return total listener count
     */
    public int getTotalListenerCount() {
        return listenersByType.values().stream()
            .mapToInt(List::size)
            .sum();
    }
}
