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
package org.nmrfx.processor.gui;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import org.controlsfx.property.editor.AbstractPropertyEditor;

/**
 *
 * @author brucejohnson
 */
public class PropertySliderEditor extends AbstractPropertyEditor<Object, Node> {

//    DoubleRangeOperationItem item;
    PropertySliderEditor(DoubleRangeOperationItem item, ZoomSlider slider) {
        super(item, slider);
    }

    @Override
    protected ObservableValue<Object> getObservableValue() {
        ZoomSlider slider = (ZoomSlider) getEditor();
        return (ObservableValue) slider.getSlider().valueProperty();
//        return (ObservableValue) getProperty();
    }

    @Override
    public void setValue(Object t) {
        ZoomSlider slider = (ZoomSlider) getEditor();
        double min = slider.getSlider().getMin();
        double max = slider.getSlider().getMax();
        double value = (Double) t;
        if ((value < min) || (value > max)) {
            double[] newRange = slider.newRange(min, max, value, 1.0);
            min = newRange[0];
            max = newRange[1];
            slider.getSlider().setMin(min);
            slider.getSlider().setMax(max);
            slider.getSlider().setBlockIncrement((max - min) / 100.0);
            slider.getSlider().setMajorTickUnit((max - min) / 2);
        }
        slider.getSlider().setValue(value);

    }

}
