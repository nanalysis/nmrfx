package org.nmrfx.analyst.gui.regions;


import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.tools.SimplePeakRegionTool;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.DatasetRegionsListListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.FileUtils;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Regions Table
 */
public class RegionsTableController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(RegionsTableController.class);
    private static RegionsTableController regionsTableController = null;
    private RegionsTable regionsTable;
    private PolyChart chart;
    private Stage stage;
    @FXML
    private BorderPane regionsBorderPane;
    @FXML
    private MenuButton fileMenuButton;
    @FXML
    private Button autoIntegrateButton;
    @FXML
    private Button addRegionButton;
    @FXML
    private Button removeRegionButton;
    @FXML
    private Button removeAllButton;

    private final ChangeListener<DatasetRegion> activeDatasetRegionListener = this::updateActiveRegion;
    private final DatasetRegionsListListener datasetRegionsListListener = this::setRegions;


    private ChangeListener<DatasetRegion> selectedRowRegionsTableListener;

    private RegionsTableController() {}

    public static RegionsTableController create() {
        FXMLLoader loader = new FXMLLoader(RegionsTableController.class.getResource("/fxml/RegionsScene.fxml"));
        loader.setControllerFactory(controller -> new RegionsTableController());

        RegionsTableController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.getController();
            controller.stage = stage;
            RegionsTableController.regionsTableController = controller;
            stage.setTitle("Regions");
        } catch (IOException ioE) {
            throw new IllegalStateException("Unable to create the RegionsTable.", ioE);
        }
        return controller;
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    @Override
    public void initialize(URL location, ResourceBundle rb) {
        regionsTable = new RegionsTable();
        regionsBorderPane.setCenter(regionsTable);
        chart = PolyChart.getActiveChart();
        chart.addRegionListener(activeDatasetRegionListener);
        if (chart.getDataset() != null) {
            chart.getDataset().addDatasetRegionsListListener(datasetRegionsListListener);
        }
        PolyChart.getCurrentDatasetProperty().addListener((observable, oldValue, newValue) -> {
            if( oldValue != null) {
                oldValue.removeDatasetRegionsListListener(datasetRegionsListListener);
            }
            if (newValue != null) {
                newValue.addDatasetRegionsListListener(datasetRegionsListListener);
            }
            updateActiveChartRegions();
        });
        MenuItem loadRegionsMenuItem = new MenuItem("Load Regions");
        loadRegionsMenuItem.setOnAction(e -> loadRegions());

        MenuItem saveRegionsMenuItem = new MenuItem("Save Regions");
        saveRegionsMenuItem.setOnAction(e -> saveRegions());
        fileMenuButton.getItems().addAll(loadRegionsMenuItem, saveRegionsMenuItem);

        removeRegionButton.disableProperty().bind(regionsTable.getSelectionModel().selectedItemProperty().isNull());
        removeRegionButton.setOnAction(event -> regionsTable.removeSelectedRegion());
        addRegionButton.setOnAction(event -> addRegion());
        autoIntegrateButton.setOnAction(event -> {
           SimplePeakRegionTool peakRegionTool = (SimplePeakRegionTool) chart.getController().getTool(SimplePeakRegionTool.class);
           if (peakRegionTool != null) {
               peakRegionTool.findRegions();
           }
        });
        removeAllButton.setOnAction(event -> {
            SimplePeakRegionTool peakRegionTool = (SimplePeakRegionTool) chart.getController().getTool(SimplePeakRegionTool.class);
            if (peakRegionTool != null) {
                peakRegionTool.clearAnalysis(false);
            }
        });

        PolyChart.getActiveChartProperty().addListener((observable, oldValue, newValue) -> {
            chart.removeRegionListener(activeDatasetRegionListener);
            if (chart.getDataset() != null) {
                chart.getDataset().removeDatasetRegionsListListener(datasetRegionsListListener);
            }
            chart = newValue;
            updateButtonBindings();
            updateActiveChartRegions();
            chart.addRegionListener(activeDatasetRegionListener);
            if (chart.getDataset() != null) {
                chart.getDataset().addDatasetRegionsListListener(datasetRegionsListListener);
            }
            if (chart.getActiveRegion().isPresent()) {
                regionsTable.selectRegion(chart.getActiveRegion().get());
            }
        });
        updateActiveChartRegions();
        selectedRowRegionsTableListener = this:: setSelectedRowRegionsTableListener;
        regionsTable.getSelectionModel().selectedItemProperty().addListener(selectedRowRegionsTableListener);
        updateButtonBindings();
    }

    /**
     * Removes any previous disable property bindings for the buttons fileMenuButton, addRegionButton, autoIntegrateButton and removeAllButton,
     * and creates a new boolean binding for each disable property using the newChart
     */
    private void updateButtonBindings() {
        fileMenuButton.disableProperty().unbind();
        addRegionButton.disableProperty().unbind();
        autoIntegrateButton.disableProperty().unbind();
        removeAllButton.disableProperty().unbind();
        BooleanBinding disableButtonBinding = Bindings.createBooleanBinding(() -> PolyChart.getCurrentDatasetProperty().get() == null || PolyChart.getCurrentDatasetProperty().get().getNDim() > 1, PolyChart.getCurrentDatasetProperty());
        fileMenuButton.disableProperty().bind(disableButtonBinding);
        addRegionButton.disableProperty().bind(disableButtonBinding);
        autoIntegrateButton.disableProperty().bind(disableButtonBinding);
        removeAllButton.disableProperty().bind(disableButtonBinding);
    }

    /**
     * Selects the integral in the chart and centres the chart view on the selected region if it is not already in view.
     * @param observableValue The observable dataset region property from the table selection model.
     * @param oldRegion The old selected dataset region.
     * @param newRegion The new selected dataset region
     */
    private void setSelectedRowRegionsTableListener(ObservableValue<? extends DatasetRegion> observableValue, DatasetRegion oldRegion, DatasetRegion newRegion) {
        chart.selectIntegral(newRegion);
        if (newRegion != null) {
            double centre = newRegion.getAvgPPM(0);
            if (!chart.isInView(0, centre, 0.2)) {
                Double[] positions = {centre};
                chart.moveTo(positions);
            }
        }
        chart.refresh();
    }

    public void setRegions(List<DatasetRegion> regions) {
        regionsTable.setRegions(regions);
    }

    /**
     * Prompt user for a regions file and attempt to load the regions.
     */
    private void loadRegions() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read Regions File");
        File regionFile = chooser.showOpenDialog(null);
        if (regionFile != null) {
            try {
                Analyzer analyzer = Analyzer.getAnalyzer((Dataset) chart.getDataset());
                analyzer.loadRegions(regionFile);
                updateActiveChartRegions();
                chart.chartProps.setIntegrals(true);
                chart.chartProps.setRegions(true);
                chart.refresh();
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
                GUIUtils.warn("Error reading regions file", ioE.getMessage());
            }
        }
    }

    /**
     * Save the regions from the regionsTable to a regions file.
     */
    private void saveRegions() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Regions File");
        File regionFile = chooser.showSaveDialog(null);
        if (regionFile != null) {
            DatasetRegion.saveRegions(FileUtils.addFileExtensionIfMissing(regionFile, "txt"), regionsTable.getItems());
        }
    }

    /**
     * Get the regions from the active chart and update the regions table.
     */
    public void updateActiveChartRegions() {
        List<DatasetAttributes> datasetAttributes = chart.getDatasetAttributes();
        List<DatasetRegion> regions;
        if (!datasetAttributes.isEmpty()) {
            regions = new ArrayList<>(datasetAttributes.get(0).getDataset().getReadOnlyRegions());
        } else {
            regions = new ArrayList<>();
        }
        setRegions(regions);
    }

    /**
     * Listener that update the selected row in the chart to the active region.
     * @param observableValue The Active Region
     * @param oldRegion The old value of the active region
     * @param newRegion The new value of the active region
     */
    private void updateActiveRegion(ObservableValue<? extends DatasetRegion> observableValue, DatasetRegion oldRegion, DatasetRegion newRegion) {
        if (newRegion == null) {
            return;
        }
        regionsTable.getSelectionModel().selectedItemProperty().removeListener(selectedRowRegionsTableListener);
        regionsTable.selectRegion(newRegion);
        regionsTable.getSelectionModel().selectedItemProperty().addListener(selectedRowRegionsTableListener);
    }

    /**
     * Add a new region to the chart and the regions table based on the current vertical crosshairs position
     * on the active chart.
     */
    public void addRegion() {
        Dataset dataset = (Dataset) chart.getDataset();
        double[] ppms = chart.getVerticalCrosshairPositions();
        DatasetRegion region = new DatasetRegion(ppms[0], ppms[1]);
        try {
            region.measure(dataset);
        } catch (IOException e) {
            log.warn("Unable to add region. {}", e.getMessage(), e);
            return;
        }
        dataset.addRegion(region);
        chart.chartProps.setRegions(true);
        chart.chartProps.setIntegrals(true);
        chart.refresh();
    }

    /**
     * Gets the RegionsTableController. A new controller is created if one has not already been made.
     * @return The RegionsTableController instance.
     */
    public static RegionsTableController getRegionsTableController() {
        if (regionsTableController == null) {
            regionsTableController = RegionsTableController.create();
        }
        return regionsTableController;
    }
}
