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
import org.nmrfx.processor.datasets.peaks.PeakListTools.ARRAYED_FIT_MODE;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.tools.PeakLinker;

/**
 *
 * @author Bruce Johnson
 */
public class SpectrumMenu extends ChartMenu {

    public SpectrumMenu(PolyChart chart) {
        super(chart);
    }

    public void makeChartMenu() {
        chartMenu = new ContextMenu();
        Menu viewMenu = new Menu("View");
        MenuItem expandItem = new MenuItem("Expand");
        expandItem.setOnAction((ActionEvent e) -> chart.expand());
        viewMenu.getItems().add(expandItem);

        MenuItem fullItem = new MenuItem("Full");
        fullItem.setOnAction((ActionEvent e) -> chart.full());
        viewMenu.getItems().add(fullItem);

        MenuItem zoomInItem = new MenuItem("Zoom In");
        zoomInItem.setOnAction((ActionEvent e) -> chart.zoom(1.2));
        viewMenu.getItems().add(zoomInItem);
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        zoomOutItem.setOnAction((ActionEvent e) -> chart.zoom(0.8));
        viewMenu.getItems().add(zoomOutItem);
        MenuItem popOutItem = new MenuItem("Pop View Out");
        popOutItem.setOnAction((ActionEvent e) -> chart.popView());
        viewMenu.getItems().add(popOutItem);

        Menu baselineMenu = new Menu("Baseline/Extract Regions");
        MenuItem addBaselineItem = new MenuItem("Add Baseline Region");
        addBaselineItem.setOnAction((ActionEvent e) -> chart.addBaselineRange(false));
        MenuItem clearBaselineItem = new MenuItem("Clear Baseline Region");
        clearBaselineItem.setOnAction((ActionEvent e) -> chart.addBaselineRange(true));
        MenuItem clearAllBaselineItem = new MenuItem("Clear Baseline Regions");
        clearAllBaselineItem.setOnAction((ActionEvent e) -> chart.clearBaselineRanges());
        MenuItem extractItem = new MenuItem("Add Extract Region");
        extractItem.setOnAction((ActionEvent e) -> chart.addRegionRange());

        baselineMenu.getItems().add(addBaselineItem);
        baselineMenu.getItems().add(clearBaselineItem);
        baselineMenu.getItems().add(clearAllBaselineItem);
        baselineMenu.getItems().add(extractItem);
        Menu peakMenu = new Menu("Peaks");
        Menu peakFitMenu = new Menu("Fit");

        MenuItem inspectPeakItem = new MenuItem("Inspect Peak");
        inspectPeakItem.setOnAction((ActionEvent e) -> chart.showHitPeak(
                chart.getMouseBindings().getMousePressX(),
                chart.getMouseBindings().getMousePressY()));

        peakMenu.getItems().add(inspectPeakItem);

        MenuItem adjustLabelsItem = new MenuItem("Adjust Labels");
        adjustLabelsItem.setOnAction((ActionEvent e) -> chart.adjustLabels());
        peakMenu.getItems().add(adjustLabelsItem);

        MenuItem tweakPeakItem = new MenuItem("Tweak Selected");
        tweakPeakItem.setOnAction((ActionEvent e) -> chart.tweakPeaks());
        peakMenu.getItems().add(tweakPeakItem);

        MenuItem tweakListItem = new MenuItem("Tweak All Lists");
        tweakListItem.setOnAction((ActionEvent e) -> chart.tweakPeakLists());
        peakMenu.getItems().add(tweakListItem);

        MenuItem duplicatePeakMenuItem = new MenuItem("Add Duplicate Peak List");
        duplicatePeakMenuItem.setOnAction((ActionEvent e) -> {
            chart.duplicatePeakList();
        });
        peakMenu.getItems().add(duplicatePeakMenuItem);

        peakMenu.getItems().add(peakFitMenu);

//     fitPeakLists(int syncDim, boolean fitAll, boolean lsFit, boolean fitPlanes) {
        MenuItem fitListItem = new MenuItem("Fit");
        fitListItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(-1);
        });
        peakFitMenu.getItems().add(fitListItem);

        MenuItem fitColumnItem = new MenuItem("Fit Clustered Column");
        fitColumnItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(0);
        });
        peakFitMenu.getItems().add(fitColumnItem);

        MenuItem fitRowItem = new MenuItem("Fit Clustered Row");
        fitRowItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(1);
        });
        peakFitMenu.getItems().add(fitRowItem);

        MenuItem fitPlanesItem = new MenuItem("Fit Planes");
        fitPlanesItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(-1, true, false, ARRAYED_FIT_MODE.PLANES);
        });
        peakFitMenu.getItems().add(fitPlanesItem);

        MenuItem fitExpDecayItem = new MenuItem("Fit Planes (Exp)");
        fitExpDecayItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists(-1, true, false, ARRAYED_FIT_MODE.EXP);
        });
        peakFitMenu.getItems().add(fitExpDecayItem);

        MenuItem fitLSItem = new MenuItem("Lineshape pick/fit");
        fitLSItem.setOnAction((ActionEvent e) -> {
            PeakPicking.peakPickActive(chart.getController(), true, null);
        });
        peakFitMenu.getItems().add(fitLSItem);

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
        Menu extractMenu = new Menu("Extract Slice/Projection"
                + "");

        MenuItem projectMenuItem = new MenuItem("Project");
        projectMenuItem.setOnAction((ActionEvent e) -> {
            chart.projectDataset();
        });
        MenuItem removeProjectionsItem = new MenuItem("Remove Projections");
        removeProjectionsItem.setOnAction((ActionEvent e) -> chart.removeProjections());
        MenuItem extractXMenuItem = new MenuItem("Extract-X");
        extractXMenuItem.setOnAction((ActionEvent e) -> {
            chart.extractSlice(0);
        });
        MenuItem extractYMenuItem = new MenuItem("Extract-Y");
        extractYMenuItem.setOnAction((ActionEvent e) -> {
            chart.extractSlice(1);
        });
        MenuItem extractZMenuItem = new MenuItem("Extract-Z");
        extractZMenuItem.setOnAction((ActionEvent e) -> {
            chart.extractSlice(2);
        });
        extractMenu.getItems().addAll(projectMenuItem, removeProjectionsItem, extractXMenuItem, extractYMenuItem, extractZMenuItem);

        Menu linkMenu = new Menu("Peak Linking");
        MenuItem linkColumnMenuItem = new MenuItem("Link Selected Column");
        linkColumnMenuItem.setOnAction((ActionEvent e) -> {
            PeakLinker.linkSelectedPeaks(0);
        });
        MenuItem linkRowMenuItem = new MenuItem("Link Selected Row");
        linkRowMenuItem.setOnAction((ActionEvent e) -> {
            PeakLinker.linkSelectedPeaks(1);
        });

        MenuItem unlinkSelectedMenuItem = new MenuItem("Unlink Selected");
        unlinkSelectedMenuItem.setOnAction((ActionEvent e) -> {
            PeakLinker.unlinkSelected();
        });
        MenuItem unlinkSelectedColumnMenuItem = new MenuItem("Unlink Selected Column");
        unlinkSelectedColumnMenuItem.setOnAction((ActionEvent e) -> {
            PeakLinker.unlinkSelected(0);
        });
        MenuItem unlinkSelectedRowMenuItem = new MenuItem("Unlink Selected Row");
        unlinkSelectedRowMenuItem.setOnAction((ActionEvent e) -> {
            PeakLinker.unlinkSelected(1);
        });

        linkMenu.getItems().addAll(linkColumnMenuItem, linkRowMenuItem,
                unlinkSelectedMenuItem, unlinkSelectedColumnMenuItem, unlinkSelectedRowMenuItem);

        chartMenu.getItems().add(viewMenu);
        chartMenu.getItems().add(peakMenu);
        chartMenu.getItems().add(refMenu);
        chartMenu.getItems().add(baselineMenu);
        chartMenu.getItems().add(linkMenu);
        chartMenu.getItems().add(extractMenu);
    }
}
