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

import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.controls.ProcessingCodeAreaUtil;
import org.nmrfx.processor.processing.Processor;
import org.nmrfx.processor.processing.ProgressUpdater;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
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
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ExceptionDialog;
import org.python.util.PythonInterpreter;
import org.fxmisc.richtext.CodeArea;
import org.nmrfx.processor.datasets.vendor.NMRData;

public class ProcessorController implements Initializable, ProgressUpdater {

    protected Stage stage;
    @FXML
    private VBox opBox;
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

    public static ProcessorController create(FXMLController fxmlController, Stage parent, PolyChart chart) {
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/ProcessorScene.fxml"));
        ProcessorController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<ProcessorController>getController();
            controller.fxmlController = fxmlController;
            controller.stage = stage;
            controller.chart = chart;
            stage.setTitle("NMRFx Processor");

            stage.initOwner(parent);
            chart.setProcessorController(controller);
            controller.chartProcessor.setChart(chart);
            controller.chartProcessor.fxmlController = fxmlController;
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }
    @FXML
    private VBox mainBox;
    @FXML
    private ChoiceBox viewMode;
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

    public Stage getStage() {
        return stage;
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

    protected void updateDimChoice(int nDim) {
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

            chart.controller.getStatusBar().updateVecNumChoice(nDim);
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
                    ex.printStackTrace();
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
        textArea.replaceText(getFullScript());

        chartProcessor.setScriptValid(true);
    }

    @FXML
    private void openDefaultScriptAction(ActionEvent event) {
        String parent = chartProcessor.getScriptDir();
        if (parent != null) {
            File scriptFile = new File(parent, chartProcessor.getDefaultScriptName());
            openScript(scriptFile);
        } else {
            openScriptAction(event);
        }
    }

    @FXML
    private void openScriptAction(ActionEvent event) {
        String initialDir = chartProcessor.getScriptDir();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Script");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Python Script", "*.py", "py"),
                new FileChooser.ExtensionFilter("Any File", "*")
        );
        if (initialDir != null) {
            fileChooser.setInitialDirectory(new File(initialDir));
        }
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            openScript(selectedFile);
        }
    }

    @FXML
    private void openVecScriptAction(ActionEvent event) {
        String initialDir = chartProcessor.getScriptDir();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Vector Script");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Python Script", "*.txt", "txt"),
                new FileChooser.ExtensionFilter("Any File", "*")
        );
        if (initialDir != null) {
            fileChooser.setInitialDirectory(new File(initialDir));
        }
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            openVecScript(selectedFile);
        }
    }

    @FXML
    private void writeVecScriptAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        String initialDir = chartProcessor.getScriptDir();
        if (initialDir != null) {
            fileChooser.setInitialDirectory(new File(initialDir));
        }
        File saveFile = fileChooser.showSaveDialog(stage);
        if (saveFile != null) {
            String script = getScript();
            chartProcessor.writeScript(script, saveFile);
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

    private void openScript(File file) {
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
        //textArea.setText(scriptString);
        textArea.replaceText(scriptString);
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
        textArea.replaceText(getFullScript());
        chartProcessor.setScriptValid(true);
    }

    @FXML
    private void writeDefaultScriptAction(ActionEvent event) {
        String parent = chartProcessor.getScriptDir();
        if (parent != null) {
            File scriptFile = new File(parent, chartProcessor.getDefaultScriptName());
            String script = textArea.getText();
            chartProcessor.writeScript(script, scriptFile);
        } else {
            writeScriptAction(event);
        }
    }

    @FXML
    private void writeScriptAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        String initialDir = chartProcessor.getScriptDir();
        if (initialDir != null) {
            fileChooser.setInitialDirectory(new File(initialDir));
        }
        File saveFile = fileChooser.showSaveDialog(stage);
        if (saveFile != null) {
            String script = textArea.getText();
            chartProcessor.writeScript(script, saveFile);
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
            try {
                chartProcessor.renameDataset();
                viewDatasetInApp();
            } catch (IOException ioE) {
                setProcessingStatus(ioE.getMessage(), false, ioE);
            }
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
        setProcessingOn();
        processable = false;
        statusBar.setProgress(0.0);
        Processor.setUpdater(this);
        if (!chartProcessor.isScriptValid()) {
            updateScriptDisplay();
        }
        ((Service) processDataset.worker).restart();
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
                            PythonInterpreter processInterp = new PythonInterpreter();
                            updateStatus("Start processing");
                            updateTitle("Start Processing");
                            processInterp.exec("from pyproc import *");
                            processInterp.exec("useProcessor(inNMRFx=True)");
                            processInterp.exec(script);
                            return 0;
                        }
                    };
                }
            };

            ((Service<Integer>) worker).setOnSucceeded(event -> {
                processable = true;
                finishProcessing();
                writeScript(script);
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

    public void writeScript(String script) {
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
        List<MenuItem> subMenuItems = null;
        Menu menu = null;

        menu = new Menu("Common Op Lists");
        menuItems.add(menu);
        subMenuItems = new ArrayList<>();
        for (String op : basicOps) {
            MenuItem menuItem = new MenuItem(op);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> opSequenceMenuAction(event));
            subMenuItems.add(menuItem);
        }
        menu.getItems().addAll(subMenuItems);

        subMenuItems = null;
        for (String op : OperationInfo.opOrders) {
            if (op.startsWith("Cascade-")) {
                if (subMenuItems != null) {
                    menu.getItems().addAll(subMenuItems);
                }
                menu = new Menu(op.substring(8));
                subMenuItems = new ArrayList<>();
                menuItems.add(menu);
            } else {
                MenuItem menuItem = new MenuItem(op);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> opMenuAction(event));
                subMenuItems.add(menuItem);
            }
        }
        if (menu != null) {
            menu.getItems().addAll(subMenuItems);
        }
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

    }

    public PolyChart getChart() {
        return chart;
    }
}
