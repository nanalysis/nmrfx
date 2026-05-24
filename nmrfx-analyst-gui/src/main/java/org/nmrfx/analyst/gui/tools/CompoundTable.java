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
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.analyst.dataops.DBData;
import org.nmrfx.analyst.dataops.SimData;
import org.nmrfx.analyst.dataops.SimDataVecPars;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ChemicalLibraryController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;


public class CompoundTable {
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
        ListChangeListener<Integer> selectionListener= c -> selectionChanged();
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
    }

    void buildCompoundTool() {
        Tab libraryTableTab = new Tab("Compounds");
        tableView = new TableView<>();
        tableView.setMinWidth(400);
        HBox hBox = new HBox();
        hBox.getChildren().add(tableView);
        HBox.setHgrow(tableView, Priority.ALWAYS);
        libraryTableTab.setClosable(false);
        libraryTableTab.setContent(hBox);
        tableTabPane.getTabs().add(libraryTableTab);
        VBox vBox = new VBox();
        vBox.setMinWidth(300);
        hBox.getChildren().add(vBox);
        makeCompoundControls(vBox);
    }

    private void makeCompoundControls(VBox vBox) {
        ToolBar toolBar = new ToolBar();
        Button peakButton = new Button("Peaks");
        peakButton.setOnAction(e -> showPeakList());
        toolBar.getItems().add(peakButton);

        Button updateDataButton = new Button("Update");
        updateDataButton.setOnAction(e -> currentSimData.get().updateFromPeakList());
        toolBar.getItems().add(updateDataButton);

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

        vBox.setSpacing(15);
        vBox.getChildren().addAll(toolBar,  hBox1, hBox2);


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


    void selectionChanged() {
        var items = tableView.getSelectionModel().getSelectedItems();
        PolyChart chart = scannerTool.getChart();
        chart.clearSimDatasets();

        List<DatasetAttributes> datasetAttributes = chart.getDatasetAttributes();
        double offset = 0.12;
        if (!datasetAttributes.isEmpty()) {
            offset = datasetAttributes.getFirst().getOffset();
        }
        Dataset realDataset = (Dataset) chart.getDataset();
        currentCompoundName.setText("");
        if (!items.isEmpty()) {
            for (var item : items) {
                SimData simData = item.simData();
                SimData simDataCopy = currentSimMap.computeIfAbsent(simData.getName(), k -> simData.copy());
                item.simData = simDataCopy;
                Dataset dataset = makeDataset(realDataset, simDataCopy, "SIM_" + simDataCopy.getName());
                var dataAttr = chart.setDataset(dataset, true, true);
                dataAttr.setLvl(3.0e2);
                dataAttr.setOffset(offset);
                currentSimData.set(simDataCopy);
                currentCompoundName.setText(simDataCopy.getName());
            }
        }
        chart.refresh();
    }

    void updateFromPeakList() {
        currentSimData.get().updateFromPeakList();
        tableView.refresh();
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


}