package org.nmrfx.analyst.gui.tools;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.PopOver;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.IconUtilities;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.IntegralHit;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.utils.GUIUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        vBox.setPadding(new Insets(0, 1, 0, 1));
        HBox hBox = new HBox();
        hBox.setMinHeight(10);
        HBox.setHgrow(hBox, Priority.ALWAYS);
        MenuButton menu = makeMenu();
        ToolBar topBar = new ToolBar();
        topBar.getItems().add(menu);
        ToolBar buttonBar = new ToolBar();
        List<Button> buttons = new ArrayList<>();
        Button splitItem = new Button("Split", IconUtilities.getIcon("region_split"));
        splitItem.setOnAction(e -> splitRegion());
        buttons.add(splitItem);
        Button deleteItem = new Button("Delete", IconUtilities.getIcon("editdelete"));
        deleteItem.setOnAction(e -> deleteRegion());
        buttons.add(deleteItem);
        for (Button button1 : buttons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setStyle("-fx-font-size:" + AnalystApp.ICON_FONT_SIZE_STR);
            button1.getStyleClass().add("toolButton");
            buttonBar.getItems().add(button1);
        }

        vBox.getChildren().addAll(hBox, topBar, buttonBar);
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

    void setIntegralNorm(double iNorm) {
        DatasetRegion region = hit.getDatasetRegion();
        double integral = region.getIntegral();
        DatasetBase dataset = hit.getDatasetAttr().getDataset();
        dataset.setNorm(integral * dataset.getScale() / iNorm);
        chart.refresh();

    }

    void setIntegralNormToValue() {
        String normString = GUIUtils.input("Integral Norm Value");
        double norm;
        try {
            norm = Double.parseDouble(normString);
        } catch (NumberFormatException ignored) {
            return;
        }
        setIntegralNorm(norm);
    }

    public void splitRegion() {
        CrossHairs crossHairs = chart.getCrossHairs();

        if (crossHairs.hasState("v0")) {
            double ppm = chart.getCrossHairs().getVerticalPositions()[0];
            try {
                Analyzer.getAnalyzer((Dataset) chart.getDataset()).splitRegion(ppm);
            } catch (IOException e) {
                GUIUtils.warn("Error Splitting Region", e.getMessage());
            }
            chart.refresh();
        }

    }

    public void deleteRegion() {
        Optional<DatasetRegion> activeRegion = chart.getActiveRegion();
        // If the region being deleted is the active region, set active regions to null so
        // green active region bars are not drawn by the PolyChart
        if (activeRegion.isPresent() && activeRegion.get() == this.hit.getDatasetRegion()) {
            chart.setActiveRegion(null);
            hit.getDatasetAttr().setActiveRegion(null);
        }
        deleteRegion(this.hit.getDatasetRegion());
    }

    public void deleteRegion(DatasetRegion region) {
        Analyzer analyzer = Analyzer.getAnalyzer((Dataset) chart.getDataset());
        analyzer.removePeaksFromRegion(region);
        analyzer.getDataset().removeRegion(region);
        chart.refresh();
        AnalystApp.getAnalystApp().hidePopover(false);
    }
}
