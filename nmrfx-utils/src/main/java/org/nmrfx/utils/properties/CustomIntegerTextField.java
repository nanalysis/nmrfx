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
package org.nmrfx.utils.properties;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import org.controlsfx.control.textfield.CustomTextField;

import java.util.function.IntFunction;

public class CustomIntegerTextField extends CustomTextField {

    int min = Integer.MIN_VALUE;
    int max = Integer.MAX_VALUE;
    private final SimpleIntegerProperty number = new SimpleIntegerProperty();
    IntFunction callbackFunction = null;

    public final Integer getNumber() {
        return number.get();
    }

    public final void setNumber(Integer value) {
        number.set(value);
    }

    public SimpleIntegerProperty numberProperty() {
        return number;
    }

    public CustomIntegerTextField() {
        this(Integer.valueOf(0));
    }

    public CustomIntegerTextField(Integer value) {
        super();
        initHandlers();
        setNumber(value);
    }

    private void initHandlers() {

        setOnAction((ActionEvent arg0) -> {
            updateFromText();
        });

        focusedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            if (!newValue) {
                updateFromText();
            }
        });

        numberProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            setText(String.valueOf(newValue));
        });
    }

    public void setMin(int min) {
        this.min = min;
    }

    public void setMax(int max) {
        this.max = max;
    }

    private void updateFromText() {
        try {
            String input = getText();
            if (input == null || input.length() == 0) {
                return;
            }
            int newValue = Integer.parseInt(input);
            boolean changed = false;
            if (newValue < min) {
                newValue = min;
                changed = true;
            }
            if (newValue > max) {
                newValue = max;
                changed = true;
            }
            if (changed) {
                setText(String.valueOf(newValue));
            }
            setNumber(newValue);
            if (callbackFunction != null) {
                callbackFunction.apply(number.getValue());
            }
            selectAll();
        } catch (NumberFormatException ex) {
            setText(String.valueOf(number.get()));
        }
    }

    public void setFunction(IntFunction function) {
        this.callbackFunction = function;
    }
}
