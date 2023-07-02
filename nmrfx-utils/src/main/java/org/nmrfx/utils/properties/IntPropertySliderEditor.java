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
import javafx.scene.Node;
import org.controlsfx.property.editor.AbstractPropertyEditor;

/**
 * @author brucejohnson
 */
public class IntPropertySliderEditor extends AbstractPropertyEditor<Object, Node> {

    public IntPropertySliderEditor(IntRangeOperationItem item, IntSlider slider) {
        super(item, slider);
    }

    @Override
    protected ObservableValue<Object> getObservableValue() {
        IntSlider slider = (IntSlider) getEditor();
        return (ObservableValue) slider.getSlider().valueProperty();
    }

    @Override
    public void setValue(Object t) {
        IntSlider slider = (IntSlider) getEditor();
        double dValue = ((Number) t).doubleValue();
        slider.getSlider().setValue(dValue);
    }
}
