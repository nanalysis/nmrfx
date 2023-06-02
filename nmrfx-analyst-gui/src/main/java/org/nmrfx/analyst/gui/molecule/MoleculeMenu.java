/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.molecule;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
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
    }

}
