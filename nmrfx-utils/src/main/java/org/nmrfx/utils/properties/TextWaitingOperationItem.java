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
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;

import java.util.function.Consumer;

/**
 * @author brucejohnson
 */
public class TextWaitingOperationItem extends OperationItem implements ObservableStringValue {
    private static final Background ACTIVE_BACKGROUND = new Background(new BackgroundFill(Color.YELLOW, CornerRadii.EMPTY, Insets.EMPTY));

    ChangeListener<? super String> listener;
    Consumer<OperationItem> f;
    String value;
    String defaultValue;
    Background defaultBackground = null;

    public TextWaitingOperationItem(PropertySheet propertySheet, ChangeListener listener, Consumer<OperationItem> f, String defaultValue, String category, String name, String description) {
        super(propertySheet, category, name, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.listener = listener;
        this.f = f;
    }

    @Override
    public Class<?> getType() {
        return TextWaitingOperationItem.class;
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

    public void keyReleased(TextField textField, KeyEvent event) {
        if (defaultBackground == null) {
            defaultBackground = textField.getBackground();
        }
        if ((event.getCode() == KeyCode.ENTER) || (textField.getText().length() == 0)) {
            textField.setBackground(defaultBackground);
            f.accept(this);
        } else {
            textField.setBackground(ACTIVE_BACKGROUND);
        }
    }
}
