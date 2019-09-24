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
package org.nmrfx.chart;

import java.util.List;
import java.util.Optional;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;

/**
 *
 * @author brucejohnson
 */
public class XYCanvasChart {

    public final Canvas canvas;
    String title = "";
    public Axis xAxis;
    public Axis yAxis;
    double xPos = 0.0;
    double yPos = 0.0;
    double leftBorder = 0.0;
    double rightBorder = 0.0;
    double topBorder = 0.0;
    double bottomBorder = 0.0;
    double minLeftBorder = 0.0;
    double minBottomBorder = 0.0;
    boolean showLegend = true;
    double legendHeight = 50;
    CanvasLegend legend;
    ObservableList<DataSeries> data = FXCollections.observableArrayList();

    private DoubleProperty widthProperty;

    public DoubleProperty widthProperty() {
        if (widthProperty == null) {
            widthProperty = new SimpleDoubleProperty(this, "width", 200.0);
        }
        return widthProperty;
    }

    public void setWidth(double value) {
        widthProperty().set(value);
    }

    public double getWidth() {
        return widthProperty().get();
    }

    private DoubleProperty heightProperty;

    public DoubleProperty heightProperty() {
        if (heightProperty == null) {
            heightProperty = new SimpleDoubleProperty(this, "height", 200.0);
        }
        return heightProperty;
    }

    public void setHeight(double value) {
        heightProperty().set(value);
    }

    public double getHeight() {
        return heightProperty().get();
    }

    public void setXPos(double value) {
        xPos = value;
    }

    public void setYPos(double value) {
        yPos = value;
    }

    public void setMinLeftBorder(double value) {
        minLeftBorder = value;
    }

    public double getMinLeftBorder() {
        return minLeftBorder;
    }

    public void setMinBottomBorder(double value) {
        minBottomBorder = value;
    }

    public double getMinBottomBorder() {
        return minBottomBorder;
    }

    public Axis getXAxis() {
        return xAxis;
    }

    public Axis getYAxis() {
        return yAxis;
    }

    public void setShowLegend(boolean state) {
        showLegend = state;
    }

    public boolean getShowLegend() {
        return showLegend;
    }

    public static XYCanvasChart buildChart(Canvas canvas) {
        Axis xAxis = new Axis(Orientation.HORIZONTAL, 0, 100, 400, 100.0);
        Axis yAxis = new Axis(Orientation.VERTICAL, 0, 100, 100, 400);
        return new XYCanvasChart(canvas, xAxis, yAxis);
    }

    public XYCanvasChart(Canvas canvas, final Axis... AXIS) {
        this.canvas = canvas;
        xAxis = AXIS[0];
        yAxis = AXIS[1];
        widthProperty().addListener(e -> drawChart());
        heightProperty().addListener(e -> drawChart());
        data.addListener((ListChangeListener) (e -> seriesChanged()));
        legend = new CanvasLegend(this);

    }

    void seriesChanged() {
        autoScale(false);
        drawChart();
    }

    public double[] calcAutoScale() {
        if (!data.isEmpty()) {
            double xMax = Double.NEGATIVE_INFINITY;
            double xMin = Double.MAX_VALUE;
            double yMax = Double.NEGATIVE_INFINITY;
            double yMin = Double.MAX_VALUE;
            boolean ok = false;
            for (DataSeries dataSeries : data) {
                if (!dataSeries.values.isEmpty()) {
                    ok = true;
                    xMax = Math.max(xMax, dataSeries.values.stream().mapToDouble(XYValue::getXValue).max().getAsDouble());
                    xMin = Math.min(xMin, dataSeries.values.stream().mapToDouble(XYValue::getXValue).min().getAsDouble());
                    yMax = Math.max(yMax, dataSeries.values.stream().mapToDouble(XYValue::getMaxYValue).max().getAsDouble() / dataSeries.getScale());
                    yMin = Math.min(yMin, dataSeries.values.stream().mapToDouble(XYValue::getMinYValue).min().getAsDouble() / dataSeries.getScale());
                }
            }

            if (ok) {
                double[] bounds = {xMin, xMax, yMin, yMax};
                return bounds;
            }
        }
        return null;
    }

    public double[] autoScale(boolean force) {
        double[] bounds = null;
        if (!data.isEmpty() && (force || xAxis.isAutoRanging() || yAxis.isAutoRanging())) {
            double[] minMax = calcAutoScale();
            if (minMax != null) {
                bounds = new double[4];
                if (force || xAxis.isAutoRanging()) {
                    double[] axBounds = xAxis.autoRange(minMax[0], minMax[1]);
                    bounds[0] = axBounds[0];
                    bounds[1] = axBounds[1];
                }
                if (force || yAxis.isAutoRanging()) {
                    double[] axBounds = yAxis.autoRange(minMax[2], minMax[3]);
                    bounds[2] = axBounds[0];
                    bounds[3] = axBounds[1];
                }
            }
            drawChart();
        }
        return bounds;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setNames(String title, String xAxisName, String yAxisName, String extra) {
        this.title = title;
        xAxis.setLabel(xAxisName);
        yAxis.setLabel(yAxisName);
    }

    public void setBounds(double x1, double x2, double y1, double y2, double xTic, double yTic) {
        xAxis.setMinMax(x1, x2);
        yAxis.setMinMax(y1, y2);
    }

    public ObservableList<DataSeries> getData() {
        return data;
    }

    public void setData(List<DataSeries> data) {
        this.data.setAll(data);
        drawChart();
    }

    public double[] getMinBorders() {
        // fixme
//        xAxis.setTickFontSize(PreferencesController.getTickFontSize());
//        xAxis.setLabelFontSize(PreferencesController.getLabelFontSize());
        xAxis.setTickFontSize(10);
        xAxis.setLabelFontSize(12);
        double[] borders = new double[4];

//        yAxis.setTickFontSize(PreferencesController.getTickFontSize());
//        yAxis.setLabelFontSize(PreferencesController.getLabelFontSize());
        yAxis.setTickFontSize(10);
        yAxis.setLabelFontSize(12);
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
        if (showLegend && (data.size() > 1)) {
            borders[2] += legend.getLegendHeight();
        }
        return borders;
    }

    public void drawChart() {
        GraphicsContext gCC = canvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(gCC);
        gC.clearRect(xPos, yPos, getWidth(), getHeight());
        drawChart(gC);
    }

    public void drawChart(GraphicsContextInterface gC) {
        double width = getWidth();
        double height = getHeight();
        double minDimSize = width < height ? width : height;
        gC.save();
        try {
            // fixme
            xAxis.setTickFontSize(10);
            xAxis.setLabelFontSize(12);

            yAxis.setTickFontSize(10);
            yAxis.setLabelFontSize(12);
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
            if (showLegend && (data.size() > 1)) {
                legend.draw(gC);
            }
            gC.clip();
            gC.beginPath();
            for (DataSeries series : data) {
                double radius = series.radius;
                if (series.radiusInPercent) {
                    radius = radius * minDimSize;
                }
                if (series.fillSymbol || series.strokeSymbol) {
                    for (XYValue xyValue : series.values) {
                        double x = xyValue.getXValue();
                        double y = xyValue.getYValue();
                        y /= series.getScale();
                        double xC = xAxis.getDisplayPosition(x);
                        double yC = yAxis.getDisplayPosition(y);
                        series.symbol.draw(gC, xC, yC, radius, series.stroke, series.fill);
                        if (xyValue instanceof XYEValue) {
                            double errValue = ((XYEValue) xyValue).getError();
                            errValue /= series.getScale();
                            double yCHi = yAxis.getDisplayPosition(y + errValue);
                            double yCLow = yAxis.getDisplayPosition(y - errValue);
                            gC.setStroke(Color.BLACK);
                            gC.strokeLine(xC, yCHi, xC, yCLow);

                        }
                    }
                }
                if (series.drawLine) {
                    boolean firstPoint = true;
                    double lastXC = 0.0;
                    double lastYC = 0.0;
                    gC.setStroke(series.stroke);
                    for (XYValue xyValue : series.values) {
                        double x = xyValue.getXValue();
                        double y = xyValue.getYValue();
                        y /= series.getScale();
                        double xC = xAxis.getDisplayPosition(x);
                        double yC = yAxis.getDisplayPosition(y);
                        if (!firstPoint) {
                            gC.strokeLine(lastXC, lastYC, xC, yC);
                        }
                        lastXC = xC;
                        lastYC = yC;
                        firstPoint = false;
                    }
                }
            }
        } catch (GraphicsIOException ioE) {
            ioE.printStackTrace();
        }
        gC.restore();
    }

    public class Hit {

        DataSeries series;
        int index;
        XYValue value;

        Hit(DataSeries series, int index, XYValue value) {
            this.series = series;
            this.index = index;
            this.value = value;
        }

        public DataSeries getSeries() {
            return series;
        }

        public XYValue getValue() {
            return value;
        }

        public int getIndex() {
            return index;
        }

        public String toString() {
            return series.getName() + ":" + index;
        }
    }

    public Optional<Hit> pickChart(double mouseX, double mouseY, double hitRadius) {
        double minDimSize = getWidth() < getHeight() ? getWidth() : getHeight();
        Optional<Hit> hitOpt = Optional.empty();
        double minDelta = Double.MAX_VALUE;
        for (DataSeries series : data) {
            double radius = series.radius;
            if (series.radiusInPercent) {
                radius = radius * minDimSize;
            }
            int i = 0;
            for (XYValue xyValue : series.values) {
                double x = xyValue.getXValue();
                double y = xyValue.getYValue();
                double xC = xAxis.getDisplayPosition(x);
                double yC = yAxis.getDisplayPosition(y);
                double dX = Math.abs(xC - mouseX);
                double dY = Math.abs(yC - mouseY);
                if ((dX < hitRadius) && (dY < hitRadius)) {
                    double delta = Math.sqrt(dX * dX + dY * dY);
                    if (delta < hitRadius) {
                        if (delta < minDelta) {
                            minDelta = delta;
                            Hit hit = new Hit(series, i, xyValue);
                            hitOpt = Optional.of(hit);
                        }
                    }
                }
                i++;
            }
        }
        return hitOpt;
    }

}
