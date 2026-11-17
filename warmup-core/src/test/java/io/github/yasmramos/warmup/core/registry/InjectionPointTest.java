package io.github.yasmramos.warmup.core.registry;

import io.github.yasmramos.warmup.core.lifecycle.LifecycleCallbacks;
import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link InjectionPoint} model and its integration with
 * {@link BeanDefinition#injectionPoints()}.
 *
 * <p>{@code InjectionPoint.classify} is the single source of truth for turning a
 * raw dependency token into its kind, so the tests cover every branch and then
 * verify that {@code BeanDefinition} derives the deferred / field-or-setter
 * flags from the raw metadata.</p>
 */
class InjectionPointTest {

    @Test
    void classify_beanName_isBean() {
        assertEquals(InjectionPoint.Kind.BEAN, InjectionPoint.classify("someBean"));
    }

    @Test
    void classify_valueExpression_isValue() {
        assertEquals(InjectionPoint.Kind.VALUE, InjectionPoint.classify("${app.name}"));
    }

    @Test
    void classify_collectionMarker_isCollection() {
        String marker = CollectionDependency.marker(
                CollectionDependency.Kind.LIST, "java.util.List", "java.lang.String");
        assertEquals(InjectionPoint.Kind.COLLECTION, InjectionPoint.classify(marker));
    }

    @Test
    void classify_optionalMarker_isOptional() {
        String marker = OptionalDependency.marker("java.lang.String");
        assertEquals(InjectionPoint.Kind.OPTIONAL, InjectionPoint.classify(marker));
    }

    @Test
    void classify_typedValueMarker_isTypedValue() {
        String marker = TypedValueDependency.marker("java.util.List", "java.lang.Integer", "${app.ports}");
        assertEquals(InjectionPoint.Kind.TYPED_VALUE, InjectionPoint.classify(marker));
    }

    @Test
    void classify_valueDependencyObject_isValue() {
        assertEquals(InjectionPoint.Kind.VALUE,
                InjectionPoint.classify(new ValueDependency("${x}", String.class)));
    }

    @Test
    void classify_directObject_isDirect() {
        assertEquals(InjectionPoint.Kind.DIRECT, InjectionPoint.classify(new Object()));
    }

    @Test
    void injectionPoints_carryDeferredAndFieldFlagsFromMetadata() {
        BeanDefinition<Object> definition = new BeanDefinition<>(
                Object.class, "bean", Scope.SINGLETON, LifecycleCallbacks.empty(), false,
                new Object[]{"ctorDep", "lazyFieldDep", "${app.value}"},
                new String[0], new String[0], "",
                new boolean[]{false, true, true},
                new boolean[]{false, true, true});

        InjectionPoint[] points = definition.injectionPoints();

        assertEquals(3, points.length);
        assertEquals(InjectionPoint.Kind.BEAN, points[0].kind());
        assertFalse(points[0].deferred());
        assertFalse(points[0].fieldOrSetter());
        assertTrue(points[0].isBeanReference());

        assertEquals(InjectionPoint.Kind.BEAN, points[1].kind());
        assertTrue(points[1].deferred());
        assertTrue(points[1].fieldOrSetter());

        assertEquals(InjectionPoint.Kind.VALUE, points[2].kind());
        assertEquals("${app.value}", points[2].name());
        assertTrue(points[2].isConfiguration());
    }

    @Test
    void injectionPoints_areCachedBetweenCalls() {
        BeanDefinition<Object> definition = new BeanDefinition<>(
                Object.class, "bean", Scope.SINGLETON, LifecycleCallbacks.empty(), false,
                new Object[]{"dep"});

        assertSame(definition.injectionPoints(), definition.injectionPoints(),
                "Injection points must be computed once and cached");
    }
}
