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
package org.nmrfx.processor.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.NMRAxis;

import java.text.DecimalFormat;
import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class AxisChangeListener implements ChangeListener<Number> {

    PolyChart chart;
    int axNum;
    int endNum;
    static final DecimalFormat FORMATTER = new DecimalFormat();

    static {
        FORMATTER.setMaximumFractionDigits(3);
    }

    public AxisChangeListener(PolyChart chart, int axNum, int endNum) {
        this.chart = chart;
        this.axNum = axNum;
        this.endNum = endNum;

    }

    @Override
    public void changed(ObservableValue observable, Number oldValue, Number newValue) {
        if (axNum < chart.getNDim()) {
            chart.updateDatasetAttributeBounds();
            double newBound = newValue.doubleValue();
            if (chart == PolyChartManager.getInstance().getActiveChart()) {
                if (axNum >= 2) {
                    DatasetAttributes datasetAttributes = chart.getDatasetAttributes().get(0);
                    NMRAxis axis = chart.axes[axNum];
                    int indexL = chart.axModes[axNum].getIndex(datasetAttributes, axNum, axis.getLowerBound());
                    int indexU = chart.axModes[axNum].getIndex(datasetAttributes, axNum, axis.getUpperBound());

                    chart.controller.getStatusBar().updatePlaneSpinner(indexL, axNum, 0);
                    chart.controller.getStatusBar().updatePlaneSpinner(indexU, axNum, 1);
                }
                if (PolyChart.getNSyncGroups() > 0) {
                    List<String> names = chart.getDimNames();
                    String name = names.get(axNum);
                    int syncGroup = chart.getSyncGroup(name);

                    PolyChartManager.getInstance().getAllCharts().stream().filter((otherChart) -> (otherChart != chart)).forEach((otherChart) -> {
                        List<String> otherNames = otherChart.getDimNames();
                        int i = 0;
                        for (String otherName : otherNames) {
                            if (otherName.equals(name)) {
                                int otherGroup = otherChart.getSyncGroup(otherName);
                                if ((otherGroup > 0) && (syncGroup == otherGroup)) {
                                    if (endNum == 0) {
                                        otherChart.axes[i].setLowerBound(newBound);
                                    } else {
                                        otherChart.axes[i].setUpperBound(newBound);
                                    }
                                    otherChart.refresh();
                                }
                            }
                            i++;
                        }
                    });
                }
            }
        }
    }

}
