package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Profile annotation to conditionally register beans based on active profiles.
 * 
 * <p>Use this annotation on stereotype classes ({@code @Singleton}, {@code @Component}, 
 * {@code @Prototype}) or on {@code @Bean} methods to specify that the bean should only 
 * be registered in the container when one of the specified profiles is active.</p>
 * 
 * <p>Profile names can be negated using the {@code !} prefix. For example, 
 * {@code @Profile("!test")} means the bean will be registered unless the "test" profile 
 * is active.</p>
 * 
 * <pre>
 * {@code
 * @Singleton
 * @Profile("dev")
 * public class DevDatabase implements Database {
 *     // Only registered when "dev" profile is active
 * }
 * 
 * @Singleton
 * @Profile("prod")
 * public class ProdDatabase implements Database {
 *     // Only registered when "prod" profile is active
 * }
 * 
 * @Singleton
 * @Profile("!test")
 * public class RealEmailService implements EmailService {
 *     // Registered unless "test" profile is active
 * }
 * }
 * </pre>
 * 
 * <p>Active profiles are configured via the {@code Warmup.Builder.profiles(String...)} method
 * or by setting the {@code warmup.profiles.active} property in the environment.</p>
 * 
 * @see Conditional
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Profile {
    /**
     * The names of the profiles for which this bean should be registered.
     * Supports negation with '!' prefix (e.g., "!test").
     * @return array of profile names
     */
    String[] value();
}
