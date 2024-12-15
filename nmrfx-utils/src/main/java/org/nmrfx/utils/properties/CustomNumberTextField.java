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

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import org.controlsfx.control.textfield.CustomTextField;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Optional;
import java.util.function.DoubleFunction;

public class CustomNumberTextField extends CustomTextField {

    private final NumberFormat nf;
    double min = Double.NEGATIVE_INFINITY;
    double max = Double.MAX_VALUE;
    private final SimpleDoubleProperty number = new SimpleDoubleProperty();
    Optional<DoubleFunction> callbackFunction = Optional.empty();

    public final Double getNumber() {
        return number.get();
    }

    public final void setNumber(Double value) {
        number.set(value);
    }

    public SimpleDoubleProperty numberProperty() {
        return number;
    }

    public CustomNumberTextField() {
        this(Double.valueOf(0.0));
    }

    public CustomNumberTextField(Double value) {
        this(value, NumberFormat.getInstance());
        initHandlers();
    }

    public CustomNumberTextField(Double value, NumberFormat nf) {
        super();
        this.nf = nf;
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
            setText(nf.format(newValue));
        });
    }

    public void resetMinMax() {
        this.min = Double.NEGATIVE_INFINITY;
        this.max = Double.MAX_VALUE;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public void setMax(double max) {
        this.max = max;
    }

    private void updateFromText() {
        try {
            String input = getText();
            if (input == null || input.length() == 0) {
                return;
            }
            double newValue = nf.parse(input).doubleValue();
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
                setText(nf.format(newValue));
            }
            setNumber(newValue);
            callbackFunction.ifPresent(cb -> cb.apply(number.getValue()));
            selectAll();
        } catch (ParseException ex) {
            setText(nf.format(number.get()));
        }
    }

    public void setFunction(DoubleFunction function) {
        callbackFunction = Optional.of(function);
    }
}
