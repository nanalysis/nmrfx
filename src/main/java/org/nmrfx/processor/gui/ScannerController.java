/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.Measure;
import org.nmrfx.processor.datasets.Measure.MeasureTypes;
import org.nmrfx.processor.datasets.Measure.OffsetTypes;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.gui.controls.ScanTable;

/**
 * FXML Controller class
 *
 * @author Bruce Johnson
 */
public class ScannerController implements Initializable {

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

    FXMLController fxmlController;
    PolyChart chart;
    Stage stage;
    ScanTable scanTable;
    ChoicePropertyItem measureItem;
    OffsetPropertyItem offsetItem;

    class CustomPropertyItem implements Item {

        private String key;
        private String category, name, description;
        private String value;

        public CustomPropertyItem(String name, String category, String description) {
            this.name = name;
            this.category = category;
            this.description = description;
        }

        @Override
        public Class<?> getType() {
            return String.class;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "test";
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public void setValue(Object value) {
            if (value == null) {
                this.value = "";
            } else {
                this.value = value.toString();
            }
        }

        @Override
        public Optional<ObservableValue<? extends Object>> getObservableValue() {
            return Optional.empty();
        }
    }

    class ChoicePropertyItem implements Item {

        private String key;
        private String category, name, description;
        private MeasureTypes value = MeasureTypes.VOLUME;

        public ChoicePropertyItem(String name, String category, String description) {
            this.name = name;
            this.category = category;
            this.description = description;
        }

        @Override
        public Class<?> getType() {
            return MeasureTypes.class;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public MeasureTypes getValue() {
            return value;
        }

        @Override
        public void setValue(Object value) {
            this.value = (MeasureTypes) value;
        }

        @Override
        public Optional<ObservableValue<? extends Object>> getObservableValue() {
            return Optional.empty();
        }
    }

    class OffsetPropertyItem implements Item {

        private String key;
        private String category, name, description;
        private OffsetTypes value = OffsetTypes.NONE;

        public OffsetPropertyItem(String name, String category, String description) {
            this.name = name;
            this.category = category;
            this.description = description;
        }

        @Override
        public Class<?> getType() {
            return OffsetTypes.class;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public OffsetTypes getValue() {
            return value;
        }

        @Override
        public void setValue(Object value) {
            this.value = (OffsetTypes) value;
        }

        @Override
        public Optional<ObservableValue<? extends Object>> getObservableValue() {
            return Optional.empty();
        }
    }

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        scanTable = new ScanTable(this, tableView);
    }

    public static ScannerController create(FXMLController fxmlController, Stage parent, PolyChart chart) {
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/ScannerScene.fxml"));
        ScannerController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<ScannerController>getController();
            controller.fxmlController = fxmlController;
            controller.stage = stage;
            controller.chart = chart;
            controller.initParSheet();
            stage.setTitle("NMRFx Processor Scanner");

            stage.initOwner(parent);
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    private void initParSheet() {
        measureItem = new ChoicePropertyItem("mode", "measure", "Measurement modes");
        offsetItem = new OffsetPropertyItem("offset", "measure", "Offset modes");
        parSheet.getItems().addAll(measureItem, offsetItem);
    }

    @FXML
    private void processScanDirAndCombine(ActionEvent event) {
        ChartProcessor chartProcessor = fxmlController.getChartProcessor();
        scanTable.processScanDir(stage, chartProcessor, true);
    }

    @FXML
    private void processScanDir(ActionEvent event) {
        ChartProcessor chartProcessor = fxmlController.getChartProcessor();
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
        scanTable.getItems().clear();
        scanTable.getItems().addAll(tempItems);
    }

    @FXML
    private void openSelectedListFile(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            ProcessorController processorController = fxmlController.getProcessorController(false);
            if ((processorController != null) && !processorController.isViewingDataset()) {
                String scriptString = processorController.getCurrentScript();
                scanTable.openSelectedListFile(scriptString);
            }
        }
    }

    @FXML
    private void loadScriptTab(Event event) {
    }

    public Stage getStage() {
        return stage;
    }

    public PolyChart getChart() {
        return chart;
    }

    public FXMLController getFXMLController() {
        return fxmlController;
    }

    @FXML
    private void measure(ActionEvent event) {
        Dataset dataset = chart.getDataset();
        Measure measure = new Measure(dataset);
        double[] ppms = chart.getVerticalCrosshairPositions();
        double[] wppms = new double[2];
        wppms[0] = chart.getAxis(0).getLowerBound();
        wppms[1] = chart.getAxis(0).getLowerBound();
        int extra = 1;
        List<Double> values;
        try {
            values = measure.measure(0, ppms[0], ppms[1], wppms[0], wppms[1], extra, offsetItem.getValue(), measureItem.getValue());
        } catch (IOException ex) {
            Logger.getLogger(ScannerController.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        String columnDescriptor = getColumnDescriptor(ppms[0], ppms[1], wppms[0], wppms[1]);
        String newColumnName = scanTable.getNextColumnName(columnDescriptor);

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
            System.out.println(value);
            FileTableItem item = map.get(i);
            item.setExtra(newColumnName, value);
        }
        scanTable.addTableColumn(newColumnName, "D");
    }

    
    public String getColumnDescriptor(double ppm1, double ppm2, double ppm1w, double ppm2w) {
        String newColumnName = measureItem.getValue().toString().substring(0, 3).toLowerCase();
        if (null == offsetItem.getValue()) {
            newColumnName = String.format("%s_%.4f_%.4f%s", newColumnName, ppm1, ppm2, "_no_");
        } else {
            switch (offsetItem.getValue()) {
                case WINDOW:
                    newColumnName = String.format("%s_%.4f_%.4f_%.4f_%.4f%s", newColumnName, ppm1, ppm2, ppm1w, ppm2w, "_we_");
                    break;
                case REGION:
                    newColumnName = String.format("%s_%.4f_%.4f%s", newColumnName, ppm1, ppm2, "_re_");
                    break;
                default:
                    newColumnName = String.format("%s_%.4f_%.4f%s", newColumnName, ppm1, ppm2, "_no_");
                    break;
            }
        }
        return newColumnName;
    }
}
