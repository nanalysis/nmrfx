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
import javafx.geometry.Orientation;
import static javafx.geometry.Orientation.VERTICAL;
import static javafx.geometry.Orientation.HORIZONTAL;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

/**
 *
 * @author brucejohnson
 */
public class Axis {

    Orientation orientation;
    double ticSize = 10.0;
    private double lowerBound;
    private double upperBound;
    private DoubleProperty lowerBoundProp;
    private DoubleProperty upperBoundProp;
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
    private double labelFontSize = 12;
    private Color color = Color.BLACK;

    public Axis(Orientation orientation, double lowerBound, double upperBound, double width, double height) {
        this.orientation = orientation;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.width = width;
        this.height = height;
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
        return ((displayPosition) / scaleValue) + lowerBound;

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
        double f = (value.doubleValue() - lowerBound) / (upperBound - lowerBound);
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

    public DoubleProperty lowerBoundProperty() {
        if (null == lowerBoundProp) {
            lowerBoundProp = new DoublePropertyBase(lowerBound) {
                @Override
                public Object getBean() {
                    return Axis.this;
                }

                @Override
                public String getName() {
                    return "lowerBound";
                }
            };
        }
        return lowerBoundProp;
    }

    public DoubleProperty upperBoundProperty() {
        if (null == upperBoundProp) {
            upperBoundProp = new DoublePropertyBase(upperBound) {
                @Override
                public Object getBean() {
                    return Axis.this;
                }

                @Override
                public String getName() {
                    return "upperBound";
                }
            };
        }
        return upperBoundProp;
    }

    /**
     * @return the lowerBound
     */
    public double getLowerBound() {
        return lowerBound;
    }

    /**
     * @param lowerBound the lowerBound to set
     */
    public void setLowerBound(double lowerBound) {
        this.lowerBound = lowerBound;
    }

    /**
     * @return the upperBound
     */
    public double getUpperBound() {
        return upperBound;
    }

    /**
     * @param upperBound the upperBound to set
     */
    public void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
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
        return upperBound - lowerBound;
    }

    public void setMinMax(double min, double max) {
        lowerBound = min;
        upperBound = max;
    }

    public double getScale() {
        return 1.0;
    }

    private void getTickPositions() {
        double length = VERTICAL == getOrientation() ? getHeight() : getWidth();
        if (length > 0) {
            double targetPix = 140;
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
            double maxNoOfMinorTicks = 10;
            majorTickSpace = incValue;
            minorTickSpace = incValue / 5.0;
            minorTickStart = Math.ceil(lowerBound / minorTickSpace) * minorTickSpace;
            majorTickStart = Math.ceil(lowerBound / majorTickSpace) * majorTickSpace;
        }
    }

    public void draw(GraphicsContext gC) {
        if (orientation == VERTICAL) {
            drawVerticalAxis(gC);
        } else {
            drawHorizontalAxis(gC);
        }
    }

    private void drawHorizontalAxis(GraphicsContext gC) {
        gC.setTextAlign(TextAlignment.CENTER);
        gC.setTextBaseline(VPos.TOP);
        gC.setFill(color);
        gC.setStroke(color);
        getTickPositions();

        gC.strokeLine(xOrigin, yOrigin, xOrigin + width, yOrigin);
        double value = majorTickStart;
        while (value < upperBound) {
            double x = getDisplayPosition(value);
            String ticString = String.format(ticFormatString, value);
            double y1 = yOrigin;
            double y2 = yOrigin + ticSize;
            gC.strokeLine(x, y1, x, y2);
            gC.fillText(ticString, x, y2 + 2.0);
            //gC.drawText(ticString, x, y2 - 2, "n", 0.0);
            value += majorTickSpace;
        }
        gC.setTextBaseline(VPos.BOTTOM);
        if (label.length() != 0) {
            double labelTop = yOrigin + height - 2;
            gC.fillText(label, xOrigin + width / 2, labelTop);
            //gC.drawText(label, leftBorder + width / 2, labelTop, "n", 0.0);
        }

    }

    private void drawVerticalAxis(GraphicsContext gC) {
        gC.setFill(color);
        gC.setStroke(color);
        gC.setTextBaseline(VPos.CENTER);
        gC.setTextAlign(TextAlignment.RIGHT);
        getTickPositions();
        gC.strokeLine(xOrigin, yOrigin, xOrigin, yOrigin - height);
        double value = majorTickStart;
        int ticStringLen = 0;
        while (value < upperBound) {
            double y = getDisplayPosition(value);
            String ticString = String.format(ticFormatString, value);
            if (ticString.length() > ticStringLen) {
                ticStringLen = ticString.length();
            }
            double x1 = xOrigin;
            double x2 = x1 - ticSize;
            gC.strokeLine(x1, y, x2, y);
            gC.fillText(ticString, x2 - 2, y);

            value += majorTickSpace;
        }
        if (label.length() != 0) {
            gC.setTextBaseline(VPos.TOP);
            gC.setTextAlign(TextAlignment.CENTER);
            gC.save();
            gC.translate(labelFontSize / 2, yOrigin - height / 2);
            gC.rotate(270);
            gC.fillText(label, 0, 0);
            gC.restore();

            //gC.drawText(label, labelRight, bottomBorder - height / 2, "s", 90.0);
        }

    }

    public void setTickMarksVisible(boolean state) {

    }

    public void setTickLabelsVisible(boolean state) {

    }

    public void setVisible(boolean state) {

    }

}
