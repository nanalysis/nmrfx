/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chart;

import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;

/**
 * @author brucejohnson
 */
//TODO uncomment once core & utils are merged
//@PluginAPI("ring")
public class XYChartPane extends Pane {

    Canvas canvas;
    XYCanvasChart chart;

    public XYChartPane() {
        canvas = new Canvas();
        chart = XYCanvasChart.buildChart(canvas);
        addCanvas(canvas);
        widthProperty().addListener(e -> updateChart());
        heightProperty().addListener(e -> updateChart());
    }

    private void addCanvas(Canvas canvas) {
        getChildren().clear();
        getChildren().add(canvas);
    }

    public XYCanvasChart getChart() {
        return chart;
    }

    public XYCanvasBoxChart getBoxChart() {
        if (!(chart instanceof XYCanvasBoxChart)) {
            chart = XYCanvasBoxChart.buildChart(canvas);
        }
        return (XYCanvasBoxChart) chart;
    }

    public XYCanvasChart getXYChart() {
        if ((chart instanceof XYCanvasBoxChart) || (chart instanceof XYCanvasBarChart)) {
            chart = XYCanvasChart.buildChart(canvas);
        }
        return chart;
    }

    public XYCanvasBarChart getBarChart() {
        if (!(chart instanceof  XYCanvasBarChart)) {
            chart = XYCanvasBarChart.buildChart(canvas);
        }
        return (XYCanvasBarChart) chart;
    }

    public void updateChart() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        chart.setWidth(canvas.getWidth());
        chart.setHeight(canvas.getHeight());
        chart.drawChart();
    }
}
