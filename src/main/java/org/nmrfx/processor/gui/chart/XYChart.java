/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.chart;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.graphicsio.GraphicsContextInterface;
import org.nmrfx.processor.gui.graphicsio.GraphicsContextProxy;
import org.nmrfx.processor.gui.graphicsio.GraphicsIOException;

/**
 *
 * @author brucejohnson
 */
public class XYChart {

    Canvas canvas;
    Axis xAxis;
    Axis yAxis;
    double width = 200.0;
    double height = 200.0;
    double xPos = 0.0;
    double yPos = 0.0;
    double leftBorder = 0.0;
    double rightBorder = 0.0;
    double topBorder = 0.0;
    double bottomBorder = 0.0;
    double minLeftBorder = 0.0;
    double minBottomBorder = 0.0;
    ObservableList<DataSeries> data = FXCollections.observableArrayList();

    public static XYChart buildChart(Canvas canvas) {
        Axis xAxis = new Axis(Orientation.HORIZONTAL, 0, 100, 400, 100.0);
        Axis yAxis = new Axis(Orientation.VERTICAL, 0, 100, 100, 400);
        return new XYChart(canvas, xAxis, yAxis);
    }

    public XYChart(Canvas canvas, final Axis... AXIS) {
        this.canvas = canvas;
        xAxis = AXIS[0];
        yAxis = AXIS[1];
        width = canvas.getWidth();
        height = canvas.getHeight();
        DataSeries series = new DataSeries();
        for (int i = 0; i < 10; i++) {
            XYValue value = new XYValue(i * 10.0, i * 10.0);
            series.add(value);
        }
        data.add(series);
        canvas.widthProperty().addListener(e -> drawChart());
        canvas.heightProperty().addListener(e -> drawChart());
    }

    public ObservableList<DataSeries> getData() {
        return data;
    }

    public double[] getMinBorders() {
        xAxis.setTickFontSize(PreferencesController.getTickFontSize());
        xAxis.setLabelFontSize(PreferencesController.getLabelFontSize());
        double[] borders = new double[4];

        yAxis.setTickFontSize(PreferencesController.getTickFontSize());
        yAxis.setLabelFontSize(PreferencesController.getLabelFontSize());
        borders[0] = yAxis.getBorderSize();
        borders[2] = xAxis.getBorderSize();

        borders[1] = borders[0] / 4;
        borders[3] = borders[2] / 4;
        borders[3] = 7.0;
        borders[1] = 7.0;
        return borders;
    }

    public double[] getUseBorders() {
        double[] borders = getMinBorders();
        borders[0] = Math.max(borders[0], minLeftBorder);
        borders[2] = Math.max(borders[2], minBottomBorder);
        return borders;
    }

    public void drawChart() {
        width = canvas.getWidth();
        height = canvas.getHeight();
        double minDimSize = width < height ? width : height;
        System.out.println("draw chart " + xPos + " " + yPos + " " + width + " " + height);
        GraphicsContext gCC = canvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(gCC);
        gC.save();
        try {
            gC.clearRect(xPos, yPos, width, height);
            xAxis.setTickFontSize(PreferencesController.getTickFontSize());
            xAxis.setLabelFontSize(PreferencesController.getLabelFontSize());

            yAxis.setTickFontSize(PreferencesController.getTickFontSize());
            yAxis.setLabelFontSize(PreferencesController.getLabelFontSize());
            double[] borders = getUseBorders();
            leftBorder = borders[0];
            rightBorder = borders[1];
            bottomBorder = borders[2];
            topBorder = borders[3];

            xAxis.setWidth(width - leftBorder - rightBorder);
            xAxis.setHeight(bottomBorder);
            xAxis.setOrigin(xPos + leftBorder, yPos + height - bottomBorder);

            yAxis.setHeight(height - bottomBorder - topBorder);
            yAxis.setWidth(leftBorder);
            yAxis.setOrigin(xPos + leftBorder, yPos + height - bottomBorder);

            gC.setStroke(Color.BLACK);
            xAxis.draw(gC);
            yAxis.draw(gC);
            gC.setLineWidth(xAxis.getLineWidth());
            gC.strokeLine(xPos + leftBorder, yPos + topBorder, xPos + width - rightBorder, yPos + topBorder);
            gC.strokeLine(xPos + width - rightBorder, yPos + topBorder, xPos + width - rightBorder, yPos + height - bottomBorder);
            gC.rect(xPos + leftBorder, yPos + topBorder, xAxis.getWidth(), yAxis.getHeight());
            gC.clip();
            gC.beginPath();
            System.out.println("data size " + data.size());
            for (DataSeries series : data) {
                double radius = series.radius;
                if (series.radiusInPercent) {
                    radius = radius * minDimSize;
                }
                for (XYValue xyValue : series.values) {
                    double xC = xyValue.getX();
                    double yC = xyValue.getY();
                    xC = xAxis.getDisplayPosition(xC);
                    yC = yAxis.getDisplayPosition(yC);
                    series.symbol.draw(gC, xC, yC, radius, series.stroke, series.fill);
                }
            }
        } catch (GraphicsIOException ioE) {
            ioE.printStackTrace();
            //gC.restore();
        }
        gC.restore();
    }

}
