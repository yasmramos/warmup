package io.github.yasmramos.warmup.javafx;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean whose lifetime is bound to a window (a {@code Stage}).
 *
 * <p>All {@code @WindowScoped} beans resolved within the same window share one
 * instance. {@link FxLoader#closeWindow(javafx.stage.Window)} (called
 * automatically by {@code WarmupApplication} when the primary stage is hidden)
 * destroys them: the bean's {@code @PreDestroy} method is invoked, or
 * {@link AutoCloseable#close()} when no {@code @PreDestroy} method exists.</p>
 *
 * <p>The annotation processor registers the bean as a {@code Scope.CUSTOM}
 * bean with the {@link UiScopes#WINDOW} scope name. Use
 * {@link FxLoader#beginWindowScope(javafx.stage.Window)} or
 * {@link FxLoader#withWindow(javafx.stage.Window, Runnable)} when resolving
 * window-scoped beans outside the initial view load.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WindowScoped {

    /**
     * Optional bean name. Defaults to the simple class name.
     *
     * @return the bean name
     */
    String value() default "";
}
