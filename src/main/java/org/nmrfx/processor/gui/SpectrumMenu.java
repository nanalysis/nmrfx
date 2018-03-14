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
package org.nmrfx.processor.gui;

import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

/**
 *
 * @author Bruce Johnson
 */
public class SpectrumMenu {

    PolyChart chart;
    ContextMenu specMenu;

    public SpectrumMenu(PolyChart chart) {
        this.chart = chart;
        makeSpecMenu();
    }

    private void makeSpecMenu() {
        specMenu = new ContextMenu();
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

        MenuItem inspectPeakItem = new MenuItem("Inspect Peak");
        inspectPeakItem.setOnAction((ActionEvent e) -> {
            chart.showHitPeak(chart.mouseBindings.getMousePressX(), chart.mouseBindings.getMousePressY());
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

        MenuItem fitItem = new MenuItem("Fit Selected");
        fitItem.setOnAction((ActionEvent e) -> {
            chart.fitPeaks();
        });
        peakMenu.getItems().add(fitItem);
        MenuItem fitListItem = new MenuItem("Fit All Lists");
        fitListItem.setOnAction((ActionEvent e) -> {
            chart.fitPeakLists();
        });
        peakMenu.getItems().add(fitListItem);

        Menu refMenu = new Menu("Reference");

        MenuItem diagRefMenuItem = new MenuItem("Adjust Diagonal");
        diagRefMenuItem.setOnAction((ActionEvent e) -> {
            chart.adjustDiagonalReference();
        });
        refMenu.getItems().add(diagRefMenuItem);

        specMenu.getItems().add(attrItem);
        specMenu.getItems().add(viewMenu);
        specMenu.getItems().add(peakMenu);
        specMenu.getItems().add(refMenu);
        specMenu.getItems().add(baselineMenu);
        specMenu.getItems().add(extractItem);
    }

    public void show(Window window, double x, double y) {
        specMenu.show(window, x, y);
    }

}
