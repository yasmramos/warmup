package com.warmup.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks API elements that are intended for internal use only and are not part of the stable public API.
 * 
 * <p>Methods or classes annotated with {@code @InternalApi} may change or be removed without notice
 * in future releases. They should not be used by application code outside of the warmup project itself.</p>
 * 
 * <p>This annotation is typically used for:</p>
 * <ul>
 *   <li>Escape hatches needed for testing or advanced integration scenarios</li>
 *   <li>Experimental features that may change</li>
 *   <li>Low-level implementation details exposed for specific use cases</li>
 * </ul>
 * 
 * <p><strong>Warning:</strong> Using internal API may result in breaking changes when upgrading versions.</p>
 * 
 * @apiNote This annotation itself is part of the public API, but elements marked with it are not.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface InternalApi {
}
