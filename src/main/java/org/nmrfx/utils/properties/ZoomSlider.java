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
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import static javafx.scene.layout.GridPane.setHgrow;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

/**
 *
 * @author brucejohnson
 */
public class ZoomSlider extends GridPane {

    TextField textField = new TextField();
    Slider slider;
    Button downButton = new Button("-");
    Button upButton = new Button("+");
    double amin;
    double amax;

    double[] newRange(double min, double max, double value, double divider) {
        double nSeg = 2.0 / divider;

        double newDeltaHalf = (max - min) / nSeg;
        double mul = Math.floor(Math.log10(newDeltaHalf));
        mul = Math.pow(10.0, mul);

        newDeltaHalf = Math.round(newDeltaHalf / mul) * mul;

        int n = (int) (Math.floor(value / (newDeltaHalf / 4)));
        min = n * (newDeltaHalf / 4) - newDeltaHalf;
        max = min + 2 * newDeltaHalf;

        if (min < amin) {
            min = amin;
            max = min + 2 * newDeltaHalf;
        }
        if (max > amax) {
            max = amax;
            min = max - 2 * newDeltaHalf;
        }
        if (min < amin) {
            min = amin;
        }
        if (max > amax) {
            max = amax;
        }
        double[] newRange = {min, max};
        return newRange;
    }

    private void downAction(ActionEvent event) {
        Button button = (Button) event.getSource();
        double min = slider.getMin();
        double max = slider.getMax();
        double value = slider.getValue();
        double[] newRange = newRange(min, max, value, 0.5);
        min = newRange[0];
        max = newRange[1];
        slider.setMin(min);
        slider.setMax(max);
        slider.setBlockIncrement((max - min) / 100.0);
        slider.setMajorTickUnit((max - min) / 2);
    }

    private void upAction(ActionEvent event) {
        Button button = (Button) event.getSource();
        double min = slider.getMin();
        double max = slider.getMax();
        double value = slider.getValue();
        double[] newRange = newRange(min, max, value, 2.0);
        min = newRange[0];
        max = newRange[1];
        slider.setMin(min);
        slider.setMax(max);
        slider.setBlockIncrement((max - min) / 100.0);
        slider.setMajorTickUnit((max - min) / 2);
    }

    public ZoomSlider(Slider slider, double amin, double amax) {
        super();
        this.slider = slider;
        this.amin = amin;
        this.amax = amax;
        upButton.setFont(new Font(9));
        // upButton.setBorder(Border.EMPTY);
        downButton.setFont(new Font(9));
        //downButton.setBorder(Border.EMPTY);
        textField.setFont(new Font(11));
        textField.setPrefWidth(60);
        addControls();
        downButton.addEventHandler(ActionEvent.ACTION, event -> downAction(event));
        upButton.addEventHandler(ActionEvent.ACTION, event -> upAction(event));
        Bindings.bindBidirectional(textField.textProperty(), slider.valueProperty(), (StringConverter) new DoubleStringConverter());
    }

    private void addControls() {
        add(textField, 0, 0);
        add(slider, 1, 0);
        setHgrow(slider, Priority.ALWAYS);
        add(downButton, 2, 0);
        add(upButton, 3, 0);
    }

    public Slider getSlider() {
        return slider;
    }
}
