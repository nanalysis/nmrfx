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
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.converter.DoubleStringConverter;
import java.text.DecimalFormat;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.util.converter.FloatStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpectralDim;
import org.nmrfx.processor.datasets.peaks.io.PeakReader;
import org.nmrfx.processor.datasets.peaks.io.PeakWriter;
import org.python.util.PythonInterpreter;

/**
 *
 * @author johnsonb
 */
public class PeakAttrController implements Initializable, PeakNavigable {

    static final DecimalFormat formatter = new DecimalFormat();

    private Stage stage;
    @FXML
    private ToolBar menuBar;
    @FXML
    private ToolBar peakNavigatorToolBar;
    @FXML
    private TableView<PeakDim> peakTableView;
    @FXML
    private TextField intensityField;
    @FXML
    private TextField volumeField;
    @FXML
    private TextField commentField;
    @FXML
    private TextField peakListNameField;
    @FXML
    private ComboBox datasetNameField;
    @FXML
    private ToolBar peakReferenceToolBar;
    @FXML
    private TableView<SpectralDim> referenceTableView;

    @FXML
    private BorderPane graphBorderPane;
    @FXML
    private ToolBar graphNavigatorToolBar;

    @FXML
    private BorderPane simBorderPane;
    @FXML
    private TextField simPeakListNameField;
    @FXML
    private ComboBox simDatasetNameField;

    @FXML
    private ChoiceBox simTypeChoice;

    @FXML
    private HBox optionBox;

    private Slider distanceSlider = new Slider(2, 7.0, 5.0);
    private ChoiceBox transferLimitChoice = new ChoiceBox();

    private ScatterChart<Number, Number> scatterChart;

    PeakNavigator peakNavigator;
    PeakNavigator graphNavigator;

    PeakList peakList;
    Peak currentPeak;
    ToggleButton deleteButton;
    ComboTableCell comboTableCell = new ComboTableCell();
    ObservableList<String> relationChoiceItems = FXCollections.observableArrayList("", "D1", "D2", "D3", "D4");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initMenuBar();
        peakNavigator = new PeakNavigator(this);
        peakNavigator.initPeakNavigator(peakNavigatorToolBar);
        graphNavigator = new PeakNavigator(this);
        graphNavigator.initPeakNavigator(graphNavigatorToolBar, peakNavigator);
        initTable();
        initReferenceTable();
        setFieldActions();
        datasetNameField.setOnShowing(e -> updateDatasetNames());
        datasetNameField.setOnAction(e -> {
            selectDataset();
        });
        peakListNameField.setOnKeyReleased(kE -> {
            if (kE.getCode() == KeyCode.ENTER) {
                renamePeakList();

            }
        });

        simDatasetNameField.setOnShowing(e -> updateSimDatasetNames());
        simDatasetNameField.setOnAction(e -> {
            selectSimDataset();
        });

        simTypeChoice.getItems().add("HSQC");
        simTypeChoice.getItems().add("RNA-NOESY 2nd Str");
        simTypeChoice.getItems().add("NOESY");
        simTypeChoice.getItems().add("TOCSY");
        simTypeChoice.setOnAction(e -> updateOptions());

        final NumberAxis xAxis = new NumberAxis(0, 10, 1);
        final NumberAxis yAxis = new NumberAxis(-100, 500, 100);
        scatterChart = new ScatterChart<>(xAxis, yAxis);
        xAxis.setAutoRanging(true);
        yAxis.setAutoRanging(true);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        scatterChart.setAnimated(false);
        graphBorderPane.setCenter(scatterChart);
        initOptions();

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
            intensityField.setText(String.valueOf(peak.getIntensity()));
            volumeField.setText(String.valueOf(peak.getVolume1()));
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

            double[] yValues = currentPeak.getMeasures().get();
            for (int i = 0; i < yValues.length; i++) {
                XYChart.Data value = new XYChart.Data(1.0 * i, yValues[i]);
                series.getData().add(value);
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

    public void updateSimDatasetNames() {
        simDatasetNameField.getItems().clear();
        Dataset.datasets().stream().forEach(d -> {
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
        peakNavigator.initIfEmpty();
    }

    public void setPeakList(PeakList peakList) {
        this.peakList = peakList;
        if (peakList != null) {
            currentPeak = peakList.getPeak(0);
            stage.setTitle(peakList.getName());
        } else {
            stage.setTitle("Peak Inspector");
        }

    }

    void initMenuBar() {
        MenuButton fileMenu = new MenuButton("File");
        MenuItem saveList = new MenuItem("Save...");
        saveList.setOnAction(e -> saveList());
        fileMenu.getItems().add(saveList);

        MenuItem readListItem = new MenuItem("Open...");
        readListItem.setOnAction(e -> readList());
        fileMenu.getItems().add(readListItem);

        menuBar.getItems().add(fileMenu);

        MenuButton editMenu = new MenuButton("Edit");

        MenuItem compressMenuItem = new MenuItem("Compress");
        compressMenuItem.setOnAction(e -> compressPeakList());
        editMenu.getItems().add(compressMenuItem);

        MenuItem degapMenuItem = new MenuItem("Degap");
        degapMenuItem.setOnAction(e -> renumberPeakList());
        editMenu.getItems().add(degapMenuItem);

        MenuItem compressAndDegapMenuItem = new MenuItem("Compress and Degap");
        compressAndDegapMenuItem.setOnAction(e -> compressAndDegapPeakList());
        editMenu.getItems().add(compressAndDegapMenuItem);

        MenuItem deleteMenuItem = new MenuItem("Delete List");
        deleteMenuItem.setOnAction(e -> deletePeakList());
        editMenu.getItems().add(deleteMenuItem);

        MenuItem unlinkPeakMenuItem = new MenuItem("Unlink List");
        unlinkPeakMenuItem.setOnAction(e -> unLinkPeakList());
        editMenu.getItems().add(unlinkPeakMenuItem);

        MenuItem duplicateMenuItem = new MenuItem("Duplicate");
        duplicateMenuItem.setOnAction(e -> duplicatePeakList());
        editMenu.getItems().add(duplicateMenuItem);

        MenuItem clusterMenuItem = new MenuItem("Cluster");
        clusterMenuItem.setOnAction(e -> clusterPeakList());
        editMenu.getItems().add(clusterMenuItem);

        menuBar.getItems().add(editMenu);

        MenuButton measureMenu = new MenuButton("Measure");
        MenuItem measureIntensityItem = new MenuItem("Intensities");
        measureIntensityItem.setOnAction(e -> measureIntensities());
        measureMenu.getItems().add(measureIntensityItem);

        MenuItem measureVolumeItem = new MenuItem("Volumes");
        measureVolumeItem.setOnAction(e -> measureVolumes());
        measureMenu.getItems().add(measureVolumeItem);

        MenuItem measureEVolumeItem = new MenuItem("EVolumes");
        measureEVolumeItem.setOnAction(e -> measureEVolumes());
        measureMenu.getItems().add(measureEVolumeItem);

        menuBar.getItems().add(measureMenu);
    }

    class FloatStringConverter2 extends FloatStringConverter {

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

    class TextFieldTableCellFloat extends TextFieldTableCell<PeakDim, Float> {

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
    };

    class TextFieldRefTableCell extends TextFieldTableCell<SpectralDim, Double> {

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
    };

    class ComboTableCell<SpectralDim, String> extends ComboBoxTableCell<SpectralDim, String> {

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
        }
    };

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
        labelCol.setCellFactory(TextFieldTableCell.forTableColumn());
        labelCol.setEditable(true);
        labelCol.setOnEditCommit((CellEditEvent<PeakDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setLabel(value == null ? "" : value);
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

        referenceTableView.getColumns().setAll(dimNameCol, nucCol, sfCol, swCol, tolCol, patternCol, relCol, spatialCol);
    }

    void saveList() {
        if (peakList != null) {
            try {
                FileChooser fileChooser = new FileChooser();
                File file = fileChooser.showSaveDialog(null);
                if (file != null) {
                    String listFileName = file.getPath();

                    try (FileWriter writer = new FileWriter(listFileName)) {
                        PeakWriter peakWriter = new PeakWriter();
                        peakWriter.writePeaksXPK2(writer, peakList);
                        writer.close();
                    }
                    if (peakList.hasMeasures()) {
                        if (listFileName.endsWith(".xpk2")) {
                            String measureFileName = listFileName.substring(0, listFileName.length() - 4) + "mpk2";
                            try (FileWriter writer = new FileWriter(measureFileName)) {
                                PeakWriter peakWriter = new PeakWriter();
                                peakWriter.writePeakMeasures(writer, peakList);
                                writer.close();
                            }
                        }
                    }
                }

            } catch (IOException | InvalidPeakException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            }
        }
    }

    void readList() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            String listFileName = file.getPath();
            try {
                PeakReader peakReader = new PeakReader();

                PeakList newPeakList = peakReader.readXPK2Peaks(listFileName);
                if (newPeakList != null) {
                    peakNavigator.setPeakList(newPeakList);
                    String mpk2File = listFileName.substring(0, listFileName.length() - 4) + "mpk2";
                    Path mpk2Path = Paths.get(mpk2File);
                    if (Files.exists(mpk2Path)) {
                        peakReader.readMPK2(peakList, mpk2Path.toString());
                    }
                }

            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    void measureIntensities() {
        peakList.quantifyPeaks("center");
        refreshPeakView();
    }

    void measureVolumes() {
        peakList.quantifyPeaks("volume");
        refreshPeakView();
    }

    void measureEVolumes() {
        peakList.quantifyPeaks("evolume");
        refreshPeakView();
    }

    void compressPeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Permanently remove deleted peaks");
            alert.showAndWait().ifPresent(response -> {
                peakList.compress();
                refreshPeakView();
            });
        }
    }

    void renumberPeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Renumber peak list (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                peakList.reNumber();
                refreshPeakView();
            });
        }
    }

    void compressAndDegapPeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove deleted peaks and renumber (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                peakList.compress();
                peakList.reNumber();
                refreshPeakView();
            });
        }
    }

    void deletePeakList() {
        if (peakList != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Peak List");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    PeakList.remove(peakList.getName());
                    PeakList list = null;
                    peakNavigator.setPeakList(list);
                }
            });
        }
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

    void selectSimDataset() {
        String name = (String) simDatasetNameField.getValue();
        if (name != null) {
            simPeakListNameField.setText(name + "sim");
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

    void updateOptions() {
        optionBox.getChildren().clear();
        String mode = (String) simTypeChoice.getValue();
        if (mode != null) {
            if (mode.equals("TOCSY")) {
                Label label = new Label("Transfers");
                optionBox.getChildren().add(label);
                optionBox.getChildren().add(transferLimitChoice);
            } else if (mode.equals("NOESY")) {
                Label label = new Label("Distance");
                optionBox.getChildren().add(label);
                optionBox.getChildren().add(distanceSlider);
            }
        }
    }

    void unLinkPeakList() {
        if (peakList != null) {
            peakList.unLinkPeaks();
        }
    }

    void clusterPeakList() {
        if (peakList != null) {
            if (peakList.hasSearchDims()) {
                peakList.clusterPeaks();
            } else {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setHeaderText("Enter dimensions and tolerances (like: HN 0.1 N 0.5)");
                Optional<String> result = dialog.showAndWait();
                if (result.isPresent()) {
                    try {
                        peakList.setSearchDims(result.get());
                        peakList.clusterPeaks();
                    } catch (IllegalArgumentException iE) {
                        ExceptionDialog edialog = new ExceptionDialog(iE);
                        edialog.showAndWait();
                    }
                }
            }
        }
    }

    void duplicatePeakList() {
        if (peakList != null) {
            TextInputDialog dialog = new TextInputDialog();
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                PeakList newPeakList = peakList.copy(result.get(), false, false);
                if (newPeakList != null) {
                    peakNavigator.setPeakList(newPeakList);
                }
            }
        }
    }

    @FXML
    public void genPeaksAction(ActionEvent e) {
        String mode = (String) simTypeChoice.getValue();
        if (mode != null) {
            Dataset dataset = Dataset.getDataset(simDatasetNameField.getValue().toString());
            if (dataset == null) {
                // warning
                return;
            }
            String datasetName = dataset.getName();
            String listName = simPeakListNameField.getText();
            String script = null;
            switch (mode) {
                case "NOESY":
                    double range = distanceSlider.getValue();
                    script = String.format("molGen.genDistancePeaks(\"%s\", listName=\"%s\", tol=%f)", datasetName, listName, range);
                    break;
                case "TOCSY":
                    int limit = Integer.parseInt(transferLimitChoice.getValue().toString());
                    script = String.format("molGen.genTOCSYPeaks(\"%s\", listName=\"%s\", transfers=%d)", datasetName, listName, limit);
                    break;
                case "HSQC":
                    script = String.format("molGen.genHCPeaks(\"%s\", listName=\"%s\")", datasetName, listName);
                    break;
                case "RNA-NOESY 2nd Str":
                    script = String.format("molGen.genRNASecStrPeaks(\"%s\", listName=\"%s\")", datasetName, listName);
                    break;
                default:
                    break;
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
