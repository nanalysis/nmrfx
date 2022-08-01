package org.nmrfx.analyst.gui.tools;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.PopOver;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.CrossHairs;
import org.nmrfx.processor.gui.spectra.IntegralHit;
import org.nmrfx.utils.GUIUtils;

import java.io.IOException;

public class IntegralTool {
    PolyChart chart;
    VBox vBox;
    IntegralHit hit;

    public IntegralTool(PolyChart chart) {
        this.chart = chart;
    }

    public VBox getBox() {
        return vBox;
    }

    public boolean popoverInitialized() {
        return vBox != null;
    }

    public static IntegralTool getTool(PolyChart chart) {
        IntegralTool integralTool = (IntegralTool) chart.getPopoverTool(IntegralTool.class.getName());
        if (integralTool == null) {
            integralTool = new IntegralTool(chart);
            chart.setPopoverTool(IntegralTool.class.getName(), integralTool);
        }
        return integralTool;
    }

    public void initializePopover(PopOver popOver) {
        this.vBox = new VBox();
        HBox hBox = new HBox();
        hBox.setMinHeight(10);
        HBox.setHgrow(hBox, Priority.ALWAYS);
        MenuButton menu = makeMenu();

        Button splitItem = new Button("Split");
        splitItem.setOnAction(e -> splitRegion());

        vBox.getChildren().addAll(hBox, menu, splitItem);
        popOver.setContentNode(vBox);
    }

    public MenuButton makeMenu() {
        MenuButton integralMenu = new MenuButton("Normalize");
        int[] norms = {1, 2, 3, 4, 5, 6, 9, 100, 0};
        for (var norm : norms) {
            final int iNorm = norm;
            MenuItem normItem;
            if (norm == 0) {
                normItem = new MenuItem("Value...");
                normItem.setOnAction((ActionEvent e) -> setIntegralNormToValue());

            } else {
                normItem = new MenuItem(String.valueOf(iNorm));
                normItem.setOnAction((ActionEvent e) -> setIntegralNorm(iNorm));
            }

            integralMenu.getItems().add(normItem);
        }

        return integralMenu;
    }

    public void setHit(IntegralHit hit) {
        this.hit = hit;

    }

    void setIntegralNorm(int iNorm) {
        DatasetRegion region = hit.getDatasetRegion();
        double integral = region.getIntegral();
        DatasetBase dataset = hit.getDatasetAttr().getDataset();
        dataset.setNorm(integral * dataset.getScale() / iNorm);
        FXMLController.getActiveController().getActiveChart().refresh();

    }
    void setIntegralNormToValue() {
        DatasetRegion region = hit.getDatasetRegion();
        double integral = region.getIntegral();
        DatasetBase dataset = hit.getDatasetAttr().getDataset();
        String normString = GUIUtils.input("Integral Norm Value");
        try {
            double norm = Double.parseDouble(normString);
            dataset.setNorm(integral * dataset.getScale() / norm);
            FXMLController.getActiveController().getActiveChart().refresh();
        } catch (NumberFormatException ignored) {

        }
    }

    public void splitRegion() {
        CrossHairs crossHairs = chart.getCrossHairs();

        if (crossHairs.hasCrosshairState("v0")) {
            double ppm = FXMLController.getActiveController().getActiveChart().getVerticalCrosshairPositions()[0];
            try {
                Analyzer.getAnalyzer((Dataset) chart.getDataset()).splitRegion(ppm);
            } catch (IOException e) {
                GUIUtils.warn("Error Splitting Region", e.getMessage());
            }
            chart.refresh();
        }

    }
}
