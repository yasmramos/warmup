package io.github.yasmramos.warmup.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forces the named beans to be created before the annotated bean.
 *
 * <p>Unlike an injection dependency, the listed beans are not passed to the annotated bean;
 * they are only guaranteed to be initialised first. This is useful when a bean relies on the
 * side effects of another bean's initialisation (for example a bean that reads state another
 * bean sets up in a {@code @PostConstruct} method) without holding a reference to it.</p>
 *
 * <p>This annotation can be applied to:</p>
 * <ul>
 *   <li>Classes annotated with {@code @Singleton} or {@code @Component}</li>
 *   <li>Methods annotated with {@code @Bean} in a {@code @Factory} class</li>
 * </ul>
 *
 * <pre>{@code
 * @DependsOn("schemaInitializer")
 * @Singleton
 * public class UserRepository { ... }
 * }</pre>
 *
 * @author yasmramos
 * @since 1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DependsOn {

    /**
     * The names of beans that must be created before this bean.
     *
     * @return the dependency bean names
     */
    String[] value();
}
