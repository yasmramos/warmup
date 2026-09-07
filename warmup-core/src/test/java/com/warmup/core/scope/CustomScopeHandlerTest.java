package com.warmup.core.scope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RequestScopeHandler and SessionScopeHandler implementations.
 * Verifies that beans with custom scopes are reused within the same context
 * and created anew in different contexts.
 */
class CustomScopeHandlerTest {

    private RequestScopeHandler requestScopeHandler;
    private SessionScopeHandler sessionScopeHandler;

    @BeforeEach
    void setUp() {
        requestScopeHandler = new RequestScopeHandler();
        sessionScopeHandler = new SessionScopeHandler();
    }

    @AfterEach
    void tearDown() {
        RequestScopeHandler.endRequest();
        SessionScopeHandler.endSession();
    }

    @Test
    void testRequestScope_ReusesInstanceWithinSameRequest() {
        // Given: A request context is started
        RequestScopeHandler.beginRequest();
        AtomicInteger factoryCallCount = new AtomicInteger(0);

        // When: Getting the same bean twice within the same request
        Object instance1 = requestScopeHandler.get("testBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        Object instance2 = requestScopeHandler.get("testBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        // Then: The same instance is returned and factory was called only once
        assertSame(instance1, instance2, "Should return the same instance within the same request");
        assertEquals(1, factoryCallCount.get(), "Factory should be called only once");
    }

    @Test
    void testRequestScope_CreatesNewInstanceInDifferentRequests() {
        // Given: First request context
        RequestScopeHandler.beginRequest();
        AtomicInteger factoryCallCount = new AtomicInteger(0);

        Object instance1 = requestScopeHandler.get("testBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        // End first request
        RequestScopeHandler.endRequest();

        // When: Second request context starts
        RequestScopeHandler.beginRequest();
        Object instance2 = requestScopeHandler.get("testBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        // Then: Different instances are created and factory was called twice
        assertNotSame(instance1, instance2, "Should create different instances in different requests");
        assertEquals(2, factoryCallCount.get(), "Factory should be called twice (once per request)");
    }

    @Test
    void testSessionScope_ReusesInstanceWithinSameSession() {
        // Given: A session context is started
        SessionScopeHandler.beginSession();
        AtomicInteger factoryCallCount = new AtomicInteger(0);

        // When: Getting the same bean twice within the same session
        Object instance1 = sessionScopeHandler.get("sessionBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        Object instance2 = sessionScopeHandler.get("sessionBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        // Then: The same instance is returned and factory was called only once
        assertSame(instance1, instance2, "Should return the same instance within the same session");
        assertEquals(1, factoryCallCount.get(), "Factory should be called only once");
    }

    @Test
    void testSessionScope_CreatesNewInstanceInDifferentSessions() {
        // Given: First session context
        SessionScopeHandler.beginSession();
        AtomicInteger factoryCallCount = new AtomicInteger(0);

        Object instance1 = sessionScopeHandler.get("sessionBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        // End first session
        SessionScopeHandler.endSession();

        // When: Second session context starts
        SessionScopeHandler.beginSession();
        Object instance2 = sessionScopeHandler.get("sessionBean", () -> {
            factoryCallCount.incrementAndGet();
            return new Object();
        });

        // Then: Different instances are created and factory was called twice
        assertNotSame(instance1, instance2, "Should create different instances in different sessions");
        assertEquals(2, factoryCallCount.get(), "Factory should be called twice (once per session)");
    }

    @Test
    void testRequestScope_RemoveRemovesBeanFromScope() {
        // Given: A request context with a cached bean
        RequestScopeHandler.beginRequest();
        Object instance1 = requestScopeHandler.get("removableBean", () -> new Object());

        // When: The bean is removed
        requestScopeHandler.remove("removableBean");

        // And: The bean is requested again
        Object instance2 = requestScopeHandler.get("removableBean", () -> new Object());

        // Then: A new instance is created
        assertNotSame(instance1, instance2, "Should create a new instance after removal");
    }

    @Test
    void testSessionScope_DestroyClearsAllBeans() {
        // Given: A session context with multiple beans
        SessionScopeHandler.beginSession();
        Object bean1 = sessionScopeHandler.get("bean1", () -> new Object());
        Object bean2 = sessionScopeHandler.get("bean2", () -> new Object());

        // When: The scope is destroyed
        sessionScopeHandler.destroy();

        // And: A new session starts
        SessionScopeHandler.beginSession();
        Object bean1New = sessionScopeHandler.get("bean1", () -> new Object());
        Object bean2New = sessionScopeHandler.get("bean2", () -> new Object());

        // Then: All beans are cleared and new instances are created
        assertNotSame(bean1, bean1New, "Should create new instance after destroy");
        assertNotSame(bean2, bean2New, "Should create new instance after destroy");
    }

    @Test
    void testMultipleBeansInSameRequestScope() {
        // Given: A request context with multiple beans
        RequestScopeHandler.beginRequest();

        Object beanA1 = requestScopeHandler.get("beanA", () -> new Object());
        Object beanB1 = requestScopeHandler.get("beanB", () -> new Object());

        // When: Getting the same beans again
        Object beanA2 = requestScopeHandler.get("beanA", () -> new Object());
        Object beanB2 = requestScopeHandler.get("beanB", () -> new Object());

        // Then: Each bean is reused correctly
        assertSame(beanA1, beanA2, "Bean A should be reused");
        assertSame(beanB1, beanB2, "Bean B should be reused");
        assertNotSame(beanA1, beanB1, "Different beans should be different instances");
    }

    @Test
    void testMultipleBeansInSameSessionScope() {
        // Given: A session context with multiple beans
        SessionScopeHandler.beginSession();

        Object beanX1 = sessionScopeHandler.get("beanX", () -> new Object());
        Object beanY1 = sessionScopeHandler.get("beanY", () -> new Object());

        // When: Getting the same beans again
        Object beanX2 = sessionScopeHandler.get("beanX", () -> new Object());
        Object beanY2 = sessionScopeHandler.get("beanY", () -> new Object());

        // Then: Each bean is reused correctly
        assertSame(beanX1, beanX2, "Bean X should be reused");
        assertSame(beanY1, beanY2, "Bean Y should be reused");
        assertNotSame(beanX1, beanY1, "Different beans should be different instances");
    }
}
