package io.github.yasmramos.warmup.core.jit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AccessorSupport}, the reflection bridge used by
 * generated accessor classes to write private fields and invoke private
 * methods.
 */
class AccessorSupportTest {

    private static final class Target {
        private String text;
        private int number;

        private void accept(String value, int count) {
            this.text = value;
            this.number = count;
        }

        private void explode() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void field_andSet_writePrivateField() {
        Field field = AccessorSupport.field(Target.class, "text");
        Target target = new Target();
        AccessorSupport.set(field, target, "value");
        assertEquals("value", target.text);
    }

    @Test
    void method_andInvoke_callPrivateMethodWithBoxedArguments() {
        Method method = AccessorSupport.method(Target.class, "accept", String.class, int.class);
        Target target = new Target();
        AccessorSupport.invoke(method, target, "hello", 7);
        assertEquals("hello", target.text);
        assertEquals(7, target.number);
    }

    @Test
    void field_unknownName_throws() {
        assertThrows(ExceptionInInitializerError.class, () -> AccessorSupport.field(Target.class, "missing"));
    }

    @Test
    void method_unknownName_throws() {
        assertThrows(ExceptionInInitializerError.class,
                () -> AccessorSupport.method(Target.class, "missing"));
    }

    @Test
    void invoke_propagatesRuntimeExceptionFromTarget() {
        Method method = AccessorSupport.method(Target.class, "explode");
        Target target = new Target();
        assertThrows(IllegalStateException.class, () -> AccessorSupport.invoke(method, target));
    }
}
