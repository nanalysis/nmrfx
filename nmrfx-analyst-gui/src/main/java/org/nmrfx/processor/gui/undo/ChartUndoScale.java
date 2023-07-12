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

import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Bruce Johnson
 */
public class ChartUndoScale extends ChartUndo {

    Map<String, double[]> scaleMap = new HashMap<>();

    public ChartUndoScale(PolyChart chart) {
        name = chart.getName();
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        dataAttrs.stream().forEach(d -> {
            double scale = d.getLvl();
            double[] scales = {scale};
            scaleMap.put(d.getFileName(), scales);
        });
    }

    @Override
    public boolean execute() {
        Optional<PolyChart> optChart = PolyChartManager.getInstance().findChartByName(name);
        optChart.ifPresent(c -> {
            setScales(c);
        });
        return optChart.isPresent();
    }

    public void setScales(PolyChart chart) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        dataAttrs.stream().forEach(d -> {
            double[] scales = scaleMap.get(d.getFileName());
            if (scales != null) {
                d.setLvl(scales[0]);
            }
        });
        chart.refresh();
    }
}
