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
import org.nmrfx.processor.gui.controls.FractionPane;
import org.nmrfx.processor.gui.controls.LayoutControlPane;
import org.nmrfx.processor.gui.controls.FractionPaneChild;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
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
import javafx.scene.input.ClipboardContent;
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
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
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
    static boolean popOverMode = true;
    static PeakAttrController peakAttrController = null;
    ProcessorController processorController = null;
    ScannerController scannerController = null;
    Stage stage;
    String delImagString = "False";
    boolean isFID = true;

    static FXMLController activeController = null;
    static String docString = null;
    static ObservableList<Dataset> datasetList = FXCollections.observableArrayList();
    static List<FXMLController> controllers = new ArrayList<>();
    static ConsoleController consoleController = null;

    ObservableList<PolyChart> charts = FXCollections.observableArrayList();
    PolyChart activeChart = null;
    SpectrumStatusBar statusBar;
    BooleanProperty sliceStatus = new SimpleBooleanProperty(false);
    File initialDir = null;

    private FractionPane chartGroup;
    private boolean minimizeBorders = false;

    PeakNavigator peakNavigator;
    PeakSlider peakSlider;
    ListView datasetListView = new ListView();

    SimpleObjectProperty<List<Peak>> selPeaks = new SimpleObjectProperty<>();
    UndoManager undoManager = new UndoManager();

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
        PolyChart chart = PolyChart.getActiveChart();
        if (chart != null) {
            activeController = chart.getController();
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

    boolean filterChart(PolyChart chart) {
        return true;
    }

    public void setActiveChart(PolyChart chart) {
        activeChart = chart;
        PolyChart.activeChart = chart;
        if (specAttrWindowController != null) {
            if (specAttrWindowController.isShowing()) {
                specAttrWindowController.setChart(activeChart);
            }
        }
        if (statusBar != null) {
            statusBar.setChart(activeChart);
        }
    }

    public PolyChart getActiveChart() {
        return activeChart;
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
    void openAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(getInitialDirectory());
        fileChooser.setTitle("Open NMR FID/Dataset");
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
                    NMRViewData nvData = (NMRViewData) nmrData;
                    Dataset dataset = nvData.getDataset();
                    addFID(nmrData, clearOps, reload);
                    addDataset(dataset, appendFile, reload);
                } else {
                    addFID(nmrData, clearOps, reload);
                }
            }
            PreferencesController.saveRecentDatasets(filePath);
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
        for (int i = 0; i < nDim; i++) {
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
            statusBar.setMode(maxNDim.getAsInt());
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

    void closeFile(File target) {
        getActiveChart().removeAllDatasets();
        // removeAllDatasets in chart only stops displaying them, so we need to actually close the dataset
        Path path1 = target.toPath();
        for (Dataset dataset : Dataset.datasets()) {
            File file = dataset.getFile();
            if (file != null) {
                try {
                    if (Files.isSameFile(path1, file.toPath())) {
                        dataset.close();
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
    void showSpecAttrAction(ActionEvent event) {
        if (specAttrWindowController == null) {
            if (popOverMode) {
                specAttrWindowController = SpecAttrWindowController.createPane();
            } else {
                specAttrWindowController = SpecAttrWindowController.create();
            }
        }
        if (specAttrWindowController != null) {
            specAttrWindowController.setChart(getActiveChart());
            if (popOverMode) {
                showAttributesPopOver(event);
            } else {
                specAttrWindowController.getStage().show();
                stage.setResizable(true);
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
            attributesPopOver.show(getActiveChart());

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
        if (scannerController == null) {
            scannerController = ScannerController.create(this, stage, getActiveChart());
        }
        if (scannerController != null) {
            scannerController.getStage().show();
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
        double deltaPH0 = 0.0;
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
        deltaPH0 = 0.0;
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
        ph0 = Math.round(ph0 * 10) / 10.0;
        double start = 45.0 * Math.round(ph0 / 45.0) - 90.0;
        double end = start + 180.0;
        ph0Slider.setMin(start);
        ph0Slider.setMax(end);
        ph0Slider.setValue(ph0);
        ph0Label.setText(String.format("%.1f", ph0));
        setPhaseOp();
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
        ph1 = Math.round(ph1 * 10) / 10.0;
        double start = 90.0 * Math.round(ph1 / 90.0) - 180.0;
        double end = start + 360.0;
        ph1Slider.setMin(start);
        ph1Slider.setMax(end);
        ph1Slider.setValue(ph1);
        ph1Label.setText(String.format("%.1f", ph1));
        setPhaseOp();
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
            try {
                getActiveChart().exportVectorGraphics(selectedFile.toString(), "svg");
            } catch (IOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
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
    private void printActionOld(ActionEvent event) {
        System.out.println("Print!");
        PolyChart chart = getActiveChart();
        Set<Printer> printers = Printer.getAllPrinters();
        for (Printer printer : printers) {
            System.out.println(printer.getName());
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null) {
            System.out.println("gotjob");
            Node node = (Node) chart;
            Node oldClip = node.getClip();
            List<Transform> oldTransforms = new ArrayList<>(node.getTransforms());

            Printer printer = job.getPrinter();

            PageLayout pageLayout = printer.createPageLayout(Paper.NA_LETTER, PageOrientation.LANDSCAPE, Printer.MarginType.DEFAULT);
            job.showPageSetupDialog(chart.getScene().getWindow());
            double scaleX = pageLayout.getPrintableWidth() / node.getBoundsInParent().getWidth();
            double scaleY = pageLayout.getPrintableHeight() / node.getBoundsInParent().getHeight();
            node.getTransforms().add(new Scale(scaleX, scaleY));

            boolean doPrint = job.showPrintDialog(chart.getScene().getWindow());
            if (doPrint) {
                boolean success = job.printPage(node);
                if (success) {
                    job.endJob();
                }
            }
            node.getTransforms().clear();
            node.getTransforms().addAll(oldTransforms);
            node.setClip(oldClip);
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
        double start = 45.0 * Math.round(value / 45.0) - 90.0;
        double end = start + 180.0;
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

    void setActiveController() {
        activeController = this;
    }

    public static FXMLController getActiveController() {
        return activeController;
    }

    public SpectrumStatusBar getStatusBar() {
        return statusBar;
    }

    @Override
    public void refreshPeakView(Peak peak) {
        if (peak != null) {
            for (PolyChart chart : charts) {
                if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                    DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                    int cDim = chart.getNDim();
                    int aDim = dataAttr.nDim;
                    Double[] ppms = new Double[cDim];
                    for (int i = 0; i < aDim; i++) {
                        PeakDim peakDim = peak.getPeakDim(dataAttr.getLabel(i));
                        if (peakDim != null) {
                            ppms[i] = Double.valueOf(peakDim.getChemShiftValue());
                        }
                    }
                    chart.moveTo(ppms);
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
        stage = MainApp.getMainStage();
        MainApp.registerStage(stage, this);
        stage.focusedProperty().addListener(e -> setActiveController(e));
        //chartProcessor = new ChartProcessor(chart);
        rightBox.getChildren().remove(phaserBox);
        borderPane.setLeft(null);
        if (!MainApp.isMac()) {
            MenuBar menuBar = MainApp.getMenuBar();
            topBar.getChildren().add(0, menuBar);
        }
        PolyChart chart1 = new PolyChart();
        activeChart = chart1;
        initToolBar(toolBar);
        charts.add(chart1);
        chart1.setController(this);

//        PolyChart chart2 = new PolyChart();
//        charts.add(chart2);
//        chart2.setController(this);
        chartGroup = new FractionPane(this);
        LayoutControlPane layoutControl = new LayoutControlPane(chartGroup);
        chartGroup.setControlPane(layoutControl);
        chartPane.getChildren().addAll(chartGroup, layoutControl);
        layoutControl.setVisible(false);
        chartGroup.getChildren().addAll(chart1);
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

    }

    public ChartLabel getLabel(PolyChart chart, String color, String id) {
        ChartLabel label = new ChartLabel();
        label.textProperty().set(id);
        label.textAlignmentProperty().set(TextAlignment.CENTER);
        label.alignmentProperty().set(Pos.CENTER);
        label.setOpacity(1.0);
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 16d));
        label.setStyle("-fx-background-color: " + color.toString()
                + ";-fx-alignment:center;-fx-text-alignment:center;");
        label.setManaged(false);
        label.chart = chart;

        return label;
    }

    public static FXMLController create() {
        FXMLLoader loader = new FXMLLoader(FXMLController.class.getResource("/fxml/NMRScene.fxml"));
        FXMLController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<FXMLController>getController();
            controller.stage = stage;
            controllers.add(controller);
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
        bButton.setOnAction(e -> openAction(e));
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

    public void linkPeakDims() {
        PeakLinker linker = new PeakLinker();
        linker.linkAllPeakListsByLabel();
    }

    public void setNCharts(int nCharts) {
        int nCurrent = chartGroup.getChildren().size();
        if (nCurrent > nCharts) {
            for (int i = nCurrent - 1; i >= nCharts; i--) {
                PolyChart chart = (PolyChart) chartGroup.getChildren().remove(i);
                charts.remove(chart);
            }
        } else if (nCharts > nCurrent) {
            int nNew = nCharts - nCurrent;
            for (int i = 0; i < nNew; i++) {
                addChart(1);
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
                refreshChart.requestLayout();
                refreshChart.layout();
                refreshChart.layoutPlotChildren();
            }
        }
    }

    public Integer addChart(Integer pos) {
        FractionPane.ORIENTATION orient;
        if (pos < 2) {
            orient = FractionPane.ORIENTATION.HORIZONTAL;
        } else {
            orient = FractionPane.ORIENTATION.VERTICAL;
        }
        PolyChart chart = new PolyChart();
        charts.add(chart);
        chart.setController(this);
        chartGroup.setOrientation(orient, false);
        if ((pos % 2) == 0) {
            chartGroup.getChildren().add(0, chart);
        } else {
            chartGroup.getChildren().add(chart);
        }
        arrange(orient);
        activeChart = chart;

        return 0;
    }

    public int arrangeGetRows() {
        return chartGroup.getRows();
    }

    public int arrangeGetColumns() {
        return chartGroup.getColumns();
    }

    public void arrange(FractionPane.ORIENTATION orient) {
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
                    iChart.getDatasetAttributes().clear();
                    iChart.getDatasetAttributes().add(datasetAttr);
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
                chartGroup.layoutChildren();
                charts.stream().forEach(c -> c.refresh());
                return;
            }
        }
        chartGroup.setOrientation(orient, true);
        chartGroup.layoutChildren();
    }

    public void overlay() {
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
    }

    public void setBorderState(boolean state) {
        minimizeBorders = state;
        int nRows = chartGroup.getRows();
        int nCols = chartGroup.getColumns();
        chartGroup.layoutChildren();
    }

    public void prepareChildren(int nRows, int nCols) {
        int iChild = 0;
        double xMax = 0;
        double yMax = 0;
        for (Node node : chartGroup.getChildrenUnmodifiable()) {
            int iRow = iChild / nCols;
            int iCol = iChild % nCols;
            PolyChart chart = (PolyChart) node;
            if (minimizeBorders) {
                chart.setAxisState(iCol == 0, iRow == (nRows - 1));
                xMax = Math.max(xMax, chart.yAxis.getWidth());
                yMax = Math.max(yMax, chart.xAxis.getHeight());
            } else {
                chart.setAxisState(true, true);
            }
            iChild++;
        }
        if (nRows == 1) {
            yMax = 0.0;
        }
        if (nCols == 1) {
            xMax = 0.0;
        }
        chartGroup.setExtraOnLeft(xMax);
        chartGroup.setExtraOnBottom(yMax);

    }

    public void redrawChildren() {

        chartGroup.getChildrenUnmodifiable().stream().map((node) -> (PolyChart) node).forEachOrdered((chart) -> {
            chart.layoutPlotChildren();
        });
    }

    public void arrange(int nRows) {
        chartGroup.setRows(nRows);
        int nCols = chartGroup.getColumns();
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
        refList.clusterPeaks();
        int[] dims = activeAttr.dim.clone();

        for (int i = 2; i < dims.length; i++) {
            dims[i] = -1;
        }
        System.out.println(refList.getName());
        for (PolyChart chart : charts) {
            ObservableList<DatasetAttributes> dataAttrList = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrList) {
                if (dataAttr != activeAttr) {
                    PeakList movingList = PeakPicking.peakPickActive(activeChart, dataAttr, false, false, "movingList");
                    movingList.unLinkPeaks();
                    movingList.clearSearchDims();
                    movingList.addSearchDim(dimName1, 0.05);
                    movingList.addSearchDim(dimName2, 0.1);
                    movingList.clusterPeaks();

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
}
