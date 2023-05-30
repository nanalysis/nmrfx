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
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.annotations.PluginAPI;
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
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.jcamp.JCAMPData;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.CanvasBindings;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayTool;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.processor.gui.tools.SpectrumComparator;
import org.nmrfx.processor.gui.undo.UndoManager;
import org.nmrfx.processor.gui.utils.FileExtensionFilterType;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utilities.DictionarySort;
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
import java.util.*;

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
    private final Canvas canvas = new Canvas();
    private final Canvas peakCanvas = new Canvas();
    private final Canvas annoCanvas = new Canvas();
    private final Pane plotContent = new Pane();
    private final boolean[][] crossHairStates = new boolean[2][2];
    private final Set<ControllerTool> tools = new HashSet<>();
    private final SimpleObjectProperty<Cursor> cursorProperty = new SimpleObjectProperty<>(CanvasCursor.SELECTOR.getCursor());
    private final ObservableList<PolyChart> charts = FXCollections.observableArrayList();
    private final BooleanProperty sliceStatus = new SimpleBooleanProperty(false);
    private final UndoManager undoManager = new UndoManager();
    private final SimpleBooleanProperty processControllerVisible = new SimpleBooleanProperty(false);
    private ChartProcessor chartProcessor;
    private Stage stage = null;
    private boolean isFID = true;
    private PopOver popOver = null;
    private SpectrumStatusBar statusBar;
    private SpectrumMeasureBar measureBar = null;
    private PeakNavigator peakNavigator;
    private SpectrumComparator spectrumComparator;
    private double widthScale = 10.0;
    private Phaser phaser;
    private Pane attributesPane;
    private Pane contentPane;
    private AttributesController attributesController;
    private ContentController contentController;
    private AnalyzerBar analyzerBar = null;
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
    private StackPane processorPane;
    private Button cancelButton;
    private Button favoriteButton;
    private double previousStageRestoreWidth = 0;
    private double previousStageRestoreProcControllerWidth = 0;
    private boolean previousStageRestoreProcControllerVisible = false;
    private PolyChart activeChart = null;
    private GridPaneCanvas chartGroup;
    private final BooleanProperty minBorders = new SimpleBooleanProperty(this, "minBorders", false);
    private final ColorProperty bgColor = new ColorProperty(this, "bgColor", null);
    private final ColorProperty axesColor = new ColorProperty(this, "axesColor", null);

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

    public void deselectCharts() {
        for (var chart : charts) {
            chart.selectChart(false);
        }
    }

    /**
     * Updates the SpectrumStatusBar menu options based on whether FID mode is on, and the dimensions
     * of the dataset.
     */
    public void updateSpectrumStatusBarOptions(boolean initDataset) {
        if (isFIDActive()) {
            statusBar.setMode(0);
        } else {
            ObservableList<DatasetAttributes> datasetAttrList = getActiveChart().getDatasetAttributes();
            OptionalInt maxNDim = datasetAttrList.stream().mapToInt(d -> d.nDim).max();
            if (maxNDim.isPresent()) {
                if (getActiveChart().is1D() && (maxNDim.getAsInt() > 1)) {
                    OptionalInt maxRows = datasetAttrList.stream().
                            mapToInt(d -> d.nDim == 1 ? 1 : d.getDataset().getSizeReal(1)).max();
                    if (initDataset && maxRows.isPresent() && (maxRows.getAsInt() > MAX_INITIAL_TRACES)) {
                        getActiveChart().setDrawlist(0);
                    }
                    statusBar.set1DArray(maxNDim.getAsInt(), maxRows.getAsInt());
                } else {
                    statusBar.setMode(maxNDim.getAsInt());
                }
            }
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

        deselectCharts();
        isFID = false;
        activeChart = chart;
        PolyChartManager.getInstance().setActiveChart(chart);
        ProcessorController processorController = chart.getProcessorController(false);
        processorPane.getChildren().clear();
        // The chart has a processor controller setup, and can be in FID or Dataset mode.
        if (processorController != null) {
            isFID = !processorController.isViewingDataset();
            chartProcessor = processorController.chartProcessor;
            if (processorController.isVisible()) {
                processorController.show();
            }
        }
        updateSpectrumStatusBarOptions(false);
        if (attributesController != null) {
            attributesController.setChart(activeChart);
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
        return borderPane.getRight() == phaserBox;
    }

    public boolean isContentPaneShowing() {
        return borderPane.getRight() == contentPane;
    }

    public Stage getStage() {
        return stage;
    }

    private void showDatasetsAction(ActionEvent event) {
        if (popOver == null) {
            popOver = new PopOver();
        }
        if (Dataset.datasets().isEmpty()) {
            Label label = new Label("No open datasets\nUse File Menu Open item\nto open datasets");
            label.setStyle("-fx-font-size:12pt;-fx-text-alignment: center; -fx-padding:10px;");
            popOver.setContentNode(label);
        } else {
            datasetListView.setStyle("-fx-font-size:12pt;");

            DictionarySort<DatasetBase> sorter = new DictionarySort<>();
            datasetListView.getItems().clear();
            Dataset.datasets().stream().sorted(sorter).forEach((DatasetBase d) -> {
                datasetListView.getItems().add(d.getName());
            });
            datasetListView.setCellFactory(new Callback<>() {
                @Override
                public ListCell<String> call(ListView<String> p) {
                    ListCell<String> olc = new ListCell<>() {
                        @Override
                        public void updateItem(String s, boolean empty) {
                            super.updateItem(s, empty);
                            setText(s);
                        }
                    };
                    olc.setOnDragDetected(event -> {
                        Dragboard db = olc.startDragAndDrop(TransferMode.COPY);

                        /* Put a string on a dragboard */
                        ClipboardContent content = new ClipboardContent();
                        List<String> items = olc.getListView().getSelectionModel().getSelectedItems();
                        StringBuilder sBuilder = new StringBuilder();
                        for (String item : items) {
                            sBuilder.append(item);
                            sBuilder.append("\n");
                        }
                        content.putString(sBuilder.toString().trim());
                        db.setContent(content);

                        event.consume();
                    });
                    return olc;
                }

            });
            datasetListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            popOver.setContentNode(datasetListView);
        }

        popOver.setDetachable(true);
        popOver.setTitle("Datasets");
        popOver.setHeaderAlwaysVisible(true);
        popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        popOver.show((Node) event.getSource());
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

    /**
     * Gets a NMRData object from the filepath.
     *
     * @param filePath The filepath to load the NMRData from.
     * @return An NMRData object or null if there was a problem loading.
     */
    private NMRData getNMRData(String filePath) {
        NMRData nmrData = null;
        try {
            nmrData = NMRDataUtil.loadNMRData(filePath, null);
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
            if (processorController != null && (!dataset.getFile().equals(chartProcessor.datasetFile))) {
                processorPane.getChildren().clear();
                getActiveChart().processorController = null;
                processorController.cleanUp();
            }
            if (addDatasetToChart) {
                addDataset(dataset, append, false);
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
            processorController.setAutoProcess(false);
            chartProcessor.setData(nmrData, clearOps);
            processorController.viewingDataset(false);
            processorController.updateFileButton();
            processorController.show();
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
            statusBar.setMode(0);
        } else {
            log.warn("Unable to add FID because controller can not be created.");
        }
    }

    public void addDataset(DatasetBase dataset, boolean appendFile, boolean reload) {
        isFID = false;
        if (dataset.getFile() != null) {
            PreferencesController.saveRecentFiles(dataset.getFile().toString());
        }

        DatasetAttributes datasetAttributes = getActiveChart().setDataset(dataset, appendFile, false);
        PolyChart polyChart = getActiveChart();
        polyChart.getCrossHairs().setStates(true, true, true, true);
        getActiveChart().clearAnnotations();
        getActiveChart().clearPopoverTools();
        getActiveChart().removeProjections();
        ProcessorController processorController = getActiveChart().processorController;
        if (processorController != null) {
            processorController.viewingDataset(true);
        }
        borderPane.setLeft(null);
        borderPane.setBottom(null);
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
            if (nDim == 1 || nDim == 2) {
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
                    NMRData nmrData = NMRDataUtil.getFID(selectedFile.toString());
                    if (nmrData instanceof NMRViewData) {
                        PreferencesController.saveRecentFiles(selectedFile.toString());
                    }
                }
            } catch (IllegalArgumentException | IOException iaE) {
                ExceptionDialog eDialog = new ExceptionDialog(iaE);
                eDialog.showAndWait();
            }
        }
    }

    public StackPane getProcessorPane() {
        return processorPane;
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

    public void showProcessorAction(ActionEvent event) {
        ProcessorController processorController = getActiveChart().getProcessorController(true);
        processorController.show();
    }

    public Button getCancelButton() {
        return cancelButton;
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
                plotContent.setVisible(false);
                GUIUtils.snapNode(chartPane, selectedFile);
            } catch (IOException ex) {
                GUIUtils.warn("Error saving png file", ex.getLocalizedMessage());
            } finally {
                plotContent.setVisible(true);
            }
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
                pdfGC.create(true, canvas.getWidth(), canvas.getHeight(), fileName);
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
                svgGC.create(canvas.getWidth(), canvas.getHeight(), fileName);
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
            svgGC.create(canvas.getWidth(), canvas.getHeight(), stream);
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
        if (isSideBarAttributesShowing()) {
            attributesController.setChart(getActiveChart());
        }
    }

    public boolean isSideBarAttributesShowing() {
        return (attributesPane != null) && (borderPane.getRight() == attributesPane);
    }

    public void updateDatasetAttributeControls() {
        if (isSideBarAttributesShowing()) {
            attributesController.updateDatasetAttributeControls();
        }
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
        borderPane.setLeft(null);
        if (!AnalystApp.isMac()) {
            MenuBar menuBar = AnalystApp.getMenuBar();
            topBar.getChildren().add(0, menuBar);
        }
        plotContent.setMouseTransparent(true);
        activeChart = PolyChartManager.getInstance().create(this, plotContent, canvas, peakCanvas, annoCanvas);
        new CanvasBindings(this, canvas).setHandlers();
        initToolBar(toolBar);
        charts.add(activeChart);
        activeChart.setController(this);

        chartGroup = new GridPaneCanvas(this, canvas);
        chartGroup.addCharts(1, charts);
        chartGroup.setMouseTransparent(true);
        chartPane.getChildren().addAll(canvas, chartGroup, peakCanvas, annoCanvas, plotContent);
        chartGroup.setManaged(true);
        canvas.setManaged(false);
        peakCanvas.setManaged(false);
        annoCanvas.setManaged(false);
        plotContent.setManaged(false);
        mainBox.layoutBoundsProperty().addListener((ObservableValue<? extends Bounds> arg0, Bounds arg1, Bounds arg2) -> {
            if (arg2.getWidth() < 1.0 || arg2.getHeight() < 1.0) {
                return;
            }
            chartGroup.requestLayout();
        });

        statusBar.setMode(1);
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                crossHairStates[iCross][jOrient] = true;
            }
        }
        phaser = new Phaser(this, phaserBox);
        processorPane.getChildren().addListener(this::updateStageSize);
        cursorProperty.addListener(e -> setCursor());
        attributesPane = new AnchorPane();
        attributesController = AttributesController.create(this, attributesPane);
        borderPane.heightProperty().addListener(e -> attributesController.updateScrollSize(borderPane));

        contentPane = new AnchorPane();
        contentController = ContentController.create(this, contentPane);
        borderPane.heightProperty().addListener(e -> contentController.updateScrollSize(borderPane));
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

    public BorderPane getMainBox() {
        return mainBox;
    }

    public Cursor getCurrentCursor() {
        return canvas.getCursor();
    }

    public void setCurrentCursor(Cursor cursor) {
        canvas.setCursor(cursor);
    }

    public void setCursor() {
        Cursor cursor = cursorProperty.getValue();
        canvas.setCursor(cursor);
        for (PolyChart chart : charts) {
            chart.getCrossHairs().setAllStates(CanvasCursor.isCrosshair(cursor));
        }
        statusBar.updateCursorBox();
    }

    public void resizeCanvases(double width, double height) {
        canvas.setWidth(width);
        canvas.setHeight(height);
        peakCanvas.setWidth(width);
        peakCanvas.setHeight(height);
        annoCanvas.setWidth(width);
        annoCanvas.setHeight(height);
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
            analyzerBar = null;
        }
    }

    public void linkPeakDims() {
        PeakLinker linker = new PeakLinker();
        linker.linkAllPeakListsByLabel();
    }

    public void removeChart(PolyChart chart) {
        if (chart != null) {
            chartGroup.getChildren().remove(chart);
            charts.remove(chart);
            if (chart == activeChart) {
                if (charts.isEmpty()) {
                    activeChart = null;
                } else {
                    activeChart = charts.get(0);
                }
            }
            chartGroup.requestLayout();
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
        chartGroup.addCharts(chartGroup.getRows(), charts);
    }

    public void arrange(int nRows) {
        chartGroup.setRows(nRows);
        chartGroup.calculateAndSetOrientation();
    }

    public void draw() {
        chartGroup.layoutChildren();
    }

    public void addChart() {
        PolyChart chart = PolyChartManager.getInstance().create(this, plotContent, canvas, peakCanvas, annoCanvas);
        charts.add(chart);
        chart.setChartDisabled(true);
        chartGroup.addChart(chart);
        activeChart = chart;
    }

    public void setBorderState(boolean state) {
        minBorders.set(state);
        chartGroup.updateConstraints();
        chartGroup.layoutChildren();
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
            int iRow = iChild / nCols;
            int iCol = iChild % nCols;
            if (minBorders.get()) {
                chart.setAxisState(iCol == 0, iRow == (nRows - 1));
            } else {
                chart.setAxisState(true, true);
            }
            double[] borders = chart.getMinBorders();
            bordersGrid[0][iCol] = Math.max(bordersGrid[0][iCol], borders[0]);
            bordersGrid[1][iCol] = Math.max(bordersGrid[1][iCol], borders[1]);
            bordersGrid[2][iRow] = Math.max(bordersGrid[2][iRow], borders[2]);
            bordersGrid[3][iRow] = Math.max(bordersGrid[3][iRow], borders[3]);
            maxBorderX = Math.max(maxBorderX, borders[0]);
            maxBorderY = Math.max(maxBorderY, borders[2]);

            double ppmX0 = chart.getXAxis().getLowerBound();
            double ppmX1 = chart.getXAxis().getUpperBound();
            double ppmY0 = chart.getYAxis().getLowerBound();
            double ppmY1 = chart.getYAxis().getUpperBound();
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
            int iRow = iChild / nCols;
            int iCol = iChild % nCols;
            chart.minLeftBorder = bordersGrid[0][iCol];
            chart.minBottomBorder = bordersGrid[2][iRow];
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
            arrange(chartGroup.getOrientation());
        }
    }

    public void arrange(GridPaneCanvas.ORIENTATION orient) {
        setChartDisable(true);
        if (charts.size() == 1) {
            PolyChart chart = charts.get(0);
            double xLower = chart.getXAxis().getLowerBound();
            double xUpper = chart.getXAxis().getUpperBound();
            double yLower = chart.getYAxis().getLowerBound();
            double yUpper = chart.getYAxis().getUpperBound();
            List<DatasetAttributes> datasetAttrs = chart.getDatasetAttributes();
            if (datasetAttrs.size() > 1) {
                List<DatasetAttributes> current = new ArrayList<>(datasetAttrs);
                setNCharts(current.size());
                chart.getDatasetAttributes().clear();
                chartGroup.setOrientation(orient, true);
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
                    iChart.getXAxis().setLowerBound(xLower);
                    iChart.getXAxis().setUpperBound(xUpper);
                    iChart.getYAxis().setLowerBound(yLower);
                    iChart.getYAxis().setUpperBound(yUpper);
                    iChart.getCrossHairs().setAllStates(true);
                }
                setChartDisable(false);
                chartGroup.layoutChildren();
                charts.forEach(PolyChart::refresh);
                return;
            }
        }
        chartGroup.setOrientation(orient, true);
        setChartDisable(false);
        chartGroup.layoutChildren();
    }

    public int arrangeGetRows() {
        return chartGroup.getRows();
    }

    public int arrangeGetColumns() {
        return chartGroup.getColumns();
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
        PeakList refList = PeakPicking.peakPickActive(activeChart, activeAttr, false, false, null, false, "refList");
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
                    PeakList movingList = PeakPicking.peakPickActive(chart, dataAttr, false, false, null, false, "movingList");
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
    }

    public void redo() {
        undoManager.redo();
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

    /**
     * Checks if the active chart has a processorController instances or if the chart is empty.
     *
     * @return True if active chart has ProcessorController else returns false.
     */
    public boolean isProcessorControllerAvailable() {
        return getActiveChart().getProcessorController(false) != null || getActiveChart().getDataset() == null;
    }

    protected void setPhaseDimChoice(int phaseDim) {
        phaser.setPhaseDim(phaseDim);
    }

    protected int[] getExtractRegion(String vecDimName, int size) {
        int start = 0;
        int end = size - 1;
        if (chartProcessor != null) {
            List<String> listItems = chartProcessor.getOperations(vecDimName);
            if (listItems != null) {
                Map<String, String> values = null;
                for (String s : listItems) {
                    if (s.contains("EXTRACT")) {
                        values = PropertyManager.parseOpString(s);
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

    protected ArrayList<Double> getBaselineRegions(String vecDimName) {
        ArrayList<Double> fracs = new ArrayList<>();
        if (chartProcessor != null) {
            int currentIndex = chartProcessor.getProcessorController().getPropertyManager().getCurrentIndex();
            List<String> listItems = chartProcessor.getOperations(vecDimName);
            if (listItems != null) {
                log.info("curr ind {}", currentIndex);
                Map<String, String> values = null;
                if (currentIndex != -1) {
                    String s = listItems.get(currentIndex);
                    log.info(s);
                    if (s.contains("REGIONS")) {
                        values = PropertyManager.parseOpString(s);
                        if (log.isInfoEnabled()) {
                            log.info(values.toString());
                        }
                    }
                }
                if (values == null) {
                    for (String s : listItems) {
                        if (s.contains("REGIONS")) {
                            values = PropertyManager.parseOpString(s);
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

    private void toggleSideBarAttributes(ToggleButton phaserButton, ToggleButton attributesButton, ToggleButton contentButton) {
        if (phaserButton.isSelected()) {
            borderPane.setRight(phaserBox);
            phaser.getPhaseOp();
            if (chartProcessor == null) {
                phaser.setPH1Slider(activeChart.getDataPH1());
                phaser.setPH0Slider(activeChart.getDataPH0());
            }
        } else if (attributesButton.isSelected()) {
            borderPane.setRight(attributesPane);
            attributesController.setAttributeControls();
            attributesController.updateScrollSize(borderPane);
        } else if (contentButton.isSelected()) {
            borderPane.setRight(contentPane);
            contentController.update();
            contentController.updateScrollSize(borderPane);
        } else {
            borderPane.setRight(null);
        }
    }

    private void saveAsFavorite() {
        WindowIO.saveFavorite();
    }

    /**
     * Listener for changes to the processorPane's children, if a pane is added or removed, the stage width is adjusted accordingly.
     *
     * @param c The change to processorPane's children
     */
    private void updateStageSize(ListChangeListener.Change<? extends Node> c) {
        double paneAdj = 0;
        if (processorPane.getChildren().isEmpty()) {
            if (c.next()) {
                paneAdj = -1 * ((Pane) c.getRemoved().get(0)).getMinWidth();
            }
        } else if (processorPane.getChildren().size() == 1) {
            paneAdj = ((Pane) c.getList().get(0)).getMinWidth();
        }
        stage.setWidth(stage.getWidth() + paneAdj);
    }

    private void initToolBar(ToolBar toolBar) {
        ArrayList<Node> buttons = new ArrayList<>();

        ButtonBase bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FILE, "Datasets", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(this::showDatasetsAction);
        buttons.add(bButton);
        favoriteButton = GlyphsDude.createIconButton(FontAwesomeIcon.HEART, "Favorite", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        favoriteButton.setOnAction(e -> saveAsFavorite());
        // Set the initial status of the favorite button
        enableFavoriteButton();
        buttons.add(favoriteButton);
        buttons.add(new Separator(Orientation.VERTICAL));

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REFRESH, "Refresh", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(e -> getActiveChart().refresh());
        buttons.add(bButton);
        cancelButton = GlyphsDude.createIconButton(FontAwesomeIcon.STOP, "Halt", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        buttons.add(cancelButton);

        buttons.add(new Separator(Orientation.VERTICAL));
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.UNDO, "Undo", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(e -> undoManager.undo());
        buttons.add(bButton);
        bButton.disableProperty().bind(undoManager.undoable.not());
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REPEAT, "Redo", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(e -> undoManager.redo());
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
        buttons.add(new Separator(Orientation.VERTICAL));
        buttons.add(new Separator(Orientation.VERTICAL));

        Pane filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        filler.setMinWidth(20);
        buttons.add(filler);

        ToggleButton phaserButton = new ToggleButton("Phasing");
        ToggleButton attributesButton = new ToggleButton("Attributes");
        ToggleButton contentButton = new ToggleButton("Content");
        attributesButton.setOnAction(e -> toggleSideBarAttributes(phaserButton, attributesButton, contentButton));
        contentButton.setOnAction(e -> toggleSideBarAttributes(phaserButton, attributesButton, contentButton));
        phaserButton.setOnAction(e -> toggleSideBarAttributes(phaserButton, attributesButton, contentButton));
        phaserButton.getStyleClass().add("toolButton");
        attributesButton.getStyleClass().add("toolButton");
        contentButton.getStyleClass().add("toolButton");
        SegmentedButton groupButton = new SegmentedButton(phaserButton, contentButton, attributesButton);


        for (Node node : buttons) {
            if (node instanceof Button) {
                node.getStyleClass().add("toolButton");
            }
        }
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(groupButton);

        statusBar = new SpectrumStatusBar(this);
        ToolBar btoolBar = new ToolBar();
        ToolBar btoolBar2 = new ToolBar();
        btoolVBox.getChildren().addAll(btoolBar, btoolBar2);
        statusBar.buildBar(btoolBar, btoolBar2);
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
            ProcessorController pc = getActiveChart().getProcessorController(false);
            if (pc != null) {
                previousStageRestoreProcControllerVisible = pc.isVisible();
            } else {
                previousStageRestoreProcControllerVisible = false;
            }
            if (previousStageRestoreProcControllerVisible) {
                previousStageRestoreProcControllerWidth = ((Pane) processorPane.getChildren().get(0)).getMinWidth();
            } else {
                previousStageRestoreProcControllerWidth = 0;
            }
        } else {
            boolean procControllerVisible = !processorPane.getChildren().isEmpty();
            if (procControllerVisible == previousStageRestoreProcControllerVisible) {
                stage.setWidth(previousStageRestoreWidth);
            } else if (procControllerVisible) {
                Pane p = (Pane) processorPane.getChildren().get(0);
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
}
