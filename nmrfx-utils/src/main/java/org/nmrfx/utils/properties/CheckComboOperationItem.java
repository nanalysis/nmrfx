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
import javafx.beans.value.ObservableStringValue;
import javafx.collections.ListChangeListener;
import org.controlsfx.control.PropertySheet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author brucejohnson
 */
public class CheckComboOperationItem extends OperationItem implements ObservableStringValue {

    String defaultValue;
    String value;
    ListChangeListener listener;
    private final Collection<?> choices;
    private List<String> values = new ArrayList<>();

    public CheckComboOperationItem(PropertySheet propertySheet, ListChangeListener listener, String defaultValue, Collection<?> choices, String category, String name, String description) {
        super(propertySheet, category, name, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        values.add(defaultValue);
        this.listener = listener;
        this.choices = new ArrayList(choices);
    }

    @Override
    public Class<?> getType() {
        return CheckComboOperationItem.class;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(Object o) {
        List<String> valueList = (List<String>) o;
        values.clear();
        values.addAll(valueList);

        String oldValue = value;
        if (o instanceof String) {
        }
    }

    public List<String> getValues() {
        return values;
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
        setValue(sValue);
    }

    @Override
    public void setToDefault() {
        value = defaultValue;
    }

    @Override
    public String get() {
        return value;
    }

    @Override
    public void removeListener(ChangeListener<? super String> listener) {
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
        return '\'' + value + '\'';
    }

    @Override
    public void addListener(ChangeListener<? super String> cl) {
    }
}
