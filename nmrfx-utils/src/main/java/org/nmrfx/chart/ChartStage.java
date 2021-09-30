/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.chart;

import javafx.beans.Observable;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

/**
 *
 * @author brucejohnson
 */
public class ChartStage {

    Stage stage = null;
    XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Pane pane;
    Canvas canvas;
    Scene stageScene = new Scene(borderPane, 500, 500);

    ChoiceBox<String> xArrayChoice = new ChoiceBox<>();
    ChoiceBox<String> yArrayChoice = new ChoiceBox<>();

    public ChartStage() {
    }

    public void show() {
        //Create new Stage for popup window
        if ((stage == null) || !stage.isShowing()) {
            stage = new Stage();
            Label xlabel = new Label("  X Array:  ");
            Label ylabel = new Label("  Y Array:  ");
            //Populate ChoiceBoxes with fitting variable names
            xArrayChoice.getItems().clear();
            yArrayChoice.getItems().clear();
            try {
                xArrayChoice.valueProperty().addListener((Observable x) -> {
                    updateMCplot();
                });
                yArrayChoice.valueProperty().addListener((Observable y) -> {
                    updateMCplot();
                });
            } catch (NullPointerException npEmc1) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error: Fit must first be performed.");
                alert.showAndWait();
                return;
            }
            HBox hBox = new HBox();
            HBox.setHgrow(hBox, Priority.ALWAYS);
            hBox.getChildren().addAll(xlabel, xArrayChoice, ylabel, yArrayChoice);
            //Create the Scatter chart
            pane = new Pane();
            canvas = new Canvas();
            XYCanvasChart chart = XYCanvasChart.buildChart(canvas);
            pane.getChildren().add(canvas);
            activeChart = chart;
            borderPane.setTop(hBox);
            borderPane.setCenter(pane);
            pane.widthProperty().addListener(e -> updateMCplot());
            pane.heightProperty().addListener(e -> updateMCplot());
//            canvas.setWidth(500.0);
//            canvas.setHeight(500.0);
            stage.setScene(stageScene);
        }
        //updateMCPlotChoices();
        stage.show();
        updateMCplot();
    }

    void updateMCplot() {
        canvas.setWidth(pane.getWidth());
        canvas.setHeight(pane.getHeight());
        activeChart.drawChart();
    }

}
