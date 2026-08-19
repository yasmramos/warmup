package com.warmup.core.scope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Scope enum.
 */
class ScopeTest {

    @Test
    void testEnumValues() {
        Scope[] values = Scope.values();
        
        assertEquals(3, values.length);
        assertTrue(java.util.List.of(values).contains(Scope.SINGLETON));
        assertTrue(java.util.List.of(values).contains(Scope.PROTOTYPE));
        assertTrue(java.util.List.of(values).contains(Scope.CUSTOM));
    }

    @Test
    void testValueOf() {
        assertEquals(Scope.SINGLETON, Scope.valueOf("SINGLETON"));
        assertEquals(Scope.PROTOTYPE, Scope.valueOf("PROTOTYPE"));
        assertEquals(Scope.CUSTOM, Scope.valueOf("CUSTOM"));
    }

    @Test
    void testOrdinal() {
        assertEquals(0, Scope.SINGLETON.ordinal());
        assertEquals(1, Scope.PROTOTYPE.ordinal());
        assertEquals(2, Scope.CUSTOM.ordinal());
    }
}
