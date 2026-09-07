package com.warmup.core.event;

/**
 * Interface for publishing application events to registered listeners.
 * 
 * <p>This publisher supports synchronous and asynchronous event dispatching.
 * Listeners are registered by event type and invoked when matching events are published.</p>
 */
public interface ApplicationEventPublisher {

    /**
     * Publishes an event to all registered listeners that accept this event type.
     * 
     * @param event the event object to publish (must not be null)
     * @throws IllegalArgumentException if event is null
     */
    void publishEvent(Object event);
}
