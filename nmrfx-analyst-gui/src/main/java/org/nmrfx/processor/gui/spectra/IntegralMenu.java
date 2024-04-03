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
package org.nmrfx.processor.gui.spectra;

import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.utils.GUIUtils;

/**
 * @author brucejohnson
 */
public class IntegralMenu extends ChartMenu {

    IntegralHit hit;

    public IntegralMenu(PolyChart chart) {
        super(chart);
    }

    @Override
    public void makeChartMenu() {
        chartMenu = new ContextMenu();
        int[] norms = {1, 2, 3, 4, 5, 6, 9, 100, 0};
        for (var norm : norms) {
            final int iNorm = norm;
            MenuItem normItem;
            if (norm == 0) {
                normItem = new MenuItem("Value...");
                normItem.setOnAction((ActionEvent e) -> setIntegralNormToValue());

            } else {
                normItem = new MenuItem(String.valueOf(iNorm));
                normItem.setOnAction((ActionEvent e) -> setIntegralNorm(iNorm));
            }

            chartMenu.getItems().add(normItem);
        }
    }

    public void setHit(IntegralHit hit) {
        this.hit = hit;

    }

    void setIntegralNorm(int iNorm) {
        DatasetRegion region = hit.getDatasetRegion();
        double integral = region.getIntegral();
        DatasetBase dataset = hit.getDatasetAttr().getDataset();
        dataset.setNorm(integral * dataset.getScale() / iNorm);
        chart.refresh();

    }

    void setIntegralNormToValue() {
        DatasetRegion region = hit.getDatasetRegion();
        double integral = region.getIntegral();
        DatasetBase dataset = hit.getDatasetAttr().getDataset();
        String normString = GUIUtils.input("Integral Norm Value");
        try {
            double norm = Double.parseDouble(normString);
            dataset.setNorm(integral * dataset.getScale() / norm);
            chart.refresh();
        } catch (NumberFormatException ignored) {

        }

    }

}
