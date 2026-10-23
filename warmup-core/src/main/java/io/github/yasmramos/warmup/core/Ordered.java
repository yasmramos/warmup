package io.github.yasmramos.warmup.core;

/**
 * Implemented by beans that need to express their order relative to other beans of the same
 * type, without a hard dependency on the {@code @Order} annotation.
 *
 * <p>Used when beans of the same type are injected as a collection ({@code List<T>},
 * {@code Set<T>}) or returned from {@code Warmup.resolveAll(...)}. Lower values have higher
 * precedence and come first, matching Spring's {@code Ordered} contract.</p>
 *
 * <p>This interface takes precedence over {@link io.github.yasmramos.warmup.annotations.Order}.</p>
 *
 * @author yasmramos
 * @since 1.0
 */
public interface Ordered {

    /** The lowest precedence a bean can have. */
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    /** The highest precedence a bean can have. */
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /**
     * @return the order value; lower values have higher precedence
     */
    int getOrder();
}
