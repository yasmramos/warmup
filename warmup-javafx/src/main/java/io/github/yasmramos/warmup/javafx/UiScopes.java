package io.github.yasmramos.warmup.javafx;

/**
 * Names and helpers for the JavaFX UI scopes provided by this module.
 *
 * <p>Beans declared with {@link ViewScoped} or {@link WindowScoped} are
 * registered by the annotation processor with {@code Scope.CUSTOM} and the
 * names exposed here. The {@link FxLoader} registers the matching
 * {@code ScopeHandler} instances automatically.</p>
 */
public final class UiScopes {

    /** Beans live as long as a single loaded view. */
    public static final String VIEW = "view";

    /** Beans live as long as a window (stage). */
    public static final String WINDOW = "window";

    private UiScopes() {
    }
}
