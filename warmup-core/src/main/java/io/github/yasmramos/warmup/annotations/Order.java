package io.github.yasmramos.warmup.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Orders beans of the same type when they are injected as a collection
 * ({@code List<T>}, {@code Set<T>}, {@code Collection<T>}) or returned from
 * {@code Warmup.resolveAll(...)}.
 *
 * <p>Lower values have higher precedence and come first, matching Spring's semantics.
 * Beans without {@code @Order} are treated as {@link #LOWEST_PRECEDENCE} and keep their
 * registration order relative to one another.</p>
 *
 * <p>This annotation can be applied to:</p>
 * <ul>
 *   <li>Classes annotated with {@code @Singleton} or {@code @Component}</li>
 *   <li>Methods annotated with {@code @Bean} in a {@code @Factory} class</li>
 * </ul>
 *
 * <p>A bean whose class implements {@link io.github.yasmramos.warmup.core.Ordered} is ordered
 * through that interface, which takes precedence over this annotation.</p>
 *
 * <pre>{@code
 * @Order(1)
 * @Singleton
 * public class FirstProcessor implements PaymentProcessor { ... }
 *
 * @Order(2)
 * @Singleton
 * public class SecondProcessor implements PaymentProcessor { ... }
 * }</pre>
 *
 * @see io.github.yasmramos.warmup.core.Ordered
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Order {

    /** The lowest precedence a bean can have (unordered beans are treated as this value). */
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    /** The highest precedence a bean can have. */
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /**
     * The order value; lower values have higher precedence.
     *
     * @return the order value
     */
    int value();
}
