/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.spectra;

import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;

/**
 *
 * @author Bruce Johnson
 */
public class SpectrumMenu extends ChartMenu {

    public SpectrumMenu(PolyChart chart) {
        super(chart);
    }

    void makeChartMenu() {
        chartMenu = new ContextMenu();
        MenuItem attrItem = new MenuItem("Attributes");
        attrItem.setOnAction((ActionEvent e) -> {
            chart.getController().showSpecAttrAction(e);
        });
        Menu viewMenu = new Menu("View");
        MenuItem expandItem = new MenuItem("Expand");
        expandItem.setOnAction((ActionEvent e) -> {
            chart.expand();
        });
        viewMenu.getItems().add(expandItem);

        MenuItem fullItem = new MenuItem("Full");
        fullItem.setOnAction((ActionEvent e) -> {
            chart.full();
        });
        viewMenu.getItems().add(fullItem);

        MenuItem zoomInItem = new MenuItem("Zoom In");
        zoomInItem.setOnAction((ActionEvent e) -> {
            chart.zoom(1.2);
        });
        viewMenu.getItems().add(zoomInItem);
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        zoomOutItem.setOnAction((ActionEvent e) -> {
            chart.zoom(0.8);
        });
        viewMenu.getItems().add(zoomOutItem);

        Menu baselineMenu = new Menu("Baseline");
        MenuItem addBaselineItem = new MenuItem("Add Baseline Region");
        addBaselineItem.setOnAction((ActionEvent e) -> {
            chart.addBaselineRange(false);
        });
        MenuItem clearBaselineItem = new MenuItem("Clear Baseline Region");
        clearBaselineItem.setOnAction((ActionEvent e) -> {
            chart.addBaselineRange(true);
        });
        MenuItem clearAllBaselineItem = new MenuItem("Clear Baseline Regions");
        clearAllBaselineItem.setOnAction((ActionEvent e) -> {
            chart.clearBaselineRanges();
        });
        MenuItem extractItem = new MenuItem("Add Extract Region");
        extractItem.setOnAction((ActionEvent e) -> {
            chart.addRegionRange();
        });

        baselineMenu.getItems().add(addBaselineItem);
        baselineMenu.getItems().add(clearBaselineItem);
        baselineMenu.getItems().add(clearAllBaselineItem);
        Menu peakMenu = new Menu("Peaks");
        Menu peakFitMenu = new Menu("Fit");
        Menu peakClusterMenu = new Menu("Cluster");

        MenuItem inspectPeakItem = new MenuItem("Inspect Peak");
        inspectPeakItem.setOnAction((ActionEvent e) -> {
            chart.showHitPeak(chart.getMouseBindings().getMousePressX(), chart.getMouseBindings().getMousePressY());
        });

        peakMenu.getItems().add(inspectPeakItem);

        MenuItem adjustLabelsItem = new MenuItem("Adjust Labels");
        adjustLabelsItem.setOnAction((ActionEvent e) -> {
            chart.adjustLabels();
        });
        peakMenu.getItems().add(adjustLabelsItem);

        MenuItem tweakPeakItem = new MenuItem("Tweak Selected");
        tweakPeakItem.setOnAction((ActionEvent e) -> {
            chart.tweakPeaks();
        });
        peakMenu.getItems().add(tweakPeakItem);

        MenuItem tweakListItem = new MenuItem("Tweak All Lists");
        tweakListItem.setOnAction((ActionEvent e) -> {
            chart.tweakPeakLists();
        });
        peakMenu.getItems().add(tweakListItem);

        peakMenu.getItems().add(peakFitMenu);
        peakMenu.getItems().add(peakClusterMenu);

//     fitPeakLists(int syncDim, boolean fitAll, boolean lsFit, boolean fitPlanes) {
        MenuItem fitListItem = new MenuItem("Fit");
        fitListItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(-1, true, false, false);
        });
        peakFitMenu.getItems().add(fitListItem);

        MenuItem fitColumnItem = new MenuItem("Fit Clustered Column");
        fitColumnItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(0, true, false, false);
        });
        peakFitMenu.getItems().add(fitColumnItem);

        MenuItem fitRowItem = new MenuItem("Fit Clustered Row");
        fitRowItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(1, true, false, false);
        });
        peakFitMenu.getItems().add(fitRowItem);

        MenuItem fitPlanesItem = new MenuItem("Fit Planes");
        fitPlanesItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(-1, true, false, true);
        });
        peakFitMenu.getItems().add(fitPlanesItem);

        MenuItem fitLSItem = new MenuItem("Lineshape pick/fit");
        fitLSItem.setOnAction((ActionEvent e) -> {
            PeakPicking.peakPickActive(chart.getController(), true);
        });
        peakFitMenu.getItems().add(fitLSItem);

        MenuItem clusterRowItem = new MenuItem("Cluster Row");
        clusterRowItem.setOnAction((ActionEvent e) -> {
            chart.clusterPeakLists(1);
        });
        peakClusterMenu.getItems().add(clusterRowItem);

        MenuItem clusterColumnItem = new MenuItem("Cluster Column");
        clusterColumnItem.setOnAction((ActionEvent e) -> {
            chart.clusterPeakLists(0);
        });
        peakClusterMenu.getItems().add(clusterColumnItem);

        Menu refMenu = new Menu("Reference");

        MenuItem diagRefMenuItem = new MenuItem("Adjust Diagonal");
        diagRefMenuItem.setOnAction((ActionEvent e) -> {
            SpectrumAdjuster.adjustDiagonalReference();
        });
        MenuItem shiftRefMenuItem = new MenuItem("Shift Reference");
        shiftRefMenuItem.setOnAction((ActionEvent e) -> {
            SpectrumAdjuster.adjustDatasetRef();
        });
        MenuItem shiftPeaksMenuItem = new MenuItem("Shift Peaks");
        shiftPeaksMenuItem.setOnAction((ActionEvent e) -> {
            SpectrumAdjuster.shiftPeaks();
        });
        MenuItem setRefMenuItem = new MenuItem("Set Reference...");
        setRefMenuItem.setOnAction((ActionEvent e) -> {
            SpectrumAdjuster.showRefInput();
        });
        MenuItem undoRefMenuItem = new MenuItem("Undo");
        undoRefMenuItem.setOnAction((ActionEvent e) -> {
            SpectrumAdjuster.undo();
        });
        MenuItem writeRefMenuItem = new MenuItem("Write Reference Parameters");
        writeRefMenuItem.setOnAction((ActionEvent e) -> {
            SpectrumAdjuster.writePars();
        });

        refMenu.getItems().addAll(setRefMenuItem, shiftRefMenuItem,
                diagRefMenuItem, shiftPeaksMenuItem,
                undoRefMenuItem, writeRefMenuItem);

        chartMenu.getItems().add(attrItem);
        chartMenu.getItems().add(viewMenu);
        chartMenu.getItems().add(peakMenu);
        chartMenu.getItems().add(refMenu);
        chartMenu.getItems().add(baselineMenu);
        chartMenu.getItems().add(extractItem);
    }
}
