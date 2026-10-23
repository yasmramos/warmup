package io.github.yasmramos.warmup.javafx;

import io.github.yasmramos.warmup.core.scope.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FxController annotation.
 */
class FxControllerTest {

    @Test
    void testAnnotationExists() {
        // Verify the annotation can be referenced
        Class<?> annotationClass = FxController.class;
        assertEquals("io.github.yasmramos.warmup.javafx.FxController", annotationClass.getName());
    }

    @Test
    void testDefaultFxmlValue() throws NoSuchMethodException {
        var method = FxController.class.getMethod("fxml");
        assertNotNull(method);
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    void testDefaultScopeValue() throws NoSuchMethodException {
        var method = FxController.class.getMethod("scope");
        assertNotNull(method);
        assertEquals(Scope.class, method.getReturnType());
    }

    @Test
    void testAnnotationRetention() throws NoSuchMethodException {
        var retention = FxController.class.getAnnotation(
            java.lang.annotation.Retention.class
        );
        assertNotNull(retention);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void testAnnotationTarget() throws NoSuchMethodException {
        var target = FxController.class.getAnnotation(
            java.lang.annotation.Target.class
        );
        assertNotNull(target);
        assertTrue(java.util.List.of(target.value()).contains(
            java.lang.annotation.ElementType.TYPE
        ));
    }

    @Test
    void testAnnotatedClass() {
        // Create a test class with the annotation
        TestController controller = new TestController();
        
        // Verify it has the annotation
        assertTrue(controller.getClass().isAnnotationPresent(FxController.class));
        
        FxController annotation = 
            controller.getClass().getAnnotation(FxController.class);
        
        assertEquals("", annotation.fxml());
        assertEquals(Scope.PROTOTYPE, annotation.scope());
    }

    @FxController
    private static class TestController {
        // Test controller class
    }
}
