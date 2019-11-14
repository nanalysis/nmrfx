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

import java.util.List;
import java.util.Map;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 *
 * @author brucejohnson
 */
public class Phaser {

    String delImagString = "False";
    FXMLController controller;
    Slider[] sliders = new Slider[2];
    Label[] phLabels = new Label[2];
    double[] scales = {1, 8};

    public Phaser(FXMLController controller, VBox vbox) {
        this.controller = controller;
        initGUI(vbox);
    }

    public void initGUI(VBox vbox) {
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
            vbox.getChildren().addAll(label, slider, phLabels[iPh], sep);
        }

        MenuButton phaseMenuButton = new MenuButton("Phase");

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

        MenuItem autoPhaseFlat0Item = new MenuItem("AutoPhase 0");
        autoPhaseFlat0Item.setOnAction(e -> autoPhaseFlat0());

        MenuItem autoPhaseFlat01Item = new MenuItem("AutoPhase 0+1");
        autoPhaseFlat01Item.setOnAction(e -> autoPhaseFlat01());

        MenuItem autoPhaseMaxItem = new MenuItem("AutoPhase MaxMode");
        autoPhaseMaxItem.setOnAction(e -> autoPhaseMax());

        phaseMenuButton.getItems().addAll(setPhaseItem, getPhaseItem, setPivotItem,
                setPhase0_0Item, setPhase180_0Item, setPhase90_180Item,
                autoPhaseFlat0Item, autoPhaseFlat01Item, autoPhaseMaxItem);

        vbox.getChildren().add(phaseMenuButton);
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
            //chart.setPh0(sliderPH0);
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
//System.out.printf("ph0 %.3f ph1 %.3f delta %.3f pivotfr %.3f delta0 %.3f\n",sliderPH0, sliderPH1, deltaPH1, pivotFraction,(deltaPH1*pivotFraction));

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
            //chart.setPh0(sliderPH0);
            //chart.setPh1(sliderPH1);
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
        handlePh0Reset(ph0, true);
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
        handlePh1Reset(ph1, true);
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
            //handlePh1Reset(chart.getPh1());
            //handlePh0Reset(chart.getPh0());
        } else {
            chart.phaseDim = 0;
            handlePh1Reset(0.0);
            handlePh0Reset(0.0);
        }
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
        String phaseDim = String.valueOf(chart.phaseDim + 1);
        if (chart.hasData() && (controller.chartProcessor != null)) {
            if (chart.is1D()) {
                String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", ph0, ph1, delImagString);
                if (chart.processorController != null) {
                    setPhaseOp(opString);
                }
                chart.setPh0(0.0);
                chart.setPh1(0.0);
                chart.layoutPlotChildren();
            } else if (phaseDim.equals(controller.chartProcessor.getVecDimName().substring(1))) {
                //double newph0 = ph0 + chart.getDataPH0();
                //double newph1 = ph1 + chart.getDataPH1();
                double newph0 = ph0;
                double newph1 = ph1;
                double deltaPH0 = ph0 - chart.getDataPH0();
                double deltaPH1 = ph1 - chart.getDataPH1();

                String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", newph0, newph1, delImagString);
                if (chart.processorController != null) {
                    setPhaseOp(opString);
                }
                //chart.setPh0(ph0);
                //chart.setPh1(ph1);
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
        if (!chart.hasData()) {
            return;
        }
        String phaseDim = "D" + String.valueOf(chart.phaseDim + 1);
        if (controller.chartProcessor != null) {
            List<String> listItems = controller.chartProcessor.getOperations(phaseDim);
            if (listItems != null) {
                Map<String, String> values = null;
                for (String s : listItems) {
                    if (s.contains("PHASE")) {
                        values = PropertyManager.parseOpString(s);
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
                    }
                }
            }
        }
        setPH1Slider(ph1);
        setPH0Slider(ph0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
    }

    private void setPhasePivot() {
        controller.getActiveChart().setPhasePivot();
    }

    private void autoPhaseFlat0() {
        controller.getActiveChart().autoPhaseFlat(false);
    }

    private void autoPhaseFlat01() {
        controller.getActiveChart().autoPhaseFlat(true);
    }

    private void autoPhaseMax() {
        controller.getActiveChart().autoPhaseMax();
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
