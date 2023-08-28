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
import javafx.beans.value.ObservableBooleanValue;
import org.controlsfx.control.PropertySheet;

/**
 * @author brucejohnson
 */
//TODO add annotations once core and utils are merged
// @PluginAPI("ring")
public class BooleanOperationItem extends OperationItem implements ObservableBooleanValue {

    boolean value;
    boolean defaultValue;
    ChangeListener<Boolean> listener;

    public BooleanOperationItem(PropertySheet propertySheet, ChangeListener listener, boolean defaultValue, String category, String name, String description) {
        super(propertySheet, category, name, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.listener = listener;
    }

    @Override
    public Class<?> getType() {
        return BooleanOperationItem.class;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public void setValue(Object o) {
        boolean oldValue = value;
        if (o instanceof Boolean) {
            boolean newValue = (Boolean) o;

            value = newValue;
            if ((value != oldValue) && (listener != null)) {
                listener.changed(this, oldValue, value);
            }
        }
    }

    @Override
    public boolean isDefault() {
        return value == defaultValue;
    }

    @Override
    public void setFromString(String sValue) {
        value = Boolean.parseBoolean(sValue);
    }

    @Override
    public void setToDefault() {
        value = defaultValue;
    }

    @Override
    public boolean get() {
        return value;
    }

    @Override
    public void addListener(ChangeListener<? super Boolean> listener) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeListener(ChangeListener<? super Boolean> listener) {
    }

    @Override
    public void addListener(InvalidationListener listener) {
    }

    @Override
    public void removeListener(InvalidationListener listener) {
    }

    @Override
    public String getStringRep() {
        if (value) {
            return "True";
        } else {
            return "False";
        }
    }
}
