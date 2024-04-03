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
import org.nmrfx.peaks.Peak;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;

/**
 * @author brucejohnson
 */
public class PeakMenu extends ChartMenu {

    Peak peak;

    public PeakMenu(PolyChart chart) {
        super(chart);
    }

    @Override
    public void makeChartMenu() {
        chartMenu = new ContextMenu();
        MenuItem inspectorItem = new MenuItem("Inspector");
        inspectorItem.setOnAction((ActionEvent e) -> {
            showPeakInspector();
        });
        chartMenu.getItems().add(inspectorItem);

    }

    void showPeakInspector() {
        chart.focus();
        FXMLController controller = chart.getFXMLController();
        controller.showPeakAttr();
        if (peak != null) {
            controller.getPeakAttrController().gotoPeak(peak);
        }
        controller.getPeakAttrController().getStage().toFront();
    }

    public void setActivePeak(Peak peak) {
        this.peak = peak;
    }

    public Peak getPeak() {
        return peak;
    }
}
