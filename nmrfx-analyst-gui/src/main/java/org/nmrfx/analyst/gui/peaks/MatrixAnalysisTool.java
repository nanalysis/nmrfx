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
package org.nmrfx.analyst.gui.peaks;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.tools.ScannerTool;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.tools.MatrixAnalyzer;
import org.nmrfx.structure.tools.MCSAnalysis;
import org.nmrfx.structure.tools.MCSAnalysis.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class MatrixAnalysisTool {
    private static final Logger log = LoggerFactory.getLogger(MatrixAnalysisTool.class);

    private Stage stage;

    @FXML
    ScannerTool scannerTool;
    @FXML
    ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
    MatrixAnalyzer matrixAnalyzer = new MatrixAnalyzer();
    String[] dimNames = null;
    double[] mcsTols = null;
    double[] mcsAlphas = null;
    double mcsTol = 0.0;
    int refIndex = 0;
    PolyChart chart = PolyChartManager.getInstance().getActiveChart();
    int nPCA = 5;

    public MatrixAnalysisTool(ScannerTool scannerTool) {
        this.scannerTool = scannerTool;
    }

    public void addPCA() {
        for (int i = 0; i < 5; i++) {
            final int pcaIndex = i;
            String columnName = "PCA" + (pcaIndex + 1);
            scannerTool.getScanTable().addTableColumn(columnName, "D");
        }
        String columnName = "PCADelta";
        scannerTool.getScanTable().addTableColumn(columnName, "D");
    }

    public void refresh() {
        scannerTool.getScanTable().refresh();
    }


    public void setupBucket() {
        Dataset dataset = chart.getDatasetAttributes().getFirst().getDataset();
        int nDatasets = chart.getDatasetAttributes().size();
        int nDataDim = dataset.getNDim();
        int nDim = 0;
        if (nDatasets == 1) {
            nDim = nDataDim - 1;
        } else {
            nDim = nDataDim;
        }
        List<String> chartDimNames = chart.getDimNames().subList(0, nDim);
        dimNames = new String[nDim];
        mcsTols = new double[nDim];
        mcsAlphas = new double[nDim];
        double[][] ppms = new double[nDim][2];
        int[] deltas = new int[nDim];
        if (!chart.getCrossHairs().hasRegion()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("No crosshair region");
            alert.showAndWait();
            return;
        }

        for (int i = 0; i < nDim; i++) {
            dimNames[i] = chartDimNames.get(i);
            deltas[i] = 10;
            Double[] positions0 = chart.getCrossHairs().getPositions(0);
            Double[] positions1 = chart.getCrossHairs().getPositions(1);
            ppms[i][0] = positions0[i];
            ppms[i][1] = positions1[i];
            // fixm need to set based on nucleus and/or in gui
            mcsTols[i] = 1.0;
            mcsAlphas[i] = 1.0;
            if (i > 0) {
                mcsTols[i] = 5.0;
                mcsAlphas[i] = 5.0;
            }
        }
        var datasets = scannerTool.getScanTable().getItems().stream().map(item -> item.getDatasetAttributes().getDataset()).distinct().toList();
        matrixAnalyzer.setup(datasets.getFirst(), dimNames, ppms, deltas);
        matrixAnalyzer.setDatasets(datasets);
    }

    public void doPCA() {
        if (chart.getDatasetAttributes().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("No dataset in active window");
            alert.showAndWait();
            return;
        }
        double threshold = chart.getDatasetAttributes().get(0).getLvl();
        threshold = 0.002;
        try {
            matrixAnalyzer.bucket(threshold);
        } catch (IOException ex) {
            ExceptionDialog eDialog = new ExceptionDialog(ex);
            eDialog.showAndWait();
            return;
        }
        addPCA();
        double[][] pcaValues = matrixAnalyzer.doPCA(nPCA);
        List<FileTableItem> scannerRows = scannerTool.getScanTable().getItems();
        double[] pcaDists = matrixAnalyzer.getPCADelta(refIndex, 2);
        int iRow = 0;
        for (FileTableItem scannerRow : scannerRows) {
            for (int j = 0; j < pcaValues.length; j++) {
                double pca = pcaValues[j][iRow];
                scannerRow.setExtra("PCA"+(j+1), pca);
            }
            scannerRow.setExtra("PCADelta",pcaDists[iRow]);
            iRow++;
        }
        refresh();
    }

    public void doMCS() {
        List<FileTableItem> scannerRows = scannerTool.getScanTable().getItems();
        if (!scannerRows.isEmpty()) {
            for (FileTableItem scannerRow : scannerRows) {
                Dataset dataset = scannerRow.getDatasetAttributes().getDataset();
                PeakList peakList = PeakList.getPeakListForDataset(dataset.getName());
                if (peakList == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("No peakList");
                    alert.showAndWait();
                    return;
                }
            }
            String columnName = "MCS";
            scannerTool.getScanTable().addTableColumn(columnName, "D");

            FileTableItem refInfo = scannerRows.get(refIndex);
            Dataset refDataset = refInfo.getDatasetAttributes().getDataset();
            PeakList refPeakList = PeakList.getPeakListForDataset(refDataset.getName());
            for (FileTableItem scannerRow : scannerRows) {
                Dataset dataset = scannerRow.getDatasetAttributes().getDataset();
                PeakList peakList = PeakList.getPeakListForDataset(dataset.getName());
                if (peakList == null) {
                    break;
                }
                double score = 0.0;
                if (peakList != refPeakList) {
                    MCSAnalysis mcsAnalysis = new MCSAnalysis(peakList, mcsTols, mcsAlphas, dimNames, refPeakList);
                    List<Hit> hits = mcsAnalysis.calc();
                    score = mcsAnalysis.score(hits, mcsTol);
                    System.out.println(hits.size() + " " + score);
                }
                scannerRow.setExtra("MCS", score);
            }
        }
        refresh();
    }
}
