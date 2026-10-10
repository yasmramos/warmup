package io.github.yasmramos.warmup.core.registry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Encodes an injection point that resolves to <em>all</em> beans of a given type
 * instead of to a single bean.
 *
 * <p>Entries in {@link BeanDefinition#dependencies()} are normally plain bean names, but a
 * collection injection point such as {@code List<PaymentProcessor>} has no single bean name
 * to refer to. The annotation processor therefore encodes those injection points as a marker
 * string built by {@link #marker(Kind, String, String)}. The container recognises the marker
 * while resolving dependencies and delegates to {@code resolveAll} / {@code resolveAllAsMap}.</p>
 *
 * <p>The marker payload carries three colon-separated fields:</p>
 * <pre>$$warmup:collection:&lt;kind&gt;:&lt;declaredRawType&gt;:&lt;elementType&gt;</pre>
 * <p>The declared raw type is kept because the generated factory casts the resolved value to
 * the declared parameter type, so an injected {@code Deque} or {@code TreeSet} has to be
 * materialised as that exact type rather than as a generic {@code ArrayList}.</p>
 *
 * <p>The {@code $$warmup:} prefix cannot occur in a valid Java identifier, so a marker can
 * never collide with a user supplied bean name.</p>
 *
 * @author yasmramos
 * @since 1.0
 */
public final class CollectionDependency {

    /**
     * The kind of collection an injection point asks for.
     */
    public enum Kind {
        /** Injected as {@code List<T>} or a {@code List} implementation. */
        LIST,
        /** Injected as {@code Set<T>} or a {@code Set} implementation. */
        SET,
        /** Injected as {@code Collection<T>}, {@code Queue} or {@code Deque}. */
        COLLECTION,
        /** Injected as {@code Map<String, T>}, keyed by bean name. */
        MAP
    }

    private static final String PREFIX = "$$warmup:collection:";

    private CollectionDependency() {
    }

    /**
     * Builds the marker encoding a collection injection point.
     *
     * @param kind the kind of collection requested
     * @param declaredTypeFqn fully qualified name of the raw (erased) declared injection type
     * @param elementFqn fully qualified name of the element (or, for maps, value) type
     * @return the marker to store as the dependency name
     */
    public static String marker(Kind kind, String declaredTypeFqn, String elementFqn) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        if (declaredTypeFqn == null || declaredTypeFqn.isBlank()) {
            throw new IllegalArgumentException("declaredTypeFqn cannot be null or blank");
        }
        if (elementFqn == null || elementFqn.isBlank()) {
            throw new IllegalArgumentException("elementFqn cannot be null or blank");
        }
        return PREFIX + kind.name().toLowerCase() + ":" + declaredTypeFqn + ":" + elementFqn;
    }

    /**
     * @param name a candidate dependency name
     * @return {@code true} when {@code name} encodes a collection injection point
     */
    public static boolean isMarker(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /**
     * Reads the collection kind back out of a marker.
     *
     * @param name a marker previously produced by {@link #marker(Kind, String, String)}
     * @return the encoded kind
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static Kind kindOf(String name) {
        String[] parts = partsOf(name);
        try {
            return Kind.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed collection marker: " + name, e);
        }
    }

    /**
     * Reads the declared injection type back out of a marker.
     *
     * @param name a marker previously produced by {@link #marker(Kind, String, String)}
     * @return the fully qualified name of the raw declared type
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static String declaredTypeOf(String name) {
        return partsOf(name)[1];
    }

    /**
     * Reads the element type back out of a marker.
     *
     * @param name a marker previously produced by {@link #marker(Kind, String, String)}
     * @return the fully qualified element (or value) type name
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static String elementOf(String name) {
        return partsOf(name)[2];
    }

    /**
     * Creates an empty container able to hold the values for the declared injection type.
     *
     * <p>Concrete types are instantiated reflectively so that {@code LinkedList},
     * {@code TreeSet}, {@code CopyOnWriteArrayList} and user collection types all keep their
     * declared type. Interfaces and abstract types fall back to a compatible default.</p>
     *
     * @param declaredType the raw declared injection type
     * @param kind the collection kind
     * @return an empty, mutable container of a type assignable to {@code declaredType}
     */
    public static Object newContainer(Class<?> declaredType, Kind kind) {
        if (kind == Kind.MAP) {
            if (!declaredType.isInterface() && !Modifier.isAbstract(declaredType.getModifiers())) {
                Object created = newInstanceOf(declaredType);
                if (created instanceof Map) {
                    return created;
                }
            }
            return (declaredType.getName().endsWith("SortedMap")
                    || declaredType.getName().endsWith("NavigableMap"))
                    ? new TreeMap<>()
                    : new LinkedHashMap<>();
        }

        if (!declaredType.isInterface() && !Modifier.isAbstract(declaredType.getModifiers())) {
            Object created = newInstanceOf(declaredType);
            if (created instanceof Collection) {
                return created;
            }
        }

        String name = declaredType.getName();
        if (kind == Kind.SET) {
            return (name.endsWith("SortedSet") || name.endsWith("NavigableSet"))
                    ? new TreeSet<>()
                    : new LinkedHashSet<>();
        }
        if (name.endsWith("Deque") || name.endsWith("Queue")) {
            return new ArrayDeque<>();
        }
        return new ArrayList<>();
    }

    /**
     * Resolves a type name using the thread context class loader, falling back to this
     * module's class loader.
     *
     * @param fqn fully qualified type name
     * @return the resolved class
     * @throws IllegalStateException when the type cannot be found
     */
    public static Class<?> loadType(String fqn) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try {
                return Class.forName(fqn, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
                // fall through to this module's loader
            }
        }
        try {
            return Class.forName(fqn, false, CollectionDependency.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Cannot resolve collection element type '" + fqn + "'. "
                            + "Make sure the type is on the application class path.", e);
        }
    }

    private static Object newInstanceOf(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static String[] partsOf(String name) {
        if (!isMarker(name)) {
            throw new IllegalArgumentException("Not a collection marker: " + name);
        }
        String[] parts = name.substring(PREFIX.length()).split(":", 3);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new IllegalArgumentException("Malformed collection marker: " + name);
        }
        return parts;
    }

}
