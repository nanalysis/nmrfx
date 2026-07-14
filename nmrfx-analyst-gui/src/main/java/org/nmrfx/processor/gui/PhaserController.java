package org.nmrfx.processor.gui;

import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.nmrfx.processor.gui.spectra.SliceAttributes;

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
        phaserPane.expandedProperty().addListener((observable, wasExpanded, isNowExpanded)
                -> updatePhaser(isNowExpanded));
    }

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
}
