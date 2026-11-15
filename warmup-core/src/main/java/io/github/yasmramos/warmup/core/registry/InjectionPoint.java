package io.github.yasmramos.warmup.core.registry;

/**
 * Rich description of a single injection point declared by a bean: what kind of
 * value it expects, the raw dependency token recorded by the annotation
 * processor, and whether it can be satisfied after construction.
 *
 * <p>Before this type existed the container classified every dependency slot
 * ad hoc, re-running the same chain of {@code isMarker(...)} /
 * {@code isValueExpression(...)} checks in the flattening pass, the resolution
 * loop and the JIT dependency-class collector. {@link #classify(Object)} is now
 * the single source of truth for that decision, so a new dependency kind only
 * has to be taught here.</p>
 *
 * @param index position of this injection point in the owning bean's
 * dependency array
 * @param kind the kind of value this injection point expects
 * @param name the raw dependency token (bean name, {@code @Value} expression or
 * marker string)
 * @param deferred whether the dependency may be injected after the bean is
 * constructed ({@code @Lazy} field/setter injection)
 * @param fieldOrSetter whether the dependency comes from field or setter
 * injection rather than a constructor parameter
 */
public record InjectionPoint(int index, Kind kind, String name, boolean deferred, boolean fieldOrSetter) {

    /**
     * The kind of value an injection point expects.
     */
    public enum Kind {
        /** A single bean referenced by name. */
        BEAN,
        /** A {@code @Value} placeholder expression resolved through the property resolver. */
        VALUE,
        /** A typed {@code @Value} collection encoded by {@link TypedValueDependency}. */
        TYPED_VALUE,
        /** Every bean of an element type, encoded by {@link CollectionDependency}. */
        COLLECTION,
        /** An {@code Optional<T>} element bean, encoded by {@link OptionalDependency}. */
        OPTIONAL,
        /** A direct object reference rather than a name (legacy registration paths). */
        DIRECT
    }

    /**
     * Classifies a raw dependency token into its {@link Kind}.
     *
     * @param dependency the dependency token from a bean definition
     * @return the kind this token represents; {@link Kind#DIRECT} for non-string
     * references that are not {@link ValueDependency} instances
     */
    public static Kind classify(Object dependency) {
        if (dependency instanceof ValueDependency) {
            return Kind.VALUE;
        }
        if (dependency instanceof String name) {
            if (CollectionDependency.isMarker(name)) {
                return Kind.COLLECTION;
            }
            if (OptionalDependency.isMarker(name)) {
                return Kind.OPTIONAL;
            }
            if (TypedValueDependency.isMarker(name)) {
                return Kind.TYPED_VALUE;
            }
            if (isValueExpression(name)) {
                return Kind.VALUE;
            }
            return Kind.BEAN;
        }
        return Kind.DIRECT;
    }

    /**
     * @param name a dependency name
     * @return {@code true} when {@code name} is a {@code @Value} placeholder
     * expression such as {@code ${app.name}} or {@code ${app.port:8080}}
     */
    public static boolean isValueExpression(String name) {
        return name != null && name.length() >= 3 && name.startsWith("${") && name.endsWith("}");
    }

    /**
     * @return {@code true} when this injection point names a single bean
     */
    public boolean isBeanReference() {
        return kind == Kind.BEAN;
    }

    /**
     * @return {@code true} when this injection point is resolved from configuration
     * rather than from a bean
     */
    public boolean isConfiguration() {
        return kind == Kind.VALUE || kind == Kind.TYPED_VALUE;
    }
}
