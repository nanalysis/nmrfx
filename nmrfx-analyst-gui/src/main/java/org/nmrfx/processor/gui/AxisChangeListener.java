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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.nmrfx.chart.Axis;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

public class AxisChangeListener implements ChangeListener<Number> {
    private final PolyChart chart;
    private final int axisIndex;
    private final Axis.Bound bound;

    public AxisChangeListener(PolyChart chart, int axisIndex, Axis.Bound bound) {
        this.chart = chart;
        this.axisIndex = axisIndex;
        this.bound = bound;
    }

    @Override
    public void changed(ObservableValue observable, Number oldValue, Number newValue) {
        if (axisIndex < chart.getNDim()) {
            chart.updateDatasetAttributeBounds();
            if (chart == PolyChartManager.getInstance().getActiveChart()) {
                if (axisIndex >= 2) {
                    DatasetAttributes datasetAttributes = chart.getDatasetAttributes().get(0);
                    Axis axis = chart.getAxes().get(axisIndex);
                    int indexL = chart.getAxes().getMode(axisIndex).getIndex(datasetAttributes, axisIndex, axis.getLowerBound());
                    int indexU = chart.getAxes().getMode(axisIndex).getIndex(datasetAttributes, axisIndex, axis.getUpperBound());

                    chart.getFXMLController().getStatusBar().updatePlaneSpinner(indexL, axisIndex, 0);
                    chart.getFXMLController().getStatusBar().updatePlaneSpinner(indexU, axisIndex, 1);
                }

                PolyChartManager.getInstance().getSynchronizer().syncAxes(chart, axisIndex, bound, newValue.doubleValue());
            }
        }
    }
}
