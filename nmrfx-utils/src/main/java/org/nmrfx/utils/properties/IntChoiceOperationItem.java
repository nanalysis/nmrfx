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

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author brucejohnson
 */
public class IntChoiceOperationItem extends OperationItem implements ObservableIntegerValue {

    Integer defaultValue;
    Integer value;
    ChangeListener<? super Number> listener;
    private final Collection<?> choices;

    public IntChoiceOperationItem(PropertySheet propertySheet, ChangeListener listener, Integer defaultValue, Collection<?> choices, String category, String name, String description) {
        super(propertySheet, category, name, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.listener = listener;
        this.choices = new ArrayList(choices);
    }

    @Override
    public Class<?> getType() {
        return IntChoiceOperationItem.class;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public void setValue(Object o) {
        Integer oldValue = value;
        if (o instanceof Integer) {
            Integer newValue = (Integer) o;
            value = newValue;
            if ((!value.equals(oldValue)) && (listener != null)) {
                listener.changed(this, oldValue, value);
            }
        }
    }

    @Override
    public boolean isDefault() {
        if (defaultValue == null) {
            return value == null;
        } else {
            return defaultValue.equals(value);
        }
    }

    @Override
    public void setFromString(String sValue) {
        // fixme  need general method to strip leading and trailing quotes
        if (sValue.charAt(0) == '\'') {
            sValue = sValue.substring(1, sValue.length() - 1);
        }
        if (sValue.charAt(0) == '"') {
            sValue = sValue.substring(1, sValue.length() - 1);
        }
        setValue(Integer.parseInt(sValue));
    }

    @Override
    public void setToDefault() {
        value = defaultValue;
    }

    @Override
    public int get() {
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

    public Collection<?> getChoices() {
        return choices;
    }

    @Override
    public String getStringRep() {
        return String.valueOf(value);
    }

    @Override
    public int intValue() {
        return value;
    }

    @Override
    public long longValue() {
        return value.longValue();
    }

    @Override
    public float floatValue() {
        return value.floatValue();
    }

    @Override
    public double doubleValue() {
        return value.doubleValue();
    }
}
