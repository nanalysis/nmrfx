/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.molecule;

import javafx.scene.control.*;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.ChartMenu;

/**
 * @author brucejohnson
 */
public class MoleculeMenu extends ChartMenu {

    CanvasMolecule canvasMolecule;

    public MoleculeMenu(PolyChart chart, CanvasMolecule canvasMolecule) {
        super(chart);
        this.canvasMolecule = canvasMolecule;
    }

    @Override
    public void makeChartMenu() {
        chartMenu = new ContextMenu();
        MenuItem zoomInItem = new MenuItem("Zoom Out");
        chartMenu.getItems().add(zoomInItem);
        zoomInItem.setOnAction(e -> canvasMolecule.zoom(1.25));
        MenuItem zoomOutItem = new MenuItem("Zoom In");
        chartMenu.getItems().add(zoomOutItem);
        zoomOutItem.setOnAction(e -> canvasMolecule.zoom(0.75));
        Menu labelMenu = new Menu("Labels");

        ToggleGroup toggleGroup = new ToggleGroup();
        AtomLabels.LabelTypes[] labelTypes = {AtomLabels.LabelTypes.LABEL_NONE, AtomLabels.LabelTypes.LABEL_NONHCO,
        AtomLabels.LabelTypes.LABEL_PPM, AtomLabels.LabelTypes.LABEL_NAME};
        for (var labelType : labelTypes) {
            RadioMenuItem noneLabelMenuItem = new RadioMenuItem(labelType.toString().substring(6));
            noneLabelMenuItem.setUserData(labelType);
            toggleGroup.getToggles().add(noneLabelMenuItem);
            labelMenu.getItems().add(noneLabelMenuItem);
            if (labelType == AtomLabels.LabelTypes.LABEL_NONHCO) {
                toggleGroup.selectToggle(noneLabelMenuItem);
            }
        }



        toggleGroup.selectedToggleProperty().addListener(e -> {
            canvasMolecule.setLabels((AtomLabels.LabelTypes) toggleGroup.getSelectedToggle().getUserData());
            canvasMolecule.redraw();
        });

        chartMenu.getItems().add(labelMenu);
    }

}
