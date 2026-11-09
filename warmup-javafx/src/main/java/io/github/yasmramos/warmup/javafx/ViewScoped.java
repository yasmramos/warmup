package io.github.yasmramos.warmup.javafx;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean whose lifetime is bound to a single loaded FXML view.
 *
 * <p>All {@code @ViewScoped} beans produced while a view is loaded share one
 * instance. When the view is closed (see {@link FxView#close()}) or hot-reloaded,
 * the instance is destroyed: its {@code @PreDestroy} method is invoked, or
 * {@link AutoCloseable#close()} when no {@code @PreDestroy} method exists.</p>
 *
 * <p>The annotation processor registers the bean as a {@code Scope.CUSTOM}
 * bean with the {@link UiScopes#VIEW} scope name. The scope is only active
 * while a view is being created, so view-scoped dependencies should be
 * injected into controllers/view models; resolving them outside a view load
 * throws {@link IllegalStateException}.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ViewScoped {

    /**
     * Optional bean name. Defaults to the simple class name.
     *
     * @return the bean name
     */
    String value() default "";
}
