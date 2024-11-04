/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2023 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.molecule.MoleculeMenuActions;
import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.analyst.gui.tools.RunAboutGUI;
import org.nmrfx.analyst.gui.tools.ScannerTool;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.PDFGraphicsContext;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.peaks.PeakLinker;
import org.nmrfx.processor.datasets.peaks.PeakListAlign;
import org.nmrfx.processor.datasets.peaks.PeakNeighbors;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.jcamp.JCAMPData;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayTool;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.processor.gui.tools.SpectrumComparator;
import org.nmrfx.processor.gui.undo.UndoManager;
import org.nmrfx.processor.gui.utils.FileExtensionFilterType;
import org.nmrfx.processor.processing.ProcessingOperationInterface;
import org.nmrfx.processor.processing.ProcessingSection;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.ColorProperty;
import org.nmrfx.utils.properties.PublicPropertyContainer;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.DoubleFunction;

import static org.nmrfx.analyst.gui.AnalystApp.getFXMLControllerManager;
import static org.nmrfx.processor.gui.controls.GridPaneCanvas.getGridDimensionInput;

@PluginAPI("parametric")
public class FXMLController implements Initializable, StageBasedController, PublicPropertyContainer, PeakNavigable {
    private static final Logger log = LoggerFactory.getLogger(FXMLController.class);
    public static final int MAX_INITIAL_TRACES = 32;

    private static final int PSEUDO_2D_SIZE_THRESHOLD = 100;

    private static PeakAttrController peakAttrController = null;
    private static String docString = null;
    private static File initialDir = null;
    private final SimpleObjectProperty<List<Peak>> selectedPeaks = new SimpleObjectProperty<>();
    private final VBox phaserBox = new VBox();
    private final ListView<String> datasetListView = new ListView<>();
    private final boolean[][] crossHairStates = new boolean[2][2];
    private final SpectrumStatusBar statusBar = new SpectrumStatusBar(this);
    private final Set<ControllerTool> tools = new HashSet<>();
    private final SimpleObjectProperty<Cursor> cursorProperty = new SimpleObjectProperty<>(CanvasCursor.SELECTOR.getCursor());
    private final ObservableList<PolyChart> charts = FXCollections.observableArrayList();
    private final BooleanProperty sliceStatus = new SimpleBooleanProperty(false);
    private final UndoManager undoManager = new UndoManager();
    private final Button haltButton = GlyphsDude.createIconButton(FontAwesomeIcon.STOP, "Halt", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
    private final Button favoriteButton = GlyphsDude.createIconButton(FontAwesomeIcon.HEART, "Favorite", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
    private final SimpleBooleanProperty processControllerVisible = new SimpleBooleanProperty(false);
    private final ToggleButton processorButton = new ToggleButton("Processor");
    private final NmrControlRightSidePane nmrControlRightSidePane = new NmrControlRightSidePane();

    private ChartProcessor chartProcessor;
    private Stage stage = null;
    private boolean isFID = true;
    private SpectrumMeasureBar measureBar = null;
    private PeakNavigator peakNavigator;
    private SpectrumComparator spectrumComparator;
    private double widthScale = 10.0;
    private Phaser phaser;
    private AttributesController attributesController;
    private ToolController toolController;
    private ContentController contentController;
    private AnalyzerBar analyzerBar = null;
    @FXML
    private HBox topLevelHBox;
    @FXML
    private VBox topBar;
    @FXML
    private ToolBar toolBar;
    @FXML
    private VBox btoolVBox;
    @FXML
    private VBox bottomBox;
    @FXML
    private StackPane chartPane;
    @FXML
    private BorderPane borderPane;
    @FXML
    private BorderPane mainBox;
    @FXML
    private HBox leftBar;
    @FXML SplitPane splitPane;
    ScannerTool scannerTool;

    private double previousStageRestoreWidth = 0;
    private double previousStageRestoreProcControllerWidth = 0;
    private boolean previousStageRestoreNmrControlRightSideContentVisible = false;
    private PolyChart activeChart = null;
    private ChartDrawingLayers chartDrawingLayers;
    private final BooleanProperty minBorders = new SimpleBooleanProperty(this, "minBorders", false);
    private final ColorProperty bgColor = new ColorProperty(this, "bgColor", null);
    private final ColorProperty axesColor = new ColorProperty(this, "axesColor", null);
    private boolean viewProcessorControllerIfPossible = true;

    public Color getBgColor() {
        return bgColor.get();
    }

    public List<Peak> getSelectedPeaks() {
        return selectedPeaks.get();
    }

    public SimpleObjectProperty<List<Peak>> selectedPeaksProperty() {
        return selectedPeaks;
    }

    public boolean isProcessControllerVisible() {
        return processControllerVisible.get();
    }

    public SimpleBooleanProperty processControllerVisibleProperty() {
        return processControllerVisible;
    }

    public Color getAxesColor() {
        return axesColor.get();
    }

    public Cursor getCursor() {
        return cursorProperty.get();
    }

    public void setCursor(Cursor cursor) {
        cursorProperty.set(cursor);
    }

    protected void close() {
        // need to make copy of charts as the call to chart.close will remove the chart from charts
        // resulting in a java.util.ConcurrentModificationException
        saveDatasets();
        List<PolyChart> tempCharts = new ArrayList<>(charts);
        for (PolyChart chart : tempCharts) {
            chart.close();
        }
        chartPane.getChildren().clear();
        chartPane = null;
        charts.clear();
    }

    public void saveDatasets() {
        var chartProcessor = getChartProcessor();
        if (chartProcessor != null) {
            var processorController = chartProcessor.getProcessorController();
            if (processorController != null) {
                processorController.saveOnClose();
            }
        }
    }

    public ChartProcessor getChartProcessor() {
        return chartProcessor;
    }

    public void setChartProcessor(ChartProcessor chartProcessor) {
        this.chartProcessor = chartProcessor;
    }

    /**
     * Updates the SpectrumStatusBar menu options based on whether FID mode is on, and the dimensions
     * of the dataset.
     */
    public void updateSpectrumStatusBarOptions(boolean initDataset) {
        if (isFIDActive()) {
            statusBar.setMode(SpectrumStatusBar.DataMode.FID);
        } else {
            ObservableList<DatasetAttributes> datasetAttrList = getActiveChart().getDatasetAttributes();
            datasetAttrList.stream().mapToInt(d -> d.nDim).max().ifPresent(maxNDim -> {
                if (getActiveChart().is1D() && maxNDim > 1) {
                    int maxRows = datasetAttrList.stream()
                            .mapToInt(d -> d.nDim == 1 ? 1 : d.getDataset().getSizeReal(1))
                            .max().orElse(0);
                    if (initDataset && maxRows > MAX_INITIAL_TRACES) {
                        getActiveChart().setDrawlist(0);
                    }
                    statusBar.set1DArray(maxNDim, maxRows);
                } else {
                    statusBar.setMode(SpectrumStatusBar.DataMode.fromDimensions(maxNDim), maxNDim);
                }
            });
        }
    }

    public boolean isFIDActive() {
        return isFID;
    }

    public void setFIDActive(boolean isFID) {
        this.isFID = isFID;
    }

    public PolyChart getActiveChart() {
        return activeChart;
    }

    public void setActiveChart(PolyChart chart) {
        if (activeChart == chart) {
            return;
        }

        isFID = false;
        activeChart = chart;
        PolyChartManager.getInstance().setActiveChart(chart);
        ProcessorController processorController = chart.getProcessorController(false);
        // The chart has a processor controller setup, and can be in FID or Dataset mode.
        if (processorController != null) {
            isFID = !processorController.isViewingDataset();
            chartProcessor = processorController.chartProcessor;
            if (viewProcessorControllerIfPossible || processorButton.isSelected()) {
                nmrControlRightSidePane.addContent(processorController);
            }
            processorButton.setSelected(viewProcessorControllerIfPossible);
        } else {
            processorButton.setSelected(false);
        }
        updateSpectrumStatusBarOptions(false);
        if (attributesController != null) {
            attributesController.setChart(activeChart);
        }
        if (scannerTool != null) {
            scannerTool.setChart(activeChart);
        }
        if (contentController != null) {
            contentController.setChart(activeChart);
        }
    }

    public void processorCreated(Pane pane) {
        processControllerVisible.bind(pane.parentProperty().isNotNull());
        isFID = !getActiveChart().getProcessorController(true).isViewingDataset();
        updateSpectrumStatusBarOptions(true);
    }

    public boolean isPhaseSliderVisible() {
        return borderPane.getRight() == phaserBox || (processControllerVisible.get() && getActiveChart().getProcessorController().isPhaserActive());
    }

    public Stage getStage() {
        return stage;
    }

    private void showDatasetBrowser(ActionEvent event) {
        AnalystApp.getAnalystApp().getOrCreateDatasetBrowserController().show();
    }

    public void openAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Open NMR File");
        fileChooser.getExtensionFilters().addAll(
                FileExtensionFilterType.NMR_FILES.getFilter(),
                FileExtensionFilterType.ALL_FILES.getFilter()
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            setInitialDirectory(selectedFile.getParentFile());
            openFile(selectedFile.toString(), true, false);
        }
        stage.setResizable(true);
    }

    public void openFile(String filePath, boolean clearOps, boolean appendFile) {
        openFile(filePath, clearOps, appendFile, null);
    }

    public void openFile(String filePath, boolean clearOps, boolean appendFile, DatasetType datasetType) {
        NMRData nmrData = getNMRData(filePath);
        if (nmrData == null) {
            return;
        }
        if (nmrData.isFID()) {
            openFile(nmrData, clearOps, appendFile, datasetType);
        } else {
            openDataset(nmrData, appendFile, true);
        }
    }

    public void openFIDForDataset() {
        Dataset dataset = (Dataset) getActiveChart().getDataset();
        if (dataset != null) {
            dataset.sourceFID().ifPresentOrElse(file -> {
                if (file.exists()) {
                    openFile(file.toString(), true, false);
                } else {
                    openAction(null);
                }
            }, () -> openAction(null));
        }
    }

    /**
     * Gets a NMRData object from the filepath.
     *
     * @param filePath The filepath to load the NMRData from.
     * @return An NMRData object or null if there was a problem loading.
     */
    private NMRData getNMRData(String filePath) {
        NMRData nmrData = null;
        try {
            File file = new File(filePath);
            nmrData = NMRDataUtil.loadNMRData(file, null, true);
        } catch (IOException ioE) {
            log.error("Unable to load NMR file: {}", filePath, ioE);
            ExceptionDialog eDialog = new ExceptionDialog(ioE);
            eDialog.showAndWait();
        }
        return nmrData;
    }

    private void openFile(NMRData nmrData, boolean clearOps, boolean appendFile, DatasetType datasetType) {
        boolean reload = false;
        File newFile = new File(nmrData.getFilePath());

        File oldFile = getActiveChart().getDatasetFile();
        if (!appendFile && (oldFile != null)) {
            try {
                if (oldFile.getCanonicalPath().equals(newFile.getCanonicalPath())) {
                    reload = true;
                }
            } catch (java.io.IOException ioE) {
                reload = false;
            }
        }

        if (datasetType != null) {
            nmrData.setPreferredDatasetType(datasetType);
        }
        addFID(nmrData, clearOps, reload);

        PreferencesController.saveRecentFiles(nmrData.getFilePath());
        undoManager.clear();
    }

    /**
     * Gets the dataset from the provided NMRData. The dataset is added to the chart if addDatasetToChart is true. If
     * append is false, the chart is cleared of any datasets before adding the new dataset, if true, the dataset is
     * added to the chart in addition to any datasets already present.
     *
     * @param nmrData           The NMRData object containing the dataset.
     * @param append            Whether to append the new dataset.
     * @param addDatasetToChart Whether to add the opened dataset to the chart.
     * @return The newly opened dataset or null if no dataset was opened.
     */
    private Dataset openDataset(NMRData nmrData, boolean append, boolean addDatasetToChart) {
        File selectedFile = new File(nmrData.getFilePath());
        Dataset dataset = null;
        try {
            setInitialDirectory(selectedFile.getParentFile());
            if (nmrData instanceof NMRViewData nvData) {
                PreferencesController.saveRecentFiles(selectedFile.toString());
                dataset = nvData.getDataset();
            } else if (nmrData instanceof BrukerData brukerData) {
                PreferencesController.saveRecentFiles(selectedFile.toString());
                String suggestedName = brukerData.suggestName(new File(brukerData.getFilePath()));
                String datasetName = GUIUtils.input("Dataset name", suggestedName);
                dataset = brukerData.toDataset(datasetName);
            } else if (nmrData instanceof RS2DData rs2dData) {
                PreferencesController.saveRecentFiles(selectedFile.toString());
                String suggestedName = rs2dData.suggestName(new File(rs2dData.getFilePath()));
                dataset = rs2dData.toDataset(suggestedName);
            } else if (nmrData instanceof JCAMPData jcampData) {
                PreferencesController.saveRecentFiles(selectedFile.toString());
                String suggestedName = jcampData.suggestName(new File(jcampData.getFilePath()));
                dataset = jcampData.toDataset(suggestedName);
            }
        } catch (IOException | DatasetException ex) {
            log.warn(ex.getMessage(), ex);
            GUIUtils.warn("Open Dataset", ex.getMessage());
        }
        if (dataset != null) {
            ProcessorController processorController = getActiveChart().getProcessorController(false);
            if (processorController != null && (!dataset.getFile().equals(chartProcessor.getDatasetFile()))) {
                nmrControlRightSidePane.removeContent(processorController);
                getActiveChart().setProcessorController(null);
                processorController.cleanUp();
            }
            if (addDatasetToChart) {
                addDataset(getActiveChart(), dataset, append, false);
            }
        } else {
            log.info("Unable to find a dataset format for: {}", selectedFile);
        }
        stage.setResizable(true);
        return dataset;
    }

    private void addFID(NMRData nmrData, boolean clearOps, boolean reload) {
        isFID = true;
        // Only create a new processor controller, if the active chart does not have one already created.
        ProcessorController processorController = getActiveChart().getProcessorController(true);
        if (processorController != null) {
            processorButton.setSelected(true);
            processorController.setAutoProcess(false);
            chartProcessor.setData(nmrData, clearOps);
            processorController.viewingDataset(false);
            processorController.updateFileButton();
            nmrControlRightSidePane.addContent(processorController);
            processorController.clearOperationList();
            chartProcessor.clearAllOperations();
            processorController.parseScript("");
            if (!reload) {
                getActiveChart().full();
                getActiveChart().autoScale();
                generateScriptAndParse(nmrData, processorController);
            }
            getActiveChart().clearAnnotations();
            getActiveChart().removeProjections();
            getActiveChart().layoutPlotChildren();
            statusBar.setMode(SpectrumStatusBar.DataMode.FID);
            processorController.hideDatasetToolBar();
            if (PreferencesController.getLoadMoleculeIfPresent()) {
                File file = new File(nmrData.getFilePath());
                try {
                    MoleculeMenuActions.readMoleculeInDirectory(file.getParentFile().toPath());
                } catch (IOException e) {
                    ExceptionDialog exceptionDialog = new ExceptionDialog(e);
                    exceptionDialog.showAndWait();
                }
            }
        } else {
            log.warn("Unable to add FID because controller can not be created.");
        }
    }

    public void addDataset(PolyChart chart, DatasetBase dataset, boolean appendFile, boolean reload) {
        isFID = false;
        if (dataset.getFile() != null) {
            PreferencesController.saveRecentFiles(dataset.getFile().toString());
        }

        DatasetAttributes datasetAttributes = getActiveChart().setDataset(dataset, appendFile, false);
        chart.getCrossHairs().setStates(true, true, true, true);
        getActiveChart().clearAnnotations();
        getActiveChart().clearPopoverTools();
        getActiveChart().removeProjections();
        ProcessorController processorController = getActiveChart().getProcessorController();
        if (processorController != null) {
            processorController.viewingDataset(true);
            if (processorController.chartProcessor.getNMRData() == null) {
                processorController.showDatasetToolBar();
            }
        }
        borderPane.setLeft(null);
        updateSpectrumStatusBarOptions(true);

        phaser.getPhaseOp();
        if (!reload) {
            if (!datasetAttributes.getHasLevel()) {
                getActiveChart().full();
                if (datasetAttributes.getDataset().isLvlSet()) {
                    datasetAttributes.setLvl(datasetAttributes.getDataset().getLvl());
                    datasetAttributes.setHasLevel(true);
                } else {
                    getActiveChart().autoScale();
                }
            }
        }
        getActiveChart().layoutPlotChildren();
        undoManager.clear();
        ProjectBase.getActive().projectChanged(true);
        if (PreferencesController.getLoadMoleculeIfPresent()) {
            Molecule molecule = (Molecule) MoleculeFactory.getActive();
            if (molecule != null) {
                MoleculeUtils.removeMoleculeFromCanvas();
                MoleculeUtils.addActiveMoleculeToCanvas();
            }

        }
    }

    /**
     * Check for existing default processing script and parse if present, otherwise generate
     * and parse a processing script for FID data for 1D and 2D data.
     *
     * @param nmrData             The NMRData set that will be processed.
     * @param processorController The ProcessorController to parse the script with.
     */
    private void generateScriptAndParse(NMRData nmrData, ProcessorController processorController) {
        processorController.setAutoProcess(false);
        int nDim = nmrData.getNDim();
        if (isFIDActive() && chartProcessor != null) {
            boolean loadedScript = chartProcessor.loadDefaultScriptIfPresent();
            if (PreferencesController.getAutoProcessData() && (nDim == 1 || nDim == 2)) {
                if (!loadedScript) {
                    // TODO NMR-5184 update here if there is better way to determine if pseudo2D
                    // This is an estimate of whether the 2D data is pseudo2D, some pseudo2Ds may still be processed as 2Ds
                    boolean isPseudo2D = nmrData.getNVectors() < PSEUDO_2D_SIZE_THRESHOLD && nDim == 2;
                    String script = chartProcessor.getGenScript(isPseudo2D);
                    processorController.parseScript(script);
                    log.info("Autogenerated processing script.");
                }
                processorController.processDataset(false);
                processorController.setAutoProcess(true);
            } else if (nDim > 2) {
                log.info("Script was not autogenerated because number of dimensions is greater than 2.");
            }
        }
    }

    /**
     * Opens the dataset from the selected file.
     *
     * @param selectedFile      The file containing the dataset.
     * @param append            Whether to append the new dataset.
     * @param addDatasetToChart Whether to add the opened dataset to the chart.
     * @return The newly opened dataset or null if no dataset was opened.
     */
    public Dataset openDataset(File selectedFile, boolean append, boolean addDatasetToChart) {
        if (selectedFile == null) {
            return null;
        }
        NMRData nmrData = getNMRData(selectedFile.toString());
        if (nmrData == null) {
            return null;
        }
        return openDataset(nmrData, append, addDatasetToChart);
    }

    public void addNoDrawAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Add NMR Dataset");
        fileChooser.getExtensionFilters().addAll(
                FileExtensionFilterType.NMR_DATASET.getFilter(),
                FileExtensionFilterType.ALL_FILES.getFilter()
        );
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(null);
        if (selectedFiles != null) {
            try {
                for (File selectedFile : selectedFiles) {
                    setInitialDirectory(selectedFile.getParentFile());
                    NMRData nmrData = NMRDataUtil.getFID(selectedFile);
                    if (nmrData instanceof NMRViewData nmrviewData) {
                        PreferencesController.saveRecentFiles(selectedFile.toString());
                        Dataset dataset = nmrviewData.getDataset();
                        dataset.addFile();
                    }
                }
            } catch (IllegalArgumentException | IOException iaE) {
                ExceptionDialog eDialog = new ExceptionDialog(iaE);
                eDialog.showAndWait();
            }
        }
    }

    public NmrControlRightSidePane getNmrControlRightSidePane() {
        return nmrControlRightSidePane;
    }

    public void closeFile(File target) {
        getActiveChart().removeAllDatasets();
        // removeAllDatasets in chart only stops displaying them, so we need to actually close the dataset
        List<DatasetBase> currentDatasets = new ArrayList<>(Dataset.datasets());
        for (DatasetBase datasetBase : currentDatasets) {
            Dataset dataset = (Dataset) datasetBase;
            File file = dataset.getFile();
            if (file != null) {
                try {
                    if (Files.exists(file.toPath())) {
                        if (Files.isSameFile(target.toPath(), file.toPath())) {
                            dataset.close();
                        }
                    }
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    public void showPeakAttrAction(ActionEvent event) {
        showPeakAttr();
        peakAttrController.initIfEmpty();
    }

    public Button getHaltButton() {
        return haltButton;
    }

    public void exportGraphics() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PNG");
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.getExtensionFilters().addAll(
                FileExtensionFilterType.ALL_FILES.getFilter()
        );
        File selectedFile = fileChooser.showSaveDialog(null);
        String name = selectedFile.getName();
        int dot = name.lastIndexOf('.');
        String extension = "";
        if (dot != -1) {
             extension = name.substring(dot+1);
        }
        switch (extension) {
            case "svg" -> exportSVG(selectedFile);
            case "pdf" -> exportPDF(selectedFile);
            case "png" -> exportPNG(selectedFile);
            default -> {
                String fileName = selectedFile.toString() + ".svg";
                exportSVG(fileName);
            }
        }
    }

    public void exportPNG(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PNG");
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.getExtensionFilters().addAll(
                FileExtensionFilterType.PNG.getFilter(),
                FileExtensionFilterType.ALL_FILES.getFilter()
        );
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            try {
                chartDrawingLayers.getTopPane().setVisible(false);
                GUIUtils.snapNode(chartPane, selectedFile);
            } catch (IOException ex) {
                GUIUtils.warn("Error saving png file", ex.getLocalizedMessage());
            } finally {
                chartDrawingLayers.getTopPane().setVisible(true);
            }
        }
    }

    public void exportPNG(File selectedFile) {
        try {
            chartDrawingLayers.getTopPane().setVisible(false);
            GUIUtils.snapNode(chartPane, selectedFile);
        } catch (IOException ex) {
            GUIUtils.warn("Error saving png file", ex.getLocalizedMessage());
        } finally {
            chartDrawingLayers.getTopPane().setVisible(true);
        }
    }

    public void exportPDFAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.getExtensionFilters().addAll(
                FileExtensionFilterType.PDF.getFilter(),
                FileExtensionFilterType.ALL_FILES.getFilter()
        );
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            exportPDF(selectedFile);
        }
    }

    private void exportPDF(File file) {
        exportPDF(file.toString());
    }

    public void exportPDF(String fileName) {
        if (fileName != null) {
            try {
                PDFGraphicsContext pdfGC = new PDFGraphicsContext();
                pdfGC.create(true, chartDrawingLayers.getWidth(), chartDrawingLayers.getHeight(), fileName);
                for (PolyChart chart : charts) {
                    chart.exportVectorGraphics(pdfGC);
                }
                pdfGC.saveFile();
            } catch (GraphicsIOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        stage.setResizable(true);
    }

    public void exportSVGAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.getExtensionFilters().addAll(
                FileExtensionFilterType.SVG.getFilter(),
                FileExtensionFilterType.ALL_FILES.getFilter()
        );
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            exportSVG(selectedFile);
        }
    }

    private void exportSVG(File file) {
        exportSVG(file.toString());
    }

    public void exportSVG(String fileName) {
        if (fileName != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            try {
                svgGC.create(chartDrawingLayers.getWidth(), chartDrawingLayers.getHeight(), fileName);
                for (PolyChart chart : charts) {
                    chart.exportVectorGraphics(svgGC);
                }
                svgGC.saveFile();
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
        stage.setResizable(true);
    }

    public void copySVGAction(ActionEvent event) {
        SVGGraphicsContext svgGC = new SVGGraphicsContext();
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            svgGC.create(chartDrawingLayers.getWidth(), chartDrawingLayers.getHeight(), stream);
            for (PolyChart chart : charts) {
                chart.exportVectorGraphics(svgGC);
            }
            svgGC.saveFile();
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            DataFormat svgFormat = DataFormat.lookupMimeType("image/svg+xml");
            if (svgFormat == null) {
                svgFormat = new DataFormat("image/svg+xml");
            }
            content.put(svgFormat, ByteBuffer.wrap(stream.toByteArray()));
            content.put(DataFormat.PLAIN_TEXT, stream.toString());
            clipboard.setContent(content);
        } catch (GraphicsIOException ex) {
            ExceptionDialog eDialog = new ExceptionDialog(ex);
            eDialog.showAndWait();
        }

        stage.setResizable(true);
    }

    public void updateAttrDims() {
        if (nmrControlRightSidePane.isContentShowing(attributesController)) {
            attributesController.setChart(getActiveChart());
        }
    }

    public void updateDatasetAttributeControls() {
        if (nmrControlRightSidePane.isContentShowing(attributesController)) {
            attributesController.updateDatasetAttributeControls();
        }
        if (scannerTool != null) {
            scannerTool.getScanTable().refresh();
        }
    }

    public void setDim(String rowName, String dimName) {
        getCharts().forEach(chart -> chart.getFirstDatasetAttributes().ifPresent(attr -> {
            attr.setDim(rowName, dimName);
            getStatusBar().setPlaneRanges();
            chart.updateProjections();
            chart.updateProjectionBorders();
            chart.updateProjectionScale();
            for (int i = 0; i < chart.getNDim(); i++) {
                // fixme  should be able to swap existing limits, not go to full
                chart.full(i);
            }
        }));
    }
    public SpectrumStatusBar getStatusBar() {
        return statusBar;
    }

    public void refreshPeakView(String peakSpecifier) {
        Peak peak = PeakList.getAPeak(peakSpecifier);
        log.info("show peak2 {} {}", peakSpecifier, peak);

        if (peak != null) {
            refreshPeakView(peak);
        }
    }

    @Override
    public void refreshPeakView(Peak peak) {
        if (peak != null) {
            PeakDisplayTool.gotoPeak(peak, charts, widthScale);
        }
    }

    @Override
    public void refreshPeakView() {
    }

    @Override
    public void refreshPeakListView(PeakList peakList) {
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        topLevelHBox.getChildren().add(nmrControlRightSidePane);
        borderPane.setLeft(null);
        initializeNmrControlRightSideContentToggleButtons();
        chartDrawingLayers = new ChartDrawingLayers(this, chartPane);
        activeChart = PolyChartManager.getInstance().create(this, chartDrawingLayers);
        initToolBar(toolBar);
        initStatusBar();
        charts.add(activeChart);
        chartDrawingLayers.getGrid().addCharts(1, charts);

        mainBox.layoutBoundsProperty().addListener((ObservableValue<? extends Bounds> arg0, Bounds arg1, Bounds arg2) -> {
            if (arg2.getWidth() < 1.0 || arg2.getHeight() < 1.0) {
                return;
            }
            chartDrawingLayers.getGrid().requestLayout();
        });
        SplitPane.setResizableWithParent(bottomBox, false);

        statusBar.setMode(SpectrumStatusBar.DataMode.DATASET_1D);
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                crossHairStates[iCross][jOrient] = true;
            }
        }
        phaser = new Phaser(this, phaserBox, Orientation.VERTICAL);
        nmrControlRightSidePane.addContentListener(this::updateStageSize);
        cursorProperty.addListener(e -> setCursor());
        attributesController = AttributesController.create(this);
        toolController = ToolController.create(this);

        contentController = ContentController.create(this);
    }

    /**
     * Initialize the toggle buttons Processing, Attributes and Contents. On mac these buttons will appear right
     * aligned in a separate top menu in the window, otherwise they will appear right aligned in the file menu.
     */
    private void initializeNmrControlRightSideContentToggleButtons() {
        // Note processor button is already created, just needs to have action listener and style setup
        ToggleButton attributesButton = new ToggleButton("Attributes");
        ToggleButton contentButton = new ToggleButton("Content");
        ToggleButton toolButton = new ToggleButton("Tools");
        SegmentedButton groupButton = new SegmentedButton(processorButton, contentButton, attributesButton, toolButton);
        groupButton.getButtons().forEach(button -> {
            // need to listen to property instead of action so toggle method is triggered when setSelected is called.
            button.selectedProperty().addListener((obs, oldValue, newValue) ->
                    toggleNmrControlRightSideContent(attributesButton, contentButton, processorButton, toolButton));
            button.getStyleClass().add("toolButton");
        });
        processorButton.disableProperty().addListener((observable, oldValue, newValue) -> {
            if(Boolean.TRUE.equals(newValue) && processorButton.isSelected()) {
                viewProcessorControllerIfPossible = true;
            }
        });
        if (AnalystApp.isMac()) {
            ToolBar toggleButtonToolbar = new ToolBar();
            // Remove padding from top and bottom to match style of how the buttons appear on non mac os
            Insets current = toggleButtonToolbar.getPadding();
            toggleButtonToolbar.setPadding(new Insets(0, current.getRight(), 0, current.getLeft()));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            toggleButtonToolbar.getItems().addAll(spacer, groupButton);
            topBar.getChildren().add(0, toggleButtonToolbar);
        } else {
            MenuBar menuBar = AnalystApp.getMenuBar();
            groupButton.maxHeightProperty().bind(menuBar.heightProperty());
            StackPane sp = new StackPane(menuBar, groupButton);
            sp.setAlignment(Pos.CENTER_RIGHT);
            topBar.getChildren().add(0, sp);
        }
    }

    /**
     * Called by controller manager directly after creation by FXMLLoader.
     * Used to pass additional parameters that can't be passed to a constructor.
     * <p>
     * Note that this will be called after the initialize() method.
     *
     * @param stage the stage managed by this controller.
     */
    @Override
    public void setStage(Stage stage) {
        this.stage = stage;

        stage.maximizedProperty().addListener(this::adjustSizeAfterMaximize);
    }

    public void initStageGeometry() {
        boolean firstStage = AnalystApp.getFXMLControllerManager().getControllers().size() == 1;
        var bounds = Screen.getPrimary().getBounds();
        double scale = 0.8;
        double width = bounds.getWidth() - 500.0;  // allow for expanded right pane
        double height = bounds.getHeight() * scale;
        double xPos = 20.0;
        double yPos = 50.0;
        // make later stages smaller and approximately centered
        if (!firstStage) {
            width = width * 0.75;
            height = height * 0.75;
            xPos = Math.max((bounds.getWidth() - 500.0 - width) / 2.0, 20);
            yPos = (bounds.getHeight() - height) / 2.0;
        }
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(xPos);
        stage.setY(yPos);
    }

    public BorderPane getMainBox() {
        return mainBox;
    }

    public ToolController getToolController() {
        return toolController;
    }

    public Cursor getCurrentCursor() {
        return chartDrawingLayers.getCursor();
    }

    public void setCurrentCursor(Cursor cursor) {
        chartDrawingLayers.setCursor(cursor);
    }

    public void setCursor() {
        Cursor cursor = cursorProperty.getValue();
        chartDrawingLayers.setCursor(cursor);
        for (PolyChart chart : charts) {
            chart.getCrossHairs().setAllStates(CanvasCursor.isCrosshair(cursor));
        }
        statusBar.updateCursorBox();
    }

     DoubleFunction getCrossHairUpdateFunction(int crossHairNum, Orientation orientation) {
        return value -> {
            PolyChart chart = getActiveChart();
            if (CanvasCursor.isCrosshair(getCurrentCursor())) {
                chart.getCrossHairs().updatePosition(crossHairNum, orientation, value);
            } else {
                int axNum = orientation == Orientation.VERTICAL ? 0 : 1;
                final double v1;
                final double v2;
                if (crossHairNum == 0) {
                    v1 = chart.getAxes().get(axNum).getLowerBound();
                    v2 = value;
                } else {
                    v1 = value;
                    v2 = chart.getAxes().get(axNum).getUpperBound();
                }
                chart.getAxes().setMinMax(axNum, v1, v2);
                chart.refresh();
            }
            return null;
        };
    }


    public void setPhaser(Phaser phaser) {
        this.phaser = phaser;
    }

    public Phaser getPhaser() {
        return phaser;
    }

    public boolean getCrossHairState(int index, Orientation orientation) {
        return crossHairStates[index][orientation.ordinal()];
    }

    public void enableFavoriteButton() {
        favoriteButton.setDisable(ProjectBase.getActive().getProjectDir() == null);
    }

    public void showPeakNavigator() {
        if (peakNavigator == null) {
            ToolBar navBar = new ToolBar();
            bottomBox.getChildren().add(navBar);
            peakNavigator = PeakNavigator.create(this).onClose(this::removePeakNavigator).showAtoms().initialize(navBar);
            peakNavigator.setPeakList();
            addScaleBox(peakNavigator, navBar);
        }
    }

    private void removePeakNavigator(Object o) {
        if (peakNavigator != null) {
            peakNavigator.removePeakList();
            bottomBox.getChildren().remove(peakNavigator.getToolBar());
            peakNavigator = null;
            if (bottomBox.getChildren().isEmpty()) {
                splitPane.setDividerPosition(0, 1.0);
            }
        }
    }

    public void addScaleBox(PeakNavigator navigator, ToolBar navBar) {
        ObservableList<Double> scaleList = FXCollections.observableArrayList(0.0, 2.5, 5.0, 7.5, 10.0, 15.0, 20.0);
        ChoiceBox<Double> scaleBox = new ChoiceBox<>(scaleList);
        scaleBox.setValue(widthScale);
        scaleBox.setOnAction(e -> {
            widthScale = scaleBox.getValue();
            Peak peak = navigator.getPeak();
            if (peak != null) {
                refreshPeakView(peak);
            }
        });
        navBar.getItems().add(scaleBox);
    }

    public void showSpectrumComparator() {
        if (spectrumComparator == null) {
            VBox vBox = new VBox();
            bottomBox.getChildren().add(vBox);
            spectrumComparator = new SpectrumComparator(this, this::removeSpectrumComparator);
            spectrumComparator.initPathTool(vBox);
        }
    }

    private void removeSpectrumComparator(Object o) {
        if (spectrumComparator != null) {
            bottomBox.getChildren().remove(spectrumComparator.getToolBar());
            spectrumComparator = null;
            if (bottomBox.getChildren().isEmpty()) {
                splitPane.setDividerPosition(0, 1.0);
            }
        }
    }

    public SpectrumMeasureBar getSpectrumMeasureBar() {
        return measureBar;
    }

    public void showSpectrumMeasureBar() {
        if (measureBar == null) {
            VBox vBox = new VBox();
            measureBar = new SpectrumMeasureBar(this, this::removeSpectrumMeasureBar);
            measureBar.buildBar(vBox);
            bottomBox.getChildren().add(vBox);
        }
    }

    private void removeSpectrumMeasureBar(Object o) {
        if (measureBar != null) {
            bottomBox.getChildren().remove(measureBar.getToolBar());
            if (bottomBox.getChildren().isEmpty()) {
                splitPane.setDividerPosition(0, 1.0);
            }
            measureBar = null;
        }
    }

    public void showAnalyzerBar() {
        if (analyzerBar == null) {
            VBox vBox = new VBox();
            analyzerBar = new AnalyzerBar(this, this::removeAnalyzerBar);
            analyzerBar.buildBar(vBox);
            bottomBox.getChildren().add(vBox);
        }
    }

    private void removeAnalyzerBar(Object o) {
        if (analyzerBar != null) {
            bottomBox.getChildren().remove(analyzerBar.getToolBar());
            if (bottomBox.getChildren().isEmpty()) {
                splitPane.setDividerPosition(0, 1.0);
            }
            analyzerBar = null;
        }
    }

    public void linkPeakDims() {
        PeakLinker linker = new PeakLinker();
        linker.linkAllPeakListsByLabel("");
    }

    public void removeChart(PolyChart chart) {
        if (chart != null) {
            chartDrawingLayers.getGrid().getChildren().remove(chart);
            charts.remove(chart);
            if (chart == activeChart) {
                if (charts.isEmpty()) {
                    activeChart = null;
                } else {
                    activeChart = charts.get(0);
                }
            }
            chartDrawingLayers.getGrid().requestLayout();
            for (PolyChart refreshChart : charts) {
                refreshChart.layoutPlotChildren();
            }
        }
    }

    public void overlay() {
        setChartDisable(true);
        List<DatasetAttributes> current = new ArrayList<>();
        for (PolyChart chart : charts) {
            current.addAll(chart.getDatasetAttributes());
        }
        setNCharts(1);
        PolyChart chart = charts.get(0);
        List<DatasetAttributes> datasetAttrs = chart.getDatasetAttributes();
        datasetAttrs.clear();
        datasetAttrs.addAll(current);
        arrange(1);

        setChartDisable(false);
        draw();
    }

    public void setChartDisable(boolean state) {
        for (PolyChart chart : charts) {
            chart.setChartDisabled(state);
        }
    }

    public void setNCharts(int nCharts) {
        int nCurrent = charts.size();
        if (nCurrent > nCharts) {
            for (int i = nCurrent - 1; i >= nCharts; i--) {
                charts.get(i).close();
            }
        } else if (nCharts > nCurrent) {
            int nNew = nCharts - nCurrent;
            for (int i = 0; i < nNew; i++) {
                addChart();
            }
        }
        chartDrawingLayers.getGrid().addCharts(chartDrawingLayers.getGrid().getRows(), charts);
    }

    public void arrange(int nRows) {
        chartDrawingLayers.getGrid().setRows(nRows);
        chartDrawingLayers.getGrid().calculateAndSetOrientation();
    }

    public GridPaneCanvas getGridPaneCanvas() {
        return chartDrawingLayers.getGrid();
    }
    public void draw() {
        chartDrawingLayers.getGrid().layoutChildren();
    }

    public void addChart() {
        PolyChart chart = PolyChartManager.getInstance().create(this, chartDrawingLayers);
        charts.add(chart);
        chart.setChartDisabled(true);
        chartDrawingLayers.getGrid().addChart(chart);
        activeChart = chart;
    }

    public void setBorderState(boolean state) {
        minBorders.set(state);
        chartDrawingLayers.getGrid().updateConstraints();
        chartDrawingLayers.getGrid().layoutChildren();
    }

    public double[][] prepareChildren(int nRows, int nCols) {
        int iChild = 0;
        double maxBorderX = 0.0;
        double maxBorderY = 0.0;
        double[][] bordersGrid = new double[6][];
        bordersGrid[0] = new double[nCols];
        bordersGrid[1] = new double[nCols];
        bordersGrid[2] = new double[nRows];
        bordersGrid[3] = new double[nRows];
        bordersGrid[4] = new double[nCols];
        bordersGrid[5] = new double[nRows];

        for (PolyChart chart : charts) {
            var gridPos = getGridPaneCanvas().getGridLocation(chart);
            int iRow = gridPos.rows();
            int iCol = gridPos.columns();
            if (minBorders.get()) {
                chart.getAxes().setAxisState(iCol == 0, iRow == (nRows - 1));
            } else {
                chart.getAxes().setAxisState(true, true);
            }
            Insets borders = chart.getMinBorders();
            bordersGrid[0][iCol] = Math.max(bordersGrid[0][iCol], borders.getLeft());
            bordersGrid[1][iCol] = Math.max(bordersGrid[1][iCol], borders.getRight());
            bordersGrid[2][iRow] = Math.max(bordersGrid[2][iRow], borders.getBottom());
            bordersGrid[3][iRow] = Math.max(bordersGrid[3][iRow], borders.getTop());
            maxBorderX = Math.max(maxBorderX, borders.getLeft());
            maxBorderY = Math.max(maxBorderY, borders.getBottom());

            double ppmX0 = chart.getAxes().getX().getLowerBound();
            double ppmX1 = chart.getAxes().getX().getUpperBound();
            double ppmY0 = chart.getAxes().getY().getLowerBound();
            double ppmY1 = chart.getAxes().getY().getUpperBound();
            if (minBorders.get()) {
                double nucScaleX = 1.0;
                double nucScaleY = 1.0;
                if (!chart.getDatasetAttributes().isEmpty()) {
                    DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                    nucScaleX = dataAttr.getDataset().getNucleus(dataAttr.getDim(0)).getFreqRatio();
                    if (dataAttr.nDim > 1) {
                        nucScaleY = dataAttr.getDataset().getNucleus(dataAttr.getDim(1)).getFreqRatio();
                    }
                }
                if (!chart.getDatasetAttributes().isEmpty()) {
                    bordersGrid[4][iCol] = Math.max(bordersGrid[4][iCol], Math.abs(ppmX0 - ppmX1)) * nucScaleX;
                    bordersGrid[5][iRow] = Math.max(bordersGrid[5][iRow], Math.abs(ppmY0 - ppmY1)) * nucScaleY;
                }
            } else {
                bordersGrid[4][iCol] = 100.0;
                bordersGrid[5][iRow] = 100.0;
            }
            iChild++;
        }
        iChild = 0;
        for (PolyChart chart : charts) {
            var gridPos = getGridPaneCanvas().getGridLocation(chart);
            int iRow = gridPos.rows();
            int iCol = gridPos.columns();
            double minLeftBorder = bordersGrid[0][iCol];
            double minBottomBorder = bordersGrid[2][iRow];
            chart.setMinBorders(minBottomBorder, minLeftBorder);

            iChild++;
        }

        return bordersGrid;
    }

    public List<PolyChart> getCharts() {
        return charts;
    }

    public Optional<PolyChart> getChart(double x, double y) {
        Optional<PolyChart> hitChart = Optional.empty();
        // go backwards so we find the last added chart if they overlap
        for (int i = charts.size() - 1; i >= 0; i--) {
            PolyChart chart = charts.get(i);
            if (chart.contains(x, y)) {
                hitChart = Optional.of(chart);
                break;
            }
        }
        return hitChart;
    }

    public void redrawChildren() {
        // fixme
    }

    public void addGrid() {
        GridPaneCanvas.GridDimensions gdims = getGridDimensionInput();
        if (gdims == null) {
            return;
        }
        addCharts(gdims.rows(), gdims.cols());
    }

    private void addCharts(int nRows, int nColumns) {
        setChartDisable(true);
        int nCharts = nRows * nColumns;
        setNCharts(nCharts);
        arrange(nRows);
        var chartActive = charts.get(0);
        setActiveChart(chartActive);
        setChartDisable(false);
        draw();
    }

    public void removeSelectedChart() {
        if (charts.size() > 1) {
            getActiveChart().close();
            arrange(chartDrawingLayers.getGrid().getOrientation());
        }
    }

    public void arrange(GridPaneCanvas.ORIENTATION orient) {
        setChartDisable(true);
        if (charts.size() == 1) {
            PolyChart chart = charts.get(0);
            double xLower = chart.getAxes().getX().getLowerBound();
            double xUpper = chart.getAxes().getX().getUpperBound();
            double yLower = chart.getAxes().getY().getLowerBound();
            double yUpper = chart.getAxes().getY().getUpperBound();
            List<DatasetAttributes> datasetAttrs = chart.getDatasetAttributes();
            if (datasetAttrs.size() > 1) {
                List<DatasetAttributes> current = new ArrayList<>(datasetAttrs);
                setNCharts(current.size());
                chart.getDatasetAttributes().clear();
                chartDrawingLayers.getGrid().setOrientation(orient, true);
                for (int i = 0; i < charts.size(); i++) {
                    DatasetAttributes datasetAttr = current.get(i);
                    PolyChart iChart = charts.get(i);
                    iChart.setDataset(datasetAttr.getDataset());
                    iChart.setDatasetAttr(datasetAttr);
                }
                PolyChartManager.getInstance().getSynchronizer().syncSceneMates(chart);
                setChartDisable(true);
                for (int i = 0; i < charts.size(); i++) {
                    PolyChart iChart = charts.get(i);
                    iChart.getAxes().getX().setLowerBound(xLower);
                    iChart.getAxes().getX().setUpperBound(xUpper);
                    iChart.getAxes().getY().setLowerBound(yLower);
                    iChart.getAxes().getY().setUpperBound(yUpper);
                    iChart.getCrossHairs().setAllStates(true);
                }
                setChartDisable(false);
                chartDrawingLayers.getGrid().layoutChildren();
                charts.forEach(PolyChart::refresh);
                return;
            }
        }
        chartDrawingLayers.getGrid().setOrientation(orient, true);
        setChartDisable(false);
        chartDrawingLayers.getGrid().layoutChildren();
    }

    public int arrangeGetRows() {
        return chartDrawingLayers.getGrid().getGridSize().rows();
    }

    public int arrangeGetColumns() {
        return chartDrawingLayers.getGrid().getGridSize().columns();
    }

    public void alignCenters() {
        Optional<DatasetAttributes> firstAttributes = activeChart.getFirstDatasetAttributes();
        if (firstAttributes.isEmpty()) {
            log.warn("No dataset attributes on active chart!");
            return;
        }

        DatasetAttributes activeAttr = firstAttributes.get();
        if (activeChart.getPeakListAttributes().isEmpty()) {
            alignCentersWithTempLists();
        } else {
            PeakList refList = activeChart.getPeakListAttributes().get(0).getPeakList();
            List<String> dimNames = new ArrayList<>();
            dimNames.add(activeAttr.getLabel(0));
            dimNames.add(activeAttr.getLabel(1));
            List<PeakList> movingLists = new ArrayList<>();
            for (PolyChart chart : charts) {
                if (chart != activeChart) {
                    PeakList movingList = chart.getPeakListAttributes().get(0).getPeakList();
                    movingLists.add(movingList);
                }
            }
            PeakListAlign.alignCenters(refList, dimNames, movingLists);
        }
    }

    private void alignCentersWithTempLists() {
        Optional<DatasetAttributes> firstAttributes = activeChart.getFirstDatasetAttributes();
        if (firstAttributes.isEmpty()) {
            log.warn("No dataset attributes on active chart!");
            return;
        }

        DatasetAttributes activeAttr = firstAttributes.get();
        // any peak lists created just for alignmnent should be deleted
        PeakPickParameters peakPickParameters = new PeakPickParameters();
        peakPickParameters.listName = "refList";
        PeakList refList = PeakPicking.peakPickActive(activeChart, activeAttr, null, peakPickParameters);
        if (refList == null) {
            return;
        }
        String dimName1 = activeAttr.getLabel(0);
        String dimName2 = activeAttr.getLabel(1);
        refList.unLinkPeaks();
        refList.clearSearchDims();
        refList.addSearchDim(dimName1, 0.05);
        refList.addSearchDim(dimName2, 0.1);
        int[] dims = activeAttr.dim.clone();

        for (int i = 2; i < dims.length; i++) {
            dims[i] = -1;
        }
        log.info(refList.getName());
        for (PolyChart chart : charts) {
            ObservableList<DatasetAttributes> dataAttrList = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrList) {
                if (dataAttr != activeAttr) {
                    PeakPickParameters peakPickParametersM = new PeakPickParameters();
                    peakPickParametersM.listName = "movingList";
                    PeakList movingList = PeakPicking.peakPickActive(chart, dataAttr, null, peakPickParametersM);
                    movingList.unLinkPeaks();
                    movingList.clearSearchDims();
                    movingList.addSearchDim(dimName1, 0.05);
                    movingList.addSearchDim(dimName2, 0.1);
                    log.info("act {} {}", dataAttr.getFileName(), movingList.size());

                    log.info("test {}", movingList.getName());
                    double[] centers = refList.centerAlign(movingList, dims);
                    for (double center : centers) {
                        log.info("{}", center);
                    }
                    double[] match;
                    String[] dimNames = {dimName1, dimName2};
                    double[] nOffset = {centers[0], centers[1]};
                    PeakNeighbors neighbor = new PeakNeighbors(refList, movingList, 25, dimNames);
                    neighbor.optimizeMatch(nOffset, 0.0, 1.0);
                    match = new double[3];
                    match[0] = nOffset[0];
                    match[1] = nOffset[1];
                    for (int i = 0, j = 0; i < dims.length; i++) {
                        if (dims[i] != -1) {
                            double ref = dataAttr.getDataset().getRefValue(dims[i]);
                            double delta = match[j++];
                            ref -= delta;
                            dataAttr.getDataset().setRefValue(dims[i], ref);
                            int pDim = movingList.getListDim(dataAttr.getLabel(i));
                            movingList.shiftPeak(pDim, -delta);
                        }
                    }
                    dataAttr.getDataset().writeParFile();
                    PeakList.remove("movingList");

                }
            }
            chart.refresh();
        }
        PeakList.remove("refList");
    }

    @Override
    public Collection<Property<?>> getPublicProperties() {
        return Set.of(minBorders, bgColor, axesColor);
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public void undo() {
        undoManager.undo();
        getActiveChart().refresh();
    }

    public void redo() {
        undoManager.redo();
        getActiveChart().refresh();
    }

    @PluginAPI("parametric")
    public VBox getBottomBox() {
        return bottomBox;
    }

    @PluginAPI("parametric")
    public void addTool(ControllerTool tool) {
        tools.add(tool);
    }

    @PluginAPI("parametric")
    public boolean containsTool(Class classType) {
        boolean result = false;
        for (ControllerTool tool : tools) {
            if (tool.getClass() == classType) {
                result = true;
                break;
            }
        }
        return result;
    }

    public ControllerTool getTool(Class classType) {
        ControllerTool result = null;
        for (ControllerTool tool : tools) {
            if (tool.getClass() == classType) {
                result = tool;
                break;
            }
        }
        return result;
    }

    @PluginAPI("parametric")
    public boolean removeTool(Class classType) {
        boolean result = false;
        for (ControllerTool tool : tools) {
            if (tool.getClass() == classType) {
                result = true;
                tools.remove(tool);
                if (bottomBox.getChildren().isEmpty()) {
                    splitPane.setDividerPosition(0, 1.0);
                }
                break;
            }
        }
        return result;
    }

    public void toggleCrossHairState(int index, Orientation orientation) {
        int orientationIndex = orientation.ordinal();
        crossHairStates[index][orientationIndex] = !crossHairStates[index][orientationIndex];
        boolean state = crossHairStates[index][orientationIndex];
        for (PolyChart chart : charts) {
            CrossHairs crossHairs = chart.getCrossHairs();
            crossHairs.setState(index, orientation, state);
        }
        statusBar.setIconState(index, orientation, state);
    }

    public void addSelectedPeakListener(ChangeListener listener) {
        selectedPeaks.addListener(listener);

    }

    protected void setPhaseDimChoice(int phaseDim) {
        phaser.setPhaseDim(phaseDim);
    }

    protected int[] getExtractRegion(ProcessingSection vecDimName, int size) {
        int start = 0;
        int end = size - 1;
        if (chartProcessor != null) {
            List<ProcessingOperationInterface> listItems = chartProcessor.getOperations(vecDimName);
            if (listItems != null) {
                Map<String, String> values = null;
                for (ProcessingOperationInterface processingOperation : listItems) {
                    if (processingOperation.getName().equals("EXTRACT")) {
                        values = PropertyManager.parseOpString(processingOperation.toString());
                    }
                }
                if (values != null) {
                    try {
                        if (values.containsKey("start")) {
                            String value = values.get("start");
                            start = Integer.parseInt(value);
                        }
                        if (values.containsKey("end")) {
                            String value = values.get("end");
                            end = Integer.parseInt(value);
                        }
                    } catch (NumberFormatException nfE) {
                        log.warn("Unable to parse region.", nfE);
                    }
                }
            }
        }
        return new int[]{start, end};
    }

    protected ArrayList<Double> getBaselineRegions(ProcessingSection section) {
        ArrayList<Double> fracs = new ArrayList<>();
        if (chartProcessor != null) {
            int currentIndex = chartProcessor.getProcessorController().getPropertyManager().getCurrentIndex();
            List<ProcessingOperationInterface> listItems = chartProcessor.getOperations(section);
            if (listItems != null) {
                log.info("curr ind {}", currentIndex);
                Map<String, String> values = null;
                if (currentIndex != -1) {
                    ProcessingOperationInterface processingOperation = listItems.get(currentIndex);
                    if (processingOperation.getName().equals("REGIONS")) {
                        values = PropertyManager.parseOpString(processingOperation.toString());
                        if (log.isInfoEnabled()) {
                            log.info(values.toString());
                        }
                    }
                }
                if (values == null) {
                    for (ProcessingOperationInterface processingOperation : listItems) {
                        if (processingOperation.getName().equals("REGIONS")) {
                            values = PropertyManager.parseOpString(processingOperation.toString());
                        }
                    }
                }
                if (values != null) {
                    if (values.containsKey("regions")) {
                        String value = values.get("regions").trim();
                        if (value.length() > 1) {
                            if (value.charAt(0) == '[') {
                                value = value.substring(1);
                            }
                            if (value.charAt(value.length() - 1) == ']') {
                                value = value.substring(0, value.length() - 1);
                            }
                        }
                        String[] fields = value.split(",");
                        try {
                            for (String field : fields) {
                                double frac = Double.parseDouble(field);
                                fracs.add(frac);
                            }

                        } catch (NumberFormatException nfE) {
                            log.warn("Error {}", value, nfE);
                        }
                    }
                }
            }
        }
        return fracs;
    }

    /**
     * Switches which NmrControlRightSideContent is displayed in the nmrControlRightSidePane.
     * @param attributesButton The attributes toggle button.
     * @param contentButton The content toggle button.
     * @param processorButton The processor toggle button.
     */
    private void toggleNmrControlRightSideContent(ToggleButton attributesButton, ToggleButton contentButton,
                                                  ToggleButton processorButton, ToggleButton toolButton) {
        if (attributesButton.isSelected()) {
            nmrControlRightSidePane.addContent(attributesController);
            attributesController.setAttributeControls();
            viewProcessorControllerIfPossible = false;
        } else if (contentButton.isSelected()) {
            nmrControlRightSidePane.addContent(contentController);
            contentController.update();
            viewProcessorControllerIfPossible = false;
        } else if (toolButton.isSelected()) {
            nmrControlRightSidePane.addContent(toolController);
            viewProcessorControllerIfPossible = false;
        } else if (processorButton.isSelected()) {
            boolean dataIsFID = false;
            if (chartProcessor != null) {
                var dataset = chartProcessor.getChart().getDataset();
                if ((dataset != null) && (dataset.getName().equals("vec0"))) {
                    dataIsFID = true;
                }
            }
            isFID = dataIsFID || (chartProcessor != null) && (chartProcessor.getNMRData() != null);
            nmrControlRightSidePane.addContent(getActiveChart().getProcessorController(true));
            updateSpectrumStatusBarOptions(false);
            viewProcessorControllerIfPossible = true;
        } else {
            nmrControlRightSidePane.clear();
            if (!processorButton.isDisabled() && getActiveChart().getProcessorController(false) != null) {
                viewProcessorControllerIfPossible = false;
            }
        }
        if (!processorButton.isSelected()) {
            isFID = false;
            updateSpectrumStatusBarOptions(false);
        }
    }

    public void updatePhaser(boolean showPhaser) {

        PolyChart chart = getActiveChart();
        if (showPhaser) {
            Cursor cursor = getCurrentCursor();
            if (cursor == null) {
                cursor = Cursor.MOVE;
            }
            phaser.sliceStatus(sliceStatusProperty().get());
            phaser.cursor(cursor);
            borderPane.setRight(phaserBox);
            phaser.getPhaseOp();
            if (chartProcessor == null) {
                phaser.setPH1Slider(activeChart.getDataPH1());
                phaser.setPH0Slider(activeChart.getDataPH0());
            }

            if (!chart.is1D()) {
                sliceStatusProperty().set(true);
                setCursor(Cursor.CROSSHAIR);
                chart.getSliceAttributes().setSlice1State(true);
                chart.getSliceAttributes().setSlice2State(false);
                chart.getCrossHairs().refresh();
            }
        } else {
            sliceStatusProperty().set(phaser.sliceStatus);
            setCursor(phaser.cursor());
            setCursor();
            chart.getCrossHairs().refresh();
            if (borderPane.getRight() == phaserBox) {
                borderPane.setRight(null);
            }
        }
    }

    private void saveAsFavorite() {
        WindowIO.saveFavorite();
    }

    /**
     * Listener for changes to the nMRControlRightSidePane children, if a pane is added or removed, the stage width is adjusted accordingly.
     *
     * @param c The change to nMRControlRightSidePane's children
     */
    private void updateStageSize(ListChangeListener.Change<? extends Node> c) {
        double paneAdj = 0;
        if (!nmrControlRightSidePane.hasContent()) {
            if (c.next()) {
                paneAdj = -1 * ((Pane) c.getRemoved().get(0)).getMinWidth();
            }
        } else if (nmrControlRightSidePane.size() == 1) {
            paneAdj = ((Pane) c.getList().get(0)).getMinWidth();
        }
        stage.setWidth(stage.getWidth() + paneAdj);
    }

    private void initToolBar(ToolBar toolBar) {
        ArrayList<Node> buttons = new ArrayList<>();
        ButtonBase bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FILE, "Datasets", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(this::showDatasetBrowser);
        buttons.add(bButton);
        favoriteButton.setOnAction(e -> saveAsFavorite());
        // Set the initial status of the favorite button
        enableFavoriteButton();
        buttons.add(favoriteButton);
        buttons.add(new Separator(Orientation.VERTICAL));

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REFRESH, "Refresh", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(e -> getActiveChart().refresh());
        buttons.add(bButton);
        buttons.add(haltButton);

        buttons.add(new Separator(Orientation.VERTICAL));
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.UNDO, "Undo", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(e -> undo());
        buttons.add(bButton);
        bButton.disableProperty().bind(undoManager.undoable.not());
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REPEAT, "Redo", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(e -> redo());
        buttons.add(bButton);
        bButton.disableProperty().bind(undoManager.redoable.not());

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.EXPAND, "Full", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(this::doFull);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH, "Expand", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(this::doExpand);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH_PLUS, "In", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doZoom(e, 1.2));
        bButton.setOnScroll((ScrollEvent event) -> {
            double y = event.getDeltaY();
            if (y < 0.0) {
                getActiveChart().zoom(1.1);
            } else {
                getActiveChart().zoom(0.9);

            }
        });
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH_MINUS, "Out", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doZoom(e, 0.8));
        bButton.setOnScroll((ScrollEvent event) -> {
            double y = event.getDeltaY();
            if (y < 0.0) {
                getActiveChart().zoom(1.1);
            } else {
                getActiveChart().zoom(0.9);

            }
        });
        buttons.add(bButton);

        buttons.add(new Separator(Orientation.VERTICAL));
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROWS_V, "Auto", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doScale(e, 0.0));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_UP, "Higher", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doScale(e, 0.8));
        bButton.setOnScroll((ScrollEvent event) -> {
            double y = event.getDeltaY();
            for (PolyChart applyChart : getCharts(event.isShiftDown())) {
                if (y < 0.0) {
                    applyChart.adjustScale(0.9);
                } else {
                    applyChart.adjustScale(1.1);
                }
            }
        });
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_DOWN, "Lower", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doScale(e, 1.2));

        bButton.setOnScroll((ScrollEvent event) -> {
            double y = event.getDeltaY();
            for (PolyChart applyChart : getCharts(event.isShiftDown())) {
                if (y < 0.0) {
                    applyChart.adjustScale(0.9);
                } else {
                    applyChart.adjustScale(1.1);
                }
            }
        });

        buttons.add(bButton);

        for (Node node : buttons) {
            if (node instanceof Button) {
                node.getStyleClass().add("toolButton");
            }
        }
        toolBar.getItems().addAll(buttons);
        // Make all buttons the same width to align the edges
        toolBar.widthProperty().addListener((observable, oldValue, newValue) -> GUIUtils.nodeAdjustWidths(buttons));
    }

    private void initStatusBar() {
        statusBar.init();
        btoolVBox.getChildren().addAll(statusBar.getToolbars());
        AnalystApp.getAnalystApp().addStatusBarTools(statusBar);
    }

    private List<PolyChart> getCharts(boolean all) {
        if (all) {
            return charts;
        } else {
            return Collections.singletonList(getActiveChart());
        }
    }

    private void doScale(MouseEvent e, double value) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            if (value == 0.0) {
                chart.autoScale();
            } else {
                chart.adjustScale(value);
            }
        }
    }

    private void doFull(MouseEvent e) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            chart.full();
        }
    }

    private void doExpand(MouseEvent e) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            chart.expand();
        }
    }

    private void doZoom(MouseEvent e, double value) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            chart.zoom(value);
        }
    }

    protected void refreshAttributes() {
        if (attributesController != null) {
            attributesController.setAttributeControls();
        }
    }

    /**
     * If the window is maximized, the current window widths are saved. If the window is restored down, the previously
     * saved values are used to set the window width.
     *
     * @param observable the maximize property
     * @param oldValue   previous value of maximize
     * @param newValue   new value of maximize
     */
    private void adjustSizeAfterMaximize(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if (Boolean.TRUE.equals(newValue)) {
            previousStageRestoreWidth = stage.getWidth();
            previousStageRestoreNmrControlRightSideContentVisible = nmrControlRightSidePane.hasContent();
            if (previousStageRestoreNmrControlRightSideContentVisible) {
                previousStageRestoreProcControllerWidth = nmrControlRightSidePane.getContentPane().getMinWidth();
            } else {
                previousStageRestoreProcControllerWidth = 0;
            }
        } else {
            boolean procControllerVisible = nmrControlRightSidePane.hasContent();
            if (procControllerVisible == previousStageRestoreNmrControlRightSideContentVisible) {
                stage.setWidth(previousStageRestoreWidth);
            } else if (procControllerVisible) {
                Pane p = nmrControlRightSidePane.getContentPane();
                stage.setWidth(previousStageRestoreWidth + p.getMinWidth());
            } else {
                stage.setWidth(previousStageRestoreWidth - previousStageRestoreProcControllerWidth);
            }
        }
    }

    public BooleanProperty sliceStatusProperty() {
        return sliceStatus;
    }

    public static File getInitialDirectory() {
        if (initialDir == null) {
            String homeDirName = System.getProperty("user.home");
            initialDir = new File(homeDirName);
        }
        return initialDir;
    }

    private void setInitialDirectory(File file) {
        initialDir = file;
    }

    public static void showPeakAttr() {
        if (peakAttrController == null) {
            peakAttrController = PeakAttrController.create();
        }
        if (peakAttrController != null) {
            peakAttrController.getStage().show();
            peakAttrController.getStage().toFront();
        } else {
            log.warn("Couldn't make controller");
        }
    }

    public static PeakAttrController getPeakAttrController() {
        return peakAttrController;
    }

    public static boolean isPeakAttrControllerShowing() {
        boolean state = false;
        if (peakAttrController != null) {
            if (peakAttrController.getStage().isShowing()) {
                state = true;
            }
        }
        return state;
    }

    public static String getHTMLDocs() {
        if (docString == null) {
            try (PythonInterpreter interpreter = new PythonInterpreter()) {
                interpreter.exec("from pyproc import *");
                interpreter.exec("from pydocs import *");
                PyObject pyDocObject = interpreter.eval("genAllDocs()");
                docString = (String) pyDocObject.__tojava__(String.class);
            }
        }
        return docString;
    }

    public void updateScannerTool(ToggleButton button) {
        if (button.isSelected()) {
            showScannerTool();
        } else {
            hideScannerTool();
        }
    }

    public void showScannerMenus() {
        showScannerTool();
        scannerTool.showMenus();
    }

    public void hideScannerMenus() {
        if (scannerTool != null) {
            scannerTool.hideMenus();
        }
    }

    public boolean isScannerToolPresent() {
        return (scannerTool != null) && scannerTool.scannerActive();
    }

    public void showScannerTool() {
        BorderPane vBox;
        if (scannerTool != null) {
            vBox = scannerTool.getBox();
        } else {
            vBox = new BorderPane();
            scannerTool = new ScannerTool(this);
            scannerTool.initialize(vBox);
        }
        if (!getBottomBox().getChildren().contains(vBox)) {
            splitPane.setDividerPosition(0, scannerTool.getSplitPanePosition());
            getBottomBox().getChildren().add(vBox);
            addTool(scannerTool);
        }
    }

    public void hideScannerTool() {
        if (scannerTool != null) {
            removeScannerTool();
        }
    }


    public void removeScannerTool() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        double[] dividerPositions = controller.splitPane.getDividerPositions();
        controller.removeTool(ScannerTool.class);
        if (scannerTool != null) {
            scannerTool.setSplitPanePosition(dividerPositions[0]);
            removeBottomBoxNode(scannerTool.getBox());
        }
    }

    public Optional<RunAboutGUI>  showRunAboutTool() {
        RunAboutGUI runAboutGUI;
        if (!containsTool(RunAboutGUI.class)) {
            TabPane tabPane = new TabPane();
            getBottomBox().getChildren().add(tabPane);
            tabPane.setMinHeight(200);
            runAboutGUI = new RunAboutGUI(this, this::removeRunaboutTool);
            runAboutGUI.initialize(tabPane);
            addTool(runAboutGUI);
            return Optional.of(runAboutGUI);
        } else {
            return getRunAboutTool();
        }
    }

    public Optional<RunAboutGUI> getRunAboutTool() {
        ControllerTool tool = getTool(RunAboutGUI.class);
        if (tool instanceof RunAboutGUI runAboutGUI) {
            return Optional.of(runAboutGUI);
        } else {
            return Optional.empty();
        }
    }

    public void removeRunaboutTool(RunAboutGUI runaboutTool) {
        removeTool(RunAboutGUI.class);
        removeBottomBoxNode(runaboutTool.getTabPane());
    }

    public StripController showStripsBar() {
        if (!containsTool(StripController.class)) {
            VBox vBox = new VBox();
            getBottomBox().getChildren().add(vBox);
            StripController stripsController = new StripController(this, this::removeStripsBar);
            stripsController.initialize(vBox);
            addTool(stripsController);
        }
        return (StripController) getTool(StripController.class);
    }

    public void removeStripsBar(StripController stripsController) {
        removeTool(StripController.class);
        removeBottomBoxNode(stripsController.getBox());
    }

    public void removeBottomBoxNode(Node node) {
        getBottomBox().getChildren().remove(node);
        if (getBottomBox().getChildren().isEmpty()) {
            splitPane.setDividerPosition(0, 1.0);
        }
    }

    public void setSplitPaneDivider(double f) {
        splitPane.setDividerPosition(0, f);
    }

    public double getSplitPaneDivider() {
        return splitPane.getDividerPositions()[0];
    }

}
