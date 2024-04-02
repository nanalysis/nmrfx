package org.nmrfx.processor.gui;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.datasets.DatasetGroupIndex;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.utils.GUIUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class NavigatorGUI implements Initializable, StageBasedController {
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
    private ChoiceBox<Integer> scanMaxN;
    @FXML
    private ChoiceBox<Double> scanRatio;
    List<RadioButton> vectorDimButtons = new ArrayList<>();
    private final List<String> realImagChoices = new ArrayList<>();
    ChangeListener<String> vecNumListener;
    int[] rowIndices;
    int[] vecSizes;

    Stage stage = null;
    ProcessorController processorController;
    ChartProcessor chartProcessor;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        scanRatio.getItems().addAll(0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 10.0);
        scanRatio.setValue(3.0);
        scanMaxN.getItems().addAll(5, 10, 20, 50, 100, 200);
        scanMaxN.setValue(50);

    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static NavigatorGUI create(ProcessorController processorController) {
        NavigatorGUI controller = Fxml.load(NavigatorGUI.class, "NavigatorScene.fxml")
                .withNewStage("Navigator")
                .getController();
        controller.processorController = processorController;
        controller.chartProcessor = processorController.chartProcessor;
        controller.setupListeners();
        controller.stage.setAlwaysOnTop(true);
        return controller;
    }


    private void setupListeners() {
        rowToggleGroup.selectedToggleProperty().addListener(e -> handleRowDimChange());
        vecNumListener = (observableValue, string, string2) -> {
            String text = realImagChoiceBox.getValue();
            int vecNum = realImagChoices.indexOf(text);
            chartProcessor.setVector(vecNum);
            setFileIndex();
        };
        corruptedIndicesListView.getSelectionModel().selectedItemProperty().addListener(e -> corruptedIndexListener());

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
            if ((rows.length > 0) && (vecNum1 != null) && vecNum1.isVisible()) {
                int size = vecSizes[iDim - 1];
                updateVectorSlider(size);
                vecNum1.setMax(size);
            }
        }
    }

    protected void setRowLabel(int iDim, int row, int size, boolean allLabels) {
        if (allLabels || vectorDimButtons.get(iDim).isSelected()) {
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
        updateRowLabels(iDim, iRow, false);
        int[] rows = getRows();
        chartProcessor.vecRow(rows);
        processorController.chart.layoutPlotChildren();
    }

    private void updateRowLabels(int iDim, int i, boolean allRows) {
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
            setRowLabel(iDim - 1, i + 1, size, allRows);
        }
    }

    private void corruptedIndexListener() {
        DatasetGroupIndex groupIndex = corruptedIndicesListView.getSelectionModel().getSelectedItem();
        if (groupIndex != null) {
            int[] indices = groupIndex.getIndices();
            for (int iDim = 0; iDim < indices.length; iDim++) {
                updateRowLabels(iDim + 1, indices[iDim], true);
            }
            int[] rows = getRows();
            String realImagChoice = groupIndex.getGroupIndex();
            if (!realImagChoice.equals("")) {
                realImagChoiceBox.setValue(realImagChoice);
            }
            chartProcessor.vecRow(rows);
        }
        processorController.chart.layoutPlotChildren();
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


    void showStage() {
        stage.show();
        stage.toFront();
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
        processorController.updateScriptDisplay();
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
