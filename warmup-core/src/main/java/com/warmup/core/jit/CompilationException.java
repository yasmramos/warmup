package com.warmup.core.jit;

/**
 * Exception thrown when JIT compilation fails.
 */
public class CompilationException extends Exception {

    public CompilationException(String message) {
        super(message);
    }

    public CompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
