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

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.tools.ScannerTool;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakPicker;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.tools.LigandScannerInfo;
import org.nmrfx.processor.tools.MatrixAnalyzer;
import org.nmrfx.structure.tools.MCSAnalysis;
import org.nmrfx.structure.tools.MCSAnalysis.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class MatrixAnalysisTool {
    private static final Logger log = LoggerFactory.getLogger(MatrixAnalysisTool.class);

    @FXML
    ScannerTool scannerTool;
    @FXML
    MatrixAnalyzer matrixAnalyzer = new MatrixAnalyzer();
    String[] dimNames = null;
    double[] mcsTols = null;
    double[] mcsAlphas = null;
    double mcsTol = 0.0;
    int refIndex = 0;
    PolyChart chart = PolyChartManager.getInstance().getActiveChart();
    int nPCA = 5;
    int nWidth = 10;

    public MatrixAnalysisTool(ScannerTool scannerTool) {
        this.scannerTool = scannerTool;
    }

    public void setRefIndex(int value) {
        refIndex = value;
    }

    public void setNWidth(int value) {
        nWidth = value;
    }

    public void addPCA() {
        for (int i = 0; i < 5; i++) {
            String columnName = "PCA" + (i + 1);
            scannerTool.getScanTable().addTableColumn(columnName, "D");
        }
        String columnName = "PCADelta";
        scannerTool.getScanTable().addTableColumn(columnName, "D");
    }

    public void refresh() {
        scannerTool.getScanTable().refresh();
    }

    public int setupDims() {
        Dataset dataset = chart.getDatasetAttributes().getFirst().getDataset();
        int nDatasets = chart.getDatasetAttributes().size();
        int nDataDim = dataset.getNDim();
        int nDim;
        if (nDatasets == 1) {
            nDim = nDataDim - 1;
        } else {
            nDim = nDataDim;
        }
        List<String> chartDimNames = chart.getDimNames().subList(0, nDim);
        dimNames = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            dimNames[i] = chartDimNames.get(i);
        }
        return nDim;
    }

    public void setupMCS() {
        int nDim = dimNames.length;
        mcsTols = new double[nDim];
        mcsAlphas = new double[nDim];
        for (int i = 0; i < nDim; i++) {
            // fixm need to set based on nucleus and/or in gui
            mcsTols[i] = 1.0;
            mcsAlphas[i] = 1.0;
            if (i > 0) {
                mcsTols[i] = 5.0;
                mcsAlphas[i] = 5.0;
            }
        }
    }

    public void setupBucket(List<FileTableItem> fileTableItems) {
        int nDim = setupDims();
        double[][] ppms = new double[nDim][2];
        int[] deltas = new int[nDim];
        if (chart.getCrossHairs().hasRegion()) {
            for (int i = 0; i < nDim; i++) {
                deltas[i] = nWidth;
                Double[] positions0 = chart.getCrossHairs().getPositions(0);
                Double[] positions1 = chart.getCrossHairs().getPositions(1);
                ppms[i][0] = positions0[i];
                ppms[i][1] = positions1[i];
            }
        } else {
            for (int i = 0; i < nDim; i++) {
                deltas[i] = nWidth;
                double[][] bounds = chart.getWorld();
                ppms[i][0] = bounds[i][0];
                ppms[i][1] = bounds[i][1];
            }
        }
        matrixAnalyzer.setup(fileTableItems.getFirst().getDatasetAttributes().getDataset(), dimNames, ppms, deltas);
        setupPCA(fileTableItems);
    }

    private void setupPCA(List<FileTableItem> fileTableItems) {
        List<LigandScannerInfo> ligandScannerInfos = new ArrayList<>();
        for (FileTableItem item : fileTableItems) {
            Dataset dataset = item.getDatasetAttributes().getDataset();
            Integer row = item.getRow();
            if (row == null) {
                row = 0;
            } else {
                row = row -1;
            }
            LigandScannerInfo scannerInfo = new LigandScannerInfo(dataset, row);
            ligandScannerInfos.add(scannerInfo);
        }
        matrixAnalyzer.setScannerRows(ligandScannerInfos);
    }

    public void doPCA(List<FileTableItem> scannerRows) {
        if (chart.getDatasetAttributes().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("No dataset in active window");
            alert.showAndWait();
            return;
        }
        double threshold;
        if (chart.is1D()) {
            boolean scaleToLargest = true;
            int nWin = 32;
            double maxRatio = 20.0;
            double sdRatio = 30.0;
            Dataset dataset = chart.getDatasetAttributes().getFirst().getDataset();
            threshold = PeakPicker.calculateThreshold(dataset, scaleToLargest, nWin, maxRatio, sdRatio);
            Analyzer analyzer = new Analyzer(chart.getDatasetAttributes().getFirst().getDataset());
            analyzer.calculateThreshold();
        } else {
             threshold = chart.getDatasetAttributes().getFirst().getLvl();
        }

        try {
            matrixAnalyzer.bucket2(threshold);
        } catch (IOException ex) {
            ExceptionDialog eDialog = new ExceptionDialog(ex);
            eDialog.showAndWait();
            return;
        }
        addPCA();
        double[][] pcaValues = matrixAnalyzer.doPCA2(nPCA);
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
        setupDims();
        setupMCS();
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
                }
                scannerRow.setExtra("MCS", score);
            }
        }
        refresh();
    }
}
