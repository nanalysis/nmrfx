/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.nmrfx.analyst.gui.tools.TRACTGUI;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.Measure;
import org.nmrfx.processor.datasets.Measure.MeasureTypes;
import org.nmrfx.processor.datasets.Measure.OffsetTypes;
import org.nmrfx.processor.gui.ChartProcessor;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import static org.nmrfx.processor.gui.PreferencesController.getDatasetDirectory;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.DirectoryOperationItem;
import org.nmrfx.utils.properties.NvFxPropertyEditorFactory;
import org.nmrfx.utils.properties.TextOperationItem;

/**
 * FXML Controller class
 *
 * @author Bruce Johnson
 */
public class ScannerTool implements ControllerTool {

    BorderPane borderPane;

    @FXML
    ToolBar scannerBar;
    @FXML
    private VBox mainBox;
    @FXML
    private VBox opBox;
    @FXML
    private Button scanDirChooserButton;
    @FXML
    private Button loadTableChooserButton;
    @FXML
    private Button processScanDirButton;
    @FXML
    private Button measureButton;
    @FXML
    private CheckBox combineFiles;
    @FXML
    private TableView<FileTableItem> tableView;
    @FXML
    private PropertySheet parSheet;
    @FXML
    Consumer<ScannerTool> closeAction;

    FXMLController controller;
    PolyChart chart;
    Stage stage;
    ScanTable scanTable;
    ToggleGroup measureTypeGroup = new ToggleGroup();
    ToggleGroup offsetTypeGroup = new ToggleGroup();

    DirectoryOperationItem scanDirItem;
    DirectoryOperationItem outputDirItem;
    TextOperationItem outputFileItem;
    ChangeListener<String> scanDirListener;
    ChangeListener<String> outputDirListener;
    static Consumer createControllerAction = null;
    TRACTGUI tractGUI = null;

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
//        initMenus(scannerBar);
//        ToolBarUtils.addFiller(scannerBar, 10, 500);
//        initNavigator(scannerBar);
//        ToolBarUtils.addFiller(scannerBar, 10, 20);
//        initIntegralType(scannerBar);
//        ToolBarUtils.addFiller(scannerBar, 10, 500);
//
//        ToolBar toolBar2 = new ToolBar();
//        initTools(toolBar2);

        Separator vsep1 = new Separator(Orientation.HORIZONTAL);
        Separator vsep2 = new Separator(Orientation.HORIZONTAL);
        borderPane.setTop(scannerBar);

        tableView = new TableView<>();
        tableView.setPrefHeight(250.0);
        borderPane.setCenter(tableView);
        scannerBar.getItems().add(makeFileMenu());
        scannerBar.getItems().add(makeRegionMenu());
        MinerController miner = new MinerController(this);
        scanTable = new ScanTable(this, tableView);
    }

    public static void addCreateAction(Consumer<ScannerTool> action) {
        createControllerAction = action;
    }

    public void close() {
        closeAction.accept(this);
    }

    public BorderPane getBox() {
        return borderPane;
    }

    private MenuButton makeFileMenu() {
        MenuButton menu = new MenuButton("File");
        MenuItem scanMenuItem = new MenuItem("Scan Directory");
        scanMenuItem.setOnAction(e -> scanDirAction(e));
        MenuItem openTableItem = new MenuItem("Open Table...");
        openTableItem.setOnAction(e -> loadTableAction(e));
        MenuItem saveTableItem = new MenuItem("Save Table...");
        saveTableItem.setOnAction(e -> saveTableAction(e));
        MenuItem processAndCombineItem = new MenuItem("Process and Combine");
        processAndCombineItem.setOnAction(e -> processScanDirAndCombine(e));
        MenuItem processItem = new MenuItem("Process");
        processItem.setOnAction(e -> processScanDir(e));
        MenuItem loadFromDatasetItem = new MenuItem("Load From Dataset");
        loadFromDatasetItem.setOnAction(e -> loadFromDataset(e));
        menu.getItems().addAll(scanMenuItem, openTableItem, saveTableItem,
                processAndCombineItem, processItem, loadFromDatasetItem);
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
        addRegionMenuItem.setOnAction(e -> measure(e));
        MenuItem saveRegionsMenuItem = new MenuItem("Save Regions...");
        saveRegionsMenuItem.setOnAction(e -> saveRegions());
        MenuItem loadRegionsMenuItem = new MenuItem("Load Regions...");
        loadRegionsMenuItem.setOnAction(e -> loadRegions());
        MenuItem measureMenuItem = new MenuItem("Measure All");
        measureMenuItem.setOnAction(e -> measureRegions());
        MenuItem showAllMenuItem = new MenuItem("Show All");
        showAllMenuItem.setOnAction(e -> processScanDir(e));
        MenuItem clearAllMenuItem = new MenuItem("Clear All");
        clearAllMenuItem.setOnAction(e -> clearRegions());
        menu.getItems().addAll(addRegionMenuItem, measureMode, offsetMode, saveRegionsMenuItem, loadRegionsMenuItem,
                measureMenuItem, showAllMenuItem, clearAllMenuItem);
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

    @FXML
    private void processScanDirAndCombine(ActionEvent event) {
        ChartProcessor chartProcessor = controller.getChartProcessor();
        scanTable.processScanDir(stage, chartProcessor, true);
    }

    @FXML
    private void processScanDir(ActionEvent event) {
        ChartProcessor chartProcessor = controller.getChartProcessor();
        scanTable.processScanDir(stage, chartProcessor, false);
    }

    @FXML
    private void scanDirAction(ActionEvent event) {
        scanTable.loadScanFiles(stage);
    }

    @FXML
    private void loadTableAction(ActionEvent event) {
        scanTable.loadScanTable();
    }

    @FXML
    private void saveTableAction(ActionEvent event) {
        scanTable.saveScanTable();
    }

    @FXML
    private void freezeSort(ActionEvent event) {

    }

    @FXML
    private void purgeInactive(ActionEvent event) {
        ObservableList<FileTableItem> tempItems = FXCollections.observableArrayList();
        tempItems.addAll(tableView.getItems());
        scanTable.getItems().setAll(tempItems);
    }

    @FXML
    private void loadFromDataset(ActionEvent event) {
        scanTable.loadFromDataset();
    }

    @FXML
    private void openSelectedListFile(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            scanTable.openSelectedListFile();
        }
    }

    @FXML
    private void loadScriptTab(Event event) {
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

    public String getScanDirectory() {
        return scanDirItem.get();
    }

    public void setScanDirectory(String dirString) {
        scanDirItem.setFromString(dirString);
    }

    public void updateScanDirectory(String dirString) {
        scanDirItem.setFromString(dirString);
        scanDirItem.updateEditor();
    }

    public String getOutputDirectory() {
        return outputDirItem.get();
    }

    public String getOutputFileName() {
        return outputFileItem.get();
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

    @FXML
    private void measure(ActionEvent event) {
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
            DatasetBase dataset = chart.getDataset();
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
                    File datasetFile = new File(scanTable.getScanOutputDirectory(), datasetName);
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
            Logger.getLogger(ScannerTool.class.getName()).log(Level.SEVERE, null, ex);
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
        Map<Integer, FileTableItem> map = new HashMap<>();
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

    @FXML
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
                    Logger.getLogger(ScannerTool.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
            }
        }
        scanTable.refresh();
    }

    @FXML
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

    @FXML
    void clearRegions() {
        DatasetBase dataset = chart.getDataset();
        TreeSet<DatasetRegion> regions = new TreeSet<>();

        dataset.setRegions(regions);
        chart.chartProps.setRegions(false);
        chart.refresh();
    }

    @FXML
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

    @FXML
    void saveRegions() {
        FileChooser chooser = new FileChooser();
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try {
                file.createNewFile();
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
}
