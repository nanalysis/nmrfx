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
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexFormat;
import org.apache.commons.math3.exception.MathParseException;
import org.controlsfx.control.PropertySheet;
import org.python.core.PyComplex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 */
public class ComplexOperationItem extends OperationItem implements ObservableObjectValue<String> {

    private static final Logger log = LoggerFactory.getLogger(ComplexOperationItem.class);
    Complex value;
    Complex defaultValue;
    Complex min = null;
    Complex max = null;
    ChangeListener<? super String> listener;
    private final ComplexFormat cf;

    public ComplexOperationItem(PropertySheet propertySheet, ChangeListener listener, Complex defaultValue, String category, String name, String description) {
        super(propertySheet, category, name, description);
        if (defaultValue != null) {
            this.defaultValue = defaultValue;
        } else {
            this.defaultValue = Complex.ZERO;
        }
        this.value = this.defaultValue;
        this.listener = listener;
        this.cf = new ComplexFormat("j");
    }

    public ComplexOperationItem(PropertySheet propertySheet, ChangeListener listener, Complex defaultValue, Complex min, Complex max, String category, String name, String description) {
        this(propertySheet, listener, defaultValue, category, name, description);

        this.min = min;
        this.max = max;
    }

    @Override
    public String getValue() {
        return value.getReal() + " + " + value.getImaginary() + "j";
    }

    @Override
    public Class<?> getType() {
        return ComplexOperationItem.class;
    }

    @Override
    public void setValue(Object o) {
        Complex oldValue = value;
        if (o instanceof String) {
            try {
                Complex newValue = cf.parse((String) o);
                if (newValue == null) {
                    return;
                }
                setFromString((String) o);
                value = newValue;
                if ((value != oldValue) && (listener != null)) {
                    listener.changed(this, cf.format(oldValue), cf.format(value));
                }
            } catch (MathParseException mpe) {
                log.warn("Unable to parse new value", mpe);
            }

        } else if (o instanceof Complex) {
            Complex newValue = (Complex) o;
            value = newValue;
            if ((value != oldValue) && (listener != null)) {
                listener.changed(this, cf.format(oldValue), cf.format(value));
            }
        }
    }

    private String complexToString(Complex value) {
        if (value != null) {
            return cf.format(value);
        } else {
            return complexToString(defaultValue);
        }
    }

    @Override
    public String get() {
        return complexToString(value);
    }

    @Override
    public void addListener(ChangeListener<? super String> listener) {
        System.out.println("add Listener " + getName());
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
        return Complex.equals(value, defaultValue);
    }

    @Override
    public void setFromString(String sValue) {
        ComplexFormat cfTemp = new ComplexFormat();
        Complex oldValue = value;

        Complex newValue = cfTemp.parse(sValue);
        if (newValue == null) {
            return;
        }

        value = newValue;

        listener.changed(this, complexToString(oldValue),
                complexToString(value));

    }

    public void setFromPyComplex(PyComplex pc) {
        Complex oldValue = value;
        value = new Complex(pc.real, pc.imag);
        listener.changed(this, complexToString(oldValue),
                complexToString(value));
    }

    public void setFromDouble(Double d) {
        Complex oldValue = value;
        value = new Complex(d);
        listener.changed(this, complexToString(oldValue), complexToString(value));

    }

    @Override
    public void setToDefault() {
        Complex old = value;
        if (value == null) {
            value = defaultValue;
        }
    }

    @Override
    public String getStringRep() {
        if (value == null) {
            return "0 + 0j";
        } else {
            return value.getReal() + " + " + value.getImaginary() + "j";
        }
    }
}
