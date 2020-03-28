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
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.processor.gui.PolyChart;
import static org.nmrfx.processor.gui.spectra.MouseBindings.MOUSE_ACTION.DRAG_VIEW;
import static org.nmrfx.processor.gui.spectra.MouseBindings.MOUSE_ACTION.DRAG_VIEWX;
import static org.nmrfx.processor.gui.spectra.MouseBindings.MOUSE_ACTION.DRAG_VIEWY;

/**
 *
 * @author Bruce Johnson
 */
public class MouseBindings {

    public static enum MOUSE_ACTION {
        NOTHING,
        DRAG_SELECTION,
        DRAG_VIEW,
        DRAG_VIEWX,
        DRAG_VIEWY,
        DRAG_EXPAND,
        DRAG_PEAK,
        DRAG_PEAK_WIDTH,
        DRAG_REGION,
        DRAG_ADDREGION,
        CROSSHAIR
    }

    PolyChart chart;
    double[] dragStart = new double[2];
    boolean moved = false;
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

    boolean isPopupTrigger(MouseEvent mouseEvent) {
        boolean popUpTrigger = mouseEvent.isPopupTrigger();
        if (MainApp.isMac()) {
            popUpTrigger = popUpTrigger || mouseEvent.isControlDown();
        }
        return popUpTrigger;
    }

    public void mouseDragged(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        double deltaX = Math.abs(x - dragStart[0]);
        double deltaY = Math.abs(y - dragStart[1]);
        double tol = 3;
        if ((deltaX > tol) || (deltaY > tol)) {
            moved = true;
        }
        boolean draggingView = mouseAction == MOUSE_ACTION.DRAG_VIEW
                || mouseAction == MOUSE_ACTION.DRAG_VIEWX
                || mouseAction == MOUSE_ACTION.DRAG_VIEWY;
        if (!isPopupTrigger(mouseEvent)) {
            if (!draggingView && (mouseEvent.isMetaDown() || chart.getCursor().toString().equals("CROSSHAIR"))) {
                chart.handleCrossHair(mouseEvent, false);
            } else {
                if (mouseEvent.isPrimaryButtonDown()) {
                    switch (mouseAction) {
                        case CROSSHAIR:
                            chart.handleCrossHair(mouseEvent, false);
                            break;
                        case DRAG_EXPAND:
                            chart.dragBox(mouseAction, dragStart, x, y);
                            break;
                        case DRAG_ADDREGION:
                            chart.dragBox(mouseAction, dragStart, x, y);
                            break;
                        case DRAG_SELECTION:
                            chart.dragBox(mouseAction, dragStart, x, y);
                            break;
                        case DRAG_PEAK:
                            if (moved) {
                                chart.dragPeak(dragStart, x, y, false);
                            }
                            break;
                        case DRAG_PEAK_WIDTH:
                            if (moved) {
                                chart.dragPeak(dragStart, x, y, true);
                            }
                            break;
                        case DRAG_REGION:
                            chart.dragRegion(dragStart, x, y, true);
                            break;
                        case DRAG_VIEW:
                        case DRAG_VIEWX:
                        case DRAG_VIEWY:
                            double dx = x - dragStart[0];
                            double dy = y - dragStart[1];
                            if ((Math.abs(dx) >= 1.0) || (Math.abs(dy) >= 1.0)) {
                                dragStart[0] = x;
                                dragStart[1] = y;
                                if (mouseAction == DRAG_VIEWX) {
                                    dy = 0.0;
                                } else if (mouseAction == DRAG_VIEWY) {
                                    dx = 0.0;
                                }

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
        chart.setActiveChart();
        dragStart[0] = x;
        dragStart[1] = y;
        moved = false;
        mouseAction = MOUSE_ACTION.NOTHING;

//        System.out.println("sh " + mouseEvent.isShiftDown() + " alt " + mouseEvent.isAltDown()
//                + " meta " + mouseEvent.isMetaDown() + " cntrl " + mouseEvent.isControlDown());
        boolean altShift = mouseEvent.isShiftDown() && (mouseEvent.isAltDown() || mouseEvent.isControlDown());
        int border = chart.hitBorder(x, y);
        if (!isPopupTrigger(mouseEvent)) {
            if (!(altShift || (border != 0)) && (mouseEvent.isMetaDown() || chart.getCursor().toString().equals("CROSSHAIR"))) {
                if (!chart.getCursor().toString().equals("CROSSHAIR")) {
                    chart.getCrossHairs().setCrossHairState(true);
                }
                chart.handleCrossHair(mouseEvent, true);
                mouseAction = MOUSE_ACTION.CROSSHAIR;
            } else {
                if (mouseEvent.isPrimaryButtonDown()) {
                    if (border != 0) {
                        mouseAction = border == 1 ? MOUSE_ACTION.DRAG_VIEWY : MOUSE_ACTION.DRAG_VIEWX;
                    } else if (altShift) {
                        mouseAction = DRAG_VIEW;
                    } else {
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
                        } else if (mouseEvent.isAltDown() || mouseEvent.isControlDown()) {
                            if (hit.isPresent()) {
                                mouseAction = MOUSE_ACTION.DRAG_PEAK_WIDTH;
                            } else {
                                mouseAction = MOUSE_ACTION.DRAG_ADDREGION;
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
                                } else {
                                    mouseAction = MOUSE_ACTION.DRAG_EXPAND;
                                }
                            }
                            if (hit.isPresent() || hitPeak) {
                                mouseAction = MOUSE_ACTION.DRAG_PEAK;
                            }
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
        boolean menuShowing = chart.getSpectrumMenu().chartMenu.isShowing();
        if (!menuShowing && !mouseEvent.isPopupTrigger()) {
            boolean draggingView = mouseAction == MOUSE_ACTION.DRAG_VIEW
                    || mouseAction == MOUSE_ACTION.DRAG_VIEWX
                    || mouseAction == MOUSE_ACTION.DRAG_VIEWY;
            if (!draggingView && (mouseEvent.isMetaDown() || chart.getCursor().toString().equals("CROSSHAIR"))) {
                chart.handleCrossHair(mouseEvent, false);
                if (!chart.getCursor().toString().equals("CROSSHAIR")) {
                    chart.getCrossHairs().setCrossHairState(false);
                }
            } else {
                double x = mouseEvent.getX();
                double y = mouseEvent.getY();
                switch (mouseAction) {
                    case DRAG_EXPAND:
                        chart.finishBox(mouseAction, dragStart, x, y);
                        break;
                    case DRAG_ADDREGION:
                        chart.finishBox(mouseAction, dragStart, x, y);
                        break;
                    case DRAG_SELECTION:
                        chart.finishBox(mouseAction, dragStart, x, y);
                        break;
                    case DRAG_PEAK:
                        dragStart[0] = x;
                        dragStart[1] = y;
                        if (moved) {
                            chart.dragPeak(dragStart, x, y, false);
                        }
                        break;
                    case DRAG_PEAK_WIDTH:
                        dragStart[0] = x;
                        dragStart[1] = y;
                        if (moved) {
                            chart.dragPeak(dragStart, x, y, true);
                        }
                        widthMode = Optional.empty();
                        break;
                    default:
                        dragStart[0] = x;
                        dragStart[1] = y;
                        if (moved && widthMode.isPresent()) {
                            chart.dragPeak(dragStart, x, y, widthMode.get());
                        }
                        widthMode = Optional.empty();
                        break;
                }
            }

        }
    }

}
