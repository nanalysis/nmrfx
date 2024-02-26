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

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;

import java.util.*;

/**
 * @author brucejohnson
 */
public class SpectrumAdjuster {
    private static final Map<String, Double> datasetUndo = new HashMap<>();
    private static final Map<String, Double> peakUndo = new HashMap<>();
    private static String undoChartName;

    public static void showRefInput() {
        PolyChart chart = PolyChartManager.getInstance().getActiveChart();
        int nDim = 1;
        if (chart.getNDim() > 1) {
            nDim = 2;
        }

        Stage stage = new Stage(StageStyle.UNDECORATED);
        GridPane grid = new GridPane();
        Scene scene = new Scene(grid);
        stage.setScene(scene);
        String[] labels = {"X", "Y"};
        List<TextField> textFields = new ArrayList<>();
        Label msg = new Label("Enter shifts at crosshair");
        Button cancelButton = new Button("Cancel");
        Button applyButton = new Button("Apply");
        grid.add(msg, 0, 0, 2, 1);
        for (int i = 0; i < nDim; i++) {
            Label label = new Label(" " + labels[i] + " Position:");
            grid.add(label, 0, i + 1);
            TextField textField = new TextField();
            grid.add(textField, 1, i + 1);
            textFields.add(textField);
            textField.textProperty().addListener(e -> {
                checkChange(applyButton, textField);
            });
        }
        CheckBox saveParButton = new CheckBox("Save Par File");
        saveParButton.setSelected(true);
        CheckBox shiftPeaksButton = new CheckBox("Shift Peaks");
        shiftPeaksButton.setSelected(true);
        grid.add(saveParButton, 0, nDim + 1);
        grid.add(shiftPeaksButton, 1, nDim + 1);
        grid.add(cancelButton, 0, nDim + 2);
        grid.add(applyButton, 1, nDim + 2);
        cancelButton.setOnAction(e -> stage.close());
        applyButton.setOnAction(e -> {
            setReference(chart, textFields, saveParButton.isSelected(), shiftPeaksButton.isSelected());
            stage.close();
        });
        stage.show();
    }

    private static void checkChange(Button applyButton, TextField textField) {
        try {
            String s = textField.getText();
            s = s.trim();
            double value = Double.parseDouble(s);
            applyButton.setDisable(false);
        } catch (NumberFormatException nfE) {
            applyButton.setDisable(true);
        }

    }

    public static void setReference(PolyChart chart, List<TextField> textFields, boolean savePars, boolean shiftPeaks) {
        int iDim = 0;
        datasetUndo.clear();
        peakUndo.clear();
        for (TextField textField : textFields) {
            String s = textField.getText();
            s = s.trim();
            if (s.length() != 0) {
                try {
                    double newRef = Double.parseDouble(s);
                    processReference(chart, iDim, newRef, shiftPeaks);
                } catch (NumberFormatException nfE) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("Invalid shift format");
                    alert.showAndWait();
                    return;

                }
            }
            iDim++;

        }
        if (savePars) {
            writePars(chart);
        }
        chart.refresh();

    }

    public static void writePars() {
        PolyChart chart = PolyChartManager.getInstance().getActiveChart();
        writePars(chart);
    }

    private static void writePars(PolyChart chart) {
        for (DatasetAttributes dataAttr : chart.getDatasetAttributes()) {
            DatasetBase dataset = dataAttr.getDataset();
            dataset.writeParFile();
        }

    }

    public static void processReference(PolyChart chart, int iDim, double newPos, boolean shiftPeaks) {
        Double[] cPos = chart.getCrossHairs().getPositions(0);
        double oldPos = cPos[iDim];
        double delta = newPos - oldPos;
        undoChartName = chart.getName();
        for (DatasetAttributes dataAttr : chart.getDatasetAttributes()) {
            DatasetBase dataset = dataAttr.getDataset();
            if (iDim < dataset.getNDim()) {
                int dataDim = dataAttr.getDim(iDim);
                double oldRef = dataset.getRefValue(dataDim);
                datasetUndo.put(dataset.getName() + ":" + dataDim, oldRef);
                double newRef = oldRef + delta;
                dataset.setRefValue(dataAttr.getDim(iDim), newRef);
            }
        }
        if (shiftPeaks) {
            for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
                PeakList peakList = peakAttr.getPeakList();
                int[] peakDim = peakAttr.getPeakDim();
                peakList.shiftPeak(peakDim[iDim], delta);
                peakUndo.put(peakList.getName() + ":" + peakDim[iDim], delta);
            }
        }
    }

    public static void adjustDatasetRef() {
        adjustDatasetRef(Optional.empty(), Optional.empty(), true, false);
    }

    public static void shiftPeaks() {
        adjustDatasetRef(Optional.empty(), Optional.empty(), false, true);

    }

    public static void adjustDatasetRef(Optional<Double> delXOpt,
                                        Optional<Double> delYOpt, boolean shiftDataset, boolean alwaysShiftPeaks) {
        PolyChart chart = PolyChartManager.getInstance().getActiveChart();
        CrossHairs crossHairs = chart.getCrossHairs();
        int nDim = 1;
        if (chart.getNDim() > 1) {
            nDim = 2;
        }
        Double[] c0 = crossHairs.getPositions(0);
        Double[] c1 = crossHairs.getPositions(1);

        double[] deltas = {0.0, 0.0};
        boolean gotShifts = false;
        if (crossHairs.hasState("||") || delXOpt.isPresent()) {
            deltas[0] = delXOpt.isPresent() ? delXOpt.get() : c1[0] - c0[0];
            gotShifts = true;
        }
        if ((nDim > 1) && (crossHairs.hasState("=") || delYOpt.isPresent())) {
            deltas[1] = delYOpt.isPresent() ? delXOpt.get() : c1[1] - c0[1];
            gotShifts = true;
        }
        if (gotShifts) {
            datasetUndo.clear();
            peakUndo.clear();
            undoChartName = chart.getName();
            if (shiftDataset) {
                for (DatasetAttributes dataAttr : chart.getDatasetAttributes()) {
                    DatasetBase dataset = dataAttr.getDataset();
                    for (int i = 0; i < nDim; i++) {
                        int dataDim = dataAttr.getDim(i);
                        double ref = dataset.getRefValue(dataDim);
                        datasetUndo.put(dataset.getName() + ":" + dataDim, ref);
                        double newRef = ref - deltas[i];
                        dataset.setRefValue(dataAttr.getDim(i), newRef);
                    }
                }
            }
            if (alwaysShiftPeaks || shiftPeaks(chart)) {
                for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
                    PeakList peakList = peakAttr.getPeakList();
                    int[] peakDim = peakAttr.getPeakDim();
                    for (int i = 0; i < nDim; i++) {
                        peakList.shiftPeak(peakDim[i], -deltas[i]);
                        peakUndo.put(peakList.getName() + ":" + peakDim[i], -deltas[i]);
                    }
                }
            }
            if (shiftDataset && saveParFiles()) {
                writePars(chart);
            }
            chart.refresh();
        }
    }

    public static void adjustDiagonalReference() {
        PolyChart chart = PolyChartManager.getInstance().getActiveChart();
        if (chart.getNDim() < 2) {
            return;
        }
        CrossHairs crossHairs = chart.getCrossHairs();
        if (!crossHairs.hasState("|_")) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Need a vertical and horizontal crosshair");
            alert.showAndWait();
            return;
        }
        Double[] pos = crossHairs.getPositions();

        double delta = pos[0] - pos[1];
        boolean ok = true;
        if (Math.abs(delta) > 0.5) {
            String message = String.format("Changing reference by a lot (%.3f), Continue?", delta);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message);
            Optional<ButtonType> response = alert.showAndWait();
            if (response.isPresent() && !response.get().getText().equals("OK")) {
                ok = false;
            }
        }
        if (ok) {
            datasetUndo.clear();
            peakUndo.clear();
            undoChartName = chart.getName();
            chart.getDatasetAttributes().forEach((dataAttr) -> {
                int dataDim = dataAttr.dim[1];
                DatasetBase dataset = dataAttr.getDataset();
                double oldRef = dataset.getRefValue(dataDim);
                datasetUndo.put(dataset.getName() + ":" + dataDim, oldRef);
                dataAttr.getDataset().setRefValue(dataDim, oldRef + delta);
            });
            if (shiftPeaks(chart)) {
                for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
                    PeakList peakList = peakAttr.getPeakList();
                    int[] peakDim = peakAttr.getPeakDim();
                    peakList.shiftPeak(peakDim[1], delta);
                    peakUndo.put(peakList.getName() + ":" + peakDim[1], delta);
                }
            }

            chart.refresh();
            if (saveParFiles()) {
                writePars(chart);
            }
        }
    }

    public static void undo() {
        Optional<PolyChart> chartOpt = PolyChartManager.getInstance().findChartByName(undoChartName);
        if (chartOpt.isPresent()) {
            for (Map.Entry<String, Double> entry : datasetUndo.entrySet()) {
                String[] fields = entry.getKey().split(":");
                Dataset dataset = Dataset.getDataset(fields[0]);
                int iDim = Integer.valueOf(fields[1]);
                dataset.setRefValue(iDim, entry.getValue());
            }
            for (Map.Entry<String, Double> entry : peakUndo.entrySet()) {
                String[] fields = entry.getKey().split(":");
                PeakList peakList = (PeakList) PeakList.get(fields[0]);
                int iDim = Integer.valueOf(fields[1]);
                peakList.shiftPeak(iDim, -entry.getValue());
            }
            chartOpt.get().refresh();
            datasetUndo.clear();
            peakUndo.clear();
        }
    }

    private static boolean saveParFiles() {
        Alert saveParsAlert = new Alert(Alert.AlertType.CONFIRMATION, "Save Dataset Parameter File");
        Optional<ButtonType> saveResponse = saveParsAlert.showAndWait();
        return saveResponse.isPresent() && saveResponse.get() == ButtonType.OK;
    }

    private static boolean shiftPeaks(PolyChart chart) {
        boolean result = !chart.getPeakListAttributes().isEmpty();
        if (result) {
            Alert shiftPeaksAlert = new Alert(Alert.AlertType.CONFIRMATION, "Shift Peaks");
            Optional<ButtonType> shiftResponse = shiftPeaksAlert.showAndWait();
            result = shiftResponse.isPresent() && shiftResponse.get() == ButtonType.OK;
        }
        return result;
    }
}
