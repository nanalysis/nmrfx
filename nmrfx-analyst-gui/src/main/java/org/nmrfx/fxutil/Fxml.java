package org.nmrfx.fxutil;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Simplification over javafx.fxml.FXMLLoader, avoid code duplication for loading Scenes, setting CSS or other properties.
 */
public class Fxml {
    private static final String FXML_RESOURCES_BASE = "/fxml/";
    private static final String DEFAULT_CSS = "/styles/Styles.css";

    public static FxmlBuilder load(Class<?> klass, String fileName) {
        return new FxmlBuilder(klass, fileName);
    }

    public static class FxmlBuilder {
        private final String fileName;
        private final FXMLLoader loader;
        private Scene scene;

        public FxmlBuilder(Class<?> klass, String fileName) {
            this.fileName = fileName;
            this.loader = new FXMLLoader(klass.getResource(FXML_RESOURCES_BASE + fileName));
            initialize();
        }

        private void initialize() {
            Parent parent = load();
            this.scene = new Scene(parent);
            this.scene.getStylesheets().add(DEFAULT_CSS);
        }

        private <T> T load() {
            try {
                return loader.load();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load fxml file: " + this.fileName, e);
            }
        }

        public FxmlBuilder withStage(Stage stage) {
            stage.setScene(scene);

            if (getController() instanceof StageBasedController stageBased) {
                stageBased.setStage(stage);
            }

            return this;
        }

        public <T> T getController() {
            return loader.getController();
        }
    }
}
