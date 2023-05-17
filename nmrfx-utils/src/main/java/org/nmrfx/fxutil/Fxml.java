package org.nmrfx.fxutil;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;

/**
 * Simplification over javafx.fxml.FXMLLoader, avoid code duplication for loading Scenes, setting CSS or other properties.
 */
public class Fxml {
    private static final String FXML_RESOURCES_BASE = "/fxml/";
    private static final String DEFAULT_CSS = "/styles/Styles.css";

    public static Builder load(Class<?> klass, String fileName) {
        return new Builder(klass, fileName);
    }

    public static class Builder {
        private final String fileName;
        private final FXMLLoader loader;
        private final Parent node;
        private Scene scene;
        private Stage stage;

        public Builder(Class<?> klass, String fileName) {
            this.fileName = fileName;
            this.loader = new FXMLLoader(klass.getResource(FXML_RESOURCES_BASE + fileName));
            this.node = load();
        }

        private <T> T load() {
            try {
                return loader.load();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load fxml file: " + this.fileName, e);
            }
        }

        private Scene getOrCreateScene() {
            if (scene == null) {
                this.scene = new Scene(node);
                scene.getStylesheets().add(DEFAULT_CSS);
            }
            return scene;
        }

        public Builder withAdditionalStyleSheet(String stylesheet) {
            getOrCreateScene().getStylesheets().add(stylesheet);
            return this;
        }


        public Builder withNewStage(String title) {
            return withNewStage(title, null);
        }

        public Builder withNewStage(String title, Window owner) {
            Stage stage = new Stage(StageStyle.DECORATED);
            stage.setTitle(title);

            if (owner != null) {
                stage.initOwner(owner);
            }

            return withStage(stage);
        }

        public Builder withStage(Stage stage) {
            this.stage = stage;
            stage.setScene(getOrCreateScene());

            if (getController() instanceof StageBasedController stageBased) {
                stageBased.setStage(stage);
            }

            return this;
        }

        public Builder withParent(Pane parent) {
            parent.getChildren().add(node);
            return this;
        }

        public <T> T getController() {
            return loader.getController();
        }

        @SuppressWarnings("unchecked")
        public <T extends Parent> T getNode() {
            return (T) node;
        }

        public Stage getStage() {
            return stage;
        }
    }
}
