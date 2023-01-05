package org.nmrfx.processor.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SideBarContent extends StackPane {

    private Stage stage;

    public SideBarContent(Node node) {
//        this.setStyle("-fx-background-color: green;");
        HBox.setHgrow(this, Priority.ALWAYS);
        VBox.setVgrow(this, Priority.ALWAYS);
        HBox.setHgrow(node, Priority.ALWAYS);
        HBox.setHgrow(node, Priority.ALWAYS);
        ScrollPane sp = new ScrollPane(node);
        getChildren().add(sp);
        StackPane.setMargin(sp, new Insets(4, 4, 4, 4));
        stage = new Stage(StageStyle.DECORATED);
        this.parentProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                Region region = (Region) newValue;
//                this.setMaxHeight(region.getHeight());
                System.out.println("What: " + region.getHeight());
//                this.prefHeightProperty().bind(region.heightProperty());
//                this.prefWidthProperty().bind(region.widthProperty());
                region.heightProperty().addListener((observable1, oldValue1, newValue1) -> {
                    this.setPrefHeight(newValue1.doubleValue());
                    sp.setPrefHeight(newValue1.doubleValue());
                });
                region.widthProperty().addListener((observable1, oldValue1, newValue1) -> {
                    this.setPrefWidth(newValue1.doubleValue());
                    sp.setPrefWidth(newValue1.doubleValue());
                });

            } else {
                this.prefHeightProperty().unbind();
                this.prefWidthProperty().unbind();
            }
//            System.out.println("old: " + oldValue);
//            System.out.println("new: " + newValue);
    });
//        sp.prefHeightProperty().bind(this.prefHeightProperty());
//        sp.prefWidthProperty().bind(this.prefWidthProperty());

//        this.prefHeightProperty().addListener((observable, oldValue, newValue) -> System.out.println(newValue));
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
