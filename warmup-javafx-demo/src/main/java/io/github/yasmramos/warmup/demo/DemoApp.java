package io.github.yasmramos.warmup.demo;

import io.github.yasmramos.warmup.core.Warmup;
import io.github.yasmramos.warmup.javafx.FxView;
import io.github.yasmramos.warmup.javafx.WarmupApplication;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Minimal JavaFX application that showcases the Warmup integration:
 *
 * <ul>
 *   <li>{@link GreetingService} is discovered by the annotation processor
 *       ({@code @Singleton}) and injected into {@link MainController}.</li>
 *   <li>{@link MainController} is registered by the processor
 *       ({@code @FxController}) and loaded with a typed
 *       {@link FxView}.</li>
 *   <li>The stylesheet declared in the annotation is applied to the root, and
 *       the stage is tracked for hot-reload (run with
 *       {@code -Dwarmup.dev.mode=true}).</li>
 * </ul>
 *
 * <p>Run it with:</p>
 * <pre>
 * mvn -pl warmup-javafx-demo javafx:run
 * </pre>
 */
public final class DemoApp extends WarmupApplication {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    protected void registerBeans(Warmup warmup) {
        // Nothing to do: @Singleton and @FxController beans are registered by the
        // generated FactoryRegistrar that Warmup discovers via ServiceLoader.
    }

    @Override
    protected void onStart(Stage stage) throws Exception {
        FxView<MainController> view = getFxLoader().loadView(MainController.class);

        Scene scene = new Scene(view.root(), 640, 420);
        getFxLoader().registerScene(scene);
        registerSceneForHotReload(stage, view);

        stage.setTitle("Warmup + JavaFX");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    protected void onStop() throws Exception {
        if (getFxLoader() != null) {
            getFxLoader().stopFileWatching();
        }
    }
}
