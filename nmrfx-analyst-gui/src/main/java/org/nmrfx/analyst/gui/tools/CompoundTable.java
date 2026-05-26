package org.nmrfx.analyst.gui.tools;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.analyst.dataops.DBData;
import org.nmrfx.analyst.dataops.SimData;
import org.nmrfx.analyst.dataops.SimDataVecPars;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.datasets.peaks.PeakPicker;
import org.nmrfx.processor.gui.ChemicalLibraryController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;


public class CompoundTable {
    private static final Logger log = LoggerFactory.getLogger(CompoundTable.class);
    TableView<CompoundItem> tableView;
    ScannerTool scannerTool;
    TabPane tableTabPane;
    Map<String, SimData> currentSimMap = new HashMap<>();
    SimpleObjectProperty<SimData> currentSimData = new SimpleObjectProperty<>();
    TextField searchField;
    Label currentCompoundName;

    public static class CompoundItem {
        SimData simData;
        double score;

        public CompoundItem(SimData simData, double score) {
            this.simData = simData;
            this.score = score;
        }

        public SimData simData() {
            return simData;
        }

        public double score() {
            return score;
        }

    }

    public CompoundTable(ScannerTool scannerTool, TabPane tableTabPane) {
        this.scannerTool = scannerTool;
        this.tableTabPane = tableTabPane;
        init();
    }

    private void init() {
        buildCompoundTool();
        TableColumn<CompoundItem, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().simData().getName()));
        tableView.getColumns().addAll(nameColumn);

        TableColumn<CompoundItem, Number> scoreColumn = new TableColumn<>("Score");
        scoreColumn.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().score()));
        tableView.getColumns().addAll(scoreColumn);

        TableColumn<CompoundItem, Boolean> modifiedColumn = new TableColumn<>("Modified");
        modifiedColumn.setCellValueFactory(e -> new SimpleBooleanProperty(e.getValue().simData().modified()));
        tableView.getColumns().addAll(modifiedColumn);

        if (!SimData.loaded()) {
            ChemicalLibraryController.loadSimData();
        }
        var cmpdItems = SimData.getSimData().stream().map(simData -> new CompoundItem(simData, 1.0)).toList();
        ObservableList<CompoundItem> items = FXCollections.observableArrayList(cmpdItems);
        tableView.setItems(items);
        ListChangeListener<Integer> selectionListener = c -> selectionChanged();
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
    }

    void buildCompoundTool() {
        Tab libraryTableTab = new Tab("Compounds");
        tableView = new TableView<>();
        tableView.setMinWidth(400);
        HBox hBox = new HBox();
        HBox.setHgrow(tableView, Priority.ALWAYS);
        libraryTableTab.setClosable(false);
        libraryTableTab.setContent(hBox);
        tableTabPane.getTabs().add(libraryTableTab);
        VBox vBox = new VBox();
        vBox.setMinWidth(400);
        hBox.getChildren().addAll(vBox, tableView);
        hBox.setSpacing(15);
        makeCompoundControls(vBox);
    }

    private void makeCompoundControls(VBox vBox) {
        ToolBar toolBar = new ToolBar();
        Button peakButton = new Button("Peaks");
        peakButton.setOnAction(e -> showPeakList());
        toolBar.getItems().add(peakButton);

        Button updateDataButton = new Button("Update");
        updateDataButton.setOnAction(e -> updateFromPeakList());
        toolBar.getItems().add(updateDataButton);

        Button matchDataButton = new Button("Match");
        matchDataButton.setOnAction(e -> matchData());
        toolBar.getItems().add(matchDataButton);

        Label activeCompoundLabel = new Label("Active:");
        currentCompoundName = new Label();
        Label searchLabel = new Label("Search:");
        searchLabel.setPrefWidth(60);
        searchField = new TextField();
        searchField.setPrefWidth(200);
        searchField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                setMol(searchField.getText());
            }
        });
        HBox hBox1 = new HBox();
        hBox1.setAlignment(Pos.CENTER_LEFT);
        hBox1.setSpacing(10);
        hBox1.getChildren().addAll(activeCompoundLabel, currentCompoundName);

        HBox hBox2 = new HBox();
        hBox2.setAlignment(Pos.CENTER_LEFT);
        hBox2.setSpacing(10);
        hBox2.getChildren().addAll(searchLabel, searchField);

        vBox.setSpacing(5);
        vBox.getChildren().addAll(toolBar, hBox1, hBox2);


        Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> suggestionProvider = param -> getMatchingNames(param.getUserText());
        TextFields.bindAutoCompletion(searchField, suggestionProvider);

    }

    List<String> getMatchingNames(String pattern) {
        ChemicalLibraryController.LIBRARY_MODE mode = ChemicalLibraryController.LIBRARY_MODE.GISSMO;

        if (mode == ChemicalLibraryController.LIBRARY_MODE.SEGMENTS) {
            String dbPath = AnalystPrefs.getSegmentLibraryFile();
            try {
                DBData.loadData(Path.of(dbPath));
            } catch (IOException e) {
            }

            return DBData.getNames(pattern);
        } else {
            if (!SimData.loaded()) {
                ChemicalLibraryController.loadSimData();
            }
            return SimData.getNames(pattern);
        }
    }


    private void setMol(String name) {
        select(name);
        searchField.setText("");
    }

    void select(String name) {
        Optional<CompoundItem> itemOpt = tableView.getItems().stream().filter(compoundItem -> compoundItem.simData().getName().equalsIgnoreCase(name)).findFirst();
        itemOpt.ifPresent(item -> {
            tableView.scrollTo(item);
            tableView.getSelectionModel().select(item);
        });
    }


    double getRegionMax(Vec vec, double[] ppms, int extra) {
        int first = vec.refToPt(ppms[0]);
        int last = vec.refToPt(ppms[1]);
        if (first > last) {
            int hold = first;
            first = last;
            last = hold;
        }
        first -= extra;
        last += extra;
        System.out.println(first + " " + last);
        return vec.maxIndex(first, last).getValue();
    }

    double analyzeRegions(Vec vec, List<SimData.SimDataRegion> regions, int extra) {
        DescriptiveStatistics dState = new DescriptiveStatistics();
        for (SimData.SimDataRegion simDataRegion : regions) {
            double[] ppms = simDataRegion.ppms();
            double max = getRegionMax(vec, ppms, extra);
            dState.addValue(max);
        }
        double max = vec.maxIndex().getValue();
        double percentile = dState.getPercentile(70);
        return Math.max(percentile, max / 10.0);
    }

    void selectionChanged() {
        var items = tableView.getSelectionModel().getSelectedItems();
        PolyChart chart = scannerTool.getChart();
        chart.clearSimDatasets();

        Dataset realDataset = (Dataset) chart.getDataset();
        currentCompoundName.setText("");
        if (!items.isEmpty()) {
            for (var item : items) {
                SimData simData = item.simData();
                SimData simDataCopy = currentSimMap.computeIfAbsent(simData.getName().toLowerCase(), k -> simData.copy());
                item.simData = simDataCopy;
                Dataset dataset = makeDataset(realDataset, simDataCopy, "SIM_" + simDataCopy.getName());
                var dataAttr = chart.setDataset(dataset, true, true);
                double lvl = 250.0 * 9.0 * 1.1;
                dataAttr.setLvl(lvl);
                dataAttr.setOffset(0.01);
                currentSimData.set(simDataCopy);
                currentCompoundName.setText(simDataCopy.getName());
            }
        }
        chart.refresh();
    }

    void updateFromPeakList() {
        if (currentSimData.get() != null) {
            currentSimData.get().updateFromPeakList();
            tableView.refresh();
        }
    }

    void showPeakList() {
        if (currentSimData.get() != null) {
            PolyChart chart = scannerTool.getChart();
            chart.clearPeaks();
            Dataset realDataset = (Dataset) chart.getDataset();
            if (realDataset != null) {
                PeakList peakList = currentSimData.get().buildPeakList(realDataset);
                chart.updatePeakLists(List.of(peakList));
            }
        }
    }

    private Dataset makeDataset(Dataset currData, SimData simData, String name) {
        SimDataVecPars pars;
        if (currData != null) {
            pars = new SimDataVecPars(currData);
        } else {
            pars = ChemicalLibraryController.defaultPars();
        }
        double lb = 1.0;
        Dataset newDataset = SimData.genDataset(simData, name, pars, lb);
        newDataset.setFreqDomain(0, true);
        newDataset.addProperty("SIM", name);
        return newDataset;
    }

    double[] getLimits() {
        PolyChart chart = scannerTool.getChart();
        double[] ppms;
        if (chart.getCrossHairs().hasRegion()) {
            CrossHairs crossHairs = chart.getCrossHairs();
            ppms = crossHairs.getVerticalPositions();
        } else {
            ppms = chart.getWorld()[0];
        }
        return ppms;
    }

    private void matchData() {
        PolyChart chart = scannerTool.getChart();
        Dataset dataset = (Dataset) chart.getDataset();
        double[] ppms = getLimits();
        String listName = "TEMP";
        try {
            boolean scaleToLargest = true;
            int nWin = 32;
            double maxRatio = 100.0;
            double sdRatio = 30.0;

            double threshold = PeakPicker.calculateThreshold(dataset, scaleToLargest, nWin, maxRatio, sdRatio);

            PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(threshold).mode(PeakPickParameters.PickMode.REPLACEIF);
            peakPickPar.limit(0, ppms[0], ppms[1]);
            PeakPicker picker = new PeakPicker(peakPickPar);
            PeakList peakList = picker.peakPick();
            List<ShiftGroup> peakPositions = new ArrayList<>();
            for (Peak peak : peakList.peaks()) {
                ShiftGroup shiftGroup = new ShiftGroup((double) peak.getPeakDim(0).getChemShiftValue(), 1.0);
                peakPositions.add(shiftGroup);
            }
            peakList.remove();
            peakPositions = clusterGroups(peakPositions, 15.0, dataset.getSf(0));
            List<Double> groupedPositions = peakPositions.stream().map(p -> p.shift).toList();
            Map<String, SimData> tempMap = new HashMap<>();
            tempMap.putAll(SimData.getData());
            tempMap.putAll(currentSimMap);
            var results = SimData.match(groupedPositions, 0.075, tempMap.values());
            ObservableList<CompoundItem> newList = FXCollections.observableArrayList();
            for (SimData.MatchData matchResult : results) {
                CompoundItem compoundItem = new CompoundItem(matchResult.simData(), matchResult.score());
                newList.add(compoundItem);
            }
            tableView.setItems(newList);
        } catch (IOException | IllegalArgumentException ex) {
            log.error(ex.getMessage(), ex);
        }

    }


    public record ShiftGroup(double shift, double intensity) {
    }

    public static List<ShiftGroup> clusterGroups(List<ShiftGroup> peaks, double maxCouplingHz, double fieldMHz) {
        if (peaks.isEmpty()) return List.of();

        double gapPpm = maxCouplingHz / fieldMHz;

        List<ShiftGroup> sorted = peaks.stream()
                .sorted(Comparator.comparingDouble(ShiftGroup::shift))
                .toList();

        List<List<ShiftGroup>> clusters = new ArrayList<>();
        List<ShiftGroup> current = new ArrayList<>();
        current.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            double gap = sorted.get(i).shift() - sorted.get(i - 1).shift();
            if (gap <= gapPpm) {
                current.add(sorted.get(i));
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(sorted.get(i));
            }
        }
        clusters.add(current);

        return clusters.stream().map(CompoundTable::weightedCentroid).toList();
    }

    private static ShiftGroup weightedCentroid(List<ShiftGroup> cluster) {
        double totalIntensity = cluster.stream().mapToDouble(ShiftGroup::intensity).sum();
        double centroidShift = cluster.stream()
                .mapToDouble(p -> p.shift() * p.intensity())
                .sum() / totalIntensity;
        return new ShiftGroup(centroidShift, totalIntensity);
    }
}