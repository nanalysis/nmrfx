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
import javafx.beans.value.ObservableIntegerValue;
import org.controlsfx.control.PropertySheet;

/**
 * @author brucejohnson
 */
public class IntOperationItem extends OperationItem implements ObservableIntegerValue {

    int value;
    int defaultValue;
    private Integer min = null;
    private Integer max = null;
    ChangeListener<? super Number> listener;

    /**
     * @return the min
     */
    public Integer getMin() {
        return min;
    }

    /**
     * @return the max
     */
    public Integer getMax() {
        return max;
    }

    public IntOperationItem(PropertySheet propertySheet, ChangeListener listener, int defaultValue, String category, String name, String description) {
        super(propertySheet, category, name, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.listener = listener;
    }

    public IntOperationItem(PropertySheet propertySheet, ChangeListener listener, int defaultValue, int min, int max, String category, String name, String description) {
        this(propertySheet, listener, defaultValue, category, name, description);

        this.min = min;
        this.max = max;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public Class<?> getType() {
        return IntOperationItem.class;
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
        int oldValue = value;
        if (o instanceof Number) {
            int newValue = ((Number) o).intValue();
            if ((getMin() != null) && (newValue < getMin())) {
                newValue = getMin();
            }
            if ((getMax() != null) && (newValue > getMax())) {
                newValue = getMax();
            }
            value = newValue;
            if ((value != oldValue) && (listener != null)) {
                listener.changed(this, oldValue, value);
            }
        }
    }

    @Override
    public int get() {
        return value;
    }

    @Override
    public int intValue() {
        return value;
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
        return (double) value;
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
        return (value == defaultValue);
    }

    @Override
    public void setFromString(String sValue) {
        value = Integer.parseInt(sValue);
    }

    @Override
    public void setToDefault() {
        value = defaultValue;
    }

}
