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
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.IntegralHit;

/**
 *
 * @author Bruce Johnson
 */
public class MouseBindings {

    private static enum MOUSE_ACTION {
        NOTHING,
        DRAG_SELECTION,
        DRAG_VIEW,
        DRAG_EXPAND,
        DRAG_PEAK,
        DRAG_PEAK_WIDTH,
        DRAG_REGION,
        CROSSHAIR
    }

    PolyChart chart;
    double[] dragStart = new double[2];
    MOUSE_ACTION mouseAction = MOUSE_ACTION.NOTHING;
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
        if (!mouseEvent.isControlDown()) {
            if (mouseEvent.isMetaDown() || chart.getCursor().toString().equals("CROSSHAIR")) {
                chart.handleCrossHair(mouseEvent, false);
            } else {
                if (mouseEvent.isPrimaryButtonDown()) {
                    switch (mouseAction) {
                        case CROSSHAIR:
                            chart.handleCrossHair(mouseEvent, false);
                            break;
                        case DRAG_EXPAND:
                            chart.dragBox(dragStart, x, y);
                            break;
                        case DRAG_SELECTION:
                            chart.dragBox(dragStart, x, y);
                            break;
                        case DRAG_PEAK:
                            chart.dragPeak(dragStart, x, y, false);
                            break;
                        case DRAG_PEAK_WIDTH:
                            chart.dragPeak(dragStart, x, y, true);
                            break;
                        case DRAG_REGION:
                            chart.dragRegion(dragStart, x, y, true);
                            break;
                        case DRAG_VIEW:
                            double dx = x - dragStart[0];
                            double dy = y - dragStart[1];
                            if ((Math.abs(dx) >= 1.0) || (Math.abs(dy) >= 1.0)) {
                                dragStart[0] = x;
                                dragStart[1] = y;
                                chart.scroll(dx, dy);
                            }
                            break;

                        default:

                    }
                }
            }
        }
    }

    public void mouseMoved(MouseEvent mouseEvent) {
        mouseX = mouseEvent.getX();
        mouseY = mouseEvent.getY();
    }

    public void mousePressed(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        dragStart[0] = x;
        dragStart[1] = y;
        mouseAction = MOUSE_ACTION.NOTHING;
        if (!mouseEvent.isControlDown()) {
            if (mouseEvent.isMetaDown() || chart.getCursor().toString().equals("CROSSHAIR")) {
                if (!chart.getCursor().toString().equals("CROSSHAIR")) {
                    chart.getCrossHairs().setCrossHairState(true);
                }
                chart.handleCrossHair(mouseEvent, true);
                mouseAction = MOUSE_ACTION.CROSSHAIR;
            } else {
                if (mouseEvent.isPrimaryButtonDown()) {
                    Optional<Peak> hit = chart.hitPeak(x, y);
                    if (!hit.isPresent()) {
                        Optional<IntegralHit> hitR = chart.hitRegion(x, y);
                        if (!hitR.isPresent()) {
                            hitR = chart.hitIntegral(x, y);
                        }
                    }
                    if (mouseEvent.isShiftDown()) {
                        mouseAction = MOUSE_ACTION.DRAG_SELECTION;
                        chart.selectPeaks(x, y, true);
                    } else if (mouseEvent.isAltDown()) {
                        if (hit.isPresent()) {
                            mouseAction = MOUSE_ACTION.DRAG_PEAK_WIDTH;
                        } else {
                            mouseAction = MOUSE_ACTION.DRAG_EXPAND;
                        }
                    } else {
                        boolean hitPeak = chart.selectPeaks(x, y, false);
                        if (!hitPeak) {
                            boolean hadRegion = chart.hasActiveRegion();
                            boolean hitRegion = chart.selectRegion(x, y);
                            if (!hitRegion) {
                                hitRegion = chart.selectIntegral(x, y);
                            }
                            if ((hadRegion && !hitRegion) || (!hadRegion && hitRegion)) {
                                chart.refresh();
                            }
                            if (hitRegion) {
                                mouseAction = MOUSE_ACTION.DRAG_REGION;
                            }
                        }
                        if (hit.isPresent() || hitPeak) {
                            mouseAction = MOUSE_ACTION.DRAG_PEAK;
                        }
                    }
                } else if (mouseEvent.isMiddleButtonDown()) {
                    mouseAction = MOUSE_ACTION.DRAG_VIEW;
                }
            }
        }
    }

    public void mouseReleased(Event event) {
        mouseDown = false;
        MouseEvent mouseEvent = (MouseEvent) event;
        if (!mouseEvent.isControlDown()) {
            if (mouseEvent.isMetaDown() || chart.getCursor().toString().equals("CROSSHAIR")) {
                chart.handleCrossHair(mouseEvent, false);
                if (!chart.getCursor().toString().equals("CROSSHAIR")) {
                    chart.getCrossHairs().setCrossHairState(false);
                }
            } else {
                double x = mouseEvent.getX();
                double y = mouseEvent.getY();
                switch (mouseAction) {
                    case DRAG_EXPAND:
                        chart.finishBox(false, dragStart, x, y);
                        break;
                    case DRAG_SELECTION:
                        chart.finishBox(true, dragStart, x, y);
                        break;
                    case DRAG_PEAK:
                        dragStart[0] = x;
                        dragStart[1] = y;
                        chart.dragPeak(dragStart, x, y, false);
                        break;
                    case DRAG_PEAK_WIDTH:
                        dragStart[0] = x;
                        dragStart[1] = y;
                        chart.dragPeak(dragStart, x, y, true);
                        widthMode = Optional.empty();
                        break;
                    default:
                        dragStart[0] = x;
                        dragStart[1] = y;
                        if (widthMode.isPresent()) {
                            chart.dragPeak(dragStart, x, y, widthMode.get());
                        }
                        widthMode = Optional.empty();
                        break;
                }
            }

        }
    }

}
