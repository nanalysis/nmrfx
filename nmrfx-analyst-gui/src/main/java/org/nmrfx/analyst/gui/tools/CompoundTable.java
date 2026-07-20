package org.nmrfx.analyst.gui.tools;

import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.utils.GUIUtils;
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
    SimpleDoubleProperty scaleProperty = new SimpleDoubleProperty(1.0);
    SimpleDoubleProperty offsetProperty = new SimpleDoubleProperty(0.01);
    double scale = 1.0;
    double offset = 0.01;
    Color color = Color.RED;


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
        scoreColumn.setCellValueFactory(e -> new SimpleDoubleProperty(Math.round(e.getValue().score() * 1000.0)/ 1000.0));
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

    public void showCompoundTab() {
        tableTabPane.getSelectionModel().select(1);
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
        TabPane tabPane = new TabPane();
        tabPane.setMinWidth(300);
        hBox.getChildren().addAll(tabPane, tableView);
        hBox.setSpacing(15);

        makeCompoundControls(tabPane);
    }

    private void makeCompoundControls(TabPane tabPane) {

        Tab peakTab = new Tab("Peaks");
        ToolBar peakToolBar = new ToolBar();
        Button peakButton = new Button("Peaks");
        peakButton.setOnAction(e -> showPeakList());
        peakToolBar.getItems().add(peakButton);

        Button updateDataButton = new Button("Update");
        updateDataButton.setOnAction(e -> updateFromPeakList());
        peakToolBar.getItems().add(updateDataButton);
        VBox peakBox = new VBox();
        peakBox.setPadding(new Insets(5, 5, 5, 5));
        peakTab.setContent(peakBox);
        peakBox.getChildren().add(peakToolBar);

        Tab searchTab = new Tab("Search");
        VBox vBox = new VBox();
        vBox.setPadding(new Insets(5, 5, 5, 5));

        searchTab.setContent(vBox);
        ToolBar searchToolBar = new ToolBar();

        Button matchDataButton = new Button("Match");
        matchDataButton.setOnAction(e -> matchData());
        searchToolBar.getItems().add(matchDataButton);

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
        searchLabel.getStyleClass().add(Styles.SMALL);
        searchField.getStyleClass().add(Styles.SMALL);

        vBox.setSpacing(5);
        Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> suggestionProvider = param -> getMatchingNames(param.getUserText());
        TextFields.bindAutoCompletion(searchField, suggestionProvider);
        final ColorPicker cp = new ColorPicker();
        cp.getStyleClass().add("button");
        cp.setStyle("-fx-color-label-visible:false;");
        cp.setValue(Color.RED);
        cp.setOnAction(t -> {
            color = cp.getValue();
            update();
        });
        vBox.getChildren().addAll(searchToolBar, hBox1, hBox2);




        Label offsetLabel = new Label("Offset:");
        offsetLabel.setPrefWidth(60);

        Label scaleLabel = new Label("Scale:");
        scaleLabel.setPrefWidth(60);

        Slider scaleSlider = new Slider(0.1, 20.0, 1.0);
        TextField scaleField = GUIUtils.getDoubleTextField(scaleProperty, 2);
        scaleField.setPrefWidth(50);
        GUIUtils.bindSliderField(scaleSlider, scaleField, "#.##");
        scaleProperty.set(1.0);
        scaleSlider.setValue(1.0);
        scaleSlider.valueProperty().addListener(v -> scaleChanged(scaleSlider.getValue()));
        scaleSlider.setOnMouseReleased(e -> setScaleSlider(scaleSlider));
        HBox hBoxScale = new HBox();
        hBoxScale.setSpacing(10);

        hBoxScale.getChildren().addAll(scaleLabel, scaleSlider, scaleField);
        scaleSlider.getStyleClass().add(Styles.SMALL);
        searchLabel.getStyleClass().add(Styles.SMALL);
        scaleField.getStyleClass().add(Styles.SMALL);
        Slider offsetSlider = new Slider(0.0, 1.0, 0.01);

        TextField offsetField = GUIUtils.getDoubleTextField(offsetProperty, 2);
        GUIUtils.bindSliderField(offsetSlider, offsetField, "#.##");
        offsetProperty.set(0.01);
        offsetSlider.setValue(0.01);
        offsetSlider.valueProperty().addListener(v -> offsetChanged(offsetSlider.getValue()));
        offsetSlider.setOnMouseReleased(e -> setOffsetSlider(offsetSlider));
        offsetField.setPrefWidth(50);
        HBox hBoxOffset = new HBox();
        hBoxOffset.setSpacing(10);
        offsetSlider.getStyleClass().add(Styles.SMALL);
        offsetLabel.getStyleClass().add(Styles.SMALL);
        offsetField.getStyleClass().add(Styles.SMALL);

        hBoxOffset.getChildren().addAll(offsetLabel, offsetSlider, offsetField);

        HBox hBoxColor = new HBox();
        hBoxColor.setSpacing(10);
        Label colorLabel = new Label("Color:");
        colorLabel.setPrefWidth(60);
        hBoxColor.getChildren().addAll(colorLabel, cp);

        Tab viewTab = new Tab("View");
        VBox viewBox = new VBox();
        viewBox.setPadding(new Insets(5, 5, 5, 5));

        viewTab.setContent(viewBox);
        viewBox.setSpacing(10);
        viewBox.getChildren().addAll(hBoxScale, hBoxOffset, hBoxColor );
        tabPane.getTabs().addAll(searchTab, viewTab, peakTab);
    }

    void update() {
        PolyChart chart = scannerTool.getChart();
        var datasetAttrsList = chart.getDatasetAttributes();
        for (DatasetAttributes datasetAttributes : datasetAttrsList) {
            if (datasetAttributes.isSim()) {
                update(datasetAttributes);
            }
        }
        chart.refresh();
    }

    void update(DatasetAttributes dataAttr) {
        double lvl = 250.0 * 9.0 * 1.1;
        dataAttr.setLvl(lvl / scale);
        dataAttr.setOffset(offset);
        dataAttr.setPosColor(color);
    }

    void offsetChanged(double offset) {
        this.offset = offset;
        update();
    }

    void setOffsetSlider(Slider slider) {
        offsetChanged(slider.getValue());
    }

    void scaleChanged(double scale) {
        this.scale = scale;
        update();
    }

    void setScaleSlider(Slider slider) {
        scaleChanged(slider.getValue());
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

        currentCompoundName.setText("");
        if (!items.isEmpty()) {
            for (var item : items) {
                showData(item);
            }
        }
        chart.refresh();
    }

    void showData(CompoundItem item) {
        SimData simData = item.simData();
        SimData simDataCopy = currentSimMap.computeIfAbsent(simData.getName().toLowerCase(), k -> simData.copy());
        item.simData = simDataCopy;
        showData(simDataCopy, false);
    }

    void showData(SimData simDataCopy, boolean refresh) {
        PolyChart chart = scannerTool.getChart();
        String datasetName = "SIM_" + simDataCopy.getName();
        Dataset dataset = Dataset.getDataset(datasetName);
        if ((dataset == null) || refresh) {
            Dataset realDataset = (Dataset) chart.getDataset();
            dataset = makeDataset(realDataset, simDataCopy, datasetName);
        }
        var dataAttr = chart.setDataset(dataset, true, true);
        update(dataAttr);
        currentSimData.set(simDataCopy);
        currentCompoundName.setText(simDataCopy.getName());

    }

    void updateFromPeakList() {
        if (currentSimData.get() != null) {
            currentSimData.get().updateFromPeakList();
            showData(currentSimData.get(), true);
            tableView.refresh();
            update();
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
                ShiftGroup shiftGroup = new ShiftGroup(peak.getPeakDim(0).getChemShiftValue(), 1.0);
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
        current.add(sorted.getFirst());

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