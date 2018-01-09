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

import java.util.Optional;
import javafx.event.Event;
import javafx.scene.input.MouseEvent;
import org.nmrfx.processor.gui.PolyChart;

/**
 *
 * @author Bruce Johnson
 */
public class MouseBindings {

    PolyChart chart;
    double[] dragStart = new double[2];
    boolean dragMode = false;
    boolean selectMode = false;
    boolean mouseDown = false;
    Optional<Boolean> widthMode = Optional.empty();
    double mouseX;
    double mouseY;
    double mousePressX;
    double mousePressY;

    public MouseBindings(PolyChart chart) {
        this.chart = chart;
    }

    public double getMousePressX() {
        return mousePressX;
    }

    public double getMousePressY() {
        return mousePressY;
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public boolean isMouseDown() {
        return mouseDown;
    }

    public void mouseDragged(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        if (mouseEvent.isMetaDown() || (!mouseEvent.isControlDown() && chart.getCursor().toString().equals("CROSSHAIR"))) {
            chart.handleCrossHair(mouseEvent, false);
        } else {
            if (mouseEvent.isPrimaryButtonDown()) {
                int dragTol = 4;
                if ((Math.abs(x - dragStart[0]) > dragTol) || (Math.abs(y - dragStart[1]) > dragTol)) {
                    if (dragMode) {
                        chart.dragBox(dragStart, x, y);
                    } else {
                        if (!widthMode.isPresent()) {
                            boolean metaDown = mouseEvent.isAltDown();
                            widthMode = Optional.of(metaDown);
                        }
                        chart.dragPeak(dragStart, x, y, widthMode.get());
                    }
                }
            } else if (mouseEvent.isMiddleButtonDown()) {
                double dx = x - dragStart[0];
                double dy = y - dragStart[1];
                if ((Math.abs(dx) >= 1.0) || (Math.abs(dy) >= 1.0)) {
                    dragStart[0] = x;
                    dragStart[1] = y;
                    chart.scroll(dx, dy);
                }
            }
        }
    }

    public void mouseMoved(MouseEvent mouseEvent) {
        mouseX = mouseEvent.getX();
        mouseY = mouseEvent.getY();
    }

    public void mousePressed(MouseEvent mouseEvent) {
        mouseDown = true;
        dragMode = false;
        selectMode = false;
        chart.focus();
        chart.setActiveChart();
        mousePressX = mouseEvent.getX();
        mousePressY = mouseEvent.getY();
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        if (mouseEvent.isMetaDown() || (!mouseEvent.isControlDown() && chart.getCursor().toString().equals("CROSSHAIR"))) {
            if (!chart.getCursor().toString().equals("CROSSHAIR")) {
                chart.getCrossHairs().setCrossHairState(true);
            }
            chart.handleCrossHair(mouseEvent, true);
        } else {
            if (mouseEvent.isPrimaryButtonDown() && !mouseEvent.isControlDown()) {
                dragStart[0] = x;
                dragStart[1] = y;
                widthMode = Optional.empty();
                if (mouseEvent.isShiftDown()) {
                    dragMode = true;
                    selectMode = true;
                    chart.selectPeaks(x, y, true);
                } else if (mouseEvent.isAltDown()) {
                    dragMode = true;
                } else {
                    chart.selectPeaks(x, y, false);
                }
            } else if (mouseEvent.isMiddleButtonDown()) {
                dragStart[0] = x;
                dragStart[1] = y;
            }

        }

    }

    public void mouseReleased(Event event) {
        mouseDown = false;
        MouseEvent mouseEvent = (MouseEvent) event;
        if (!mouseEvent.isControlDown()) {
            if (mouseEvent.isMetaDown() || (!mouseEvent.isControlDown() && chart.getCursor().toString().equals("CROSSHAIR"))) {
                chart.handleCrossHair(mouseEvent, false);
                if (!chart.getCursor().toString().equals("CROSSHAIR")) {
                    chart.getCrossHairs().setCrossHairState(false);
                }
            } else {
                double x = mouseEvent.getX();
                double y = mouseEvent.getY();
                if (dragMode) {
                    chart.finishBox(selectMode, dragStart, x, y);
                } else {
                    dragStart[0] = x;
                    dragStart[1] = y;
                    if (widthMode.isPresent()) {
                        chart.dragPeak(dragStart, x, y, widthMode.get());
                    }
                    widthMode = Optional.empty();
                }
            }
        }
    }

}
