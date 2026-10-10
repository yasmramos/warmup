package io.github.yasmramos.warmup.core.container;

import io.github.yasmramos.warmup.core.registry.CollectionDependency;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link CollectionDependency} marker contract shared between the
 * annotation processor (encoding) and the container (decoding + materialisation).
 *
 * <p>The marker is a plain string so the annotation processor stays a thin JAR with no
 * runtime dependency on warmup-core; these tests pin the exact format so a change on either
 * side of the contract cannot drift silently.</p>
 */
class CollectionDependencyTest {

    @Test
    void markerRoundTrips() {
        String marker = CollectionDependency.marker(
                CollectionDependency.Kind.LIST, "java.util.List", "com.example.PaymentProcessor");

        assertTrue(CollectionDependency.isMarker(marker));
        assertEquals(CollectionDependency.Kind.LIST, CollectionDependency.kindOf(marker));
        assertEquals("java.util.List", CollectionDependency.declaredTypeOf(marker));
        assertEquals("com.example.PaymentProcessor", CollectionDependency.elementOf(marker));
    }

    @Test
    void mapMarkerEncodesValueType() {
        String marker = CollectionDependency.marker(
                CollectionDependency.Kind.MAP, "java.util.Map", "com.example.PaymentProcessor");

        assertEquals(CollectionDependency.Kind.MAP, CollectionDependency.kindOf(marker));
        assertEquals("com.example.PaymentProcessor", CollectionDependency.elementOf(marker));
    }

    @Test
    void markerNeverCollidesWithBeanNames() {
        // Bean names are simple names or explicit @Bean/@Component("...") values. The
        // "$$" prefix cannot appear in a Java identifier, and the marker requires all
        // three payload fields.
        assertFalse(CollectionDependency.isMarker("PaymentProcessor"));
        assertFalse(CollectionDependency.isMarker("$$warmup"));
        assertFalse(CollectionDependency.isMarker("$$warmup:collection"));
        assertFalse(CollectionDependency.isMarker(""));
        assertFalse(CollectionDependency.isMarker(null));
    }

    @Test
    void malformedMarkersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CollectionDependency.kindOf("$$warmup:collection:oops"));
        assertThrows(IllegalArgumentException.class,
                () -> CollectionDependency.elementOf("$$warmup:collection:list:java.util.List"));
        assertThrows(IllegalArgumentException.class,
                () -> CollectionDependency.kindOf("not-a-marker"));
    }

    @Test
    void newContainerMaterialisesDeclaredType() {
        assertEquals(ArrayList.class,
                CollectionDependency.newContainer(List.class, CollectionDependency.Kind.LIST).getClass());
        // Concrete declared types are instantiated so the generated factory's CHECKCAST
        // to the exact declared type never fails.
        assertEquals(LinkedList.class,
                CollectionDependency.newContainer(LinkedList.class, CollectionDependency.Kind.LIST).getClass());
        assertEquals(ArrayDeque.class,
                CollectionDependency.newContainer(Deque.class, CollectionDependency.Kind.COLLECTION).getClass());
        assertEquals(LinkedHashSet.class,
                CollectionDependency.newContainer(Set.class, CollectionDependency.Kind.SET).getClass());
        assertEquals(TreeSet.class,
                CollectionDependency.newContainer(TreeSet.class, CollectionDependency.Kind.SET).getClass());
        assertEquals(LinkedHashMap.class,
                CollectionDependency.newContainer(Map.class, CollectionDependency.Kind.MAP).getClass());
        // A concrete declared map type is instantiated as itself (assignable to the CHECKCAST).
        assertEquals(HashMap.class,
                CollectionDependency.newContainer(HashMap.class, CollectionDependency.Kind.MAP).getClass());
        assertEquals(TreeMap.class,
                CollectionDependency.newContainer(TreeMap.class, CollectionDependency.Kind.MAP).getClass());
    }

    @Test
    void loadTypeResolvesAndRejectsUnknownTypes() {
        assertEquals(String.class, CollectionDependency.loadType("java.lang.String"));
        assertThrows(IllegalStateException.class,
                () -> CollectionDependency.loadType("com.example.DoesNotExist"));
    }
}