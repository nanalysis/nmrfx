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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ExceptionDialog;
import org.fxmisc.richtext.CodeArea;
import org.greenrobot.eventbus.EventBus;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;
import org.nmrfx.processor.datasets.DatasetGroupIndex;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.VendorPar;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.events.DatasetSavedEvent;
import org.nmrfx.processor.gui.controls.ProcessingCodeAreaUtil;
import org.nmrfx.processor.processing.Processor;
import org.nmrfx.processor.processing.ProcessorAvailableStatusListener;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utilities.ProgressUpdater;
import org.nmrfx.utils.FormatUtils;
import org.nmrfx.utils.GUIUtils;
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

public class ProcessorController implements Initializable, ProgressUpdater {

    private static final Logger log = LoggerFactory.getLogger(ProcessorController.class);
    private static final String[] basicOps = {"APODIZE(lb=0.5) ZF FT", "SB ZF FT", "SB(c=0.5) ZF FT", "VECREF GEN"};
    private static final String[] commonOps = {"APODIZE", "SUPPRESS", "ZF", "FT", "AUTOPHASE", "EXTRACT", "BC"};

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

    Pane processorPane;
    Pane pane;

    @FXML
    private ToolBar toolBar;
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
    private ListView<String> scriptView;
    @FXML
    private StatusBar statusBar;
    private final Circle statusCircle = new Circle(10.0, Color.GREEN);

    @FXML
    private MenuButton opMenuButton;
    final ObservableList<String> operationList = FXCollections.observableArrayList();
    EventHandler<ActionEvent> menuHandler;
    PopOver popOver = new PopOver();

    ChangeListener<String> dimListener;
    ChangeListener<Number> refDimListener;

    PropertyManager propertyManager;
    RefManager refManager;

    @FXML
    PropertySheet propertySheet;

    @FXML
    PropertySheet refSheet;

    // script tab fields
    @FXML
    CodeArea textArea;
    @FXML
    CheckBox autoProcess;

    @FXML
    ToolBar fidParToolBar;
    @FXML
    TableView<VendorPar> fidParTableView;
    @FXML
    HBox navHBox;
    @FXML
    private VBox dimVBox;
    @FXML
    private Slider vecNum1;
    @FXML
    VBox navDetailsVBox;
    private TextField[] rowTextBoxes = new TextField[0];
    @FXML
    private TextField fileIndexTextBox;
    @FXML
    private ListView<DatasetGroupIndex> corruptedIndicesListView;
    ToggleGroup rowToggleGroup = new ToggleGroup();
    @FXML
    private ChoiceBox<String> realImagChoiceBox;
    @FXML
    private ChoiceBox<DisplayMode> viewMode;
    @FXML
    private Button datasetFileButton;
    @FXML
    private Button processDatasetButton;
    @FXML
    private Button haltProcessButton;
    @FXML
    private Button opDocButton;
    @FXML
    private ChoiceBox<Integer> scanMaxN;
    @FXML
    private ChoiceBox<Double> scanRatio;
    private final List<String> realImagChoices = new ArrayList<>();
    ChangeListener<String> vecNumListener;
    int[] rowIndices;
    int[] vecSizes;

    CheckBox genLSCatalog;
    TextField nLSCatFracField;
    TextField[][] lsTextFields;
    List<RadioButton> vectorDimButtons = new ArrayList<>();

    ChartProcessor chartProcessor;
    DocWindowController dwc = null;
    PolyChart chart;
    private AtomicBoolean idleMode = new AtomicBoolean(false);
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    private AtomicBoolean doProcessWhenDone = new AtomicBoolean(false);
    private final ProcessDataset processDataset = new ProcessDataset();
    ListChangeListener<String> opListListener = null;

    final ReadOnlyObjectProperty<Worker.State> stateProperty = processDataset.worker.stateProperty();
    private final ObjectProperty<Boolean> processorAvailable = new SimpleObjectProperty<>();
    private final ProcessorAvailableStatusListener processorAvailableStatusListener = this::processorAvailableStatusUpdated;
    Throwable processingThrowable;
    String currentText = "";

    ProcessingCodeAreaUtil codeAreaUtil;
    private ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);
    static AtomicBoolean aListUpdated = new AtomicBoolean(false);
    private AtomicBoolean needToFireEvent = new AtomicBoolean(false);
    private AtomicReference<Dataset> saveObject = new AtomicReference<>();
    ScheduledFuture futureUpdate = null;


    public static ProcessorController create(FXMLController fxmlController, StackPane processorPane, PolyChart chart) {
        Fxml.Builder builder = Fxml.load(ProcessorController.class, "ProcessorScene.fxml")
                .withParent(processorPane);
        ProcessorController controller = builder.getController();

        controller.chart = chart;
        chart.setProcessorController(controller);
        controller.chartProcessor.setChart(chart);
        controller.chartProcessor.fxmlController = fxmlController;
        controller.processorPane = processorPane;
        controller.pane = builder.getNode();
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        closeButton.setOnAction(e -> controller.hide());
        controller.toolBar.getItems().add(closeButton);
        fxmlController.processorCreated(controller.pane);

        return controller;
    }

    public void show() {
        if (processorPane.getChildren().isEmpty()) {
            processorPane.getChildren().add(pane);
            pane.setVisible(true);
        }
    }

    public void hide() {
        if (!processorPane.getChildren().isEmpty()) {
            processorPane.getChildren().clear();
            pane.setVisible(false);
        }
    }

    public boolean isVisible() {
        return pane.isVisible();
    }

    public PropertyManager getPropertyManager() {
        return propertyManager;
    }

    protected void clearOperationList() {
        operationList.clear();
    }

    protected void setOperationList(ArrayList<String> scriptList) {
        operationList.setAll(scriptList);
    }

    protected List<String> getOperationList() {
        return operationList;
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

    protected void updateDimChoice(boolean[] complex) {
        int nDim = complex.length;
        dimChoice.getSelectionModel().selectedItemProperty().removeListener(dimListener);
        ObservableList<String> dimList = FXCollections.observableArrayList();
        for (int i = 1; i <= nDim; i++) {
            dimList.add("D" + i);
            if ((i == 1) && (nDim > 2)) {
                StringBuilder sBuilder = new StringBuilder();
                sBuilder.append("D2");
                for (int j = 3; j <= nDim; j++) {
                    sBuilder.append(",");
                    sBuilder.append(j);
                }
                dimList.add(sBuilder.toString());
            }
        }
        dimList.add("D_ALL");
        for (int i = 1; i <= nDim; i++) {
            dimList.add("P" + i);
            if ((i == 1) && (nDim > 2)) {
                dimList.add("P2,3");
            }
        }
        dimChoice.setItems(dimList);
        dimChoice.getSelectionModel().select(0);
        dimChoice.getSelectionModel().selectedItemProperty().addListener(dimListener);

        updateVecNumChoice(complex);

        updateLineshapeCatalog(nDim);
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
            if (chartProcessor.datasetFile != null) {
                if (currentDataset != null) {
                    currentDataset.close();
                }
                boolean viewingDataset = isViewingDataset();
                chart.getFXMLController().openDataset(chartProcessor.datasetFile, false, true);
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
        dimChoice.getSelectionModel().select(0);
        chartProcessor.setVecDim("D1");
        viewMode.setValue(DisplayMode.FID_OPS);
        chart.getFXMLController().getUndoManager().clear();
        chart.getFXMLController().updateSpectrumStatusBarOptions(false);
    }

    @FXML
    void viewRawFID() {
        dimChoice.getSelectionModel().select(0);
        chartProcessor.setVecDim("D1");
        viewMode.setValue(DisplayMode.FID);
        chart.getFXMLController().getUndoManager().clear();
        chart.getFXMLController().updateSpectrumStatusBarOptions(false);
    }

    public String getScript() {
        StringBuilder script = new StringBuilder();
        for (Object obj : operationList) {
            script.append(obj.toString());
            script.append("\n");
        }
        return script.toString();
    }

    @FXML
    void handleScriptKey(KeyEvent event) {
        if ((event.getCode() == KeyCode.DELETE) || (event.getCode() == KeyCode.BACK_SPACE)) {
            deleteItem();
        }
    }


    public void deleteOp() {
        deleteItem();
    }

    void deleteItem() {
        if (!operationList.isEmpty()) {
            int index = scriptView.getSelectionModel().getSelectedIndex();

            /*
              If we are deleting the last element, select the previous,
              else select the next element. If this is the first element,
              then unselect the scriptView.
             */
            propertyManager.removeScriptListener();
            operationList.removeListener(opListListener);

            try {
                operationList.remove(index);
            } catch (Exception ex) {
                log.warn(ex.getMessage(), ex);
            } finally {
                propertyManager.addScriptListener();
                operationList.addListener(opListListener);
                OperationListCell.updateCells();
                chartProcessor.updateOpList();
            }
            if (index == operationList.size()) {
                scriptView.getSelectionModel().select(index - 1);
            } else {
                scriptView.getSelectionModel().select(index);
            }
            if (operationList.isEmpty()) {
                propertyManager.clearPropSheet();
                if (!chartProcessor.areOperationListsValidProperty().get()) {
                    String currentDatasetName = chart.getDataset().getName();
                    haltProcessAction();
                    // Set available as halt may happen before code flow reaches the Processor
                    Processor.getProcessor().setProcessorAvailableStatus(true);
                    chartProcessor.reloadData();
                    chartProcessor.datasetFile = null;
                    chartProcessor.datasetFileTemp = null;
                    viewingDataset(false);
                    ProjectBase.getActive().removeDataset(currentDatasetName);
                    chart.refresh();
                }
            } else {
                int currentIndex = scriptView.getSelectionModel().getSelectedIndex();
                if (currentIndex != -1) {
                    String selOp = scriptView.getItems().get(currentIndex);
                    propertyManager.setPropSheet(currentIndex, selOp);
                } else {
                    propertyManager.clearPropSheet();
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

    @FXML
    private void handleNewOp(ActionEvent event) {
        TextField textField = (TextField) event.getSource();
        String op = textField.getText();
        boolean appendOp = false;
        if (op.charAt(0) == '+') {
            appendOp = true;
            op = op.substring(1);
        }
        if (OperationInfo.isOp(op)) {
            int index = -1;
            if (appendOp) {
                index = scriptView.getSelectionModel().getSelectedIndex() + 1;
            }
            propertyManager.setOp(op, appendOp, index);
        } else {
            List<String> opCandidates = OperationInfo.getOps(op);
            if (opCandidates.size() == 1) {
                propertyManager.setOp(opCandidates.get(0));
            }
        }
    }

    @FXML
    private void showOpDoc() {
        if (dwc == null) {
            dwc = new DocWindowController();
        }
        dwc.load();
    }

    private void opMenuAction(ActionEvent event) {
        MenuItem menuItem = (MenuItem) event.getSource();
        String op = menuItem.getText();
        int index = propertyManager.getCurrentPosition(op);
        if (index != -1) {
            index = scriptView.getSelectionModel().getSelectedIndex() + 1;
            propertyManager.setOp(menuItem.getText(), true, index);
        } else {
            propertyManager.setOp(menuItem.getText(), false, -1);

        }
    }

    private void opSequenceMenuAction(ActionEvent event) {
        MenuItem menuItem = (MenuItem) event.getSource();
        String[] ops = menuItem.getText().split(" ");
        for (String op : ops) {
            propertyManager.setOp(op);
        }
    }

    @FXML
    private void loadScriptTab() {
        updateScriptDisplay();
    }

    void updateScriptDisplay() {
        String script = getFullScript();
        if (!script.equals(currentText)) {
            textArea.replaceText(script);
            currentText = script;
        }
        chartProcessor.setScriptValid(true);
    }

    boolean fixDatasetName() {
        String script = textArea.getText();
        if (!chartProcessor.scriptHasDataset(script)) {
            Optional<String> scriptOpt = chartProcessor.fixDatasetName(script);
            if (scriptOpt.isEmpty()) {
                return false;
            }
            textArea.replaceText(scriptOpt.get());
        }
        return true;
    }

    void unsetDatasetName() {
        String script = textArea.getText();
        if (chartProcessor.scriptHasDataset(script)) {
            script = chartProcessor.removeDatasetName(script);
            textArea.replaceText(script);
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
                propertyManager.setOp(op, true, 9999);
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
        HashSet<String> refOps = new HashSet<>();
        refOps.add("skip");
        refOps.add("sw");
        refOps.add("sf");
        refOps.add("ref");
        refOps.add("label");
        refOps.add("acqOrder");
        refOps.add("acqarray");
        refOps.add("acqsize");
        refOps.add("tdsize");
        refOps.add("fixdsp");
        if (!scriptString.equals(currentText)) {
            textArea.replaceText(scriptString);
            currentText = scriptString;
        }
        String[] lines = scriptString.split("\n");
        List<String> headerList = new ArrayList<>();
        List<String> dimList = null;
        Map<String, List<String>> mapOpLists = new TreeMap<>();

        String dimNum = "";
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
                    }
                } else if (dimList != null) {
                    dimList.add(line);
                } else if (refOps.contains(opName)) {
                    headerList.add(line);
                } else if (opName.equals("markrows")) {
                    parseMarkRows(args);
                }
            }
        }
        chartProcessor.setScripts(headerList, mapOpLists);
        String script = getFullScript();
        if (!script.equals(currentText)) {
            textArea.replaceText(script);
            currentText = script;
        }
        chartProcessor.setScriptValid(true);
        updateSkipIndices();
    }

    @FXML
    private void writeDefaultScriptAction() {
        File parent = chartProcessor.getScriptDir();
        if (parent != null) {
            File scriptFile = chartProcessor.getDefaultScriptFile();
            String script = textArea.getText();
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
            String script = textArea.getText();
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
                            script = textArea.getText();
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
        navHBox.getChildren().clear();
        scriptView.setItems(operationList);
        List<MenuItem> menuItems = getMenuItems();

        opMenuButton.getItems().addAll(menuItems);
        popOver.setContentNode(new Text("hello"));

        propertyManager = new PropertyManager(this, scriptView, propertySheet, operationList, opTextField, popOver);
        propertyManager.setupItems();
        refManager = new RefManager(this, refSheet);
        refManager.setupItems(0);
        statusBar.setProgress(0.0);

        statusBar.getLeftItems().add(statusCircle);
        Tooltip statusBarToolTip = new Tooltip();
        statusBarToolTip.textProperty().bind(statusBar.textProperty());
        statusBar.setTooltip(statusBarToolTip);

        codeAreaUtil = new ProcessingCodeAreaUtil(textArea);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        scanRatio.getItems().addAll(0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 10.0);
        scanRatio.setValue(3.0);
        scanMaxN.getItems().addAll(5, 10, 20, 50, 100, 200);
        scanMaxN.setValue(50);
        viewMode.getItems().addAll(DisplayMode.values());

        initTable();
        setupListeners();


    }

    private void setupListeners() {
        chartProcessor.nmrDataProperty().addListener((observable, oldValue, newValue) -> enableRealFeatures(newValue));

        opListListener = change -> {
            OperationListCell.updateCells();
            chartProcessor.updateOpList();
        };
        operationList.addListener(opListListener);

        scriptView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<String> call(ListView<String> p) {
                return new OperationListCell<>(scriptView) {
                    @Override
                    public void updateItem(String s, boolean empty) {
                        super.updateItem(s, empty);
                        setText(s);
                    }
                };
            }

        });

        dimListener = (observableValue, dimName, dimName2) -> {
            chartProcessor.setVecDim(dimName2);
            try {
                if (StringUtils.isNumeric(dimName2.substring(1))) {
                    int vecDim = Integer.parseInt(dimName2.substring(1));
                    refManager.setupItems(vecDim - 1);
                } else {
                    refManager.clearItems();
                }
            } catch (NumberFormatException nfE) {
                log.warn("Unable to parse vector dimension.", nfE);
            }
        };
        refDimListener = (observableValue, number, number2) -> {
            int vecDim = (Integer) number2;
            log.info("refdim {}", vecDim);
            refManager.setupItems(vecDim);
        };

        Processor.getProcessor().addProcessorAvailableStatusListener(processorAvailableStatusListener);
        processorAvailable.set(Processor.getProcessor().isProcessorAvailable());
        processDatasetButton.disableProperty()
                .bind(stateProperty.isEqualTo(Worker.State.RUNNING)
                        .or(chartProcessor.nmrDataProperty().isNull())
                        .or(chartProcessor.areOperationListsValidProperty().not())
                        .or(processorAvailable.isEqualTo(false)));
        haltProcessButton.disableProperty().bind(stateProperty.isNotEqualTo(Worker.State.RUNNING));

        rowToggleGroup.selectedToggleProperty().addListener(e -> handleRowDimChange());
        vecNumListener = (observableValue, string, string2) -> {
            String text = realImagChoiceBox.getValue();
            int vecNum = realImagChoices.indexOf(text);
            chartProcessor.setVector(vecNum);
            setFileIndex();
        };

        statusCircle.setOnMousePressed((Event d) -> {
            if (processingThrowable != null) {
                ExceptionDialog dialog = new ExceptionDialog(processingThrowable);
                dialog.showAndWait();
            }
        });

        corruptedIndicesListView.getSelectionModel().selectedItemProperty().addListener(e -> corruptedIndexListener());

    }

    private List<MenuItem> getMenuItems() {
        List<MenuItem> menuItems = new ArrayList<>();
        menuHandler = e -> log.info("menu action ");

        Menu menu = new Menu("Commonly Grouped Operations");
        menuItems.add(menu);
        List<MenuItem> subMenuItems = new ArrayList<>();
        for (String op : basicOps) {
            MenuItem menuItem = new MenuItem(op);
            menuItem.addEventHandler(ActionEvent.ACTION, this::opSequenceMenuAction);
            subMenuItems.add(menuItem);
        }
        menu.getItems().addAll(subMenuItems);

        Menu commonOpMenu = new Menu("Common Operations");
        menuItems.add(commonOpMenu);
        List<MenuItem> commonOpMenuItems = new ArrayList<>();
        for (String op : commonOps) {
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
                MenuItem menuItem = new MenuItem(op);
                menuItem.addEventHandler(ActionEvent.ACTION, this::opMenuAction);
                subMenuItems.add(menuItem);
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

    void initTable() {
        TableColumn<VendorPar, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory("Name"));
        nameCol.setEditable(false);
        nameCol.setPrefWidth(125);

        TableColumn<VendorPar, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory("Value"));
        valueCol.setEditable(false);
        valueCol.setPrefWidth(260);
        fidParTableView.getColumns().setAll(nameCol, valueCol);

    }

    public void updateParTable(NMRData data) {
        List<VendorPar> vPars = data.getPars();
        vPars.sort(Comparator.comparing(VendorPar::getName));
        ObservableList<VendorPar> pars = FXCollections.observableArrayList(vPars);
        fidParTableView.setItems(pars);
    }

    @FXML
    protected void vectorStatus(int[] sizes, int vecDim) {
        int nDim = sizes.length;
        vecSizes = sizes.clone();
        if (nDim > 1) {
            if (rowTextBoxes.length != (nDim - 1)) {
                updateRowBoxes(nDim);
            }
            int j = 0;
            if (vecNum1 != null) {
                for (int i = 0; i < nDim; i++) {
                    if (i != vecDim) {
                        String dimName = String.valueOf(i + 1);
                        vectorDimButtons.get(j).setText(dimName);
                        if (j == 0) {
                            updateVectorSlider(sizes[i]);
                        }
                        rowTextBoxes[j].setText(1 + " / " + sizes[i]);
                        fileIndexTextBox.setText("1");
                        realImagChoiceBox.setValue(realImagChoices.get(0));
                        j++;
                    }
                }
            }
        } else {
            navHBox.getChildren().clear();
        }
    }

    private void updateVectorSlider(int size) {
        vecNum1.setMax(size);
        int majorTic = Math.max(1, size / 8);
        vecNum1.setMajorTickUnit(majorTic);
        vecNum1.setMinorTickCount(4);
        vecNum1.setValue(1);
    }

    private void updateRowBoxes(int nDim) {
        vectorDimButtons.clear();
        navHBox.getChildren().clear();
        navHBox.getChildren().add(vecNum1);
        navHBox.getChildren().add(navDetailsVBox);
        rowTextBoxes = new TextField[nDim - 1];
        dimVBox.setId("dimVBox");
        dimVBox.getChildren().clear();
        for (int i = 0; i < nDim - 1; i++) {
            rowTextBoxes[i] = new TextField();
            rowTextBoxes[i].setEditable(false);
            HBox.setHgrow(rowTextBoxes[i], Priority.ALWAYS);
            RadioButton radioButton = new RadioButton((i + 2) + ": ");
            dimVBox.getChildren().add(new HBox(radioButton, rowTextBoxes[i]));
            radioButton.setToggleGroup(rowToggleGroup);
            vectorDimButtons.add(radioButton);
            if (i == 0) {
                rowToggleGroup.selectToggle(radioButton);
            }
        }
        fileIndexTextBox.setPrefWidth(60);
        fileIndexTextBox.setEditable(false);
    }

    Integer getRowChoice() {
        RadioButton radioButton = (RadioButton) rowToggleGroup.getSelectedToggle();
        int iDim;
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
                if ((vecNum1 != null) && vecNum1.isVisible()) {
                    int size = vecSizes[iDim - 1];
                    updateVectorSlider(size);
                    vecNum1.setMax(size);
                }
            }
        }
    }

    protected void setRowLabel(int row, int size) {
        for (int i = 0; i < vectorDimButtons.size(); i++) {
            if (vectorDimButtons.get(i).isSelected()) {
                rowTextBoxes[i].setText(row + " / " + size);
            }
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
            if ((riIndex != -1) && (riIndex < rowIndices.length)) {
                int index = rowIndices[riIndex];
                fileIndexTextBox.setText(String.valueOf(index + 1));
            }
        }
    }

    String getRealImaginaryChoice() {
        return realImagChoiceBox.getValue();
    }

    @FXML
    private void handleVecNum(Event event) {
        Slider slider = (Slider) event.getSource();
        int iRow = (int) slider.getValue() - 1;
        int iDim = getRowChoice() - 1;
        updateRowLabels(iDim, iRow);
        int[] rows = getRows();
        chartProcessor.vecRow(rows);
        chart.layoutPlotChildren();
    }

    private void updateRowLabels(int iDim, int i) {
        if (getNMRData() != null) {
            int nDim = getNMRData().getNDim();
            int size = 1;
            if (nDim > 1) {
                size = getNMRData().getSize(iDim);
            }

            if (i >= size) {
                i = size - 1;
            }
            if (i < 0) {
                i = 0;
            }
            setRowLabel(i + 1, size);
        }
    }

    private void corruptedIndexListener() {
        DatasetGroupIndex groupIndex = corruptedIndicesListView.getSelectionModel().getSelectedItem();
        if (groupIndex != null) {
            int[] indices = groupIndex.getIndices();
            for (int iDim = 0; iDim < indices.length; iDim++) {
                updateRowLabels(iDim + 1, indices[iDim]);
            }
            int[] rows = getRows();
            String realImagChoice = groupIndex.getGroupIndex();
            if (!realImagChoice.equals("")) {
                realImagChoiceBox.setValue(realImagChoice);
            }
            chartProcessor.vecRow(rows);
        }
        chart.layoutPlotChildren();
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

        int start = (delta / 4 * (iRow / delta / 4)) - delta / 2;
        if (start < 1) {
            start = 1;
        }
        double end = (double) start + delta;
        slider.setMin(start);
        slider.setMax(end);

    }

    protected void updateVecNumChoice(boolean[] complex) {
        char[] chars = {'R', 'I'};
        realImagChoices.clear();
        realImagChoiceBox.getItems().clear();
        int nDim = complex.length;
        if (nDim > 1) {
            int[] sizes = new int[nDim - 1];
            for (int iDim = 1; iDim < nDim; iDim++) {
                sizes[iDim - 1] = complex[iDim] ? 2 : 1;
            }
            realImagChoiceBox.valueProperty().removeListener(vecNumListener);
            StringBuilder sBuilder = new StringBuilder();
            MultidimensionalCounter counter = new MultidimensionalCounter(sizes);
            var iterator = counter.iterator();
            while (iterator.hasNext()) {
                iterator.next();
                int[] counts = iterator.getCounts();
                sBuilder.setLength(0);
                for (int i : counts) {
                    sBuilder.append(chars[i]);
                }
                realImagChoiceBox.getItems().add(sBuilder.toString());
                realImagChoices.add(sBuilder.toString());
            }
            realImagChoiceBox.setValue(realImagChoices.get(0));
            realImagChoiceBox.valueProperty().addListener(vecNumListener);
        }
    }

    NMRData getNMRData() {
        NMRData nmrData = null;
        if (chartProcessor != null) {
            nmrData = chartProcessor.getNMRData();
        }
        return nmrData;
    }

    @FXML
    private void addCorruptedIndex() {
        int[] rows = getRows();
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            nmrData.addSkipGroup(rows, getRealImaginaryChoice());
        }
        updateSkipIndices();
    }

    @FXML
    private void scanForCorruption() {
        clearCorruptedIndex();
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            if (nmrData.getSampleSchedule() != null) {
                GUIUtils.warn("Corruption Scan", "Can't scan a NUS dataset");
                return;
            }
            double ratio = scanRatio.getValue();
            int scanN = scanMaxN.getValue();
            List<ChartProcessor.VecIndexScore> indices = chartProcessor.scanForCorruption(ratio, scanN);
            for (ChartProcessor.VecIndexScore vecIndexScore : indices) {
                var vecIndex = vecIndexScore.vecIndex();
                int maxIndex = vecIndexScore.maxIndex();
                int[][] outVec = vecIndex.getOutVec(0);
                int[] groupIndices = new int[outVec.length - 1];
                for (int i = 0; i < groupIndices.length; i++) {
                    groupIndices[i] = outVec[i + 1][0] / 2;
                }
                DatasetGroupIndex groupIndex = new DatasetGroupIndex(groupIndices, realImagChoices.get(maxIndex));
                nmrData.addSkipGroup(groupIndex);
            }
        }
        updateSkipIndices();
    }

    @FXML
    private void addCorruptedDim() {
        int[] rows = getRows();
        int iDim = getRowChoice() - 2;
        for (int i = 0; i < rows.length; i++) {
            if (i != iDim) {
                rows[i] = -1;
            }
        }
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            nmrData.addSkipGroup(rows, "");
        }
        updateSkipIndices();
    }

    void updateSkipIndices() {
        corruptedIndicesListView.setItems(getSkipList());
        updateScriptDisplay();
    }

    @FXML
    private void clearCorruptedIndex() {
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            nmrData.clearSkipGroups();
        }
        updateSkipIndices();
    }

    @FXML
    private void deleteCorruptedIndex() {
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            int index = corruptedIndicesListView.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                nmrData.getSkipGroups().remove(index);
            }
        }
        updateSkipIndices();
    }

    public Optional<String> getSkipString() {
        Optional<String> result = Optional.empty();
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            result = DatasetGroupIndex.getSkipString(nmrData.getSkipGroups());
        }
        return result;
    }

    public ObservableList<DatasetGroupIndex> getSkipList() {
        ObservableList<DatasetGroupIndex> groupList = FXCollections.observableArrayList();
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            groupList.addAll(nmrData.getSkipGroups());
        }
        return groupList;
    }

    void parseMarkRows(String markRowsArg) {
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            nmrData.getSkipGroups().clear();
            String[] fields = markRowsArg.split("\\]\\s*,\\s*\\[");
            for (var field : fields) {
                field = field.trim();
                if (field.charAt(0) == '[') {
                    field = field.substring(1);
                }
                if (field.endsWith("]")) {
                    field = field.substring(0, field.length() - 1);
                }
                DatasetGroupIndex datasetGroupIndex = new DatasetGroupIndex(field);
                nmrData.addSkipGroup(datasetGroupIndex);
            }
        }
    }
}
