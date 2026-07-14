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

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.nmrfx.peaks.Peak;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;

import java.util.Optional;

/**
 * @author brucejohnson
 */
public class CanvasBindings {

    final FXMLController controller;
    final Canvas canvas;

    public CanvasBindings(FXMLController controller, Canvas canvas) {
        this.controller = controller;
        this.canvas = canvas;

    }

    public final void setHandlers() {
        Node mouseNode = canvas;
        canvas.setFocusTraversable(false);
        mouseNode.setOnContextMenuRequested((ContextMenuEvent event) -> {
            PolyChart chart = controller.getActiveChart();
            double x = event.getX();
            double y = event.getY();
            Optional<Peak> hitPeak = chart.hitPeak(x, y);
            ChartMenu menu = null;
            if (hitPeak.isPresent()) {
                menu = chart.getPeakMenu();
                ((PeakMenu) menu).setActivePeak(hitPeak.get());
            } else {
                Optional<IntegralHit> hitIntegral = chart.hitIntegral(x, y);
                if (hitIntegral.isPresent()) {
                    menu = chart.getIntegralMenu();
                    ((IntegralMenu) menu).setHit(hitIntegral.get());
                } else {
                    Optional<IntegralHit> hitRegion = chart.hitRegion(false, x, y);
                    if (hitRegion.isPresent()) {
                        if (chart.getActiveRegion().isPresent() && (chart.getActiveRegion().get() == hitRegion.get().getDatasetRegion())) {
                            menu = chart.getRegionMenu();
                            ((RegionMenu) menu).setHit(hitRegion.get());
                        }
                    } else {
                        Optional<CanvasAnnotation> hitAnno = chart.hitAnnotation(x, y, false);
                        if (hitAnno.isPresent()) {
                            menu = hitAnno.get().getMenu();
                        }
                    }
                }
            }
            if (menu == null) {
                menu = chart.getSpectrumMenu();
            }
            menu.show(mouseNode.getScene().getWindow(), event.getScreenX(), event.getScreenY());

        });
        mouseNode.setOnKeyPressed((KeyEvent keyEvent) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getKeyBindings().keyPressed(keyEvent);
            }
        });

        mouseNode.setOnKeyReleased((KeyEvent keyEvent) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getKeyBindings().keyReleased(keyEvent);
            }
        });

        mouseNode.setOnKeyTyped((KeyEvent keyEvent) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getKeyBindings().keyTyped(keyEvent);
            }
        });

        mouseNode.setOnMouseDragged((MouseEvent mouseEvent) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getMouseBindings().mouseDragged(mouseEvent);
            }
        });

        mouseNode.setOnMousePressed((MouseEvent mouseEvent) -> {
            Optional<PolyChart> oChart = controller.getChart(mouseEvent.getX(), mouseEvent.getY());
            oChart.ifPresent(chart -> {
                PolyChartManager.getInstance().setActiveChart(chart);
                chart.getMouseBindings().mousePressed(mouseEvent);
            });
            mouseNode.requestFocus();
        });


        mouseNode.setOnMouseMoved((MouseEvent mouseEvent) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getMouseBindings().mouseMoved(mouseEvent);
            }
        });

        mouseNode.setOnMouseReleased((MouseEvent mouseEvent) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getMouseBindings().mouseReleased(mouseEvent);
            }
        });

        mouseNode.setOnZoom((Event event) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getGestureBindings().zoom(event);
            }
        });
        mouseNode.setOnScroll((ScrollEvent event) -> {
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                chart.getGestureBindings().scroll(event);
            }
        });
    }
}
