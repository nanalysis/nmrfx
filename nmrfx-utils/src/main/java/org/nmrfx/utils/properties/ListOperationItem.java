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
import javafx.beans.value.ObservableObjectValue;
import org.controlsfx.control.PropertySheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author johnsonb
 */
public class ListOperationItem extends OperationItem implements ObservableObjectValue<String> {

    List<?> value;
    List<?> defaultValue;
    ChangeListener<? super String> listener;
    /**
     * This enables us to see the type of Unit that we interpret the List as.
     */
    ChoiceOperationItem typeSelector;

    /**
     * @param listener
     * @param defaultValue optional default value for the List.
     * @param category
     * @param name
     * @param description
     * @param typeSelector
     */
    public ListOperationItem(PropertySheet propertySheet, ChangeListener<? super String> listener, List<?> defaultValue, String category, String name, String description, ChoiceOperationItem typeSelector) {
        super(propertySheet, category, name, description);
        this.defaultValue = Objects.requireNonNullElseGet(defaultValue, ArrayList::new);
        this.value = this.defaultValue;
        this.listener = listener;
        this.typeSelector = typeSelector;
    }

    /**
     * Returns values as a string created by the java List implementation of toString()
     */
    @Override
    public String getValue() {
        return value.toString();
    }

    @Override
    public Class<?> getType() {
        return ListOperationItem.class;
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

    /**
     * Value is set by giving a String of comma separated values or an ArrayList object.
     * Any other object types will not change value. The listener is only updated if the
     * new values from o are different from the old values.
     *
     * @param o
     */
    @Override
    public void setValue(Object o) {
        List<?> newValue;
        if (o instanceof String) {
            ArrayList<Double> numberValues = new ArrayList<>();
            String lst = (String) o;

            lst = lst.replace("[", "").replace("]", "");
            lst = lst.trim();

            if (lst.length() != 0) {
                for (String sValue : lst.split(",")) {
                    switch (sValue) {
                        case "":
                            numberValues.add(0.0);
                            break;
                        case "-":
                            numberValues.add(-0.0);
                            break;
                        default:
                            try {
                                numberValues.add(Double.parseDouble(sValue));
                            } catch (NumberFormatException nfE) {
                                numberValues.add(0.0);
                            }
                            break;
                    }
                }
            }
            newValue = numberValues;
        } else if (o instanceof ArrayList) {
            newValue = new ArrayList<>((ArrayList<?>) o);
        } else {
            return;
        }
        List<?> oldValue = new ArrayList<>(value);
        value = newValue;
        if (!value.equals(oldValue) && (listener != null)) {
            listener.changed(this, listToString(oldValue), listToString(value));
        }
    }

    /**
     * Returns the string representation of all Number values in the format ".,.,." without any extra white
     * space, if values contain non Numbers, those elements are not returned.
     * Note this method differs from getValue which will return the java List implementation of toString()
     */
    @Override
    public String get() {
        return listToString(value);
    }

    private String listToString(List<?> list) {

        StringBuilder str = new StringBuilder("");
        list.forEach(o -> {
            if (o instanceof Number) {
                str.append(o).append(",");
            } else {
                System.out.println("non Number in List");
            }
        });
        //remove trailing comma
        return str.length() == 0 ? "" : str.toString().substring(0, str.toString().length() - 1);
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
    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    @Override
    public void setFromString(String sValue) {
        this.setValue(sValue);
    }

    @Override
    public void setToDefault() {
        value = defaultValue;
    }

    /**
     * Returns the string representation of all Number values in the format "[.,.,.]" without any extra white
     * space, if values contain non Numbers, those elements are not returned.
     * Example: if values = [1.0, 2.0, 3.0] -> "[1.0,2.0,3.0]"
     * if values = ["one", "two", "three"] -> "[]"
     *
     * @return The string representation of values
     */
    @Override
    public String getStringRep() {
        return "[" + listToString(value) + "]";
    }

}
