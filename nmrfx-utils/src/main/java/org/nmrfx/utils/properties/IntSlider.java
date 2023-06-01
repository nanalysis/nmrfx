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

import javafx.beans.binding.Bindings;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.util.StringConverter;
import javafx.util.converter.FormatStringConverter;

import java.text.NumberFormat;

/**
 * @author brucejohnson
 */
public class IntSlider extends GridPane {

    TextField textField = new TextField();
    Slider slider;
    int amin;
    int amax;
    NumberFormat nf = NumberFormat.getInstance();
    FormatStringConverter ds = new FormatStringConverter(nf);

    public IntSlider(Slider slider, int amin, int amax) {
        super();
        this.slider = slider;
        this.amin = amin;
        this.amax = amax;
        textField.setFont(new Font(11));
        textField.setPrefWidth(60);
        addControls();
        nf.setMaximumFractionDigits(0);
        Bindings.bindBidirectional(textField.textProperty(), slider.valueProperty(), (StringConverter) ds);
    }

    private void addControls() {
        add(textField, 0, 0);
        add(slider, 1, 0);
        setHgrow(slider, Priority.ALWAYS);
    }

    public Slider getSlider() {
        return slider;
    }
}
