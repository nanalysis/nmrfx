/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chart;

import java.util.Optional;
import javafx.collections.ListChangeListener;
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
public class XYCanvasBarChart extends XYCanvasChart {

    public static XYCanvasBarChart buildChart(Canvas canvas) {
        Axis xAxis = new Axis(Orientation.HORIZONTAL, 0, 100, 400, 100.0);
        Axis yAxis = new Axis(Orientation.VERTICAL, 0, 1.0, 100, 400);
        yAxis.setZeroIncluded(true);
        return new XYCanvasBarChart(canvas, xAxis, yAxis);
    }

    public XYCanvasBarChart(Canvas canvas, final Axis... AXIS) {
        super(canvas, AXIS);
        xAxis = AXIS[0];
        yAxis = AXIS[1];
        widthProperty().addListener(e -> drawChart());
        heightProperty().addListener(e -> drawChart());
        data.addListener((ListChangeListener) (e -> seriesChanged()));
        showLegend = false;
    }

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
        try {
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
            int nSeries = getData().size();
            for (int seriesIndex = 0; seriesIndex < getData().size(); seriesIndex++) {
                DataSeries series = getData().get(seriesIndex);
                Color fill = series.fill;
                for (XYValue value : series.values) {
                    double x = xAxis.getDisplayPosition(value.getXValue());
                    double y = yAxis.getDisplayPosition(value.getYValue());
                    double zeroPos = yAxis.getDisplayPosition(0.0);
                    double barWidth;
                    double fullWidth;
                    double stepSize;
                    double gap = 0.05;
                    stepSize = (xAxis.getDisplayPosition(1.0) - xAxis.getDisplayPosition(0.0));
                    fullWidth = stepSize / nSeries;
                    barWidth = fullWidth - gap * stepSize / nSeries;
                    double x1 = x - 0.5 * stepSize + gap * stepSize / 2.0 + barWidth * seriesIndex;
                    double x2 = x1 + barWidth;
                    double y0 = zeroPos;
                    double y2 = y;
                    gC.setFill(fill);
                    double w = x2 - x1;
                    double h = Math.abs(y0 - y2);
                    if (y2 > y0) {
                        y2 = y0;
                    }
                    gC.fillRect(x1, y2, w, h);
                    if (value instanceof XYEValue) {
                        double errValue = ((XYEValue) value).getError();
                        if (errValue != 0.0) {
                            double low = yAxis.getDisplayPosition(value.getYValue() - errValue);
                            double high = yAxis.getDisplayPosition(value.getYValue() + errValue);
                            gC.setStroke(Color.BLACK);
                            double xc = x1 + barWidth / 2;
                            gC.strokeLine(xc, low, xc, high);
                        }
                    }

                }
            }
        } catch (GraphicsIOException ioE) {
            ioE.printStackTrace();

        }
        gC.restore();

    }

    public Optional<Hit> pickChart(double mouseX, double mouseY, double hitRadius) {
        double width = getWidth();
        double height = getHeight();

        double minDimSize = width < height ? width : height;
        Optional<Hit> hitOpt = Optional.empty();
        int nSeries = getData().size();
        int seriesIndex = 0;
        for (DataSeries series : data) {
            double radius = series.radius;
            if (series.radiusInPercent) {
                radius = radius * minDimSize;
            }
            int i = 0;
            for (XYValue value : series.values) {
                double x = xAxis.getDisplayPosition(value.getXValue());
                double y = yAxis.getDisplayPosition(value.getYValue());
                double zeroPos = yAxis.getDisplayPosition(0.0);
                double barWidth;
                double fullWidth;
                double stepSize;
                double gap = 0.05;
                stepSize = (xAxis.getDisplayPosition(1.0) - xAxis.getDisplayPosition(0.0));
                fullWidth = stepSize / nSeries;
                barWidth = fullWidth - gap * stepSize / nSeries;
                double x1 = x - 0.5 * stepSize + gap * stepSize / 2.0 + barWidth * seriesIndex;
                double x2 = x1 + barWidth;
                double y1 = zeroPos;
                double y2 = y;
                if (y2 > y1) {
                    double hold = y1;
                    y1 = y2;
                    y2 = hold;
                }
                if ((mouseX > x1) && (mouseX < x2)) {
                    if ((mouseY > y2) && (mouseY < y1)) {
                        Hit hit = new Hit(series, i, value);
                        hitOpt = Optional.of(hit);
                    }
                }
                i++;
            }
            seriesIndex++;
        }
        return hitOpt;
    }

}
