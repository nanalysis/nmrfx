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

import java.util.Optional;

/**
 * @author brucejohnson
 */
//TODO uncomment once core & utils are merged
//@PluginAPI("ring")
public class XYCanvasBarChart extends XYCanvasChart {

    private static final Logger log = LoggerFactory.getLogger(XYCanvasBarChart.class);

    public XYCanvasBarChart(Canvas canvas, final Axis... AXIS) {
        super(canvas, AXIS);
        xAxis = AXIS[0];
        yAxis = AXIS[1];
        xAxis.setIntegerAxis(true);
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
        double gap = 0.05;
        int nSeries = getData().size();
        double stepSize = (xAxis.getDisplayPosition(1.0) - xAxis.getDisplayPosition(0.0));
        double fullWidth = stepSize / nSeries;
        double barThickness = fullWidth * (1.0 - gap);
        for (int seriesIndex = 0; seriesIndex < nSeries; seriesIndex++) {
            DataSeries series = getData().get(seriesIndex);
            BarMark barMark = new BarMark(series.fill, Color.BLACK, Orientation.VERTICAL);
            int iValue = 0;
            for (XYValue value : series.getValues()) {
                double x = xAxis.getDisplayPosition(value.getXValue());
                double y = yAxis.getDisplayPosition(value.getYValue());
                boolean drawError = false;
                double errValue = 0.0;
                if (value instanceof XYEValue) {
                    errValue = Math.abs(((XYEValue) value).getError());
                    if (errValue > 1.0e-30) {
                        drawError = true;
                    }
                }
                double low = 0.0;
                double high = 0.0;
                if (drawError) {
                    low = yAxis.getDisplayPosition(value.getYValue() - errValue);
                    high = yAxis.getDisplayPosition(value.getYValue() + errValue);
                }

                double xC = x - 0.5 * stepSize + gap * stepSize / 2.0
                        + barThickness * seriesIndex + barThickness / 2.0;
                double yC = yAxis.getDisplayPosition(0.0);
                double barLength = y - yC;
                if (pickPt != null) {
                    if (barMark.hit(xC, yC, barThickness, barLength, pickPt)) {
                        Hit hit = new Hit(series, iValue, value);
                        hitOpt = Optional.of(hit);
                        break;
                    }
                } else {
                    if (drawError) {
                        barMark.draw(gC, xC, yC, barThickness, barLength, true, low, high);
                    } else {
                        barMark.draw(gC, xC, yC, barThickness, barLength);
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
