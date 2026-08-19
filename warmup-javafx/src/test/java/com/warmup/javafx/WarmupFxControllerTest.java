package com.warmup.javafx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WarmupFxController annotation.
 */
class WarmupFxControllerTest {

    @Test
    void testAnnotationExists() {
        // Verify the annotation can be referenced
        Class<?> annotationClass = WarmupFxController.class;
        assertEquals("com.warmup.javafx.WarmupFxController", annotationClass.getName());
    }

    @Test
    void testDefaultFxmlValue() throws NoSuchMethodException {
        var method = WarmupFxController.class.getMethod("fxml");
        assertNotNull(method);
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    void testDefaultScopeValue() throws NoSuchMethodException {
        var method = WarmupFxController.class.getMethod("scope");
        assertNotNull(method);
        assertEquals(com.warmup.core.scope.Scope.class, method.getReturnType());
    }

    @Test
    void testAnnotationRetention() throws NoSuchMethodException {
        var retention = WarmupFxController.class.getAnnotation(
            java.lang.annotation.Retention.class
        );
        assertNotNull(retention);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void testAnnotationTarget() throws NoSuchMethodException {
        var target = WarmupFxController.class.getAnnotation(
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
        assertTrue(controller.getClass().isAnnotationPresent(WarmupFxController.class));
        
        WarmupFxController annotation = 
            controller.getClass().getAnnotation(WarmupFxController.class);
        
        assertEquals("", annotation.fxml());
        assertEquals(com.warmup.core.scope.Scope.PROTOTYPE, annotation.scope());
    }

    @WarmupFxController
    private static class TestController {
        // Test controller class
    }
}
