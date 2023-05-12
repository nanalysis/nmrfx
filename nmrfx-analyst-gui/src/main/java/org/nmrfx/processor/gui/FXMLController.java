/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.apache.commons.beanutils.PropertyUtils;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.datasets.DatasetBase;
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
import org.nmrfx.processor.gui.spectra.*;
import org.nmrfx.processor.gui.tools.SpectrumComparator;
import org.nmrfx.processor.gui.undo.UndoManager;
import org.nmrfx.processor.gui.utils.FileExtensionFilterType;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utilities.DictionarySort;
import org.nmrfx.utils.GUIUtils;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.nmrfx.processor.gui.controls.GridPaneCanvas.getGridDimensionInput;

@PluginAPI("parametric")
public class FXMLController implements  Initializable, PeakNavigable {
    private static final Logger log = LoggerFactory.getLogger(FXMLController.class);
    private static final int PSEUDO_2D_SIZE_THRESHOLD = 100;
    public static final int MAX_INITIAL_TRACES = 32;

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
    PopOver popOver = null;
    private VBox phaserBox = new VBox();

    ChartProcessor chartProcessor;
    static PeakAttrController peakAttrController = null;
    Stage stage = null;
    private double previousStageRestoreWidth = 0;
    private double previousStageRestoreProcControllerWidth = 0;
    private boolean previousStageRestoreProcControllerVisible = false;
    boolean isFID = true;
    static public final SimpleObjectProperty<FXMLController> activeController = new SimpleObjectProperty<>(null);
    static String docString = null;
    static List<FXMLController> controllers = new ArrayList<>();

    ObservableList<PolyChart> charts = FXCollections.observableArrayList();
    private PolyChart activeChart = null;
    SpectrumStatusBar statusBar;
    SpectrumMeasureBar measureBar = null;
    BooleanProperty sliceStatus = new SimpleBooleanProperty(false);
    static File initialDir = null;

    CanvasBindings canvasBindings;

    private GridPaneCanvas chartGroup;

    PeakNavigator peakNavigator;
    SpectrumComparator spectrumComparator;
    ListView datasetListView = new ListView();

    public final SimpleObjectProperty<List<Peak>> selPeaks = new SimpleObjectProperty<>();
    UndoManager undoManager = new UndoManager();
    double widthScale = 10.0;
    Canvas canvas = new Canvas();
    Canvas peakCanvas = new Canvas();
    Canvas annoCanvas = new Canvas();
    Pane plotContent = new Pane();
    boolean[][] crossHairStates = new boolean[2][2];
    private BooleanProperty minBorders;
    Phaser phaser;
    Pane attributesPane;
    Pane contentPane;
    AttributesController attributesController;
    ContentController contentController;
    Set<ControllerTool> tools = new HashSet<>();
    SimpleBooleanProperty processControllerVisible = new SimpleBooleanProperty(false);
    SimpleObjectProperty<Cursor> cursorProperty = new SimpleObjectProperty<>(CanvasCursor.SELECTOR.getCursor());


    private BooleanProperty minBordersProperty() {
        if (minBorders == null) {
            minBorders = new SimpleBooleanProperty(this, "minBorders", false);
        }
        return minBorders;
    }

    public void setMinBorders(boolean value) {
        minBordersProperty().set(value);
    }

    public boolean getMinBorders() {
        return minBordersProperty().get();
    }

    private ColorProperty bgColor;

    public ColorProperty bgColorProperty() {
        if (bgColor == null) {
            bgColor = new ColorProperty(this, "bgColor", null);
        }
        return bgColor;
    }

    public Color getBgColor() {
        return bgColorProperty().get();
    }

    private ColorProperty axesColor;

    public ColorProperty axesColorProperty() {
        if (axesColor == null) {
            axesColor = new ColorProperty(this, "axesColor", null);
        }
        return axesColor;
    }

    public Color getAxesColor() {
        return axesColorProperty().get();
    }

    public static File getInitialDirectory() {
        if (initialDir == null) {
            String homeDirName = System.getProperty("user.home");
            initialDir = new File(homeDirName);
        }
        return initialDir;
    }

    public void setInitialDirectory(File file) {
        initialDir = file;
    }

    public Cursor getCursor() {
        return cursorProperty.get();
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


    public void close() {
        // need to make copy of charts as the call to chart.close will remove the chart from charts
        // resulting in a java.util.ConcurrentModificationException
        saveDatasets();
        List<PolyChart> tempCharts = new ArrayList<>();
        tempCharts.addAll(charts);
        for (PolyChart chart : tempCharts) {
            chart.close();
        }
        controllers.remove(this);
        PolyChart activeChart = PolyChart.getActiveChart();
        if (activeChart == null) {
            if (!PolyChart.CHARTS.isEmpty()) {
                activeChart = PolyChart.CHARTS.get(0);
            }
        }
        if (activeChart != null) {
            activeController.set(activeChart.getController());
            activeController.get().setActiveChart(activeChart);
        } else {
            activeController.set(null);
        }
    }

    public void processorCreated(Pane pane) {
        processControllerVisible.bind(pane.parentProperty().isNotNull());
        isFID = !getActiveChart().getProcessorController(true).isViewingDataset();
        updateSpectrumStatusBarOptions(true);
    }

    public boolean isPhaseSliderVisible() {
        return borderPane.getRight() ==phaserBox;
    }

    public boolean isSideBarAttributesShowing() {
        return (attributesPane != null) && (borderPane.getRight() ==attributesPane);
    }

    public boolean isContentPaneShowing() {
        return borderPane.getRight() ==contentPane;
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

    public static List<FXMLController> getControllers() {
        return controllers;
    }

    public void setActiveChart(PolyChart chart) {
        if (activeChart == chart) {
            return;
        }

        deselectCharts();
        isFID = false;
        activeChart = chart;
        PolyChart.activeChart.set(chart);
        ProcessorController processorController = chart.getProcessorController(false);
        processorPane.getChildren().clear();
        // The chart has a processor controller setup, and can be in FID or Dataset mode.
        if (processorController != null) {
            isFID = !processorController.isViewingDataset();
            chartProcessor = processorController.chartProcessor;
            if(processorController.isVisible()) {
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

    public PolyChart getActiveChart() {
        return activeChart;
    }

    public void deselectCharts() {
        for (var chart:charts) {
            chart.selectChart(false);
        }
    }

    public Stage getStage() {
        return stage;
    }

    @FXML
    private void autoScaleAction(ActionEvent event) {
        charts.stream().forEach(chart -> {
            chart.autoScale();
            chart.layoutPlotChildren();
        });
    }

    @FXML
    private void fullAction(ActionEvent event) {
        charts.stream().forEach(chart -> {
            chart.full();
            chart.layoutPlotChildren();
        });
    }

    @FXML
    public void showDatasetsAction(ActionEvent event) {
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
            datasetListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
                @Override
                public ListCell<String> call(ListView<String> p) {
                    ListCell<String> olc = new ListCell<>() {
                        @Override
                        public void updateItem(String s, boolean empty) {
                            super.updateItem(s, empty);
                            setText(s);
                        }
                    };
                    olc.setOnDragDetected(new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
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
                        }
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

    @FXML
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

    /**
     * Opens the dataset from the selected file.
     * @param selectedFile The file containing the dataset.
     * @param append Whether to append the new dataset.
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

    /**
     * Gets the dataset from the provided NMRData. The dataset is added to the chart if addDatasetToChart is true. If
     * append is false, the chart is cleared of any datasets before adding the new dataset, if true, the dataset is
     * added to the chart in addition to any datasets already present.
     * @param nmrData The NMRData object containing the dataset.
     * @param append Whether to append the new dataset.
     * @param addDatasetToChart Whether to add the opened dataset to the chart.
     * @return The newly opened dataset or null if no dataset was opened.
     */
    public Dataset openDataset(NMRData nmrData, boolean append, boolean addDatasetToChart) {
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
            } else if (nmrData instanceof  RS2DData rs2dData) {
                PreferencesController.saveRecentFiles(selectedFile.toString());
                String suggestedName = rs2dData.suggestName(new File(rs2dData.getFilePath()));
                dataset = rs2dData.toDataset(suggestedName);
            } else if (nmrData instanceof JCAMPData jcampData) {
                PreferencesController.saveRecentFiles(selectedFile.toString());
                String suggestedName = jcampData.suggestName(new File (jcampData.getFilePath()));
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

    @FXML
    void addAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Add NMR FID/Dataset");
        fileChooser.getExtensionFilters().addAll(
                FileExtensionFilterType.NMR_FID.getFilter(),
                FileExtensionFilterType.ALL_FILES.getFilter()
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            setInitialDirectory(selectedFile.getParentFile());
            openFile(selectedFile.toString(), true, true);
        }
        stage.setResizable(true);
    }

    @FXML
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

    /**
     * Gets a NMRData object from the filepath.
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

    public void openFile(NMRData nmrData, boolean clearOps, boolean appendFile, DatasetType datasetType) {
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

    public StackPane getProcessorPane() {
        return processorPane;
    }

    public ChartProcessor getChartProcessor() {
        return chartProcessor;
    }

    public boolean isFIDActive() {
        return isFID;
    }

    void addFID(NMRData nmrData, boolean clearOps, boolean reload) {
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

    /**
     * Check for existing default processing script and parse if present, otherwise generate
     * and parse a processing script for FID data for 1D and 2D data.
     * @param nmrData The NMRData set that will be processed.
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

    public void addDataset(DatasetBase dataset, boolean appendFile, boolean reload) {
        isFID = false;
        if (dataset.getFile() != null) {
            PreferencesController.saveRecentFiles(dataset.getFile().toString());
        }

        DatasetAttributes datasetAttributes = getActiveChart().setDataset(dataset, appendFile, false);
        getActiveChart().setCrossHairState(true, true, true, true);
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

    public void closeFile(File target) {
        getActiveChart().removeAllDatasets();
        // removeAllDatasets in chart only stops displaying them, so we need to actually close the dataset
        Path path1 = target.toPath();
        List<DatasetBase> currentDatasets = new ArrayList<>();
        currentDatasets.addAll(Dataset.datasets());
        for (DatasetBase datasetBase : currentDatasets) {
            Dataset dataset = (Dataset) datasetBase;
            File file = dataset.getFile();
            if (file != null) {
                try {
                    if (Files.exists(file.toPath())) {
                        if (Files.isSameFile(path1, file.toPath())) {
                            dataset.close();
                        }
                    }
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    @FXML
    private void expandAction(ActionEvent event) {
        charts.stream().forEach(chart -> {
            chart.expand();
        });
    }

    public void saveAsFavorite() {
        WindowIO.saveFavorite();
    }

    @FXML
    public void showPeakAttrAction(ActionEvent event) {
        showPeakAttr();
        peakAttrController.initIfEmpty();
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

    @FXML
    public void showProcessorAction(ActionEvent event) {
        ProcessorController processorController = getActiveChart().getProcessorController(true);
        processorController.show();
    }

    @FXML
    public void viewDatasetInNvJAction(ActionEvent event) {
        if ((chartProcessor != null) && (chartProcessor.datasetFile != null)) {
            String datasetPath = chartProcessor.datasetFile.getPath();
            if (datasetPath.equals("")) {
                return;
            }
            Runtime runTime = Runtime.getRuntime();

            String osName = System.getProperty("os.name");
            try {
                if (osName.startsWith("Mac")) {
                    String[] cmd = {"/usr/bin/open", datasetPath};
                    runTime.exec(cmd);
                } else if (osName.startsWith("Win")) {
                    String[] cmd = {"rundll32", "url.dll,FileProtocolHandler", datasetPath};
                    runTime.exec(cmd);
                } else if (osName.startsWith("Lin")) {
                    String[] cmd = {"NMRViewJ", "--files", datasetPath};
                    runTime.exec(cmd);
                }
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
        }
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    @FXML
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

    @FXML
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

    public void exportPDF(File file) {
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

    @FXML
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

    public void exportSVG(File file) {
        exportSVG(file.toString());
    }

    public void exportSVG(String fileName) {
        if (fileName != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            try {
                svgGC.create(true, canvas.getWidth(), canvas.getHeight(), fileName);
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

    @FXML
    public void copySVGAction(ActionEvent event) {
        SVGGraphicsContext svgGC = new SVGGraphicsContext();
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            svgGC.create(true, canvas.getWidth(), canvas.getHeight(), stream);
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

    @FXML
    private void printAction(ActionEvent event) {
        try {
            getActiveChart().printSpectrum();
        } catch (IOException ex) {
            ExceptionDialog eDialog = new ExceptionDialog(ex);
            eDialog.showAndWait();
        }
    }

    public void updateAttrDims() {
        if (isSideBarAttributesShowing()) {
            attributesController.setChart(getActiveChart());
        }
    }

    public void updateDatasetAttributeControls() {
        if (isSideBarAttributesShowing()) {
            attributesController.updateDatasetAttributeControls();
        }
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

    void setActiveController(Observable obs) {
        if (stage.isFocused()) {
            setActiveController();
        }
    }

    public void setActiveController() {
        activeController.set(this);
        if (attributesController != null) {
            attributesController.setAttributeControls();
        }
    }

    public static FXMLController getActiveController() {
        if (activeController.get() == null) {
            FXMLController controller = FXMLController.create();
            controller.setActiveController();
        }
        return activeController.get();
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
        PolyChart chart1 = new PolyChart(this, plotContent, canvas, peakCanvas, annoCanvas);
        activeChart = chart1;
        canvasBindings = new CanvasBindings(this, canvas);
        canvasBindings.setHandlers();
        initToolBar(toolBar);
        charts.add(chart1);
        chart1.setController(this);

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
            if (arg2.getWidth()  < 1.0 || arg2.getHeight() < 1.0) {
                return;
            }
            chartGroup.requestLayout();
        });

        controllers.add(this);
        statusBar.setMode(1);
        activeController.set(this);
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                crossHairStates[iCross][jOrient] = true;
            }
        }
        phaser = new Phaser(this, phaserBox);
        processorPane.getChildren().addListener(this::updateStageSize);
        cursorProperty.addListener( e -> setCursor());
        attributesPane = new AnchorPane();
        attributesController =  AttributesController.create(this, attributesPane);
        borderPane.heightProperty().addListener(e -> attributesController.updateScrollSize(borderPane));

        contentPane = new AnchorPane();
        contentController =  ContentController.create(this, contentPane);
        borderPane.heightProperty().addListener(e -> contentController.updateScrollSize(borderPane));

    }

    public BorderPane getMainBox() {
        return mainBox;
    }

    public void setCursor(Cursor cursor) {
        cursorProperty.set(cursor);
    }

    public void setCurrentCursor(Cursor cursor) {
        canvas.setCursor(cursor);
    }

    public Cursor getCurrentCursor() {
        return canvas.getCursor();
    }

    public void setCursor() {
        Cursor cursor = cursorProperty.getValue();
        canvas.setCursor(cursor);
        for (PolyChart chart : charts) {
            if (CanvasCursor.isCrosshair(cursor)) {
                chart.getCrossHairs().setCrossHairState(true);
            } else {
                chart.getCrossHairs().setCrossHairState(false);
            }
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

    public boolean getCrossHairState(int iCross, int jOrient) {
        return crossHairStates[iCross][jOrient];
    }

    public static FXMLController create() {
        return create(null);
    }

    public static FXMLController create(Stage stage) {
        FXMLLoader loader = new FXMLLoader(FXMLController.class.getResource("/fxml/NMRScene.fxml"));
        FXMLController controller = null;
        if (stage == null) {
            stage = new Stage(StageStyle.DECORATED);
        }

        try {
            Parent parent = loader.load();
            Scene scene = new Scene(parent);
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");
            AnalystApp.setStageFontSize(stage, AnalystApp.REG_FONT_SIZE_STR);
            controller = loader.getController();
            controller.stage = stage;
            FXMLController myController = controller;
            stage.focusedProperty().addListener(e -> myController.setActiveController(e));
            controller.setActiveController();
            AnalystApp.registerStage(stage, controller);
            stage.show();
        } catch (IOException ioE) {
            throw new IllegalStateException("Unable to create controller", ioE);
        }

        stage.maximizedProperty().addListener(controller::adjustSizeAfterMaximize);

        return controller;
    }

    /**
     * If the window is maximized, the current window widths are saved. If the window is restored down, the previously
     * saved values are used to set the window width.
     * @param observable the maximize property
     * @param oldValue previous value of maximize
     * @param newValue new value of maximize
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
            if(previousStageRestoreProcControllerVisible) {
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

    /**
     * Listener for changes to the processorPane's children, if a pane is added or removed, the stage width is adjusted accordingly.
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

    void initToolBar(ToolBar toolBar) {
        ArrayList<Node> buttons = new ArrayList<>();

        ButtonBase bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FILE, "Datasets", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnAction(e -> showDatasetsAction(e));
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
        bButton.setOnMouseClicked(e -> doFull(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH, "Expand", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doExpand(e));
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
        phaserButton.setOnAction(e -> toggleSideBarAttributes(phaserButton, attributesButton,contentButton));
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
        AnalystApp.getMainApp().addStatusBarTools(statusBar);

    }

    public void enableFavoriteButton() {
        favoriteButton.setDisable(ProjectBase.getActive().getProjectDir() == null);
    }

    List<PolyChart> getCharts(boolean all) {
        if (all) {
            return charts;
        } else {
            return Collections.singletonList(getActiveChart());
        }
    }

    public void doScale(MouseEvent e, double value) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            if (value == 0.0) {
                chart.autoScale();
            } else {
                chart.adjustScale(value);

            }
        }
    }

    public void doFull(MouseEvent e) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            chart.full();
        }
    }

    public void doExpand(MouseEvent e) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            chart.expand();
        }
    }

    public void doZoom(MouseEvent e, double value) {
        for (PolyChart chart : getCharts(e.isShiftDown())) {
            chart.zoom(value);
        }
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

    public void addScaleBox(PeakNavigator navigator, ToolBar navBar) {
        ObservableList<Double> scaleList = FXCollections.observableArrayList(0.0, 2.5, 5.0, 7.5, 10.0, 15.0, 20.0);
        ChoiceBox<Double> scaleBox = new ChoiceBox(scaleList);
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

    public void removePeakNavigator(Object o) {
        if (peakNavigator != null) {
            peakNavigator.removePeakList();
            bottomBox.getChildren().remove(peakNavigator.getToolBar());
            peakNavigator = null;
        }
    }

    public void showSpectrumComparator() {
        if (spectrumComparator == null) {
            VBox vBox = new VBox();
            bottomBox.getChildren().add(vBox);
            spectrumComparator = new SpectrumComparator(this, this::removeSpectrumComparator);
            spectrumComparator.initPathTool(vBox);
        }
    }

    public void removeSpectrumComparator(Object o) {
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

    public void removeSpectrumMeasureBar(Object o) {
        if (measureBar != null) {
            bottomBox.getChildren().remove(measureBar.getToolBar());
            measureBar = null;
        }
    }

    AnalyzerBar analyzerBar = null;

    public void showAnalyzerBar() {
        if (analyzerBar == null) {
            VBox vBox = new VBox();
            analyzerBar = new AnalyzerBar(this, this::removeAnalyzerBar);
            analyzerBar.buildBar(vBox);
            bottomBox.getChildren().add(vBox);
        }
    }

    public void removeAnalyzerBar(Object o) {
        if (analyzerBar != null) {
            bottomBox.getChildren().remove(analyzerBar.getToolBar());
            analyzerBar = null;
        }
    }

    public void linkPeakDims() {
        PeakLinker linker = new PeakLinker();
        linker.linkAllPeakListsByLabel();
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

    public void addChart() {
        PolyChart chart = new PolyChart(this, plotContent, canvas, peakCanvas, annoCanvas);
        charts.add(chart);
        chart.setChartDisabled(true);
        chartGroup.addChart(chart);
        activeChart = chart;
    }

    public void setChartDisable(boolean state) {
        for (PolyChart chart : charts) {
            chart.setChartDisabled(state);
        }

    }

    public void arrange(GridPaneCanvas.ORIENTATION orient) {
        setChartDisable(true);
        if (charts.size() == 1) {
            PolyChart chart = charts.get(0);
            double xLower = chart.xAxis.getLowerBound();
            double xUpper = chart.xAxis.getUpperBound();
            double yLower = chart.yAxis.getLowerBound();
            double yUpper = chart.yAxis.getUpperBound();
            List<DatasetAttributes> datasetAttrs = chart.getDatasetAttributes();
            if (datasetAttrs.size() > 1) {
                List<DatasetAttributes> current = new ArrayList<>();
                current.addAll(datasetAttrs);
                setNCharts(current.size());
                chart.getDatasetAttributes().clear();
                chartGroup.setOrientation(orient, true);
                for (int i = 0; i < charts.size(); i++) {
                    DatasetAttributes datasetAttr = current.get(i);
                    PolyChart iChart = charts.get(i);
                    iChart.setDataset(datasetAttr.getDataset());
                    iChart.setDatasetAttr(datasetAttr);
                }
                chart.syncSceneMates();
                setChartDisable(true);
                for (int i = 0; i < charts.size(); i++) {
                    PolyChart iChart = charts.get(i);
                    iChart.xAxis.setLowerBound(xLower);
                    iChart.xAxis.setUpperBound(xUpper);
                    iChart.yAxis.setLowerBound(yLower);
                    iChart.yAxis.setUpperBound(yUpper);
                    iChart.getCrossHairs().setCrossHairState(true);
                }
                setChartDisable(false);
                chartGroup.layoutChildren();
                charts.stream().forEach(c -> c.refresh());
                return;
            }
        }
        chartGroup.setOrientation(orient, true);
        setChartDisable(false);
        chartGroup.layoutChildren();
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

    public void setBorderState(boolean state) {
        setMinBorders(state);
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
            if (getMinBorders()) {
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
            if (getMinBorders()) {
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

    public void draw() {
        chartGroup.layoutChildren();
    }

    public void addGrid() {
        GridPaneCanvas.GridDimensions gdims = getGridDimensionInput();
        if (gdims == null) {
            return;
        }
        addCharts(gdims.rows(), gdims.cols());
    }

    public void removeSelectedChart() {
        if (charts.size() > 1) {
            getActiveChart().close();
            arrange(chartGroup.getOrientation());
        }
    }

    public void addCharts(int nRows, int nColumns) {
        setChartDisable(true);
        int nCharts = nRows * nColumns;
        setNCharts(nCharts);
        arrange(nRows);
        var chartActive = charts.get(0);
        setActiveChart(chartActive);
        setChartDisable(false);
        draw();
    }

    public int arrangeGetRows() {
        return chartGroup.getRows();
    }

    public int arrangeGetColumns() {
        return chartGroup.getColumns();
    }

    public void arrange(int nRows) {
        chartGroup.setRows(nRows);
        chartGroup.calculateAndSetOrientation();
    }

    public void alignCenters() {
        DatasetAttributes activeAttr = activeChart.datasetAttributesList.get(0);
        if (activeChart.peakListAttributesList.isEmpty()) {
            alignCentersWithTempLists();
        } else {
            PeakList refList = activeChart.peakListAttributesList.get(0).getPeakList();
            List<String> dimNames = new ArrayList<>();
            dimNames.add(activeAttr.getLabel(0));
            dimNames.add(activeAttr.getLabel(1));
            List<PeakList> movingLists = new ArrayList<>();
            for (PolyChart chart : charts) {
                if (chart != activeChart) {
                    PeakList movingList = chart.peakListAttributesList.get(0).getPeakList();
                    movingLists.add(movingList);
                }
            }
            PeakListAlign.alignCenters(refList, dimNames, movingLists);
        }
    }

    public void alignCentersWithTempLists() {
        DatasetAttributes activeAttr = activeChart.datasetAttributesList.get(0);
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

    public void config(String name, Object value) {
        if (Platform.isFxApplicationThread()) {
            try {
                PropertyUtils.setSimpleProperty(this, name, value);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                log.error(ex.getMessage(), ex);
            }
        } else {
            Platform.runLater(() -> {
                        try {
                            PropertyUtils.setProperty(this, name, value);
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                            log.error(ex.getMessage(), ex);
                        }
                    }
            );
        }
    }

    public Map<String, Object> config() {
        Map<String, Object> data = new HashMap<>();
        String[] beanNames = {"bgColor", "axesColor", "minBorders"};
        for (String beanName : beanNames) {
            try {
                if (beanName.contains("Color")) {
                    Object colObj = PropertyUtils.getSimpleProperty(this, beanName);
                    if (colObj instanceof Color) {
                        String colorName = GUIScripter.toRGBCode((Color) colObj);
                        data.put(beanName, colorName);
                    }
                } else {
                    data.put(beanName, PropertyUtils.getSimpleProperty(this, beanName));
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        return data;
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

    public void toggleCrossHairState(int iCross, int jOrient) {
        crossHairStates[iCross][jOrient] = !crossHairStates[iCross][jOrient];
        boolean state = crossHairStates[iCross][jOrient];
        for (PolyChart chart : charts) {
            CrossHairs crossHairs = chart.getCrossHairs();
            crossHairs.setCrossHairState(iCross, jOrient, state);
        }
        statusBar.setIconState(iCross, jOrient, state);
    }

    public void addSelectedPeakListener(ChangeListener listener) {
        selPeaks.addListener(listener);

    }

    /**
     * Checks if the active chart has a processorController instances or if the chart is empty.
     * @return True if active chart has ProcessorController else returns false.
     */
    public boolean isProcessorControllerAvailable() {
        return getActiveChart().getProcessorController(false) != null || getActiveChart().getDataset() == null;
    }
}
