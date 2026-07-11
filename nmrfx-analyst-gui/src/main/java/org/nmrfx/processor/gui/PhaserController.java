package org.nmrfx.processor.gui;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.gui.spectra.SliceAttributes;
import org.nmrfx.utils.GUIUtils;

import static org.nmrfx.processor.datasets.peaks.PeakPickParameters.PickMode.*;

public class PhaserController {
    FXMLController fxmlController;
    PolyChart chart;
    Phaser phaser;


    public void setup(FXMLController fxmlController, TitledPane phaserPane) {
        this.fxmlController = fxmlController;
        this.chart = fxmlController.getActiveChart();
        VBox vBox = new VBox();
        vBox.setSpacing(10);
        phaserPane.setContent(vBox);
        phaser = new Phaser(fxmlController, vBox, Orientation.HORIZONTAL);
        phaserPane.expandedProperty().addListener((observable, wasExpanded, isNowExpanded) -> {
            if (isNowExpanded) {
                updatePhaser(true);
                // Add your open/expand logic here
            } else {
                updatePhaser(false);
                // Add your close/collapse logic here
            }
        });    }

    public void updatePhaser(boolean showPhaser) {

        PolyChart chart = fxmlController.getActiveChart();
        if (showPhaser) {
            Cursor cursor = fxmlController.getCurrentCursor();
            if (cursor == null) {
                cursor = Cursor.MOVE;
            }
            phaser.cursor(cursor);
            phaser.getPhaseOp();
                phaser.setPH1Slider(chart.getDataPH1());
                phaser.setPH0Slider(chart.getDataPH0());


            if (!chart.is1D()) {
                fxmlController.setCursor(CanvasCursor.SLICE.getCursor());
                SliceAttributes sliceAttributes = chart.getSliceAttributes();
                sliceAttributes.setSlice1State(true);
                sliceAttributes.setSlice2State(true);
                sliceAttributes.setSlice2Color(Color.RED);
                sliceAttributes.setUseDatasetColor(false);
                chart.getCrossHairs().refresh();
            }
        } else {
            fxmlController.setCursor(phaser.cursor());
            fxmlController.setCursor();
            SliceAttributes sliceAttributes = chart.getSliceAttributes();
            sliceAttributes.setSlice1State(true);
            sliceAttributes.setSlice2State(false);
            sliceAttributes.setSlice2Color(Color.RED);
            sliceAttributes.setUseDatasetColor(true);
            chart.getCrossHairs().refresh();
        }
    }
//        phaserButton.disableProperty().bind(controller.processControllerVisibleProperty());

}
