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

import javafx.beans.value.ChangeListener;
import org.controlsfx.control.PropertySheet;

/**
 * @author brucejohnson
 */
public class DoubleUnitsRangeOperationItem extends DoubleOperationItem {

    ZoomSlider slider = null;

    public DoubleUnitsRangeOperationItem(PropertySheet propertySheet, ChangeListener listener, double defaultValue, String category, String name, String description) {
        super(propertySheet, listener, defaultValue, category, name, description);

    }

    public DoubleUnitsRangeOperationItem(PropertySheet propertySheet, ChangeListener listener, double defaultValue, double min, double max, String category, String name, String description) {
        super(propertySheet, listener, defaultValue, min, max, category, name, description);
    }

    public DoubleUnitsRangeOperationItem(PropertySheet propertySheet, ChangeListener listener, double defaultValue, double min, double max, double amin, double amax, String category, String name, String description) {
        super(propertySheet, listener, defaultValue, min, max, amin, amax, category, name, description);
    }

    @Override
    public Class<?> getType() {
        return DoubleUnitsRangeOperationItem.class;
    }

    public void setZoomSlider(ZoomSlider slider) {
        this.slider = slider;
        if (Character.isLetter(getLastChar())) {
            slider.setIconLabel(String.valueOf(getLastChar()));
        }

    }

    @Override
    public void setFromString(String sValue) {
        if (sValue.startsWith("'") && sValue.endsWith("'")) {
            sValue = sValue.substring(1, sValue.length() - 1);
        }
        if (sValue.length() > 1) {
            char endChar = sValue.charAt(sValue.length() - 1);
            if (Character.isLetter(endChar)) {
                sValue = sValue.substring(0, sValue.length() - 1);
                lastChar = endChar;
                if (slider != null) {
                    slider.setIconLabel(String.valueOf(endChar));
                }
            } else if (slider != null) {
                slider.setIconLabel("");
            }
        }
        value = Double.parseDouble(sValue);
    }

    @Override
    public String getStringRep() {
        String strValue = String.format(slider.format, value);
        if (slider != null) {
            String unitName = slider.getIconLabel();
            if (!unitName.equals("")) {
                return '\'' + strValue + unitName + '\'';
            }
        }
        if (lastChar != (char) -1) {
            return '\'' + strValue + lastChar + '\'';
        } else {
            return strValue;
        }
    }

}
