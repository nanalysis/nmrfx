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

import javafx.beans.InvalidationListener;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.DragEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.CanvasBindings;
import org.nmrfx.processor.gui.spectra.DragBindings;

/**
 * Layers on which NMR charts are drawn.
 * These layers can be shared across multiple charts, which will have to draw at the correct coordinated by themselves using the layout information.
 */
public class ChartDrawingLayers {
    enum Item {
        Spectrum,
        Peaks,
        Annotations,
        Slices,
        DragBoxes,
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
    private Pane top = new Pane();

    private final InvalidationListener widthListener = observable -> updateCanvasWidth();
    private final InvalidationListener heightListener = observable -> updateCanvasHeight();

    public ChartDrawingLayers(FXMLController controller, StackPane stack) {
        grid = new GridPaneCanvas(controller, base);
        grid.addCharts(1, controller.getCharts());
        grid.setMouseTransparent(true);
        grid.setManaged(true);
        grid.widthProperty().addListener(widthListener);
        grid.heightProperty().addListener(heightListener);

        base.setManaged(false);
        base.setCache(true);

        peaksAndAnnotations.setManaged(false);
        peaksAndAnnotations.setCache(true);
        peaksAndAnnotations.setMouseTransparent(true);

        slicesAndDragBoxes.setManaged(false);
        slicesAndDragBoxes.setMouseTransparent(true);

        top.setManaged(false);
        top.setMouseTransparent(true);

        stack.getChildren().addAll(base, grid, peaksAndAnnotations, slicesAndDragBoxes, top);
        setupEventHandlers(controller);
    }

    private void setupEventHandlers(FXMLController controller) {
        CanvasBindings canvasBindings = new CanvasBindings(controller, base);
        canvasBindings.setHandlers();

        DragBindings dragBindings = new DragBindings(controller, base);
        base.setOnDragOver(dragBindings::mouseDragOver);
        base.setOnDragDropped(dragBindings::mouseDragDropped);
        base.setOnDragExited((DragEvent event) -> base.setStyle("-fx-border-color: #C6C6C6;"));
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

    public double getWidth() {
        return base.getWidth();
    }

    public double getHeight() {
        return base.getHeight();
    }

    public void setCursor(Cursor value) {
        base.setCursor(value);
    }

    public Cursor getCursor() {
        return base.getCursor();
    }

    public void requestFocus() {
        base.requestFocus();
    }

    public GraphicsContext getGraphicsContextFor(Item item) {
        Canvas canvas = switch (item) {
            case Spectrum -> base;
            case Peaks, Annotations -> peaksAndAnnotations;
            case Slices, DragBoxes -> slicesAndDragBoxes;
        };

        return canvas.getGraphicsContext2D();
    }

    public GraphicsContextProxy getGraphicsProxyFor(Item item) {
        return new GraphicsContextProxy(getGraphicsContextFor(item));
    }

    public GridPaneCanvas getGrid() {
        return grid;
    }

    public Canvas getBaseCanvas() {
        return base;
    }

    public Pane getTopPane() {
        return top;
    }
}
