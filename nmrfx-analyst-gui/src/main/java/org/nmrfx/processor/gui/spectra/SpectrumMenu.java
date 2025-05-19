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
import org.nmrfx.processor.datasets.peaks.PeakFitParameters;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.tools.PeakLinker;
import org.nmrfx.processor.gui.undo.PeaksUndo;
import org.nmrfx.utils.GUIUtils;

/**
 * @author Bruce Johnson
 */
public class SpectrumMenu extends ChartMenu {

    public SpectrumMenu(PolyChart chart) {
        super(chart);
    }

    PeaksUndo undo = null;

    void addSelectedPeaksUndo() {
        undo = new PeaksUndo(PeakLinker.getSelectedPeaks());
    }

    void addSelectedPeaksUndoRedo(String name) {
        if (undo != null) {
            PeaksUndo redo = new PeaksUndo(PeakLinker.getSelectedPeaks());
            chart.getFXMLController().getUndoManager().add(name, undo, redo);
            undo = null;
        }
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
        duplicatePeakMenuItem.setOnAction((ActionEvent e) -> chart.duplicatePeakList());
        peakMenu.getItems().add(duplicatePeakMenuItem);

        peakMenu.getItems().add(peakFitMenu);

        MenuItem fitListItem = new MenuItem("Fit");
        fitListItem.setOnAction((ActionEvent e) -> chart.fitPeakLists(-1));
        peakFitMenu.getItems().add(fitListItem);

        MenuItem fitColumnItem = new MenuItem("Fit Clustered Column");
        fitColumnItem.setOnAction((ActionEvent e) -> chart.fitPeakLists(0));
        peakFitMenu.getItems().add(fitColumnItem);

        MenuItem fitRowItem = new MenuItem("Fit Clustered Row");
        fitRowItem.setOnAction((ActionEvent e) -> chart.fitPeakLists(1));
        peakFitMenu.getItems().add(fitRowItem);

        MenuItem fitPlanesItem = new MenuItem("Fit Planes");
        fitPlanesItem.setOnAction((ActionEvent e) -> {
            PeakFitParameters fitPars = new PeakFitParameters();
            fitPars.arrayedFitMode(PeakFitParameters.ARRAYED_FIT_MODE.PLANES);
            chart.fitPeakLists(fitPars, true);
        });
        peakFitMenu.getItems().add(fitPlanesItem);

        MenuItem fitExpDecayItem = new MenuItem("Fit Planes (Exp)");
        fitExpDecayItem.setOnAction((ActionEvent e) -> {
            PeakFitParameters fitPars = new PeakFitParameters();
            fitPars.arrayedFitMode(PeakFitParameters.ARRAYED_FIT_MODE.EXP);
            chart.fitPeakLists(fitPars, true);
        });
        peakFitMenu.getItems().add(fitExpDecayItem);
        MenuItem fitZZDecayItem = new MenuItem("Fit Planes (ZZ)");
        fitZZDecayItem.setOnAction((ActionEvent e) -> {
            PeakFitParameters fitPars = new PeakFitParameters();
            fitPars.arrayedFitMode(PeakFitParameters.ARRAYED_FIT_MODE.ZZ_SHAPE);
            chart.fitPeakLists(fitPars, true);
        });
        peakFitMenu.getItems().add(fitZZDecayItem);

        MenuItem fitZZIntensityItem = new MenuItem("Fit Planes (ZZ Intensity)");
        fitZZIntensityItem.setOnAction((ActionEvent e) -> {
            PeakFitParameters fitPars = new PeakFitParameters();
            fitPars.arrayedFitMode(PeakFitParameters.ARRAYED_FIT_MODE.ZZ_INTENSITY);
            chart.fitPeakLists(fitPars, true);
        });
        peakFitMenu.getItems().add(fitZZIntensityItem);

        MenuItem fitLSItem = new MenuItem("Lineshape pick/fit");
        PeakPickParameters peakPickParameters = new PeakPickParameters();
        peakPickParameters.refineLS = true;

        fitLSItem.setOnAction((ActionEvent e) -> PeakPicking.peakPickActive(chart.getFXMLController(), peakPickParameters));
        peakFitMenu.getItems().add(fitLSItem);

        Menu refMenu = new Menu("Reference");

        MenuItem diagRefMenuItem = new MenuItem("Adjust Diagonal");
        diagRefMenuItem.setOnAction((ActionEvent e) -> SpectrumAdjuster.adjustDiagonalReference());
        MenuItem shiftRefMenuItem = new MenuItem("Shift Reference");
        shiftRefMenuItem.setOnAction((ActionEvent e) -> SpectrumAdjuster.adjustDatasetRef());
        MenuItem shiftPeaksMenuItem = new MenuItem("Shift Peaks");
        shiftPeaksMenuItem.setOnAction((ActionEvent e) -> SpectrumAdjuster.shiftPeaks());
        MenuItem setRefMenuItem = new MenuItem("Set Reference...");
        setRefMenuItem.setOnAction((ActionEvent e) -> SpectrumAdjuster.showRefInput());
        MenuItem undoRefMenuItem = new MenuItem("Undo");
        undoRefMenuItem.setOnAction((ActionEvent e) -> SpectrumAdjuster.undo());
        MenuItem writeRefMenuItem = new MenuItem("Write Reference Parameters");
        writeRefMenuItem.setOnAction((ActionEvent e) -> SpectrumAdjuster.writePars());

        refMenu.getItems().addAll(setRefMenuItem, shiftRefMenuItem,
                diagRefMenuItem, shiftPeaksMenuItem,
                undoRefMenuItem, writeRefMenuItem);
        Menu projectMenu = new Menu("Projections");

        MenuItem projectMenuItem = new MenuItem("Project Full");
        projectMenuItem.setOnAction((ActionEvent e) -> chart.projectDataset(false));
        MenuItem projectViewMenuItem = new MenuItem("Project View");
        projectViewMenuItem.setOnAction((ActionEvent e) -> chart.projectDataset(true));
        MenuItem removeProjectionsItem = new MenuItem("Remove Projections");
        removeProjectionsItem.visibleProperty().bind(chart.getDisDimProperty().isEqualTo(PolyChart.DISDIM.TwoD));
        removeProjectionsItem.setOnAction((ActionEvent e) -> chart.removeProjections());
        projectMenu.getItems().addAll(projectMenuItem, projectViewMenuItem, removeProjectionsItem);

        Menu extractMenu = new Menu("Extract");
        Menu extractAddMenu = new Menu("Extract-Add");

        String[] dimNames = {"X", "Y", "Z"};
        for (int i = 0;i<3;i++) {
            final int iOrient = i;
            MenuItem extractMenuItem = new MenuItem( dimNames[iOrient]);
            extractMenuItem.setOnAction((ActionEvent e) -> chart.extractSlice(iOrient, false));
            MenuItem extractAddMenuItem = new MenuItem(dimNames[iOrient]);
            extractAddMenuItem.setOnAction((ActionEvent e) -> chart.extractSlice(iOrient, true));
            extractMenu.getItems().add(extractMenuItem);
            extractAddMenu.getItems().add(extractAddMenuItem);
        }

        Menu linkMenu = new Menu("Peak Linking");
        MenuItem linkColumnMenuItem = new MenuItem("Link Selected Column");
        linkColumnMenuItem.setOnAction((ActionEvent e) -> {
            addSelectedPeaksUndo();
            PeakLinker.linkSelectedPeaks(0);
            addSelectedPeaksUndoRedo("Link Column");
        });
        MenuItem linkRowMenuItem = new MenuItem("Link Selected Row");
        linkRowMenuItem.setOnAction((ActionEvent e) -> {
            addSelectedPeaksUndo();
            PeakLinker.linkSelectedPeaks(1);
            addSelectedPeaksUndoRedo("Link Row");
        });

        MenuItem unlinkSelectedMenuItem = new MenuItem("Unlink Selected");
        unlinkSelectedMenuItem.setOnAction((ActionEvent e) -> {
            addSelectedPeaksUndo();
            PeakLinker.unlinkSelected();
            addSelectedPeaksUndoRedo("Unlink selected");
        });
        MenuItem unlinkSelectedColumnMenuItem = new MenuItem("Unlink Selected Column");
        unlinkSelectedColumnMenuItem.setOnAction((ActionEvent e) -> {
            addSelectedPeaksUndo();
            PeakLinker.unlinkSelected(0);
            addSelectedPeaksUndoRedo("Unlink Column");
        });
        MenuItem unlinkSelectedRowMenuItem = new MenuItem("Unlink Selected Row");
        unlinkSelectedRowMenuItem.setOnAction((ActionEvent e) -> {
            addSelectedPeaksUndo();
            PeakLinker.unlinkSelected(1);
            addSelectedPeaksUndoRedo("Unlink Row");
        });
        MenuItem linkFourPeaksItem = new MenuItem("Link Four Peaks(ZZ)");
        linkFourPeaksItem.setOnAction((ActionEvent e) -> {
            addSelectedPeaksUndo();
            try {
                PeakLinker.linkFourPeaks();
                chart.getPeakListAttributes().get(0).setDrawLinks(true);
                chart.refresh();
            } catch (IllegalStateException iSE) {
                GUIUtils.warn("Peak Linking", "Need exactly four selected peaks");
                return;
            }
            addSelectedPeaksUndoRedo("Unlink Row");
        });

        linkMenu.getItems().addAll(linkColumnMenuItem, linkRowMenuItem,
                unlinkSelectedMenuItem, unlinkSelectedColumnMenuItem, unlinkSelectedRowMenuItem,
                linkFourPeaksItem);

        chartMenu.getItems().add(viewMenu);
        chartMenu.getItems().add(peakMenu);
        chartMenu.getItems().add(refMenu);
        chartMenu.getItems().add(linkMenu);
        chartMenu.getItems().add(projectMenu);
        chartMenu.getItems().add(extractMenu);
        chartMenu.getItems().add(extractAddMenu);
    }
}
