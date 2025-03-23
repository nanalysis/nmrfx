package org.nmrfx.processor.gui;

import javafx.geometry.Insets;

import java.util.List;

public class InsetChart {
    PolyChart parent;
    PolyChart chart;
    double fX = 0.1;
    double fY = 0.1;
    double fWidth = 0.3;
    double fHeight = 0.3;
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
        double insetX = width * fX + x + borders.getLeft();
        double insetY = height * fY + y + borders.getTop();
        double insetWidth = width * fWidth;
        double insetHeight = height * fHeight;
        chart.setLayoutX(insetX);
        chart.setLayoutY(insetY);
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
        parent.removeInsetChart(this);
    }
}
