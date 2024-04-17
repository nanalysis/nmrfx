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

import javafx.animation.PauseTransition;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.annotations.AnnoText;
import org.nmrfx.processor.gui.spectra.ChartBorder;
import org.nmrfx.processor.gui.spectra.IntegralHit;
import org.nmrfx.processor.gui.spectra.MultipletSelection;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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
        DRAG_PEAKPICK,
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
    AtomicBoolean waitingForPopover = new AtomicBoolean(false);
    PauseTransition pause = null;
    Object currentSelection;
    Bounds currentBounds;

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
        if (AnalystApp.isMac()) {
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
        if (!isPopupTrigger(mouseEvent) && (handler != null)) {
            handler.mouseDragged(mouseEvent);
        }
    }

    private void showPopOver() {
        if (waitingForPopover.get()) {
            Bounds objectBounds = chart.getCanvas().localToScreen(currentBounds);
            AnalystApp.getAnalystApp().showPopover(chart, objectBounds, currentSelection);
        }
    }

    private void hidePopOver(boolean always) {
        AnalystApp.getAnalystApp().hidePopover(always);
    }

    private void showAfterDelay() {
        if (pause == null) {
            pause = new PauseTransition(Duration.millis(1000));
            pause.setOnFinished(
                    e -> showPopOver());
        }
        pause.playFromStart();
    }

    public void mouseMoved(MouseEvent mouseEvent) {
        mouseX = mouseEvent.getX();
        mouseY = mouseEvent.getY();
        Optional<MultipletSelection> hit = PeakMouseHandlerHandler.handlerOverMultiplet(this);
        ChartBorder border = chart.hitBorder(mouseX, mouseY);
        if (border == ChartBorder.LEFT) {
            setCursor(Cursor.CLOSED_HAND);
            return;
        } else if (border == ChartBorder.BOTTOM) {
            setCursor(Cursor.CLOSED_HAND);
            return;
        } else {
            unsetCursor();
        }

        if (hit.isPresent()) {
            if (handler == null) {
                handler = new PeakMouseHandlerHandler(this);
            }
            if (!hit.get().isLine()) {
                MultipletSelection multipletSelection = hit.get();
                Bounds bounds = multipletSelection.getBounds();
                if ((bounds != null) && !waitingForPopover.get()) {
                    currentBounds = bounds;
                    currentSelection = multipletSelection;
                    waitingForPopover.set(true);
                    showAfterDelay();
                }
            }
            setCursor(Cursor.HAND);
        } else {
            Optional<CanvasAnnotation> annoOpt = chart.hitAnnotation(mouseX, mouseY, false);
            if (annoOpt.isPresent()) {
                var annotation = annoOpt.get();
                if (handler == null) {
                    handler = new AnnotationMouseHandlerHandler(this, annotation);
                }
                if (!waitingForPopover.get() && (annotation instanceof AnnoText)) {
                    var annoText = (AnnoText) annotation;
                    currentBounds = annoText.getBounds();
                    waitingForPopover.set(true);
                    currentSelection = annotation;
                    showAfterDelay();
                }
                setCursor(Cursor.HAND);
            } else {
                Optional<IntegralHit> hitIntegral = IntegralMouseHandlerHandler.handlerOverIntegral(this);
                if (hitIntegral.isPresent() && (hitIntegral.get().getBounds() != null)) {
                    IntegralHit integralHit = hitIntegral.get();
                    if (handler == null) {
                        handler = new IntegralMouseHandlerHandler(this, integralHit);
                    }
                    if (!waitingForPopover.get()) {
                        currentBounds = integralHit.getBounds();
                        currentSelection = integralHit;
                        waitingForPopover.set(true);
                        showAfterDelay();
                    }
                    setCursor(Cursor.HAND);

                } else {
                    handler = null;
                    waitingForPopover.set(false);
                    if (pause != null) {
                        pause.stop();
                    }
                    unsetCursor();
                }
            }
        }

        if (handler != null) {
            handler.mouseMoved(mouseEvent);
        }
    }

    private void setCursor(Cursor cursor) {
        FXMLController controller = chart.getFXMLController();
        if (controller.getCurrentCursor() != cursor) {
            controller.setCurrentCursor(cursor);
        }
    }

    private void unsetCursor() {
        FXMLController controller = chart.getFXMLController();
        if (controller.getCurrentCursor() != controller.getCursor()) {
            controller.setCurrentCursor(controller.getCursor());
        }
    }


    private void setHandler(MouseHandler handler) {
        this.handler = handler;
    }

    public void mousePressed(MouseEvent mouseEvent) {
        this.mouseEvent = mouseEvent;
        mouseX = mouseEvent.getX();
        mouseY = mouseEvent.getY();
        PolyChartManager.getInstance().setActiveChart(chart);
        dragStart[0] = mouseX;
        dragStart[1] = mouseY;
        moved = false;
        int clickCount = mouseEvent.getClickCount();
        handler = null;
        waitingForPopover.set(false);
        hidePopOver(false);

        boolean altShift = mouseEvent.isShiftDown() && (mouseEvent.isAltDown() || mouseEvent.isControlDown());

        if (!isPopupTrigger(mouseEvent)) {
            ChartBorder border = chart.hitBorder(mouseX, mouseY);
            if (!(altShift || (border == ChartBorder.LEFT || border == ChartBorder.BOTTOM)) && (mouseEvent.isMetaDown() || CanvasCursor.isCrosshair(chart.getCanvasCursor()))) {
                if (mouseEvent.isMetaDown() && !CanvasCursor.isSelector(chart.getCanvasCursor())) {
                    BoxMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                } else {
                    if (CanvasCursor.isCrosshair(chart.getCanvasCursor()) || mouseEvent.isMetaDown()) {
                        chart.getCrossHairs().setAllStates(true);
                    }
                    setHandler(new CrossHairMouseHandler(this));
                }
                handler.mousePressed(mouseEvent);
            } else {
                if (mouseEvent.isPrimaryButtonDown()) {
                    if (CanvasCursor.isPeak(chart.getCanvasCursor())) {
                        PeakPickHandler.handler(this).ifPresent(this::setHandler);
                    }
                    if (CanvasCursor.isRegion(chart.getCanvasCursor())) {
                        RegionMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                    }
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
                            PeakMouseHandlerHandler.handlePeaks(this, mouseEvent.isShiftDown()).ifPresent(this::setHandler);
                        }
                    }
                    if (handler == null) {
                        PeakMouseHandlerHandler.handlerHitMultiplet(this).ifPresent(this::setHandler);
                        if (handler != null) {
                            PeakMouseHandlerHandler.handlePeaks(this, mouseEvent.isShiftDown()).ifPresent(this::setHandler);
                        }
                    }
                    if (handler == null) {
                        if (hadRegion) {
                            RegionMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                        }
                        if (handler == null) {
                            IntegralMouseHandlerHandler.handler(this).ifPresent(this::setHandler);
                        }
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
                    } else if (currentRegion.isPresent() && previousRegion.isPresent() &&
                            (currentRegion.get() != previousRegion.get())) {
                        chart.refresh();
                    }
                    handler.mousePressed(mouseEvent);
                }
            }
        }
    }

    public void mouseReleased(Event event) {
        mouseDown = false;
        MouseEvent mouseEvent = (MouseEvent) event;
        boolean menuShowing = chart.getSpectrumMenu().chartMenu.isShowing();
        if (!menuShowing && !mouseEvent.isPopupTrigger() && (handler != null)) {
            handler.mouseReleased(mouseEvent);
        }
        handler = null;
    }
}
