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
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.skin.TitledPaneSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ExceptionDialog;
import org.greenrobot.eventbus.EventBus;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.events.DatasetSavedEvent;
import org.nmrfx.processor.gui.spectra.SpecRegion;
import org.nmrfx.processor.gui.utils.ModifiableAccordionScrollPane;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.processing.*;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utilities.ProgressUpdater;
import org.nmrfx.utils.FormatUtils;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.OperationItem;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ProcessorController implements Initializable, ProgressUpdater, NmrControlRightSideContent {
    private static final Logger log = LoggerFactory.getLogger(ProcessorController.class);
    private static final String[] BASIC_OPS = {"APODIZE(lb=0.5) ZF FT", "SB ZF FT", "SB(c=0.5) ZF FT", "VECREF GEN"};
    private static final String[] COMMON_OPS = {"APODIZE", "SUPPRESS", "ZF", "FT", "AUTOPHASE", "EXTRACTP", "AutoCorrect Baseline"};
    private static final AtomicBoolean aListUpdated = new AtomicBoolean(false);
    public static final int GROUP_SCALE = 1000;

    private enum DisplayMode {
        FID("FID"),
        FID_OPS("FID w/ OPs"),
        SPECTRUM("Spectrum");
        private final String strValue;

        DisplayMode(String strValue) {
            this.strValue = strValue;
        }

        @Override
        public String toString() {
            return this.strValue;
        }
    }

    NmrControlRightSidePane nmrControlRightSidePane;
    @FXML
    private BorderPane mainBox;
    @FXML
    private TextField opTextField;

    @FXML
    private ChoiceBox<String> dimChoice;
    @FXML
    private MenuItem autoGenerateScript;
    @FXML
    private MenuItem autoGenerateArrayedScript;
    @FXML
    private MenuItem openDefaultScript;
    @FXML
    private MenuItem openScript;
    @FXML
    private MenuItem saveScript;
    @FXML
    private MenuItem saveScriptAs;
    @FXML
    private MenuItem openOperations;
    @FXML
    private MenuItem saveOperations;
    @FXML
    private Accordion dimAccordion;
    @FXML
    private ModifiableAccordionScrollPane accordion;
    @FXML
    ToolBar opBox;
    @FXML
    private StatusBar statusBar;
    private final Circle statusCircle = new Circle(10.0, Color.GREEN);

    EventHandler<ActionEvent> menuHandler;
    PopOver popOver = new PopOver();

    ChangeListener<String> dimListener;
    ChangeListener<Number> refDimListener;

    PropertyManager propertyManager;
    RefManager refManager;

    // script tab fields
    @FXML
    CheckBox autoProcess;

    @FXML
    private ChoiceBox<DisplayMode> viewMode;
    @FXML
    private Button processDatasetButton;
    @FXML
    private Button haltProcessButton;
    @FXML
    ToggleButton detailButton;
    @FXML
    private Button opDocButton;
    @FXML

    Map<String, TitledPane> dimensionPanes = new HashMap<>();
    ObservableMap<String, List<ProcessingOperationInterface>> mapOpLists;
    String currentDimName = "";
    TitledPane referencePane;
    NavigatorGUI navigatorGUI;
    private Button datasetFileButton = new Button("File...");

    CheckBox genLSCatalog;
    TextField nLSCatFracField;
    TextField[][] lsTextFields;
    ChartProcessor chartProcessor;
    DocWindowController dwc = null;
    PolyChart chart;
    private final AtomicBoolean idleMode = new AtomicBoolean(false);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean doProcessWhenDone = new AtomicBoolean(false);
    private final AtomicBoolean isPhaserActive = new AtomicBoolean(false);
    private final ProcessDataset processDataset = new ProcessDataset();
    MapChangeListener<String, List<ProcessingOperationInterface>> opListListener = null;

    final ReadOnlyObjectProperty<Worker.State> stateProperty = processDataset.worker.stateProperty();
    private final ObjectProperty<Boolean> processorAvailable = new SimpleObjectProperty<>();
    private final ProcessorAvailableStatusListener processorAvailableStatusListener = this::processorAvailableStatusUpdated;
    Throwable processingThrowable;
    String currentText = "";

    private final ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);
    private AtomicBoolean needToFireEvent = new AtomicBoolean(false);
    private final AtomicReference<Dataset> saveObject = new AtomicReference<>();
    ScheduledFuture futureUpdate = null;
    Map<String, PhaserAndPane> phasersPanes = new HashMap<>();
    ScriptGUI scriptGUI = new ScriptGUI();


    public static ProcessorController create(FXMLController fxmlController, NmrControlRightSidePane nmrControlRightSidePane, PolyChart chart) {
        Fxml.Builder builder = Fxml.load(ProcessorController.class, "ProcessorScene.fxml");
        ProcessorController controller = builder.getController();

        controller.chart = chart;
        chart.setProcessorController(controller);
        controller.chartProcessor.setChart(chart);
        controller.chartProcessor.setFxmlController(fxmlController);
        controller.nmrControlRightSidePane = nmrControlRightSidePane;
        fxmlController.processorCreated(controller.mainBox);
        nmrControlRightSidePane.addContent(controller);
        if (chart.getDataset() == null) {
            controller.createSimulatorAccordion();
            controller.viewMode.setValue(DisplayMode.FID_OPS);
        }
        controller.navigatorGUI = NavigatorGUI.create(controller);

        return controller;
    }

    public Pane getPane() {
        return mainBox;
    }

    public boolean isVisible() {
        return mainBox.isVisible();
    }

    @FXML
    public void showNavigator() {
        if (viewMode.getValue() == DisplayMode.SPECTRUM) {
            viewMode.setValue(DisplayMode.FID_OPS);
        }
        navigatorGUI.showStage();
    }

    public PropertyManager getPropertyManager() {
        return propertyManager;
    }

    protected void clearOperationList() {
        if (mapOpLists.containsKey(currentDimName)) {
            mapOpLists.get(currentDimName).clear();
        }
    }

    public List<ProcessingOperationInterface> getOperationList() {
        if (currentDimName.isBlank()) {
            return Collections.emptyList();
        } else {
            return mapOpLists.computeIfAbsent(currentDimName, k -> new ArrayList<>());
        }
    }

    protected String getFullScript() {
        return chartProcessor.buildScript();
    }

    class UpdateTask implements Runnable {
        @Override
        public void run() {
            if (aListUpdated.get()) {
                needToFireEvent.set(true);
                aListUpdated.set(false);
                startTimer();
            } else if (needToFireEvent.get()) {
                needToFireEvent.set(false);
                Fx.runOnFxThread(() -> saveDataset(saveObject.getAndSet(null)));
                if (aListUpdated.get()) {
                    startTimer();
                }
            }
        }
    }

    synchronized void startTimer() {
        if (schedExecutor != null) {
            if (needToFireEvent.get() || (futureUpdate == null) || futureUpdate.isDone()) {
                UpdateTask updateTask = new UpdateTask();
                futureUpdate = schedExecutor.schedule(updateTask, 2000, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void updateProgress(double f) {
        Fx.runOnFxThread(() -> statusBar.setProgress(f));
    }

    public void updateStatus(String s) {
        Fx.runOnFxThread(() -> setProcessingStatus(s, true));
    }

    void updateFileButton() {
        if (chartProcessor.getDatasetType() == DatasetType.SPINit) {
            datasetFileButton.setText("Next ProcNum");
        } else {
            datasetFileButton.setText("File...");
        }
    }

    private void setActivePane(String name, TitledPane titledPane) {
        if (!titledPane.isExpanded() && (name.equals(currentDimName))) {
            currentDimName = "";
        } else if (titledPane.isExpanded() && !currentDimName.equals(name)) {
            currentDimName = name;
            if (name.equals("D1-REF")) {
                currentDimName = "D1";
            }
            if (!currentDimName.isBlank()) {
                if (dimensionPanes.containsKey(name)) {
                    accordion = (ModifiableAccordionScrollPane) dimensionPanes.get(currentDimName).getContent();
                }
                if (currentDimName.charAt(0) == 'D' && StringUtils.isNumeric(currentDimName.substring(1))) {
                    dimChoice.setValue(currentDimName);
                    updatePhaser();
                    chartProcessor.setVecDim(currentDimName);
                    if (!isViewingDataset()) {
                        chartProcessor.execScriptList(false);
                        chart.full();
                        chart.autoScale();
                    }
                }
            }
        }
    }

    private void addTitleBar(TitledPane titledPane, String name, boolean addMenu) {
        titledPane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        titledPane.setGraphicTextGap(0);
        titledPane.setSkin(new ButtonTitlePaneSkin(titledPane));

        HBox titleBox = new HBox();
        titleBox.setPadding(new Insets(0, 5, 0, 5));
        //titleBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        // Create Title
        Label label = new Label(titledPane.getText());
        label.textProperty().bind(titledPane.textProperty());
        label.textFillProperty().bind(titledPane.textFillProperty());

        titleBox.getChildren().add(label);
        // Create spacer to separate label and buttons
        Pane spacer = ToolBarUtils.makeFiller(100);
        titleBox.getChildren().add(spacer);
        if (addMenu) {
            MenuButton menuButton = new MenuButton("");
            menuButton.setGraphic(GlyphsDude.createIcon(FontAwesomeIcon.PLUS, "10"));
            menuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            if (name.equals("FULL DATASET")) {
                menuButton.getItems().addAll(getMenuItemsForDataset());
            } else if (name.startsWith("POLISHING")) {
                menuButton.getItems().addAll(getMenuItemsForPolishingt());
            } else {
                menuButton.getItems().addAll(getMenuItems());
            }
            titleBox.getChildren().addAll(menuButton);
            menuButton.setDisable(true);
            menuButton.disableProperty().bind(titledPane.expandedProperty().not());
        }
        titledPane.setGraphic(titleBox);
    }

    private static class ButtonTitlePaneSkin extends TitledPaneSkin {
        final Region arrow;

        ButtonTitlePaneSkin(final TitledPane titledPane) {
            super(titledPane);
            arrow = (Region) getSkinnable().lookup(".arrow-button");

        }

        @Override
        protected void layoutChildren(final double x, final double y, final double w, final double h) {
            super.layoutChildren(x, y, w, h);
            double arrowWidth = arrow.getLayoutBounds().getWidth();
            double arrowPadding = arrow.getPadding().getLeft() + arrow.getPadding().getRight();

            ((Region) getSkinnable().getGraphic()).setMinWidth(w - (arrowWidth + arrowPadding));
        }
    }

    private TitledPane addTitlePane(String name, String title) {
        TitledPane titledPane = new TitledPane();
        titledPane.expandedProperty().addListener(c -> setActivePane(name, titledPane));
        titledPane.setText(title);
        addTitleBar(titledPane, title, true);
        ModifiableAccordionScrollPane accordion1 = new ModifiableAccordionScrollPane();
        titledPane.setContent(accordion1);
        dimensionPanes.put(name, titledPane);
        dimAccordion.getPanes().add(titledPane);
        return titledPane;

    }

    protected void createSimulatorAccordion() {
        dimChoice.getSelectionModel().selectedItemProperty().removeListener(dimListener);
        dimensionPanes.clear();
        dimAccordion.getPanes().clear();
        currentDimName = "D" + 1;
        var titledPane = addTitlePane(currentDimName, "SIMULATION");
        titledPane.setExpanded(true);
        accordion = (ModifiableAccordionScrollPane) dimensionPanes.get(currentDimName).getContent();
    }

    protected void updateDimChoice(boolean[] complex) {
        dimensionPanes.clear();
        dimAccordion.getPanes().clear();
        int nDim = complex.length;
        dimAccordion.getPanes().add(referencePane);
        referencePane.expandedProperty().addListener(c -> setActivePane("D1-REF", referencePane));

        refManager.updateReferencePane(getNMRData(), nDim);
        dimChoice.getSelectionModel().selectedItemProperty().removeListener(dimListener);
        ObservableList<String> dimList = FXCollections.observableArrayList();
        for (int i = 1; i <= nDim; i++) {
            addTitlePane("D" + i, "DIMENSION " + i);
            dimList.add("D" + i);
            if ((i == 1) && (nDim > 2)) {
                StringBuilder sBuilder = new StringBuilder();
                sBuilder.append("D2");
                for (int j = 3; j <= nDim; j++) {
                    sBuilder.append(",");
                    sBuilder.append(j);
                }
                addTitlePane(sBuilder.toString(), "INDIRECT MATRIX");
                dimList.add(sBuilder.toString());
            }
        }
        if (nDim > 1) {
            addTitlePane("D_ALL", "FULL DATASET");

            for (int i = 1; i <= nDim; i++) {
                addTitlePane("P" + i, "DIMENSION " + i + " (post processing)");
            }
        }
        currentDimName = "D" + 1;
        accordion = (ModifiableAccordionScrollPane) dimensionPanes.get(currentDimName).getContent();
        dimChoice.setItems(dimList);
        dimChoice.getSelectionModel().select(0);
        dimChoice.getSelectionModel().selectedItemProperty().addListener(dimListener);

        navigatorGUI.updateVecNumChoice(complex);

        updateLineshapeCatalog(nDim);
    }

    public Optional<String> getActiveDimPane() {
        return dimensionPanes.entrySet().stream().filter(e -> e.getValue().isExpanded()).map(Map.Entry::getKey).findFirst();
    }

    protected void updateLineshapeCatalog(int nDim) {
        NMRData nmrData = null;
        if (chartProcessor != null) {
            nmrData = chartProcessor.getNMRData();
        }

        BorderPane borderPane = new BorderPane();
        HBox topBox = new HBox();
        genLSCatalog = new CheckBox("Generate");
        Label nFracLabel = new Label("nFrac:");
        nFracLabel.setPrefWidth(70);
        nLSCatFracField = new TextField("2");
        nLSCatFracField.setPrefWidth(40);
        topBox.getChildren().addAll(genLSCatalog, nFracLabel, nLSCatFracField);
        GridPane gridPane = new GridPane();
        gridPane.add(new Label("Dim"), 0, 0);
        gridPane.add(new Label("LwMin"), 1, 0);
        gridPane.add(new Label("LwMax"), 2, 0);
        gridPane.add(new Label("NLw"), 3, 0);
        gridPane.add(new Label("NPts"), 4, 0);
        lsTextFields = new TextField[nDim][4];
        int[] widths = {60, 60, 40, 40};
        for (int i = 0; i < nDim; i++) {
            Label label = new Label(String.valueOf(i + 1));
            label.setPrefWidth(30);
            gridPane.add(label, 0, i + 1);
            for (int iCol = 0; iCol < 4; iCol++) {
                lsTextFields[i][iCol] = new TextField();
                lsTextFields[i][iCol].setPrefWidth(widths[iCol]);
                gridPane.add(lsTextFields[i][iCol], iCol + 1, i + 1);
            }
            if (nmrData != null) {
                int size = nmrData.getSize(i);
                double sw = nmrData.getSW(i);
                double res = 2.0 * sw / size;
                double lwMin = res / 4;
                double lwMax = res * 2;
                lsTextFields[i][0].setText(String.format("%.0f", lwMin));
                lsTextFields[i][1].setText(String.format("%.0f", lwMax));
                lsTextFields[i][2].setText("30");
                lsTextFields[i][3].setText("64");
            }
        }
        borderPane.setTop(topBox);
        borderPane.setCenter(gridPane);
    }

    String getLSScript() {
        StringBuilder sBuilder = new StringBuilder();
        if (genLSCatalog.isSelected()) {
            boolean ok = true;
            //genLSCatalog(lw, nLw, nKeep, 2)
            for (TextField[] lsTextField : lsTextFields) {
                for (int j = 0; j < 2; j++) {
                    try {
                        Double.parseDouble(lsTextField[j].getText());
                    } catch (NumberFormatException nfE) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) {
                sBuilder.append("genLSCatalog(");
                for (int j = 0; j < lsTextFields[0].length; j++) {
                    if (j != 0) {
                        sBuilder.append(",");
                    }
                    sBuilder.append("[");
                    for (int i = 0; i < lsTextFields.length; i++) {
                        if (i != 0) {
                            sBuilder.append(",");
                        }
                        sBuilder.append(lsTextFields[i][j].getText());
                    }
                    sBuilder.append("]");
                }
                sBuilder.append(",");

                sBuilder.append(nLSCatFracField.getText()).append(")");
                sBuilder.append("\n");

            }
        }
        return sBuilder.toString();
    }

    @FXML
    void viewMode() {
        if (viewMode.getValue() == DisplayMode.SPECTRUM) {
            if (chart.getFXMLController().isFIDActive()) {
                viewDatasetInApp(null);
            }
        } else if (viewMode.getValue() == DisplayMode.FID_OPS) {
            viewFID();
        } else if (viewMode.getValue() == DisplayMode.FID) {
            viewRawFID();
        }
    }

    @FXML
    public void viewDatasetInApp(Dataset dataset) {
        Dataset currentDataset = (Dataset) chart.getDataset();
        if (dataset != null) {
            chart.getFXMLController().addDataset(dataset, false, false);
            if ((currentDataset != null) && (currentDataset != dataset)) {
                currentDataset.close();
            }
        } else {
            if (chartProcessor.getDatasetFile() != null) {
                if (currentDataset != null) {
                    currentDataset.close();
                }
                boolean viewingDataset = isViewingDataset();
                chart.getFXMLController().openDataset(chartProcessor.getDatasetFile(), false, true);
                viewMode.setValue(DisplayMode.SPECTRUM);
                if (!viewingDataset) {
                    chart.full();
                    chart.autoScale();
                }
            }
        }
    }

    void viewingDataset(boolean state) {
        if (state) {
            viewMode.setValue(DisplayMode.SPECTRUM);
        } else {
            viewMode.setValue(DisplayMode.FID_OPS);
        }
    }

    public boolean isViewingDataset() {
        return viewMode.getValue() == DisplayMode.SPECTRUM;
    }

    public boolean isViewingFID() {
        return viewMode.getValue() == DisplayMode.FID_OPS;
    }

    @FXML
    void viewFID() {
        if (getNMRData() != null) {
            dimChoice.getSelectionModel().select(0);
            if (currentDimName.isBlank()) {
                currentDimName = "D1";
            }
            chartProcessor.setVecDim(currentDimName);
            viewMode.setValue(DisplayMode.FID_OPS);
            chart.getFXMLController().getUndoManager().clear();
            chart.getFXMLController().updateSpectrumStatusBarOptions(false);
            if (!isViewingDataset()) {
                chartProcessor.execScriptList(false);
                chart.full();
                chart.autoScale();
            }
        }
    }

    @FXML
    void viewRawFID() {
        dimChoice.getSelectionModel().select(0);
        chartProcessor.setVecDim("D1");
        viewMode.setValue(DisplayMode.FID);
        chart.getFXMLController().getUndoManager().clear();
        chart.getFXMLController().updateSpectrumStatusBarOptions(false);
        chartProcessor.execScript("", true, false);
        chart.full();
        chart.autoScale();
    }

    public String getScript() {
        StringBuilder script = new StringBuilder();
        for (var processingOp : getOperationList()) {
            if (processingOp instanceof ProcessingOperation op) {
                script.append(op);
                script.append("\n");
            } else if (processingOp instanceof ProcessingOperationGroup groupOp) {
                if (!groupOp.isDisabled()) {
                    for (var op : groupOp.getProcessingOperationList()) {
                        if (!op.isDisabled()) {
                            script.append(op);
                            script.append("\n");
                        }
                    }
                }
            }
        }
        return script.toString();
    }

    public void deleteItem(int index) {
        if (!getOperationList().isEmpty()) {

            /*
              If we are deleting the last element, select the previous,
              else select the next element. If this is the first element,
              then unselect the scriptView.
             */
            propertyManager.removeScriptListener();
            removeOpListener();

            try {
                getOperationList().remove(index);
            } catch (Exception ex) {
                log.warn(ex.getMessage(), ex);
            } finally {
                propertyManager.addScriptListener();
                addOpListener();
                chartProcessor.updateOpList();
            }
            updateAccordionList();
            if (getOperationList().isEmpty()) {
                if (!chartProcessor.areOperationListsValidProperty().get()) {
                    String currentDatasetName = chart.getDataset().getName();
                    haltProcessAction();
                    // Set available as halt may happen before code flow reaches the Processor
                    Processor.getProcessor().setProcessorAvailableStatus(true);
                    chartProcessor.reloadData();
                    chartProcessor.setDatasetFile(null);
                    viewingDataset(false);
                    ProjectBase.getActive().removeDataset(currentDatasetName);
                    chart.refresh();
                }
            }

            chartProcessor.execScript(getScript(), true, false);
            chart.layoutPlotChildren();
        }
    }

    @FXML
    void handleOpKey(KeyEvent event) {
        if (event.getCode() != KeyCode.ESCAPE) {
            TextField textField = (TextField) event.getSource();
            String opString = textField.getText();
            Text text = ((Text) popOver.getContentNode());
            List<String> opCandidates = OperationInfo.getOps(opString);
            StringBuilder opStrings = new StringBuilder();
            for (String opCandidate : opCandidates) {
                opStrings.append(opCandidate);
                opStrings.append("\n");
            }
            text.setText(opStrings.toString());
            if (opString.length() == 0) {
                popOver.hide();
            } else if (!popOver.isShowing()) {
                final Point2D nodeCoord = textField.localToScreen(textField.getLayoutBounds().getMaxX(), textField.getLayoutBounds().getMaxY());
                popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_LEFT);
                popOver.setAnchorLocation(PopOver.AnchorLocation.WINDOW_BOTTOM_RIGHT);
                popOver.show(textField, nodeCoord.getX(), nodeCoord.getY());
            }
        }
    }

    private boolean opPresent(String op) {
        String trimOp = OperationInfo.trimOp(op);
        return accordion.getPanes().stream().anyMatch(p -> p.getText().equals(trimOp));
    }

    public boolean detailSelected() {
        return detailButton.isSelected();
    }

    private void updateTitledPane(ModifiableAccordionScrollPane.ModifiableTitlePane titledPane, ProcessingOperation op) {
        titledPane.setProcessingOperation(op);
        titledPane.setDetailedTitle(detailButton.isSelected());
        PropertySheet opPropertySheet = (PropertySheet) titledPane.getProperties().get("PropSheet");
        if (opPropertySheet != null) {
            opPropertySheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory(this));
            opPropertySheet.setMode(PropertySheet.Mode.NAME);
            opPropertySheet.setModeSwitcherVisible(false);
            opPropertySheet.setSearchBoxVisible(false);
            setPropSheet(titledPane, opPropertySheet, op);
        }
        titledPane.getCheckBoxSelectedProperty().addListener(e -> updateActiveState(e, op));
    }

    private void titlePaneExpanded(ModifiableAccordionScrollPane.ModifiableTitlePane titledPane, ProcessingOperationInterface processingOperation) {
        if (titledPane.isExpanded()) {
            titledPane.setActive(true);
            processingOperation.disabled(false);
        }
    }

    private ModifiableAccordionScrollPane.ModifiableTitlePane newTitledPane(ModifiableAccordionScrollPane opAccordion, ProcessingOperationInterface op, int index) {
        final ModifiableAccordionScrollPane.ModifiableTitlePane titledPane;
        if (op instanceof ProcessingOperation processingOperation) {
            titledPane = opAccordion.makeNewTitlePane(this, processingOperation);
            VBox vBox = new VBox();
            HBox hBox = new HBox();
            if (op.getName().equals("PHASE")) {
                Pane phaserPane = makePhaserPane(titledPane, processingOperation);
                vBox.getChildren().add(phaserPane);
            } else {
                hBox.setSpacing(10);
                PropertySheet opPropertySheet = new PropertySheet();
                if (op.getName().equals("EXTRACTP")) {
                    makeExtractButtons(opPropertySheet, hBox, processingOperation);
                } else if (op.getName().equals("REGIONS")) {
                    makeRegionButtons(opPropertySheet, hBox, processingOperation);
                }
                vBox.getChildren().addAll(hBox, opPropertySheet);
                titledPane.getProperties().put("PropSheet", opPropertySheet);
                opPropertySheet.getProperties().put("Op", processingOperation);
            }
            titledPane.setContent(vBox);
            updateTitledPane(titledPane, processingOperation);
            opAccordion.add(titledPane);
            titledPane.setIndex(index);
            titledPane.expandedProperty().addListener(e -> titlePaneExpanded(titledPane, processingOperation));

        } else {
            ProcessingOperationGroup group = (ProcessingOperationGroup) op;
            ModifiableAccordionScrollPane groupAccordion = new ModifiableAccordionScrollPane();
            titledPane = opAccordion.makeNewTitlePane(this, group);
            titledPane.setIndex(index);
            VBox vBox = new VBox();
            vBox.getChildren().addAll(groupAccordion);
            titledPane.setContent(vBox);
            titledPane.setText(group.getTitle(false));
            titledPane.getCheckBoxSelectedProperty().addListener(e -> updateActiveState(e, op));
            opAccordion.add(titledPane);
            updateGroupAccordion(groupAccordion, group, index);
            titledPane.expandedProperty().addListener(e -> titlePaneExpanded(titledPane, op));

        }
        return titledPane;
    }

    public void updateGroupAccordion(
            ModifiableAccordionScrollPane groupAccordion,
            ProcessingOperationGroup group, int index) {
        groupAccordion.getPanes().clear();
        index *= GROUP_SCALE;
        for (var groupedOp : group.getProcessingOperationList()) {
            ModifiableAccordionScrollPane.ModifiableTitlePane pane = newTitledPane(groupAccordion, groupedOp, index);
            pane.setIndex(index++);
        }
    }

    public int getExpandedTitlePane() {
        int index = -1;
        TitledPane activePane = null;
        int i = 0;
        for (var pane : accordion.getPanes()) {
            if (pane.isExpanded()) {
                index = i;
                activePane = pane;
            }
            i++;
        }
        return index;
    }

    public boolean isPhaserActive() {
        return isPhaserActive.get();
    }

    record PhaserAndPane(TitledPane phaserPane, Phaser phaser) {
    }

    private Pane makePhaserPane(ModifiableAccordionScrollPane.ModifiableTitlePane phaserPane, ProcessingOperation processingOperation) {
        VBox phaserBox = new VBox();
        Phaser phaser = new Phaser(chart.getFXMLController(), phaserBox, Orientation.HORIZONTAL, processingOperation);
        PhaserAndPane phaserAndPane = new PhaserAndPane(phaserPane, phaser);
        phasersPanes.put(currentDimName, phaserAndPane);
        phaserPane.expandedProperty().addListener(e -> updatePhaser(phaserPane, phaser));
        return phaserBox;
    }

    void updatePhaser() {
        PhaserAndPane phaserAndPane = phasersPanes.get(currentDimName);
        if (phaserAndPane != null) {
            updatePhaser(phaserAndPane.phaserPane, phaserAndPane.phaser);
        }
    }

    void updatePhaser(TitledPane phaserPane, Phaser phaser) {
        FXMLController fxmlController = chart.getFXMLController();
        if (phaserPane.isExpanded()) {
            Cursor cursor = fxmlController.getCurrentCursor();
            if (cursor == null) {
                cursor = Cursor.MOVE;
            }
            final int vecDim = chartProcessor.getVecDim();
            phaser.setPhaseDim(vecDim);
            phaser.getPhaseOp();
            fxmlController.setPhaser(phaser);
            isPhaserActive.set(true);
            phaser.sliceStatus(fxmlController.sliceStatusProperty().get());
            phaser.cursor(cursor);

            if (!chart.is1D()) {
                fxmlController.sliceStatusProperty().set(true);
                fxmlController.setCursor(Cursor.CROSSHAIR);
                chart.getSliceAttributes().setSlice1State(true);
                chart.getSliceAttributes().setSlice2State(false);
                chart.getCrossHairs().refresh();
            }
        } else {
            isPhaserActive.set(false);
            fxmlController.sliceStatusProperty().set(phaser.sliceStatus);
            fxmlController.setCursor(phaser.cursor());
            fxmlController.setCursor();
            chart.getCrossHairs().refresh();
        }
    }

    void makeExtractButtons(PropertySheet opPropertySheet, HBox hBox, ProcessingOperation processingOperation) {
        Button extractButton = new Button("Add Region");

        extractButton.setOnAction(e -> {
            var regionRangeOpt = chart.addRegionRange(true);
            regionRangeOpt.ifPresent(regionRange -> {
                double ppm0 = regionRange.ppm0();
                double ppm1 = regionRange.ppm1();
                String opString = "EXTRACTP(start=" + ppm0 + ",end=" + ppm1 + ",mode='region')";
                processingOperation.update(opString);
                chartProcessor.updateOpList();
                propertyManager.setPropSheet(opPropertySheet, opString);

            });
        });
        hBox.getChildren().add(extractButton);
    }

    void makeRegionButtons(PropertySheet opPropertySheet, HBox hBox, ProcessingOperation processingOperation) {
        Button addButton = new Button("Add");
        addButton.setOnAction(e -> {
            var regionRangeOpt = chart.addRegionRange(false);
            regionRangeOpt.ifPresent(regionRange -> {
                double f0 = regionRange.f0();
                double f1 = regionRange.f1();
                List<Double> currentRegion = getCurrentRegion(processingOperation);
                String opString = addBaselineRegion(currentRegion, f0, f1, false);
                processingOperation.update(opString);
                chartProcessor.updateOpList();
                propertyManager.setPropSheet(opPropertySheet, opString);
            });
        });
        Button clearRegionButton = new Button("Clear");
        clearRegionButton.setOnAction(e -> {
            var regionRangeOpt = chart.addRegionRange(false);
            regionRangeOpt.ifPresent(regionRange -> {
                double f0 = regionRange.f0();
                double f1 = regionRange.f1();
                List<Double> currentRegion = getCurrentRegion(processingOperation);
                String opString = addBaselineRegion(currentRegion, f0, f1, true);
                processingOperation.update(opString);
                chartProcessor.updateOpList();
                propertyManager.setPropSheet(opPropertySheet, opString);
            });
        });
        Button clearButton = new Button("Clear All");
        clearButton.setOnAction(e -> {
            String opString = clearBaselineRegions();
            processingOperation.update(opString);
            chartProcessor.updateOpList();
            propertyManager.setPropSheet(opPropertySheet, opString);
        });
        hBox.getChildren().addAll(addButton, clearRegionButton, clearButton);
    }

    String addBaselineRegion(List<Double> values, double f1, double f2, boolean clear) {
        TreeSet<SpecRegion> regions = new TreeSet(new SpecRegion());
        for (int i = 0; i < values.size(); i += 2) {
            SpecRegion region = new SpecRegion(values.get(i), values.get(i + 1));
            regions.add(region);
        }

        f1 = Math.round(f1 * 1.0e5) / 1.0e5;
        f2 = Math.round(f2 * 1.0e5) / 1.0e5;
        SpecRegion region = new SpecRegion(f1, f2);
        region.removeOverlapping(regions);
        if (!clear) {
            regions.add(region);
        }

        StringBuilder sBuilder = new StringBuilder();
        boolean first = true;
        for (SpecRegion specRegion : regions) {
            if (!first) {
                sBuilder.append(",");
            } else {
                first = false;
            }
            sBuilder.append(specRegion.getSpecRegionStart(0));
            sBuilder.append(",");
            sBuilder.append(specRegion.getSpecRegionEnd(0));

        }
        return "REGIONS(regions=[" + sBuilder + "])";
    }

    String clearBaselineRegions() {
        return "REGIONS(regions=[])";
    }

    List<Double> getCurrentRegion(ProcessingOperation processingOperation) {
        List<Double> fracs = new ArrayList<>();
        Map<String, ProcessingOperation.OperationParameter> params = processingOperation.getParameterMap();
        if (params.containsKey("regions")) {
            String value = params.get("regions").value();
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
        return fracs;
    }


    void setPropSheet(ModifiableAccordionScrollPane.ModifiableTitlePane titledPane, PropertySheet opPropertySheet, ProcessingOperation op) {
        opPropertySheet.getItems().clear();
        ObservableList<PropertySheet.Item> newItems = FXCollections.observableArrayList();
        propertyManager.setupItem(opPropertySheet, op.getName());
        ObservableList<PropertySheet.Item> propItems = propertyManager.getItems();
        for (PropertySheet.Item item : propItems) {
            if (item == null) {
                System.out.println("item null");
            } else if (!item.getName().equals("disabled")) {
                boolean foundIt = false;
                for (ProcessingOperation.OperationParameter parameter : op.getParameters()) {
                    if (item.getName().equals(parameter.name())) {
                        foundIt = true;
                        ((OperationItem) item).setFromString(parameter.value());
                    }
                }
                if (!foundIt) {
                    ((OperationItem) item).setToDefault();
                }
                newItems.add(item);
            }
        }
        titledPane.setActive(!op.isDisabled());
        opPropertySheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory(this));
        opPropertySheet.setMode(PropertySheet.Mode.NAME);
        opPropertySheet.setModeSwitcherVisible(false);
        opPropertySheet.setSearchBoxVisible(false);
        opPropertySheet.getItems().setAll(newItems);
    }

    private void updateActiveState(Observable e, ProcessingOperationInterface op) {
        BooleanProperty b = (BooleanProperty) e;
        op.disabled(!b.get());
        chartProcessor.updateOpList();
    }

    public void updateAllAccordions() {
        var mapOpList = chartProcessor.getScriptList();
        for (var entry : mapOpList.entrySet()) {
            updateAccordion(entry.getKey(), entry.getValue());
        }
    }

    public void updateAccordion(String name, List<ProcessingOperationInterface> processingOperations) {
        var titledPane = dimensionPanes.get(name);
        var accordionPane = (ModifiableAccordionScrollPane) titledPane.getContent();
        accordionPane.getPanes().clear();
        int i = 0;
        currentDimName = name;
        for (var processingOperation : processingOperations) {
            ModifiableAccordionScrollPane.ModifiableTitlePane pane = newTitledPane(accordionPane, processingOperation, i++);
        }
    }

    private void updateAllAccordionTitles() {
        for (var dimensionPane : dimensionPanes.values()) {
            var content = dimensionPane.getContent();
            if (content instanceof ModifiableAccordionScrollPane accordion) {
                for (var pane : accordion.getPanes()) {
                    if (pane instanceof ModifiableAccordionScrollPane.ModifiableTitlePane titledPane) {
                        titledPane.setDetailedTitle(detailButton.isSelected());
                        if (titledPane.getContent() instanceof VBox vBox) {
                            if (!vBox.getChildren().isEmpty()) {
                                if (vBox.getChildren().get(0) instanceof ModifiableAccordionScrollPane spane) {
                                    for (var pane2 : spane.getPanes()) {
                                        if (pane2 instanceof ModifiableAccordionScrollPane.ModifiableTitlePane titledPane2) {
                                            titledPane2.setDetailedTitle(detailButton.isSelected());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateAccordionTitles() {
        for (var pane : accordion.getPanes()) {
            ModifiableAccordionScrollPane.ModifiableTitlePane mPane = (ModifiableAccordionScrollPane.ModifiableTitlePane) pane;
            mPane.setDetailedTitle(detailButton.isSelected());
        }
    }

    public void updateAccordionList() {
        accordion.getPanes().clear();
        int i = 0;
        for (var processingOperation : getOperationList()) {
            ModifiableAccordionScrollPane.ModifiableTitlePane pane = newTitledPane(accordion, processingOperation, i++);
        }
    }

    @FXML
    private void showOpDoc() {
        if (dwc == null) {
            dwc = new DocWindowController();
        }
        dwc.load();
    }

    private void addOperation(String opName) {
        List<ProcessingOperationInterface> ops = getOperationList();
        if (opName.equals("AutoCorrect Baseline")) {
            opName = "BCWHIT";
        } else if (opName.equals("AutoPhase Dataset")) {
            opName = "DPHASE";
        } else if (opName.equals("APODIZE")) {
            int nDim = 1;
            if (getNMRData() != null) {
                nDim = getNMRData().getNDim();
            }
            if (nDim == 1) {
                opName = "EXPD";
            } else {
                opName = "SB";
            }
        }
        if (ApodizationGroup.opInGroup(opName)) {
            int index = propertyManager.getCurrentPosition(ops, "Apodization");
            ApodizationGroup apodizationGroup = index != -1 ? (ApodizationGroup) ops.get(index) : new ApodizationGroup();
            if (index == -1) {
                propertyManager.addOp(apodizationGroup, ops, index);
            }
            apodizationGroup.update(opName, opName);
        } else if (BaselineGroup.opInGroup(opName)) {
            int index = propertyManager.getCurrentPosition(ops, "Baseline Correction");
            BaselineGroup baselineGroup = index != -1 ? (BaselineGroup) ops.get(index) : new BaselineGroup();
            if (index == -1) {
                propertyManager.addOp(baselineGroup, ops, index);
            }
            baselineGroup.update(opName, opName);
        } else {
            int index = propertyManager.getCurrentPosition(ops, opName);
            ProcessingOperation processingOperation = new ProcessingOperation(opName);
            propertyManager.addOp(processingOperation, ops, index);
        }
    }

    private void opMenuAction(ActionEvent event) {
        MenuItem menuItem = (MenuItem) event.getSource();
        String opName = menuItem.getText();
        getActiveDimPane().ifPresent(dimName -> {
            addOperation(opName);
            updateAfterOperationListChanged();
        });
    }

    private void opSequenceMenuAction(ActionEvent event) {
        getActiveDimPane().ifPresent(dimName -> {
            getOperationList().clear();
            MenuItem menuItem = (MenuItem) event.getSource();
            String[] ops = menuItem.getText().split(" ");
            for (String opName : ops) {
                addOperation(opName);
            }
            updateAfterOperationListChanged();
        });
    }

    @FXML
    private void loadScriptTab() {
        updateScriptDisplay();
    }

    void updateScriptDisplay() {
        String script = getFullScript();
        if (!script.equals(currentText)) {
            scriptGUI.replaceText(script);
            currentText = script;
        }
        chartProcessor.setScriptValid(true);
    }

    boolean fixDatasetName() {
        String script = scriptGUI.getText();
        if (!chartProcessor.scriptHasDataset(script)) {
            Optional<String> scriptOpt = chartProcessor.fixDatasetName(script);
            if (scriptOpt.isEmpty()) {
                return false;
            }
            scriptGUI.replaceText(scriptOpt.get());
        }
        return true;
    }

    void unsetDatasetName() {
        String script = scriptGUI.getText();
        if (chartProcessor.scriptHasDataset(script)) {
            script = chartProcessor.removeDatasetName(script);
            scriptGUI.replaceText(script);
        }
    }

    @FXML
    private void datasetFileAction() {
        unsetDatasetName();
        fixDatasetName();
    }

    @FXML
    private void openDefaultScriptAction() {
        File parent = chartProcessor.getScriptDir();
        if (parent != null) {
            File scriptFile = chartProcessor.getDefaultScriptFile();
            openScript(scriptFile);
        } else {
            openScriptAction();
        }
    }

    @FXML
    private void openScriptAction() {
        File initialDir = chartProcessor.getScriptDir();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Script");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Python Script", "*.py", "py"),
                new FileChooser.ExtensionFilter("Any File", "*")
        );
        if (initialDir != null) {
            fileChooser.setInitialDirectory(initialDir);
        }
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            openScript(selectedFile);
        }
    }

    @FXML
    private void openVecScriptAction() {
        File initialDir = chartProcessor.getScriptDir();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Vector Script");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Python Script", "*.txt", "txt"),
                new FileChooser.ExtensionFilter("Any File", "*")
        );
        if (initialDir != null) {
            fileChooser.setInitialDirectory(initialDir);
        }
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            openVecScript(selectedFile);
        }
    }

    @FXML
    private void writeVecScriptAction() {
        FileChooser fileChooser = new FileChooser();
        File initialDir = chartProcessor.getScriptDir();
        if (initialDir != null) {
            fileChooser.setInitialDirectory(initialDir);
        }
        File saveFile = fileChooser.showSaveDialog(null);
        if (saveFile != null) {
            String script = getScript();
            try {
                chartProcessor.writeScript(script, saveFile);
            } catch (IOException ex) {
                GUIUtils.warn("Write Script Error", ex.getMessage());
            }
        }
    }

    private void openVecScript(File file) {
        String scriptString = null;
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(file.toString()));
            scriptString = new String(encoded);
        } catch (IOException ioe) {
            log.warn("Can't read script. {}", ioe.getMessage(), ioe);
        }
        if (scriptString != null) {
            String[] ops = scriptString.split("\n");
            for (String op : ops) {
                op = op.trim();
                ProcessingOperation processingOperation = new ProcessingOperation(op);
                getActiveDimPane().ifPresent(dimName -> {
                    List<ProcessingOperationInterface> currentOps = chartProcessor.getOperations(dimName);
                    propertyManager.addOp(processingOperation, currentOps, 9999);
                });
            }
        }
    }

    protected void openScript(File file) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(file.toString()));
            String scriptString = new String(encoded);
            parseScript(scriptString);
            chartProcessor.execScriptList(true);
            PolyChartManager.getInstance().getActiveChart().refresh();
        } catch (IOException ioe) {
            log.warn("Can't read script {}", ioe.getMessage(), ioe);
        }
    }

    @FXML
    private void genDefaultScript() {
        String scriptString = chartProcessor.getGenScript(false);
        parseScript(scriptString);
    }

    @FXML
    private void genDefaultScriptArrayed() {
        String scriptString = chartProcessor.getGenScript(true);
        parseScript(scriptString);
    }

    public void parseScript(String scriptString) {
        boolean autoProcessState = autoProcess.isSelected();
        setAutoProcess(false);
        HashSet<String> refOps = new HashSet<>();
        refOps.add("skip");
        refOps.add("sw");
        refOps.add("sf");
        refOps.add("ref");
        refOps.add("label");
        refOps.add("acqOrder");
        refOps.add("acqarray");
        refOps.add("acqmode");
        refOps.add("acqsize");
        refOps.add("tdsize");
        refOps.add("fixdsp");
        if (!scriptString.equals(currentText)) {
            scriptGUI.replaceText(scriptString);
            currentText = scriptString;
        }
        String[] lines = scriptString.split("\n");
        List<String> headerList = new ArrayList<>();
        List<ProcessingOperationInterface> dimList = null;
        Map<String, List<ProcessingOperationInterface>> mapOpLists = new TreeMap<>();

        String dimNum = "";
        ApodizationGroup apodizationGroup = null;
        BaselineGroup baselineGroup = null;
        NUSGroup nusGroup = null;
        for (String line : lines) {
            line = line.trim();
            if (line.equals("")) {
                continue;
            }
            if (line.charAt(0) == '#') {
                continue;
            }
            int index = line.indexOf('(');
            boolean lastIsClosePar = line.charAt(line.length() - 1) == ')';
            if ((index != -1) && lastIsClosePar) {
                String opName = line.substring(0, index);
                String args = line.substring(index + 1, line.length() - 1);
                if (opName.equals("run")) {
                    continue;
                }
                if (opName.equals("DIM")) {
                    String newDim = args;
                    if (newDim.equals("")) {
                        newDim = "_ALL";
                    }
                    if (!newDim.equals(dimNum)) {
                        dimList = new ArrayList<>();
                        String prefix = "D";
                        if (mapOpLists.containsKey("D" + newDim)) {
                            prefix = "P";
                        }
                        mapOpLists.put(prefix + newDim, dimList);
                        dimNum = newDim;
                        apodizationGroup = null;
                        baselineGroup = null;
                        nusGroup = null;
                    }
                } else if (dimList != null) {
                    if (opName.equals("BaselineGroup")) {
                        if (baselineGroup == null) {
                            baselineGroup = new BaselineGroup();
                            dimList.add(baselineGroup);
                        }
                        baselineGroup.update("BCWHIT", "BCWHIT()");
                        baselineGroup.disabled(true);
                    } else if (opName.equals("NUSGroup")) {
                        if (nusGroup == null) {
                            nusGroup = new NUSGroup();
                            dimList.add(nusGroup);
                        }
                        nusGroup.update("NESTA", "NESTA()");
                        nusGroup.disabled(false);
                    } else if (ApodizationGroup.opInGroup(opName)) {
                        if (apodizationGroup == null) {
                            apodizationGroup = new ApodizationGroup();
                            dimList.add(apodizationGroup);
                        }
                        apodizationGroup.update(opName, line);
                    } else if (BaselineGroup.opInGroup(opName)) {
                        if (baselineGroup == null) {
                            baselineGroup = new BaselineGroup();
                            dimList.add(baselineGroup);
                        }
                        baselineGroup.update(opName, line);
                    } else if (NUSGroup.opInGroup(opName)) {
                        if (nusGroup == null) {
                            nusGroup = new NUSGroup();
                            dimList.add(nusGroup);
                        }
                        nusGroup.update(opName, line);
                    } else {
                        dimList.add(new ProcessingOperation(line));
                    }
                } else if (refOps.contains(opName)) {
                    headerList.add(line);
                } else if (opName.equals("markrows")) {
                    navigatorGUI.parseMarkRows(args);
                }
            }
        }
        chartProcessor.setScripts(headerList, mapOpLists);
        String script = getFullScript();
        if (!script.equals(currentText)) {
            scriptGUI.replaceText(script);
            currentText = script;
        }
        chartProcessor.setScriptValid(true);
        navigatorGUI.updateSkipIndices();
        updateAllAccordions();
        setAutoProcess(autoProcessState);
    }

    @FXML
    private void writeDefaultScriptAction() {
        File parent = chartProcessor.getScriptDir();
        if (parent != null) {
            File scriptFile = chartProcessor.getDefaultScriptFile();
            String script = scriptGUI.getText();
            try {
                chartProcessor.writeScript(script, scriptFile);
            } catch (IOException ex) {
                GUIUtils.warn("Write Script Error", ex.getMessage());
            }
        } else {
            writeScriptAction();
        }
    }

    @FXML
    private void writeScriptAction() {
        FileChooser fileChooser = new FileChooser();
        File initialDir = chartProcessor.getScriptDir();
        if (initialDir != null) {
            fileChooser.setInitialDirectory(initialDir);
        }
        File saveFile = fileChooser.showSaveDialog(null);
        if (saveFile != null) {
            String script = scriptGUI.getText();
            try {
                chartProcessor.writeScript(script, saveFile);
            } catch (IOException ex) {
                GUIUtils.warn("Write Script Error", ex.getMessage());
            }
        }
    }

    void finishProcessing(Dataset dataset) {
        Fx.runOnFxThread(() -> finishOnPlatform(dataset));
    }

    void finishOnPlatform(Dataset dataset) {
        if (dataset != null) {
            String newName = dataset.getFile().toString();
            Dataset currentDataset = (Dataset) chart.getDataset();
            if (currentDataset != null) {
                chart.clearDrawlist();
                currentDataset.close();
            }
            try {
                dataset.setFile(new File(newName));
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            setSaveState(dataset);
        }
        viewDatasetInApp(dataset);
    }

    public void saveDataset(Dataset dataset) {
        if ((dataset != null) && (dataset.isMemoryFile()) && dataset.hasDataFile()) {
            try {
                if (dataset.getFileName().endsWith(RS2DData.DATA_FILE_NAME) && (Processor.getProcessor().getNMRData() instanceof RS2DData rs2DData)) {
                    Path procNumPath = rs2DData.saveDataset(dataset);
                    EventBus.getDefault().post(new DatasetSavedEvent(RS2DData.DATASET_TYPE, procNumPath));
                } else {
                    dataset.saveMemoryFile();
                }
                String script = dataset.script();
                if (!script.isBlank()) {
                    try {
                        writeScript(script);
                    } catch (IOException ex) {
                        GUIUtils.warn("Write Script Error", ex.getMessage());
                    }
                }
            } catch (IOException | DatasetException | NullPointerException e) {
                log.error("Couldn't save dataset", e);
            }
        }
    }

    public void setAutoProcess(boolean state) {
        autoProcess.setSelected(state);
    }

    void processIfIdle() {
        if (autoProcess.isSelected()) {
            if (isProcessing.get()) {
                doProcessWhenDone.set(true);
            } else {
                doProcessWhenDone.set(false);
                processDataset(true);
            }
        }
    }

    @FXML
    private void processDatasetAction() {
        processDataset(false);
    }

    public void processDataset(boolean idleModeValue) {
        if (!Processor.getProcessor().isProcessorAvailable() || !chartProcessor.areOperationListsValidProperty().get()) {
            return;
        }
        Processor.getProcessor().setProcessorAvailableStatus(false);
        Dataset.useCacheFile(SystemUtils.IS_OS_WINDOWS);
        doProcessWhenDone.set(false);
        isProcessing.set(true);
        idleMode.set(idleModeValue);
        statusBar.setProgress(0.0);
        Processor.setUpdater(this);
        if (!chartProcessor.isScriptValid()) {
            updateScriptDisplay();
        }
        if (fixDatasetName()) {
            if (!idleModeValue) {
                saveObject.set(null);
            }
            ((Service) processDataset.worker).restart();
        } else {
            Processor.getProcessor().setProcessorAvailableStatus(true);
        }
    }

    @FXML
    private void haltProcessAction() {
        processDataset.worker.cancel();
        Processor.getProcessor().setProcessorError();
    }

    public void saveOnClose() {
        Fx.runOnFxThread(() -> saveDataset(saveObject.getAndSet(null)));
    }

    private void setSaveState(Dataset dataset) {
        aListUpdated.set(true);
        saveObject.set(dataset);
        startTimer();
    }

    private class ProcessDataset {
        Processor processor;
        String script;
        public Worker<Integer> worker;

        private ProcessDataset() {
            worker = new Service<>() {

                protected Task createTask() {
                    return new Task() {
                        protected Object call() {
                            doProcessWhenDone.set(false);
                            isProcessing.set(true);
                            Processor.getProcessor().setProcessorAvailableStatus(false);
                            script = scriptGUI.getText();
                            try (PythonInterpreter processInterp = new PythonInterpreter()) {
                                updateStatus("Start processing");
                                updateTitle("Start Processing");
                                processInterp.exec("from pyproc import *");
                                processor = Processor.getProcessor();
                                processor.keepDatasetOpen(true);
                                processor.setTempFileMode(idleMode.get());
                                processor.clearDataset();
                                processInterp.exec("useProcessor(inNMRFx=True)");
                                processInterp.exec(FormatUtils.formatStringForPythonInterpreter(script));
                            }
                            return 0;
                        }
                    };
                }
            };

            ((Service<Integer>) worker).setOnSucceeded(event -> {
                Dataset dataset = processor.releaseDataset(null);
                if (dataset != null) {
                    dataset.script(script);
                } else {
                    try {
                        writeScript(script);
                    } catch (IOException ioE) {
                        log.error(ioE.getMessage(), ioE);
                    }
                }
                finishProcessing(dataset);
                isProcessing.set(false);
                if (doProcessWhenDone.get()) {
                    processIfIdle();
                }
            });
            ((Service<Integer>) worker).setOnCancelled(event -> {
                processor.clearDataset();
                isProcessing.set(false);
                setProcessingStatus("cancelled", false);
            });
            ((Service<Integer>) worker).setOnFailed(event -> {
                processor.clearDataset();
                isProcessing.set(false);
                // Processing is finished if it has ended with errors
                Processor.getProcessor().setProcessorAvailableStatus(true);
                final Throwable exception = worker.getException();
                setProcessingStatus(exception.getMessage(), false, exception);
            });

        }
    }

    public void writeScript(String script) throws IOException {
        chartProcessor.writeScript(script);
    }

    public void setProcessingStatus(String s, boolean ok) {
        setProcessingStatus(s, ok, null);
    }

    public void setProcessingStatus(String s, boolean ok, Throwable throwable) {
        Fx.runOnFxThread(() -> updateProcessingStatus(s, ok, throwable));
    }

    private void updateProcessingStatus(String s, boolean ok, Throwable throwable) {
        statusBar.setText(Objects.requireNonNullElse(s, ""));
        if (ok) {
            statusCircle.setFill(Color.GREEN);
            processingThrowable = null;
        } else {
            statusCircle.setFill(Color.RED);
            log.warn("error: {}", s);
            processingThrowable = throwable;
        }
        statusBar.setProgress(0.0);
    }

    public void clearProcessingTextLabel() {
        setProcessingStatus("", true);
    }

    /**
     * Enables/disables a set of features based on whether a dataset is provided. The dataset could
     * be null if the processor controller is simulating data.
     *
     * @param dataset The dataset to check.
     */
    private void enableRealFeatures(NMRData dataset) {
        boolean disable = dataset == null;
        datasetFileButton.setDisable(disable);
        autoProcess.setDisable(disable);
        autoGenerateScript.setDisable(disable);
        autoGenerateArrayedScript.setDisable(disable);
        openDefaultScript.setDisable(disable);
        openScript.setDisable(disable);
        saveScript.setDisable(disable);
        saveScriptAs.setDisable(disable);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Disable all real features that should only be enabled if an FID is set in chart processor.
        enableRealFeatures(null);
        chartProcessor = new ChartProcessor(this);
        mapOpLists = chartProcessor.mapOpLists;

        List<MenuItem> menuItems = getMenuItems();

        popOver.setContentNode(new Text("hello"));

        propertyManager = new PropertyManager(this, opTextField, popOver);
        referencePane = new TitledPane();
        referencePane.setText("PARAMETERS");
        addTitleBar(referencePane, "PARAMETERS", false);

        refManager = new RefManager(this, referencePane);
        statusBar.setProgress(0.0);

        statusBar.getLeftItems().add(statusCircle);
        Tooltip statusBarToolTip = new Tooltip();
        statusBarToolTip.textProperty().bind(statusBar.textProperty());
        statusBar.setTooltip(statusBarToolTip);

        viewMode.getItems().addAll(DisplayMode.values());
        Text detailIcon = GlyphsDude.createIcon(FontAwesomeIcon.INFO,
                AnalystApp.ICON_SIZE_STR);
        detailButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        detailButton.setGraphic(detailIcon);
        detailButton.setOnAction(e -> updateAllAccordionTitles());
        dimChoice.disableProperty().bind(viewMode.valueProperty().isEqualTo(DisplayMode.SPECTRUM));

        datasetFileButton.setOnAction(e -> datasetFileAction());

        setupListeners();
    }

    public Button getDatasetFileButton() {
        return datasetFileButton;
    }

    NMRData getNMRData() {
        NMRData nmrData = null;
        if (chartProcessor != null) {
            nmrData = chartProcessor.getNMRData();
        }
        return nmrData;
    }


    public void updateAfterOperationListChanged() {
        chartProcessor.updateOpList();
        updateAccordionList();
    }

    public void removeOpListener() {
        mapOpLists.removeListener(opListListener);
    }

    public void addOpListener() {
        mapOpLists.addListener(opListListener);
    }

    private void setupListeners() {
        chartProcessor.nmrDataProperty().addListener((observable, oldValue, newValue) -> enableRealFeatures(newValue));

        opListListener = change -> updateAfterOperationListChanged();
        addOpListener();

        dimListener = (observableValue, dimName, dimName2) -> {
            chartProcessor.setVecDim(dimName2);
        };
        refDimListener = (observableValue, number, number2) -> {
            int vecDim = (Integer) number2;
            log.info("refdim {}", vecDim);
        };

        Processor.getProcessor().addProcessorAvailableStatusListener(processorAvailableStatusListener);
        processorAvailable.set(Processor.getProcessor().isProcessorAvailable());
        processDatasetButton.disableProperty()
                .bind(stateProperty.isEqualTo(Worker.State.RUNNING)
                        .or(chartProcessor.nmrDataProperty().isNull())
                        .or(chartProcessor.areOperationListsValidProperty().not())
                        .or(processorAvailable.isEqualTo(false)));
        haltProcessButton.disableProperty().bind(stateProperty.isNotEqualTo(Worker.State.RUNNING));

        statusCircle.setOnMousePressed((Event d) -> {
            if (processingThrowable != null) {
                ExceptionDialog dialog = new ExceptionDialog(processingThrowable);
                dialog.showAndWait();
            }
        });
    }

    private List<MenuItem> getMenuItemsForDataset() {
        List<MenuItem> menuItems = new ArrayList<>();
        String[] opNames = {"AutoPhase Dataset"};
        for (String opName : opNames) {
            MenuItem menuItem = new MenuItem(opName);
            menuItem.addEventHandler(ActionEvent.ACTION, this::opMenuAction);
            menuItems.add(menuItem);
        }
        return menuItems;
    }

    private List<MenuItem> getMenuItemsForPolishingt() {
        List<MenuItem> menuItems = new ArrayList<>();
        String[] opNames = {"AutoCorrect Baseline"};
        for (String opName : opNames) {
            MenuItem menuItem = new MenuItem(opName);
            menuItem.addEventHandler(ActionEvent.ACTION, this::opMenuAction);
            menuItems.add(menuItem);
        }
        return menuItems;
    }

    private List<MenuItem> getMenuItems() {
        List<MenuItem> menuItems = new ArrayList<>();
        menuHandler = e -> log.info("menu action ");

        Menu menu = new Menu("Commonly Grouped Operations");
        menuItems.add(menu);
        List<MenuItem> subMenuItems = new ArrayList<>();
        for (String op : BASIC_OPS) {
            MenuItem menuItem = new MenuItem(op);
            menuItem.addEventHandler(ActionEvent.ACTION, this::opSequenceMenuAction);
            subMenuItems.add(menuItem);
        }
        menu.getItems().addAll(subMenuItems);

        Menu commonOpMenu = new Menu("Common Operations");
        menuItems.add(commonOpMenu);
        List<MenuItem> commonOpMenuItems = new ArrayList<>();
        for (String op : COMMON_OPS) {
            MenuItem menuItem = new MenuItem(op);
            menuItem.addEventHandler(ActionEvent.ACTION, this::opMenuAction);
            commonOpMenuItems.add(menuItem);
        }
        commonOpMenu.getItems().addAll(commonOpMenuItems);

        Menu advancedOpMenu = new Menu("Advanced Operations");
        menuItems.add(advancedOpMenu);

        subMenuItems = new ArrayList<>();
        menu = null;
        for (String op : OperationInfo.opOrders) {
            if (op.startsWith("Cascade-")) {
                if (menu != null) {
                    menu.getItems().addAll(subMenuItems);
                }
                menu = new Menu(op.substring(8));
                advancedOpMenu.getItems().add(menu);
                subMenuItems = new ArrayList<>();
            } else {
                if (!op.equals("Apodization") && !op.equals("Baseline Correction")) {
                    MenuItem menuItem = new MenuItem(op);
                    menuItem.addEventHandler(ActionEvent.ACTION, this::opMenuAction);
                    subMenuItems.add(menuItem);
                }
            }
        }
        // add last group of items (earlier ones added at each new Cascade item)
        if (menu != null) {
            menu.getItems().addAll(subMenuItems);
        }
        return menuItems;
    }

    /**
     * Listener for the Processor availability status and updates the processor available status
     *
     * @param newStatus the newly updated status
     */
    public void processorAvailableStatusUpdated(boolean newStatus) {
        processorAvailable.setValue(newStatus);
    }

    /**
     * Removes the ProcessorAvailable listener.
     */
    public void cleanUp() {
        Processor.getProcessor().removeProcessorAvailableStatusListener(processorAvailableStatusListener);
    }


    public void showScriptGUI() {
        scriptGUI.showStage();
    }
}
