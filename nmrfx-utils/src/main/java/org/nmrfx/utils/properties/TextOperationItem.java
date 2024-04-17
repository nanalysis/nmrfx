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
import org.controlsfx.control.PropertySheet;
import org.controlsfx.property.editor.PropertyEditor;

/**
 * @author brucejohnson
 */
public class TextOperationItem extends OperationItem implements ObservableStringValue {

    ChangeListener<? super String> listener;
    String value;
    String defaultValue;
    PropertyEditor editor;

    public TextOperationItem(PropertySheet propertySheet, ChangeListener listener, String defaultValue, String category, String name, String description) {
        super(propertySheet, category, name, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.listener = listener;
    }

    public void setEditor(PropertyEditor editor) {
        this.editor = editor;
    }

    public void updateEditor() {
        if (editor != null) {
            editor.setValue(value);
        }
    }

    @Override
    public Class<?> getType() {
        return TextOperationItem.class;
    }

    @Override
    public void setValue(Object o) {
        String oldValue = value;
        value = o.toString();
        if ((!value.equals(oldValue)) && (listener != null)) {
            listener.changed(this, oldValue, value);
        }
    }

    @Override
    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    @Override
    public void setFromString(String sValue) {
        // fixme  need general method to strip leading and trailing quotes
        if (sValue.length() > 0) {
            if (sValue.charAt(0) == '\'') {
                sValue = sValue.substring(1, sValue.length() - 1);
            }
            if (sValue.charAt(0) == '"') {
                sValue = sValue.substring(1, sValue.length() - 1);
            }
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
    public void addListener(ChangeListener<? super String> listener) {
        this.listener = listener;
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

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getStringRep() {
        return '\'' + value + '\'';
    }

}
