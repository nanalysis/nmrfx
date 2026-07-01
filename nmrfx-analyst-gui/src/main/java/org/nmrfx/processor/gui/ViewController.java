package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.converter.IntegerStringConverter;
import org.nmrfx.chart.Axis;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.undo.ChartUndoLimits;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ViewController {
    private static final Logger log = LoggerFactory.getLogger(ViewController.class);
    private static final Background DEFAULT_BACKGROUND = null;
    private static final Background ERROR_BACKGROUND = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));
    SimpleDoubleProperty[][] viewLimitProps = new SimpleDoubleProperty[SpectrumStatusBar.DIM_NAMES.length][2];
    private final Spinner<Integer>[][] planeSpinner = new Spinner[SpectrumStatusBar.DIM_NAMES.length][2];
    private final ChangeListener<Integer>[][] planeListeners = new ChangeListener[SpectrumStatusBar.DIM_NAMES.length][2];
    private final CheckBox[] valueModeBox = new CheckBox[SpectrumStatusBar.DIM_NAMES.length];
    private boolean arrayMode = false;
    List<List<Node>> gridNodes = new ArrayList<>();
    FXMLController fxmlController;
    AttributesController attributesController;
    PolyChart chart;
    VBox viewBox;
    GridPane viewGridPane;
    private final ComboBox<DisplayMode> displayModeComboBox = new ComboBox<>();
    private SpectrumStatusBar.DataMode currentMode = SpectrumStatusBar.DataMode.FID;
    private int currentModeDimensions = 0;

    public enum DisplayMode {
        TRACES("Traces (1D)"),
        STACKPLOT("Stack Plot"),
        CONTOURS("Contours (2D)");
        private final String strValue;

        DisplayMode(String strValue) {
            this.strValue = strValue;
        }

        @Override
        public String toString() {
            return this.strValue;
        }
    }


    public ViewController(AttributesController attributesController, VBox viewBox) {
        this.attributesController = attributesController;
        this.fxmlController = attributesController.fxmlController;
        this.viewBox = viewBox;
        init();
    }

    private void init() {
        this.viewGridPane = new GridPane();
        HBox displayBox = new HBox();
        displayBox.setSpacing(10);
        viewBox.setSpacing(5);
        displayBox.getChildren().addAll(new Label("Display Mode:"), displayModeComboBox);
        viewBox.getChildren().add(displayBox);
        viewBox.getChildren().add(viewGridPane);
        createViewGrid();
        displayModeComboBox.getItems().setAll(ViewController.DisplayMode.values());
        displayModeComboBox.getSelectionModel().selectedItemProperty().addListener(e -> displayModeComboBoxSelectionChanged());
    }

    ComboBox<DisplayMode> getDisplayModeComboBox() {
        return displayModeComboBox;
    }

    List<PolyChart> getCharts(boolean all) {
        if (all) {
            return chart.getFXMLController().getCharts();
        } else {
            return Collections.singletonList(chart);
        }
    }

    private boolean isStacked() {
        PolyChart chart = fxmlController.getActiveChart();
        return chart.getChartProperties().getStackX() > 0.01 ||
                chart.getChartProperties().getStackY() > 0.01;
    }


    /**
     * Updates the spectrum status bar and the type of plot displayed in the active chart
     * based on the selected option.
     */
    private void displayModeComboBoxSelectionChanged() {
        PolyChart chart = fxmlController.getActiveChart();
        boolean autoScale = true;
        OptionalInt maxNDim = chart.getDatasetAttributes().stream().mapToInt(d -> d.nDim).max();
        if (maxNDim.isEmpty()) {
            log.warn("Unable to update display mode. No dimensions set.");
            return;
        }
        DisplayMode selected = displayModeComboBox.getSelectionModel().getSelectedItem();
        if (selected == DisplayMode.TRACES || selected == DisplayMode.STACKPLOT) {
            OptionalInt maxRows = chart.getDatasetAttributes().stream().
                    mapToInt(d -> d.nDim == 1 ? 1 : d.getDataset().getSizeReal(1)).max();
            if (maxRows.isEmpty()) {
                log.warn("Unable to update display mode. No rows set.");
                return;
            }
            chart.getDisDimProperty().set(PolyChart.DISDIM.OneDX);
            if (maxRows.getAsInt() > FXMLController.MAX_INITIAL_TRACES) {
                chart.setDrawlist(0);
            }

            if (selected == DisplayMode.STACKPLOT) {
                chart.clearDrawlist();
                if (!isStacked()) {
                    chart.getChartProperties().setStackX(0.35);
                    chart.getChartProperties().setStackY(0.75);
                }
            } else {
                chart.getChartProperties().setStackX(0.0);
                chart.getChartProperties().setStackY(0.0);
            }
            fxmlController.getStatusBar().set1DArray(maxNDim.getAsInt(), maxRows.getAsInt());
        } else if (selected == DisplayMode.CONTOURS) {
            chart.getDisDimProperty().set(PolyChart.DISDIM.TwoD);
            chart.getDatasetAttributes().getFirst().drawList.clear();
            autoScale = !chart.getDatasetAttributes().getFirst().getHasLevel();
            autoScale = true;
            chart.updateProjections();
            chart.updateProjectionScale();
            int nDim = maxNDim.getAsInt();
            fxmlController.getStatusBar().setMode(SpectrumStatusBar.DataMode.fromDimensions(nDim), nDim);
        }
        attributesController.setDimControls();
        chart.updateAxisType(true);
        chart.full();
        if (autoScale) {
            chart.autoScale();
        }
    }

    private List<DatasetAttributes> getDatasetAttributesList() {
        List<DatasetAttributes> result;
        result = new ArrayList<>();
        for (var aChart : getCharts(attributesController.allCharts())) {
            result.addAll(aChart.getDatasetAttributes());
        }
        return result;
    }

    private Optional<DatasetAttributes> getDatasetAttributes() {
        PolyChart chart = fxmlController.getActiveChart();
        Optional<DatasetAttributes> result;
        if (!chart.getDatasetAttributes().isEmpty()) {
            DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
            result = Optional.of(dataAttr);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private List<PeakListAttributes> getPeakListAttributes() {
        List<PeakListAttributes> result;
        result = new ArrayList<>();
        for (var aChart : getCharts(attributesController.allCharts())) {
            result.addAll(aChart.getPeakListAttributes());
        }
        return result;
    }


    private int findPlane(double value, int axNum) {
        PolyChart chart = fxmlController.getActiveChart();
        ObservableList<DatasetAttributes> dataAttrList = chart.getDatasetAttributes();
        int planeIndex = -1;
        if (!dataAttrList.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrList.get(0);
            if (chart.getAxes().getMode(axNum) == DatasetAttributes.AXMODE.PTS) {
                double[] values = dataAttr.getDataset().getValues(axNum);
                if (values != null) {
                    double min = Double.MAX_VALUE;
                    int iMin = -1;
                    for (int i = 0; i < values.length; i++) {
                        double delta = Math.abs(value - values[i]);
                        if (delta < min) {
                            min = delta;
                            iMin = i;
                        }
                    }
                    planeIndex = iMin;
                }
            }
        }
        return planeIndex;
    }


    private class SpinnerConverter extends IntegerStringConverter {
        final int axNum;
        final int spinNum;
        boolean valueMode = false;

        SpinnerConverter(int axNum, int spinNum) {
            this.axNum = axNum;
            this.spinNum = spinNum;
        }

        @Override
        public Integer fromString(String s) {
            int result = 1;
            Spinner<Integer> spinner = planeSpinner[axNum][spinNum];
            boolean showValue = valueMode && valueModeBox[axNum].isSelected();
            if (showValue) {
                return spinner.getValueFactory().getValue();
            }
            try {
                if (!s.isEmpty()) {
                    if (s.contains(".")) {
                        double planePPM = Double.parseDouble(s);
                        int planeIndex = findPlane(planePPM, axNum);
                        if (planeIndex == -1) {
                            var dataAttrOpt = getDatasetAttributes();
                            if (dataAttrOpt.isPresent()) {
                                DatasetAttributes dataAttr = dataAttrOpt.get();
                                planeIndex = DatasetAttributes.AXMODE.PPM.getIndex(dataAttr, axNum, planePPM);
                            }
                        }
                        result = planeIndex + 1;
                    } else {
                        result = Integer.parseInt(s);
                    }
                }
                spinner.getEditor().setBackground(DEFAULT_BACKGROUND);
            } catch (NumberFormatException nfE) {
                spinner.getEditor().setBackground(ERROR_BACKGROUND);
            }
            return result;
        }

        @Override
        public String toString(Integer iValue) {
            boolean showValue = valueMode && valueModeBox[axNum].isSelected();
            if (showValue) {
                var doubleOpt = getPlaneValue(axNum, iValue - 1);
                return doubleOpt.isPresent() ? String.format("%.2f", doubleOpt.get()) : "";
            } else {
                return String.valueOf(iValue);
            }
        }

        void setValueMode(boolean mode) {
            valueMode = mode;
        }
    }

    public void updateRowSpinner(int row, int axNum) {
        SpinnerValueFactory<Integer> planeFactory = planeSpinner[axNum][0].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[axNum][0]);
        planeFactory.setValue(row + 1);
        planeFactory.valueProperty().addListener(planeListeners[axNum][0]);
    }


    public void setPlaneRanges() {
        getDatasetAttributes().ifPresent(dataAttr -> {
            for (int axNum = 2; axNum < dataAttr.nDim; axNum++) {
                int dDim = dataAttr.dim[axNum];
                int size = dataAttr.getDataset().getSizeReal(dDim);
                setPlaneRanges(axNum, size);
            }
        });
    }

    private void setPlaneRanges(int iDim, int max) {
        for (int j = 0; j < 2; j++) {
            setPlaneRange(iDim, j, max);
        }
    }

    private void setPlaneRange(int iDim, int iSpin, int max) {
        SpinnerValueFactory.IntegerSpinnerValueFactory planeFactory = (SpinnerValueFactory.IntegerSpinnerValueFactory) planeSpinner[iDim][iSpin].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[iDim][iSpin]);
        planeFactory.setMin(1);
        planeFactory.setMax(max);
        planeFactory.valueProperty().addListener(planeListeners[iDim][iSpin]);
    }

    private void setPlaneRange(int iDim) {
        SpinnerValueFactory.IntegerSpinnerValueFactory planeFactory0 = (SpinnerValueFactory.IntegerSpinnerValueFactory) planeSpinner[iDim][0].getValueFactory();
        SpinnerValueFactory.IntegerSpinnerValueFactory planeFactory1 = (SpinnerValueFactory.IntegerSpinnerValueFactory) planeSpinner[iDim][1].getValueFactory();
        int min0;
        planeFactory1.valueProperty().removeListener(planeListeners[iDim][1]);
        min0 = planeFactory0.getValue();
        planeFactory1.setMin(min0);
        planeFactory1.valueProperty().addListener(planeListeners[iDim][1]);
    }

    private Optional<Double> getPlaneValue(int axNum, int plane) {
        var dataOpt = getDatasetAttributes();
        Double value = null;
        if (dataOpt.isPresent()) {
            DatasetAttributes dataAttr = dataOpt.get();
            PolyChart chart = fxmlController.getActiveChart();
            Dataset dataset = dataAttr.getDataset();
            if (!dataset.getFreqDomain(axNum) || chart.getAxes().getMode(axNum) == DatasetAttributes.AXMODE.PTS) {
                double[] values = dataset.getValues(axNum);
                if (values != null && values.length > plane) {
                    value = values[plane];
                } else {
                    value = (double) (plane + 1);
                }
            } else {
                value = DatasetAttributes.AXMODE.PPM.indexToValue(dataAttr, axNum, plane);
            }
        }
        return Optional.ofNullable(value);
    }

    public void updatePlaneSpinner(int plane, int axNum, int spinNum) {
        SpinnerValueFactory<Integer> planeFactory = planeSpinner[axNum][spinNum].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[axNum][spinNum]);
        planeFactory.setValue(plane + 1);
        planeFactory.valueProperty().addListener(planeListeners[axNum][spinNum]);
    }

    private void scrollPlane(ScrollEvent e, int iDim, int iSpin) {
        Spinner<Integer> spinner = planeSpinner[iDim][iSpin];

        double delta = e.getDeltaY();
        int nPlanes = (int) Math.round(delta / 10.0);
        if (nPlanes == 0) {
            nPlanes = delta < 0.0 ? -1 : 1;
        }
        nPlanes *= -1;  // scrolling up should increase.  Is this dependent on Mac scrolling settting
        SpinnerValueFactory<Integer> planeFactory = spinner.getValueFactory();
        planeFactory.increment(nPlanes);
    }

    private void updatePlane(int iDim, int iSpin, int plane, boolean shiftDown) {
        PolyChart chart = fxmlController.getActiveChart();
        if (!chart.getDatasetAttributes().isEmpty()) {
            DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
            plane--;
            MenuMode menuMode = getMenuMode(dataAttr, iDim);

            if (menuMode == MenuMode.ROW) {
                List<Integer> drawList = chart.getDrawList();
                int first = 0;
                int last = 0;
                int delta = 0;
                if ((drawList != null) && !drawList.isEmpty()) {
                     first = drawList.getFirst();
                     last = drawList.getLast();
                     delta = last -first;
                }
                if (iSpin == 0) {
                    if (delta == 0) {
                        chart.setDrawlist(plane);
                    } else {
                        first = plane;
                        last = first + delta;
                        drawList = new ArrayList<>();
                        for (int i=first;i<=last;i++) {
                            drawList.add(i);
                            chart.setDrawlist(drawList);
                        }
                    }
                } else {
                    last = plane;
                    drawList = new ArrayList<>();
                    for (int i=first;i<=last;i++) {
                        drawList.add(i);
                        chart.setDrawlist(drawList);
                    }
                }

                fxmlController.getActiveChart().refresh();
            } else {
                Axis axis = chart.getAxes().get(iDim);
                int[] pts = new int[2];
                pts[1] = chart.getAxes().getMode(iDim).getIndex(dataAttr, iDim, axis.getLowerBound());
                pts[0] = chart.getAxes().getMode(iDim).getIndex(dataAttr, iDim, axis.getUpperBound());
                int other = iSpin == 0 ? 1 : 0;
                int delta = pts[1] - pts[0];
                pts[iSpin] = plane;
                if ((iSpin == 0) && !shiftDown) {
                    pts[other] = pts[iSpin] + delta;
                }
                double ppm1 = chart.getAxes().getMode(iDim).indexToValue(dataAttr, iDim, pts[1]);
                double ppm2 = chart.getAxes().getMode(iDim).indexToValue(dataAttr, iDim, pts[0]);
                ChartUndoLimits undo = new ChartUndoLimits(fxmlController.getActiveChart());
                PolyChart polyChart = fxmlController.getActiveChart();
                polyChart.getAxes().setMinMax(iDim, ppm1, ppm2);
                fxmlController.getActiveChart().refresh();
                ChartUndoLimits redo = new ChartUndoLimits(fxmlController.getActiveChart());
                fxmlController.getUndoManager().add("plane", undo, redo);
            }
        }
    }

    private void updateSpinner(int iDim) {
        for (int j = 0; j < 2; j++) {
            SpinnerValueFactory<Integer> planeFactory = planeSpinner[iDim][j].getValueFactory();
            int value = planeFactory.getValue();
            String text = planeFactory.getConverter().toString(value);
            planeSpinner[iDim][j].getEditor().setText(text);
        }
    }

    private void initSpinners() {
        for (int i = 0; i < planeSpinner.length; i++) {
            final int iDim = i;
            for (int j = 0; j < 2; j++) {
                final int iSpin = j;
                Spinner<Integer> spinner = new Spinner<>(0, 127, 63);
                planeSpinner[i][j] = spinner;
                spinner.setEditable(true);
                spinner.getEditor().setPrefWidth(60);
                spinner.setPrefWidth(80);
                spinner.setOnScroll(e -> {
                    spinner.setUserData(e.isControlDown());
                    scrollPlane(e, iDim, iSpin);
                });
                spinner.addEventFilter(MouseEvent.MOUSE_PRESSED,
                        e -> spinner.setUserData(e.isControlDown()));
                planeListeners[i][j] = (ObservableValue<? extends Integer> observableValue, Integer oldValue, Integer newValue) -> {
                    if (newValue != null && !newValue.equals(oldValue)) {
                        Object spinData = spinner.getUserData();
                        boolean shiftDown = spinData != null ? ((Boolean) spinData).booleanValue() : false;
                        updatePlane(iDim, iSpin, newValue, shiftDown);
                        if (iSpin == 0) {
                            setPlaneRange(iDim);
                        }
                    }
                };
                SpinnerValueFactory<Integer> planeFactory = planeSpinner[i][j].getValueFactory();
                planeFactory.valueProperty().addListener(planeListeners[i][j]);
                SpinnerConverter converter = new SpinnerConverter(iDim, j);
                planeFactory.setConverter(converter);
            }
            valueModeBox[i] = new CheckBox("V");
            planeSpinner[i][0].editableProperty().bind(valueModeBox[i].selectedProperty().not());
            planeSpinner[i][1].editableProperty().bind(valueModeBox[i].selectedProperty().not());
            valueModeBox[i].setOnAction(e -> updateSpinner(iDim));
        }
    }

    private void updateDimMenu(PolyChart chart, DatasetAttributes attr, MenuButton dimMenu, int iAxis) {
        int nDim = attr.nDim;
        String rowName = SpectrumStatusBar.DIM_NAMES[iAxis];
        MenuItem menuItem = new MenuItem("Full");
        dimMenu.getItems().add(menuItem);
        menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iAxis));

        for (int iDim = 0; iDim < nDim; iDim++) {
            String dimName = attr.getDataset().getLabel(iDim);
            menuItem = new MenuItem(iDim + 1 + ":" + dimName);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> dimAction(rowName, dimName));
            dimMenu.getItems().add(menuItem);
            if (fxmlController.isPhaseSliderVisible()) {
                chart.updatePhaseDim();
            }
        }

    }

    MenuMode getMenuMode(DatasetAttributes attr, int iAxis) {
        int nDim = attr.getDataset().getNDim();
        int nFreqDim = attr.getDataset().getNFreqDims();
        boolean traceMode = displayModeComboBox.getValue() == DisplayMode.TRACES;
        if (iAxis == 0) {
            return MenuMode.DIM;
        } else {
            if (traceMode) {
                return MenuMode.ROW;
            } else {
                if (iAxis < nFreqDim) {
                    if (iAxis < nFreqDim - 1) {
                        return MenuMode.DIM;
                    } else {
                        return MenuMode.PLANE;
                    }
                } else {
                    return MenuMode.ROW;
                }
            }
        }
    }

    enum MenuMode {
        DIM,
        PLANE,
        ROW
    }

    private void updateXYMenu(MenuButton dimMenu, int iAxis) {
        PolyChart chart = fxmlController.getActiveChart();
        dimMenu.getItems().clear();
        chart.getFirstDatasetAttributes().ifPresent(attr -> {
            MenuMode menuMode = getMenuMode(attr, iAxis);
            switch (menuMode) {
                case MenuMode.DIM -> updateDimMenu(chart, attr, dimMenu, iAxis);
                case MenuMode.PLANE -> updatePlaneMenu(chart, dimMenu, iAxis);
                case MenuMode.ROW -> updateRowMenu(chart, dimMenu, iAxis);
            }
        });
    }

    private void updatePlaneMenu(PolyChart chart, MenuButton mButton, int iAxis) {
        MenuItem fullItem = new MenuItem("Full");
        fullItem.setOnAction(e -> {
            chart.full(iAxis);
            chart.refresh();
        });
        mButton.getItems().add(fullItem);

        MenuItem centerItem = new MenuItem("Center");
        centerItem.setOnAction(e -> {
            chart.center(iAxis);
            chart.refresh();
        });
        mButton.getItems().add(centerItem);

        MenuItem firstItem = new MenuItem("First");
        firstItem.setOnAction(e -> {
            chart.firstPlane(iAxis);
            chart.refresh();
        });
        mButton.getItems().add(firstItem);

        MenuItem lastItem = new MenuItem("Last");
        lastItem.setOnAction(e -> {
            chart.lastPlane(iAxis);
            chart.refresh();
        });
        mButton.getItems().add(lastItem);

        MenuItem maxItem = new MenuItem("Max");
        maxItem.setOnAction(e -> {
            chart.gotoMaxPlane();
            chart.refresh();
        });
        mButton.getItems().add(maxItem);


    }


    private void dimAction(String rowName, String dimName) {
        fxmlController.setDim(rowName, dimName);
    }

    private void updateRowMenu(PolyChart chart, MenuButton mButton, int iAxis) {
        MenuItem fullItem = new MenuItem("Full");
        fullItem.setOnAction(e -> {
            chart.clearDrawlist();
            chart.refresh();
        });
        mButton.getItems().add(fullItem);

        MenuItem firstItem = new MenuItem("First");
        firstItem.setOnAction(e -> {
            chart.setDrawlist(0);
            chart.refresh();
        });
        mButton.getItems().add(firstItem);

        MenuItem lastItem = new MenuItem("Last");
        lastItem.setOnAction(e -> {
            chart.setDrawlist(1000);
            chart.refresh();
        });
        mButton.getItems().add(lastItem);
    }


    private void dimMenuAction(ActionEvent event, int iAxis) {
        MenuItem menuItem = (MenuItem) event.getSource();
        PolyChart chart = fxmlController.getActiveChart();
        if (menuItem.getText().equals("Full")) {
            chart.full(iAxis);
        } else if (menuItem.getText().equals("Center")) {
            chart.center(iAxis);
        } else if (menuItem.getText().equals("First")) {
            chart.firstPlane(iAxis);
        } else if (menuItem.getText().equals("Last")) {
            chart.lastPlane(iAxis);
        } else if (menuItem.getText().equals("Max")) {
            chart.gotoMaxPlane();
        }
        chart.refresh();
    }

    private void createViewGrid() {
        initSpinners();
        viewGridPane.setHgap(10);
        viewGridPane.setVgap(5);
        for (int i = 0; i < SpectrumStatusBar.DIM_NAMES.length; i++) {
            List<Node> rowNodes = new ArrayList<>();
            gridNodes.add(rowNodes);
            final int iAxis = i;
            String dimName = SpectrumStatusBar.DIM_NAMES[i];
            MenuButton mButton = new MenuButton(dimName);
            viewGridPane.add(mButton, 0, i);
            double textWidth = 50.0;
            double spinnerWidth = 75.0;
            rowNodes.add(mButton);
            viewLimitProps[i][0] = new SimpleDoubleProperty(0.0);
            viewLimitProps[i][1] = new SimpleDoubleProperty(0.0);
            TextField lowField = GUIUtils.getDoubleTextField(viewLimitProps[i][0], 2);
            TextField upField = GUIUtils.getDoubleTextField(viewLimitProps[i][1], 2);
            lowField.setPrefWidth(textWidth);
            upField.setPrefWidth(textWidth);
            lowField.setOnKeyReleased(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    keyTyped(iAxis, 0);
                }
            });
            upField.setOnKeyReleased(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    keyTyped(iAxis, 1);
                }
            });

            mButton.showingProperty().addListener(e -> updateXYMenu(mButton, iAxis));
            viewGridPane.add(upField, 1, i);
            viewGridPane.add(planeSpinner[i][0], 2, i);
            viewGridPane.add(lowField, 3, i);
            viewGridPane.add(planeSpinner[i][1], 4, i);
            planeSpinner[i][0].setPrefWidth(spinnerWidth);
            planeSpinner[i][1].setPrefWidth(spinnerWidth);
            rowNodes.add(lowField);
            rowNodes.add(upField);
            rowNodes.add(planeSpinner[i][0]);
            rowNodes.add(planeSpinner[i][1]);
        }
    }

    void keyTyped(int iAxis, int ij) {
        double value = viewLimitProps[iAxis][ij].getValue();
        var axes = chart.getAxes();
        if (ij == 0) {
            axes.get(iAxis).setLowerBound(value);
        } else {
            axes.get(iAxis).setUpperBound(value);
            if (iAxis > 1) {
                axes.get(iAxis).setLowerBound(value);
            }
        }
        chart.refresh();
    }

    void updateGridNodes(int n) {
        for (int i = 0; i < gridNodes.size(); i++) {
            var nodes = gridNodes.get(i);
            boolean state = i < n;
            for (Node node : nodes) {
                node.setVisible(state);
                node.setManaged(state);
            }
        }
    }

    void updateView(PolyChart chart) {
        if ((viewLimitProps != null) && !chart.getDatasetAttributes().isEmpty()) {
            DisplayMode displayMode = displayModeComboBox.getValue();
            DatasetAttributes dataAttr = chart.getDatasetAttributes().getFirst();
            int nFreqDim = displayMode == DisplayMode.TRACES ? 1 : dataAttr.getDataset().getNFreqDims();
            int nDim = dataAttr.getDataset().getNDim();
            var axes = chart.getAxes();
            int nAxes = axes.count();
            nAxes = Math.min(nDim, nAxes);
            updateGridNodes(nAxes);
            for (int i = 0; i < nAxes; i++) {
                Axis axis = chart.getAxes().get(i);
                if (i < nFreqDim) {
                    viewLimitProps[i][0].set(axis.getLowerBound());
                    viewLimitProps[i][1].set(axis.getUpperBound());
                }
            }
            for (int axNum = 0; axNum < nAxes; axNum++) {
                Axis axis = chart.getAxes().get(axNum);
                var axMode = chart.getAxes().getMode(axNum);
                int indexL = axMode.getIndex(dataAttr, axNum, axis.getLowerBound());
                int indexU = axMode.getIndex(dataAttr, axNum, axis.getUpperBound());
                int finalAxNum = axNum;
                int dDim = dataAttr.dim[axNum];
                int size = dataAttr.getDataset().getSizeReal(dDim);
                if (axNum >= nFreqDim) {
                    if ((chart.getDrawList() != null) && (!chart.getDrawList().isEmpty())) {
                        indexL = chart.getDrawList().getFirst();
                        indexU = chart.getDrawList().getLast();
                    }
                    getPlaneValue(axNum, indexL).ifPresent(v -> {
                        viewLimitProps[finalAxNum][0].set(v);
                    });
                    getPlaneValue(axNum, indexU).ifPresent(v -> {
                        viewLimitProps[finalAxNum][1].set(v);
                    });
                    setPlaneRanges(axNum, size);
                    updatePlaneSpinner(indexL, axNum, 0);
                    updatePlaneSpinner(indexU, axNum, 1);
                } else {
                    setPlaneRanges(axNum, size);
                    if (axMode == DatasetAttributes.AXMODE.PTS) {
                        updatePlaneSpinner(indexL, axNum, 0);
                        updatePlaneSpinner(indexU, axNum, 1);
                    } else {
                        updatePlaneSpinner(indexL, axNum, 1);
                        updatePlaneSpinner(indexU, axNum, 0);
                    }
                }
                setPlaneRange(axNum);
            }
        }
    }
}
