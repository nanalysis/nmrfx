package org.nmrfx.processor.gui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SideBarContent extends Pane {

    private Stage stage;

    public SideBarContent(Node node) {
        getChildren().add(node);
        stage = new Stage(StageStyle.DECORATED);
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    public void hide() {
        stage.hide();
    }

    public void show() {
        if (this.getScene() == null) {
            Scene scene = new Scene(this, 400, 400);
            stage.setScene(scene);
        }
        stage.show();
        stage.toFront();
    }

    public Stage getStage() {
        return stage;
    }


}
