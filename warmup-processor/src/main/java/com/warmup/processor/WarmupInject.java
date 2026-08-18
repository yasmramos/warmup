package com.warmup.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor or field for dependency injection.
 * 
 * When applied to a constructor, all parameters are treated as dependencies.
 * When applied to a field, the field is injected after construction.
 * 
 * Only one constructor per class should be annotated with @WarmupInject.
 * If no constructor is annotated, the processor uses:
 * 1. The single public constructor (if only one exists)
 * 2. The no-arg constructor (if available)
 * 
 * Example:
 * ```java
 * @WarmupBean
 * public class UserService {
 *     private final UserRepository repository;
 *     
 *     @WarmupInject
 *     public UserService(UserRepository repository) {
 *         this.repository = repository;
 *     }
 * }
 * ```
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface WarmupInject {
}
