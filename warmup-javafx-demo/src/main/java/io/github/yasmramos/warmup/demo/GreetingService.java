package io.github.yasmramos.warmup.demo;

import io.github.yasmramos.warmup.annotations.Singleton;

/**
 * Simple service used by the demo controller. It is discovered by the Warmup
 * annotation processor thanks to {@link Singleton}, so no manual registration
 * is required in {@link DemoApp}.
 */
@Singleton
public class GreetingService {

    public String greet(String name) {
        String target = (name == null || name.isBlank()) ? "Warmup" : name.trim();
        return "Hello, " + target + "!";
    }
}
