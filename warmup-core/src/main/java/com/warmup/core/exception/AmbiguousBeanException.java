package com.warmup.core.exception;

/**
 * Exception thrown when multiple beans of the same type are registered without a @Primary designation
 * and no explicit qualifier/name is specified during resolution.
 */
public class AmbiguousBeanException extends RuntimeException {
    
    /**
     * Creates a new ambiguous bean exception with details about the conflicting beans.
     * 
     * @param type the bean type that has multiple candidates
     * @param beanNames the names of all beans of that type
     */
    public AmbiguousBeanException(Class<?> type, java.util.Collection<String> beanNames) {
        super("Ambiguous bean resolution for type " + type.getName() + 
              ". Multiple candidates found: " + String.join(", ", beanNames) + 
              ". Use @Primary on one bean or specify the bean name with @Named.");
    }
}
