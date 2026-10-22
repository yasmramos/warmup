package io.github.yasmramos.warmup.demo;

import io.github.yasmramos.warmup.annotations.Inject;
import io.github.yasmramos.warmup.javafx.FxController;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Demo controller. The annotation processor registers it as a bean (thanks to
 * {@link FxController}) and generates a factory that injects
 * {@link GreetingService}; {@code FxLoader} then lets JavaFX inject the
 * {@link FXML} fields.
 */
@FxController(
        fxml = "/io/github/yasmramos/warmup/demo/main.fxml",
        css = "/io/github/yasmramos/warmup/demo/demo.css")
public class MainController {

    // Package-private: the generated factory writes this field directly, which
    // is why the processor rejects private @Inject fields.
    @Inject
    GreetingService greetingService;

    @FXML
    private TextField nameField;

    @FXML
    private Label greetingLabel;

    @FXML
    private void onGreet() {
        greetingLabel.setText(greetingService.greet(nameField.getText()));
    }

    /**
     * Exposed for the wiring test.
     *
     * @return the injected service
     */
    public GreetingService greetingService() {
        return greetingService;
    }
}
