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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableStringValue;
import org.controlsfx.control.PropertySheet;

/**
 * @author Bruce Johnson
 */
//TODO add annotations once core and utils are merged
// @PluginAPI("ring")
public class DirectoryOperationItem extends TextOperationItem implements ObservableStringValue {

    public DirectoryOperationItem(PropertySheet propertySheet, ChangeListener listener, String defaultValue, String category, String name, String description) {
        super(propertySheet, listener, defaultValue, category, name, description);
    }

    @Override
    public Class<?> getType() {
        return DirectoryOperationItem.class;
    }

}
