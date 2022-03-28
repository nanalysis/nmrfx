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
package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.event.Event;
import javafx.scene.input.MouseEvent;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.processor.gui.PolyChart;

import java.util.Optional;

/**
 * @author Bruce Johnson
 */
public class MouseBindings {

    public enum MOUSE_ACTION {
        NOTHING,
        DRAG,
        DRAG_SELECTION,
        DRAG_VIEW,
        DRAG_VIEWX,
        DRAG_VIEWY,
        DRAG_EXPAND,
        DRAG_PEAK,
        DRAG_PEAK_WIDTH,
        DRAG_REGION,
        DRAG_ADDREGION,
        DRAG_ANNO,
        CROSSHAIR
    }

    PolyChart chart;
    MouseHandler handler;
    MouseEvent mouseEvent;
    double[] dragStart = new double[2];
    boolean moved = false;
    boolean mouseDown = false;
    double mouseX;
    double mouseY;
    double mousePressX;
    double mousePressY;

    public MouseBindings(PolyChart chart) {
        this.chart = chart;
    }

    public PolyChart getChart() {
        return chart;
    }

    public MouseEvent getMouseEvent() {
        return mouseEvent;
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

    public double[] getDragStart() {
        return dragStart;
    }

    public boolean getMoved() {
        return moved;
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
        mouseX = x;
        mouseY = y;
        double deltaX = Math.abs(x - dragStart[0]);
        double deltaY = Math.abs(y - dragStart[1]);
        double tol = 3;
        if ((deltaX > tol) || (deltaY > tol)) {
            moved = true;
        }
        if (!isPopupTrigger(mouseEvent)) {
            if (handler != null) {
                handler.mouseDragged(mouseEvent);
            }
        }
    }

    public void mouseMoved(MouseEvent mouseEvent) {
        mouseX = mouseEvent.getX();
        mouseY = mouseEvent.getY();
        if (handler != null) {
            handler.mouseMoved(mouseEvent);
        }
    }

    private void setHandler(MouseHandler handler) {
        this.handler = handler;
    }

    public void mousePressed(MouseEvent mouseEvent) {
        this.mouseEvent = mouseEvent;
        mouseX = mouseEvent.getX();
        mouseY = mouseEvent.getY();
        chart.setActiveChart();
        dragStart[0] = mouseX;
        dragStart[1] = mouseY;
        moved = false;
        int clickCount = mouseEvent.getClickCount();
        handler = null;

        boolean altShift = mouseEvent.isShiftDown() && (mouseEvent.isAltDown() || mouseEvent.isControlDown());
        int border = chart.hitBorder(mouseX, mouseY);
        if (chart.isSelected()) {
            Optional<Integer> hitCorner = hitChartCorner(mouseX, mouseY, 10);
            if (hitCorner.isPresent()) {
            }
            return;
        }

        if (!isPopupTrigger(mouseEvent)) {
            if (!(altShift || (border != 0)) && (mouseEvent.isMetaDown() || chart.getCanvasCursor().toString().equals("CROSSHAIR"))) {
                if (!chart.getCanvasCursor().toString().equals("CROSSHAIR")) {
                    chart.getCrossHairs().setCrossHairState(true);
                }
                CrossHairMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                handler.mousePressed(mouseEvent);
            } else {
                if (mouseEvent.isPrimaryButtonDown()) {
                    boolean hadRegion = chart.hasActiveRegion();
                    Optional<DatasetRegion> previousRegion = chart.getActiveRegion();
                    boolean selectedRegion = false;
                    AnnotationMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                    if (handler == null) {
                        ViewMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                    }
                    if (handler == null) {
                        PeakMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                        if (handler != null) {
                            PeakMouseHandlerHandler.handlePeaks(this).ifPresent(this::setHandler);
                        }
                    }
                    if (handler == null) {
                        PeakMouseHandlerHandler.handlerHitMultiplet(this).ifPresent(this::setHandler);
                        if (handler != null) {
                            PeakMouseHandlerHandler.handlePeaks(this).ifPresent(this::setHandler);
                        }
                    }
                    if (handler == null) {
                        if (hadRegion) {
                            RegionMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                         }
                        if (handler == null) {
                            IntegralMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                        }
                        //selectedRegion = chart.selectIntegral(x, y);
                    }
                    if (handler == null) {
                        chart.selectPeaks(mouseX, mouseY, false);
                        if (clickCount == 2) {
                            selectedRegion = chart.selectRegion(false, mouseX, mouseY);
                        }
                    }
                    if (handler == null) {
                        BoxMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                    }
                    Optional<DatasetRegion> currentRegion = chart.getActiveRegion();
                    if (currentRegion.isPresent() != previousRegion.isPresent()) {
                        chart.refresh();
                    } else if (currentRegion.isPresent() && currentRegion.get() != previousRegion.get()) {
                        chart.refresh();
                    }
                    if (handler instanceof BoxMouseHandlerHandler) {
                        if (!selectedRegion && chart.isSelectable() && (clickCount == 2)) {
                            handler = null;
                            chart.selectChart(true);
                            chart.refresh();
                        } else {
                            handler.mousePressed(mouseEvent);
                        }
                    } else {
                        handler.mousePressed(mouseEvent);
                    }
                }
            }
        }
    }

    public void mouseReleased(Event event) {
        mouseDown = false;
        MouseEvent mouseEvent = (MouseEvent) event;
        boolean menuShowing = chart.getSpectrumMenu().chartMenu.isShowing();
        if (!menuShowing && !mouseEvent.isPopupTrigger()) {
            if (handler != null) {
                handler.mouseReleased(mouseEvent);
            }
        }
        handler = null;
    }

    Optional<Integer> hitChartCorner(double x, double y, double halfWidth) {
        double[][] corners = chart.getCorners();
        Optional<Integer> result = Optional.empty();
        int iCorner = 0;
        for (var corner : corners) {
            double dx = Math.abs(corner[0] - x);
            double dy = Math.abs(corner[1] - y);
            if ((dx < halfWidth) && (dy < halfWidth)) {
                result = Optional.of(iCorner);
                break;
            }
            iCorner++;
        }
        return result;
    }

}
