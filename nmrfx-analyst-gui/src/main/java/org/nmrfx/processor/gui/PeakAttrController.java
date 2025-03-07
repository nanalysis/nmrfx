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
import javafx.fxml.Initializable;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.FloatStringConverter;
import org.nmrfx.chart.DataSeries;
import org.nmrfx.chart.XYCanvasChart;
import org.nmrfx.chart.XYChartPane;
import org.nmrfx.chart.XYValue;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.io.PeakPatternReader;
import org.nmrfx.peaks.types.PeakListType;
import org.nmrfx.peaks.types.PeakListTypes;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.optimization.LorentzGaussND;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author johnsonb
 */
public class PeakAttrController implements Initializable, StageBasedController, PeakNavigable, PeakMenuTarget {
    private static final Logger log = LoggerFactory.getLogger(PeakAttrController.class);

    private static PeakListTypes peakListTypes = null;

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
    private ComboBox<String> datasetNameField;
    @FXML
    private ComboBox<String> conditionField;
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

    private XYCanvasChart scatterChart;

    PeakNavigator peakNavigator;

    PeakList peakList;
    Peak currentPeak;

    PeakDim currentPeakDim = null;


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
        peakNavigator = PeakNavigator.create(this).addShowPeakButton().addIDPeakButton().initialize(peakNavigatorToolBar, peakListMenuButton);
        initTable();
        initReferenceTable();
        setFieldActions();
        datasetNameField.setOnShowing(e -> updateDatasetNames());
        datasetNameField.setOnAction(e -> selectDataset());
        conditionField.setOnShowing(e -> updateConditionNames());
        conditionField.setOnAction(e -> setCondition());
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
        XYChartPane chartPane = new XYChartPane();

        scatterChart = chartPane.getChart();
        xAxis.setAutoRanging(true);
        yAxis.setAutoRanging(true);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        graphBorderPane.setCenter(chartPane);
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
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static PeakAttrController create() {
        PeakAttrController controller = Fxml.load(PeakAttrController.class, "PeakAttrScene.fxml")
                .withNewStage("Peak Attributes")
                .getController();
        controller.stage.show();
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
            return;
        }
        boolean clearIt = true;
        if (peak != null) {
            if (peak != currentPeak) {
                ObservableList<PeakDim> peakDimList = FXCollections.observableArrayList();
                Collections.addAll(peakDimList, peak.getPeakDims());
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
            if (addZZFit()) {
                return;
            }
            scatterChart.getData().clear();
            scatterChart.xAxis.setAutoRanging(true);
            scatterChart.yAxis.setAutoRanging(true);
            scatterChart.yAxis.setZeroIncluded(true);
            DataSeries series = new DataSeries();

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
                    XYValue value = new XYValue(xValue, yValue.get(), err);
                    series.add(value);
                }
            }
            scatterChart.getData().add(series);
            addExpFit(xValues);
        }
    }

    private boolean addExpFit(double[] xValues) {
        String comment = currentPeak.getComment();
        String zzPattern = "R1 +([0-9.]+)";
        Pattern pattern = Pattern.compile(zzPattern);
        Matcher matcher = pattern.matcher(comment.trim());
        if (matcher.matches()) {
            double r1 = Double.parseDouble(matcher.group(1));
            DataSeries lineSeries = new DataSeries();
            lineSeries.drawLine(true);
            lineSeries.drawSymbol(false);
            lineSeries.fillSymbol(false);
            double intensity = currentPeak.getIntensity();
            double xMax = xValues[xValues.length - 1];
            int nPoints = 100;
            for (int i = 0; i < nPoints; i++) {
                double delay = i * xMax / (nPoints - 1);
                double y = intensity * Math.exp(-delay * r1);
                XYValue value = new XYValue(delay, y);
                lineSeries.getData().add(value);
            }
            scatterChart.getData().add(lineSeries);
            return true;
        }
        return false;
    }

    private boolean addZZFit() {
        String comment = currentPeak.getComment();
        String zzPattern = "AA +I +([0-9.]+) +R1A +([0-9.]+) +R1B +([0-9.]+) +KeX +([0-9.]+) +pA +([0-9.]+)";
        String zzPattern2 = "AA +I +([0-9.]+) +R1A +([0-9.]+) +R1B +([0-9.]+) +KeXAB +([0-9.]+) +KeXBA +([0-9.]+) +pA +([0-9.]+)";

        Pattern pattern = Pattern.compile(zzPattern);
        Matcher matcher = pattern.matcher(comment.trim());
        int matched = 0;
        double intensity = 0;
        double r1 = 0.0;
        double r1A = 0;
        double r1B = 0;
        double kEx = 0;
        double kAB = 0;
        double kBA = 0;
        double pA = 0;
        if (matcher.matches()) {
            intensity = Double.parseDouble(matcher.group(1));
            r1 = Double.parseDouble(matcher.group(2));
            kEx = Double.parseDouble(matcher.group(3));
            pA = Double.parseDouble(matcher.group(4));
            matched = 1;
        } else {
            pattern = Pattern.compile(zzPattern2);
            matcher = pattern.matcher(comment.trim());
            if (matcher.matches()) {
                intensity = Double.parseDouble(matcher.group(1));
                r1A = Double.parseDouble(matcher.group(2));
                r1B = Double.parseDouble(matcher.group(3));
                kAB = Double.parseDouble(matcher.group(4));
                kBA = Double.parseDouble(matcher.group(5));
                pA = Double.parseDouble(matcher.group(6));
                matched = 2;
            }

        }
        if (matched > 0) {
            var peakBAOpt = PeakList.getLinkedPeakDims(currentPeak, 0).stream().filter(p -> p.getPeak() != currentPeak).findFirst();
            var peakABOpt = PeakList.getLinkedPeakDims(currentPeak, 1).stream().filter(p -> p.getPeak() != currentPeak).findFirst();
            if (peakBAOpt.isPresent() && peakABOpt.isPresent()) {
                Peak peakBA = peakBAOpt.get().getPeak();
                var peakBBOpt = PeakList.getLinkedPeakDims(peakBA, 1).stream().filter(p -> p.getPeak() != peakBA).findFirst();
                if (peakBBOpt.isPresent()) {
                    var peakAB = peakABOpt.get().getPeak();
                    var peakBB = peakBBOpt.get().getPeak();
                    Peak[] peaks = {currentPeak, peakBB, peakAB, peakBA};
                    double[] xValues = currentPeak.getPeakList().getMeasureValues();
                    scatterChart.getData().clear();
                    scatterChart.xAxis.setAutoRanging(true);
                    scatterChart.yAxis.setAutoRanging(true);

                    int iSig = 0;
                    String[] peakLabels = {"AA", "BB", "BA", "AB"};

                    for (Peak peak : peaks) {
                        DataSeries series = new DataSeries();
                        series.setStroke(XYCanvasChart.colors[iSig]);
                        series.setFill(XYCanvasChart.colors[iSig]);
                        series.setName(peakLabels[iSig]);

                        peak.getMeasures().ifPresent(measures -> {
                            double[] yValues = measures[0];
                            double[] errs = measures[1];
                            for (int i = 0; i < yValues.length; i++) {
                                double yValue = yValues[i];
                                double xValue = xValues != null ? xValues[i] : 1.0 * i;
                                XYValue value = new XYValue(xValue, yValue);
                                series.add(value);
                            }
                        });
                        scatterChart.getData().add(series);
                        DataSeries lineSeries = new DataSeries();
                        lineSeries.drawLine(true);
                        lineSeries.drawSymbol(false);
                        lineSeries.fillSymbol(false);
                        lineSeries.setStroke(XYCanvasChart.colors[iSig]);
                        lineSeries.setFill(XYCanvasChart.colors[iSig]);
                        lineSeries.setName(peakLabels[iSig]);

                        double xMax = xValues[xValues.length - 1];
                        int nPoints = 100;
                        for (int i = 0; i < nPoints; i++) {
                            double delay = i * xMax / (nPoints - 1);
                            double y;
                            if (matched == 1) {
                                y = intensity * LorentzGaussND.zzAmplitude(r1, pA, kEx, delay, iSig);
                            } else {
                                y = intensity * LorentzGaussND.zzAmplitude2(r1A, r1B, pA, kAB, kBA, delay, iSig);
                            }
                            XYValue value = new XYValue(delay, y);
                            lineSeries.getData().add(value);
                        }
                        iSig++;
                        scatterChart.getData().add(lineSeries);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void updateDatasetNames() {
        datasetNameField.getItems().clear();
        DatasetBase.datasets().forEach(d -> datasetNameField.getItems().add(d.getName()));
        if (peakList != null) {
            datasetNameField.setUserData(Boolean.TRUE);
            datasetNameField.setValue(peakList.getDatasetName());
            datasetNameField.setUserData(Boolean.FALSE);
        }
    }

    public void updateConditionNames() {
        conditionField.getItems().clear();
        Set<String> conditions = PeakList.peakLists().stream().
                map(PeakList::getSampleConditionLabel).
                filter(Objects::nonNull).collect(Collectors.toSet());
        conditions.stream().sorted().forEach(s -> conditionField.getItems().add(s));
        if (peakList != null) {
            conditionField.setValue(peakList.getSampleConditionLabel());
        }
    }

    public void refreshPeakListView(PeakList refreshPeakList) {
        if (referenceTableView == null) {
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
            datasetNameField.setUserData(Boolean.TRUE);
            datasetNameField.setValue(peakList.getDatasetName());
            datasetNameField.setUserData(Boolean.FALSE);
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

    public Optional<Peak> getPeak() {
        Peak peak = peakNavigator.getPeak();
        return Optional.ofNullable(peak);
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

    void aliasPeak(double direction) {
        if (currentPeakDim != null) {
            double ppm = currentPeakDim.getChemShiftValue();
            double sw = currentPeakDim.getSpectralDimObj().getSw();
            double sf = currentPeakDim.getSpectralDimObj().getSf();
            double deltaPPM = sw / sf;
            ppm += direction * deltaPPM;
            currentPeakDim.setChemShiftValue((float) ppm);
        }
    }

    void showFoldingMenu(TableCell<PeakDim, Float> tableCell, MouseEvent e, ContextMenu contextMenu, PeakDim peakDim) {
        currentPeakDim = peakDim;
        contextMenu.show(tableCell, e.getScreenX(), e.getScreenY());
    }

    void initTable() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem itemUp = new MenuItem("Alias Up");
        itemUp.setOnAction(e -> aliasPeak(1.0));
        MenuItem itemDown = new MenuItem("Alias Down");
        itemDown.setOnAction(e -> aliasPeak(-1.0));
        contextMenu.getItems().addAll(itemUp, itemDown);

        FloatStringConverter fsConverter = new FloatStringConverter2();
        peakTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        peakTableView.setEditable(true);
        TableColumn<PeakDim, String> dimNameCol = new TableColumn<>("Dim");
        dimNameCol.setCellValueFactory(new PropertyValueFactory<>("DimName"));
        dimNameCol.setEditable(false);

        TableColumn<PeakDim, String> labelCol = makeLabelColumn();

        TableColumn<PeakDim, Float> ppmCol = makePPMColumn(fsConverter, contextMenu);

        TableColumn<PeakDim, Float> widthCol = makeWidthColumn(fsConverter);

        TableColumn<PeakDim, Float> boundsCol = makeBoundsColumn(fsConverter);

        TableColumn<PeakDim, Float> shapeCol = makeShapeColumn(fsConverter);

        TableColumn<PeakDim, String> resonanceColumn = new TableColumn<>("ResID");
        resonanceColumn.setCellValueFactory(new PropertyValueFactory<>("ResonanceIDsAsString"));
        resonanceColumn.setEditable(false);

        TableColumn<PeakDim, String> userCol = makeUserColumn();

        peakTableView.getColumns().setAll(dimNameCol, labelCol, ppmCol, widthCol, boundsCol, shapeCol, resonanceColumn, userCol);
    }

    private static TableColumn<PeakDim, String> makeUserColumn() {
        TableColumn<PeakDim, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("User"));
        userCol.setCellFactory(TextFieldTableCell.forTableColumn());
        userCol.setEditable(true);
        userCol.setOnEditCommit((CellEditEvent<PeakDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setUser(value == null ? "" : value);
        });
        return userCol;
    }

    private static TableColumn<PeakDim, String> makeLabelColumn() {
        TableColumn<PeakDim, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(new PropertyValueFactory<>("Label"));
        labelCol.setCellFactory(tc -> new TextFieldTableCellPeakLabel(new DefaultStringConverter()));

        labelCol.setEditable(true);
        labelCol.setOnEditCommit((CellEditEvent<PeakDim, String> t) -> {
            String value = t.getNewValue();
            PeakDim peakDim = t.getRowValue();
            if (value == null) {
                value = "";
            }
            AtomResPattern.assignDim(peakDim, value);
            TablePosition<PeakDim, String> tPos = t.getTablePosition();
            int row = tPos.getRow();
            row++;
            if (row < t.getTableView().getItems().size()) {
                t.getTableView().getSelectionModel().selectBelowCell();
                t.getTableView().edit(row, labelCol);
            }

        });
        return labelCol;
    }

    private static TableColumn<PeakDim, Float> makeShapeColumn(FloatStringConverter fsConverter) {
        TableColumn<PeakDim, Float> shapeCol = new TableColumn<>("Shape");
        shapeCol.setCellValueFactory(new PropertyValueFactory<>("ShapeFactor"));
        shapeCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        shapeCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setShapeFactorValue(value);
                    }
                });

        shapeCol.setEditable(true);
        return shapeCol;
    }

    private static TableColumn<PeakDim, Float> makeBoundsColumn(FloatStringConverter fsConverter) {
        TableColumn<PeakDim, Float> boundsCol = new TableColumn<>("Bounds");
        boundsCol.setCellValueFactory(new PropertyValueFactory<>("BoundsHz"));
        boundsCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        boundsCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setBoundsHz(value);
                    }
                });

        boundsCol.setEditable(true);
        return boundsCol;
    }

    private static TableColumn<PeakDim, Float> makeWidthColumn(FloatStringConverter fsConverter) {
        TableColumn<PeakDim, Float> widthCol = new TableColumn<>("Width");
        widthCol.setCellValueFactory(new PropertyValueFactory<>("LineWidthHz"));
        widthCol.setCellFactory(tc -> new TextFieldTableCellFloat(fsConverter));
        widthCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setLineWidthHz(value);
                    }
                });
        widthCol.setEditable(true);
        return widthCol;
    }

    private TableColumn<PeakDim, Float> makePPMColumn(FloatStringConverter fsConverter, ContextMenu contextMenu) {
        TableColumn<PeakDim, Float> ppmCol = new TableColumn<>("PPM");
        ppmCol.setCellValueFactory(new PropertyValueFactory<>("ChemShift"));
        ppmCol.setCellFactory(tc -> {
            TableCell<PeakDim, Float> cell = new TextFieldTableCellFloat(fsConverter);
            cell.setOnMousePressed(e -> {
                if (e.isPopupTrigger()) {
                    TableRow<PeakDim> tableRow = cell.getTableRow();
                    PeakDim peakDim = tableRow == null ? null : tableRow.getItem();
                    showFoldingMenu(cell, e, contextMenu, peakDim);
                }
            });
            cell.setOnMouseReleased(e -> {
                if (e.isPopupTrigger()) {
                    TableRow<PeakDim> tableRow = cell.getTableRow();
                    PeakDim peakDim = tableRow == null ? null : tableRow.getItem();
                    showFoldingMenu(cell, e, contextMenu, peakDim);
                }
            });
            return cell;
        });
        ppmCol.setOnEditCommit(
                (CellEditEvent<PeakDim, Float> t) -> {
                    Float value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setChemShift(value);
                    }
                });

        ppmCol.setEditable(true);
        return ppmCol;
    }

    private void setFieldActions() {
        commentField.setOnKeyPressed(e -> {
            if (currentPeak != null && e.getCode() == KeyCode.ENTER) {
                currentPeak.setComment(commentField.getText().trim());
            }
        });
        intensityField.setOnKeyPressed(e -> {
            if (currentPeak != null && e.getCode() == KeyCode.ENTER) {
                try {
                    float value = Float.parseFloat(intensityField.getText().trim());
                    currentPeak.setIntensity(value);
                } catch (NumberFormatException nfE) {
                    log.warn("Unable to parse intensity field.", nfE);
                }
            }
        });
        volumeField.setOnKeyPressed(e -> {
            if (currentPeak != null && e.getCode() == KeyCode.ENTER) {
                try {
                    float value = Float.parseFloat(volumeField.getText().trim());
                    currentPeak.setVolume1(value);
                } catch (NumberFormatException nfE) {
                    log.warn("Unable to parse volume field.", nfE);
                }

            }

        });
    }

    void initReferenceTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        referenceTableView.setEditable(true);
        TableColumn<SpectralDim, String> dimNameCol = new TableColumn<>("Dim");
        dimNameCol.setCellValueFactory(new PropertyValueFactory<>("DimName"));
        dimNameCol.setEditable(false);

        TableColumn<SpectralDim, String> labelCol = new TableColumn<>("Name");
        labelCol.setCellValueFactory(new PropertyValueFactory<>("DimName"));
        labelCol.setCellFactory(TextFieldTableCell.forTableColumn());
        labelCol.setEditable(true);
        labelCol.setOnEditCommit((CellEditEvent<SpectralDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setDimName(value == null ? "" : value);
        });

        TableColumn<SpectralDim, String> nucCol = new TableColumn<>("Nucleus");
        nucCol.setCellValueFactory(new PropertyValueFactory<>("Nucleus"));
        nucCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nucCol.setEditable(true);
        nucCol.setOnEditCommit((CellEditEvent<SpectralDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setNucleus(value == null ? "" : value);
        });

        TableColumn<SpectralDim, Double> sfCol = new TableColumn<>("SF");
        sfCol.setCellValueFactory(new PropertyValueFactory<>("Sf"));
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
        swCol.setCellValueFactory(new PropertyValueFactory<>("Sw"));
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
        tolCol.setCellValueFactory(new PropertyValueFactory<>("IdTol"));
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
        patternCol.setCellValueFactory(new PropertyValueFactory<>("Pattern"));
        patternCol.setCellFactory(TextFieldTableCell.forTableColumn());
        patternCol.setEditable(true);
        patternCol.setOnEditCommit((CellEditEvent<SpectralDim, String> t) -> {
            String value = t.getNewValue();
            t.getRowValue().setPattern(value == null ? "" : value);
        });

        TableColumn<SpectralDim, String> relCol = new TableColumn<>("Bonded");
        relCol.setCellValueFactory(new PropertyValueFactory<>("RelationDim"));
        relCol.setCellFactory(ComboBoxTableCell.forTableColumn(relationChoiceItems));
        relCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, String> t) -> {
                    String value = t.getNewValue();
                    t.getRowValue().setRelation(value == null ? "" : value);
                });
        TableColumn<SpectralDim, String> spatialCol = new TableColumn<>("Spatial");
        spatialCol.setCellValueFactory(new PropertyValueFactory<>("SpatialRelationDim"));
        spatialCol.setCellFactory(ComboBoxTableCell.forTableColumn(relationChoiceItems));
        spatialCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, String> t) -> {
                    String value = t.getNewValue();
                    t.getRowValue().setSpatialRelation(value == null ? "" : value);
                });

        TableColumn<SpectralDim, String> foldCol = new TableColumn<>("Folded");
        foldCol.setCellValueFactory(new PropertyValueFactory<>("FoldMode"));
        foldCol.setCellFactory(ComboBoxTableCell.forTableColumn(new String[]{"folded", "aliased", "none"}));
        foldCol.setOnEditCommit(
                (CellEditEvent<SpectralDim, String> t) -> {
                    String value = t.getNewValue();
                    if (value.equals("none")) {
                        t.getRowValue().setFoldMode(value.charAt(0));
                    } else if (Objects.equals(t.getRowValue().getNucleus(), Nuclei.C13.getNumberName())) {
                        t.getRowValue().setFoldMode(value.charAt(0));
                        t.getRowValue().setFoldCount(1);
                    } else {
                        GUIUtils.warn("Folding is only enabled for C13","Folding is only enabled for C13");
                    }
                });

        referenceTableView.getColumns().setAll(labelCol, nucCol, sfCol, swCol, tolCol, patternCol, relCol, spatialCol, foldCol);
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
        if (datasetNameField.getUserData() == Boolean.TRUE) {
            return;
        }

        if (peakList != null) {
            String name = datasetNameField.getValue();
            peakList.setDatasetName(name);
            peakList.linkToDataset(name);
            referenceTableView.refresh();
            refreshPeakView();
        }
    }

    void setCondition() {
        if (peakList != null) {
            String condition = conditionField.getValue();
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

        }, this::setPeakListName);
        referenceTableView.refresh();
    }

    void setPeakListName() {
        String peakListTypeName = peakListTypeChoice.getValue();
        PeakListType.setPeakList(peakList, peakListTypeName);
    }


}
