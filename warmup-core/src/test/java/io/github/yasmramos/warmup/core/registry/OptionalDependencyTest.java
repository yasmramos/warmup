package io.github.yasmramos.warmup.core.registry;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OptionalDependency}: the string contract shared with the annotation
 * processor that encodes an {@code Optional<T>} injection point.
 */
class OptionalDependencyTest {

    @Test
    void markerRoundTrips() {
        String marker = OptionalDependency.marker("test.Greeter");

        assertEquals("$$warmup:optional:java.util.Optional:test.Greeter", marker);
        assertTrue(OptionalDependency.isMarker(marker));
        assertEquals("java.util.Optional", OptionalDependency.declaredTypeOf(marker));
        assertEquals("test.Greeter", OptionalDependency.elementOf(marker));
    }

    @Test
    void plainAndCollectionNamesAreNotMarkers() {
        assertFalse(OptionalDependency.isMarker("greeter"));
        assertFalse(OptionalDependency.isMarker(null));
        assertFalse(OptionalDependency.isMarker(
                "$$warmup:collection:list:java.util.List:test.Greeter"));
    }

    @Test
    void blankElementIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OptionalDependency.marker(null));
        assertThrows(IllegalArgumentException.class, () -> OptionalDependency.marker("  "));
    }

    @Test
    void malformedMarkersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> OptionalDependency.declaredTypeOf("greeter"));
        assertThrows(IllegalArgumentException.class, () -> OptionalDependency.elementOf("greeter"));
        assertThrows(IllegalArgumentException.class,
                () -> OptionalDependency.elementOf("$$warmup:optional:java.util.Optional:"));
        assertThrows(IllegalArgumentException.class,
                () -> OptionalDependency.elementOf("$$warmup:optional:java.util.List:test.Greeter"));
    }

    @Test
    void declaredTypeLoadsAsJavaUtilOptional() {
        String marker = OptionalDependency.marker("test.Greeter");
        assertEquals(Optional.class,
                OptionalDependency.loadType(OptionalDependency.declaredTypeOf(marker)));
    }
}
