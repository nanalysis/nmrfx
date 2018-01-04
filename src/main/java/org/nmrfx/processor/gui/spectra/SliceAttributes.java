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
    private ColorProperty slice1Color;
    private ColorProperty slice2Color;
    private BooleanProperty useDatasetColor;
    private BooleanProperty show2ndSlice;

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

    public ColorProperty slice1ColorProperty() {
        if (slice1Color == null) {
            slice1Color = new ColorProperty(this, "color1", Color.BLUE);
        }
        return slice1Color;
    }

    public void setSlice1Color(Color value) {
        slice1ColorProperty().set(value);
    }

    public Color getSlice1Color() {
        return slice1ColorProperty().get();
    }

    public ColorProperty slice2ColorProperty() {
        if (slice2Color == null) {
            slice2Color = new ColorProperty(this, "color2", Color.BLUE);
        }
        return slice2Color;
    }

    public void setSlice2Color(Color value) {
        slice2ColorProperty().set(value);
    }

    public Color getSlice2Color() {
        return slice2ColorProperty().get();
    }

    public BooleanProperty useDatasetColorProperty() {
        if (useDatasetColor == null) {
            useDatasetColor = new SimpleBooleanProperty(this, "usedatasetcolor", true);
        }
        return useDatasetColor;
    }

    public void setUseDatasetColor(boolean value) {
        useDatasetColorProperty().set(value);
    }

    public boolean getUseDatasetColor() {
        return useDatasetColorProperty().get();
    }

    public BooleanProperty show2ndSliceProperty() {
        if (show2ndSlice == null) {
            show2ndSlice = new SimpleBooleanProperty(this, "show2ndslice", false);
        }
        return show2ndSlice;
    }

    public void setShow2ndSlice(boolean value) {
        show2ndSliceProperty().set(value);
    }

    public boolean getShow2ndSlice() {
        return show2ndSliceProperty().get();
    }

}
