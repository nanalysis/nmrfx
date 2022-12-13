package org.nmrfx.processor.gui;

import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Accordion;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.Pane;
import org.controlsfx.control.ListSelectionView;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ContentController {
    private static final Logger log = LoggerFactory.getLogger(ContentController.class);
    @FXML
    Accordion contentAccordion;
    @FXML
    ListSelectionView<String> datasetView;
    @FXML
    ListSelectionView<String> peakView;

    DatasetView datasetViewController;
    FXMLController fxmlController;
    Pane pane;
    PolyChart chart;
    ListChangeListener<String> peakTargetListener;
    ChoiceBox<String> showOnlyCompatibleBox = new ChoiceBox<>();
    MapChangeListener mapChangeListener = change -> update();

    public static ContentController create(FXMLController fxmlController, Pane processorPane) {
        FXMLLoader loader = new FXMLLoader(ContentController.class.getResource("/fxml/ContentController.fxml"));
        final ContentController controller;
        try {
            Pane pane = loader.load();
            processorPane.getChildren().add(pane);

            controller = loader.getController();
            controller.fxmlController = fxmlController;
            controller.pane = pane;
            controller.datasetViewController = new DatasetView(fxmlController, controller);
            pane.visibleProperty().addListener(e -> controller.updatePeakView());
            controller.update();
            return controller;
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
            return null;
        }
    }

    @FXML
    public void initialize() {
        peakView.setSourceFooter(showOnlyCompatibleBox);
        showOnlyCompatibleBox.getItems().add("Matching");
        showOnlyCompatibleBox.getItems().add("Compatible");
        showOnlyCompatibleBox.getItems().add("All");
        showOnlyCompatibleBox.setValue("Compatible");
        showOnlyCompatibleBox.setOnAction(e -> updatePeakView());

        peakTargetListener = (ListChangeListener.Change<? extends String> c) -> updateChartPeakLists();
        peakView.getTargetItems().addListener(peakTargetListener);
        ProjectBase.getActive().addDatasetListListener(mapChangeListener);
        ProjectBase.getActive().addPeakListListener(mapChangeListener);
    }

    private boolean isShowing() {
        return fxmlController.isContentPaneShowing();
    }

    public void setChart(PolyChart chart) {
        this.chart = chart;
        update();
    }


    public void update() {
        if (isShowing()) {
            chart = fxmlController.getActiveChart();
            chart.setChartDisabled(true);
            datasetViewController.updateDatasetView();
            updatePeakView();
            chart.setChartDisabled(false);
        }
    }

    private void updateChartPeakLists() {
        ObservableList<String> peakListTargets = peakView.getTargetItems();
        chart.updatePeakLists(peakListTargets);
    }

    void updatePeakView() {
        peakView.getTargetItems().removeListener(peakTargetListener);
        String showOnlyMode = showOnlyCompatibleBox.getValue();
        ObservableList<String> peaksTarget = peakView.getTargetItems();
        ObservableList<String> peaksSource = peakView.getSourceItems();
        peaksTarget.clear();
        peaksSource.clear();
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();

        for (PeakListAttributes peakAttr : peakAttrs) {
            peaksTarget.add(peakAttr.getPeakListName());
        }
        for (PeakList peakList : PeakList.peakLists()) {
            if (!peaksTarget.contains(peakList.getName())) {
                boolean ok = false;
                if (showOnlyMode.equals("All")) {
                    ok = true;
                } else if (showOnlyMode.equals("Compatible") && chart.isPeakListCompatible(peakList, false)) {
                    ok = true;
                } else if (showOnlyMode.equals("Matching")) {
                    String datasetName = peakList.getDatasetName();
                    ok = dataAttrs.stream().anyMatch(d -> d.getFileName().equals(datasetName));
                }
                if (ok) {
                    peaksSource.add(peakList.getName());
                }
            }
        }
        peakView.getTargetItems().addListener(peakTargetListener);
    }
}
