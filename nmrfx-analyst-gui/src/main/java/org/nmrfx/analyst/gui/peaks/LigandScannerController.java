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

import javafx.beans.Observable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.tools.ScannerTool;
import org.nmrfx.chart.*;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
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
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * @author Bruce Johnson
 */
public class LigandScannerController implements Initializable, StageBasedController {
    private static final Logger log = LoggerFactory.getLogger(LigandScannerController.class);

    private Stage stage;

    @FXML
    SplitPane splitPane;
    ScannerTool scannerTool;
    XYChartPane chartPane;
    @FXML
    private ToolBar menuBar;
    ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
    MatrixAnalyzer matrixAnalyzer = new MatrixAnalyzer();
    String[] dimNames = null;
    double[] mcsTols = null;
    double[] mcsAlphas = null;
    double mcsTol = 0.0;
    int refIndex = 0;
    PolyChart chart = PolyChartManager.getInstance().getActiveChart();
    XYCanvasChart activeChart = null;
    ChoiceBox<String> xArrayChoice;
    ChoiceBox<String> yArrayChoice;
    int nPCA = 5;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        chartPane = new XYChartPane();
        splitPane.getItems().addAll(chartPane);
        activeChart = chartPane.getChart();

        initMenuBar();
        initTable();
        try {
            xArrayChoice.valueProperty().addListener((Observable x) -> {
                updatePlot();
            });
            yArrayChoice.valueProperty().addListener((Observable y) -> {
                updatePlot();
            });
        } catch (NullPointerException npEmc1) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error: Fit must first be performed.");
            alert.showAndWait();
        }
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static LigandScannerController create(ScannerTool scannerTool) {
        LigandScannerController controller = Fxml.load(LigandScannerController.class, "LigandScannerScene.fxml")
                .withNewStage("Ligand Scanner")
                .getController();
        controller.initScanner(scannerTool);
        controller.stage.show();
        return controller;
    }

    void initScanner(ScannerTool scannerTool) {
        this.scannerTool = scannerTool;
    }

    void initMenuBar() {
        MenuButton fileMenu = new MenuButton("File");
        MenuItem readScannerTableItem = new MenuItem("Read Table...");
        menuBar.getItems().add(fileMenu);
        Button setupButton = new Button("Setup");
        setupButton.setOnAction(e -> setupBucket());
        menuBar.getItems().add(setupButton);
        Button pcaButton = new Button("PCA");
        pcaButton.setOnAction(e -> doPCA());
        menuBar.getItems().add(pcaButton);
        Button mcsButton = new Button("MCS");
        mcsButton.setOnAction(e -> doMCS());

        menuBar.getItems().add(mcsButton);
        xArrayChoice = new ChoiceBox<>();
        yArrayChoice = new ChoiceBox<>();
        menuBar.getItems().addAll(xArrayChoice, yArrayChoice);

    }

    private void initTable() {
        TableColumn<LigandScannerInfo, String> datasetColumn = new TableColumn<>("Dataset");
        TableColumn<LigandScannerInfo, Integer> indexColumn = new TableColumn<>("Index");
        TableColumn<LigandScannerInfo, Integer> nPeaksColumn = new TableColumn<>("nPks");
        TableColumn<LigandScannerInfo, Double> minShiftColumn = new TableColumn<>("MinShift");
        TableColumn<LigandScannerInfo, Double> pcaDistColumn = new TableColumn<>("PCADist");

        datasetColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getDataset().getName()));
        indexColumn.setCellValueFactory(new PropertyValueFactory<>("Index"));
        nPeaksColumn.setCellValueFactory(new PropertyValueFactory<>("NPeaks"));
        pcaDistColumn.setCellValueFactory(new PropertyValueFactory<>("PCADist"));
        minShiftColumn.setCellValueFactory(new PropertyValueFactory<>("MinShift"));
        xArrayChoice.getItems().add("Conc");
        xArrayChoice.getItems().add("MinShift");
        xArrayChoice.getItems().add("PCADist");
        yArrayChoice.getItems().add("Conc");
        yArrayChoice.getItems().add("MinShift");
        yArrayChoice.getItems().add("PCADist");
    }

    public void addPCA() {
        for (int i = 0; i < 5; i++) {
            final int pcaIndex = i;
            String columnName = "PCA" + (pcaIndex + 1);
            scannerTool.getScanTable().addTableColumn(columnName, "D");
            xArrayChoice.getItems().add(columnName);
            yArrayChoice.getItems().add(columnName);
        }
        String columnName = "PCADelta";
        scannerTool.getScanTable().addTableColumn(columnName, "D");
        xArrayChoice.getItems().add(columnName);
        yArrayChoice.getItems().add(columnName);
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

    void doMCS() {
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
            xArrayChoice.getItems().add(columnName);
            yArrayChoice.getItems().add(columnName);

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

    public ObservableList<FileTableItem> getItems() {
        return fileListItems;
    }


    double[] getTableValues(String columnName) {
        double[] values = null;
        List<LigandScannerInfo> scannerRows = matrixAnalyzer.getScannerRows();
        int nItems = scannerRows.size();
        if (nItems != 0) {
            values = new double[nItems];
            int i = 0;
            int pcaIndex = 0;
            if (columnName.startsWith("PCA ")) {
                pcaIndex = Integer.parseInt(columnName.substring(4)) - 1;
                columnName = "PCA";
            }
            for (LigandScannerInfo info : scannerRows) {
                switch (columnName) {
                    case "MinShift":
                        values[i] = info.getMinShift();
                        break;
                    case "PCADist":
                        values[i] = info.getPCADist();
                        break;
                    case "PCA":
                        values[i] = info.getPCAValue(pcaIndex);
                        break;
                }
                i++;
            }
        }
        return values;
    }

    void updatePlot() {
        Axis xAxis = activeChart.getXAxis();
        Axis yAxis = activeChart.getYAxis();
        String xElem = xArrayChoice.getValue();
        String yElem = yArrayChoice.getValue();
        if ((xElem != null) && (yElem != null)) {
            xAxis.setLabel(xElem);
            yAxis.setLabel(yElem);
            xAxis.setZeroIncluded(false);
            yAxis.setZeroIncluded(false);
            xAxis.setAutoRanging(true);
            yAxis.setAutoRanging(true);
            DataSeries series = new DataSeries();
            activeChart.getData().clear();
            //Prepare XYChart.Series objects by setting data
            series.clear();
            double[] xValues = getTableValues(xElem);
            double[] yValues = getTableValues(yElem);
            if ((xValues != null) && (yValues != null)) {
                for (int i = 0; i < xValues.length; i++) {
                    series.add(new XYValue(xValues[i], yValues[i]));
                }
            }
            System.out.println("plot");
            activeChart.getData().add(series);
            activeChart.autoScale(true);
        }

    }

}
