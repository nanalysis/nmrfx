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

import javafx.beans.value.ObservableValue;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;

import java.util.Optional;

/**
 * @author brucejohnson
 */
public abstract class OperationItem implements Item {
    final PropertySheet propertySheet;
    final String category;
    final String description;
    final String name;

    public OperationItem(PropertySheet propertySheet, String category, String name, String description) {
        this.propertySheet = propertySheet;
        this.category = category;
        this.description = description;
        this.name = name;
    }

    public PropertySheet getPropertySheet() {
        return propertySheet;
    }

    @Override
    public abstract Class<?> getType();

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
    public abstract Object getValue();

    @Override
    public abstract void setValue(Object o);

    public abstract boolean isDefault();

    public abstract void setFromString(String sValue);

    public abstract void setToDefault();

    public String getStringRep() {
        return getValue().toString();
    }

    @Override
    public Optional<ObservableValue<? extends Object>> getObservableValue() {
        return Optional.empty();
    }

}
