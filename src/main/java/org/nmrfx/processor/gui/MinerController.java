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
package org.nmrfx.processor.gui;

import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ToolBar;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.nmrfx.processor.dataops.Align;
import org.nmrfx.processor.dataops.Normalize;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import org.nmrfx.processor.gui.spectra.NMRAxis;

/**
 *
 * @author johnsonb
 */
public class MinerController {

    ScannerController scannerController;

    public MinerController(ScannerController scannerController) {
        this.scannerController = scannerController;
        makeMenus();
    }

    private void makeMenus() {
        ToolBar scannerBar = scannerController.getToolBar();
        MenuButton adjusterMenu = new MenuButton("Adjust");
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

        MenuButton alignButton = new MenuButton("Align");
        MenuItem maxAlignMenuItem = new MenuItem("By Max");
        maxAlignMenuItem.setOnAction(e -> alignToMax(e));
        MenuItem covMenuItem = new MenuItem("By Covariance");
        covMenuItem.setOnAction(e -> alignByCov(e));
        MenuItem undoAlignMenuItem = new MenuItem("Undo");
        undoAlignMenuItem.setOnAction(e -> undoAlign(e));
        alignMenu.getItems().addAll(maxAlignMenuItem, covMenuItem, undoAlignMenuItem);
    }

    @FXML
    public void undoAlign(ActionEvent event) {
        PolyChart polyChart = scannerController.getChart();
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            try {
                List<Double> valueList = null;
                if (scannerController.hasColumn("offset")) {
                    valueList = scannerController.getValues("offset");
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
                scannerController.scanTable.addTableColumn("offset", "D");
                scannerController.setItems("offset", valueList);
                scannerController.scanTable.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @FXML
    public void alignToMax(ActionEvent event) {
        PolyChart polyChart = scannerController.getChart();
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            try {
                List<Double> valueList = null;
                if (scannerController.hasColumn("offset")) {
                    valueList = scannerController.getValues("offset");
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
                scannerController.scanTable.addTableColumn("offset", "D");
                scannerController.setItems("offset", valueList);
                scannerController.scanTable.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @FXML
    public void reorderByCorr(ActionEvent event) {
        PolyChart polyChart = scannerController.getChart();
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            try {
                aligner.sortByCorr(dataset, -1, pt1, pt2);
                polyChart.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @FXML
    public void alignByCov(ActionEvent event) {
        PolyChart polyChart = scannerController.getChart();
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            int sectionLength = 0;
            int pStart = pt1;
            int iWarp = 0;
            int tStart = 0;

            try {
                List<Double> valueList = null;
                if (scannerController.hasColumn("offset")) {
                    valueList = scannerController.getValues("offset");
                }
                Double[] deltas = aligner.alignByCovStream(dataset, pt1, pt2, pStart, sectionLength, iWarp, tStart);
                polyChart.refresh();
                if (valueList != null) {
                    for (int i = 0; i < valueList.size(); i++) {
                        valueList.set(i, valueList.get(i) + deltas[i]);
                    }
                } else {
                    valueList = Arrays.asList(deltas);
                }
                scannerController.scanTable.addTableColumn("offset", "D");
                scannerController.setItems("offset", valueList);
                scannerController.scanTable.refresh();

            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
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
        PolyChart polyChart = scannerController.getChart();
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            AXMODE axMode = polyChart.getAxMode(0);
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Normalize normalizer = new Normalize();
            try {
                List<Double> valueList = null;
                if (scannerController.hasColumn("scale")) {
                    valueList = scannerController.getValues("scale");
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
                scannerController.scanTable.addTableColumn("scale", "D");
                scannerController.setItems("scale", valueList);
                polyChart.refresh();
                scannerController.scanTable.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}
