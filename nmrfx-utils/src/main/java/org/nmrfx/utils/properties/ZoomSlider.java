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
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import org.controlsfx.control.textfield.CustomTextField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author brucejohnson
 */
public class ZoomSlider extends GridPane {

    CustomTextField textField = new CustomTextField();
    Slider slider;
    Button downButton = null;
    Button upButton = null;
    double amin;
    double amax;
    List<String> choices = new ArrayList<>();
    Consumer labelUpdatedFunction = null;
    Label iconLabel = null;
    String format = "%.3f";

    class DConverter extends DoubleStringConverter {

        @Override
        public String toString(Double value) {
            return String.format(format, value);

        }
    }

    public boolean hasIcon() {
        return textField.getRight() != null;
    }

    public void setIcon(List<String> choices, Consumer labelUpdatedFunction) {
        textField.setPrefWidth(80);
        this.labelUpdatedFunction = labelUpdatedFunction;
        this.choices.addAll(choices);
        StackPane pane = new StackPane();
        Rectangle rect = new Rectangle(14, 14);
        rect.setFill(Color.ORANGE);
        iconLabel = new Label(choices.get(0));
        iconLabel.setFont(Font.font(12));
        pane.getChildren().addAll(rect, iconLabel);
        textField.setRight(pane);
        iconLabel.setMouseTransparent(true);
        rect.setOnMouseClicked(e -> updateLabel());

    }

    public void setIconLabel(String s) {
        if (iconLabel != null) {
            if ((s.length() > 0) && Character.isLetter(s.charAt(0))) {
                iconLabel.setText(s);
            }
        }
    }

    public String getIconLabel() {
        String result = "";
        if (iconLabel != null) {
            result = iconLabel.getText();
        }
        return result;
    }

    void updateLabel() {
        String text = iconLabel.getText();
        int index = choices.indexOf(text);
        index += 1;
        if (index >= choices.size()) {
            index = 0;
        }
        text = choices.get(index);
        iconLabel.setText(text);
        slider.setValue(0.0);
        if (labelUpdatedFunction != null) {
            labelUpdatedFunction.accept(this);
        }

    }

    public void updateFormat() {
        double incr = slider.getBlockIncrement();
        if (incr >= 5) {
            format = "%.0f";
        } else if (incr >= 1) {
            format = "%.1f";
        } else {
            int nDigits = (int) -Math.floor(Math.log10(incr));
            nDigits++;
            format = "%." + nDigits + "f";
        }
    }

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
        min = Math.max(min, amin);
        max = Math.min(max, amax);

        double[] newRange = {min, max};
        return newRange;
    }

    public void setRange(double min, double max, double value) {
        slider.setMin(min);
        slider.setMax(max);
        slider.setBlockIncrement((max - min) / 100.0);
        updateFormat();
        slider.setMajorTickUnit((max - min) / 2);
        slider.setValue(value);
    }

    private void downAction(ActionEvent event) {
        double min = slider.getMin();
        double max = slider.getMax();
        double value = slider.getValue();
        double[] newRange = newRange(min, max, value, 0.5);
        min = newRange[0];
        max = newRange[1];
        slider.setMin(min);
        slider.setMax(max);
        slider.setBlockIncrement((max - min) / 100.0);
        updateFormat();

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
        updateFormat();

        slider.setMajorTickUnit((max - min) / 2);
    }

    public ZoomSlider(Slider slider, double amin, double amax) {
        this(slider, amin, amax, true);

    }

    public ZoomSlider(Slider slider, double amin, double amax, boolean zoomable) {
        super();
        this.slider = slider;
        this.amin = amin;
        this.amax = amax;
        if (zoomable) {
            downButton = new Button("-");
            upButton = new Button("+");
            upButton.getStyleClass().add("toolButton");
            downButton.getStyleClass().add("toolButton");
        }

        textField.setFont(new Font(11));
        textField.setPrefWidth(60);
        addControls(zoomable);
        if (zoomable) {
            downButton.addEventHandler(ActionEvent.ACTION, event -> downAction(event));
            upButton.addEventHandler(ActionEvent.ACTION, event -> upAction(event));
        }
        Bindings.bindBidirectional(textField.textProperty(), slider.valueProperty(), (StringConverter) new DConverter());
        updateFormat();
    }

    private void addControls(boolean zoomable) {
        add(textField, 0, 0);
        add(slider, 1, 0);
        setHgrow(slider, Priority.ALWAYS);
        if (zoomable) {
            add(downButton, 2, 0);
            add(upButton, 3, 0);
        }
    }

    public Slider getSlider() {
        return slider;
    }
}
