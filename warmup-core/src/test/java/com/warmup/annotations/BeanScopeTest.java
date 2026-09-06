package com.warmup.annotations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Bean.Scope enum.
 * 
 * @author yasmramos
 * @since 1.0
 */
class BeanScopeTest {

    @Test
    void testSingletonScope() {
        Bean.Scope singleton = Bean.Scope.SINGLETON;
        
        assertEquals("SINGLETON", singleton.name());
        assertEquals(0, singleton.ordinal());
    }

    @Test
    void testPrototypeScope() {
        Bean.Scope prototype = Bean.Scope.PROTOTYPE;
        
        assertEquals("PROTOTYPE", prototype.name());
        assertEquals(1, prototype.ordinal());
    }

    @Test
    void testCustomScope() {
        Bean.Scope custom = Bean.Scope.CUSTOM;
        
        assertEquals("CUSTOM", custom.name());
        assertEquals(2, custom.ordinal());
    }

    @Test
    void testAllScopesValues() {
        Bean.Scope[] allScopes = Bean.Scope.values();
        
        assertEquals(3, allScopes.length);
        assertEquals(Bean.Scope.SINGLETON, allScopes[0]);
        assertEquals(Bean.Scope.PROTOTYPE, allScopes[1]);
        assertEquals(Bean.Scope.CUSTOM, allScopes[2]);
    }

    @Test
    void testValueOfSingleton() {
        Bean.Scope scope = Bean.Scope.valueOf("SINGLETON");
        assertEquals(Bean.Scope.SINGLETON, scope);
    }

    @Test
    void testValueOfPrototype() {
        Bean.Scope scope = Bean.Scope.valueOf("PROTOTYPE");
        assertEquals(Bean.Scope.PROTOTYPE, scope);
    }

    @Test
    void testValueOfCustom() {
        Bean.Scope scope = Bean.Scope.valueOf("CUSTOM");
        assertEquals(Bean.Scope.CUSTOM, scope);
    }

    @Test
    void testValueOfInvalidThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            Bean.Scope.valueOf("INVALID");
        });
    }

    @Test
    void testValueOfNullThrowsException() {
        // Note: valueOf(null) throws NullPointerException, not IllegalArgumentException
        // This is the standard Java Enum behavior
        assertThrows(NullPointerException.class, () -> {
            Bean.Scope.valueOf(null);
        });
    }
}
