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
package org.nmrfx.processor.gui.undo;

import java.util.Optional;
import org.nmrfx.processor.gui.PolyChart;

/**
 *
 * @author Bruce Johnson
 */
public class ChartUndoLimits extends ChartUndo {

    final double[][] limits;

    public ChartUndoLimits(PolyChart chart) {
        name = chart.getName();
        int nDim = chart.getNDim();
        limits = new double[nDim][2];
        for (int i = 0; i < nDim; i++) {
            limits[i][0] = chart.getAxis(i).getLowerBound();
            limits[i][1] = chart.getAxis(i).getUpperBound();
        }
    }

    @Override
    public boolean execute() {
        Optional<PolyChart> optChart = PolyChart.getChart(name);
        optChart.ifPresent(c -> {
            setLimits(c);
        });
        return optChart.isPresent();
    }

    public void setLimits(PolyChart chart) {
        int nDim = chart.getNDim();
        for (int i = 0; i < nDim; i++) {
            chart.getAxis(i).setLowerBound(limits[i][0]);
            chart.getAxis(i).setUpperBound(limits[i][1]);
        }
        chart.refresh();
    }
}
