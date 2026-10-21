package io.github.yasmramos.warmup.annotations;

import io.github.yasmramos.warmup.core.scope.Scope;
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
        Scope singleton = Scope.SINGLETON;
        
        assertEquals("SINGLETON", singleton.name());
        assertEquals(0, singleton.ordinal());
    }

    @Test
    void testPrototypeScope() {
        Scope prototype = Scope.PROTOTYPE;
        
        assertEquals("PROTOTYPE", prototype.name());
        assertEquals(1, prototype.ordinal());
    }

    @Test
    void testCustomScope() {
        Scope custom = Scope.CUSTOM;
        
        assertEquals("CUSTOM", custom.name());
        assertEquals(2, custom.ordinal());
    }

    @Test
    void testAllScopesValues() {
        Scope[] allScopes = Scope.values();
        
        assertEquals(3, allScopes.length);
        assertEquals(Scope.SINGLETON, allScopes[0]);
        assertEquals(Scope.PROTOTYPE, allScopes[1]);
        assertEquals(Scope.CUSTOM, allScopes[2]);
    }

    @Test
    void testValueOfSingleton() {
        Scope scope = Scope.valueOf("SINGLETON");
        assertEquals(Scope.SINGLETON, scope);
    }

    @Test
    void testValueOfPrototype() {
        Scope scope = Scope.valueOf("PROTOTYPE");
        assertEquals(Scope.PROTOTYPE, scope);
    }

    @Test
    void testValueOfCustom() {
        Scope scope = Scope.valueOf("CUSTOM");
        assertEquals(Scope.CUSTOM, scope);
    }

    @Test
    void testValueOfInvalidThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            Scope.valueOf("INVALID");
        });
    }

    @Test
    void testValueOfNullThrowsException() {
        // Note: valueOf(null) throws NullPointerException, not IllegalArgumentException
        // This is the standard Java Enum behavior
        assertThrows(NullPointerException.class, () -> {
            Scope.valueOf(null);
        });
    }
}
