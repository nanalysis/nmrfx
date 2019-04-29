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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.PopOver;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.tools.LigandScannerInfo;
import org.nmrfx.processor.tools.MatrixAnalyzer;
import org.nmrfx.structure.tools.MCSAnalysis;
import org.nmrfx.structure.tools.MCSAnalysis.Hit;
import org.renjin.primitives.vector.RowNamesVector;
import org.renjin.sexp.AttributeMap;
import org.renjin.sexp.DoubleVector;
import org.renjin.sexp.Environment;
import org.renjin.sexp.IntVector;
import org.renjin.sexp.ListVector;
import org.renjin.sexp.SEXP;
import org.renjin.sexp.StringArrayVector;
import org.renjin.sexp.StringVector;
import org.renjin.sexp.Symbols;

/**
 *
 * @author Bruce Johnson
 */
public class LigandScannerController implements Initializable {

    private Stage stage;

    @FXML
    TableView<LigandScannerInfo> ligandTableView;
    @FXML
    private ToolBar menuBar;
    PopOver popOver = new PopOver();
    ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
    HashMap<String, String> columnTypes = new HashMap<>();
    HashMap<String, String> columnDescriptors = new HashMap<>();
    MatrixAnalyzer matrixAnalyzer = new MatrixAnalyzer();
    String[] dimNames = null;
    double[] mcsTols = null;
    double[] mcsAlphas = null;
    double mcsTol = 0.0;
    int refIndex = 0;
    PolyChart chart = PolyChart.getActiveChart();

    int nPCA = 5;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initMenuBar();
        initTable();
    }

    public Stage getStage() {
        return stage;
    }

    public static LigandScannerController create() {
        FXMLLoader loader = new FXMLLoader(LigandScannerController.class.getResource("/fxml/LigandScannerScene.fxml"));
        LigandScannerController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<LigandScannerController>getController();
            controller.stage = stage;
            stage.setTitle("Ligand Scanner");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    void initMenuBar() {
        MenuButton fileMenu = new MenuButton("File");
        MenuItem readScannerTableItem = new MenuItem("Read Table...");
        readScannerTableItem.setOnAction(e -> readTable());
        fileMenu.getItems().add(readScannerTableItem);
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
    }

    private void initTable() {
        TableColumn<LigandScannerInfo, String> datasetColumn = new TableColumn<>("Dataset");
        TableColumn<LigandScannerInfo, Integer> indexColumn = new TableColumn<>("Index");
        TableColumn<LigandScannerInfo, Integer> nPeaksColumn = new TableColumn<>("nPks");
        TableColumn<LigandScannerInfo, String> groupColumn = new TableColumn<>("Group");
        TableColumn<LigandScannerInfo, String> sampleColumn = new TableColumn<>("Sample");
        TableColumn<LigandScannerInfo, Double> concColumn = new TableColumn<>("Conc");
        TableColumn<LigandScannerInfo, Double> minShiftColumn = new TableColumn<>("MinShift");
        TableColumn<LigandScannerInfo, Double> pcaDistColumn = new TableColumn<>("PCADist");

        datasetColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getDataset().getName()));
        indexColumn.setCellValueFactory(new PropertyValueFactory<>("Index"));
        nPeaksColumn.setCellValueFactory(new PropertyValueFactory<>("NPeaks"));
        groupColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getGroup()));
        sampleColumn.setCellValueFactory((e) -> new SimpleStringProperty(String.valueOf(e.getValue().getSample())));
        concColumn.setCellValueFactory(new PropertyValueFactory<>("Conc"));
        pcaDistColumn.setCellValueFactory(new PropertyValueFactory<>("PCADist"));
        minShiftColumn.setCellValueFactory(new PropertyValueFactory<>("MinShift"));
        ligandTableView.getColumns().addAll(datasetColumn, indexColumn, nPeaksColumn,
                groupColumn, sampleColumn, concColumn, minShiftColumn,
                pcaDistColumn);
        for (int i = 0; i < 5; i++) {
            final int pcaIndex = i;
            TableColumn<LigandScannerInfo, Number> pcaColumn = new TableColumn<>("PCA " + (pcaIndex + 1));
            ligandTableView.getColumns().add(pcaColumn);
            pcaColumn.setCellValueFactory((e) -> new SimpleDoubleProperty(e.getValue().getPCAValue(pcaIndex)));

        }

    }

    public void refresh() {
        ligandTableView.refresh();
    }

    public void readTable() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                matrixAnalyzer.readScannerFile(file.toString());
            } catch (IOException ex) {
            }
            ligandTableView.getItems().setAll(matrixAnalyzer.getScannerRows());
            refresh();
        }
    }

    public void setupBucket() {
        List<String> chartDimNames = chart.getDimNames();
        int nDim = chartDimNames.size();
//        if (chart.getDatasetAttributes().size() == 1) {
//            nDim--;
//        }
        dimNames = new String[nDim];
        mcsTols = new double[nDim];
        mcsAlphas = new double[nDim];
        double[][] ppms = new double[nDim][2];
        int[] deltas = new int[nDim];
        if (!chart.getCrossHairs().hasCrosshairRegion()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("No crosshair region");
            alert.showAndWait();
            return;
        }

        for (int i = 0; i < nDim; i++) {
            dimNames[i] = chartDimNames.get(i);
            deltas[i] = 10;
            Double[] positions0 = chart.getCrossHairs().getCrossHairPositions(0);
            Double[] positions1 = chart.getCrossHairs().getCrossHairPositions(1);
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
        matrixAnalyzer.setup(dimNames, ppms, deltas);
    }

    public void doPCA() {
        if (chart.getDatasetAttributes().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("No dataset in active window");
            alert.showAndWait();
            return;
        }
        double threshold = chart.getDatasetAttributes().get(0).getLvl();
        try {
            matrixAnalyzer.bucket(threshold);
        } catch (IOException ex) {
            ExceptionDialog eDialog = new ExceptionDialog(ex);
            eDialog.showAndWait();
            return;
        }
        double[][] pcaValues = matrixAnalyzer.doPCA(nPCA);
        List<LigandScannerInfo> scannerRows = matrixAnalyzer.getScannerRows();
        double[] pcaDists = matrixAnalyzer.getPCADelta(refIndex, 2);
        int iRow = 0;
        double[] pcaCol = new double[pcaValues.length];
        for (LigandScannerInfo scannerRow : scannerRows) {
            for (int j = 0; j < pcaCol.length; j++) {
                pcaCol[j] = pcaValues[j][iRow];
            }
            scannerRow.setPCValues(pcaCol);
            scannerRow.setPCADist(pcaDists[iRow]);
            iRow++;
        }
        refresh();
    }

    void doMCS() {
        List<LigandScannerInfo> scannerRows = matrixAnalyzer.getScannerRows();
        if (!scannerRows.isEmpty()) {
            for (LigandScannerInfo scannerRow : scannerRows) {
                PeakList peakList = scannerRow.getPeakList();
                if (peakList == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("No peakList");
                    alert.showAndWait();
                    return;
                }
            }
            LigandScannerInfo refInfo = scannerRows.get(refIndex);
            PeakList refPeakList = refInfo.getPeakList();
            for (LigandScannerInfo scannerRow : scannerRows) {
                PeakList peakList = scannerRow.getPeakList();
                double score = 0.0;
                if (peakList != refPeakList) {
                    MCSAnalysis mcsAnalysis = new MCSAnalysis(peakList, mcsTols, mcsAlphas, dimNames, refPeakList);
                    List<Hit> hits = mcsAnalysis.calc();
                    score = mcsAnalysis.score(hits, mcsTol);
                    System.out.println(hits.size() + " " + score);
                }
                scannerRow.setMinShift(score);
            }
        }
        refresh();
    }

    public ObservableList<FileTableItem> getItems() {
        return fileListItems;
    }

    class DoubleColumnVector extends DoubleVector {

        String name;
        Function<FileTableItem, Double> getter;

        public DoubleColumnVector(AttributeMap attributes) {
            super(attributes);
        }

        DoubleColumnVector(String name, Function<FileTableItem, Double> getter) {
            this.name = name;
            this.getter = getter;
        }

        @Override
        protected SEXP cloneWithNewAttributes(AttributeMap am) {
            DoubleColumnVector clone = new DoubleColumnVector(am);
            clone.name = name;
            clone.getter = getter;
            return clone;
        }

        @Override
        public double getElementAsDouble(int i) {
            FileTableItem item = fileListItems.get(i);
            return getter.apply(item);
        }

        @Override
        public int length() {
            return fileListItems.size();
        }

        @Override
        public boolean isConstantAccessTime() {
            return true;
        }

    }

    class IntColumnVector extends IntVector {

        String name;
        Function<FileTableItem, Integer> getter;

        public IntColumnVector(AttributeMap attributes) {
            super(attributes);
        }

        IntColumnVector(String name, Function<FileTableItem, Integer> getter) {
            this.name = name;
            this.getter = getter;
        }

        @Override
        protected SEXP cloneWithNewAttributes(AttributeMap am) {
            IntColumnVector clone = new IntColumnVector(am);
            clone.name = name;
            clone.getter = getter;
            return clone;
        }

        @Override
        public int getElementAsInt(int i) {
            FileTableItem item = fileListItems.get(i);
            return getter.apply(item);
        }

        @Override
        public int length() {
            return fileListItems.size();
        }

        @Override
        public boolean isConstantAccessTime() {
            return true;
        }

    }

    class StringColumnVector extends StringVector {

        String name;
        Function<FileTableItem, String> getter;

        public StringColumnVector(AttributeMap attributes) {
            super(attributes);
        }

        public StringColumnVector(String name, Function<FileTableItem, String> getter) {
            super(AttributeMap.EMPTY);
            this.name = name;
            this.getter = getter;
        }

        @Override
        public int length() {
            return fileListItems.size();
        }

        @Override
        public boolean isConstantAccessTime() {
            return true;
        }

        @Override
        public String getElementAsString(int i) {
            FileTableItem item = fileListItems.get(i);
            if (item == null) {
                System.out.println("null item " + i + " column " + name);
                return NA;
            }
            return getter.apply(item);
        }

        @Override
        protected StringColumnVector cloneWithNewAttributes(AttributeMap am) {
            StringColumnVector clone = new StringColumnVector(am);
            clone.name = name;
            clone.getter = getter;
            return clone;
        }

    }

    public void updateDataFrame() {
        ObservableList<TableColumn<LigandScannerInfo, ?>> columns = ligandTableView.getColumns();

        ListVector.NamedBuilder builder = new ListVector.NamedBuilder();

        int iCol = 0;
        for (TableColumn column : columns) {
            String fullName = column.getText();
            int colonPos = fullName.indexOf(":");
            final String name;
            if (colonPos != -1) {
                name = fullName.substring(0, colonPos);
            } else {
                name = fullName;
            }
            String type = columnTypes.get(fullName);
            if (type == null) {
                System.out.println("null type " + name);
                type = "S";
            }
            switch (type) {
                case "D": {
                    DoubleColumnVector dVec = new DoubleColumnVector(name, item -> item.getDoubleExtra(fullName));
                    builder.add(name, dVec);
                    break;
                }
                case "I": {
                    IntColumnVector iVec;
                    if (name.equalsIgnoreCase("row")) {
                        iVec = new IntColumnVector(name, item -> item.getRow());
                    } else if (name.equalsIgnoreCase("ndim")) {
                        iVec = new IntColumnVector(name, item -> item.getNDim());
                    } else if (name.equalsIgnoreCase("etime")) {
                        iVec = new IntColumnVector(name, item -> item.getDate().intValue());
                    } else if (name.equalsIgnoreCase("group")) {
                        iVec = new IntColumnVector(name, item -> item.getGroup());
                    } else {
                        iVec = new IntColumnVector(name, item -> item.getIntegerExtra(fullName));
                    }
                    builder.add(name, iVec);
                    break;
                }

                case "S": {
                    StringColumnVector sVec;
                    if (name.equalsIgnoreCase("path")) {
                        sVec = new StringColumnVector(name, item -> item.getFileName());
                    } else if (name.equalsIgnoreCase("sequence")) {
                        sVec = new StringColumnVector(name, item -> item.getSeqName());
                    } else {
                        sVec = new StringColumnVector(name, item -> item.getExtra(fullName));
                    }
                    builder.add(name, sVec);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid column type");
                }

            }
            iCol++;
        }
        builder.setAttribute(Symbols.ROW_NAMES, new RowNamesVector(fileListItems.size()));
        builder.setAttribute(Symbols.CLASS, StringArrayVector.valueOf("data.frame"));

        ListVector dFrame = builder.build();

        ConsoleController consoleController = MainApp.getConsoleController();
        if (consoleController == null) {
            System.out.println("null proccon");
        } else {
            Environment env = consoleController.getREnvironment();
            if (env == null) {
                System.out.println("null env");
            } else {
                env.setVariableUnsafe("scntbl", dFrame);
            }
        }
    }

}
