/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.spectra;

import eu.hansolo.fx.charts.Axis;
import eu.hansolo.fx.charts.Position;
import javafx.util.StringConverter;
import java.util.List;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Orientation;
import javafx.scene.layout.AnchorPane;
import javafx.util.converter.NumberStringConverter;

/**
 *
 * @author brucejohnson
 */
public class NMRAxis extends Axis implements NMRAxisLimits {

    private Object currentAnimationID;
    private IntegerProperty currentRangeIndexProperty = new SimpleIntegerProperty(this, "currentRangeIndex", -1);
    private ReadOnlyDoubleWrapper scale = new ReadOnlyDoubleWrapper(this, "scale", 1.0);
    StringConverter<Number> defaultFormatter = new NumberStringConverter();
    final NMRAxisBase baseAxis;
    boolean showTicsAndLabels = true;

    public NMRAxis(Orientation orientation, Position position, double lowerBound, double upperBound, double tickUnit) {
        super(orientation, position);
        setMinMax(lowerBound, upperBound);
        setMajorTickSpace(tickUnit);
        baseAxis = new NMRAxisBase();

        if (position == Position.BOTTOM) {
            AnchorPane.setBottomAnchor(this, 0d);
            AnchorPane.setLeftAnchor(this, 75d);
            AnchorPane.setRightAnchor(this, 25d);
            setPrefHeight(50);
        } else if (position == Position.LEFT) {
            AnchorPane.setTopAnchor(this, 0d);
            AnchorPane.setBottomAnchor(this, 50d);
            AnchorPane.setLeftAnchor(this, 0d);
            setPrefWidth(70);

        }
    }

    @Override
    protected void drawAxis() {
        super.drawAxis();
    }

    public void setReverse(boolean state) {
        setAxisReversed(state);
    }

    public boolean getReverse() {
        return isAxisReversed();
    }

    public boolean getShowTicsAndLabels() {
        return showTicsAndLabels;
    }

    public void setShowTicsAndLabels(boolean state) {
        showTicsAndLabels = state;
    }

    ReadOnlyDoubleWrapper scalePropertyImpl() {
        return scale;
    }

    public double getScale() {
        return scale.get();
    }

    public Number getValueForDisplay(double displayPosition) {
        double scaleValue = calcScale();
        if (isAxisReversed() && (getOrientation() == Orientation.HORIZONTAL)) {
            displayPosition = getWidth() - displayPosition;
        } else if (!isAxisReversed() && (getOrientation() == Orientation.VERTICAL)) {
            displayPosition = getHeight() - displayPosition;
        }
        double offset = 0.0;
        return ((displayPosition - offset) / scaleValue) + minValueProperty().get();

    }

    double calcScale() {
        double scaleValue;
        double range = maxValueProperty().get() - minValueProperty().get();
        if (getOrientation() == Orientation.VERTICAL) {
            scaleValue = getHeight() / range;
        } else {
            scaleValue = getWidth() / range;
        }
        return scaleValue;
    }

    public double getDisplayPosition(Number value) {
        double offset = 0.0;
        double scaleValue = calcScale();
        double displayPosition = offset + ((value.doubleValue() - minValueProperty().get()) * scaleValue);
        if (isAxisReversed() && (getOrientation() == Orientation.HORIZONTAL)) {
            displayPosition = getWidth() - displayPosition;
        } else if (!isAxisReversed() && (getOrientation() == Orientation.VERTICAL)) {
            displayPosition = getHeight() - displayPosition;
        }
        return displayPosition;

    }

    /**
     * Calculate a list of all the data values for each tick mark in range
     *
     * @param length The length of the axis in display units
     * @param range A range object returned from autoRange()
     * @return A list of tick marks that fit along the axis if it was the given
     * length
     */
    protected List<Number> calculateTickValues(double length, Object range) {
        return baseAxis.calculateTickValues(length, range);

//        final double[] rangeProps = (double[]) range;
//        final double lowerBound = rangeProps[0];
//        final double upperBound = rangeProps[1];
//        final double tickUnit = rangeProps[2];
//        calculateSpacing(lowerBound, upperBound, length);
//        return getTics(lowerBound, upperBound);
    }

    public void setTickUnit(double value) {
        setMajorTickSpace(value);
    }

    @Override
    public double getLowerBound() {
        return minValueProperty().get();
    }

    @Override
    public double getUpperBound() {
        return maxValueProperty().get();
    }

    public void setLowerBound(double value) {
        setMinValue(value);
    }

    public void setUpperBound(double value) {
        setMaxValue(value);
    }

    @Override
    public void setLabel(String label) {
        setTitle(label);
    }

    @Override
    public String getLabel() {
        return getTitle();
    }

}
