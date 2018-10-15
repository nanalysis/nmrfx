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

import java.text.DecimalFormat;
import java.util.List;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

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
            Double oldBound = (Double) oldValue;
            double newBound = newValue.doubleValue();
            if (chart == PolyChart.activeChart) {
                if (FXMLController.specAttrWindowController != null) {
                    StringProperty limitProp = FXMLController.specAttrWindowController.limitFields[axNum][endNum];
                    limitProp.setValue(FORMATTER.format(newBound));
                }
                if (axNum >= 2) {
                    int pt1 = (int) chart.axes[axNum].getLowerBound();
                    int pt2 = (int) chart.axes[axNum].getUpperBound();
                    int center = (pt1 + pt2) / 2;
                    chart.controller.getStatusBar().updatePlaneSpinner(center, axNum);
                }
                chart.refresh();
                if (PolyChart.getNSyncGroups() > 0) {
                    List<String> names = chart.getDimNames();
                    String name = names.get(axNum);
                    int syncGroup = chart.getSyncGroup(name);

                    PolyChart.charts.stream().filter((otherChart) -> (otherChart != chart)).forEach((otherChart) -> {
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
                                    if (otherNames.indexOf(otherName) > 1) {
                                        otherChart.layoutPlotChildren();
                                    }
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
