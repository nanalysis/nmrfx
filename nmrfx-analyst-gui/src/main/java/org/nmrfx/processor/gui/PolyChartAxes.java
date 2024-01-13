/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2023 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui;

import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import org.apache.commons.lang3.Range;
import org.nmrfx.chart.Axis;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

import java.util.List;

/**
 * Axis management for PolyChart.
 */
public class PolyChartAxes {
    public static final int X_INDEX = 0;
    public static final int Y_INDEX = 1;

    private final ObservableList<DatasetAttributes> datasetAttributesList; // shared with PolyChart

    private final Axis xAxis = new Axis(Orientation.HORIZONTAL, 0, 100, 200, 50);
    private final Axis yAxis = new Axis(Orientation.VERTICAL, 0, 100, 50, 200);

    private Axis[] axes = {xAxis, yAxis};
    private DatasetAttributes.AXMODE[] modes = {DatasetAttributes.AXMODE.PPM, DatasetAttributes.AXMODE.PPM};

    public PolyChartAxes(ObservableList<DatasetAttributes> datasetAttributesList) {
        this.datasetAttributesList = datasetAttributesList;
    }

    public void init(PolyChart chart) {
        xAxis.lowerBoundProperty().addListener(new AxisChangeListener(chart, X_INDEX, Axis.Bound.Lower));
        xAxis.upperBoundProperty().addListener(new AxisChangeListener(chart, X_INDEX, Axis.Bound.Upper));
        yAxis.lowerBoundProperty().addListener(new AxisChangeListener(chart, Y_INDEX, Axis.Bound.Lower));
        yAxis.upperBoundProperty().addListener(new AxisChangeListener(chart, Y_INDEX, Axis.Bound.Upper));
    }

    public int count() {
        return axes.length;
    }

    public Axis getX() {
        return xAxis;
    }

    public Axis getY() {
        return yAxis;
    }

    public Axis get(int iDim) {
        return iDim < axes.length ? axes[iDim] : null;
    }

    public DatasetAttributes.AXMODE getMode(int iDim) {
        return iDim < modes.length ? modes[iDim] : null;
    }

    public void setMode(int iDim, DatasetAttributes.AXMODE mode) {
        modes[iDim] = mode;
    }

    public void resetFrom(PolyChart chart, DatasetAttributes datasetAttrs, int nAxes) {
        DatasetBase dataset = datasetAttrs.getDataset();

        axes = new Axis[nAxes];
        axes[X_INDEX] = xAxis;
        axes[Y_INDEX] = yAxis;
        modes = new DatasetAttributes.AXMODE[nAxes];
        modes[X_INDEX] = DatasetAttributes.AXMODE.PPM;
        modes[Y_INDEX] = DatasetAttributes.AXMODE.PPM;
        for (int i = 2; i < nAxes; i++) {
            if (axes[i] != null) {
                if (dataset.getFreqDomain(i)) {
                    modes[i] = DatasetAttributes.AXMODE.PPM;
                } else {
                    modes[i] = DatasetAttributes.AXMODE.PTS;
                }
                axes[i].lowerBoundProperty().addListener(new AxisChangeListener(chart, i, Axis.Bound.Lower));
                axes[i].upperBoundProperty().addListener(new AxisChangeListener(chart, i, Axis.Bound.Upper));
            }
        }
    }

    public void updateAxisType(PolyChart chart, DatasetAttributes datasetAttrs, int nAxes) {
        DatasetBase dataset = datasetAttrs.getDataset();
        int[] dims = datasetAttrs.getDims();

        axes = new Axis[nAxes];
        axes[X_INDEX] = xAxis;
        axes[Y_INDEX] = yAxis;
        modes = new DatasetAttributes.AXMODE[nAxes];
        modes[X_INDEX] = DatasetAttributes.AXMODE.PPM;
        modes[Y_INDEX] = DatasetAttributes.AXMODE.PPM;
        for (int i = 2; i < nAxes; i++) {
            double[] ppmLimits = datasetAttrs.getMaxLimits(i);
            double centerPPM = (ppmLimits[X_INDEX] + ppmLimits[Y_INDEX]) / 2.0;
            axes[i] = new Axis(Orientation.HORIZONTAL, centerPPM, centerPPM, X_INDEX, Y_INDEX);
            if (dataset.getFreqDomain(dims[i])) {
                modes[i] = DatasetAttributes.AXMODE.PPM;
            } else {
                modes[i] = DatasetAttributes.AXMODE.PTS;
            }
            axes[i].lowerBoundProperty().addListener(new AxisChangeListener(chart, i, Axis.Bound.Lower));
            axes[i].upperBoundProperty().addListener(new AxisChangeListener(chart, i, Axis.Bound.Upper));
        }
    }

    public void copyTo(PolyChartAxes other) {
        for (int iAxis = 0; iAxis < axes.length; iAxis++) {
            other.modes[iAxis] = modes[iAxis];
            other.setMinMax(iAxis, axes[iAxis].getLowerBound(), axes[iAxis].getUpperBound());
            other.axes[iAxis].setLabel(axes[iAxis].getLabel());
            axes[iAxis].copyTo(other.axes[iAxis]);
        }
    }

    public void setMinMax(int iAxis, double min, double max) {
        Axis axis = get(iAxis);
        if (axis != null) {
            axis.setMinMax(min, max);
        }
    }

    public void incrementPlane(int axis, DatasetAttributes datasetAttributes, int amount) {
        int indexL = modes[axis].getIndex(datasetAttributes, axis, axes[axis].getLowerBound());
        int indexU = modes[axis].getIndex(datasetAttributes, axis, axes[axis].getUpperBound());
        int[] maxLimits = datasetAttributes.getMaxLimitsPt(axis);

        indexL += amount;
        indexU += amount;
        if (indexL < maxLimits[X_INDEX]) {
            indexL = maxLimits[X_INDEX];
        }
        if (indexU < maxLimits[X_INDEX]) {
            indexU = maxLimits[X_INDEX];
        }

        if (indexL > maxLimits[Y_INDEX]) {
            indexL = maxLimits[Y_INDEX];
        }
        if (indexU > maxLimits[Y_INDEX]) {
            indexU = maxLimits[Y_INDEX];
        }

        if (modes[axis] == DatasetAttributes.AXMODE.PTS) {
            axes[axis].setLowerBound(indexL);
            axes[axis].setUpperBound(indexU);
        } else {
            double posL = modes[axis].indexToValue(datasetAttributes, axis, indexL);
            double posU = modes[axis].indexToValue(datasetAttributes, axis, indexU);
            axes[axis].setLowerBound(posL);
            axes[axis].setUpperBound(posU);
        }
    }

    public void fullLimits(PolyChart.DISDIM disdim) {
        double[] limits = getRange(0);
        xAxis.setMinMax(limits[0], limits[1]);
        if (disdim == PolyChart.DISDIM.TwoD) {
            limits = getRange(1);
            yAxis.setMinMax(limits[0], limits[1]);
        }
    }

    public double[] getRange(int axis) {
        return getRangeFromDatasetAttributesList(datasetAttributesList, axis);
    }

    public double[] getRangeFromDatasetAttributesList(List<DatasetAttributes> attributes, int axis) {
        double[] limits = {Double.MAX_VALUE, Double.NEGATIVE_INFINITY};
        for (DatasetAttributes dataAttr : attributes) {
            if (!dataAttr.isProjection()) {
                dataAttr.checkRange(getMode(axis), axis, limits);
            } else {
                if (dataAttr.projection() == axis) {
                    dataAttr.checkRange(getMode(axis), 0, limits);
                }
            }
        }
        return limits;
    }

    public double[] getRange(int axis, double min, double max) {
        double[] limits = getRange(axis);
        if (min > limits[0]) {
            limits[0] = min;
        }
        if (max < limits[1]) {
            limits[1] = max;
        }
        return limits;
    }

    public double[] getLimits(int axis, double min, double max) {
        double[] limits = getRange(axis);
        double range = max -min;
        if (min > limits[0]) {
            limits[0] = min;
        } else {
            max = limits[0] + range;
        }
        if (max < limits[1]) {
            limits[1] = max;
        } else {
            limits[0] = limits[1] - range;
        }
        return limits;
    }


    /**
     * Checks the current axis is within provided range.
     *
     * @param limits The limits of the new range, lower bound at index 0, upper bound at index 1
     * @param iAxis  The axis to check
     * @return true if the axis range is within the provided range
     */
    public boolean currentRangeWithinNewRange(double[] limits, int iAxis) {
        Axis axis = get(iAxis);
        Range<Double> range = Range.between(limits[0], limits[1]);
        return range.contains(axis.getLowerBound()) && range.contains(axis.getUpperBound());
    }


    /**
     * Given a lower and upper bound, gets a valid range and attempts to keep the range between the original lower
     * and upper bounds. The new bounds may have a different range than the originally provided bounds if it is not
     * possible to have a valid range that large.
     *
     * @param axis       The axis to get the range for
     * @param lowerBound The lower bound to try.
     * @param upperBound The upper bound to try.
     * @return A new set of bounds that are within the valid range of the dataset.
     */
    public double[] getRangeMinimalAdjustment(int axis, double lowerBound, double upperBound) {
        double currentRange = Math.abs(upperBound - lowerBound);
        double[] validLimits = getRange(axis, lowerBound, upperBound);
        // if one of the limits has changed, adjust the other limit so the range is still the same.
        if (Double.compare(validLimits[0], lowerBound) != 0) {
            lowerBound = validLimits[0];
            upperBound = validLimits[0] + currentRange;
        } else {
            upperBound = validLimits[1];
            lowerBound = validLimits[1] - currentRange;
        }
        // Need to check the range again, in case the currentRange value was greater than the valid range.
        return getRange(axis, lowerBound, upperBound);
    }

    public void setAxisState(boolean leftEdge, boolean bottomEdge) {
        xAxis.setTicksAndLabelsVisible(bottomEdge);
        xAxis.setTickLabelsVisible(bottomEdge);
        xAxis.setTickMarksVisible(bottomEdge);
        xAxis.setLabelVisible(bottomEdge);
        yAxis.setTicksAndLabelsVisible(leftEdge);
        yAxis.setTickLabelsVisible(leftEdge);
        yAxis.setTickMarksVisible(leftEdge);
        yAxis.setLabelVisible(leftEdge);
    }

    public void setYAxisByLevel() {
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes dataAttr = datasetAttributesList.get(0);
            double delta = dataAttr.getLvl();
            double fOffset = dataAttr.getOffset();
            double min = -fOffset * delta;
            double max = min + delta;
            yAxis.setMinMax(min, max);
        }
    }
}
