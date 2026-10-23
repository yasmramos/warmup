package io.github.yasmramos.warmup.demo;

import io.github.yasmramos.warmup.core.Warmup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the whole compile-time pipeline of the demo without starting the
 * JavaFX toolkit: the processor must register the {@code @Singleton} service
 * and the {@code @FxController} controller, and the generated factory must
 * inject the service into the controller.
 */
class DemoWiringTest {

    @Test
    void processorRegistersServiceAndController() {
        try (Warmup warmup = Warmup.builder().build()) {
            GreetingService service = warmup.resolve(GreetingService.class);
            assertNotNull(service, "@Singleton service should be discovered by the processor");
            assertEquals("Hello, Warmup!", service.greet("Warmup"));

            MainController controller = warmup.resolve(MainController.class);
            assertNotNull(controller, "@FxController should be registered by the processor");
            assertNotNull(controller.greetingService(), "@Inject fields should be wired");
            assertEquals("Hello, Ada!", controller.greetingService().greet("Ada"));
        }
    }
}
