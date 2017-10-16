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

import javafx.util.StringConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.CssMetaData;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.scene.chart.ValueAxis;
import javafx.util.converter.NumberStringConverter;

/**
 *
 * @author brucejohnson
 */
public class NMRAxis extends ValueAxis implements NMRAxisLimits {

    private Object currentAnimationID;
    private IntegerProperty currentRangeIndexProperty = new SimpleIntegerProperty(this, "currentRangeIndex", -1);
    private ReadOnlyDoubleWrapper scale = new ReadOnlyDoubleWrapper(this, "scale", 0);
    StringConverter<Number> defaultFormatter = new NumberStringConverter();
    final NMRAxisBase baseAxis;
    boolean showTicsAndLabels = true;

    public NMRAxis(double lowerBound, double upperBound, double tickUnit) {
        super(lowerBound, upperBound);
        setTickUnit(tickUnit);
        baseAxis = new NMRAxisBase();
    }

    public void setReverse(boolean state) {
        baseAxis.setReverse(state);
    }

    public boolean getReverse() {
        return baseAxis.getReverse();
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

    @Override
    protected List calculateMinorTickMarks() {
        return new ArrayList<Double>();
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        final double[] rangeProps = (double[]) range;
        final double lowerBound = rangeProps[0];
        final double upperBound = rangeProps[1];
        final double tickUnit = rangeProps[2];
        final double scale = rangeProps[3];
        final double rangeIndex = rangeProps[4];
        currentRangeIndexProperty.set((int) rangeIndex);
        final double oldLowerBound = getLowerBound();
        setLowerBound(lowerBound);
        setUpperBound(upperBound);
        setTickUnit(tickUnit);
        currentLowerBound.set(lowerBound);
        setScale(scale);
    }

    @Override
    public Number getValueForDisplay(double displayPosition) {
        if (baseAxis.getReverse()) {
            if (getSide().isVertical()) {
                displayPosition = getHeight() - displayPosition;
            } else {
                displayPosition = getWidth() - displayPosition;
            }
        }
        return super.getValueForDisplay(displayPosition);

    }

    @Override
    public double getDisplayPosition(Number value) {
        double displayPosition = super.getDisplayPosition(value);
        if (baseAxis.getReverse()) {
            if (getSide().isVertical()) {
                return getHeight() - displayPosition;
            } else {
                return getWidth() - displayPosition;
            }
        } else {
            return displayPosition;
        }
    }

    @Override
    protected Object autoRange(double minValue, double maxValue, double length, double labelSize) {
        Double[] range = new Double[]{minValue, maxValue};
        return range;
    }

    @Override
    protected Object getRange() {
        return new double[]{
            getLowerBound(),
            getUpperBound(),
            getTickUnit(),
            getScale(),
            currentRangeIndexProperty.get()
        };
    }

    /**
     * Calculate a list of all the data values for each tick mark in range
     *
     * @param length The length of the axis in display units
     * @param range A range object returned from autoRange()
     * @return A list of tick marks that fit along the axis if it was the given length
     */
    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        return baseAxis.calculateTickValues(length, range);

//        final double[] rangeProps = (double[]) range;
//        final double lowerBound = rangeProps[0];
//        final double upperBound = rangeProps[1];
//        final double tickUnit = rangeProps[2];
//        calculateSpacing(lowerBound, upperBound, length);
//        return getTics(lowerBound, upperBound);
    }

    /**
     * Get the string label name for a tick mark with the given value
     *
     * @param value The value to format into a tick label string
     * @return A formatted string for the given value
     */
    @Override
    protected String getTickMarkLabel(Object value) {
//        return baseAxis.getTickMarkLabel(value);
        StringConverter<Number> formatter = getTickLabelFormatter();
        if (formatter == null) {
            formatter = defaultFormatter;
        }
        return formatter.toString((Number) value);
    }
    // -------------- STYLESHEET HANDLING ------------------------------------------------------------------------------

    /**
     * @treatAsPrivate implementation detail
     */
    private static class StyleableProperties {

        private static final CssMetaData<NMRAxis, Number> TICK_UNIT
                = new CssMetaData<NMRAxis, Number>("-fx-tick-unit", StyleConverter.getSizeConverter(), 5.0) {

            @Override
            public boolean isSettable(NMRAxis n) {
                return n.tickUnit == null || !n.tickUnit.isBound();
            }

            @Override
            public StyleableProperty<Number> getStyleableProperty(NMRAxis n) {
                return (StyleableProperty<Number>) n.tickUnitProperty();
            }
        };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables
                    = new ArrayList<>(ValueAxis.getClassCssMetaData());
            styleables.add(TICK_UNIT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * The value between each major tick mark in data units. This is automatically set if we are auto-ranging.
     */
    private final DoubleProperty tickUnit = new StyleableDoubleProperty(5) {
        @Override
        protected void invalidated() {
            if (!isAutoRanging()) {
                invalidateRange();
                requestAxisLayout();
            }
        }

        @Override
        public CssMetaData<NMRAxis, Number> getCssMetaData() {
            return StyleableProperties.TICK_UNIT;
        }

        @Override
        public Object getBean() {
            return NMRAxis.this;
        }

        @Override
        public String getName() {
            return "tickUnit";
        }
    };

    public final double getTickUnit() {
        return tickUnit.get();
    }

    public final void setTickUnit(double value) {
        tickUnit.set(value);
    }

    public final DoubleProperty tickUnitProperty() {
        return tickUnit;
    }

}
