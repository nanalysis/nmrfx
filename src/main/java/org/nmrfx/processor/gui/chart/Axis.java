/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.chart;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Orientation;
import static javafx.geometry.Orientation.VERTICAL;
import static javafx.geometry.Orientation.HORIZONTAL;
import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.nmrfx.processor.gui.graphicsio.GraphicsContextInterface;
import org.nmrfx.processor.gui.graphicsio.GraphicsIOException;

/**
 *
 * @author brucejohnson
 */
public class Axis {

    Orientation orientation;
    double ticSize = 10.0;
    private double width;
    private DoubleProperty widthProp;
    private double height;
    private DoubleProperty heightProp;
    private boolean reverse;
    private BooleanProperty reverseProp;
    private String label = "";
    private String ticFormatString = "%.1f";
    private double minorTickSpace;
    private double majorTickSpace;
    private double minorTickStart;
    private double majorTickStart;
    private double xOrigin = 100.0;
    private double yOrigin = 800.0;
    private int labelFontSize = 16;
    private int ticFontSize = 12;
    private double lineWidth = 1.0;
    private Color color = Color.BLACK;
    private Font ticFont = new Font(ticFontSize);
    private Font labelFont = new Font(labelFontSize);
    public static double targetPix = 120;
    private double defaultUpper;
    private double defaultLower;
    private boolean tickMarksVisible = true;
    private boolean tickLabelsVisible = true;
    private boolean labelVisible = true;

    public Axis(Orientation orientation, double lowerBound, double upperBound, double width, double height) {
        this.orientation = orientation;
        this.defaultLower = lowerBound;
        this.defaultUpper = upperBound;
        this.width = width;
        this.height = height;
    }
    private DoubleProperty lowerBound;

    public DoubleProperty lowerBoundProperty() {
        if (lowerBound == null) {
            lowerBound = new SimpleDoubleProperty(this, "lower", defaultLower);
        }
        return lowerBound;
    }

    public void setLowerBound(double value) {
        lowerBoundProperty().set(value);
    }

    public double getLowerBound() {
        return lowerBoundProperty().get();
    }
    private DoubleProperty upperBound;

    public DoubleProperty upperBoundProperty() {
        if (upperBound == null) {
            upperBound = new SimpleDoubleProperty(this, "upper", defaultUpper);
        }
        return upperBound;
    }

    public void setUpperBound(double value) {
        upperBoundProperty().set(value);
    }

    public double getUpperBound() {
        return upperBoundProperty().get();
    }

    public Number getValueForDisplay(double displayPosition) {
        double scaleValue = calcScale();
        double length = orientation == VERTICAL ? height : width;
        double offset = orientation == VERTICAL ? yOrigin - height : xOrigin;
        displayPosition -= offset;
        if (isReversed() && (getOrientation() == Orientation.HORIZONTAL)) {
            displayPosition = length - displayPosition;
        } else if (!isReversed() && (getOrientation() == Orientation.VERTICAL)) {
            displayPosition = length - displayPosition;
        }
        return ((displayPosition) / scaleValue) + getLowerBound();

    }

    double calcScale() {
        double scaleValue;
        double range = getRange();
        if (getOrientation() == Orientation.VERTICAL) {
            scaleValue = getHeight() / range;
        } else {
            scaleValue = getWidth() / range;
        }
        return scaleValue;
    }

    public double getDisplayPosition(Number value) {
        double f = (value.doubleValue() - getLowerBound()) / (getUpperBound() - getLowerBound());
        double displayPosition;
        if (orientation == HORIZONTAL) {
            if (reverse) {
                displayPosition = xOrigin + width - f * width;
            } else {
                displayPosition = xOrigin + f * width;
            }
        } else {
            if (!reverse) {
                displayPosition = yOrigin - f * height;
            } else {
                displayPosition = yOrigin - height + f * height;
            }
        }
        return displayPosition;
    }

    public void setOrigin(double x, double y) {
        xOrigin = x;
        yOrigin = y;
    }

    public double getXOrigin() {
        return xOrigin;
    }

    public double getYOrigin() {
        return yOrigin;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setHeight(double value) {
        height = value;
    }

    public void setWidth(double value) {
        width = value;
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    public void setReverse(boolean state) {
        reverse = state;
    }

    public boolean isReversed() {
        return reverse;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public double getRange() {
        return getUpperBound() - getLowerBound();
    }

    public void setMinMax(double min, double max) {
        /*
                lowerBoundProperty().setValue(min);
        upperBoundProperty().setValue(max);

         */
        lowerBoundProperty().set(min);
        upperBoundProperty().set(max);
    }

    public double getScale() {
        return calcScale();
    }

    public void setTickFontSize(int size) {
        ticFontSize = size;
        ticFont = new Font(ticFontSize);
    }

    public void setLabelFontSize(int size) {
        labelFontSize = size;
        labelFont = new Font(labelFontSize);
    }

    private void getTickPositions() {
        double length = VERTICAL == getOrientation() ? getHeight() : getWidth();
        if (length > 0) {
            double nTic1 = length / targetPix;
            double range = getRange();
            double scale = range / nTic1;
            double logScale = Math.log10(scale);
            double floorScale = Math.floor(logScale);
            int nDecimals = (int) Math.round(-logScale) + 1;
            if (nDecimals < 0) {
                nDecimals = 0;
            } else if (nDecimals > 10) {
                nDecimals = 10;
            }
            ticFormatString = new StringBuilder("%.").append(Integer.toString(nDecimals)).append("f").toString();
            double normalizedScale = scale / Math.pow(10.0, floorScale);
            double minDelta = Double.MAX_VALUE;
            double[] targetValues = {1.0, 2.0, 5.0, 10.0};
            double selValue = 1.0;
            for (double targetValue : targetValues) {
                double delta = Math.abs(normalizedScale - targetValue);
                if (delta < minDelta) {
                    minDelta = delta;
                    selValue = targetValue;
                }
            }
            double incValue = selValue * Math.pow(10.0, floorScale);
            majorTickSpace = incValue;
            minorTickSpace = incValue / 5.0;
            minorTickStart = Math.ceil(getLowerBound() / minorTickSpace) * minorTickSpace;
            majorTickStart = Math.ceil(getLowerBound() / majorTickSpace) * majorTickSpace;
        }
    }

    public void draw(GraphicsContextInterface gC) throws GraphicsIOException {
        gC.setTextAlign(TextAlignment.CENTER);
        gC.setTextBaseline(VPos.TOP);
        gC.setFill(color);
        gC.setStroke(color);
        gC.setFont(ticFont);
        gC.setLineWidth(lineWidth);
        getTickPositions();
        if (orientation == VERTICAL) {
            drawVerticalAxis(gC);
        } else {
            drawHorizontalAxis(gC);
        }
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public double getBorderSize() {
        if (orientation == HORIZONTAL) {
            int gap1 = ticFontSize / 4;
            int gap2 = labelFontSize / 4;
            ticSize = ticFontSize * 0.75;
            double border = 0.0;
            if (tickMarksVisible) {
                border += ticSize + gap1;
                if (tickLabelsVisible) {
                    border += ticFontSize + gap1;
                }
            }
            if (labelVisible) {
                border += labelFontSize + gap2;
            }
            return border;

        } else {
            int gap1 = ticFontSize / 4;
            int gap2 = labelFontSize / 4;
            ticSize = ticFontSize * 0.75;
            double nChar = Math.round(Math.abs(Math.log10(getUpperBound()))) + 2;
            double border = 0.0;
            if (tickMarksVisible) {
                border += ticSize + gap1;
                if (tickLabelsVisible) {
                    border += ticFontSize * 0.75 * nChar + gap1;
                }
            }
            if (labelVisible) {
                border += labelFontSize + gap2;
            }
            return border;
        }
    }

    private void drawHorizontalAxis(GraphicsContextInterface gC) throws GraphicsIOException {
        gC.strokeLine(xOrigin, yOrigin, xOrigin + width, yOrigin);
        double value = minorTickStart;
        int gap1 = ticFontSize / 4;
        ticSize = ticFontSize * 0.75;
        double upper = getUpperBound();
        if (tickMarksVisible) {
            while (value < upper) {
                double x = getDisplayPosition(value);
                double y1 = yOrigin;
                double delta = Math.abs(value - Math.round(value / majorTickSpace) * majorTickSpace);
                if (delta < (minorTickSpace / 10.0)) {
                    String ticString = String.format(ticFormatString, value);
                    double y2 = yOrigin + ticSize;
                    gC.strokeLine(x, y1, x, y2);
                    if (tickLabelsVisible) {
                        gC.fillText(ticString, x, y2 + gap1);
                    }
                } else {
                    double y2 = yOrigin + ticSize / 2;
                    gC.strokeLine(x, y1, x, y2);
                }
                value += minorTickSpace;
            }
        }
        if (labelVisible && label.length() != 0) {
            gC.setTextBaseline(VPos.TOP);
            gC.setFont(labelFont);
            double labelTop = yOrigin;
            if (tickMarksVisible) {
                labelTop += ticSize + gap1;
                if (tickLabelsVisible) {
                    labelTop += ticFontSize + gap1;
                }
            }
            gC.fillText(label, xOrigin + width / 2, labelTop);
            //gC.drawText(label, leftBorder + width / 2, labelTop, "n", 0.0);
        }

    }

    private void drawVerticalAxis(GraphicsContextInterface gC) throws GraphicsIOException {
        gC.setTextBaseline(VPos.CENTER);
        gC.setTextAlign(TextAlignment.RIGHT);
        gC.strokeLine(xOrigin, yOrigin, xOrigin, yOrigin - height);
        int gap2 = labelFontSize / 4;
        if (tickMarksVisible) {
            getTickPositions();
            int gap1 = ticFontSize / 4;
            gap2 = labelFontSize / 4;
            ticSize = ticFontSize * 0.75;
            double value = minorTickStart;
            int ticStringLen = 0;
            double upper = getUpperBound();
            while (value < upper) {
                double y = getDisplayPosition(value);
                double x1 = xOrigin;
                double delta = Math.abs(value - Math.round(value / majorTickSpace) * majorTickSpace);
                if (delta < (minorTickSpace / 10.0)) {
                    String ticString = String.format(ticFormatString, value);
                    if (ticString.length() > ticStringLen) {
                        ticStringLen = ticString.length();
                    }
                    double x2 = x1 - ticSize;
                    gC.strokeLine(x1, y, x2, y);
                    if (tickLabelsVisible) {
                        gC.fillText(ticString, x2 - gap1, y);
                    }
                } else {
                    double x2 = x1 - ticSize / 2;
                    gC.strokeLine(x1, y, x2, y);
                }
                value += minorTickSpace;
            }
        }
        if (labelVisible && label.length() != 0) {
            gC.setFont(labelFont);

            gC.setTextBaseline(VPos.TOP);
            gC.setTextAlign(TextAlignment.CENTER);
            gC.save();
            gC.translate(xOrigin - width + gap2, yOrigin - height / 2);
            gC.rotate(270);
            gC.fillText(label, 0, 0);
            gC.restore();

            //gC.drawText(label, labelRight, bottomBorder - height / 2, "s", 90.0);
        }
    }

    public void setTickMarksVisible(boolean state) {
        tickMarksVisible = state;
    }

    public void setTickLabelsVisible(boolean state) {
        tickLabelsVisible = state;
    }

    public void setLabelVisible(boolean state) {
        labelVisible = state;
    }

    public void setVisible(boolean state) {

    }

}
