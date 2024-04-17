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
import org.apache.commons.math3.complex.Complex;
import org.controlsfx.control.PropertySheet;

/**
 * @author johnsonb
 */
public class ComplexRangeOperationItem extends ComplexOperationItem {

    public ComplexRangeOperationItem(PropertySheet propertySheet, ChangeListener listener, Complex defaultValue, String category, String name, String description) {
        super(propertySheet, listener, defaultValue, category, name, description);
    }

    public ComplexRangeOperationItem(PropertySheet propertySheet, ChangeListener listener, Complex defaultValue, Complex min, Complex max, String category, String name, String description) {
        super(propertySheet, listener, defaultValue, min, max, category, name, description);
    }

    @Override
    public Class<?> getType() {
        return ComplexRangeOperationItem.class;
    }
}
