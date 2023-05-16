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
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.FloatStringConverter;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.io.PeakPatternReader;
import org.nmrfx.peaks.types.PeakListType;
import org.nmrfx.peaks.types.PeakListTypes;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author johnsonb
 */
public class PeakAttrController implements Initializable, PeakNavigable, PeakMenuTarget {

    private static final Logger log = LoggerFactory.getLogger(PeakAttrController.class);

    static PeakListTypes peakListTypes = null;
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
    private ChoiceBox<String> peakListTypeChoice;
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
    private ChoiceBox<PEAK_NORM> normChoice;

    private ScatterChart<Number, Number> scatterChart;

    PeakNavigator peakNavigator;

    PeakList peakList;
    Peak currentPeak;
    ToggleButton deleteButton;
    ObservableList<String> relationChoiceItems = FXCollections.observableArrayList("", "D1", "D2", "D3", "D4");

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
        MenuButton peakListMenuButton = initMenuBar();
        peakNavigator = PeakNavigator.create(this).addShowPeakButton().initialize(peakNavigatorToolBar, peakListMenuButton);
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

        try {
            if (peakListTypes == null) {
                peakListTypes = PeakPatternReader.loadYaml();
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        if (peakListTypes != null) {
            for (PeakListType peakListType : peakListTypes.getTypes()) {
                peakListTypeChoice.getItems().add(peakListType.getName());
            }
        }
        peakListTypeChoice.setOnAction(this::setPeakListType);
        if (!MainApp.isAnalyst()) {
            tabPane.getTabs().remove(3);
        }
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
            log.warn(ioE.getMessage(), ioE);
        }

        return controller;

    }

    public void selectTab(String tabName) {
        var tabOpt = tabPane.getTabs().stream().filter(t -> t.getText().equals(tabName)).findFirst();
        tabOpt.ifPresent(t -> tabPane.getSelectionModel().select(t));
    }

    @Override
    public void copyPeakTableView() {
        TableUtils.copyTableToClipboard(peakTableView, true);
    }

    @Override
    public void deletePeaks() {
        peakNavigator.getPeak().delete();
    }

    @Override
    public void restorePeaks() {
        peakNavigator.getPeak().setStatus(0);
    }

    public void refreshPeakView() {
        refreshPeakView(currentPeak);
    }

    @Override
    public void refreshChangedListView() {
        int index = currentPeak.getIndex();
        if (index >= peakList.size()) {
            index = peakList.size() - 1;
        }
        Peak peak = peakList.getPeak(index);
        peakNavigator.setPeak(peak);
        refreshPeakView(peak);
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
            peakListTypeChoice.setOnAction(null);
            peakListTypeChoice.setValue(peakList.getExperimentType());
            peakListTypeChoice.setOnAction(this::setPeakListType);
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

    public void initIfEmpty() {
        peakNavigator.setPeakList();
    }

    public PeakList getPeakList() {
        return peakList;
    }

    public void setPeakList(PeakList peakList) {
        peakNavigator.setPeakList(peakList);
    }

    MenuButton initMenuBar() {
        PeakMenuBar peakMenuBar = new PeakMenuBar(this);
        peakMenuBar.initMenuBar(menuBar, true);
        return peakMenuBar.getPeakListMenu();
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
            }
        }
    }

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
            }
        }
    }

    void initTable() {
        FloatStringConverter fsConverter = new FloatStringConverter2();
        peakTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
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

        TableColumn<PeakDim, Float> shapeCol = new TableColumn<>("Shape");
        shapeCol.setCellValueFactory(new PropertyValueFactory("ShapeFactor"));
        shapeCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        shapeCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setShapeFactorValue(value);
                    }
                });

        shapeCol.setEditable(true);

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

        peakTableView.getColumns().setAll(dimNameCol, labelCol, ppmCol, widthCol, boundsCol, shapeCol, resonanceColumn, userCol);
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
                        log.warn("Unable to parse intensity field.", nfE);
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
                        log.warn("Unable to parse volume field.", nfE);
                    }

                }
            }
        });
    }

    void initReferenceTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
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

    void setPeakListType(ActionEvent actionEvent) {
        String peakListTypeName = peakListTypeChoice.getValue();
        peakListTypes.getTypes().stream().filter(pType -> pType.getName().equals(peakListTypeName)).findFirst().ifPresentOrElse(peakListType -> {
            try {
                peakListType.setPeakList(peakList);
            } catch (IllegalArgumentException iaE) {
                GUIUtils.warn("Set Peak List Type ", iaE.getMessage());
            }

        },this::setPeakListName);
        referenceTableView.refresh();
    }

    void setPeakListName() {
        String peakListTypeName = peakListTypeChoice.getValue();
        PeakListType.setPeakList(peakList, peakListTypeName);
    }



}
