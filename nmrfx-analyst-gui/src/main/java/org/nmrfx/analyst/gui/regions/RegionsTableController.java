package org.nmrfx.analyst.gui.regions;


import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.tools.SimplePeakRegionTool;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.DatasetRegionsListListener;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
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
public class RegionsTableController implements Initializable, StageBasedController {
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
    private final DatasetRegionsListListener datasetRegionsListListener = this::updateRegions;

    private ChangeListener<DatasetRegion> selectedRowRegionsTableListener;

    public static RegionsTableController create() {
        regionsTableController = Fxml.load(RegionsTableController.class, "RegionsScene.fxml")
                .withNewStage("Regions")
                .getController();

        return regionsTableController;
    }

    public void show() {
        updateActiveChartRegions();
        stage.show();
        stage.toFront();
    }

    @Override
    public void initialize(URL location, ResourceBundle rb) {
        regionsTable = new RegionsTable();
        regionsBorderPane.setCenter(regionsTable);
        chart = PolyChartManager.getInstance().getActiveChart();
        chart.addRegionListener(activeDatasetRegionListener);
        if (chart.getDataset() != null) {
            chart.getDataset().addDatasetRegionsListListener(datasetRegionsListListener);
        }
        PolyChartManager.getInstance().currentDatasetProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
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
            SimplePeakRegionTool peakRegionTool = (SimplePeakRegionTool) chart.getFXMLController().getTool(SimplePeakRegionTool.class);
            if (peakRegionTool != null) {
                peakRegionTool.findRegions();
            }
        });
        removeAllButton.setOnAction(event -> {
            SimplePeakRegionTool peakRegionTool = (SimplePeakRegionTool) chart.getFXMLController().getTool(SimplePeakRegionTool.class);
            if (peakRegionTool != null) {
                peakRegionTool.clearAnalysis(false);
            }
        });
        PolyChartManager.getInstance().activeChartProperty().addListener(this::activeChartUpdatedListener);
        updateActiveChartRegions();
        selectedRowRegionsTableListener = this::setSelectedRowRegionsTableListener;
        regionsTable.getSelectionModel().selectedItemProperty().addListener(selectedRowRegionsTableListener);
        updateButtonBindings();
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Listener for PolyChart active chart property. Updates chart, button bindings, and regions. Adds and removes
     * listeners
     *
     * @param observableValue The active chart property.
     * @param oldChart        The previously set PolyChart.
     * @param newChart        The newly set PolyChart.
     */
    private void activeChartUpdatedListener(ObservableValue<? extends PolyChart> observableValue, PolyChart oldChart, PolyChart newChart) {
        if (chart != null) {
            chart.removeRegionListener(activeDatasetRegionListener);
            if (chart.getDataset() != null) {
                chart.getDataset().removeDatasetRegionsListListener(datasetRegionsListListener);
            }
        }
        chart = newChart;
        updateButtonBindings();
        if (chart == null) {
            return;
        }
        updateActiveChartRegions();
        chart.addRegionListener(activeDatasetRegionListener);
        if (chart.getDataset() != null) {
            chart.getDataset().addDatasetRegionsListListener(datasetRegionsListListener);
        }
        chart.getActiveRegion().ifPresent(activeRegion -> regionsTable.selectRegion(activeRegion));
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
        BooleanBinding disableButtonBinding = Bindings.createBooleanBinding(() -> {
            if (PolyChartManager.getInstance().currentDatasetProperty().get() == null) return true;
            return PolyChartManager.getInstance().currentDatasetProperty().get().getNDim() > 1;
        }, PolyChartManager.getInstance().currentDatasetProperty());
        fileMenuButton.disableProperty().bind(disableButtonBinding);
        addRegionButton.disableProperty().bind(disableButtonBinding);
        autoIntegrateButton.disableProperty().bind(disableButtonBinding);
        removeAllButton.disableProperty().bind(disableButtonBinding);
    }

    /**
     * Selects the integral in the chart and centres the chart view on the selected region if it is not already in view.
     *
     * @param observableValue The observable dataset region property from the table selection model.
     * @param oldRegion       The old selected dataset region.
     * @param newRegion       The new selected dataset region
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

    void updateRegions(List<DatasetRegion> regions) {
        updateActiveChartRegions();
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
                AnalystApp.getShapePrefs(analyzer.getFitParameters(false));
                analyzer.loadRegions(regionFile);
                updateActiveChartRegions();
                chart.getChartProperties().setIntegralValues(true);
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
        List<DatasetRegion> allRegions = new ArrayList<>();
        for (DatasetAttributes datasetAttribute : datasetAttributes) {
            System.out.println(datasetAttribute.getDataset() + " " + datasetAttribute.getDataset().getReadOnlyRegions().size());
            allRegions.addAll(datasetAttribute.getDataset().getReadOnlyRegions());
        }
        setRegions(allRegions);
    }

    /**
     * Listener that update the selected row in the chart to the active region.
     *
     * @param observableValue The Active Region
     * @param oldRegion       The old value of the active region
     * @param newRegion       The new value of the active region
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
        double[] ppms = chart.getCrossHairs().getVerticalPositions();
        chart.addRegion(ppms[0], ppms[1]);
        chart.getChartProperties().setIntegralValues(true);
        chart.refresh();
    }

    /**
     * Gets the RegionsTableController. A new controller is created if one has not already been made.
     *
     * @return The RegionsTableController instance.
     */
    public static RegionsTableController getRegionsTableController() {
        if (regionsTableController == null) {
            regionsTableController = RegionsTableController.create();
        }
        return regionsTableController;
    }

    public static void updateIfExists() {
        if (regionsTableController != null) {
            regionsTableController.updateActiveChartRegions();
        }
    }
}
