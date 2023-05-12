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
package org.nmrfx.processor.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.operations.AutoPhase;
import org.nmrfx.processor.operations.IDBaseline2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author brucejohnson
 */
public class Phaser {

    private static final Logger log = LoggerFactory.getLogger(Phaser.class);
    String delImagString = "False";
    FXMLController controller;
    Slider[] sliders = new Slider[2];
    Label[] phLabels = new Label[2];
    double[] scales = {1, 8};
    SimpleStringProperty phaseChoice = new SimpleStringProperty("X");
    ChoiceBox xyPhaseChoice;
    List<MenuItem> processorMenuItems = new ArrayList<>();
    List<MenuItem> datasetMenuItems = new ArrayList<>();
    MenuButton phaseMenuButton = null;

    public Phaser(FXMLController controller, VBox vbox) {
        this.controller = controller;
        initGUI(vbox);
    }

    private void initGUI(VBox vbox) {
        for (int iPh = 0; iPh < 2; iPh++) {
            final int phMode = iPh;
            Label label = new Label("PH" + iPh);
            Slider slider = new Slider();
            slider.setBlockIncrement(1.0 * scales[iPh]);
            slider.setMajorTickUnit(15.0 * scales[iPh]);
            slider.setMin(-45.0 * scales[iPh]);
            slider.setMax(45.0 * scales[iPh]);
            slider.setMinorTickCount(3);
            slider.setShowTickMarks(true);
            slider.setShowTickLabels(true);
            slider.setOrientation(Orientation.VERTICAL);
            slider.valueProperty().addListener(e -> handlePh(phMode));
            slider.setOnMouseReleased(e -> handlePhReset(phMode));
            sliders[iPh] = slider;
            VBox.setVgrow(slider, Priority.ALWAYS);
            Separator sep = new Separator();
            phLabels[iPh] = new Label();
            vbox.getChildren().addAll(label, slider, phLabels[iPh]);
            if (iPh == 0) {
                Pane filler = new Pane();
                filler.setMinHeight(20);
                vbox.getChildren().add(filler);
            }
        }
        xyPhaseChoice = new ChoiceBox();
        xyPhaseChoice.getItems().addAll("X", "Y");
        vbox.getChildren().add(xyPhaseChoice);
        xyPhaseChoice.valueProperty().bindBidirectional(phaseChoice);

        phaseMenuButton = new MenuButton("Phase");

        MenuItem setPhaseItem = new MenuItem("Put Phases");
        setPhaseItem.setOnAction(e -> setPhaseOp());

        MenuItem getPhaseItem = new MenuItem("Get Phases");
        getPhaseItem.setOnAction(e -> getPhaseOp());

        MenuItem setPivotItem = new MenuItem("Set Pivot");
        setPivotItem.setOnAction(e -> setPhasePivot());

        MenuItem setPhase0_0Item = new MenuItem("0,0");
        setPhase0_0Item.setOnAction(e -> setPhase_0_0());

        MenuItem setPhase180_0Item = new MenuItem("180,0");
        setPhase180_0Item.setOnAction(e -> setPhase_180_0());

        MenuItem setPhase90_180Item = new MenuItem("-90,180");
        setPhase90_180Item.setOnAction(e -> setPhase_minus90_180());

        MenuItem autoPhase0Item = new MenuItem("AutoPhase 0");
        autoPhase0Item.setOnAction(e -> autoPhase0());

        MenuItem autoPhase01Item = new MenuItem("AutoPhase 0+1");
        autoPhase01Item.setOnAction(e -> autoPhase01());

        MenuItem autoPhaseMaxItem = new MenuItem("AutoPhase MaxMode");
        autoPhaseMaxItem.setOnAction(e -> autoPhaseMax());

        MenuItem applyPhaseItem = new MenuItem("Apply Phase");
        applyPhaseItem.setOnAction(e -> applyPhase());

        MenuItem autoPhaseDataset0Item = new MenuItem("Auto Phase 0");
        autoPhaseDataset0Item.setOnAction(e -> autoPhaseDataset0());

        MenuItem autoPhaseDataset01Item = new MenuItem("Auto Phase 0/1");
        autoPhaseDataset01Item.setOnAction(e -> autoPhaseDataset01());

        MenuItem resetPhaseItem = new MenuItem("Reset Phases");
        resetPhaseItem.setOnAction(e -> resetPhases());

        Collections.addAll(processorMenuItems, setPhaseItem, getPhaseItem, setPivotItem,
                setPhase0_0Item, setPhase180_0Item, setPhase90_180Item,
                autoPhase0Item, autoPhase01Item, autoPhaseMaxItem);

        Collections.addAll(datasetMenuItems, setPivotItem, applyPhaseItem,
                autoPhaseDataset0Item, autoPhaseDataset01Item, resetPhaseItem);

        phaseMenuButton.getItems().addAll(datasetMenuItems);

        vbox.getChildren().add(phaseMenuButton);
        phaseChoice.addListener(e -> setChartPhaseDim());
        controller.processControllerVisible.addListener(e -> resetMenus(controller.processControllerVisible));
    }

    void resetMenus(SimpleBooleanProperty procVis) {
        if (procVis.get()) {
            phaseMenuButton.getItems().setAll(processorMenuItems);
            xyPhaseChoice.setVisible(false);
        } else {
            phaseMenuButton.getItems().setAll(datasetMenuItems);
            xyPhaseChoice.setVisible(true);
        }
    }

    void setChartPhaseDim() {
        PolyChart chart = controller.getActiveChart();
        chart.setPhaseDim(phaseChoice.get().equals("X") ? 0 : 1);
        if ((controller.chartProcessor == null) || !controller.processControllerVisible.get()) {
            setPH1Slider(chart.getDataPH1());
            setPH0Slider(chart.getDataPH0());
        }
    }

    void handlePhReset(int iPh) {
        if (iPh == 0) {
            handlePh0Reset();
        } else {
            handlePh1Reset();
        }

    }

    private void handlePh(int iPh) {
        if (iPh == 0) {
            handlePh0();
        } else {
            handlePh1();
        }
    }

    private void handlePh0() {
        double sliderPH0 = sliders[0].getValue();
        sliderPH0 = Math.round(sliderPH0 * 10) / 10.0;
        phLabels[0].setText(String.format("%.1f", sliderPH0));
        double deltaPH0 = 0.0;
        PolyChart chart = controller.getActiveChart();
        if (controller.getActiveChart().hasData()) {
            deltaPH0 = sliderPH0 - chart.getDataPH0();
        }
        if (chart.is1D()) {
            chart.setPh0(deltaPH0);
            chart.layoutPlotChildren();
        } else {
            chart.setPh0(deltaPH0);
            chart.getCrossHairs().refreshCrossHairs();
        }
    }

    private void handlePh1() {
        PolyChart chart = controller.getActiveChart();
        double sliderPH0 = sliders[0].getValue();
        double sliderPH1 = sliders[1].getValue();
        double deltaPH1 = 0.0;
        if (chart.hasData()) {
            deltaPH1 = sliderPH1 - (chart.getDataPH1() + chart.getPh1());
        }
        double pivotFraction = chart.getPivotFraction();
        sliderPH0 = sliderPH0 - deltaPH1 * pivotFraction;

        sliderPH0 = Math.round(sliderPH0 * 10) / 10.0;
        sliderPH1 = Math.round(sliderPH1 * 10) / 10.0;

        setPH0Slider(sliderPH0);
        double deltaPH0 = 0.0;
        deltaPH1 = 0.0;
        if (chart.hasData()) {
            deltaPH0 = sliderPH0 - chart.getDataPH0();
            deltaPH1 = sliderPH1 - chart.getDataPH1();
        }

        phLabels[0].setText(String.format("%.1f", sliderPH0));
        phLabels[1].setText(String.format("%.1f", sliderPH1));

        if (chart.is1D()) {
            chart.setPh0(deltaPH0);
            chart.setPh1(deltaPH1);
            chart.layoutPlotChildren();
        } else {
            chart.setPh0(deltaPH0);
            chart.setPh1(deltaPH1);
            chart.getCrossHairs().refreshCrossHairs();
        }
    }

    private void handlePh0Reset() {
        double ph0 = sliders[0].getValue();
        handlePh0Reset(ph0);
    }

    public void handlePh0Reset(double ph0) {
        handlePh0Reset(ph0, controller.processControllerVisible.get());
    }

    public void handlePh0Reset(double ph0, boolean updateOp) {
        ph0 = Math.round(ph0 * 10) / 10.0;
        double halfRange = 22.5;
        double start = halfRange * Math.round(ph0 / halfRange) - 2.0 * halfRange;
        double end = start + 4 * halfRange;
        sliders[0].setMin(start);
        sliders[0].setMax(end);
        sliders[0].setBlockIncrement(0.1);
        sliders[0].setValue(ph0);
        phLabels[0].setText(String.format("%.1f", ph0));
        if (updateOp) {
            setPhaseOp();
        }
    }

    public void setPhaseLabels(double ph0, double ph1) {
        ph0 = Math.round(ph0 * 10) / 10.0;
        ph1 = Math.round(ph1 * 10) / 10.0;
        phLabels[0].setText(String.format("%.1f", ph0));
        phLabels[1].setText(String.format("%.1f", ph1));
    }

    private void handlePh1Reset() {
        double ph1 = sliders[1].getValue();
        handlePh1Reset(ph1);
    }

    void handlePh1Reset(double ph1) {
        handlePh1Reset(ph1, controller.processControllerVisible.get());
    }

    void handlePh1Reset(double ph1, boolean updateOp) {
        ph1 = Math.round(ph1 * 10) / 10.0;
        double start = 90.0 * Math.round(ph1 / 90.0) - 180.0;
        double end = start + 360.0;
        sliders[1].setMin(start);
        sliders[1].setMax(end);
        sliders[1].setValue(ph1);
        phLabels[1].setText(String.format("%.1f", ph1));
        if (updateOp) {
            setPhaseOp();
        }
    }

    protected void setPH0Slider(double value) {
        value = Math.round(value * 10) / 10.0;
        double halfRange = 22.5;
        double start = halfRange * Math.round(value / halfRange) - 2.0 * halfRange;
        double end = start + 4 * halfRange;
        sliders[0].setMin(start);
        sliders[0].setMax(end);
        sliders[0].setValue(value);
    }

    protected void setPH1Slider(double value) {
        value = Math.round(value * 10) / 10.0;
        double start = 90.0 * Math.round(value / 90.0) - 180.0;
        double end = start + 360.0;
        sliders[1].setMin(start);
        sliders[1].setMax(end);
        sliders[1].setValue(value);
    }

    protected void setPhaseDim(int phaseDim) {
        PolyChart chart = controller.getActiveChart();
        if (phaseDim >= 0) {
            chart.setPhaseDim(phaseDim);
            getPhaseOp();
        } else {
            chart.datasetPhaseDim = 0;
            handlePh1Reset(0.0);
            handlePh0Reset(0.0);
        }
        phaseChoice.set(chart.phaseAxis == 0 ? "X" : "Y");
    }

    public void setPhaseOp(String opString) {
        PolyChart chart = controller.getActiveChart();
        int opIndex = chart.processorController.propertyManager.setOp(opString);
        chart.processorController.propertyManager.setPropSheet(opIndex, opString);
    }

    public void setPhaseOp() {
        PolyChart chart = controller.getActiveChart();
        double ph0 = sliders[0].getValue();
        double ph1 = sliders[1].getValue();
        String phaseDim = String.valueOf(chart.datasetPhaseDim + 1);
        if (chart.hasData() && (controller.chartProcessor != null)) {
            if (chart.is1D()) {
                List<String> listItems = controller.chartProcessor.getOperations("D" + phaseDim);
                if (listItems != null) {
                    Map<String, String> values = null;
                    for (String s : listItems) {
                        if (s.contains("AUTOPHASE")) {
                            double aph0 = AutoPhase.lastPh0.get();
                            double aph1 = AutoPhase.lastPh1.get();
                            ph0 -= aph0;
                            ph1 -= aph1;
                        }
                    }
                }
                String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", ph0, ph1, delImagString);
                if (chart.processorController != null) {
                    setPhaseOp(opString);
                }
                chart.setPh0(0.0);
                chart.setPh1(0.0);
                chart.layoutPlotChildren();
            } else if (phaseDim.equals(controller.chartProcessor.getVecDimName().substring(1))) {
                double newph0 = ph0;
                double newph1 = ph1;
                double deltaPH0 = ph0 - chart.getDataPH0();
                double deltaPH1 = ph1 - chart.getDataPH1();

                String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", newph0, newph1, delImagString);
                if (chart.processorController != null) {
                    setPhaseOp(opString);
                }
                chart.setPh0(deltaPH0);
                chart.setPh1(deltaPH1);
                chart.getCrossHairs().refreshCrossHairs();
            }
        }
    }

    protected void getPhaseOp() {
        PolyChart chart = controller.getActiveChart();
        double ph0 = 0.0;
        double ph1 = 0.0;
        double aph0 = 0.0;
        double aph1 = 0.0;
        if (!chart.hasData()) {
            return;
        }
        DatasetBase dataset = chart.getDataset();
        String phaseDim = "D" + String.valueOf(chart.datasetPhaseDim + 1);
        if (controller.chartProcessor != null) {
            List<String> listItems = controller.chartProcessor.getOperations(phaseDim);
            if (listItems != null) {
                Map<String, String> values = null;
                for (String s : listItems) {
                    if (s.contains("PHASE")) {
                        values = PropertyManager.parseOpString(s);
                    }
                    if (s.contains("AUTOPHASE")) {
                        aph0 = AutoPhase.lastPh0.get();
                        aph1 = AutoPhase.lastPh1.get();
                    }
                }
                if (values != null) {
                    try {
                        if (values.containsKey("ph0")) {
                            String value = values.get("ph0");
                            ph0 = Double.parseDouble(value);
                        } else {
                            ph0 = 0.0;
                        }
                        if (values.containsKey("ph1")) {
                            String value = values.get("ph1");
                            ph1 = Double.parseDouble(value);
                        } else {
                            ph1 = 0.0;
                        }
                        if (values.containsKey("dimag")) {
                            String value = values.get("dimag");
                            delImagString = value;
                        } else {
                            delImagString = "False";
                        }
                    } catch (NumberFormatException nfE) {
                        log.warn("Unable to parse phase.", nfE);
                    }
                }
            }
        }

        setPH1Slider(ph1 + aph1);
        setPH0Slider(ph0 + aph0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
    }

    private void setPhasePivot() {
        controller.getActiveChart().setPhasePivot();
        controller.getActiveChart().drawSlices();
    }

    private void autoPhase0() {
        controller.getActiveChart().autoPhase(false, false);
    }

    private void autoPhase01() {
        controller.getActiveChart().autoPhase(false,true);
    }

    private void autoPhaseMax() {
        controller.getActiveChart().autoPhase(true, false);
    }

    private void applyPhase() {
        PolyChart chart = controller.getActiveChart();
        DatasetBase datasetBase = chart.getDataset();
        Dataset dataset = (Dataset) datasetBase;
        try {
            int iDim = chart.datasetPhaseDim;
            double ph0 = chart.getPh0();
            double ph1 = chart.getPh1();
            dataset.phaseDim(iDim, ph0, ph1);
            chart.setPh0(0.0);
            chart.setPh1(0.0);
            chart.refresh();
        } catch (IOException ex) {
            ExceptionDialog d = new ExceptionDialog(ex);
            d.showAndWait();
        }
    }

    private void autoPhaseDataset0() {
        autoPhaseDataset(false);
    }

    private void autoPhaseDataset01() {
        autoPhaseDataset(true);
    }

    private void autoPhaseDataset(boolean firstOrder) {
        PolyChart chart = controller.getActiveChart();
        DatasetBase datasetBase = chart.getDataset();
        if (!(datasetBase instanceof Dataset)) {

        }
        Dataset dataset = (Dataset) datasetBase;
        double ratio = 25.0;
        IDBaseline2.ThreshMode threshMode = IDBaseline2.ThreshMode.SDEV;

        int iDim = chart.datasetPhaseDim;
        int winSize = 2;
        double ph1Limit = 90.0;
        try {
            double[] phases = dataset.autoPhase(iDim, firstOrder, winSize, ratio, ph1Limit, threshMode);
            chart.setPh0(0.0);
            chart.setPh1(0.0);
            chart.refresh();
        } catch (IOException ioE) {
            ExceptionDialog d = new ExceptionDialog(ioE);
            d.showAndWait();
        }
    }

    private void resetPhases() {
        PolyChart chart = controller.getActiveChart();
        DatasetBase dataset = chart.getDataset();
        for (int i = 0; i < dataset.getNDim(); i++) {
            dataset.setPh0(i, 0.0);
            dataset.setPh0_r(i, 0.0);
            dataset.setPh1(i, 0.0);
            dataset.setPh1_r(i, 0.0);
        }
        setPH1Slider(0.0);
        setPH0Slider(0.0);
        chart.resetChartPhases();
    }

    private void setPhase_minus90_180() {
        PolyChart chart = controller.getActiveChart();
        String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", -90.0, 180.0, delImagString);
        setPhaseOp(opString);
        setPH1Slider(180.0);
        setPH0Slider(-90.0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
        chart.layoutPlotChildren();
    }

    private void setPhase_0_0() {
        PolyChart chart = controller.getActiveChart();
        String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", 0.0, 0.0, delImagString);
        setPhaseOp(opString);
        setPH1Slider(0.0);
        setPH0Slider(0.0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
        chart.layoutPlotChildren();
    }

    private void setPhase_180_0() {
        PolyChart chart = controller.getActiveChart();
        String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", 180.0, 0.0, delImagString);
        setPhaseOp(opString);
        setPH1Slider(0.0);
        setPH0Slider(180.0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
        chart.layoutPlotChildren();
    }

}
