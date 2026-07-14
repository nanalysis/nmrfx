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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.paint.Color;

/**
 * @author brucejohnson
 */
//TODO uncomment once core & utils are merged
//@PluginAPI("ring")
public class DataSeries {

    private final ObservableList<XYValue> values = FXCollections.observableArrayList();
    private final FilteredList<XYValue> fValues = new FilteredList<>(values, p -> true);

    String name = "";
    Color fill = Color.BLACK;
    Color stroke = Color.BLACK;
    Symbol symbol = Symbol.CIRCLE;
    double radius = 3;
    boolean radiusInPercent = false;
    boolean fillSymbol = true;
    boolean strokeSymbol = true;
    boolean drawLine = false;
    private double scale = 1.0;
    private double minX = Double.MAX_VALUE;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double minY = Double.MAX_VALUE;
    private double maxY = Double.NEGATIVE_INFINITY;
    private double limitXMin = Double.NEGATIVE_INFINITY;
    private double limitXMax = Double.MAX_VALUE;

    public DataSeries() {
        updatePredicate();
    }

    public ObservableList<XYValue> getValues() {
        return fValues;
    }

    public ObservableList<XYValue> getData() {
        return values;
    }

    public void clear() {
        values.clear();
        minX = Double.MAX_VALUE;
        maxX = Double.NEGATIVE_INFINITY;
        minY = Double.MAX_VALUE;
        maxY = Double.NEGATIVE_INFINITY;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public void drawLine(boolean state) {
        drawLine = state;
    }

    public void drawSymbol(boolean state) {
        strokeSymbol = state;
    }

    public void fillSymbol(boolean state) {
        fillSymbol = state;
    }

    public void setStroke(Color color) {
        this.stroke = color;
    }

    public void setFill(Color color) {
        this.fill = color;
    }

    public void setRadius(double value) {
        radius = value;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void autoScale() {
        minX = Double.MAX_VALUE;
        maxX = Double.NEGATIVE_INFINITY;
        minY = Double.MAX_VALUE;
        maxY = Double.NEGATIVE_INFINITY;
        for (XYValue value : values) {
            if (value.isDisabled()) {
                continue;
            }
            minX = Math.min(minX, value.getXValue());
            maxX = Math.max(maxX, value.getXValue());
            minY = Math.min(minY, value.getMinYValue());
            maxY = Math.max(maxY, value.getMaxYValue());
        }
    }

    public void add(XYValue value) {
        values.add(value);
        if (!value.isDisabled()) {
            minX = Math.min(minX, value.getXValue());
            maxX = Math.max(maxX, value.getXValue());
            minY = Math.min(minY, value.getMinYValue());
            maxY = Math.max(maxY, value.getMaxYValue());
        }
    }

    public double getMinX() {
        return minX;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMinY() {
        return minY / scale;
    }

    public double getMaxY() {
        return maxY / scale;
    }

    /**
     * @return the scale
     */
    public double getScale() {
        return scale;
    }

    /**
     * @param scale the scale to set
     */
    public void setScale(double scale) {
        this.scale = scale;
    }

    public void setLimits(double limitXMin, double limitXMax) {
        this.limitXMin = limitXMin;
        this.limitXMax = limitXMax;
        updatePredicate();
    }

    public void updatePredicate() {
        fValues.setPredicate(p -> p.getXValue() >= limitXMin && p.getXValue() <= limitXMax);
    }

}
