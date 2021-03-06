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
package org.nmrfx.analyst.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.nmrfx.analyst.gui.TablePlotGUI;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.Measure;
import org.nmrfx.processor.datasets.Measure.MeasureTypes;
import org.nmrfx.processor.datasets.Measure.OffsetTypes;
import org.nmrfx.processor.gui.ChartProcessor;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ScannerTool class
 *
 * @author Bruce Johnson
 */
public class ScannerTool implements ControllerTool {
    private static final Logger log = LoggerFactory.getLogger(ScannerTool.class);

    BorderPane borderPane;

    ToolBar scannerBar;
    private TableView<FileTableItem> tableView;
    Consumer<ScannerTool> closeAction;

    FXMLController controller;
    PolyChart chart;
    Stage stage;
    ScanTable scanTable;
    ToggleGroup measureTypeGroup = new ToggleGroup();
    ToggleGroup offsetTypeGroup = new ToggleGroup();

    static Consumer createControllerAction = null;
    TRACTGUI tractGUI = null;
    TablePlotGUI plotGUI = null;
    MinerController miner;
    static final Pattern WPAT = Pattern.compile("([^:]+):([0-9\\.\\-]+)_([0-9\\.\\-]+)_([0-9\\.\\-]+)_([0-9\\.\\-]+)(_[VMmE]W)$");
    static final Pattern RPAT = Pattern.compile("([^:]+):([0-9\\.\\-]+)_([0-9\\.\\-]+)(_[VMmE][NR])?$");
    static final Pattern[] PATS = {WPAT, RPAT};

    public ScannerTool(FXMLController controller, Consumer<ScannerTool> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        chart = controller.getActiveChart();
    }

    public void initialize(BorderPane borderPane) {
        this.borderPane = borderPane;
        String iconSize = "12px";
        String fontSize = "7pt";
        scannerBar = new ToolBar();
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());
        scannerBar.getItems().add(closeButton);
        borderPane.setTop(scannerBar);

        tableView = new TableView<>();
        tableView.setPrefHeight(250.0);
        borderPane.setCenter(tableView);
        scannerBar.getItems().add(makeFileMenu());
        scannerBar.getItems().add(makeProcessMenu());
        scannerBar.getItems().add(makeRegionMenu());
        scannerBar.getItems().add(makeScoreMenu());
        scannerBar.getItems().add(makeToolMenu());
        miner = new MinerController(this);
        scanTable = new ScanTable(this, tableView);
    }

    public static void addCreateAction(Consumer<ScannerTool> action) {
        createControllerAction = action;
    }

    @Override
    public void close() {
        closeAction.accept(this);
    }

    public BorderPane getBox() {
        return borderPane;
    }

    private MenuButton makeFileMenu() {
        MenuButton menu = new MenuButton("File");
        MenuItem scanMenuItem = new MenuItem("Scan Directory...");
        scanMenuItem.setOnAction(e -> scanDirAction());
        MenuItem openTableItem = new MenuItem("Open Table...");
        openTableItem.setOnAction(e -> loadTableAction());
        MenuItem saveTableItem = new MenuItem("Save Table...");
        saveTableItem.setOnAction(e -> saveTableAction());
        MenuItem purgeInactiveItem = new MenuItem("Purge Inactive");
        purgeInactiveItem.setOnAction(e -> purgeInactive());
        MenuItem loadFromDatasetItem = new MenuItem("Load From Dataset");
        loadFromDatasetItem.setOnAction(e -> loadFromDataset());
        menu.getItems().addAll(scanMenuItem, openTableItem, saveTableItem,
                purgeInactiveItem, loadFromDatasetItem);
        return menu;
    }

    private MenuButton makeProcessMenu() {
        MenuButton menu = new MenuButton("Process");
        MenuItem loadRowFIDItem = new MenuItem("Load Row FID");
        loadRowFIDItem.setOnAction(e -> openSelectedListFile());
        MenuItem processAndCombineItem = new MenuItem("Process and Combine");
        processAndCombineItem.setOnAction(e -> processScanDirAndCombine());
        MenuItem processItem = new MenuItem("Process");
        processItem.setOnAction(e -> processScanDir());
        menu.getItems().addAll(loadRowFIDItem, processAndCombineItem,
                processItem);
        return menu;
    }

    private MenuButton makeRegionMenu() {
        MenuButton menu = new MenuButton("Regions");

        Menu measureMode = new Menu("Measure Modes");
        for (var mType : MeasureTypes.values()) {
            RadioMenuItem menuItem = new RadioMenuItem(mType.toString());
            menuItem.setUserData(mType);
            menuItem.setToggleGroup(measureTypeGroup);
            measureMode.getItems().add(menuItem);
        }

        Menu offsetMode = new Menu("Offset Modes");
        for (var oType : OffsetTypes.values()) {
            RadioMenuItem menuItem = new RadioMenuItem(oType.toString());
            menuItem.setUserData(oType);
            menuItem.setToggleGroup(offsetTypeGroup);
            offsetMode.getItems().add(menuItem);
        }
        MenuItem addRegionMenuItem = new MenuItem("Add Crosshair Region");
        addRegionMenuItem.setOnAction(e -> measure());
        MenuItem saveRegionsMenuItem = new MenuItem("Save Regions...");
        saveRegionsMenuItem.setOnAction(e -> saveRegions());
        MenuItem loadRegionsMenuItem = new MenuItem("Load Regions...");
        loadRegionsMenuItem.setOnAction(e -> loadRegions());
        MenuItem measureMenuItem = new MenuItem("Measure All");
        measureMenuItem.setOnAction(e -> measureRegions());
        MenuItem showAllMenuItem = new MenuItem("Show All");
        showAllMenuItem.setOnAction(e -> showRegions());
        MenuItem clearAllMenuItem = new MenuItem("Clear All");
        clearAllMenuItem.setOnAction(e -> clearRegions());
        menu.getItems().addAll(addRegionMenuItem, measureMode, offsetMode, saveRegionsMenuItem, loadRegionsMenuItem,
                measureMenuItem, showAllMenuItem, clearAllMenuItem);
        return menu;
    }

    private MenuButton makeScoreMenu() {
        MenuButton menu = new MenuButton("Score");
        MenuItem scoreMenuItem = new MenuItem("Cosine Score");
        scoreMenuItem.setOnAction(e -> scoreSimilarity());
        menu.getItems().addAll(scoreMenuItem);
        return menu;
    }

    private MenuButton makeToolMenu() {
        MenuButton menu = new MenuButton("Tools");
        MenuItem plotMenuItem = new MenuItem("Show Plot Tool");
        plotMenuItem.setOnAction(e -> showPlotGUI());
        MenuItem tractMenuItem = new MenuItem("Show TRACT Tool");
        tractMenuItem.setOnAction(e -> showTRACTGUI());
        menu.getItems().addAll(plotMenuItem, tractMenuItem);
        return menu;
    }

    MeasureTypes getMeasureType() {
        Toggle toggle = measureTypeGroup.getSelectedToggle();
        return toggle != null ? (MeasureTypes) toggle.getUserData() : MeasureTypes.V;
    }

    OffsetTypes getOffsetType() {
        Toggle toggle = offsetTypeGroup.getSelectedToggle();
        return toggle != null ? (OffsetTypes) toggle.getUserData() : OffsetTypes.N;
    }

    private void processScanDirAndCombine() {
        ChartProcessor chartProcessor = controller.getChartProcessor();
        scanTable.processScanDir(chartProcessor, true);
    }

    private void processScanDir() {
        ChartProcessor chartProcessor = controller.getChartProcessor();
        scanTable.processScanDir(chartProcessor, false);
    }

    private void scanDirAction() {
        scanTable.loadScanFiles(stage);
    }

    private void loadTableAction() {
        scanTable.loadScanTable();
    }

    private void saveTableAction() {
        scanTable.saveScanTable();
    }

    private void purgeInactive() {
        ObservableList<FileTableItem> tempItems = FXCollections.observableArrayList();
        tempItems.addAll(tableView.getItems());
        scanTable.getItems().setAll(tempItems);
    }

    private void loadFromDataset() {
        scanTable.loadFromDataset();
    }

    private void openSelectedListFile() {
        scanTable.openSelectedListFile();
    }

    public Stage getStage() {
        return stage;
    }

    public ToolBar getToolBar() {
        return scannerBar;
    }

    public PolyChart getChart() {
        return chart;
    }

    public FXMLController getFXMLController() {
        return controller;
    }

    public ScanTable getScanTable() {
        return scanTable;
    }

    private boolean hasColumnName(String columnName) {
        List<String> headers = scanTable.getHeaders();
        boolean result = false;
        for (String header : headers) {
            int colon = header.indexOf(":");
            if (colon != -1) {
                if (header.substring(0, colon).equals(columnName)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private void measure() {
        TextInputDialog textInput = new TextInputDialog();
        textInput.setHeaderText("New column name");
        Optional<String> columNameOpt = textInput.showAndWait();
        if (columNameOpt.isPresent()) {
            String columnName = columNameOpt.get();
            columnName = columnName.replace(':', '_').replace(' ', '_');
            if (!columnName.equals("")) {
                if (hasColumnName(columnName)) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Column exists");
                    alert.showAndWait();
                    return;
                }
            }
            double[] ppms = chart.getVerticalCrosshairPositions();
            double[] wppms = new double[2];
            wppms[0] = chart.getAxis(0).getLowerBound();
            wppms[1] = chart.getAxis(0).getUpperBound();
            int extra = 1;

            Measure measure = new Measure(columnName, 0, ppms[0], ppms[1], wppms[0], wppms[1], extra, getOffsetType(), getMeasureType());
            String columnDescriptor = measure.getColumnDescriptor();
            String columnPrefix = scanTable.getNextColumnName(columnName, columnDescriptor);
            measure.setName(columnPrefix);
            String newColumnName = columnPrefix + ":" + columnDescriptor;
            List<Double> allValues = new ArrayList<>();
            List<FileTableItem> items = scanTable.getItems();

            for (FileTableItem item : items) {
                String datasetName = item.getDatasetName();
                Dataset itemDataset = Dataset.getDataset(datasetName);
                if (itemDataset == null) {
                    File datasetFile = new File(scanTable.getScanDir(), datasetName);
                    try {
                        itemDataset = new Dataset(datasetFile.getPath(), datasetFile.getPath(), true, false);
                    } catch (IOException ioE) {
                        GUIUtils.warn("Measure", "Can't open dataset " + datasetFile.getPath());
                        return;
                    }
                }

                List<Double> values = measureRegion(itemDataset, measure);
                if (values == null) {
                    return;
                }
                allValues.addAll(values);
                if (allValues.size() >= items.size()) {
                    break;
                }
            }
            setItems(newColumnName, allValues);
            scanTable.addTableColumn(newColumnName, "D");
        }
    }

    private List<Double> measureRegion(Dataset dataset, Measure measure) {
        List<Double> values;
        try {
            values = measure.measure(dataset);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
        return values;
    }

    private void measureSearchBins() {
        int nBins = 100;
        double[] ppms = chart.getVerticalCrosshairPositions();
        double[] wppms = new double[2];
        wppms[0] = chart.getAxis(0).getLowerBound();
        wppms[1] = chart.getAxis(0).getUpperBound();
        int extra = 1;

        Measure measure = new Measure("binValues", 0, ppms[0], ppms[1], wppms[0], wppms[1], extra, getOffsetType(), getMeasureType());
        measure.setName("bins");
        List<double[]> allValues = new ArrayList<>();
        List<FileTableItem> items = scanTable.getItems();

        for (FileTableItem item : items) {
            String datasetName = item.getDatasetName();
            Dataset itemDataset = Dataset.getDataset(datasetName);

            if (itemDataset == null) {
                File datasetFile = new File(scanTable.getScanDir(), datasetName);
                try {
                    itemDataset = new Dataset(datasetFile.getPath(), datasetFile.getPath(), true, false);
                } catch (IOException ioE) {
                    GUIUtils.warn("Measure", "Can't open dataset " + datasetFile.getPath());
                    return;
                }
            }
            System.out.println("measure " + itemDataset.getName());

            List<double[]> values = measureBins(itemDataset, measure, nBins);
            if (values == null) {
                return;
            }
            allValues.addAll(values);
            if (allValues.size() >= items.size()) {
                break;
            }
        }
        int iItem = 0;
        for (FileTableItem item : items) {
            item.setObjectExtra("binValues", allValues.get(iItem++));
        }
    }

    void scoreSimilarity() {
        FileTableItem refItem = tableView.getSelectionModel().getSelectedItem();
        if (refItem != null) {
            double[] refValues = (double[]) refItem.getObjectExtra("binValues");
            if (refValues == null) {
                measureSearchBins();
            }
            scoreSimilarity(refItem);
        }
    }

    void scoreSimilarity(FileTableItem refItem) {
        String newColumnName = "score";
        List<FileTableItem> items = scanTable.getItems();
        double[] refValues = (double[]) refItem.getObjectExtra("binValues");
        RealVector refVec = new ArrayRealVector(refValues);
        for (FileTableItem item : items) {
            double[] itemValues = (double[]) item.getObjectExtra("binValues");
            RealVector itemVec = new ArrayRealVector(itemValues);
            double score = refVec.cosine(itemVec);
            item.setExtra(newColumnName, score);
        }
        scanTable.addTableColumn(newColumnName, "D");
        scanTable.refresh();
    }

    private List<double[]> measureBins(Dataset dataset, Measure measure, int nBins) {
        List<double[]> values;
        try {
            values = measure.measureBins(dataset, nBins);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
        return values;
    }

    public void setItems(String columnName, List<Double> values) {
        ObservableList<FileTableItem> items = scanTable.getItems();
        Map<Integer, FileTableItem> map = new HashMap<>();
        for (FileTableItem item : items) {
            if (item.getRow() > 0) {
                // rows index from 1
                map.put(item.getRow() - 1, item);
            }
        }
        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            FileTableItem item = map.get(i);
            if (item != null) {
                item.setExtra(columnName, value);
            }
        }
    }

    public List<Double> getValues(String columnName) {
        ObservableList<FileTableItem> items = scanTable.getItems();
        List<Double> values = new ArrayList<>(items.size());
        values.addAll(Collections.nCopies(items.size(), 0.0));
        for (FileTableItem item : items) {
            if (item.getRow() > 0) {
                int row = item.getRow() - 1;
                double value = item.getDoubleExtra(columnName);
                values.set(row, value);
            }
        }
        return values;
    }

    public boolean hasColumn(String columnName) {
        return scanTable.getHeaders().contains(columnName);
    }

    void measureRegions() {
        DatasetBase dataset = chart.getDataset();
        List<String> headers = scanTable.getHeaders();
        for (String header : headers) {
            Optional<Measure> measureOpt = matchHeader(header);
            if (measureOpt.isPresent()) {
                try {
                    List<Double> values = measureOpt.get().measure(dataset);
                    setItems(header, values);
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                    return;
                }
            }
        }
        scanTable.refresh();
    }

    void showRegions() {
        DatasetBase dataset = chart.getDataset();
        List<String> headers = scanTable.getHeaders();
        TreeSet<DatasetRegion> regions = new TreeSet<>();

        for (String header : headers) {
            Optional<Measure> measureOpt = matchHeader(header);
            if (measureOpt.isPresent()) {
                Measure measure = measureOpt.get();
                DatasetRegion region = new DatasetRegion(measure.ppm1, measure.ppm2);
                regions.add(region);
            }
        }
        dataset.setRegions(regions);
        chart.chartProps.setRegions(true);
        chart.chartProps.setIntegrals(false);
        chart.refresh();
    }

    void clearRegions() {
        DatasetBase dataset = chart.getDataset();
        TreeSet<DatasetRegion> regions = new TreeSet<>();

        dataset.setRegions(regions);
        chart.chartProps.setRegions(false);
        chart.refresh();
    }

    void loadRegions() {
        FileChooser chooser = new FileChooser();
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
                while (true) {
                    String s = reader.readLine();
                    if (s == null) {
                        break;
                    }
                    String[] fields = s.split(" +");
                    if (fields.length > 2) {
                        String name = fields[0];
                        StringBuilder sBuilder = new StringBuilder();
                        for (int i = 1; i < fields.length; i++) {
                            sBuilder.append(fields[i]);
                            if (i != fields.length - 1) {
                                sBuilder.append("_");
                            }
                        }
                        String columnPrefix;
                        if (name.startsWith("V.")) {
                            columnPrefix = scanTable.getNextColumnName("", sBuilder.toString());
                        } else {
                            columnPrefix = name;
                        }
                        sBuilder.insert(0, ':');
                        sBuilder.insert(0, columnPrefix);
                        scanTable.addTableColumn(sBuilder.toString(), "D");
                    }
                }
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Couldn't read file");
                alert.showAndWait();
            }

        }
    }

    void saveRegions() {
        FileChooser chooser = new FileChooser();
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try {
                if (!file.exists()) {
                    boolean created = file.createNewFile();
                    if (!created) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setContentText("Couldn't create region file");
                        alert.showAndWait();
                        return;

                    }
                }
                try (FileWriter writer = new FileWriter(file)) {
                    List<String> headers = scanTable.getHeaders();
                    for (String header : headers) {
                        Optional<Measure> measure = matchHeader(header);
                        if (measure.isPresent()) {
                            writer.write(measure.get().getFileString());
                            writer.write('\n');
                        }
                    }
                }
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Couldn't save file");
                alert.showAndWait();
            }
        }
    }

    public static Optional<Measure> matchHeader(String header) {
        Optional<Measure> result = Optional.empty();
        String columnName;
        String oMode;
        String mMode;
        String group = "_VN";
        double ppm1;
        double ppm2;
        double ppmw1;
        double ppmw2;
        for (Pattern pat : PATS) {
            Matcher matcher = pat.matcher(header);
            if (matcher.matches()) {
                columnName = matcher.group(1);
                int nGroups = matcher.groupCount();
                ppm1 = Double.parseDouble(matcher.group(2));
                ppm2 = Double.parseDouble(matcher.group(3));
                if (nGroups == 6) {
                    ppmw1 = Double.parseDouble(matcher.group(4));
                    ppmw2 = Double.parseDouble(matcher.group(5));
                } else {
                    ppmw1 = ppm1;
                    ppmw2 = ppm2;
                }

                if (nGroups >= 4) {
                    String lastGroup = matcher.group(nGroups);
                    if (lastGroup != null) {
                        group = lastGroup;
                    }
                }

                mMode = group.substring(1, 2);
                oMode = group.substring(2, 3);

                OffsetTypes oType = OffsetTypes.valueOf(oMode);
                MeasureTypes mType = MeasureTypes.valueOf(mMode);
                Measure measure = new Measure(columnName, 0, ppm1, ppm2, ppmw1, ppmw2,
                        0, oType, mType);
                result = Optional.of(measure);
                break;
            }
        }
        return result;

    }

    void showPlotGUI() {
        if (plotGUI == null) {
            plotGUI = new TablePlotGUI(tableView);
        }
        plotGUI.showPlotStage();
    }

    void showTRACTGUI() {
        if (tractGUI == null) {
            tractGUI = new TRACTGUI(this);

        }
        tractGUI.showMCplot();
    }

}
