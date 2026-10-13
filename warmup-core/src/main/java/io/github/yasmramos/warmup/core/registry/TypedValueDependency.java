package io.github.yasmramos.warmup.core.registry;

/**
 * Encodes a {@code @Value} injection point whose declared type is a collection with a
 * non-{@code String} element type, such as {@code @Value("${app.ports}") List<Integer>}.
 *
 * <p>A plain {@code @Value} placeholder is recorded as the raw expression (e.g.
 * {@code ${app.ports}}) and the container derives the erased target type by reflection. That
 * is enough for {@code List<String>}, but the element type is erased for {@code List<Integer>},
 * so every element would stay a {@code String} and blow up at the first typed access. The
 * annotation processor therefore encodes those injection points as a marker string carrying
 * both the declared raw type and the element type. The container recognises the marker while
 * resolving dependencies and converts every split element to the element type.</p>
 *
 * <p>The marker payload carries three colon-separated fields:</p>
 * <pre>$$warmup:value:&lt;declaredRawType&gt;:&lt;elementType&gt;:&lt;expression&gt;</pre>
 * <p>The expression is the last field, so it may itself contain colons (for example a default
 * value such as {@code ${app.ports:8080,8081}}).</p>
 *
 * <p>The {@code $$warmup:} prefix cannot occur in a valid Java identifier, so a marker can
 * never collide with a user supplied bean name.</p>
 *
 * @author yasmramos
 * @since 1.0
 */
public final class TypedValueDependency {

    private static final String PREFIX = "$$warmup:value:";

    private TypedValueDependency() {
    }

    /**
     * Builds the marker encoding a typed {@code @Value} collection injection point.
     *
     * @param declaredTypeFqn fully qualified name of the raw (erased) declared collection type
     * @param elementFqn fully qualified name of the element type
     * @param expression the {@code @Value} placeholder expression
     * @return the marker to store as the dependency name
     */
    public static String marker(String declaredTypeFqn, String elementFqn, String expression) {
        if (declaredTypeFqn == null || declaredTypeFqn.isBlank()) {
            throw new IllegalArgumentException("declaredTypeFqn cannot be null or blank");
        }
        if (elementFqn == null || elementFqn.isBlank()) {
            throw new IllegalArgumentException("elementFqn cannot be null or blank");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression cannot be null or blank");
        }
        return PREFIX + declaredTypeFqn + ":" + elementFqn + ":" + expression;
    }

    /**
     * @param name a candidate dependency name
     * @return {@code true} when {@code name} encodes a typed {@code @Value} collection
     */
    public static boolean isMarker(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /**
     * Reads the declared raw collection type back out of a marker.
     *
     * @param name a marker previously produced by {@link #marker(String, String, String)}
     * @return the fully qualified name of the raw declared type
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static String declaredTypeOf(String name) {
        return partsOf(name)[0];
    }

    /**
     * Reads the element type back out of a marker.
     *
     * @param name a marker previously produced by {@link #marker(String, String, String)}
     * @return the fully qualified element type name
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static String elementOf(String name) {
        return partsOf(name)[1];
    }

    /**
     * Reads the {@code @Value} placeholder expression back out of a marker.
     *
     * @param name a marker previously produced by {@link #marker(String, String, String)}
     * @return the encoded placeholder expression
     * @throws IllegalArgumentException when {@code name} is not a well formed marker
     */
    public static String expressionOf(String name) {
        return partsOf(name)[2];
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
            throw new IllegalArgumentException("Not a typed value marker: " + name);
        }
        String[] parts = name.substring(PREFIX.length()).split(":", 3);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new IllegalArgumentException("Malformed typed value marker: " + name);
        }
        return parts;
    }
}
