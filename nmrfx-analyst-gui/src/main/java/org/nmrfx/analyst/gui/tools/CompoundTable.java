package org.nmrfx.analyst.gui.tools;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.nmrfx.analyst.dataops.SimData;
import org.nmrfx.analyst.dataops.SimDataVecPars;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ChemicalLibraryController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

import java.util.List;
import java.util.Optional;


public class CompoundTable {
    TableView<CompoundItem> tableView;
    ScannerTool scannerTool;
    public record CompoundItem(SimData simData, double score) {}

    public CompoundTable(ScannerTool scannerTool, TableView<CompoundItem> tableView) {
        this.scannerTool = scannerTool;
        this.tableView = tableView;
        init();
    }

    private void init() {
        TableColumn<CompoundItem, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().simData().getName()));
        tableView.getColumns().addAll(nameColumn);

        TableColumn<CompoundItem, Number> scoreColumn = new TableColumn<>("Score");
        scoreColumn.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().score()));
        tableView.getColumns().addAll(scoreColumn);

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
        if (!items.isEmpty()) {
            for (var item : items) {
                Dataset dataset = makeDataset(realDataset, item.simData(), "SIM_" + item.simData().getName());
                var dataAttr = chart.setDataset(dataset, true, true);
                dataAttr.setLvl(3.0e2);
                dataAttr.setOffset(offset);
            }
        }
        chart.refresh();
    }

    void showPeakList() {
        var items = tableView.getSelectionModel().getSelectedItems();
        PolyChart chart = scannerTool.getChart();
        chart.clearPeaks();
        if (!items.isEmpty()) {
            Dataset realDataset = (Dataset) chart.getDataset();
            if (realDataset != null) {
                PeakList peakList = items.getFirst().simData().buildPeakList(realDataset);
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