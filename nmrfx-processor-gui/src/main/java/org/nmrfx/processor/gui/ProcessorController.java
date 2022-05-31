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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ExceptionDialog;
import org.fxmisc.richtext.CodeArea;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.VendorPar;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.controls.ProcessingCodeAreaUtil;
import org.nmrfx.processor.processing.Processor;
import org.nmrfx.utilities.ProgressUpdater;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ProcessorController implements Initializable, ProgressUpdater {

    private static final Logger log = LoggerFactory.getLogger(ProcessorController.class);

    Pane processorPane;
    Pane pane;

    @FXML
    private ToolBar toolBar;
    @FXML
    private TextField opTextField;

    @FXML
    private ChoiceBox dimChoice;

    @FXML
    private ListView scriptView;
    @FXML
    private StatusBar statusBar;
    private Circle statusCircle = new Circle(10.0, Color.GREEN);

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
    CheckBox combineFiles;

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

    CheckBox genLSCatalog;
    TextField nLSCatFracField;
    TextField[][] lsTextFields;

    //PopOver propOver = new PopOver();
    //PropertySheet propSheet = new PropertySheet();
    static String[] basicOps = {"SB ZF FT", "SB(c=0.5) ZF FT", "EXPD ZF FT", "VECREF GEN"};
    static String[] eaOps = {"TDCOMB(coef='ea2d')", "SB", "ZF", "FT"};
    ChartProcessor chartProcessor;
    FXMLController fxmlController;
    DocWindowController dwc = null;
    SpecAttrWindowController specAttrWindowController = null;
    PolyChart chart;
    private boolean isProcessing = false;
    private boolean doProcessWhenDone = false;
    private boolean processable = false;
    private ProcessDataset processDataset = new ProcessDataset();
    ListChangeListener<String> opListListener = null;

    final ReadOnlyObjectProperty<Worker.State> stateProperty = processDataset.worker.stateProperty();
    Throwable processingThrowable;
    String currentText = "";

    public static ProcessorController create(FXMLController fxmlController, StackPane processorPane, PolyChart chart) {
        String iconSize = "12px";
        String fontSize = "7pt";
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/ProcessorScene.fxml"));
        final ProcessorController controller;
        Stage stage = fxmlController.getStage();
        double width = stage.getWidth();
        try {
            Pane pane = (Pane) loader.load();
            processorPane.getChildren().add(pane);

            controller = loader.<ProcessorController>getController();
            controller.fxmlController = fxmlController;
            controller.chart = chart;
            chart.setProcessorController(controller);
            controller.chartProcessor.setChart(chart);
            controller.chartProcessor.fxmlController = fxmlController;
            controller.processorPane = processorPane;
            controller.pane = pane;
            Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
            closeButton.setOnAction(e -> controller.hide());
            controller.toolBar.getItems().add(closeButton);
            fxmlController.processorCreated(pane);

            stage.setWidth(width + pane.getMinWidth());
            return controller;
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
            return null;
        }
    }
    @FXML
    private BorderPane mainBox;
    @FXML
    private ChoiceBox viewMode;
    @FXML
    private Button datasetFileButton;
    @FXML
    private Button processDatasetButton;
    @FXML
    private Button haltProcessButton;
    @FXML
    private Button viewDatasetButton;
    @FXML
    private Button scanDirChooserButton;
    @FXML
    private Button processScanDirButton;
    @FXML
    private Button opDocButton;
    @FXML
    private TitledPane lsOptionsPane;

    ProcessingCodeAreaUtil codeAreaUtil;
    ConsoleUtil consoleUtil;

    public void show() {
        if (processorPane.getChildren().isEmpty()) {
            processorPane.getChildren().add(pane);
            Stage stage = fxmlController.getStage();
            double width = stage.getWidth();
            stage.setWidth(width + pane.getMinWidth());
        }
    }

    public void hide() {
        if (!processorPane.getChildren().isEmpty()) {
            processorPane.getChildren().clear();
            Stage stage = fxmlController.getStage();
            double width = stage.getWidth();
            stage.setWidth(width - pane.getMinWidth());

        }
    }

    public boolean isVisible() {
        return pane.getParent() != null;
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

    public void setScripts(List<String> headerList, Map<String, List<String>> mapOps) {
        chartProcessor.setScripts(headerList, mapOps);
    }

    protected List<String> getOperationList() {
        return operationList;
    }

    protected String getFlagString() {
        return "";
    }

    protected String getFullScript() {
        return chartProcessor.buildScript();
    }

    public ChartProcessor getChartProcessor() {
        return chartProcessor;
    }

    public void updateProgress(double f) {
        if (Platform.isFxApplicationThread()) {
            statusBar.setProgress(f);
        } else {
            Platform.runLater(() -> {
                statusBar.setProgress(f);
            });
        }
    }

    public void updateStatus(String s) {
        if (Platform.isFxApplicationThread()) {
            setProcessingStatus(s, true);
        } else {
            Platform.runLater(() -> {
                setProcessingStatus(s, true);
            });
        }
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
        if (nDim > 1) {
            dimChoice.getSelectionModel().selectedItemProperty().removeListener(dimListener);
            ObservableList<String> dimList = FXCollections.observableArrayList();
            for (int i = 1; i <= nDim; i++) {
                dimList.add("D" + String.valueOf(i));
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
                dimList.add("P" + String.valueOf(i));
                if ((i == 1) && (nDim > 2)) {
                    dimList.add("P2,3");
                }
            }
            dimChoice.setItems(dimList);
            dimChoice.getSelectionModel().select(0);
            dimChoice.getSelectionModel().selectedItemProperty().addListener(dimListener);

            chart.controller.updateVecNumChoice(complex);
        }
        updateLineshapeCatalog(nDim);
    }

    protected void updateLineshapeCatalog(int nDim) {
        NMRData nmrData = null;
        if (chartProcessor == null) {
            chartProcessor = getChartProcessor();
        }
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
        lsOptionsPane.setContent(borderPane);
    }

    String getLSScript() {
        StringBuilder sBuilder = new StringBuilder();
        if (genLSCatalog.isSelected()) {
            boolean ok = true;
            //genLSCatalog(lw, nLw, nKeep, 2)
            for (int i = 0; i < lsTextFields.length; i++) {
                for (int j = 0; j < 2; j++) {
                    try {
                        Double.parseDouble(lsTextFields[i][j].getText());
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
        if (viewMode.getSelectionModel().getSelectedIndex() == 1) {
            if (chart.controller.isFIDActive()) {
                viewDatasetInApp();
            }
        } else if (!chart.controller.isFIDActive()) {
            viewFID();
        }
    }

    @FXML
    public void viewDatasetInApp() {
        if (chartProcessor.datasetFile != null) {
            boolean viewingDataset = isViewingDataset();
            chart.controller.openDataset(chartProcessor.datasetFile, false);
            viewMode.getSelectionModel().select(1);
            if (!viewingDataset) {
                chart.full();
                chart.autoScale();
            }
        }
    }

    void viewingDataset(boolean state) {
        if (state) {
            viewMode.getSelectionModel().select(1);
        } else {
            viewMode.getSelectionModel().select(0);
        }
    }

    public boolean isViewingDataset() {
        return viewMode.getSelectionModel().getSelectedIndex() == 1;
    }

    @FXML
    void viewFID() {
        dimChoice.getSelectionModel().select(0);
        chartProcessor.setVecDim("D1");
        viewMode.setValue("FID");
        chart.controller.undoManager.clear();
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
            if (!operationList.isEmpty()) {
                int index = scriptView.getSelectionModel().getSelectedIndex();

                /**
                 *
                 * If we are deleting the last element, select the previous,
                 * else select the next element. If this is the first element,
                 * then unselect the scriptView.
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
                chartProcessor.execScript(getScript(), true, false);
                chart.layoutPlotChildren();
            }
        }
    }

    @FXML
    void handleOpKey(KeyEvent event) {
        if (!(event.getCode() == KeyCode.ESCAPE)) {
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
            //text.setFill(Color.RED);

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
    private void showOpDoc(ActionEvent event) {
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
    private void loadScriptTab(Event event) {
        updateScriptDisplay();
    }

    void updateScriptDisplay() {
        //textArea.setText(getFullScript());
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
    private void datasetFileAction(ActionEvent event) {
        unsetDatasetName();
        fixDatasetName();
    }

    @FXML
    private void openDefaultScriptAction(ActionEvent event) {
        File parent = chartProcessor.getScriptDir();
        if (parent != null) {
            File scriptFile = chartProcessor.getDefaultScriptFile();
            openScript(scriptFile);
        } else {
            openScriptAction(event);
        }
    }

    @FXML
    private void openScriptAction(ActionEvent event) {
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
    private void openVecScriptAction(ActionEvent event) {
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
    private void writeVecScriptAction(ActionEvent event) {
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
            System.out.println("Can't read script");
        }
        if (scriptString != null) {
            String[] ops = scriptString.split("\n");
            for (String op : ops) {
                op = op.trim();
                propertyManager.setOp(op, true, 9999);
            }
        }
    }

    private void loadOps(File file) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(file.toString()));
            String scriptString = new String(encoded);
            parseScript(scriptString);
        } catch (IOException ioe) {
            System.out.println("Can't read script");
        }
    }

    protected void openScript(File file) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(file.toString()));
            String scriptString = new String(encoded);
            parseScript(scriptString);
            chartProcessor.execScriptList(true);
            PolyChart.getActiveChart().refresh();
        } catch (IOException ioe) {
            System.out.println("Can't read script");
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
        //textArea.setText(scriptString);
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
                //System.out.println(opName);
                if (opName.equals("run")) {
                    continue;
                }
                //System.out.println(line);
                //System.out.println(args);
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
                }
            }
        }
        chartProcessor.setScripts(headerList, mapOpLists);
        //textArea.setText(getFullScript());
        String script = getFullScript();
        if (!script.equals(currentText)) {
            textArea.replaceText(script);
            currentText = script;
        }
        chartProcessor.setScriptValid(true);
    }

    @FXML
    private void writeDefaultScriptAction(ActionEvent event) {
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
            writeScriptAction(event);
        }
    }

    @FXML
    private void writeScriptAction(ActionEvent event) {
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

    public String getCurrentScript() {
        return textArea.getText();
    }

    synchronized void setProcessingOn() {
        isProcessing = true;
        doProcessWhenDone = false;
    }

    synchronized void setProcessingOff() {
        isProcessing = false;
    }

    boolean isProcessing() {
        return isProcessing;
    }

    void doProcessWhenDone() {
        doProcessWhenDone = true;
    }

    void finishProcessing() {
        Platform.runLater(() -> {
            //chartProcessor.renameDataset();
            viewDatasetInApp();
        });
    }

    void processIfIdle() {
        if (autoProcess.isSelected()) {
            if (processable) {
                if (isProcessing()) {
                    doProcessWhenDone();
                } else {
                    doProcessWhenDone = false;
                    processDataset();
                }
            }
        }
    }

    @FXML
    private void processDatasetAction(ActionEvent event) {
        processDataset();
    }

    private void processDataset() {
        if (SystemUtils.IS_OS_WINDOWS) {
            Dataset.useCacheFile(true);
        } else {
            Dataset.useCacheFile(false);
        }
        setProcessingOn();
        processable = false;
        statusBar.setProgress(0.0);
        Processor.setUpdater(this);
        if (!chartProcessor.isScriptValid()) {
            updateScriptDisplay();
        }
        if (fixDatasetName()) {
            ((Service) processDataset.worker).restart();
        }
    }

    @FXML
    private void haltProcessAction(ActionEvent event) {
        processDataset.worker.cancel();
        Processor.getProcessor().setProcessorError();
    }

    private class ProcessDataset {

        String script;
        public Worker<Integer> worker;

        private ProcessDataset() {
            worker = new Service<Integer>() {

                protected Task createTask() {
                    return new Task() {
                        protected Object call() {
                            script = textArea.getText();
                            try (PythonInterpreter processInterp = new PythonInterpreter()) {
                                updateStatus("Start processing");
                                updateTitle("Start Processing");
                                processInterp.exec("from pyproc import *");
                                processInterp.exec("useProcessor(inNMRFx=True)");
                                processInterp.exec(script);
                            }
                            return 0;
                        }
                    };
                }
            };

            ((Service<Integer>) worker).setOnSucceeded(event -> {
                processable = true;
                finishProcessing();
                try {
                    writeScript(script);
                } catch (IOException ex) {
                    GUIUtils.warn("Write Script Error", ex.getMessage());
                }
                setProcessingOff();
                if (doProcessWhenDone) {
                    processIfIdle();
                }
            });
            ((Service<Integer>) worker).setOnCancelled(event -> {
                setProcessingOff();
                setProcessingStatus("cancelled", false);
            });
            ((Service<Integer>) worker).setOnFailed(event -> {
                setProcessingOff();
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
        if (s == null) {
            statusBar.setText("");
        } else {
            statusBar.setText(s);
        }
        if (ok) {
            statusCircle.setFill(Color.GREEN);
            processingThrowable = null;
        } else {
            statusCircle.setFill(Color.RED);
            System.out.println("error: " + s);
            processingThrowable = throwable;
        }
        statusBar.setProgress(0.0);
    }

    public void clearProcessingTextLabel() {
        statusBar.setText("");
        statusCircle.setFill(Color.GREEN);
    }

    private void setupRefItems() {
        ObservableList<PropertySheet.Item> newItems = FXCollections.observableArrayList();
        refSheet.getItems().setAll(newItems);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb
    ) {
        chartProcessor = new ChartProcessor(this);
        scriptView.setItems(operationList);
        List<MenuItem> menuItems = new ArrayList<>();
        menuHandler = new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                System.out.println("menu action ");
            }
        };

        Menu menu = new Menu("Common Op Lists");
        menuItems.add(menu);
        List<MenuItem> subMenuItems = new ArrayList<>();
        for (String op : basicOps) {
            MenuItem menuItem = new MenuItem(op);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> opSequenceMenuAction(event));
            subMenuItems.add(menuItem);
        }
        menu.getItems().addAll(subMenuItems);

        subMenuItems = new ArrayList<>();
        for (String op : OperationInfo.opOrders) {
            if (op.startsWith("Cascade-")) {
                menu.getItems().addAll(subMenuItems);
                menu = new Menu(op.substring(8));
                subMenuItems = new ArrayList<>();
                menuItems.add(menu);
            } else {
                MenuItem menuItem = new MenuItem(op);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> opMenuAction(event));
                subMenuItems.add(menuItem);
            }
        }
        menu.getItems().addAll(subMenuItems);

        opMenuButton.getItems().addAll(menuItems);
        popOver.setContentNode(new Text("hello"));

        scriptView.setOnMousePressed(new EventHandler() {
            public void handle(Event d) {
                MouseEvent mEvent = (MouseEvent) d;
                if (mEvent.getClickCount() == 2) {
                    Node node = (Node) d.getSource();

                }
            }
        });

        opListListener = new ListChangeListener<String>() {
            @Override
            public void onChanged(ListChangeListener.Change<? extends String> change) {
                OperationListCell.updateCells();
                chartProcessor.updateOpList();
            }
        };
        operationList.addListener(opListListener);

        scriptView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> p) {
                OperationListCell<String> olc = new OperationListCell<String>(scriptView) {
                    @Override
                    public void updateItem(String s, boolean empty) {
                        super.updateItem(s, empty);
                        setText(s);
                    }
                };
                return olc;
            }

        });

        dimListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String dimName, String dimName2) {

                System.out.println("dim " + dimName2);
                chartProcessor.setVecDim(dimName2);
                try {
                    int vecDim = Integer.parseInt(dimName2.substring(1));
                    refManager.setupItems(vecDim - 1);

                } catch (NumberFormatException nfE) {
                    log.warn("Unable to parse vector dimension.", nfE);
                }
            }
        };
        refManager = new RefManager(this, refSheet);
        refDimListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                int vecDimOld = (Integer) number;
                int vecDim = (Integer) number2;
                System.out.println("refdim " + vecDim);
                refManager.setupItems(vecDim);
            }
        };
        propertyManager = new PropertyManager(this, scriptView, propertySheet, operationList, opTextField, popOver);
        propertyManager.setupItems();
        refManager.setupItems(0);
        statusBar.setProgress(0.0);

        statusBar.getLeftItems().add(statusCircle);

        processDatasetButton.disableProperty().bind(stateProperty.isEqualTo(Worker.State.RUNNING).or(chartProcessor.nmrDataProperty().isNull()));
        haltProcessButton.disableProperty().bind(stateProperty.isNotEqualTo(Worker.State.RUNNING));

        codeAreaUtil = new ProcessingCodeAreaUtil(textArea);
        textArea.setEditable(false);
        textArea.setWrapText(true);
//        consoleUtil = new ConsoleUtil();
//        consoleUtil.addHandler(consoleArea, chartProcessor.getInterpreter());
//        consoleUtil.banner();
//        consoleUtil.prompt();
//        consoleArea.setEditable(true);

        statusCircle.setOnMousePressed((Event d) -> {
            if (processingThrowable != null) {
                ExceptionDialog dialog = new ExceptionDialog(processingThrowable);
                dialog.showAndWait();
            }
        });
        initTable();

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
        vPars.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        ObservableList<VendorPar> pars = FXCollections.observableArrayList(vPars);
        fidParTableView.setItems(pars);
    }

    public PolyChart getChart() {
        return chart;
    }
}
