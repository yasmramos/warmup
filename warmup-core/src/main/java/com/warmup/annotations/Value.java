package com.warmup.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject configuration values into fields or method parameters.
 * 
 * <p>Supports property placeholder syntax: {@code ${key}} and {@code ${key:defaultValue}}.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code @Inject}
 * public void setDatabaseUrl({@code @Value}("${db.url:jdbc:h2:mem:test}") String url) {
 *     this.databaseUrl = url;
 * }
 * 
 * {@code @Value}("${app.timeout:30}")
 * private int timeout;
 * </pre>
 * 
 * <p>The value will be resolved from configured {@link com.warmup.core.config.PropertySource}s
 * in the order they were registered, with support for default values and type conversion.</p>
 * 
 * @author yasmramos
 * @since 1.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Value {
    /**
     * The property placeholder expression.
     * 
     * <p>Supports two formats:</p>
     * <ul>
     *   <li>{@code ${key}} - resolves the property, throws exception if not found</li>
     *   <li>{@code ${key:defaultValue}} - resolves the property, uses default if not found</li>
     * </ul>
     * 
     * @return the property placeholder expression
     */
    String value();
}
