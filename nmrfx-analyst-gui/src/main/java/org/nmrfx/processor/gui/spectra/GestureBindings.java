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
package org.nmrfx.processor.gui.spectra;

import javafx.event.Event;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import org.apache.commons.lang3.SystemUtils;
import org.nmrfx.processor.gui.PolyChart;

/**
 *
 * @author Bruce Johnson
 */
public class GestureBindings {

    PolyChart chart;

    public GestureBindings(PolyChart chart) {
        this.chart = chart;
    }

    public void rotate(RotateEvent rEvent) {
        if (chart.hasData() && chart.getController().isPhaseSliderVisible()) {
            double angle = rEvent.getAngle();
            chart.setPh0(chart.getPh0() + angle);
            double sliderPH0 = chart.getPh0() + chart.getDataPH0();
            double sliderPH1 = chart.getPh1() + chart.getDataPH1();
            chart.getController().getPhaser().setPhaseLabels(sliderPH0, sliderPH1);
            chart.refresh();
        }
    }

    public void rotationFinished(RotateEvent rEvent) {
        if (chart.hasData() && chart.getController().isPhaseSliderVisible()) {
            double angle = rEvent.getAngle();
            chart.setPh0(chart.getPh0() + angle);
            chart.getController().getPhaser().setPhaseLabels(chart.getPh0(), chart.getPh1());
            // use properties??
            if (rEvent.getEventType() == RotateEvent.ROTATION_FINISHED) {
                double sliderPH0 = chart.getPh0() + chart.getDataPH0();
                chart.getController().getPhaser().handlePh0Reset(sliderPH0);
            }
            chart.refresh();
        }

    }

    public void zoom(Event event) {
        ZoomEvent rEvent = (ZoomEvent) event;
        double zoom = rEvent.getZoomFactor();
        chart.zoom(zoom);

    }

    public void scroll(ScrollEvent event) {
        double x = chart.getMouseBindings().getMouseX();
        double y = chart.getMouseBindings().getMouseY();
        ChartBorder border = chart.hitBorder(x, y);
        double dx = event.getDeltaX();
        double dy = event.getDeltaY();
        int scrollDirectionFactor = SystemUtils.IS_OS_MAC  ? 1 : -1;
        if (border == ChartBorder.LEFT && chart.getNDim() < 2){
            chart.scroll(dx, scrollDirectionFactor * dy);
        }
        else if (border == ChartBorder.RIGHT || border == ChartBorder.TOP) {
            chart.updateProjectionScale(border, scrollDirectionFactor * dy);
            chart.refresh();
        } else if ((border == ChartBorder.LEFT || border == ChartBorder.BOTTOM) || (event.isAltDown() && border == ChartBorder.NONE)) {
            chart.zoom(scrollDirectionFactor * -dy / 50.0 + 1.0);
        } else {
            chart.scaleY(scrollDirectionFactor * dy);
        }
    }

}
