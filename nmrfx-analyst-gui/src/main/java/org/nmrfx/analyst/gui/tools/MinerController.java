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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.tools;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.nmrfx.analyst.dataops.Align;
import org.nmrfx.analyst.dataops.Normalize;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author johnsonb
 */
public class MinerController {
    private static final Logger log = LoggerFactory.getLogger(MinerController.class);

    ScannerTool scannerTool;
    List<Menu> menus = new ArrayList<>();
    private MenuButton adjusterMenu = null;

    public MinerController(ScannerTool scannerTool) {
        this.scannerTool = scannerTool;
        makeMenus();
    }

    private void makeMenus() {
        ToolBar scannerBar = scannerTool.getToolBar();
        adjusterMenu = new MenuButton("Adjust");
        Menu normMenu = new Menu("Normalize");
        Menu alignMenu = new Menu("Align");
        adjusterMenu.getItems().addAll(normMenu, alignMenu);

        scannerBar.getItems().add(adjusterMenu);
        MenuItem maxMenuItem = new MenuItem("To Max");
        maxMenuItem.setOnAction(e -> normalizeByMax(e));
        MenuItem medianMenuItem = new MenuItem("To Median");
        medianMenuItem.setOnAction(e -> normalizeByMedian(e));
        MenuItem integralMenuItem = new MenuItem("To Integral");
        integralMenuItem.setOnAction(e -> normalizeBySum(e));
        MenuItem undoNormMenuItem = new MenuItem("Undo");
        undoNormMenuItem.setOnAction(e -> undoNormalize(e));
        normMenu.getItems().addAll(maxMenuItem, medianMenuItem, integralMenuItem, undoNormMenuItem);

        MenuItem maxAlignMenuItem = new MenuItem("By Max");
        maxAlignMenuItem.setOnAction(e -> alignToMax(e));
        MenuItem covMenuItem = new MenuItem("By Covariance");
        covMenuItem.setOnAction(e -> alignByCov(e));

        MenuItem segmentMenuItem = new MenuItem("Segments");
        segmentMenuItem.setOnAction(e -> alignBySegments(e));

        MenuItem undoAlignMenuItem = new MenuItem("Undo");
        undoAlignMenuItem.setOnAction(e -> undoAlign(e));
        alignMenu.getItems().addAll(maxAlignMenuItem, covMenuItem, segmentMenuItem, undoAlignMenuItem);
        menus.add(normMenu);
        menus.add(alignMenu);
    }

    /**
     * Set the disabled state of the sub menu items.
     *
     * @param state If true, disable sub menu items.
     */
    public void setDisableSubMenus(boolean state) {
        for (var menu : menus) {
            menu.setDisable(state);
        }
    }

    @FXML
    public void undoAlign(ActionEvent event) {
        PolyChart polyChart = scannerTool.getChart();
        Dataset dataset = (Dataset) polyChart.getDataset();
        if (dataset != null) {
            Align aligner = new Align();
            try {
                List<Double> valueList;
                if (scannerTool.hasColumn("offset")) {
                    valueList = scannerTool.getValues("offset");
                } else {
                    return;
                }
                for (int i = 0; i < valueList.size(); i++) {
                    valueList.set(i, -valueList.get(i));
                }
                aligner.align(dataset, valueList);
                polyChart.refresh();
                for (int i = 0; i < valueList.size(); i++) {
                    valueList.set(i, 0.0);
                }
                scannerTool.getScanTable().addTableColumn("offset", "D");
                scannerTool.setItems("offset", valueList);
                scannerTool.getScanTable().refresh();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    @FXML
    public void alignToMax(ActionEvent event) {
        PolyChart polyChart = scannerTool.getChart();
        Dataset dataset = (Dataset) polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getCrossHairs().getVerticalPositions();
            DatasetAttributes dataAttr = polyChart.getDatasetAttributes().get(0);
            AXMODE axMode = polyChart.getAxes().getMode(0);
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            try {
                List<Double> valueList = null;
                if (scannerTool.hasColumn("offset")) {
                    valueList = scannerTool.getValues("offset");
                }
                Double[] deltas = aligner.alignByMaxStream(dataset, 0, pt1, pt2);
                polyChart.refresh();
                if (valueList != null) {
                    for (int i = 0; i < valueList.size(); i++) {
                        valueList.set(i, valueList.get(i) + deltas[i]);
                    }
                } else {
                    valueList = Arrays.asList(deltas);
                }
                scannerTool.getScanTable().addTableColumn("offset", "D");
                scannerTool.setItems("offset", valueList);
                scannerTool.getScanTable().refresh();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    @FXML
    public void reorderByCorr(ActionEvent event) {
        PolyChart polyChart = scannerTool.getChart();
        Dataset dataset = (Dataset) polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getCrossHairs().getVerticalPositions();
            DatasetAttributes dataAttr = polyChart.getDatasetAttributes().get(0);
            AXMODE axMode = polyChart.getAxes().getMode(0);
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            try {
                aligner.sortByCorr(dataset, -1, pt1, pt2);
                polyChart.refresh();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    @FXML
    public void alignByCov(ActionEvent event) {
        PolyChart polyChart = scannerTool.getChart();
        Dataset dataset = (Dataset) polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getCrossHairs().getVerticalPositions();
            DatasetAttributes dataAttr = polyChart.getDatasetAttributes().get(0);
            AXMODE axMode = polyChart.getAxes().getMode(0);
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            int sectionLength = 0;
            int pStart = pt1;
            int iWarp = 0;
            int tStart = 0;

            try {
                List<Double> valueList = null;
                if (scannerTool.hasColumn("offset")) {
                    valueList = scannerTool.getValues("offset");
                }
                Double[] deltas = aligner.alignByCowStream(dataset, pt1, pt2, pStart, sectionLength, iWarp, tStart);
                polyChart.refresh();
                if (valueList != null) {
                    for (int i = 0; i < valueList.size(); i++) {
                        valueList.set(i, valueList.get(i) + deltas[i]);
                    }
                } else {
                    valueList = Arrays.asList(deltas);
                }
                scannerTool.getScanTable().addTableColumn("offset", "D");
                scannerTool.setItems("offset", valueList);
                scannerTool.getScanTable().refresh();

            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }

    }

    @FXML
    public void alignBySegments(ActionEvent event) {
        PolyChart polyChart = scannerTool.getChart();
        Dataset dataset = (Dataset) polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getCrossHairs().getVerticalPositions();
            DatasetAttributes dataAttr = polyChart.getDatasetAttributes().get(0);
            AXMODE axMode = polyChart.getAxes().getMode(0);
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            int sectionLength = 0;
            int maxShift = 0;
            try {
                List<Double> valueList = null;
                if (scannerTool.hasColumn("offset")) {
                    valueList = scannerTool.getValues("offset");
                }
                Double[] deltas = aligner.alignBySegmentsStream(dataset, pt1, pt2, sectionLength, maxShift, true);
                polyChart.refresh();
                if (valueList != null) {
                    for (int i = 0; i < valueList.size(); i++) {
                        valueList.set(i, valueList.get(i) + deltas[i]);
                    }
                } else {
                    valueList = Arrays.asList(deltas);
                }
                scannerTool.getScanTable().addTableColumn("offset", "D");
                scannerTool.setItems("offset", valueList);
                scannerTool.getScanTable().refresh();

            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    @FXML
    public void normalizeByMedian(ActionEvent event) {
        normalize("Median");
    }

    @FXML
    public void normalizeBySum(ActionEvent event) {
        normalize("Sum");
    }

    @FXML
    public void normalizeByMax(ActionEvent event) {
        normalize("Max");
    }

    @FXML
    public void undoNormalize(ActionEvent event) {
        normalize("Undo");
    }

    public void normalize(String mode) {
        PolyChart polyChart = scannerTool.getChart();
        Dataset dataset = (Dataset) polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getCrossHairs().getVerticalPositions();
            DatasetAttributes dataAttr = polyChart.getDatasetAttributes().get(0);
            AXMODE axMode = polyChart.getAxes().getMode(0);
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Normalize normalizer = new Normalize();
            try {
                List<Double> valueList = null;
                if (scannerTool.hasColumn("scale")) {
                    valueList = scannerTool.getValues("scale");
                }
                if (mode.equals("Undo")) {
                    if (valueList != null) {
                        for (int i = 0; i < valueList.size(); i++) {
                            valueList.set(i, 1.0 / valueList.get(i));
                        }
                        normalizer.normalizeByStream(dataset, 0, pt1, pt2, valueList);
                        Collections.fill(valueList, 1.0);
                    }
                } else {
                    Double[] values = normalizer.normalizeByStream(dataset, 0, pt1, pt2, mode);
                    if (valueList != null) {
                        for (int i = 0; i < valueList.size(); i++) {
                            valueList.set(i, valueList.get(i) * values[i]);
                        }
                    } else {
                        valueList = Arrays.asList(values);
                    }
                }
                scannerTool.getScanTable().addTableColumn("scale", "D");
                scannerTool.setItems("scale", valueList);
                polyChart.refresh();
                scannerTool.getScanTable().refresh();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }
}
