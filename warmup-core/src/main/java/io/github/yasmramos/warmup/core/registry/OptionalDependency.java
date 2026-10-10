package io.github.yasmramos.warmup.core.registry;

/**
 * Encodes an {@code Optional<T>} injection point.
 *
 * <p>An injection point declared as {@code Optional&lt;PaymentProcessor&gt;} has no single bean
 * name: the value is {@code Optional.of(bean)} when a bean of the element type exists and
 * {@code Optional.empty()} when it does not (mirroring Spring's optional injection). The
 * annotation processor therefore records the injection point as a marker string and the
 * container recognises it while resolving dependencies.</p>
 *
 * <p>The marker payload carries two colon-separated fields:</p>
 * <pre>$$warmup:optional:&lt;declaredRawType&gt;:&lt;elementType&gt;</pre>
 * <p>The declared raw type is always {@code java.util.Optional} today, but is kept in the
 * marker for symmetry with {@link CollectionDependency} and so the erased constructor
 * parameter type can be recovered by the JIT compiler without guessing.</p>
 *
 * <p>The {@code $$warmup:} prefix cannot occur in a valid Java identifier, so a marker can
 * never collide with a user supplied bean name.</p>
 *
 * @author yasmramos
 * @since 1.0
 */
public final class OptionalDependency {

    private static final String PREFIX = "$$warmup:optional:";
    private static final String RAW_TYPE = "java.util.Optional";

    private OptionalDependency() {
    }

    /**
     * Builds the marker encoding an {@code Optional<T>} injection point.
     *
     * @param elementFqn fully qualified name of the element type {@code T}
     * @return the marker to store as the dependency name
     */
    public static String marker(String elementFqn) {
        if (elementFqn == null || elementFqn.isBlank()) {
            throw new IllegalArgumentException("elementFqn cannot be null or blank");
        }
        return PREFIX + RAW_TYPE + ":" + elementFqn;
    }

    /**
     * @param name a candidate dependency name
     * @return {@code true} when {@code name} encodes an optional injection point
     */
    public static boolean isMarker(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /**
     * Reads the declared injection type back out of a marker
     * (always {@code java.util.Optional} for markers produced by this class).
     *
     * @param name a marker previously produced by {@link #marker(String)}
     * @return the fully qualified name of the raw declared type
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static String declaredTypeOf(String name) {
        partsOf(name);
        return RAW_TYPE;
    }

    /**
     * Reads the element type back out of a marker.
     *
     * @param name a marker previously produced by {@link #marker(String)}
     * @return the fully qualified element type name
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static String elementOf(String name) {
        return partsOf(name)[1];
    }

    /**
     * Resolves a type name using the thread context class loader, falling back to this
     * module's class loader. Delegates to {@link CollectionDependency#loadType(String)} as
     * the loaders and error semantics are identical.
     *
     * @param fqn fully qualified type name
     * @return the resolved class
     * @throws IllegalStateException when the type cannot be found
     */
    public static Class<?> loadType(String fqn) {
        return CollectionDependency.loadType(fqn);
    }

    private static String[] partsOf(String name) {
        if (!isMarker(name)) {
            throw new IllegalArgumentException("Not an optional marker: " + name);
        }
        String[] parts = name.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("Malformed optional marker: " + name);
        }
        if (!parts[0].equals(RAW_TYPE)) {
            throw new IllegalArgumentException("Unexpected declared type in optional marker: " + name);
        }
        return parts;
    }
}