/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2023 One Moon Scientific, Inc., Westfield, N.J., USA
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

import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;

/**
 * Layers on which NMR charts are drawn.
 * These layers can be shared across multiple charts, which will have to draw at the correct coordinated by themselves using the layout information.
 */
public class ChartDrawingLayers {
    // Manages grid layout when displaying multiple charts on the same drawing canvas
    // Also contains chart instances (as javafx Region objects)
    private final GridPaneCanvas chartGroup;

    // Base layer (background): contains the spectra, axes, ...
    private final Canvas canvas = new Canvas();

    // Second layer: contains peaks and annotations
    private final Canvas peakCanvas = new Canvas();

    // Third layer: contains slices and drag-boxes
    private final Canvas annoCanvas = new Canvas();

    // Top level (foreground): contains crosshairs, highlighted regions, selected canvas handles
    // Equivalent to a glasspane in swing
    private final Pane plotContent = new Pane();


    public ChartDrawingLayers(FXMLController controller, StackPane stack) {
        plotContent.setMouseTransparent(true);

        chartGroup = new GridPaneCanvas(controller, canvas);
        chartGroup.addCharts(1, controller.getCharts());
        chartGroup.setMouseTransparent(true);
        chartGroup.setManaged(true);

        canvas.setManaged(false);
        peakCanvas.setManaged(false);
        annoCanvas.setManaged(false);
        plotContent.setManaged(false);
        chartGroup.widthProperty().addListener(observable -> updateCanvasWidth());
        chartGroup.heightProperty().addListener(observable -> updateCanvasHeight());

        stack.getChildren().addAll(canvas, chartGroup, peakCanvas, annoCanvas, plotContent);
    }

    private void updateCanvasWidth() {
        double width = chartGroup.getWidth();
        canvas.setWidth(width);
        peakCanvas.setWidth(width);
        annoCanvas.setWidth(width);
    }

    private void updateCanvasHeight() {
        double height = chartGroup.getHeight();
        canvas.setHeight(height);
        peakCanvas.setHeight(height);
        annoCanvas.setHeight(height);
    }

    //XXX try to remove accessor usages from FXMLController
    public GridPaneCanvas getChartGroup() {
        return chartGroup;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public Canvas getPeakCanvas() {
        return peakCanvas;
    }

    public Canvas getAnnoCanvas() {
        return annoCanvas;
    }

    public Pane getPlotContent() {
        return plotContent;
    }
}
