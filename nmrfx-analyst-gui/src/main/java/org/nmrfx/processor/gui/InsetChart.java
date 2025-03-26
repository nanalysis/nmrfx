package org.nmrfx.processor.gui;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.shape.Rectangle;

import java.util.List;

public class InsetChart {
    PolyChart parent;
    PolyChart chart;
    double fX = 0.1;
    double fY = 0.1;
    double fWidth = 0.3;
    double fHeight = 0.3;

    double xp1;
    double yp1;
    double xp2;
    double yp2;

    double startX;
    double startY;

    double startfX;
    double startfY;
    double startfWidth;
    double startffHeight;

    public InsetChart(PolyChart chart, PolyChart parent) {
        this.chart = chart;
        this.parent = parent;
    }

    public void setFractionalPosition(Double x, Double y, Double width, Double height) {
        fX = x == null ? fX : x;
        fY = y == null ? fY : y;
        fWidth = width == null ? fWidth : width;
        fHeight = height == null ? fHeight : height;
    }

    public void setPosition(double x, double y, double width, double height, Insets borders) {
        width -= borders.getLeft() + borders.getRight();
        height -= borders.getTop() + borders.getBottom();
        xp1 = width * fX + x + borders.getLeft();
        yp1 = height * fY + y + borders.getTop();
        double insetWidth = width * fWidth;
        double insetHeight = height * fHeight;
        chart.setLayoutX(xp1);
        chart.setLayoutY(yp1);
        xp2 = xp1 + insetWidth;
        yp2 = yp1 + insetHeight;
        chart.setWH(insetWidth, insetHeight);
    }

    public void shift(double dX, double dY) {
        fX += dX;
        fY += dY;
    }

    public void refresh() {
        chart.refresh();
    }

    public PolyChart getChart() {
        return chart;
    }

    public List<Double> getPosition() {
        return List.of(fX, fY, fWidth, fHeight);
    }

    public void remove() {
        chart.setChartDisabled(true);
        PolyChartManager.getInstance().setSelectedChart(null);
        parent.removeInsetChart(this);
        chart.close();
    }

    void drag(int i, double x, double y) {
        mouseAction(i, x, y, false);
    }

    void release(int i, double x, double y) {
        mouseAction(i, x, y, true);
    }

    void mouseAction(int i, double x, double y, boolean release) {
        double dX = x - startX;
        double dY = y - startY;
        double nxp1 = xp1;
        double nyp1 = yp1;
        double nxp2 = xp2;
        double nyp2 = yp2;
        switch (i) {
            case 0 -> {
                nxp1 += dX;
                nyp1 += dY;
            }
            case 1 -> {
                nxp2 += dX;
                nyp1 += dY;
            }
            case 2 -> {
                nxp2 += dX;
                nyp2 += dY;
            }
            case 3 -> {
                nxp1 += dX;
                nyp2 += dY;
            }
            default -> {
                nxp1 += dX;
                nxp2 += dX;
                nyp1 += dY;
                nyp2 += dY;
            }
        }
        if (release) {
            Rectangle2D rectangle2D = parent.getFractionalPosition(nxp1, nyp1, nxp2, nyp2);
            fX = rectangle2D.getMinX();
            fY = rectangle2D.getMinY();
            fWidth = rectangle2D.getWidth();
            fHeight = rectangle2D.getHeight();
            parent.refresh();
        } else {
            chart.moveHighlightChart(nxp1, nyp1, nxp2 - nxp1, nyp2 - nyp1);
        }
    }

    void set(double x, double y) {
        startX = x;
        startY = y;
        startfX = fX;
        startfY = fY;
        startfWidth = fWidth;
        startffHeight = fHeight;
    }

    void setHandlePositions(Rectangle[] rectangles) {
        drawHandle(rectangles[0], xp1, yp1);
        drawHandle(rectangles[1], xp2, yp1);
        drawHandle(rectangles[2], xp2, yp2);
        drawHandle(rectangles[3], xp1, yp2);
    }

    void drawHandle(Rectangle rectangle, double x, double y) {
        double offset = CanvasAnnotation.getHandleWidth() / 2.0;
        rectangle.setX(x - offset);
        rectangle.setY(y - offset);
        rectangle.setWidth(CanvasAnnotation.getHandleWidth());
        rectangle.setHeight(CanvasAnnotation.getHandleWidth());
    }
}
