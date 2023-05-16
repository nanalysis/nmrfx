package org.nmrfx.fxutil;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Simplification over javafx.fxml.FXMLLoader, avoid code duplication for loading Scenes, setting CSS or other properties.
 */
public class FxmlLoader {
    private static final String FXML_RESOURCES_BASE = "/fxml/";
    private static final String DEFAULT_CSS = "/styles/Styles.css";
    private final String fileName;
    private final FXMLLoader loader;
    private final Stage stage;

    public FxmlLoader(Class<?> klass, String fileName) {
        this(klass, fileName, null);
    }

    public FxmlLoader(Class<?> klass, String fileName, Stage stage) {
        this.fileName = fileName;
        this.loader = new FXMLLoader(klass.getResource(FXML_RESOURCES_BASE + fileName));
        this.stage = stage;
    }

    private <T> T load() {
        try {
            return loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load fxml file: " + this.fileName, e);
        }
    }

    public Scene createScene() {
        Parent parent = load();
        Scene scene = new Scene(parent);
        scene.getStylesheets().add(DEFAULT_CSS);

        if (stage != null) {
            stage.setScene(scene);
        }

        return scene;
    }

    public <T> T getController() {
        T controller = loader.getController();
        if (controller instanceof StageBasedController stageBased) {
            stageBased.setStage(stage);
        }
        return controller;
    }
}
