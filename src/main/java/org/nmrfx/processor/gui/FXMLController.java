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

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakNetworkMatch;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.NMRViewData;
import org.nmrfx.processor.gui.controls.FractionPaneChild;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.controlsfx.control.PopOver;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakLinker;
import org.nmrfx.processor.datasets.peaks.PeakNeighbors;
import org.nmrfx.processor.gui.controls.FractionCanvas;
import org.nmrfx.processor.gui.controls.LayoutControlCanvas;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.spectra.CanvasBindings;
import org.nmrfx.processor.gui.spectra.CrossHairs;
import org.nmrfx.processor.gui.undo.UndoManager;
import org.nmrfx.utilities.DictionarySort;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

public class FXMLController implements FractionPaneChild, Initializable, PeakNavigable {

    @FXML
    private VBox topBar;
    @FXML
    private ToolBar toolBar;
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
    private Label ph0Label;
    @FXML
    private Label ph1Label;
    @FXML
    private Slider ph0Slider;
    @FXML
    private Slider ph1Slider;
    @FXML
    private Label rowLabel;
    @FXML
    private Slider vecNum1;
    @FXML
    private GridPane rightBox;

    private Button cancelButton;
    EventHandler<ActionEvent> menuHandler;
    PopOver popOver = new PopOver();
    PopOver attributesPopOver = null;

    ChangeListener<String> dimListener;
    ChangeListener<Number> refDimListener;

    ChartProcessor chartProcessor;
    DocWindowController dwc = null;
    static SpecAttrWindowController specAttrWindowController = null;
    static boolean popOverMode = false;
    static PeakAttrController peakAttrController = null;
    ProcessorController processorController = null;
    SimpleObjectProperty<ScannerController> scannerController = new SimpleObjectProperty(null);
    Stage stage = null;
    String delImagString = "False";
    boolean isFID = true;

    static FXMLController activeController = null;
    static String docString = null;
    static ObservableList<Dataset> datasetList = FXCollections.observableArrayList();
    static List<FXMLController> controllers = new ArrayList<>();
    static ConsoleController consoleController = null;

    ObservableList<PolyChart> charts = FXCollections.observableArrayList();
    private PolyChart activeChart = null;
    SpectrumStatusBar statusBar;
    SpectrumMeasureBar measureBar = null;
    BooleanProperty sliceStatus = new SimpleBooleanProperty(false);
    File initialDir = null;

    CanvasBindings canvasBindings;

    private FractionCanvas chartGroup;
    private boolean minimizeBorders = false;

    PeakNavigator peakNavigator;
    PeakSlider peakSlider;
    ListView datasetListView = new ListView();

    SimpleObjectProperty<List<Peak>> selPeaks = new SimpleObjectProperty<>();
    UndoManager undoManager = new UndoManager();
    double widthScale = 5.0;
    Canvas canvas = new Canvas();
    Canvas peakCanvas = new Canvas();
    Canvas annoCanvas = new Canvas();
    Pane plotContent = new Pane();
    boolean[][] crossHairStates = new boolean[2][2];

    public File getInitialDirectory() {
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
            activeController = activeChart.getController();
            activeController.setActiveChart(activeChart);
        }
    }

    public boolean isPhaseSliderVisible() {
        return (rightBox.getChildren().size() > 0);
    }

    public void updatePhaser(boolean state) {
        if (state) {
            rightBox.add(phaserBox, 0, 0);
            getPhaseOp();
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
        activeChart = chart;
        PolyChart.activeChart = chart;
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

    public Stage getStage() {
        return stage;
    }

    public boolean hasSlider() {
        return peakSlider != null;
    }

    public PeakSlider getSlider() {
        return peakSlider;
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
    void showDatasetsAction(ActionEvent event) {
        if (Dataset.datasets().isEmpty()) {
            Label label = new Label("No open datasets\nUse File Menu Open item\nto open datasets");
            label.setStyle("-fx-font-size:12pt;-fx-text-alignment: center; -fx-padding:10px;");
            popOver.setContentNode(label);
        } else {
            datasetListView.setStyle("-fx-font-size:12pt;");

            DictionarySort<Dataset> sorter = new DictionarySort<>();
            datasetListView.getItems().clear();
            Dataset.datasets().stream().sorted(sorter).forEach((Dataset d) -> {
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
    void openFIDAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Open NMR FID");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("NMR Fid", "fid", "ser", "*.nv", "*.dx", "*.jdx"),
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
    void openDatasetAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Open NMR Dataset");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("NMR Dataset", "*.nv", "*.ucsf", "*.dx", "*.jdx"),
                new ExtensionFilter("Any File", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        openDataset(selectedFile, false);
    }

    public void openDataset(File selectedFile, boolean append) {
        if (selectedFile != null) {
            try {
                setInitialDirectory(selectedFile.getParentFile());
                NMRData nmrData = NMRDataUtil.getFID(selectedFile.toString());
                if (nmrData instanceof NMRViewData) {
                    PreferencesController.saveRecentDatasets(selectedFile.toString());
                    NMRViewData nvData = (NMRViewData) nmrData;
                    Dataset dataset = nvData.getDataset();
                    addDataset(dataset, append, true);

                }
            } catch (IOException ex) {
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
    void addNoDrawAction(ActionEvent event) {
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

            }
            if (nmrData != null) {
                if ((nmrData instanceof NMRViewData) && !nmrData.isFID()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Use \"Open Dataset\" to open non-fid file");
                    alert.showAndWait();
                    return;
                } else {
                    addFID(nmrData, clearOps, reload);
                }
            }
            PreferencesController.saveRecentFIDs(filePath);
        } catch (IOException ioE) {
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
            processorController = ProcessorController.create(this, stage, getActiveChart());
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
                processorController = ProcessorController.create(this, stage, getActiveChart());
            }
        }
        chartProcessor.setData(nmrData, clearOps);
        if (processorController != null) {
            processorController.getStage().show();
            processorController.viewingDataset(false);
        } else {
            System.out.println("Coudn't make controller");
        }
        processorController.clearOperationList();
        chartProcessor.clearAllOperations();
        processorController.parseScript("");
        if (!reload) {
            getActiveChart().full();
            getActiveChart().autoScale();
        }
        getActiveChart().layoutPlotChildren();
        statusBar.setMode(0);

    }

    public void addDataset(Dataset dataset, boolean appendFile, boolean reload) {
        isFID = false;
        //dataset.setScale(1.0);
        int nDim = dataset.getNDim();
        // fixme kluge as not all datasets that are freq domain have attribute set
        for (int i = 0; ((i < nDim) && (i <2)); i++) {
            dataset.setFreqDomain(i, true);
        }
        DatasetAttributes datasetAttributes = getActiveChart().setDataset(dataset, appendFile);
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
                        mapToInt(d -> d.nDim == 1 ? 1 : d.getDataset().getSize(1)).max();
                statusBar.set1DArray(maxNDim.getAsInt(), maxRows.getAsInt());
            } else {
                statusBar.setMode(maxNDim.getAsInt());
            }
        }

        getPhaseOp();
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

    public static void updateDatasetList() {
        datasetList.clear();
        datasetList.addAll(Dataset.datasets());
    }

    public void closeFile(File target) {
        getActiveChart().removeAllDatasets();
        // removeAllDatasets in chart only stops displaying them, so we need to actually close the dataset
        Path path1 = target.toPath();
        for (Dataset dataset : Dataset.datasets()) {
            File file = dataset.getFile();
            if (file != null) {
                try {
                    if (Files.exists(file.toPath())) {
                        if (Files.isSameFile(path1, file.toPath())) {
                            dataset.close();
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
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
    void showPeakAttrAction(ActionEvent event) {
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
    void showProcessorAction(ActionEvent event) {
        if (processorController == null) {
            processorController = ProcessorController.create(this, stage, getActiveChart());
        }
        if (processorController != null) {
            processorController.getStage().show();
        } else {
            System.out.println("Coudn't make controller");
        }
    }

    @FXML
    void showScannerAction(ActionEvent event) {
        if (scannerController.get() == null) {
            ScannerController sControl = ScannerController.create(this, stage, getActiveChart());
            scannerController.set(sControl);
        }
        if (scannerController.get() != null) {
            scannerController.get().getStage().show();
        } else {
            System.out.println("Coudn't make controller");
        }
    }

    @FXML
    void viewDatasetInNvJAction(ActionEvent event) {
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

    @FXML
    private void handlePh0(Event event) {
        double sliderPH0 = ((Slider) event.getSource()).getValue();
        sliderPH0 = Math.round(sliderPH0 * 10) / 10.0;
        ph0Label.setText(String.format("%.1f", sliderPH0));
        double deltaPH0 = 0.0;
        if (getActiveChart().hasData()) {
            deltaPH0 = sliderPH0 - getActiveChart().getDataPH0();
        }
        if (getActiveChart().is1D()) {
            getActiveChart().setPh0(deltaPH0);
            getActiveChart().layoutPlotChildren();
        } else {
            //chart.setPh0(sliderPH0);
            getActiveChart().setPh0(deltaPH0);
            getActiveChart().getCrossHairs().refreshCrossHairs();
        }
    }

    @FXML
    private void handlePh1(Event event) {
        PolyChart chart = getActiveChart();
        double sliderPH0 = ph0Slider.getValue();
        double sliderPH1 = ph1Slider.getValue();
        double deltaPH1 = 0.0;
        if (chart.hasData()) {
            deltaPH1 = sliderPH1 - (chart.getDataPH1() + chart.getPh1());
        }
        double pivotFraction = chart.getPivotFraction();
        sliderPH0 = sliderPH0 - deltaPH1 * pivotFraction;
//System.out.printf("ph0 %.3f ph1 %.3f delta %.3f pivotfr %.3f delta0 %.3f\n",sliderPH0, sliderPH1, deltaPH1, pivotFraction,(deltaPH1*pivotFraction));

        sliderPH0 = Math.round(sliderPH0 * 10) / 10.0;
        sliderPH1 = Math.round(sliderPH1 * 10) / 10.0;

        setPH0Slider(sliderPH0);
        double deltaPH0 = 0.0;
        deltaPH1 = 0.0;
        if (chart.hasData()) {
            deltaPH0 = sliderPH0 - chart.getDataPH0();
            deltaPH1 = sliderPH1 - chart.getDataPH1();
        }

        ph0Label.setText(String.format("%.1f", sliderPH0));
        ph1Label.setText(String.format("%.1f", sliderPH1));

        if (chart.is1D()) {
            chart.setPh0(deltaPH0);
            chart.setPh1(deltaPH1);
            chart.layoutPlotChildren();
        } else {
            //chart.setPh0(sliderPH0);
            //chart.setPh1(sliderPH1);
            chart.setPh0(deltaPH0);
            chart.setPh1(deltaPH1);
            chart.getCrossHairs().refreshCrossHairs();
        }
    }

    @FXML
    private void handlePh0Reset(Event event) {
        double ph0 = ph0Slider.getValue();
        handlePh0Reset(ph0);
    }

    public void handlePh0Reset(double ph0) {
        handlePh0Reset(ph0, true);
    }

    public void handlePh0Reset(double ph0, boolean updateOp) {
        ph0 = Math.round(ph0 * 10) / 10.0;
        double halfRange = 22.5;
        double start = halfRange * Math.round(ph0 / halfRange) - 2.0 * halfRange;
        double end = start + 4 * halfRange;
        ph0Slider.setMin(start);
        ph0Slider.setMax(end);
        ph0Slider.setBlockIncrement(0.1);
        ph0Slider.setValue(ph0);
        ph0Label.setText(String.format("%.1f", ph0));
        if (updateOp) {
            setPhaseOp();
        }
    }

    @FXML
    public void setPhaseLabels(double ph0, double ph1) {
        ph0 = Math.round(ph0 * 10) / 10.0;
        ph1 = Math.round(ph1 * 10) / 10.0;
        ph0Label.setText(String.format("%.1f", ph0));
        ph1Label.setText(String.format("%.1f", ph1));
    }

    @FXML
    private void handlePh1Reset(Event event) {
        double ph1 = ph1Slider.getValue();
        handlePh1Reset(ph1);
    }

    void handlePh1Reset(double ph1) {
        handlePh1Reset(ph1, true);
    }

    void handlePh1Reset(double ph1, boolean updateOp) {
        ph1 = Math.round(ph1 * 10) / 10.0;
        double start = 90.0 * Math.round(ph1 / 90.0) - 180.0;
        double end = start + 360.0;
        ph1Slider.setMin(start);
        ph1Slider.setMax(end);
        ph1Slider.setValue(ph1);
        ph1Label.setText(String.format("%.1f", ph1));
        if (updateOp) {
            setPhaseOp();
        }
    }

    @FXML
    private void handleVecNum(Event event) {
        Slider slider = (Slider) event.getSource();
        int iRow = (int) slider.getValue();
        chartProcessor.vecRow(iRow - 1);
        getActiveChart().layoutPlotChildren();
        //label.setText("Hello World!");
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
    void exportPDFAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.setInitialDirectory(getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            try {
                getActiveChart().exportVectorGraphics(selectedFile.toString(), "pdf");
            } catch (IOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
        stage.setResizable(true);
    }

    @FXML
    void exportSVGAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        fileChooser.setInitialDirectory(getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            try {
                svgGC.create(true, canvas.getWidth(), canvas.getHeight(), selectedFile.toString());
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
    void copySVGAction(ActionEvent event) {
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
//            DataFormat svgFormat = DataFormat.lookupMimeType("image/svg+xml");
//            if (svgFormat == null) {
//                svgFormat = new DataFormat("image/svg+xml");
//            }
//            content.put(svgFormat, stream.toString());
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

        if (nDim > 1) {
            borderPane.setLeft(vectorBox);
            borderPane.setBottom(rowLabel);

            if (vecNum1 == null) {
                System.out.println("null sl");
            } else {
                int sizeDim = 1;
                if (vecDim != 0) {
                    sizeDim = 0;
                }
                System.out.println(sizeDim + " " + sizes[sizeDim]);
                int maxSize = sizes[sizeDim] < 128 ? sizes[sizeDim] : 128;
                vecNum1.setMax(maxSize);
                vecNum1.setValue(1);
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
        setPhaseDim(prop.getValue());
    }

    protected void setPhaseDimChoice(int phaseDim) {
        setPhaseDim(phaseDim);
    }

    protected void setPhaseDim(int phaseDim) {
        PolyChart chart = getActiveChart();
        if (phaseDim >= 0) {
            chart.setPhaseDim(phaseDim);
            getPhaseOp();
            //handlePh1Reset(chart.getPh1());
            //handlePh0Reset(chart.getPh0());
        } else {
            chart.phaseDim = 0;
            handlePh1Reset(0.0);
            handlePh0Reset(0.0);
        }
    }

    protected void setPH0Slider(double value) {
        value = Math.round(value * 10) / 10.0;
        double halfRange = 22.5;
        double start = halfRange * Math.round(value / halfRange) - 2.0 * halfRange;
        double end = start + 4 * halfRange;
        ph0Slider.setMin(start);
        ph0Slider.setMax(end);
        ph0Slider.setValue(value);
    }

    protected void setPH1Slider(double value) {
        value = Math.round(value * 10) / 10.0;
        double start = 90.0 * Math.round(value / 90.0) - 180.0;
        double end = start + 360.0;
        ph1Slider.setMin(start);
        ph1Slider.setMax(end);
        ph1Slider.setValue(value);
    }

    protected void setRowLabel(int row, int size) {
        rowLabel.setText("Row: " + row + " / " + size);
    }

    @FXML
    private void setPhasePivot(ActionEvent event) {
        getActiveChart().setPhasePivot();
    }

    @FXML
    private void autoPhaseFlat0(ActionEvent event) {
        getActiveChart().autoPhaseFlat(false);
    }

    @FXML
    private void autoPhaseFlat01(ActionEvent event) {
        getActiveChart().autoPhaseFlat(true);
    }

    @FXML
    private void autoPhaseMax(ActionEvent event) {
        getActiveChart().autoPhaseMax();
    }

    @FXML
    private void setPhase_minus90_180(ActionEvent event) {
        PolyChart chart = getActiveChart();
        String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", -90.0, 180.0, delImagString);
        setPhaseOp(opString);
        setPH1Slider(180.0);
        setPH0Slider(-90.0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
        chart.layoutPlotChildren();
    }

    @FXML
    private void setPhase_0_0(ActionEvent event) {
        PolyChart chart = getActiveChart();
        String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", 0.0, 0.0, delImagString);
        setPhaseOp(opString);
        setPH1Slider(0.0);
        setPH0Slider(0.0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
        chart.layoutPlotChildren();
    }

    @FXML
    private void setPhase_180_0(ActionEvent event) {
        PolyChart chart = getActiveChart();
        String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", 180.0, 0.0, delImagString);
        setPhaseOp(opString);
        setPH1Slider(0.0);
        setPH0Slider(180.0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
        chart.layoutPlotChildren();
    }

    @FXML
    private void setPhaseOp(ActionEvent event) {
        setPhaseOp();
    }

    private void setPhaseOp(String opString) {
        PolyChart chart = getActiveChart();
        int opIndex = chart.processorController.propertyManager.setOp(opString);
        chart.processorController.propertyManager.setPropSheet(opIndex, opString);
    }

    private void setPhaseOp() {
        PolyChart chart = getActiveChart();
        double ph0 = ph0Slider.getValue();
        double ph1 = ph1Slider.getValue();
        String phaseDim = String.valueOf(chart.phaseDim + 1);
        if (chart.hasData() && (chartProcessor != null)) {
            if (chart.is1D()) {
                String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", ph0, ph1, delImagString);
                if (chart.processorController != null) {
                    setPhaseOp(opString);
                }
                chart.setPh0(0.0);
                chart.setPh1(0.0);
                chart.layoutPlotChildren();
            } else if (phaseDim.equals(chartProcessor.getVecDimName().substring(1))) {
                //double newph0 = ph0 + chart.getDataPH0();
                //double newph1 = ph1 + chart.getDataPH1();
                double newph0 = ph0;
                double newph1 = ph1;
                double deltaPH0 = ph0 - chart.getDataPH0();
                double deltaPH1 = ph1 - chart.getDataPH1();

                String opString = String.format("PHASE(ph0=%.1f,ph1=%.1f,dimag=%s)", newph0, newph1, delImagString);
                if (chart.processorController != null) {
                    setPhaseOp(opString);
                }
                //chart.setPh0(ph0);
                //chart.setPh1(ph1);
                chart.setPh0(deltaPH0);
                chart.setPh1(deltaPH1);
                chart.getCrossHairs().refreshCrossHairs();
            }
        }
    }

    @FXML
    private void getPhaseOp(ActionEvent event) {
        getPhaseOp();
    }

    protected void getPhaseOp() {
        PolyChart chart = getActiveChart();
        double ph0 = 0.0;
        double ph1 = 0.0;
        if (!chart.hasData()) {
            return;
        }
        String phaseDim = "D" + String.valueOf(chart.phaseDim + 1);
        if (chartProcessor != null) {
            List<String> listItems = chartProcessor.getOperations(phaseDim);
            if (listItems != null) {
                Map<String, String> values = null;
                for (String s : listItems) {
                    if (s.contains("PHASE")) {
                        values = PropertyManager.parseOpString(s);
                    }
                }
                if (values != null) {
                    try {
                        if (values.containsKey("ph0")) {
                            String value = values.get("ph0");
                            ph0 = Double.parseDouble(value);
                        } else {
                            ph0 = 0.0;
                        }
                        if (values.containsKey("ph1")) {
                            String value = values.get("ph1");
                            ph1 = Double.parseDouble(value);
                        } else {
                            ph1 = 0.0;
                        }
                        if (values.containsKey("dimag")) {
                            String value = values.get("dimag");
                            delImagString = value;
                        } else {
                            delImagString = "False";
                        }
                    } catch (NumberFormatException nfE) {
                    }
                }
            }
        }
        setPH1Slider(ph1);
        setPH0Slider(ph0);
        chart.setPh0(0.0);
        chart.setPh1(0.0);
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
        activeController = this;
        if (specAttrWindowController != null) {
            specAttrWindowController.update();
        }
    }

    public static FXMLController getActiveController() {
        return activeController;
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
                            ppms[i] = Double.valueOf(peakDim.getChemShiftValue());
                            widths[i] = widthScale * Double.valueOf(peakDim.getLineWidthValue());
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
    public void refreshPeakListView(PeakList peakList
    ) {
    }

    class ChartLabel extends Label {

        PolyChart chart;

    }

    @Override
    public void initialize(URL url, ResourceBundle rb
    ) {
        rightBox.getChildren().remove(phaserBox);
        borderPane.setLeft(null);
        if (!MainApp.isMac()) {
            MenuBar menuBar = MainApp.getMenuBar();
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

//        PolyChart chart2 = new PolyChart();
//        charts.add(chart2);
//        chart2.setController(this);
        chartGroup = new FractionCanvas(this, canvas, charts);
        LayoutControlCanvas layoutControl = new LayoutControlCanvas(chartGroup);
        chartGroup.setControlPane(layoutControl);
        chartPane.getChildren().addAll(chartGroup, plotContent, layoutControl);
        layoutControl.setVisible(false);
        chartGroup.getChildren().addAll(canvas, peakCanvas, annoCanvas);
        chartGroup.setManaged(true);
        layoutControl.setManaged(true);

        controllers.add(this);
//        l.layoutBoundsProperty().addListener(e -> boundsUpdated(l));
//        l2.layoutBoundsProperty().addListener(e -> boundsUpdated(l2));
        statusBar.setMode(0);
        if (consoleController == null) {
            consoleController = ConsoleController.create();
        }
        activeController = this;
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                crossHairStates[iCross][jOrient] = true;
            }
        }
    }

    public boolean getCrossHairState(int iCross, int jOrient) {
        return crossHairStates[iCross][jOrient];
    }

    public ChartLabel getLabel(PolyChart chart, String color, String id) {
        ChartLabel label = new ChartLabel();
        label.textProperty().set(id);
        label.textAlignmentProperty().set(TextAlignment.CENTER);
        label.alignmentProperty().set(Pos.CENTER);
        label.setOpacity(1.0);
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 16d));
        label.setStyle("-fx-background-color: " + color
                + ";-fx-alignment:center;-fx-text-alignment:center;");
        label.setManaged(false);
        label.chart = chart;

        return label;
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
            Scene scene = new Scene((Pane) loader.load());
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

    void initToolBar(ToolBar toolBar) {
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
        buttons.add(new Separator(Orientation.VERTICAL));

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
        bButton.setOnAction(e -> getActiveChart().full());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH, "Expand", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> getActiveChart().expand());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.SEARCH_MINUS, "In", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> getActiveChart().zoom(1.2));
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
        bButton.setOnAction(e -> getActiveChart().zoom(0.8));
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
        bButton.setOnAction(e -> getActiveChart().autoScale());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_UP, "Higher", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> getActiveChart().adjustScale(0.8));
        bButton.setOnScroll((ScrollEvent event) -> {
            double x = event.getDeltaX();
            double y = event.getDeltaY();
            if (y < 0.0) {
                getActiveChart().adjustScale(0.9);
            } else {
                getActiveChart().adjustScale(1.1);

            }
        });
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_DOWN, "Lower", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> getActiveChart().adjustScale(1.2));

        bButton.setOnScroll((ScrollEvent event) -> {
            double x = event.getDeltaX();
            double y = event.getDeltaY();
            if (y < 0.0) {
                getActiveChart().adjustScale(0.9);
            } else {
                getActiveChart().adjustScale(1.1);

            }
        });

        buttons.add(bButton);
        buttons.add(new Separator(Orientation.VERTICAL));

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BULLSEYE, "Pick", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> PeakPicking.peakPickActive(this));
        buttons.add(bButton);

        buttons.add(new Separator(Orientation.VERTICAL));

        Image imageIcon = new Image("/images/Icon_NVJ_16.png", true);
        ImageView imageView = new ImageView(imageIcon);
        bButton = new Button("NvJ", imageView);
        bButton.disableProperty().bind(getActiveChart().datasetFileProp.isNull());

        bButton.setOnAction(e -> viewDatasetInNvJAction(e));
        buttons.add(bButton);

        for (Node node : buttons) {
            if (node instanceof Button) {
                node.getStyleClass().add("toolButton");
            }
        }
        toolBar.getItems().addAll(buttons);
        StackPane newWinRect = makeNewWinIcon();
        toolBar.getItems().add(newWinRect);
        newWinRect.setOnMousePressed(e -> chartGroup.mousePressed(e));
        newWinRect.setOnMouseDragged(e -> chartGroup.mouseDrag(e));
        newWinRect.setOnMouseReleased(e -> chartGroup.mouseDragRelease(e, this::addChart));

        statusBar = new SpectrumStatusBar(this);
        statusBar.buildBar(btoolBar);

    }

    public void showPeakNavigator() {
        if (peakNavigator == null) {
            ToolBar navBar = new ToolBar();
            bottomBox.getChildren().add(navBar);
            peakNavigator = PeakNavigator.create(this).onClose(this::removePeakNavigator).showAtoms().initialize(navBar);
            peakNavigator.setPeakList();
            ObservableList<Double> scaleList = FXCollections.observableArrayList(0.0, 2.5, 5.0, 7.5, 10.0, 15.0, 20.0);
            ChoiceBox<Double> scaleBox = new ChoiceBox(scaleList);
            scaleBox.setValue(5.0);
            scaleBox.setOnAction(e -> {
                widthScale = scaleBox.getValue();
                Peak peak = peakNavigator.getPeak();
                if (peak != null) {
                    refreshPeakView(peak);
                }
            });
            navBar.getItems().add(scaleBox);
        }
    }

    public void removePeakNavigator(Object o) {
        if (peakNavigator != null) {
            peakNavigator.removePeakList();
            bottomBox.getChildren().remove(peakNavigator.getToolBar());
            peakNavigator = null;
        }
    }

    public void showPeakSlider() {
        if (peakSlider == null) {
            ToolBar navBar = new ToolBar();
            bottomBox.getChildren().add(navBar);
            peakSlider = new PeakSlider(this, this::removePeakSlider);
            peakSlider.initSlider(navBar);
        }
    }

    public void removePeakSlider(Object o) {
        if (peakSlider != null) {
            bottomBox.getChildren().remove(peakSlider.getToolBar());
            peakSlider = null;
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
    }

    public void removeChart() {
        if (activeChart != null) {
            removeChart(activeChart);
        }
    }

    public void removeChart(PolyChart chart) {
        if (chart != null) {
            chartGroup.removeChild(chart);
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
        chart.setDisable(true);
        // chart.setController(this);
        chartGroup.addChart(chart);
        activeChart = chart;
    }

    public Integer addChart(Integer pos) {
        FractionCanvas.ORIENTATION orient;
        if (pos < 2) {
            orient = FractionCanvas.ORIENTATION.HORIZONTAL;
        } else {
            orient = FractionCanvas.ORIENTATION.VERTICAL;
        }
        PolyChart chart = new PolyChart(this, plotContent, canvas, peakCanvas, annoCanvas);
        chart.setController(this);
        chartGroup.setOrientation(orient, false);
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
            chart.setDisable(state);
        }

    }

    public int arrangeGetRows() {
        return chartGroup.getCurrentRows();
    }

    public int arrangeGetColumns() {
        return chartGroup.getCurrentCols();
    }

    public void arrange(FractionCanvas.ORIENTATION orient) {
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
                    iChart.setDatasetAttr(datasetAttr);
                }
                chart.syncSceneMates();
                chartGroup.layoutChildren();
                for (int i = 0; i < charts.size(); i++) {
                    PolyChart iChart = charts.get(i);
                    iChart.xAxis.setLowerBound(xLower);
                    iChart.xAxis.setUpperBound(xUpper);
                    iChart.yAxis.setLowerBound(yLower);
                    iChart.yAxis.setUpperBound(yUpper);
                    iChart.getCrossHairs().setCrossHairState(true);
                    iChart.refresh();
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
        minimizeBorders = state;
        int nRows = chartGroup.getRows();
        int nCols = chartGroup.getColumns();
        chartGroup.layoutChildren();
    }

    @Override
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
            if (minimizeBorders) {
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
            if (minimizeBorders) {
                double nucScaleX = 1.0;
                double nucScaleY = 1.0;
                if (!chart.getDatasetAttributes().isEmpty()) {
                    DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                    nucScaleX = dataAttr.getDataset().getNucleus(dataAttr.getDim(0)).getFreqRatio();
                    if (dataAttr.nDim > 1) {
                        nucScaleY = dataAttr.getDataset().getNucleus(dataAttr.getDim(1)).getFreqRatio();
                    }
                }
                bordersGrid[4][iCol] = Math.max(bordersGrid[4][iCol], Math.abs(ppmX0 - ppmX1)) * nucScaleX;
                bordersGrid[5][iRow] = Math.max(bordersGrid[5][iRow], Math.abs(ppmY0 - ppmY1)) * nucScaleY;
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

    @Override
    public void redrawChildren() {
        // fixme
//        chartGroup.getChildrenUnmodifiable().stream().map((node) -> (PolyChart) node).forEachOrdered((chart) -> {
//            chart.layoutPlotChildren();
//        });
    }

    public void draw() {
        chartGroup.layoutChildren();
    }

    public void arrange(int nRows) {
        chartGroup.setRows(nRows);
        int nCols = chartGroup.getColumns();
        chartGroup.layoutChildren();
        chartGroup.layoutChildren();
    }

    public void alignCenters() {
        DatasetAttributes activeAttr = (DatasetAttributes) activeChart.datasetAttributesList.get(0);
        // any peak lists created just for alignmnent should be deleted
        PeakList refList = PeakPicking.peakPickActive(activeChart, activeAttr, false, false, "refList");
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
                    PeakList movingList = PeakPicking.peakPickActive(chart, dataAttr, false, false, "movingList");
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

    public void config() {
        ObservableList<DatasetAttributes> datasetAttrList = getActiveChart().getDatasetAttributes();
        datasetAttrList.stream().forEach(d -> {
            Map<String, Object> configMap = d.config();
            System.out.println(configMap);
        });

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

    public void toggleCrossHairState(int iCross, int jOrient) {
        crossHairStates[iCross][jOrient] = !crossHairStates[iCross][jOrient];
        boolean state = crossHairStates[iCross][jOrient];
        for (PolyChart chart : charts) {
            CrossHairs crossHairs = chart.getCrossHairs();
            crossHairs.setCrossHairState(iCross, jOrient, state);
        }
        statusBar.setIconState(iCross, jOrient, state);
    }
}
