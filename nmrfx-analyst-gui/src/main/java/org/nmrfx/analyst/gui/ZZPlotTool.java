/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.analyst.gui;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.binding.BindingUtils;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakFitException;
import org.nmrfx.processor.datasets.peaks.PeakFitParameters;
import org.nmrfx.processor.datasets.peaks.PeakLinker;
import org.nmrfx.processor.datasets.peaks.PeakListTools;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.spectra.PeakDisplayTool;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.optimization.FitPar;
import org.nmrfx.processor.optimization.LorentzGaussND;
import org.nmrfx.processor.optimization.PeakFitPars;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 * @author brucejohnson
 */
public class ZZPlotTool {

    private static final Logger log = LoggerFactory.getLogger(ZZPlotTool.class);
    private final KeyCodeCombination copyKeyCodeCombination = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    Stage stage = null;
    XYCanvasChart activeChart = null;
    BorderPane borderPane = new BorderPane();
    TableView<PeakFitPars> tableView;
    Scene stageScene = new Scene(borderPane, 500, 500);
    List<String> colNamesFull;
    List<String> colNamesRatio;

    List<String> colNames;
    CheckBox fitRatio;

    boolean tableInRatioMode = false;

    SimpleDoubleProperty proteinConc = new SimpleDoubleProperty(0.0);
    SimpleDoubleProperty ligandConc = new SimpleDoubleProperty(0.0);
    SimpleDoubleProperty kD = new SimpleDoubleProperty(0.0);
    SimpleDoubleProperty freeLigandConc = new SimpleDoubleProperty(0.0);

    PeakListTools.FitZZPeakRatioResult fitZZPeakRatioResult;

    public ZZPlotTool() {
        colNamesFull = List.of("Peak", "Label", "I", "KAB", "KAB:Err", "KA", "KBA", "KBA:Err", "KK", "KK:Err", "R1A", "R1A:Err", "R1B", "R1B:Err", "P", "P:Err");
        colNamesRatio = List.of("Peak", "Label", "KAB", "KAB:Err", "KA", "KBA", "KBA:Err", "KK", "KK:Err");
        colNames = colNamesFull;
    }

    public void show(String xAxisName, String yAxisName) {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("ZZ Plot Tool");
            MenuButton fileMenu = new MenuButton("File");
            VBox vBox = new VBox();
            ToolBar toolBar = new ToolBar();
            HBox hBox = new HBox();
            MenuItem exportSVGButton = new MenuItem("Export SVG Plot");
            MenuItem exportTableButton = new MenuItem("Export Table");
            fileMenu.getItems().addAll(exportSVGButton, exportTableButton);

            Button fitButton = new Button("Fit ");
            fitRatio = new CheckBox("Fit Ratio");

            Button showPeakButton = new Button("Goto");
            showPeakButton.setOnAction(e -> gotoPeak());

            toolBar.getItems().addAll(fileMenu, fitButton, fitRatio, showPeakButton, ToolBarUtils.makeFiller(15));

            Label pLabel = new Label("Protein");
            Label lLabel = new Label("Ligand");
            Label kdLabel = new Label("Kd");
            Label freeLigandLabel = new Label("Free Ligand");
            TextField pValue = GUIUtils.getDoubleTextField(proteinConc);
            pValue.setPrefWidth(70.0);
            TextField lValue = GUIUtils.getDoubleTextField(ligandConc);
            lValue.setPrefWidth(70.0);
            TextField kdValue = GUIUtils.getDoubleTextField(kD);
            kdValue.setPrefWidth(70.0);
            TextField freeLigandTextField = GUIUtils.getDoubleTextField(freeLigandConc);
            freeLigandTextField.setPrefWidth(70.0);
            proteinConc.addListener(e -> updateLigandConc());
            ligandConc.addListener(e -> updateLigandConc());
            kD.addListener(e -> updateLigandConc());

            hBox.getChildren().addAll(pLabel, pValue, ToolBarUtils.makeFiller(10, 15), lLabel, lValue,
                    ToolBarUtils.makeFiller(10, 15), kdLabel, kdValue, ToolBarUtils.makeFiller(10, 15), freeLigandLabel, freeLigandTextField);
            vBox.getChildren().addAll(toolBar, hBox);


            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();

            exportSVGButton.setOnAction(e -> activeChart.exportSVG());
            exportTableButton.setOnAction(e -> savePathTable());
            fitButton.setOnAction(e -> fitPeaks());

            borderPane.setTop(vBox);
            tableView = new TableView<>();
            SplitPane sPane = new SplitPane(chartPane, tableView);
            sPane.setOrientation(Orientation.VERTICAL);
            borderPane.setCenter(sPane);
            stage.setScene(stageScene);
            updateChart(xAxisName, yAxisName);
            initTable();
        }
        stage.show();
        stage.toFront();
    }

    public XYCanvasChart getChart() {
        return activeChart;
    }

    public void clear() {
        if (activeChart != null) {
            activeChart.getData().clear();
        }
    }

    void updateChart(String xAxisName, String yAxisName) {
        Axis xAxis = activeChart.getXAxis();
        Axis yAxis = activeChart.getYAxis();
        activeChart.setShowLegend(false);

        xAxis.setLabel(xAxisName);
        yAxis.setLabel(yAxisName);
        xAxis.setZeroIncluded(true);
        yAxis.setZeroIncluded(true);
        xAxis.setAutoRanging(true);
        yAxis.setAutoRanging(true);
        DataSeries series = new DataSeries();
        activeChart.getData().clear();
        //Prepare XYChart.Series objects by setting data
        activeChart.getData().add(series);
        activeChart.autoScale(true);
    }

    public void updateTable(ObservableList<PeakFitPars> paths) {
        tableView.getItems().setAll(paths);
    }

    private void updateLigandConc() {
        if ((proteinConc.get() > 1.0e-9) && (ligandConc.get() > 1.0e-9) && (kD.get() > 1.0e-9)) {
            freeLigandConc.set(ligandConc.get() - BindingUtils.boundLigand(proteinConc.get(), ligandConc.get(), kD.get()));
        }
    }

    public double getLigandConc() {
        return freeLigandConc.get();
    }

    void initTable() {
        if (fitRatio.isSelected()) {
            tableInRatioMode = true;
            colNames = colNamesRatio;
        } else {
            tableInRatioMode = false;
            colNames = colNamesFull;
        }
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListChangeListener selectionListener = (ListChangeListener.Change c) -> selectionChanged();
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        tableView.getColumns().clear();
        for (String colName : colNames) {
            if (colName.equals("Peak")) {
                TableColumn<PeakFitPars, String> col = new TableColumn<>(colName);
                col.setCellValueFactory(e -> {
                    String peakName;
                    if (e.getValue().peak() == null) {
                        peakName = "Group";
                    } else {
                        peakName = e.getValue().peak().getName();
                    }
                    return new SimpleStringProperty(peakName);
                });
                tableView.getColumns().add(col);
            } else if (colName.equals("Label")) {
                TableColumn<PeakFitPars, String> col = new TableColumn<>(colName);
                col.setCellValueFactory(e -> {
                    String peakLabel;
                    if (e.getValue().peak() == null) {
                        peakLabel = "";
                    } else {
                        peakLabel = e.getValue().peak().getPeakDim(0).getLabel();
                    }
                    return new SimpleStringProperty(peakLabel);
                });
                tableView.getColumns().add(col);
            } else {
                TableColumn<PeakFitPars, Number> col = new TableColumn<>(colName);
                tableView.getColumns().add(col);
                col.setCellValueFactory(p -> {
                    // p.getValue() returns the Path instance for a particular TableView row

                    if (p != null) {
                        boolean isErr = colName.endsWith(":Err");
                        String parName = colName;
                        if (isErr) {
                            parName = colName.substring(0, colName.indexOf(":"));
                        }
                        Double v = null;
                        FitPar fitPar = p.getValue().fitPar(parName);
                        if (fitPar != null) {
                            v = isErr ? fitPar.error() : fitPar.value();
                        }

                        if (v == null) {
                            v = 0.0;
                        }
                        return new SimpleDoubleProperty(v);
                    } else {
                        return null;
                    }
                });
                col.setCellFactory(c
                        -> new TableCell<PeakFitPars, Number>() {
                    @Override
                    public void updateItem(Number value, boolean empty) {
                        super.updateItem(value, empty);
                        if (empty || (value == null)) {
                            setText(null);
                        } else {
                            setText(String.format("%.4f", value.doubleValue()));
                        }
                    }
                });
            }

        }

        tableView.setOnKeyPressed(this::keyPressed);

    }

    public void keyPressed(KeyEvent keyEvent) {
        KeyCode code = keyEvent.getCode();
        if (code == null) {
            return;
        }
        if (code == KeyCode.C) {
            if (copyKeyCodeCombination.match(keyEvent)) {
                TableUtils.copyTableToClipboard(tableView, false);
            }
            keyEvent.consume();
        } else if ((code == KeyCode.BACK_SPACE) || (code == KeyCode.DELETE)) {
            List<PeakFitPars> selPaths = getSelected();
        }


    }

    void fitPeaks() {

        if (fitRatio.isSelected()) {
            if (!tableInRatioMode) {
                initTable();
            }
            simFitPeaks();
        } else {
            if (tableInRatioMode) {
                initTable();
            }
            fitPeaksFull();
        }
    }

    void fitPeaksFull() {
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        var chart = controller.getActiveChart();
        var peakListOpt = chart.getPeakListAttributes().stream().findFirst();
        ObservableList<PeakFitPars> fitPars = FXCollections.observableArrayList();
        Set<Peak> used = new HashSet<>();
        peakListOpt.ifPresent(peakAttributes -> {
            PeakList peakList = peakAttributes.getPeakList();
            peakList.peaks().stream().filter(peak -> !used.contains(peak))
                    .map(PeakLinker::getLinkedGroup)
                    .filter(g -> g.size() == 4)
                    .forEach(g -> {
                        used.addAll(g);
                        fitPars.add(fitPeakGroup(peakAttributes, peakList, g));
                    });
            addDerivedPars(fitPars);
        });
        tableView.setItems(fitPars);
    }

    PeakFitPars fitPeakGroup(PeakListAttributes peakListAttributes, PeakList peakList, Set<Peak> peaks) {
        Dataset dataset = (Dataset) peakListAttributes.getDatasetAttributes().getDataset();
        PeakFitParameters fitPars = new PeakFitParameters();
        fitPars.arrayedFitMode(PeakFitParameters.ARRAYED_FIT_MODE.ZZ_INTENSITY);
        try {
            List<PeakFitPars> result = PeakListTools.fitZZPeakIntensities(peakList, dataset, peaks);
            return result.get(0);
        } catch (IOException | PeakFitException e) {
            GUIUtils.warn("ZZFit", e.getMessage());
            return null;
        }
    }

    void simFitPeaks() {
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        var chart = controller.getActiveChart();
        var peakListOpt = chart.getPeakListAttributes().stream().findFirst();
        ObservableList<PeakFitPars> fitPars = FXCollections.observableArrayList();
        Set<Peak> used = new HashSet<>();
        List<Set<Peak>> allPeaks = new ArrayList<>();
        peakListOpt.ifPresent(peakAttributes -> {
            Dataset dataset = (Dataset) peakAttributes.getDatasetAttributes().getDataset();
            PeakList peakList = peakAttributes.getPeakList();
            for (Peak peak : peakList.peaks()) {
                if (used.contains(peak)) {
                    continue;
                }
                var group = PeakLinker.getLinkedGroup(peak);
                if (group.size() == 4) {
                    var groupSet = Collections.singletonList(group);
                    try {
                        PeakListTools.FitZZPeakRatioResult result = PeakListTools.fitZZPeakRatios(peakList, dataset, groupSet, false);
                        fitPars.add(result.peakFitPars());
                        allPeaks.add(group);
                    } catch (IOException | PeakFitException e) {
                        throw new RuntimeException(e);
                    }

                }
                used.addAll(group);
            }
            try {
                fitZZPeakRatioResult = PeakListTools.fitZZPeakRatios(peakList, dataset, allPeaks, true);
                fitPars.add(0, fitZZPeakRatioResult.peakFitPars());
            } catch (IOException | PeakFitException e) {
                throw new RuntimeException(e);
            }
            addDerivedPars(fitPars);
        });

        tableView.setItems(fitPars);

    }

    void addDerivedPars(List<PeakFitPars> peakFitParsList) {
        for (PeakFitPars peakFitPars : peakFitParsList) {
            addDerivedPars(peakFitPars);
        }
    }

    void addDerivedPars(PeakFitPars peakFitPars) {
        String[] addParNames = {"KA", "KAB", "KBA", "KK"};
        double lConc = getLigandConc();
        for (String parName : addParNames) {
            if (lConc < 1e-9) {
                FitPar fitPar = new FitPar(parName, 0.0, 0.0);
                peakFitPars.fitPars().put(parName, fitPar);
                continue;
            }
            if (parName.equals("KA") && peakFitPars.fitPar("KAB") != null) {
                double ka = peakFitPars.fitPar("KAB").value() / lConc;

                FitPar fitPar = new FitPar(parName, ka, 0.0);
                peakFitPars.fitPars().put(parName, fitPar);
            } else if (parName.equals("KK") && peakFitPars.fitPar("KK") == null) {
                double kab = peakFitPars.fitPar("KAB").value();
                double kba = peakFitPars.fitPar("KBA").value();
                double kk = kab * kba;
                FitPar fitPar = new FitPar(parName, kk, 0.0);
                peakFitPars.fitPars().put(parName, fitPar);

            } else if (parName.equals("KA") && peakFitPars.fitPar("KK") != null) {
                double kk = peakFitPars.fitPar("KK").value();
                double kDVal = kD.get();
                double k1sq = kk / (lConc * kDVal);
                double k1 = Math.sqrt(k1sq);

                FitPar fitPar = new FitPar(parName, k1, 0.0);
                peakFitPars.fitPars().put(parName, fitPar);
            } else if (parName.equals("KAB") && peakFitPars.fitPar("KK") != null) {
                double kk = peakFitPars.fitPar("KK").value();
                double kDVal = kD.get();
                double k1sq = kk / (lConc * kDVal);
                double k1 = Math.sqrt(k1sq);
                double ka = k1;
                double kab = ka * lConc;

                FitPar fitPar = new FitPar(parName, kab, 0.0);
                peakFitPars.fitPars().put(parName, fitPar);
            } else if (parName.equals("KBA") && peakFitPars.fitPar("KK") != null) {
                double kk = peakFitPars.fitPar("KK").value();
                double kDVal = kD.get();
                double k1sq = kk / (lConc * kDVal);
                double k1 = Math.sqrt(k1sq);
                double k1p = k1 * lConc;
                double kBA = kk / k1p;

                FitPar fitPar = new FitPar(parName, kBA, 0.0);
                peakFitPars.fitPars().put(parName, fitPar);
            }
        }
    }

    public void selectRow(PeakFitPars path) {
        tableView.getSelectionModel().clearSelection();
        tableView.getSelectionModel().select(path);
    }

    List<PeakFitPars> getSelected() {
        List<PeakFitPars> paths = new ArrayList<>();
        List<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
        for (Integer index : selected) {
            PeakFitPars path = tableView.getItems().get(index);
            paths.add(path);
        }
        return paths;
    }

    void gotoPeak() {
        var selPars = getSelected();
        if (!selPars.isEmpty()) {
            Peak peak = selPars.get(0).peak();
            if (peak != null) {
                PeakDisplayTool.gotoPeak(peak);
            }
        }
    }

    final protected void selectionChanged() {
        List<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
        activeChart.getData().clear();
        for (Integer index : selected) {
            PeakFitPars peakFitPars = (PeakFitPars) tableView.getItems().get(index);
            addZZFit(peakFitPars);
        }
    }

    public void savePathTable() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Table File");
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            savePathTable(file);
        }
    }

    private void savePathTable(File file) {
        Charset charset = Charset.forName("US-ASCII");
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), charset)) {
            boolean first = true;
            for (TableColumn column : tableView.getColumns()) {
                String header = column.getText();
                if (!first) {
                    writer.write('\t');
                } else {
                    first = false;
                }
                writer.write(header, 0, header.length());
            }
            for (PeakFitPars item : tableView.getItems()) {
                StringBuilder sBuilder = new StringBuilder();
                sBuilder.append('\n');
                for (String colName : colNames) {
                    if (sBuilder.length() != 1) {
                        sBuilder.append('\t');
                    }
                    switch (colName) {
                        case "Peak":
                            sBuilder.append(item.peak());
                            break;
                        case "Label":
                            if (item.peak() != null) {
                                sBuilder.append(item.peak().getPeakDim(0).getLabel());
                            } else {
                                sBuilder.append("");
                            }
                            break;
                        default:
                            boolean isErr = colName.endsWith(":Err");
                            String parName = colName;
                            if (isErr) {
                                parName = colName.substring(0, colName.indexOf(":"));
                            }
                            FitPar fitPar = item.fitPar(parName);
                            if (fitPar != null) {
                                double v = isErr ? fitPar.error() : fitPar.value();
                                sBuilder.append(String.format("%.4f", v));
                            } else {
                                sBuilder.append("");
                            }
                            break;
                    }

                }
                String s = sBuilder.toString();
                writer.write(s, 0, s.length());
            }
        } catch (IOException x) {
            log.warn(x.getMessage(), x);
        }
    }

    private boolean addZZFit(PeakFitPars peakFitPars) {
        Peak currentPeak = peakFitPars.peak();
        List<Peak> peaks = new ArrayList<>();
        double[] xValues = null;
        if (currentPeak != null) {
            var peakBAOpt = PeakList.getLinkedPeakDims(currentPeak, 0).stream().filter(p -> p.getPeak() != currentPeak).findFirst();
            var peakABOpt = PeakList.getLinkedPeakDims(currentPeak, 1).stream().filter(p -> p.getPeak() != currentPeak).findFirst();
            if (peakBAOpt.isPresent() && peakABOpt.isPresent()) {
                Peak peakBA = peakBAOpt.get().getPeak();
                var peakBBOpt = PeakList.getLinkedPeakDims(peakBA, 1).stream().filter(p -> p.getPeak() != peakBA).findFirst();
                if (peakBBOpt.isPresent()) {
                    var peakAB = peakABOpt.get().getPeak();
                    var peakBB = peakBBOpt.get().getPeak();
                    peaks.addAll(List.of(currentPeak, peakBB, peakAB, peakBA));
                }
            }
            xValues = currentPeak.getPeakList().getMeasureValues();
        }
        activeChart.getData().clear();
        activeChart.xAxis.setAutoRanging(true);
        activeChart.yAxis.setAutoRanging(true);
        Axis yAxis = activeChart.getYAxis();
        if (tableInRatioMode) {
            yAxis.setLabel("Ratio");
            addSeries(peakFitPars, peaks);
        } else {
            yAxis.setLabel("Intensity");
            if (xValues != null) {
                addSeries(peakFitPars, peaks, xValues);
            }
        }
        activeChart.drawChart();
        return true;
    }

    void addSeries(PeakFitPars peakFitPars, List<Peak> peaks) {
        double kk = peakFitPars.fitPars().get("KK").value();
        DataSeries series = new DataSeries();
        DataSeries lineSeries = new DataSeries();
        if (!peaks.isEmpty()) {
            PeakList peakList = peaks.get(0).getPeakList();
            PeakListTools.XYEValues xyeValues = PeakListTools.getXYErrValues(peakList, peaks);
            List<XYValue> xyValues = PeakListTools.calcRatioKK(xyeValues);
            for (XYValue xyValue : xyValues) {
                series.add(xyValue);
            }
        } else {
            List<Double> xValuesList = fitZZPeakRatioResult.xValues();
            List<Double> yValuesList = fitZZPeakRatioResult.yValues();
            for (int j = 0; j < xValuesList.size(); j++) {
                series.add(new XYValue(xValuesList.get(j), yValuesList.get(j)));
            }
        }
        double xMax = series.getMaxX();
        int nPoints = 100;
        for (int i = 0; i < nPoints; i++) {
            double delay = i * xMax / (nPoints - 1);
            double y = kk * delay * delay;
            XYValue value = new XYValue(delay, y);
            lineSeries.getData().add(value);
        }
        addSeries(series, lineSeries, Color.BLACK, "AA");
    }

    void addSeries(PeakFitPars peakFitPars, List<Peak> peaks, double[] xValues) {
        double intensity = peakFitPars.fitPars().get("I").value();
        double r1A = peakFitPars.fitPars().get("R1A").value();
        double r1B = peakFitPars.fitPars().get("R1B").value();
        double kAB = peakFitPars.fitPars().get("KAB").value();
        double kBA = peakFitPars.fitPars().get("KBA").value();
        double pA = peakFitPars.fitPars().get("P").value();
        int iSig = 0;
        String[] peakLabels = {"AA", "BB", "BA", "AB"};

        for (Peak peak : peaks) {
            DataSeries series = new DataSeries();
            DataSeries lineSeries = new DataSeries();
            Color color = XYCanvasChart.colors[iSig];
            addSeries(series, lineSeries, color, peakLabels[iSig]);

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

            double xMax = xValues[xValues.length - 1];
            int nPoints = 100;
            for (int i = 0; i < nPoints; i++) {
                double delay = i * xMax / (nPoints - 1);
                double y = intensity * LorentzGaussND.zzAmplitude2(r1A, r1B, pA, kAB, kBA, delay, iSig);

                XYValue value = new XYValue(delay, y);
                lineSeries.getData().add(value);
            }
            iSig++;
        }

    }

    void addSeries(DataSeries series, DataSeries lineSeries, Color color, String label) {
        series.setStroke(color);
        series.setFill(color);
        series.setName(label);
        activeChart.getData().add(series);
        lineSeries.drawLine(true);
        lineSeries.drawSymbol(false);
        lineSeries.fillSymbol(false);
        lineSeries.setStroke(color);
        lineSeries.setFill(color);
        lineSeries.setName(label);
        activeChart.getData().add(lineSeries);

    }

}
