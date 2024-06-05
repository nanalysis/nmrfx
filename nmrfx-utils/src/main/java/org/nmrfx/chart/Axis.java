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
 package org.nmrfx.chart;

 import javafx.beans.property.DoubleProperty;
 import javafx.beans.property.SimpleDoubleProperty;
 import javafx.geometry.Orientation;
 import javafx.geometry.VPos;
 import javafx.scene.paint.Color;
 import javafx.scene.text.Font;
 import javafx.scene.text.TextAlignment;
 import org.nmrfx.graphicsio.GraphicsContextInterface;
 import org.nmrfx.graphicsio.StyledCanvasText;

 import java.io.InputStream;

 import static javafx.geometry.Orientation.HORIZONTAL;
 import static javafx.geometry.Orientation.VERTICAL;

 /**
  * @author brucejohnson
  */
//TODO uncomment once core & utils are merged
//@PluginAPI("ring")
 public class Axis implements AxisLimits {

     public enum Bound {
         Lower, Upper
     }

     private static final double GRID_MINOR_LINE_WIDTH = 0.5;
     private static final double GRID_MAJOR_LINE_WIDTH = 1.0;
     private static final double GRID_DASHES = 1;
     private static final double TARGET_PIX = 85.0;

     private static Font defaultFont = null;

     private final Orientation orientation;
     private double ticSize = 10.0;
     private double width;
     private double height;
     private boolean reverse;
     private String label = "";
     private String ticFormatString = "%.1f";
     private boolean zeroIncluded = false;
     private double xOrigin = 100.0;
     private double yOrigin = 800.0;
     private double labelFontSize = 16;
     private double ticFontSize = 12;
     private boolean integerAxis = false;
     private final double lineWidth = 1.0;
     private Color color = Color.BLACK;
     private final String fontFamily = "Liberation Sans";
     private Font ticFont;
     private Font labelFont;
     private final double defaultUpper;
     private final double defaultLower;
     private boolean tickMarksVisible = true;
     private boolean tickLabelsVisible = true;
     private boolean labelVisible = true;
     private boolean autoRanging = false;
     private double gridLength = 0.0;
     private TickInfo tInfo = new TickInfo();

     private static void loadFont() {
         InputStream iStream = Axis.class.getResourceAsStream("/LiberationSans-Regular.ttf");
         defaultFont = Font.loadFont(iStream, 12);
     }

     public void setColor(Color value) {
         color = value;
     }

     public Color getColor() {
         return color;
     }

     public Axis(Orientation orientation, double lowerBound, double upperBound, double width, double height) {
         this.orientation = orientation;
         this.defaultLower = lowerBound;
         this.defaultUpper = upperBound;
         this.width = width;
         this.height = height;
         if (defaultFont == null) {
             loadFont();
         }
         ticFont = new Font("Liberation Sans", ticFontSize);
         labelFont = new Font("Liberation Sans", labelFontSize);
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
         if ((isReversed() && (getOrientation() == Orientation.HORIZONTAL)) || (!isReversed() && (getOrientation() == Orientation.VERTICAL))) {
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

     public void setZeroIncluded(boolean state) {
         zeroIncluded = state;
     }

     public boolean isZeroIncluded() {
         return zeroIncluded;
     }

     public Orientation getOrientation() {
         return orientation;
     }

     public boolean isAutoRanging() {
         return autoRanging;
     }

     public void setAutoRanging(boolean value) {
         this.autoRanging = value;
     }

     public void setGridLength(double value) {
         gridLength = value;
     }

     public double getGridLength() {
         return gridLength;
     }

     public double[] autoRange(double min, double max, boolean tightMode) {
         double length = VERTICAL == getOrientation() ? getHeight() : getWidth();
         boolean keepMin = false;
         if (isZeroIncluded()) {
             if (min > 0.0) {
                 min = 0.0;
                 keepMin = true;
             }
             if (max < 0.0) {
                 max = 0.0;
             }
         }
         TickInfo tf = getTickInfo(min, max, length);
         if (!tf.centerMode) {
             double adjust = tightMode ? tf.minorSpace : tf.majorSpace;
             double majEnd;
             if (tf.majorEnd > max) {
                 majEnd = tf.majorEnd - tf.majorSpace;
             } else {
                 majEnd = tf.majorEnd;
             }
             double majStart;
             if (tf.majorStart < min) {
                 majStart = tf.majorStart + tf.majorSpace;
             } else {
                 majStart = tf.majorStart;
             }
             double deltaMin = Math.abs(majStart - min);
             double deltaMax = Math.abs(max - majEnd);
             double nIncr = 1;
             if (tightMode) {
                 nIncr = Math.ceil(deltaMin / adjust) + 1.0;
             }
             if (!keepMin) {
                 min = majStart - nIncr * adjust;
             }

             nIncr = 1;
             if (tightMode) {
                 nIncr = Math.ceil(deltaMax / adjust) + 1.0;
             }
             max = majEnd + nIncr * adjust;

         }
         setMinMax(min, max);
         return new double[]{min, max};
     }

     public double getRange() {
         return getUpperBound() - getLowerBound();
     }

     public void setMinMax(double min, double max) {
         lowerBoundProperty().set(min);
         upperBoundProperty().set(max);
     }

     public double getScale() {
         return calcScale();
     }

     public boolean getIntegerAxis() {
         return integerAxis;
     }

     public void setIntegerAxis(boolean value) {
         integerAxis = value;
     }

     public void setTickFontSize(double size) {
         ticFontSize = size;
         ticFont = new Font(fontFamily, ticFontSize);
     }

     public double getTickFontSize() {
         return ticFontSize;
     }

     public void setLabelFontSize(double size) {
         labelFontSize = size;
         labelFont = new Font(fontFamily, labelFontSize);
     }

     public double getLabelFontSize() {
         return labelFontSize;
     }

     static class TickInfo {

         double minorSpace;
         double majorSpace;
         double minorStart;
         double majorStart;
         double majorEnd;
         double incr;
         boolean centerMode = false;
         int nDecimals;

         public String toString() {
             return String.format("%.3f %.3f %.3f %.3f %.3f %3f", minorStart, minorSpace, majorStart, majorSpace, majorEnd, incr);
         }
     }

     private void getTickPositions() {
         double length = VERTICAL == getOrientation() ? getHeight() : getWidth();
         tInfo = getTickInfo(getLowerBound(), getUpperBound(), length);
         ticFormatString = integerAxis ? "%d" : "%." + tInfo.nDecimals + "f";
     }

     private double calcTicSpacing(double scale, double floorScale) {
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
         return selValue;
     }

     private TickInfo getIntegerAxisInfo(double lower, double upper, double length) {
         TickInfo tf = new TickInfo();
         if (length > 0) {
             double nTic1 = length / TARGET_PIX;
             double range = upper - lower;
             double scale = range / nTic1;
             double logScale = Math.log10(scale);
             double floorScale = Math.floor(logScale);
             tf.nDecimals = 0;
             double selValue = calcTicSpacing(scale, floorScale);
             double incValue = Math.round(selValue * Math.pow(10.0, floorScale));

             tf.incr = incValue;
             if ((lower + 1.2 * incValue) > upper) {
                 tf.majorSpace = incValue;
                 tf.minorSpace = incValue;
                 tf.minorStart = (lower + upper) / 2.0;
                 tf.majorStart = tf.minorStart;
                 tf.centerMode = true;
             } else {
                 tf.majorSpace = incValue;
                 tf.minorSpace = incValue;
                 tf.minorStart = Math.ceil(lower / tf.minorSpace) * tf.minorSpace;
                 tf.majorStart = Math.ceil(lower / tf.majorSpace) * tf.majorSpace;
                 int nTicks = (int) Math.floor((upper - lower) / tf.majorSpace);
                 tf.majorEnd = tf.majorStart + tf.majorSpace * nTicks;
             }
         } else {
             tf.majorSpace = upper - lower;
             tf.minorSpace = tf.majorSpace;
             tf.minorStart = lower;
             tf.majorStart = tf.minorStart;
         }
         return tf;
     }

     private TickInfo getTickInfo(double lower, double upper, double length) {
         if (integerAxis) {
             return getIntegerAxisInfo(lower, upper, length);
         }
         TickInfo tf = new TickInfo();
         tf.centerMode = false;
         if (length > 0) {
             double nTic1 = length / TARGET_PIX;
             double range = upper - lower;
             double scale = range / nTic1;
             double logScale = Math.log10(scale);
             double floorScale = Math.floor(logScale);
             int nDecimals = (int) Math.round(-logScale) + 1;
             if (nDecimals < 0) {
                 nDecimals = 0;
             } else if (nDecimals > 10) {
                 nDecimals = 10;
             }
             tf.nDecimals = nDecimals;
             double selValue = calcTicSpacing(scale, floorScale);
             double incValue = selValue * Math.pow(10.0, floorScale);
             tf.incr = incValue;
             if ((lower + 1.2 * incValue) > upper) {
                 tf.majorSpace = incValue;
                 tf.minorSpace = incValue / 5.0;
                 tf.minorStart = (lower + upper) / 2.0;
                 tf.majorStart = tf.minorStart;
                 tf.centerMode = true;
             } else {
                 tf.majorSpace = incValue;
                 tf.minorSpace = incValue / 5.0;
                 tf.minorStart = Math.ceil(lower / tf.minorSpace) * tf.minorSpace;
                 tf.majorStart = Math.ceil(lower / tf.majorSpace) * tf.majorSpace;
                 int nTicks = (int) Math.floor((upper - lower) / tf.majorSpace);
                 tf.majorEnd = tf.majorStart + tf.majorSpace * nTicks;
             }
         } else {
             tf.majorSpace = upper - lower;
             tf.minorSpace = tf.majorSpace / 5.0;
             tf.minorStart = lower;
             tf.majorStart = tf.minorStart;
         }
         return tf;
     }

     public void draw(GraphicsContextInterface gC) {
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
         getTickPositions();
         if (orientation == HORIZONTAL) {
             double gap1 = ticFontSize / 4;
             double gap2 = labelFontSize / 4;
             ticSize = ticFontSize * 0.75;
             double border = 2.0;
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
             double gap1 = ticFontSize / 4;
             double gap2 = labelFontSize / 4;
             ticSize = ticFontSize * 0.75;
             int nChar = tInfo.nDecimals;
             int nLeftDig = (int) Math.round(Math.log10(getUpperBound()));
             nChar += nLeftDig + 2;
             double border = 2.0;
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

     private void drawHorizontalAxis(GraphicsContextInterface gC) {
         gC.setTextAlign(TextAlignment.CENTER);
         gC.strokeLine(xOrigin, yOrigin, xOrigin + width, yOrigin);
         double value = tInfo.minorStart;
         double gap1 = ticFontSize / 4;
         ticSize = ticFontSize * 0.75;
         double upper = getUpperBound();
         if (tickMarksVisible) {
             while (value < upper) {
                 double x = getDisplayPosition(value);
                 double y1 = yOrigin;
                 double delta = Math.abs(value - Math.round(value / tInfo.majorSpace) * tInfo.majorSpace);
                 if (tInfo.centerMode || (delta < (tInfo.minorSpace / 10.0))) {
                     String ticString = integerAxis ? String.format(ticFormatString, (int) value) : String.format(ticFormatString, value);
                     double y2 = yOrigin + ticSize;
                     gC.strokeLine(x, y1, x, y2);
                     if (tickLabelsVisible) {
                         gC.fillText(ticString, x, y2 + gap1);
                     }
                     if (gridLength > 0) {
                         gC.setLineWidth(GRID_MAJOR_LINE_WIDTH);
                         gC.setLineDashes(GRID_DASHES);
                         gC.strokeLine(x, y1, x, y1 - gridLength);
                         gC.setLineWidth(lineWidth);
                         gC.setLineDashes();
                     }

                 } else {
                     double y2 = yOrigin + ticSize / 2;
                     gC.strokeLine(x, y1, x, y2);
                     if (gridLength > 0) {
                         gC.setLineWidth(GRID_MINOR_LINE_WIDTH);
                         gC.setLineDashes(GRID_DASHES);
                         gC.strokeLine(x, y1, x, y1 - gridLength);
                         gC.setLineWidth(lineWidth);
                         gC.setLineDashes();

                     }
                 }
                 if (tInfo.centerMode) {
                     break;
                 }
                 value += tInfo.minorSpace;
             }
         }
         if (labelVisible && (label != null) && (!label.isEmpty())) {
             gC.setTextBaseline(VPos.TOP);
             gC.setFont(labelFont);
             double labelTop = yOrigin;
             if (tickMarksVisible) {
                 labelTop += ticSize + gap1;
                 if (tickLabelsVisible) {
                     labelTop += ticFontSize + gap1;
                 }
             }
             StyledCanvasText.drawStyledText(gC, label, xOrigin + width / 2, labelTop);
         }

     }

     private void drawVerticalAxis(GraphicsContextInterface gC) {
         gC.setTextBaseline(VPos.CENTER);
         gC.setTextAlign(TextAlignment.RIGHT);
         gC.strokeLine(xOrigin, yOrigin, xOrigin, yOrigin - height);
         double gap2 = labelFontSize / 4;
         if (tickMarksVisible) {
             double gap1 = ticFontSize / 4;
             gap2 = labelFontSize / 4;
             ticSize = ticFontSize * 0.75;
             double value = tInfo.minorStart;
             int ticStringLen = 0;
             double upper = getUpperBound();
             while (value < upper) {
                 double y = getDisplayPosition(value);
                 double x1 = xOrigin;
                 double delta = Math.abs(value - Math.round(value / tInfo.majorSpace) * tInfo.majorSpace);
                 if (tInfo.centerMode || (delta < (tInfo.minorSpace / 10.0))) {
                     String ticString = integerAxis ? String.format(ticFormatString, (int) value) : String.format(ticFormatString, value);
                     if (ticString.length() > ticStringLen) {
                         ticStringLen = ticString.length();
                     }
                     double x2 = x1 - ticSize;
                     gC.strokeLine(x1, y, x2, y);
                     if (tickLabelsVisible) {
                         gC.fillText(ticString, x2 - gap1, y);
                     }
                     if (gridLength > 0) {
                         gC.setLineWidth(GRID_MAJOR_LINE_WIDTH);
                         gC.setLineDashes(GRID_DASHES);
                         gC.strokeLine(x1, y, x1 + gridLength, y);
                         gC.setLineWidth(lineWidth);
                         gC.setLineDashes();
                     }
                 } else {
                     double x2 = x1 - ticSize / 2;
                     gC.strokeLine(x1, y, x2, y);
                     if (gridLength > 0) {
                         gC.setLineWidth(GRID_MINOR_LINE_WIDTH);
                         gC.setLineDashes(GRID_DASHES);
                         gC.strokeLine(x1, y, x1 + gridLength, y);
                         gC.setLineWidth(lineWidth);
                         gC.setLineDashes();
                     }
                 }
                 if (tInfo.centerMode) {
                     break;
                 }

                 value += tInfo.minorSpace;
             }
         }
         if (labelVisible && (label != null) && (!label.isEmpty())) {
             gC.setFont(labelFont);

             gC.setTextBaseline(VPos.TOP);
             gC.setTextAlign(TextAlignment.CENTER);
             gC.save();
             gC.translate(xOrigin - width + gap2, yOrigin - height / 2);
             gC.rotate(270);
             gC.nativeCoords(true);
             StyledCanvasText.drawStyledText(gC, label, 0, 0);
             gC.nativeCoords(false);
             gC.restore();
         }
     }

     public void setTickMarksVisible(boolean state) {
         tickMarksVisible = state;
     }

     public void setTickLabelsVisible(boolean state) {
         tickLabelsVisible = state;
     }

     public boolean isTickLabelsVisible() {
         return tickLabelsVisible;
     }

     public void setLabelVisible(boolean state) {
         labelVisible = state;
     }

     public boolean isLabelVisible() {
         return labelVisible;
     }

     public void setTicksAndLabelsVisible(boolean visible) {
         setTickLabelsVisible(visible);
         setTickMarksVisible(visible);
         setLabelVisible(visible);
     }

     public void updateLabel(String label) {
         if (!isLabelVisible()) {
             setLabel("");
         } else if (!label.equals(getLabel())) {
             setLabel(label);
         }
     }

     public void copyTo(Axis newAxis) {
         newAxis.setColor(getColor());
         newAxis.setLabel(getLabel());
         newAxis.setLabelFontSize(getLabelFontSize());
         newAxis.setAutoRanging(isAutoRanging());
         newAxis.setLabelVisible(isLabelVisible());
         newAxis.setReverse(isReversed());
         newAxis.setTickFontSize(getTickFontSize());
         newAxis.setTickLabelsVisible(isTickLabelsVisible());
     }
 }
