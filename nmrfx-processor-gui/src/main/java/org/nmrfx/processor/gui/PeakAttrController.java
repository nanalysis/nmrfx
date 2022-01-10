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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.FloatStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.io.PeakPatternReader;
import org.nmrfx.peaks.types.PeakListType;
import org.nmrfx.peaks.types.PeakListTypes;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author johnsonb
 */
public class PeakAttrController implements Initializable, PeakNavigable, PeakMenuTarget {

    static final DecimalFormat formatter = new DecimalFormat();

    private Stage stage;
    @FXML
    private TabPane tabPane;
    @FXML
    private ToolBar menuBar;
    @FXML
    private ToolBar peakNavigatorToolBar;
    @FXML
    private TableView<PeakDim> peakTableView;
    @FXML
    private TextField intensityField;
    @FXML
    private TextField intensityErrField;
    @FXML
    private TextField volumeField;
    @FXML
    private TextField volumeErrField;
    @FXML
    private TextField commentField;
    @FXML
    private TextField peakListNameField;
    @FXML
    private MenuButton peakListTypeMenu;
    @FXML
    private ComboBox datasetNameField;
    @FXML
    private ComboBox conditionField;
    @FXML
    private ToolBar peakReferenceToolBar;
    @FXML
    private TableView<SpectralDim> referenceTableView;

    @FXML
    private BorderPane peaksBorderPane;

    @FXML
    private BorderPane graphBorderPane;

    @FXML
    private BorderPane simBorderPane;
    @FXML
    private TextField simPeakListNameField;
    @FXML
    private ComboBox simDatasetNameField;

    @FXML
    private MenuButton simTypeMenu;

    @FXML
    private Label typeLabel;

    @FXML
    private Label subTypeLabel;

    @FXML
    private GridPane peakListParsPane;
    TextField[][] peakListParFields;

    @FXML
    private HBox optionBox;

    @FXML
    Button generateButton;

    @FXML
    private ChoiceBox<PEAK_NORM> normChoice;

    private Slider distanceSlider = new Slider(2, 7.0, 5.0);
    private ChoiceBox transferLimitChoice = new ChoiceBox();
    private CheckBox useNCheckBox = new CheckBox("UseN");

    private ScatterChart<Number, Number> scatterChart;

    PeakNavigator peakNavigator;

    PeakList peakList;
    Peak currentPeak;
    ToggleButton deleteButton;
    ComboTableCell comboTableCell = new ComboTableCell();
    ObservableList<String> relationChoiceItems = FXCollections.observableArrayList("", "D1", "D2", "D3", "D4");

    String type = "";
    String subType = "";
    double sfH = 700.0;

    enum PEAK_NORM {
        NO_NORM() {
            @Override
            public Optional<Double> normalize(double first, double value) {
                return Optional.of(value);
            }

        },
        FP_NORM() {
            @Override
            public Optional<Double> normalize(double first, double value) {
                return Optional.of(value / first);
            }

        },
        LOG_NORM() {
            @Override
            public Optional<Double> normalize(double first, double value) {
                Optional<Double> result;
                if (value > 0.0) {
                    result = Optional.of(-Math.log(value / first));
                } else {
                    result = Optional.empty();
                }
                return result;
            }
        };

        public abstract Optional<Double> normalize(double first, double value);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initMenuBar();
        peakNavigator = PeakNavigator.create(this).initialize(peakNavigatorToolBar);
        initTable();
        initReferenceTable();
        setFieldActions();
        datasetNameField.setOnShowing(e -> updateDatasetNames());
        datasetNameField.setOnAction(e -> {
            selectDataset();
        });
        conditionField.setOnShowing(e -> updateConditionNames());
        conditionField.setOnAction(e -> {
            setCondition();
        });
        conditionField.setEditable(true);

        peakListNameField.setOnKeyReleased(kE -> {
            if (kE.getCode() == KeyCode.ENTER) {
                renamePeakList();

            }
        });

        simDatasetNameField.setOnShowing(e -> updateSimDatasetNames());
        simDatasetNameField.setOnAction(e -> {
            selectSimDataset();
        });

        String[] basicTypes = {"HSQC-13C", "HSQC-15N", "HMBC", "TOCSY", "NOESY"};
        String[] rnaTypes = {"RNA-NOESY-2nd-str"};
        String[] proteinTypes = {"HSQC", "HNCO", "HNCOCA", "HNCOCACB", "HNCACO", "HNCA", "HNCACB"};
        String[] types = {"Basic", "Protein", "RNA"};
        Map<String, String[]> subTypeMap = new HashMap<>();
        subTypeMap.put("Basic", basicTypes);
        subTypeMap.put("RNA", rnaTypes);
        subTypeMap.put("Protein", proteinTypes);
        for (String type : types) {
            Menu menu = new Menu(type);
            simTypeMenu.getItems().add(menu);
            String[] subTypes = subTypeMap.get(type);
            for (String subType : subTypes) {
                MenuItem item = new MenuItem(subType);
                item.setOnAction(e -> setType(type, subType));
                menu.getItems().add(item);
            }
        }
        try {
            PeakListTypes peakListTypes = PeakPatternReader.loadYaml();
            for (PeakListType peakListType : peakListTypes.getTypes()) {
                MenuItem menuItem = new MenuItem(peakListType.getName());
                peakListTypeMenu.getItems().add(menuItem);
                menuItem.setOnAction(e -> setPeakListType(peakListType));
            }
        } catch (IOException e) {
        }

        generateButton.setDisable(true);

        normChoice.getItems().addAll(PEAK_NORM.values());
        normChoice.setValue(PEAK_NORM.NO_NORM);
        normChoice.setOnAction(e -> updateGraph());

        final NumberAxis xAxis = new NumberAxis(0, 10, 1);
        final NumberAxis yAxis = new NumberAxis(-100, 500, 100);
        scatterChart = new ScatterChart<>(xAxis, yAxis);
        xAxis.setAutoRanging(true);
        yAxis.setAutoRanging(true);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        scatterChart.setAnimated(false);
        graphBorderPane.setCenter(scatterChart);
        tabPane.getSelectionModel().selectedItemProperty().addListener(e -> {
            String tabText = tabPane.getSelectionModel().getSelectedItem().textProperty().get();
            if (tabText.equals("Peaks")) {
                if (graphBorderPane.getTop() != null) {
                    graphBorderPane.setTop(null);
                }
                peaksBorderPane.setTop(peakNavigatorToolBar);
            } else if (tabText.equals("Graph")) {
                if (peaksBorderPane.getTop() != null) {
                    peaksBorderPane.setTop(null);
                }
                graphBorderPane.setTop(peakNavigatorToolBar);
            } else {
            }
        });
        initOptions();
        if (!MainApp.isAnalyst()) {
            tabPane.getTabs().remove(3);
        }

//        peakListMenuButton.setOnMousePressed(e -> {
//            updatePeakListMenu();
//            peakListMenuButton.show();
//        });
//        ChangeListener<Dataset> listener = new ChangeListener<Dataset>() {
//            @Override
//            public void changed(ObservableValue<? extends Dataset> observable, Dataset oldValue, Dataset newValue) {
//                System.out.println("datasets changed");
//                PolyChart chart = PolyChart.activeChart;
//                if (chart != null) {
//                    setPeak(chart);
//                }
//            }
//        };
    }

    public Stage getStage() {
        return stage;
    }

    public static PeakAttrController create() {
        FXMLLoader loader = new FXMLLoader(PeakAttrController.class.getResource("/fxml/PeakAttrScene.fxml"));
        PeakAttrController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<PeakAttrController>getController();
            controller.stage = stage;
            stage.setTitle("Peak Attributes");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    public void refreshPeakView() {
        refreshPeakView(currentPeak);
    }

    public void refreshPeakView(Peak peak) {
        if (peakTableView == null) {
            System.out.println("null table");
            return;
        }
        boolean clearIt = true;
        if (peak != null) {
            if (peak != currentPeak) {
                ObservableList<PeakDim> peakDimList = FXCollections.observableArrayList();
                for (PeakDim peakDim : peak.getPeakDims()) {
                    peakDimList.add(peakDim);
                }
                peakTableView.setItems(peakDimList);
            }
            peakTableView.refresh();
            intensityField.setText(String.format("%.5f", peak.getIntensity()));
            intensityErrField.setText(String.format("%.5f", peak.getIntensityErr()));
            volumeField.setText(String.format("%.5f", peak.getVolume1()));
            volumeErrField.setText(String.format("%.5f", peak.getVolume1Err()));
            commentField.setText(peak.getComment());
            clearIt = false;
        }
        currentPeak = peak;
        updateGraph();
        if (clearIt) {
            clearInsepctor();
        }
    }

    public void updateGraph() {
        if ((currentPeak != null) && currentPeak.getMeasures().isPresent()) {
            ObservableList<XYChart.Series<Number, Number>> data = FXCollections.observableArrayList();
            Series series = new Series<>();
            data.add(series);
            PEAK_NORM normMode = normChoice.getValue();
            double[][] values = currentPeak.getMeasures().get();
            double[] yValues = values[0];
            double[] errs = values[1];
            double[] xValues = currentPeak.getPeakList().getMeasureValues();
            for (int i = 0; i < yValues.length; i++) {
                double xValue = xValues != null ? xValues[i] : 1.0 * i;
                if ((i == 0) && (normMode != PEAK_NORM.NO_NORM)) {
                    continue;
                }
                Optional<Double> yValue = normMode.normalize(yValues[0], yValues[i]);
                if (yValue.isPresent()) {
                    double err = 0.0;
                    if (normMode == PEAK_NORM.NO_NORM) {
                        err = errs[i];
                    }
                    XYChart.Data value = new XYChart.Data(xValue, yValue.get(), err);
                    series.getData().add(value);
                }
            }
            scatterChart.getData().setAll(data);
        } else {
            scatterChart.getData().clear();
        }
    }

    public void updateDatasetNames() {
        datasetNameField.getItems().clear();
        Dataset.datasets().stream().forEach(d -> {
            datasetNameField.getItems().add(d.getName());
        });
        if (peakList != null) {
            datasetNameField.setValue(peakList.getDatasetName());
        }
    }

    public void updateConditionNames() {
        conditionField.getItems().clear();
        Set<String> conditions = PeakList.peakLists().stream().
                map(peakList -> peakList.getSampleConditionLabel()).
                filter(label -> label != null).collect(Collectors.toSet());
        conditions.stream().sorted().forEach(s -> {
            conditionField.getItems().add(s);
        });
        if (peakList != null) {
            conditionField.setValue(peakList.getSampleConditionLabel());
        }
    }

    public void updateSimDatasetNames() {
        simDatasetNameField.getItems().clear();
        simDatasetNameField.getItems().add("Sim");

        Dataset.datasets().stream().sorted().forEach(d -> {
            simDatasetNameField.getItems().add(d.getName());
        });
    }

    public void refreshPeakListView(PeakList refreshPeakList) {
        if (referenceTableView == null) {
            System.out.println("null table");
            return;
        }
        // fixme  need to update datasets upon dataset list change
        relationChoiceItems.clear();
        peakList = refreshPeakList;
        if (peakList != null) {
            ObservableList<SpectralDim> peakDimList = FXCollections.observableArrayList();
            relationChoiceItems.add("");
            for (int i = 0; i < peakList.nDim; i++) {
                peakDimList.add(peakList.getSpectralDim(i));
                relationChoiceItems.add(peakList.getSpectralDim(i).getDimName());
            }
            referenceTableView.setItems(peakDimList);
            peakListNameField.setText(peakList.getName());
            datasetNameField.setValue(peakList.getDatasetName());
            conditionField.setValue(peakList.getSampleConditionLabel());
            stage.setTitle(peakList.getName());
        } else {
            referenceTableView.getItems().clear();
            stage.setTitle("Peak Inspector");
        }
    }

    private void clearInsepctor() {
        peakTableView.getItems().clear();
        intensityField.setText("");
        volumeField.setText("");
        commentField.setText("");

    }

    public void gotoPeak(Peak peak) {
        peakNavigator.setPeak(peak);
    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        if (peakList != peak.getPeakList()) {
            peakList = peak.getPeakList();
            stage.setTitle(peakList.getName());
        }
    }

    public void initIfEmpty() {
        peakNavigator.setPeakList();
    }

    public PeakList getPeakList() {
        return peakList;
    }

    public void setPeakList(PeakList peakList) {
        peakNavigator.setPeakList(peakList);
    }

    void initMenuBar() {
        PeakMenuBar peakMenuBar = new PeakMenuBar(this);
        peakMenuBar.initMenuBar(menuBar);
    }

    static class FloatStringConverter2 extends FloatStringConverter {

        public Float fromString(String s) {
            Float v;
            try {
                v = Float.parseFloat(s);
            } catch (NumberFormatException nfE) {
                v = null;
            }
            return v;
        }

    }

    static class TextFieldTableCellFloat extends TextFieldTableCell<PeakDim, Float> {

        public TextFieldTableCellFloat(StringConverter s) {
            super(s);
        }

        @Override
        public void updateItem(Float item, boolean empty) {
            super.updateItem(item, empty);
            if (item != null) {
                setText(String.valueOf(item));
            } else {
            }
        }
    }

    ;

    static class TextFieldTableCellPeakLabel extends TextFieldTableCell<PeakDim, String> {

        public TextFieldTableCellPeakLabel(StringConverter s) {
            super(s);
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            PeakDim peakDim = getTableRow().getItem();
            setText(null);
            setGraphic(null);
            if (!empty && (peakDim != null)) {
                if (!peakDim.isLabelValid()) {
                    setBackground(new Background(new BackgroundFill(Color.RED, null, null)));
                } else {
                    setBackground(Background.EMPTY);
                }
                setText(String.valueOf(item));
            }
        }
    }

    static class TextFieldRefTableCell extends TextFieldTableCell<SpectralDim, Double> {

        public TextFieldRefTableCell(StringConverter s) {
            super(s);
        }

        @Override
        public void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            if (item != null) {
                setText(String.valueOf(item));
            } else {
            }
        }
    }

    static class ComboTableCell<SpectralDim, String> extends ComboBoxTableCell<SpectralDim, String> {

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
        }
    }

    void initTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        FloatStringConverter fsConverter = new FloatStringConverter2();
        peakTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        IntegerStringConverter isConverter = new IntegerStringConverter();
        peakTableView.setEditable(true);
        TableColumn<PeakDim, String> dimNameCol = new TableColumn<>("Dim");
        dimNameCol.setCellValueFactory(new PropertyValueFactory("DimName"));
        dimNameCol.setEditable(false);

        TableColumn<PeakDim, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(new PropertyValueFactory("Label"));
        labelCol.setCellFactory(tc -> new TextFieldTableCellPeakLabel(new DefaultStringConverter()));

        labelCol.setEditable(true);
        labelCol.setOnEditCommit((CellEditEvent<PeakDim, String> t) -> {
            String value = t.getNewValue();
            PeakDim peakDim = t.getRowValue();
            if (value == null) {
                value = "";
            }
            AtomResPattern.assignDim(peakDim, value);
            TablePosition tPos = t.getTablePosition();
            int col = tPos.getColumn();
            int row = tPos.getRow();
            row++;
            if (row < t.getTableView().getItems().size()) {
                t.getTableView().getSelectionModel().selectBelowCell();
                t.getTableView().edit(row, labelCol);
            }

        });

        TableColumn<PeakDim, Float> ppmCol = new TableColumn<>("PPM");
        ppmCol.setCellValueFactory(new PropertyValueFactory("ChemShift"));
        ppmCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        ppmCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setChemShift(value);
                    }
                });

        ppmCol.setEditable(true);

        TableColumn<PeakDim, Float> widthCol = new TableColumn<>("Width");
        widthCol.setCellValueFactory(new PropertyValueFactory("LineWidthHz"));
        widthCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        widthCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setLineWidthHz(value);
                    }
                });
        widthCol.setEditable(true);

        TableColumn<PeakDim, Float> boundsCol = new TableColumn<>("Bounds");
        boundsCol.setCellValueFactory(new PropertyValueFactory("BoundsHz"));
        boundsCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        boundsCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setBoundsHz(value);
                    }
                });

        boundsCol.setEditable(true);

        TableColumn<PeakDim, String> resonanceColumn = new TableColumn<>("ResID");
        resonanceColumn.setCellValueFactory(new PropertyValueFactory("ResonanceIDsAsString"));
        resonanceColumn.setEditable(false);

        TableColumn<PeakDim, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory("User"));
        userCol.setCellFactory(TextFieldTableCell.forTableColumn());
        userCol.setEditable(true);
        userCol.setOnEditCommit((CellEditEvent<PeakDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setUser(value == null ? "" : value);
        });

        peakTableView.getColumns().setAll(dimNameCol, labelCol, ppmCol, widthCol, boundsCol, resonanceColumn, userCol);
    }

    private void setFieldActions() {
        commentField.setOnKeyPressed(e -> {
            if (currentPeak != null) {
                if (e.getCode() == KeyCode.ENTER) {
                    currentPeak.setComment(commentField.getText().trim());

                }
            }
        });
        intensityField.setOnKeyPressed(e -> {
            if (currentPeak != null) {
                if (e.getCode() == KeyCode.ENTER) {
                    try {
                        float value = Float.parseFloat(intensityField.getText().trim());
                        currentPeak.setIntensity(value);
                    } catch (NumberFormatException nfE) {

                    }

                }
            }
        });
        volumeField.setOnKeyPressed(e -> {
            if (currentPeak != null) {
                if (e.getCode() == KeyCode.ENTER) {
                    try {
                        float value = Float.parseFloat(volumeField.getText().trim());
                        currentPeak.setVolume1(value);
                    } catch (NumberFormatException nfE) {

                    }

                }
            }
        });
    }

    void initReferenceTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        FloatStringConverter fsConverter = new FloatStringConverter2();

        IntegerStringConverter isConverter = new IntegerStringConverter();
        referenceTableView.setEditable(true);
        TableColumn<SpectralDim, String> dimNameCol = new TableColumn<>("Dim");
        dimNameCol.setCellValueFactory(new PropertyValueFactory("DimName"));
        dimNameCol.setEditable(false);

        TableColumn<SpectralDim, String> labelCol = new TableColumn<>("Name");
        labelCol.setCellValueFactory(new PropertyValueFactory("DimName"));
        labelCol.setCellFactory(TextFieldTableCell.forTableColumn());
        labelCol.setEditable(true);
        labelCol.setOnEditCommit((CellEditEvent<SpectralDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setDimName(value == null ? "" : value);
        });

        TableColumn<SpectralDim, String> nucCol = new TableColumn<>("Nucleus");
        nucCol.setCellValueFactory(new PropertyValueFactory("Nucleus"));
        nucCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nucCol.setEditable(true);
        nucCol.setOnEditCommit((CellEditEvent<SpectralDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setNucleus(value == null ? "" : value);
        });

        TableColumn<SpectralDim, Double> sfCol = new TableColumn<>("SF");
        sfCol.setCellValueFactory(new PropertyValueFactory("Sf"));
        sfCol.setCellFactory(tc -> new TextFieldRefTableCell(dsConverter));
        sfCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, Double> t) -> {
                    Double value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setSf(value);
                    }
                });

        sfCol.setEditable(true);
        TableColumn<SpectralDim, Double> swCol = new TableColumn<>("SW");
        swCol.setCellValueFactory(new PropertyValueFactory("Sw"));
        swCol.setCellFactory(tc -> new TextFieldRefTableCell(dsConverter));
        swCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, Double> t) -> {
                    Double value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setSw(value);
                    }
                });

        swCol.setEditable(true);

        TableColumn<SpectralDim, Double> tolCol = new TableColumn<>("Tol");
        tolCol.setCellValueFactory(new PropertyValueFactory("IdTol"));
        tolCol.setCellFactory(tc -> new TextFieldRefTableCell(dsConverter));
        tolCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, Double> t) -> {
                    Double value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setIdTol(value);
                    }
                });

        tolCol.setEditable(true);

        TableColumn<SpectralDim, String> patternCol = new TableColumn<>("Pattern");
        patternCol.setCellValueFactory(new PropertyValueFactory("Pattern"));
        patternCol.setCellFactory(TextFieldTableCell.forTableColumn());
        patternCol.setEditable(true);
        patternCol.setOnEditCommit((CellEditEvent<SpectralDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setPattern(value == null ? "" : value);
        });

        TableColumn<SpectralDim, String> relCol = new TableColumn<>("Bonded");
        relCol.setCellValueFactory(new PropertyValueFactory("RelationDim"));
        relCol.setCellFactory(ComboBoxTableCell.forTableColumn(relationChoiceItems));
        relCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, String> t) -> {
                    String value = t.getNewValue();
                    t.getRowValue().setRelation(value == null ? "" : value);
                });
        TableColumn<SpectralDim, String> spatialCol = new TableColumn<>("Spatial");
        spatialCol.setCellValueFactory(new PropertyValueFactory("SpatialRelationDim"));
        spatialCol.setCellFactory(ComboBoxTableCell.forTableColumn(relationChoiceItems));
        spatialCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, String> t) -> {
                    String value = t.getNewValue();
                    t.getRowValue().setSpatialRelation(value == null ? "" : value);
                });

        referenceTableView.getColumns().setAll(labelCol, nucCol, sfCol, swCol, tolCol, patternCol, relCol, spatialCol);
    }

    public boolean checkDataset() {
        boolean ok = false;
        String datasetName = peakList.getDatasetName();
        if ((datasetName == null) || datasetName.equals("")) {
            PolyChart chart = PolyChart.getActiveChart();
            DatasetBase dataset = chart.getDataset();
            if (dataset != null) {
                for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
                    if (peakAttr.getPeakList() == peakList) {
                        peakList.setDatasetName(dataset.getName());
                        ok = true;
                        break;
                    }
                }
            }
        } else {
            ok = true;
        }
        if (!ok) {
            alert("No dataset assigned to this peak list");
        }
        return ok;
    }

    void alert(String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(text);
        alert.showAndWait();
    }

    void renamePeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Rename Peak List");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    peakList.setName(peakListNameField.getText());
                    stage.setTitle(peakListNameField.getText());
                } else {
                    peakListNameField.setText(peakList.getName());
                }
            });
        }
    }

    void selectDataset() {
        if (peakList != null) {
            String name = (String) datasetNameField.getValue();
            peakList.setDatasetName(name);
        }
    }

    void setCondition() {
        if (peakList != null) {
            String condition = (String) conditionField.getValue();
            if (condition == null) {
                condition = "";
            }
            peakList.setSampleConditionLabel(condition);
        }
    }

    void setPeakListType(PeakListType peakListType) {
        try {
            peakListType.setPeakList(peakList);
            referenceTableView.refresh();
        } catch (IllegalArgumentException iaE) {
            GUIUtils.warn("Set Peak List Type ", iaE.getMessage());
        }
    }

    void selectSimDataset() {
        String name = (String) simDatasetNameField.getValue();
        if (name != null) {
            if (!name.equals("Sim")) {
                Dataset dataset = Dataset.getDataset(name);
                if (dataset != null) {
                    int nDim = dataset.getNDim();
                    if (peakListParFields.length < nDim) {
                        initPeakListParFields(nDim);
                    }
                    for (int i = 0; i < nDim; i++) {
                        peakListParFields[i][0].setText(String.valueOf(dataset.getSw(i)));
                        peakListParFields[i][1].setText(String.valueOf(dataset.getSf(i)));
                        peakListParFields[i][2].setText(dataset.getLabel(i));
                        for (int j = 0; j < peakListParFields[i].length; j++) {
                            peakListParFields[i][j].setDisable(true);
                        }
                    }

                }
                int dotIndex = name.indexOf(".");
                if (dotIndex != -1) {
                    name = name.substring(0, dotIndex);
                }
                simPeakListNameField.setText(name + "_sim");
                generateButton.setDisable(false);

            } else {
                simPeakListNameField.setText(subType + "_sim");
                generateButton.setDisable(false);
            }
        }
    }

    void initOptions() {
        transferLimitChoice.getItems().add("1");
        transferLimitChoice.getItems().add("2");
        transferLimitChoice.getItems().add("3");
        transferLimitChoice.getItems().add("4");
        transferLimitChoice.getItems().add("5");
        transferLimitChoice.setValue("2");
        distanceSlider.setShowTickLabels(true);
        distanceSlider.setShowTickMarks(true);
        distanceSlider.setMajorTickUnit(1.0);
        distanceSlider.setMinorTickCount(9);
        distanceSlider.setMinWidth(250.0);

    }

    void setType(String type, String subType) {
        this.type = type;
        this.subType = subType;
        typeLabel.setText(type);
        subTypeLabel.setText(subType);
        generateButton.setDisable(true);
        simDatasetNameField.setValue(null);
        updateOptions();
    }

    void initPeakListParFields(int nDim) {
        peakListParsPane.getChildren().clear();
        String[] headers = {"Dim", "SW", "SF", "Label"};
        int col = 0;
        for (String header : headers) {
            peakListParsPane.add(new Label(header), col++, 0);
        }
        peakListParFields = new TextField[nDim][3];
        peakListParFields = new TextField[nDim][3];
        for (int iDim = 0; iDim < nDim; iDim++) {
            Label dimLabel = new Label(String.valueOf(iDim + 1));
            dimLabel.setMinWidth(50);
            peakListParsPane.add(dimLabel, 0, iDim + 1);
            for (int i = 0; i < 3; i++) {
                peakListParFields[iDim][i] = new TextField();
                peakListParsPane.add(peakListParFields[iDim][i], 1 + i, iDim + 1);

            }
        }
    }

    void updateOptions() {
        optionBox.getChildren().clear();
        List<String> labels = new ArrayList<>();
        double swP = 10.0;
        double[] ratios = {1, 1, 1};

        if (subType != null) {
            if (subType.equals("TOCSY")) {
                Label label = new Label("Transfers");
                optionBox.getChildren().add(label);
                optionBox.getChildren().add(transferLimitChoice);
                labels.add("H");
                labels.add("H2");
            } else if (subType.equals("NOESY")) {
                Label label = new Label("Distance");
                optionBox.getChildren().add(label);
                optionBox.getChildren().add(distanceSlider);
                labels.add("H");
                labels.add("H2");
            } else if (subType.equals("HSQC-15N")) {
                labels.add("H");
                labels.add("N");
                ratios[1] = 2.0;
            } else if (subType.equals("HSQC-13C")) {
                labels.add("H");
                labels.add("C");
                ratios[1] = 10.0;
            } else if (subType.equals("HMBC")) {
                Label label = new Label("Transfers");
                optionBox.getChildren().add(label);
                optionBox.getChildren().add(transferLimitChoice);
                labels.add("H");
                labels.add("C");
                ratios[1] = 10.0;
            } else if (subType.startsWith("RNA")) {
                labels.add("H");
                labels.add("H2");
                optionBox.getChildren().add(useNCheckBox);
            } else if (subType.equals("HSQC")) {
                labels.add("H");
                labels.add("N");
                ratios[1] = 2.0;
            } else {
                labels.add("H");
                labels.add("N");
                ratios[1] = 2.0;
                if (type.endsWith("CO")) {
                    labels.add("C");
                    ratios[2] = 2.0;
                } else {
                    labels.add("CA");
                    ratios[2] = 5.0;
                }
            }
            int nDim = labels.size();
            initPeakListParFields(nDim);
            for (int iDim = 0; iDim < nDim; iDim++) {
                for (int i = 0; i < 3; i++) {
                    double sf = sfH * Nuclei.findNuclei(labels.get(iDim).substring(0, 1)).getFreqRatio();
                    switch (i) {
                        case 0:
                            double sw = swP * ratios[i] * sf;
                            peakListParFields[iDim][i].setText(String.valueOf(sw));
                            break;
                        case 1:
                            peakListParFields[iDim][i].setText(String.valueOf(sf));
                            break;
                        case 2:
                            peakListParFields[iDim][i].setText(labels.get(iDim));
                            break;
                        default:
                            break;
                    }
                    peakListParFields[iDim][i].setDisable(false);
                }
            }
            peakListParFields[0][1].setOnKeyPressed(k -> {
                if (k.getCode() == KeyCode.ENTER) {
                    try {
                        sfH = Double.parseDouble(peakListParFields[0][1].getText());
                    } catch (NumberFormatException nfE) {

                    }
                    updateOptions();
                }
            });
        }
    }

    PeakList makePeakListFromPars() {
        String listName = simPeakListNameField.getText();
        int nDim = peakListParFields.length;
        PeakList newPeakList = new PeakList(listName, nDim);
        try {
            for (int iDim = 0; iDim < nDim; iDim++) {
                newPeakList.getSpectralDim(iDim).setSw(Double.parseDouble(peakListParFields[iDim][0].getText()));
                newPeakList.getSpectralDim(iDim).setSf(Double.parseDouble(peakListParFields[iDim][1].getText()));
                newPeakList.getSpectralDim(iDim).setDimName(peakListParFields[iDim][2].getText());
            }
            newPeakList.setSampleConditionLabel("sim");
        } catch (NumberFormatException nfE) {
            System.out.println(nfE.getMessage());
            PeakList.remove(listName);
            newPeakList = null;
        }
        return newPeakList;
    }

    PeakList makePeakListFromDataset(Dataset dataset) {
        String listName = simPeakListNameField.getText();
        int nDim = dataset.getNDim();
        PeakList newPeakList = new PeakList(listName, nDim);
        for (int iDim = 0; iDim < nDim; iDim++) {
            newPeakList.getSpectralDim(iDim).setSw(dataset.getSw(iDim));
            newPeakList.getSpectralDim(iDim).setSf(dataset.getSf(iDim));
            newPeakList.getSpectralDim(iDim).setDimName(dataset.getLabel(iDim));
        }
        newPeakList.setSampleConditionLabel("sim");
        return newPeakList;
    }

    @FXML
    public void genPeaksAction(ActionEvent e) {
        String mode = subType;

        if ((mode != null) && !mode.equals("")) {
            String listName;
            PeakList newPeakList;
            Dataset dataset;
            String datasetName = "";
            if ((simDatasetNameField.getValue() == null) || simDatasetNameField.getValue().equals("Sim")) {
                newPeakList = makePeakListFromPars();
                if (newPeakList == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("Could not make peakList");
                    alert.showAndWait();
                    return;
                }
            } else {
                datasetName = simDatasetNameField.getValue().toString();
                dataset = Dataset.getDataset(datasetName);
                newPeakList = makePeakListFromDataset(dataset);
            }
            listName = newPeakList.getName();
            String script = null;
            if (type.equals("Protein")) {
                script = String.format("molGen.genProteinPeaks(\"%s\", \"%s\", listName=\"%s\")", mode, datasetName, listName);
            } else {
                switch (mode) {
                    case "NOESY":
                        double range = distanceSlider.getValue();
                        script = String.format("molGen.genDistancePeaks(\"%s\", listName=\"%s\", tol=%f)", datasetName, listName, range);
                        break;
                    case "TOCSY":
                        int limit = Integer.parseInt(transferLimitChoice.getValue().toString());
                        script = String.format("molGen.genTOCSYPeaks(\"%s\", listName=\"%s\", transfers=%d)", datasetName, listName, limit);
                        break;
                    case "HMBC":
                        int hmbcLimit = Integer.parseInt(transferLimitChoice.getValue().toString());
                        script = String.format("molGen.genHMBCPeaks(\"%s\", listName=\"%s\", transfers=%d)", datasetName, listName, hmbcLimit);
                        break;
                    case "HSQC-13C":
                        script = String.format("molGen.genHSQCPeaks(\"%s\", \"%s\", listName=\"%s\")", "C", datasetName, listName);
                        break;
                    case "HSQC-15N":
                        script = String.format("molGen.genHSQCPeaks(\"%s\", \"%s\", listName=\"%s\")", "N", datasetName, listName);
                        break;
                    case "RNA-NOESY-2nd-str":
                        int useN = useNCheckBox.isSelected() ? 1 : 0;
                        script = String.format("molGen.genRNASecStrPeaks(\"%s\", listName=\"%s\", useN=%d)", datasetName, listName, useN);
                        break;
                    default:
                }
            }
            if (script != null) {
                PythonInterpreter interp = MainApp.getInterpreter();
                interp.exec("import molpeakgen");
                interp.exec("molGen=molpeakgen.MolPeakGen()");
                interp.exec(script);
            }
        }

    }
}
