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

import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author brucejohnson
 */
public class XYCanvasBoxChart extends XYCanvasChart {

    private static final Logger log = LoggerFactory.getLogger(XYCanvasBoxChart.class);
    Orientation orientation = Orientation.VERTICAL;

    public static XYCanvasBoxChart buildChart(Canvas canvas) {
        Axis xAxis = new Axis(Orientation.HORIZONTAL, 0, 100, 400, 100.0);
        Axis yAxis = new Axis(Orientation.VERTICAL, 0, 1.0, 100, 400);
        yAxis.setZeroIncluded(true);
        return new XYCanvasBoxChart(canvas, xAxis, yAxis);
    }

    public XYCanvasBoxChart(Canvas canvas, final Axis... AXIS) {
        super(canvas, AXIS);
        xAxis = AXIS[0];
        yAxis = AXIS[1];
        widthProperty().addListener(e -> drawChart());
        heightProperty().addListener(e -> drawChart());
        data.addListener((ListChangeListener) (e -> seriesChanged()));
        showLegend = false;
    }

    @Override
    public void drawChart() {
        double width = getWidth();
        double height = getHeight();
        GraphicsContext gCC = canvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(gCC);
        gC.clearRect(xPos, yPos, width, height);
        drawChart(gC);
    }

    public void annotate(GraphicsContextInterface gC) {

    }

    @Override
    public void drawChart(GraphicsContextInterface gC) {
        double width = getWidth();
        double height = getHeight();
        List<String> names = data.stream().map(x -> x.getName()).collect(Collectors.toList());
        gC.save();
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

        gC.clip();
        gC.beginPath();
        annotate(gC);
        drawSeries(gC);
        gC.restore();
    }

    @Override
    public double[] calcAutoScale() {
        if (!data.isEmpty()) {
            double pMin = 0.5;
            double pMax = data.size() + 0.5;
            double vMax = Double.NEGATIVE_INFINITY;
            double vMin = Double.MAX_VALUE;
            boolean ok = false;
            for (DataSeries dataSeries : data) {
                if (!dataSeries.getValues().isEmpty()) {
                    ok = true;
                    vMax = Math.max(vMax, dataSeries.getValues().stream().
                            mapToDouble(v
                                    -> ((BoxPlotData) v.getExtraValue()).max)
                            .max().getAsDouble());

                    vMin = Math.min(vMin, dataSeries.getValues().stream().
                            mapToDouble(v
                                    -> ((BoxPlotData) v.getExtraValue()).min)
                            .min().getAsDouble());
                }
            }
            if (ok) {
                if (orientation == Orientation.VERTICAL) {
                    double[] bounds = {pMin, pMax, vMin, vMax};
                    return bounds;
                } else {
                    double[] bounds = {vMin, vMax, pMin, pMax};
                    return bounds;
                }
            }
        }
        return null;
    }

    void drawSeries(GraphicsContextInterface gC) {
        doSeries(gC, null);
    }

    @Override
    public Optional<Hit> pickChart(double mouseX, double mouseY, double hitRadius) {
        PickPoint pickPt = new PickPoint(mouseX, mouseY, hitRadius);
        return doSeries(null, pickPt);
    }

    Optional<Hit> doSeries(GraphicsContextInterface gC, PickPoint pickPt) {
        Optional<Hit> hitOpt = Optional.empty();
        int nSeries = getData().size();
        double stepSize = (xAxis.getDisplayPosition(1.0) - xAxis.getDisplayPosition(0.0));
        double barThickness = stepSize / 3.0;
        for (int seriesIndex = 0; seriesIndex < nSeries; seriesIndex++) {
            DataSeries series = getData().get(seriesIndex);
            var boxMark = new BoxMark(series.fill, Color.BLACK, Orientation.VERTICAL);
            int iValue = 0;
            for (XYValue value : series.getValues()) {
                var fiveNum = (BoxPlotData) value.getExtraValue();
                if (fiveNum != null) {
                    double x = value.getXValue();
                    if (pickPt != null) {
                        if (boxMark.hit(xPos, barThickness, fiveNum, pickPt, xAxis, xAxis)) {
                            Hit hit = new Hit(series, iValue, value);
                            hitOpt = Optional.of(hit);
                            break;
                        }
                    } else {
                        boxMark.draw(gC, x, barThickness, fiveNum, xAxis, yAxis);
                    }
                }
                iValue++;
            }
            if (hitOpt.isPresent()) {
                break;
            }
        }
        return hitOpt;
    }

}
