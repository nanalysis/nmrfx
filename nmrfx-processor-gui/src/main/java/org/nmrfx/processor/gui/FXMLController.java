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
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.apache.commons.beanutils.PropertyUtils;
import org.controlsfx.control.PopOver;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.PDFGraphicsContext;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.peaks.PeakLinker;
import org.nmrfx.processor.datasets.peaks.PeakListAlign;
import org.nmrfx.processor.datasets.peaks.PeakNeighbors;
import org.nmrfx.processor.datasets.peaks.PeakNetworkMatch;
import org.nmrfx.processor.datasets.vendor.*;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.*;
import org.nmrfx.processor.gui.tools.SpectrumComparator;
import org.nmrfx.processor.gui.undo.UndoManager;
import org.nmrfx.ribbon.NmrFxRibbon;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FXMLController implements  Initializable, PeakNavigable {
    private static final Logger log = LoggerFactory.getLogger(FXMLController.class);

    @FXML
    private VBox topBar;
    @FXML
    private ToolBar btoolBar;
    @FXML
    private VBox bottomBox;
    @FXML
    private StackPane chartPane;
    @FXML
    private VBox phaserBox;
    @FXML
    private VBox vectorBox;

    @FXML
    private BorderPane borderPane;
    @FXML
    private BorderPane mainBox;
    @FXML
    private StackPane processorPane;
    @FXML
    private HBox dimHBox;
    @FXML
    private HBox dimHBox2;
    @FXML
    private Slider vecNum1;
    @FXML
    private GridPane rightBox;
    private NmrFxRibbon ribbon;

    private TextField[] rowTextBoxes = new TextField[0];
    private TextField fileIndexTextBox = new TextField();
    ToggleGroup rowToggleGroup = new ToggleGroup();
    private ChoiceBox<String> realImagChoiceBox = new ChoiceBox();
    private List<String> realImagChoices = new ArrayList<>();
    ChangeListener<String> vecNumListener;
    int[] rowIndices;
    int[] vecSizes;

    private Button cancelButton;
    EventHandler<ActionEvent> menuHandler;
    PopOver popOver = null;
    PopOver attributesPopOver = null;

    ChangeListener<String> dimListener;
    ChangeListener<Number> refDimListener;

    ChartProcessor chartProcessor;
    DocWindowController dwc = null;
    static SpecAttrWindowController specAttrWindowController = null;
    static boolean popOverMode = false;
    static PeakAttrController peakAttrController = null;
    ProcessorController processorController = null;
    Stage stage = null;
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
    Set<ControllerTool> tools = new HashSet<>();

    SimpleBooleanProperty processControllerVisible = new SimpleBooleanProperty(false);

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

    public void setBgColor(Color value) {
        bgColorProperty().set(value);
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

    public void setAxesColor(Color value) {
        axesColorProperty().set(value);
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

    void close() {
        // need to make copy of charts as the call to chart.close will remove the chart from charts
        // resulting in a java.util.ConcurrentModificationException
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
    }

    public boolean isPhaseSliderVisible() {
        return (rightBox.getChildren().size() > 0);
    }

    public void updatePhaser(boolean state) {
        if (state) {
            rightBox.add(phaserBox, 0, 0);
            phaser.getPhaseOp();
            if (chartProcessor == null) {
                phaser.setPH1Slider(activeChart.getDataPH1());
                phaser.setPH0Slider(activeChart.getDataPH0());
            }
        } else {
            rightBox.getChildren().remove(phaserBox);

        }
    }

    public static List<FXMLController> getControllers() {
        return controllers;
    }

    boolean filterChart(PolyChart chart) {
        return true;
    }

    public void setActiveChart(PolyChart chart) {
        if (activeChart != chart) {
            deselectCharts();
        }
        activeChart = chart;
        PolyChart.activeChart.set(chart);
        if (specAttrWindowController != null) {
            specAttrWindowController.setChart(activeChart);
        }
        if (statusBar != null) {
            statusBar.setChart(activeChart);
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
        charts.stream().filter(chart -> filterChart(chart)).forEach(chart -> {
            chart.autoScale();
            chart.layoutPlotChildren();
        });
    }

    @FXML
    private void fullAction(ActionEvent event) {
        charts.stream().filter(chart -> filterChart(chart)).forEach(chart -> {
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
                    ListCell<String> olc = new ListCell<String>() {
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
    public void openFIDAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Open NMR FID");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("NMR Fid", "fid", "ser", "*.nv", "*.dx", "*.jdx", "*.jdf", "*.dat"),
                new ExtensionFilter("Any File", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            setInitialDirectory(selectedFile.getParentFile());
            openFile(selectedFile.toString(), true, false);
        }
        stage.setResizable(true);
    }

    @FXML
    public void openDatasetAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Open NMR Dataset");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("NMR Dataset", "*.nv", "*.ucsf", "*.dx", "*.jdx", "1r", "2rr", "3rrr", "4rrrr", RS2DData.DATA_FILE_NAME),
                new ExtensionFilter("Any File", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        openDataset(selectedFile, false);
    }

    public void openDataset(File selectedFile, boolean append) {
        if (selectedFile != null) {
            try {
                setInitialDirectory(selectedFile.getParentFile());
                NMRData nmrData = NMRDataUtil.getNMRData(selectedFile.toString());
                if (nmrData instanceof NMRViewData) {
                    PreferencesController.saveRecentDatasets(selectedFile.toString());
                    NMRViewData nvData = (NMRViewData) nmrData;
                    Dataset dataset = nvData.getDataset();
                    addDataset(dataset, append, false);
                } else if (nmrData instanceof BrukerData) {
                    PreferencesController.saveRecentDatasets(selectedFile.toString());
                    BrukerData brukerData = (BrukerData) nmrData;
                    String suggestedName = brukerData.suggestName(new File(brukerData.getFilePath()));
                    String datasetName = GUIUtils.input("Dataset name", suggestedName);
                    Dataset dataset = brukerData.toDataset(datasetName);
                    addDataset(dataset, append, false);
                } else if (nmrData instanceof RS2DData) {
                    PreferencesController.saveRecentDatasets(selectedFile.toString());
                    RS2DData rs2dData = (RS2DData) nmrData;
                    String suggestedName = rs2dData.suggestName(new File(rs2dData.getFilePath()));
                    Dataset dataset = rs2dData.toDataset(suggestedName);
                    addDataset(dataset, append, false);
                }
            } catch (IOException ex) {
                GUIUtils.warn("Open Dataset", ex.getMessage());
            }
        }
        stage.setResizable(true);
    }

    @FXML
    void addAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Add NMR FID/Dataset");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("NMR Fid", "fid", "ser", "*.nv", "*.dx", "*.jdx"),
                new ExtensionFilter("Any File", "*.*")
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
                new ExtensionFilter("NMR Dataset", "*.nv", "*.ucsf"),
                new ExtensionFilter("Any File", "*.*")
        );
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(null);
        if (selectedFiles != null) {
            try {
                for (File selectedFile : selectedFiles) {
                    setInitialDirectory(selectedFile.getParentFile());
                    NMRData nmrData = NMRDataUtil.getFID(selectedFile.toString());
                    if (nmrData instanceof NMRViewData) {
                        PreferencesController.saveRecentDatasets(selectedFile.toString());
                        NMRViewData nvData = (NMRViewData) nmrData;
                        Dataset dataset = nvData.getDataset();
                    }
                }
            } catch (IllegalArgumentException | IOException iaE) {
                ExceptionDialog eDialog = new ExceptionDialog(iaE);
                eDialog.showAndWait();
            }

        }
    }

    public void openFile(String filePath, boolean clearOps, boolean appendFile) {
        openFile(filePath, clearOps, appendFile, null);
    }

    public void openFile(String filePath, boolean clearOps, boolean appendFile, DatasetType datasetType) {
        boolean reload = false;
        try {
            File newFile = new File(filePath);
            if (!appendFile) {
                if (chartProcessor != null) {
                    NMRData oldData = chartProcessor.getNMRData();
                    if (oldData != null) {
                        if (oldData instanceof NMRViewData) {
                            NMRViewData nvData = (NMRViewData) oldData;
                            // nvData.getDataset().close();
                        }
                    }
                }
            }
            File oldFile = getActiveChart().getDatasetFile();
            if (!appendFile && (oldFile != null)) {
                try {
                    if (oldFile.getCanonicalPath().equals(newFile.getCanonicalPath())) {
                        reload = true;
                    }
                } catch (java.io.IOException ioE) {
                    reload = false;
                }
                //chart.closeDataset();
            }
            NMRData nmrData = null;
            try {
                nmrData = NMRDataUtil.getFID(filePath);
            } catch (IllegalArgumentException iaE) {
                ExceptionDialog eDialog = new ExceptionDialog(iaE);
                eDialog.showAndWait();
                return;

            }
            if (nmrData != null) {
                if ((nmrData instanceof NMRViewData) && !nmrData.isFID()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Use \"Open Dataset\" to open non-fid file");
                    alert.showAndWait();
                    return;
                } else {
                    if (datasetType != null) {
                        nmrData.setPreferredDatasetType(datasetType);
                    }
                    addFID(nmrData, clearOps, reload);
                }
            }
            PreferencesController.saveRecentFIDs(filePath);
        } catch (IOException ioE) {
            ExceptionDialog eDialog = new ExceptionDialog(ioE);
            eDialog.showAndWait();
            return;
        }
        undoManager.clear();
    }

    public PeakAttrController getPeakAttrController() {
        return peakAttrController;
    }

    public boolean isPeakAttrControllerShowing() {
        boolean state = false;
        if (peakAttrController != null) {
            if (peakAttrController.getStage().isShowing()) {
                state = true;
            }
        }
        return state;
    }

    public ProcessorController getProcessorController(boolean createIfNull) {
        if ((processorController == null) && createIfNull) {
            processorController = ProcessorController.create(this, processorPane, getActiveChart());
        }
        return processorController;
    }

    public ChartProcessor getChartProcessor() {
        return chartProcessor;
    }

    public boolean isFIDActive() {
        return isFID;
    }

    void addFID(NMRData nmrData, boolean clearOps, boolean reload) {
        isFID = true;
        if (chartProcessor == null) {
            if (processorController == null) {
                processorController = ProcessorController.create(this, processorPane, getActiveChart());
            }
        }
        chartProcessor.setData(nmrData, clearOps);
        if (processorController != null) {
            processorController.viewingDataset(false);
            processorController.updateFileButton();
            processorController.show();
        } else {
            System.out.println("Coudn't make controller");
        }
        processorController.clearOperationList();
        chartProcessor.clearAllOperations();
        processorController.parseScript("");
        if (!reload) {
            getActiveChart().full();
            getActiveChart().autoScale();
            chartProcessor.loadDefaultScriptIfPresent();
        }
        getActiveChart().layoutPlotChildren();
        statusBar.setMode(0);

    }

    public void addDataset(DatasetBase dataset, boolean appendFile, boolean reload) {
        isFID = false;
        //dataset.setScale(1.0);
        int nDim = dataset.getNDim();
        // fixme kluge as not all datasets that are freq domain have attribute set
        for (int i = 0; ((i < nDim) && (i < 2)); i++) {
            dataset.setFreqDomain(i, true);
        }
        DatasetAttributes datasetAttributes = getActiveChart().setDataset(dataset, appendFile, false);
        datasetAttributes.dim[0] = 0;
        if (nDim > 1) {
            datasetAttributes.dim[1] = 1;
        }
        getActiveChart().setCrossHairState(true, true, true, true);
        if (processorController != null) {
            processorController.viewingDataset(true);
        }
        borderPane.setLeft(null);
        borderPane.setBottom(null);
        ObservableList<DatasetAttributes> datasetAttrList = getActiveChart().getDatasetAttributes();
        OptionalInt maxNDim = datasetAttrList.stream().mapToInt(d -> d.nDim).max();
        if (maxNDim.isPresent()) {
            if (getActiveChart().is1D() && (maxNDim.getAsInt() > 1)) {
                OptionalInt maxRows = datasetAttrList.stream().
                        mapToInt(d -> d.nDim == 1 ? 1 : d.getDataset().getSizeReal(1)).max();
                statusBar.set1DArray(maxNDim.getAsInt(), maxRows.getAsInt());
            } else {
                statusBar.setMode(maxNDim.getAsInt());
            }
        }

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
        charts.stream().filter(chart -> filterChart(chart)).forEach(chart -> {
            chart.expand();
        });
    }

    @FXML
    public void showSpecAttrAction(ActionEvent event) {
        if (specAttrWindowController == null) {
            if (popOverMode) {
                specAttrWindowController = SpecAttrWindowController.createPane();
            } else {
                specAttrWindowController = SpecAttrWindowController.create();
            }
        }
        if (specAttrWindowController != null) {
            if (popOverMode) {
                showAttributesPopOver(event);
            } else {
                specAttrWindowController.getStage().show();
                stage.setResizable(true);
                stage.toFront();
            }
        } else {
            System.out.println("Coudn't make controller");
        }
    }

    public void saveAsFavorite() {
        WindowIO.saveFavorite();
    }

    void showAttributesPopOver(ActionEvent event) {
        Pane pane = specAttrWindowController.getPane();
        if (attributesPopOver == null) {
            attributesPopOver = new PopOver(pane);
        }
        specAttrWindowController.setPopOver(attributesPopOver);
        attributesPopOver.setDetachable(true);
        attributesPopOver.setTitle("Spectrum Attributes");
        attributesPopOver.setHeaderAlwaysVisible(true);
        attributesPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        attributesPopOver.detachedProperty().addListener(e -> popOverDetached());
        specAttrWindowController.hideToolBar();
        Object obj = event.getSource();
        if (obj instanceof Node) {
            attributesPopOver.show((Node) event.getSource());
        } else {
            // fixme attributesPopOver.show(getActiveChart());

        }
    }

    private void popOverDetached() {
        if (attributesPopOver.isDetached()) {
            specAttrWindowController.showToolBar();
        } else {
            specAttrWindowController.hideToolBar();
        }
    }

    @FXML
    public void showPeakAttrAction(ActionEvent event) {
        showPeakAttr();
        peakAttrController.initIfEmpty();
    }

    public void showPeakAttr() {
        if (peakAttrController == null) {
            peakAttrController = PeakAttrController.create();
            stage.setResizable(true);
        }
        if (peakAttrController != null) {
            peakAttrController.getStage().show();
            peakAttrController.getStage().toFront();
        } else {
            System.out.println("Coudn't make controller");
        }
    }

    @FXML
    public void showProcessorAction(ActionEvent event) {
        if (processorController == null) {
            processorController = ProcessorController.create(this, processorPane, getActiveChart());
        }
        if (processorController != null) {
            processorController.show();
        } else {
            System.out.println("Coudn't make controller");
        }
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

            }
        }
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    public NmrFxRibbon getRibbon() {
        return ribbon;
    }

    @FXML
    private void handleVecNum(Event event) {
        Slider slider = (Slider) event.getSource();
        int iRow = (int) slider.getValue() - 1;
        int iDim = getRowChoice() - 1;
        chartProcessor.vecRow(iDim, iRow);
        getActiveChart().layoutPlotChildren();
        //label.setText("Hello World!");
    }

    public int[] getRows() {
        int[] rows = new int[rowTextBoxes.length];
        for (int i = 0; i < rows.length; i++) {
            if (rowTextBoxes[i] == null) {
                rows[i] = 0;
            } else {
                String text = rowTextBoxes[i].getText();
                if (text.isBlank()) {
                    rows[i] = 0;
                } else {
                    String[] fields = text.split("/");
                    int row = Integer.parseInt(fields[0].trim()) - 1;
                    rows[i] = row;
                }
            }
        }
        return rows;
    }

    @FXML
    private void handleVecRelease(Event event) {
        Slider slider = (Slider) event.getSource();
        int iRow = (int) slider.getValue();
        int delta = (int) (slider.getMax() - slider.getMin());

        int start = (int) (delta / 4 * Math.round(iRow / delta / 4)) - delta / 2;
        if (start < 1) {
            start = 1;
        }
        double end = start + delta;
        slider.setMin(start);
        slider.setMax(end);

    }

    @FXML
    public void exportPNG(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PNG");
        fileChooser.setInitialDirectory(getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            try {
                GUIUtils.snapNode(chartGroup, selectedFile);
            } catch (IOException ex) {
                GUIUtils.warn("Error saving png file", ex.getLocalizedMessage());
            }
        }
    }

    @FXML
    public void exportPDFAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.setInitialDirectory(getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        exportPDF(selectedFile);
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
        File selectedFile = fileChooser.showSaveDialog(null);
        exportSVG(selectedFile);
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
            content.put(svgFormat, stream.toString().getBytes(StandardCharsets.UTF_8));
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

    @FXML
    protected void vectorStatus(int[] sizes, int vecDim) {
        int nDim = sizes.length;
        statusBar.setMode(0);
        double sepWidth = 20.0;
        vecSizes = sizes.clone();

        if (nDim > 1) {
            borderPane.setLeft(vectorBox);
            borderPane.setBottom(dimHBox);
            if (rowTextBoxes.length != (nDim - 1)) {
                rowTextBoxes = new TextField[nDim - 1];
                dimHBox2.getChildren().clear();
                for (int i = 0; i < nDim - 1; i++) {
                    rowTextBoxes[i] = new TextField();
                    rowTextBoxes[i].setEditable(false);
                    rowTextBoxes[i].setPrefWidth(100);
                    RadioButton radioButton = new RadioButton((i + 2) + ": ");
                    Pane pane = new Pane();
                    pane.setMinWidth(sepWidth);
                    dimHBox2.getChildren().addAll(radioButton, rowTextBoxes[i], pane);
                    radioButton.setToggleGroup(rowToggleGroup);
                    if (i == 0) {
                        rowToggleGroup.selectToggle(radioButton);
                    }
                }
                Pane pane = new Pane();
                pane.setMinWidth(sepWidth);
                fileIndexTextBox.setPrefWidth(60);
                fileIndexTextBox.setEditable(false);
                dimHBox2.getChildren().addAll(realImagChoiceBox, pane, new Label("File Index: "),
                        fileIndexTextBox);

            }
            if (vecNum1 == null) {
                System.out.println("null sl");
            } else {
                int sizeDim = 1;
                if (vecDim != 0) {
                    sizeDim = 0;
                }
                System.out.println(sizeDim + " " + sizes[sizeDim]);
                int maxSize = sizes[sizeDim] < 256 ? sizes[sizeDim] : 256;
                vecNum1.setMax(maxSize);
                vecNum1.setValue(1);
                for (int iDim = 1; iDim < sizes.length; iDim++) {
                    rowTextBoxes[iDim - 1].setText(1 + " / " + sizes[iDim]);
                }
                fileIndexTextBox.setText("1");
            }

        } else {
            borderPane.setLeft(null);
            borderPane.setBottom(null);
        }
    }

    public void updateAttrDims() {
        if (specAttrWindowController != null) {
            specAttrWindowController.updateDims();
        }
    }

    protected void updatePhaseDim(Observable observable) {
        ReadOnlyIntegerProperty prop = (ReadOnlyIntegerProperty) observable;
        phaser.setPhaseDim(prop.getValue());
    }

    protected void setPhaseDimChoice(int phaseDim) {
        phaser.setPhaseDim(phaseDim);
    }

    protected void setRowLabel(int row, int size) {
        int iDim = getRowChoice() - 2;
        if (iDim >= 0) {
            rowTextBoxes[iDim].setText(row + " / " + size);
        }
    }

    void setFileIndex(int[] indices) {
        this.rowIndices = indices;
        setFileIndex();
    }

    void setFileIndex() {
        if (rowIndices != null) {
            String text = realImagChoiceBox.getValue();
            int riIndex = realImagChoices.indexOf(text);
            if (riIndex != -1) {
                int index = rowIndices[riIndex];
                fileIndexTextBox.setText(String.valueOf(index + 1));
            }
        }
    }

    Integer getRowChoice() {
        RadioButton radioButton = (RadioButton) rowToggleGroup.getSelectedToggle();
        Integer iDim;
        if (radioButton == null) {
            iDim = 1;
        } else {
            String text = radioButton.getText();
            iDim = Integer.parseInt(text.substring(0, 1));
        }
        return iDim;
    }

    void handleRowDimChange() {
        Integer iDim = getRowChoice();
        if (iDim != null) {
            int[] rows = getRows();
            if (rows.length > 0) {
                int row = rows[iDim - 2];
                if ((vecNum1 != null) && vecNum1.isVisible()) {
                    int maxSize = vecSizes[iDim - 1] < 256 ? vecSizes[iDim - 1] : 256;
                    System.out.println(iDim + " " + vecSizes[iDim - 1] + " " + maxSize);
                    vecNum1.setMax(maxSize);
                    vecNum1.setValue(row + 1);
                }
            }
        }
    }

    void updateRowDimMenu(int nDim) {
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
                    }
                }
            }
        }
        int[] region = {start, end};
        return region;
    }

    protected ArrayList<Double> getBaselineRegions(String vecDimName) {
        ArrayList<Double> fracs = new ArrayList<>();
        if (chartProcessor != null) {
            int currentIndex = processorController.propertyManager.getCurrentIndex();
            List<String> listItems = chartProcessor.getOperations(vecDimName);
            if (listItems != null) {
                System.out.println("curr ind " + currentIndex);
                Map<String, String> values = null;
                if (currentIndex != -1) {
                    String s = listItems.get(currentIndex);
                    System.out.println(s);
                    if (s.contains("REGIONS")) {
                        values = PropertyManager.parseOpString(s);
                        System.out.println(values.toString());
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
                            System.out.println("Error " + value);
                        }
                    }
                }
            }
        }
        return fracs;
    }

    public static String getHTMLDocs() {
        if (docString == null) {
            PythonInterpreter interpreter = new PythonInterpreter();
            interpreter.exec("from pyproc import *");
            interpreter.exec("from pydocs import *");
            PyObject pyDocObject = interpreter.eval("genAllDocs()");
            docString = (String) pyDocObject.__tojava__(String.class);
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
        if (specAttrWindowController != null) {
            specAttrWindowController.update();
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

    public void refreshPeakView(int peakNum) {
        PolyChart chart = getActiveChart();
        if (!chart.getPeakListAttributes().isEmpty()) {
            PeakList peakList = chart.getPeakListAttributes().get(0).getPeakList();
            Peak peak = peakList.getPeakByID(peakNum);
            if (peak != null) {
                refreshPeakView(peak);
            }
        }
    }

    public void refreshPeakView(String peakSpecifier) {
        Peak peak = PeakList.getAPeak(peakSpecifier);
        System.out.println("show peak2 " + peakSpecifier + " " + peak);

        if (peak != null) {
            refreshPeakView(peak);
        }
    }

    @Override
    public void refreshPeakView(Peak peak) {
        if (peak != null) {
            Set<String> dimsUsed = new HashSet<>();
            PeakList peakList = peak.getPeakList();
            int nDim = peakList.getNDim();
            for (int i = 0; i < nDim; i++) {
                String peakLabel = peakList.getSpectralDim(i).getDimName();
                boolean ok1 = true;
                for (PolyChart chart : charts) {
                    if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                        DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                        int aDim = dataAttr.nDim;
                        boolean ok2 = false;
                        for (int j = 0; j < aDim; j++) {
                            if (dataAttr.getLabel(j).equals(peakLabel)) {
                                ok2 = true;
                                break;
                            }
                        }
                        if (!ok2) {
                            ok1 = false;
                            break;
                        }
                    }
                }
                if (ok1) {
                    dimsUsed.add(peakLabel);
                }
            }
            for (PolyChart chart : charts) {
                if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                    DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                    int cDim = chart.getNDim();
                    int aDim = dataAttr.nDim;
                    Double[] ppms = new Double[cDim];
                    Double[] widths = new Double[cDim];
                    for (int i = 0; i < aDim; i++) {
                        if (!dimsUsed.contains(dataAttr.getLabel(i))) {
                            continue;
                        }
                        PeakDim peakDim = peak.getPeakDim(dataAttr.getLabel(i));
                        if (peakDim != null) {
                            double peakWidth = peakDim.getSpectralDimObj().getMeanWidthPPM();
                            ppms[i] = Double.valueOf(peakDim.getChemShiftValue());
                            widths[i] = widthScale * peakWidth;
                        }
                    }
                    if (widthScale > 0.0) {
                        chart.moveTo(ppms, widths);
                    } else {
                        chart.moveTo(ppms);
                    }
                }
            }
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
        rightBox.getChildren().remove(phaserBox);
        borderPane.setLeft(null);

        ribbon = MainApp.createRibbon();
        topBar.getChildren().add(ribbon);
        plotContent.setMouseTransparent(true);
        PolyChart chart1 = new PolyChart(this, plotContent, canvas, peakCanvas, annoCanvas);
        activeChart = chart1;
        canvasBindings = new CanvasBindings(this, canvas);
        canvasBindings.setHandlers();
        //TODO NMR-5099 remove toolbar, use ribbon
        initToolBar();
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
        rowToggleGroup.selectedToggleProperty().addListener(e -> handleRowDimChange());
        vecNumListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String string, String string2) {
                String text = realImagChoiceBox.getValue();
                int vecNum = realImagChoices.indexOf(text);
                chartProcessor.setVector(vecNum);
                setFileIndex();
            }
        };
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
            Scene scene = new Scene((Pane) parent);
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<FXMLController>getController();
            controller.stage = stage;
            //controllers.add(controller);
            FXMLController myController = controller;
            stage.focusedProperty().addListener(e -> myController.setActiveController(e));
            controller.setActiveController();
            stage.setTitle("NMRFx Processor");
            MainApp.registerStage(stage, controller);
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }
        return controller;
    }

    public static StackPane makeNewWinIcon() {
        StackPane stackPane = new StackPane();
        stackPane.setPadding(Insets.EMPTY);
        int size = 22;
        int size2 = size / 2 - 2;
        Rectangle rect = new Rectangle(size, size);
        rect.setFill(Color.LIGHTGREY);
        rect.setStroke(Color.LIGHTGREY);
        Line line1 = new Line(0.0f, size2, size, size2);
        Line line2 = new Line(0.0f, size2, size, size2);
        Line line3 = new Line(size2, 0, size2, size);
        Line line4 = new Line(size2, 0, size2, size);

        line1.setTranslateY(-size2);
        line2.setTranslateY(size2);
        line3.setTranslateX(-size2);
        line4.setTranslateX(size2);
        stackPane.getChildren().add(rect);
        stackPane.getChildren().add(line1);
        stackPane.getChildren().add(line2);
        stackPane.getChildren().add(line3);
        stackPane.getChildren().add(line4);
        line1.setStroke(Color.BLACK);
        line2.setStroke(Color.BLACK);
        line3.setStroke(Color.BLACK);
        line4.setStroke(Color.BLACK);
        return stackPane;
    }

    //TODO NMR-5099 remove once replaced.
    //we need to keep it for now because it creates some buttons that are accessed from the outside
    void initToolBar() {
        String iconSize = "16px";
        String fontSize = "7pt";
        ArrayList<Node> buttons = new ArrayList<>();

        ButtonBase bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FOLDER_OPEN, "Open", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> openFIDAction(e));
        // buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FILE, "Datasets", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> showDatasetsAction(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.WRENCH, "Attributes", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> showSpecAttrAction(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.HEART, "Favorite", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> saveAsFavorite());
        buttons.add(bButton);
        buttons.add(new Separator(Orientation.VERTICAL));
        // bButton.disableProperty().bind(Project.getActive());

        /* Disabled till clipping problem fixed
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.PRINT, "Print", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> printAction(e));
        buttons.add(bButton);
         */
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REFRESH, "Refresh", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> getActiveChart().refresh());
        buttons.add(bButton);
        cancelButton = GlyphsDude.createIconButton(FontAwesomeIcon.STOP, "Halt", iconSize, fontSize, ContentDisplay.TOP);
        buttons.add(cancelButton);

        buttons.add(new Separator(Orientation.VERTICAL));
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.UNDO, "Undo", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> undoManager.undo());
        buttons.add(bButton);
        bButton.disableProperty().bind(undoManager.undoable.not());
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REPEAT, "Redo", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> undoManager.redo());
        buttons.add(bButton);
        bButton.disableProperty().bind(undoManager.redoable.not());

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.EXPAND, "Full", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doFull(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH, "Expand", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doExpand(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH_MINUS, "In", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doZoom(e, 1.2));
        bButton.setOnScroll((ScrollEvent event) -> {
            double x = event.getDeltaX();
            double y = event.getDeltaY();
            if (y < 0.0) {
                getActiveChart().zoom(1.1);
            } else {
                getActiveChart().zoom(0.9);

            }
        });
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH_PLUS, "Out", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doZoom(e, 0.8));
        bButton.setOnScroll((ScrollEvent event) -> {
            double x = event.getDeltaX();
            double y = event.getDeltaY();
            if (y < 0.0) {
                getActiveChart().zoom(1.1);
            } else {
                getActiveChart().zoom(0.9);

            }
        });
        buttons.add(bButton);

        buttons.add(new Separator(Orientation.VERTICAL));
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROWS_V, "Auto", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doScale(e, 0.0));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_UP, "Higher", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doScale(e, 0.8));
        bButton.setOnScroll((ScrollEvent event) -> {
            double x = event.getDeltaX();
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
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_DOWN, "Lower", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnMouseClicked(e -> doScale(e, 1.2));

        bButton.setOnScroll((ScrollEvent event) -> {
            double x = event.getDeltaX();
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

        Image imageIcon = new Image("/images/Icon_NVJ_16.png", true);
        ImageView imageView = new ImageView(imageIcon);

        for (Node node : buttons) {
            if (node instanceof Button) {
                node.getStyleClass().add("toolButton");
            }
        }

        statusBar = new SpectrumStatusBar(this);
        statusBar.buildBar(btoolBar);
        MainApp.getMainApp().addStatusBarTools(statusBar);

    }

    public List<PolyChart> getCharts(boolean all) {
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

    public Peak getActivePeak() {
        Peak peak = null;
        if (peakNavigator != null) {
            peak = peakNavigator.getPeak();
        }
        return peak;
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
            GridPane navBar = new GridPane();
            measureBar = new SpectrumMeasureBar(this, this::removeSpectrumMeasureBar);
            measureBar.buildBar(navBar);
            bottomBox.getChildren().add(navBar);
        }
    }

    public void removeSpectrumMeasureBar(Object o) {
        if (measureBar != null) {
            bottomBox.getChildren().remove(measureBar.getToolBar());
            measureBar = null;
        }
    }
    AnalyzerBar analyzerBar = null;

    public AnalyzerBar getSpectrumAnalyzerBar() {
        return analyzerBar;
    }

    public void showAnalyzerBar() {
        if (analyzerBar == null) {
            GridPane navBar = new GridPane();
            analyzerBar = new AnalyzerBar(this, this::removeAnalyzerBar);
            analyzerBar.buildBar(navBar);
            bottomBox.getChildren().add(navBar);
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

    public void removeChart() {
        if (activeChart != null) {
            removeChart(activeChart);
        }
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
//                refreshChart.requestLayout();
//                refreshChart.layout();
                refreshChart.layoutPlotChildren();
            }
        }
    }

    public void addChart() {
        PolyChart chart = new PolyChart(this, plotContent, canvas, peakCanvas, annoCanvas);
        charts.add(chart);
        chart.setChartDisabled(true);
        // chart.setController(this);
        chartGroup.addChart(chart);
        activeChart = chart;
    }

    public Integer addChart(Integer pos) {
        GridPaneCanvas.ORIENTATION orient;
        if (pos < 2) {
            orient = GridPaneCanvas.ORIENTATION.HORIZONTAL;
        } else {
            orient = GridPaneCanvas.ORIENTATION.VERTICAL;
        }
        PolyChart chart = new PolyChart(this, plotContent, canvas, peakCanvas, annoCanvas);
        charts.add(chart);
        chart.setController(this);
       // chartGroup.setOrientation(orient, false);
        if ((pos % 2) == 0) {
            chartGroup.addChart(0, chart);
        } else {
            chartGroup.addChart(chart);
        }
        arrange(orient);
        activeChart = chart;

        return 0;
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
                    //iChart.refresh();
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
//            System.out.println("prepare " + iChild + " " + iRow + " " + iCol + " " + borders[0] + " " + borders[1] + " " + borders[2] + " " + borders[3]);
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
//        chartGroup.getChildrenUnmodifiable().stream().map((node) -> (PolyChart) node).forEachOrdered((chart) -> {
//            chart.layoutPlotChildren();
//        });
    }

    public void draw() {
        chartGroup.layoutChildren();
    }

    public void addGrid() {
        String rows = GUIUtils.input("nRows");
        try {
            if ((rows != null) && !rows.isBlank()) {
                String columns = GUIUtils.input("nColumns");
                if ((columns != null) && !columns.isBlank()) {
                    int nRows = Integer.parseInt(rows);
                    int nColumns = Integer.parseInt(columns);
                    addCharts(nRows, nColumns);
                }
            }
        } catch (NumberFormatException nfe) {
            GUIUtils.warn("Grid Values", "Entry not an integer");
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
    }

    public void alignCenters() {
        DatasetAttributes activeAttr = (DatasetAttributes) activeChart.datasetAttributesList.get(0);
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
        DatasetAttributes activeAttr = (DatasetAttributes) activeChart.datasetAttributesList.get(0);
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
        //refList.clusterPeaks();
        int[] dims = activeAttr.dim.clone();

        for (int i = 2; i < dims.length; i++) {
            dims[i] = -1;
        }
        System.out.println(refList.getName());
        for (PolyChart chart : charts) {
            ObservableList<DatasetAttributes> dataAttrList = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrList) {
                if (dataAttr != activeAttr) {
                    PeakList movingList = PeakPicking.peakPickActive(chart, dataAttr, false, false, null, false, "movingList");
                    movingList.unLinkPeaks();
                    movingList.clearSearchDims();
                    movingList.addSearchDim(dimName1, 0.05);
                    movingList.addSearchDim(dimName2, 0.1);
                    //movingList.clusterPeaks();
                    System.out.println("act " + dataAttr.getFileName() + " " + movingList.size());

                    System.out.println("test " + movingList.getName());
                    double[] centers = refList.centerAlign(movingList, dims);
                    for (double center : centers) {
                        System.out.println(center);
                    }
                    double[] match;
                    if (false) {
                        PeakNetworkMatch networkMatcher = new PeakNetworkMatch(refList, movingList);
                        networkMatcher.bpMatchPeaks(dimName1, dimName2, 0.1, 3.0, centers, true, null);
                        match = networkMatcher.getOptOffset();
                    } else {
                        String[] dimNames = {dimName1, dimName2};
                        double[] nOffset = {centers[0], centers[1]};
                        PeakNeighbors neighbor = new PeakNeighbors(refList, movingList, 25, dimNames);
                        neighbor.optimizeMatch(nOffset, 0.0, 1.0);
                        match = new double[3];
                        match[0] = nOffset[0];
                        match[1] = nOffset[1];
                    }
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

    public VBox getBottomBox() {
        return bottomBox;
    }

    public Set<ControllerTool> getTools() {
        return tools;
    }

    public void addTool(ControllerTool tool) {
        tools.add(tool);
    }

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

    public ControllerTool getTool(String className) {
        ControllerTool result = null;
        for (ControllerTool tool : tools) {
            if (tool.getClass().getName().equals(className)) {
                result = tool;
                break;
            }
        }
        return result;
    }

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

    protected void updateVecNumChoice(boolean[] complex) {
        char[] chars = {'R', 'I'};
        realImagChoices.clear();
        realImagChoiceBox.getItems().clear();
        int nDim = complex.length;
        if (nDim > 1) {
            int nVectors = 1;
            for (int iDim = 1; iDim < nDim; iDim++) {
                nVectors *= complex[iDim] ? 2 : 1;
            }
            realImagChoiceBox.valueProperty().removeListener(vecNumListener);
            StringBuilder sBuilder = new StringBuilder();
            for (int i = 0; i < nVectors; i++) {
                sBuilder.setLength(0);
                for (int j = nDim - 2; j >= 0; j--) {
                    if (complex[j + 1]) {
                        int k = (int) Math.pow(2, j);
                        int kk = (i / k) % 2;
                        sBuilder.append(chars[kk]);
                    } else {
                        sBuilder.append("R");
                    }
                }
                System.out.println(i + " " + nVectors + " " + sBuilder.toString());
                realImagChoiceBox.getItems().add(sBuilder.toString());
                realImagChoices.add(sBuilder.toString());
            }
            realImagChoiceBox.setValue(realImagChoices.get(0));
            realImagChoiceBox.valueProperty().addListener(vecNumListener);
        }
    }

}
