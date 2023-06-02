package org.nmrfx.processor.gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;

public class ChartDrawingLayers {
    // passed to chart: plotcontent, canvas, peakCanvas, annoCanvals
    // added to chartPane: canvas, chartGroup, peakCancas, annoCanvas, plotContent
    // difference: chartGroup, which is the gridpane

    private final GridPaneCanvas chartGroup;
    private final Canvas canvas = new Canvas();
    private final Canvas peakCanvas = new Canvas();
    private final Canvas annoCanvas = new Canvas();
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
