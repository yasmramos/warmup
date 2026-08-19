package com.warmup.core.registry;

import com.warmup.core.lifecycle.LifecycleCallbacks;
import com.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BeanDefinition class.
 */
class BeanDefinitionTest {

    @Test
    void testConstructorWithDefaults() {
        Class<String> type = String.class;
        String name = "testBean";
        
        BeanDefinition<String> definition = new BeanDefinition<>(type, name);
        
        assertEquals(type, definition.type());
        assertEquals(name, definition.name());
        assertEquals(Scope.SINGLETON, definition.scope());
        assertFalse(definition.isPrimary());
        assertEquals(0, definition.dependencies().length);
        assertTrue(LifecycleCallbacks.empty().equals(definition.lifecycle()));
    }

    @Test
    void testConstructorWithScope() {
        Class<String> type = String.class;
        String name = "prototypeBean";
        
        BeanDefinition<String> definition = new BeanDefinition<>(type, name, Scope.PROTOTYPE);
        
        assertEquals(type, definition.type());
        assertEquals(name, definition.name());
        assertEquals(Scope.PROTOTYPE, definition.scope());
        assertFalse(definition.isPrimary());
        assertEquals(0, definition.dependencies().length);
    }

    @Test
    void testFullConstructor() {
        Class<String> type = String.class;
        String name = "fullBean";
        Scope scope = Scope.CUSTOM;
        LifecycleCallbacks<String> lifecycle = LifecycleCallbacks.initOnly(bean -> {});
        boolean isPrimary = true;
        Object[] dependencies = new Object[]{"dep1", "dep2"};
        
        BeanDefinition<String> definition = new BeanDefinition<>(
            type, name, scope, lifecycle, isPrimary, dependencies
        );
        
        assertEquals(type, definition.type());
        assertEquals(name, definition.name());
        assertEquals(scope, definition.scope());
        assertEquals(lifecycle, definition.lifecycle());
        assertTrue(definition.isPrimary());
        assertEquals(2, definition.dependencies().length);
    }

    @Test
    void testHasLifecycleWithEmptyCallbacks() {
        BeanDefinition<String> definition = new BeanDefinition<>(String.class, "test");
        
        assertFalse(definition.hasLifecycle());
    }

    @Test
    void testHasLifecycleWithInitCallback() {
        LifecycleCallbacks<String> lifecycle = LifecycleCallbacks.initOnly(bean -> {});
        BeanDefinition<String> definition = new BeanDefinition<>(
            String.class, "test", Scope.SINGLETON, lifecycle, false, new Object[0]
        );
        
        assertTrue(definition.hasLifecycle());
    }

    @Test
    void testHasLifecycleWithDestroyCallback() {
        LifecycleCallbacks<String> lifecycle = LifecycleCallbacks.destroyOnly(bean -> {});
        BeanDefinition<String> definition = new BeanDefinition<>(
            String.class, "test", Scope.SINGLETON, lifecycle, false, new Object[0]
        );
        
        assertTrue(definition.hasLifecycle());
    }

    @Test
    void testHasLifecycleWithBothCallbacks() {
        LifecycleCallbacks<String> lifecycle = new LifecycleCallbacks<>(
            bean -> {}, 
            bean -> {}
        );
        BeanDefinition<String> definition = new BeanDefinition<>(
            String.class, "test", Scope.SINGLETON, lifecycle, false, new Object[0]
        );
        
        assertTrue(definition.hasLifecycle());
    }
}
