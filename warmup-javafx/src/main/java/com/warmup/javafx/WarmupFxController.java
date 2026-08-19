package com.warmup.javafx;

import com.warmup.core.container.HybridContainer;
import com.warmup.core.scope.Scope;
import javafx.fxml.Initializable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark JavaFX controllers for lazy loading and DI injection.
 * 
 * Usage:
 * <pre>
 * {@code @WarmupFxController}
 * public class MainController implements Initializable {
 *     {@code @Inject} private Service service;
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WarmupFxController {
    /**
     * FXML file path (optional, for auto-loading).
     */
    String fxml() default "";
    
    /**
     * Bean scope (default: PROTOTYPE for controllers).
     */
    Scope scope() default Scope.PROTOTYPE;
}
