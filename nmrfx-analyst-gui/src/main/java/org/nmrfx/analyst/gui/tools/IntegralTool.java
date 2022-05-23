package org.nmrfx.analyst.gui.tools;

import javafx.event.ActionEvent;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.PopOver;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.spectra.IntegralHit;
import org.nmrfx.utils.GUIUtils;

public class IntegralTool {
    VBox vBox;
    IntegralHit hit;

    public IntegralTool() {
    }

    public VBox getBox() {
        return vBox;
    }

    public void initializePopover(PopOver popOver) {
        this.vBox = new VBox();
        HBox hBox = new HBox();
        hBox.setMinHeight(10);
        HBox.setHgrow(hBox, Priority.ALWAYS);
        MenuButton menu = makeChartMenu();

        vBox.getChildren().addAll(hBox, menu);
        popOver.setContentNode(vBox);
    }

    public MenuButton makeChartMenu() {
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

    }}
