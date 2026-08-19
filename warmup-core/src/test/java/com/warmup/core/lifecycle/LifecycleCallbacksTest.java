package com.warmup.core.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LifecycleCallbacks class.
 */
class LifecycleCallbacksTest {

    @Test
    void testEmpty() {
        LifecycleCallbacks<String> callbacks = LifecycleCallbacks.empty();
        
        assertNull(callbacks.onInit());
        assertNull(callbacks.onDestroy());
    }

    @Test
    void testInitOnly() {
        InitCallback<String> initCallback = bean -> {};
        LifecycleCallbacks<String> callbacks = LifecycleCallbacks.initOnly(initCallback);
        
        assertEquals(initCallback, callbacks.onInit());
        assertNull(callbacks.onDestroy());
    }

    @Test
    void testDestroyOnly() {
        DestroyCallback<String> destroyCallback = bean -> {};
        LifecycleCallbacks<String> callbacks = LifecycleCallbacks.destroyOnly(destroyCallback);
        
        assertNull(callbacks.onInit());
        assertEquals(destroyCallback, callbacks.onDestroy());
    }

    @Test
    void testConstructorWithBothCallbacks() {
        InitCallback<String> initCallback = bean -> {};
        DestroyCallback<String> destroyCallback = bean -> {};
        
        LifecycleCallbacks<String> callbacks = new LifecycleCallbacks<>(
            initCallback, destroyCallback
        );
        
        assertEquals(initCallback, callbacks.onInit());
        assertEquals(destroyCallback, callbacks.onDestroy());
    }

    @Test
    void testConstructorWithNullCallbacks() {
        LifecycleCallbacks<String> callbacks = new LifecycleCallbacks<>(null, null);
        
        assertNull(callbacks.onInit());
        assertNull(callbacks.onDestroy());
    }
}
