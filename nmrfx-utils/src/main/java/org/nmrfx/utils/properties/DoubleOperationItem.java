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
package org.nmrfx.utils.properties;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableDoubleValue;
import org.controlsfx.control.PropertySheet;

/**
 * @author brucejohnson
 */
public class DoubleOperationItem extends OperationItem implements ObservableDoubleValue {

    double value;
    double defaultValue;
    private Double min = null;
    private Double max = null;
    private Double amin = null;
    private Double amax = null;
    ChangeListener<? super Number> listener;
    char lastChar = (char) -1;

    /**
     * @return the max
     */
    public Double getMax() {
        return max;
    }

    /**
     * @return the min
     */
    public Double getMin() {
        return min;
    }

    /**
     * @return the amin
     */
    public Double getAmin() {
        return amin;
    }

    /**
     * @return the amax
     */
    public Double getAmax() {
        return amax;
    }

    public DoubleOperationItem(PropertySheet propertySheet, ChangeListener listener, double defaultValue, String category, String name, String description) {
        super(propertySheet, category, name, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.listener = listener;
    }

    public DoubleOperationItem(PropertySheet propertySheet, ChangeListener listener, double defaultValue, double min, double max, String category, String name, String description) {
        this(propertySheet, listener, defaultValue, category, name, description);
        this.min = min;
        this.max = max;
        this.amin = min;
        this.amax = max;
    }

    public DoubleOperationItem(PropertySheet propertySheet, ChangeListener listener, double defaultValue, double min, double max, double amin, double amax, String category, String name, String description) {
        this(propertySheet, listener, defaultValue, category, name, description);
        this.min = min;
        this.max = max;
        this.amin = amin;
        this.amax = amax;
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public Class<?> getType() {
        return DoubleOperationItem.class;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setValue(Object o) {
        double oldValue = value;
        if (o instanceof Double) {
            double newValue = (Double) o;
            // fixme  should allow the rounding to be changed
            newValue = Math.round(newValue * 1000.0) / 1000.0;
            if ((getAmin() != null) && (newValue < getAmin())) {
                newValue = getAmin();
            }
            if ((getAmax() != null) && (newValue > getAmax())) {
                newValue = getAmax();
            }
            value = newValue;
            if ((value != oldValue) && (listener != null)) {
                listener.changed(this, oldValue, value);
            }
        }
    }

    @Override
    public double get() {
        return value;
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return (long) value;
    }

    @Override
    public float floatValue() {
        return (float) value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public void addListener(ChangeListener<? super Number> listener) {
        this.listener = listener;
    }

    @Override
    public void removeListener(ChangeListener<? super Number> listener) {
    }

    @Override
    public void addListener(InvalidationListener listener) {
    }

    @Override
    public void removeListener(InvalidationListener listener) {
    }

    @Override
    public boolean isDefault() {
        return (Math.abs(value - defaultValue) < 1.0e-9);
    }

    @Override
    public void setFromString(String sValue) {
        if (sValue.startsWith("'") && sValue.endsWith("'")) {
            sValue = sValue.substring(1, sValue.length() - 1);
        }
        if (sValue.length() > 1) {
            char endChar = sValue.charAt(sValue.length() - 1);
            if (Character.isLetter(endChar)) {
                sValue = sValue.substring(0, sValue.length() - 1);
                lastChar = endChar;
            }
        }
        value = Double.parseDouble(sValue);
    }

    @Override
    public void setToDefault() {
        value = defaultValue;
    }

    public void setLastChar(char lastChar) {
        this.lastChar = lastChar;
    }

    public char getLastChar() {
        return lastChar;
    }

    @Override
    public String getStringRep() {
        if (lastChar != (char) -1) {
            return '\'' + Double.toString(value) + lastChar + '\'';
        } else {
            return Double.toString(value);
        }
    }

}
