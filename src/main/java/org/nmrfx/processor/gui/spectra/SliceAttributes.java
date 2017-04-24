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
package org.nmrfx.processor.gui.spectra;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.paint.Color;

/**
 *
 * @author brucejohnson
 */
public class SliceAttributes {

    public BooleanProperty offsetTracking;
    public DoubleProperty offsetXValue;
    public DoubleProperty offsetYValue;
    public DoubleProperty scaleValue;
    private ColorProperty sliceColor;

    public BooleanProperty offsetTrackingProperty() {
        if (offsetTracking == null) {
            offsetTracking = new SimpleBooleanProperty(this, "offsetTracking", false);
        }
        return offsetTracking;
    }

    public void setOffsetTracking(boolean value) {
        offsetTrackingProperty().set(value);
    }

    public boolean getOffsetTracking() {
        return offsetTrackingProperty().get();
    }

    public DoubleProperty offsetXValueProperty() {
        if (offsetXValue == null) {
            offsetXValue = new SimpleDoubleProperty(this, "offsetXValue", 0.5);
        }
        return offsetXValue;
    }

    public void setOffsetXValue(double value) {
        offsetXValueProperty().set(value);
    }

    public double getOffsetXValue() {
        return offsetXValueProperty().get();
    }

    public DoubleProperty offsetYValueProperty() {
        if (offsetYValue == null) {
            offsetYValue = new SimpleDoubleProperty(this, "offsetYValue", 0.5);
        }
        return offsetYValue;
    }

    public void setOffsetYValue(double value) {
        offsetYValueProperty().set(value);
    }

    public double getOffsetYValue() {
        return offsetYValueProperty().get();
    }

    public DoubleProperty scaleValueProperty() {
        if (scaleValue == null) {
            scaleValue = new SimpleDoubleProperty(this, "scaleValue", 10.0);
        }
        return scaleValue;
    }

    public void setScaleValue(double value) {
        scaleValueProperty().set(value);
    }

    public double getScaleValue() {
        return scaleValueProperty().get();
    }

    public ColorProperty sliceColorProperty() {
        if (sliceColor == null) {
            sliceColor = new ColorProperty(this, "color", Color.BLUE);
        }
        return sliceColor;
    }

    public void setSliceColor(Color value) {
        sliceColorProperty().set(value);
    }

    public Color getSliceColor() {
        return sliceColorProperty().get();
    }

}
