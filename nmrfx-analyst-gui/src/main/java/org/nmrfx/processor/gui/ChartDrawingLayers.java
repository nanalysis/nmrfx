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
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;

/**
 * Layers on which NMR charts are drawn.
 * These layers can be shared across multiple charts, which will have to draw at the correct coordinated by themselves using the layout information.
 */
public class ChartDrawingLayers {
    enum Item {
        Spectrum,
        Peaks
    }

    // Manages grid layout when displaying multiple charts on the same drawing canvas
    // Also contains chart instances (as javafx Region objects)
    private final GridPaneCanvas grid;

    // Base layer (background): contains the spectra, axes, ...
    private final Canvas base = new Canvas();

    // Second layer: contains peaks and annotations
    private final Canvas peaksAndAnnotations = new Canvas();

    // Third layer: contains slices and drag-boxes
    private final Canvas slicesAndDragBoxes = new Canvas();

    // Top level (foreground): contains crosshairs, highlighted regions, selected canvas handles
    // Equivalent to a glasspane in swing
    private final Pane top = new Pane();


    public ChartDrawingLayers(FXMLController controller, StackPane stack) {
        top.setMouseTransparent(true);

        grid = new GridPaneCanvas(controller, base);
        grid.addCharts(1, controller.getCharts());
        grid.setMouseTransparent(true);
        grid.setManaged(true);

        base.setManaged(false);
        peaksAndAnnotations.setManaged(false);
        slicesAndDragBoxes.setManaged(false);
        top.setManaged(false);
        grid.widthProperty().addListener(observable -> updateCanvasWidth());
        grid.heightProperty().addListener(observable -> updateCanvasHeight());

        stack.getChildren().addAll(base, grid, peaksAndAnnotations, slicesAndDragBoxes, top);
    }

    private void updateCanvasWidth() {
        double width = grid.getWidth();
        base.setWidth(width);
        peaksAndAnnotations.setWidth(width);
        slicesAndDragBoxes.setWidth(width);
    }

    private void updateCanvasHeight() {
        double height = grid.getHeight();
        base.setHeight(height);
        peaksAndAnnotations.setHeight(height);
        slicesAndDragBoxes.setHeight(height);
    }

    public GraphicsContext getGraphicsContextFor(Item item) {
        Canvas canvas =  switch (item) {
            case Spectrum -> base;
            case Peaks -> peaksAndAnnotations;
        };

        return canvas.getGraphicsContext2D();
    }

    public GraphicsContextProxy getGraphicsProxyFor(Item item) {
        return new GraphicsContextProxy(getGraphicsContextFor(item));
    }

    //XXX try to remove accessor usages from FXMLController
    public GridPaneCanvas getGrid() {
        return grid;
    }

    public Canvas getBase() {
        return base;
    }

    public Canvas getPeaksAndAnnotations() {
        return peaksAndAnnotations;
    }

    public Canvas getSlicesAndDragBoxes() {
        return slicesAndDragBoxes;
    }

    public Pane getTop() {
        return top;
    }
}
