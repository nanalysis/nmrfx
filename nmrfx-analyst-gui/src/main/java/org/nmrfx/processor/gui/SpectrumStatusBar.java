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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.converter.IntegerStringConverter;
import org.controlsfx.control.SegmentedButton;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chart.Axis;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.undo.ChartUndoLimits;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.utils.properties.CustomNumberTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Bruce Johnson
 */
@PluginAPI("parametric")
public class SpectrumStatusBar {
    private static final Logger log = LoggerFactory.getLogger(SpectrumStatusBar.class);
    private static final int MAX_SPINNERS = 4;
    private static final String[] DIM_NAMES = {"X", "Y", "Z", "A", "B", "C", "D", "E"};
    private static final String[] ROW_NAMES = {"X", "Row", "Plane", "A", "B", "C", "D", "E"};
    private static final Background DEFAULT_BACKGROUND = null;
    private static final Background ERROR_BACKGROUND = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));

    private final FXMLController controller;

    // cursor, measure spinners, etc
    private final ToolBar primaryToolbar = new ToolBar();
    private final CheckBox complexStatus = new CheckBox("Complex");
    private final CustomNumberTextField[][] crossText = new CustomNumberTextField[2][2];
    private final StackPane[][] crossTextIcons = new StackPane[2][2];
    private final StackPane[][] limitTextIcons = new StackPane[2][2];
    private final boolean[][] iconStates = new boolean[2][2];
    private final Spinner<Integer>[][] planeSpinner = new Spinner[MAX_SPINNERS][2];
    private final ChangeListener<Integer>[][] planeListeners = new ChangeListener[MAX_SPINNERS][2];
    private final CheckBox[] valueModeBox = new CheckBox[MAX_SPINNERS];
    private final MenuButton[] dimMenus = new MenuButton[MAX_SPINNERS + 2];
    private final MenuButton[] rowMenus = new MenuButton[MAX_SPINNERS];
    private final ComboBox<DisplayMode> displayModeComboBox = new ComboBox<>();
    private final ChangeListener<PolyChart.DISDIM> displayedDimensionsListener = this::chartDisplayDimensionChanged;
    private final SegmentedButton cursorButtons = new SegmentedButton();
    private final ToggleButton tableButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.TABLE, "Table",
            AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.LEFT);


    // tools & additional buttons
    private final ToolBar secondaryToolbar = new ToolBar();
    private final MenuButton toolButton = new MenuButton("Tools");
    private final List<ButtonBase> specialButtons = new ArrayList<>();
    private final ToggleButton phaserButton = new ToggleButton("Phasing");
    private final CheckBox sliceStatusCheckBox = new CheckBox("Slices");



    private boolean arrayMode = false;
    private DataMode currentMode = DataMode.FID;
    private int currentModeDimensions = 0;

    private Cursor preSliceCursor = null;
    public SpectrumStatusBar(FXMLController controller) {
        this.controller = controller;
    }

    // can't be called from constructor: relies on controller.getActiveChart(), which returns null at construction
    public void init() {
        tableButton.setOnAction(e -> controller.updateScannerTool(tableButton));
        initCursorButtonGroup();
        setupTools();

        initCrossText();

        Pane filler = createHorizontalSpacer();
        primaryToolbar.getItems().add(filler);

        initSpinners();


        for (int i = 0; i < dimMenus.length; i++) {
            final int iAxis = i;
            String rowName = DIM_NAMES[iAxis];

            MenuButton mButton = new MenuButton(rowName);
            dimMenus[i] = mButton;
            if (iAxis < 2) {
                mButton.showingProperty().addListener(e -> updateXYMenu(mButton, iAxis));
            } else {
                MenuItem menuItem = new MenuItem("Full");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iAxis));
                menuItem = new MenuItem("Center");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iAxis));
                menuItem = new MenuItem("First");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iAxis));
                menuItem = new MenuItem("Last");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iAxis));
                menuItem = new MenuItem("Max");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iAxis));
            }
        }
        displayModeComboBox.getItems().setAll(DisplayMode.values());
        displayModeComboBox.getSelectionModel().selectedItemProperty().addListener(e -> displayModeComboBoxSelectionChanged());


        for (int i = 0; i < rowMenus.length; i++) {
            final int iAxis = i + 1;
            String rowName = ROW_NAMES[iAxis];

            MenuButton mButton = new MenuButton(rowName);
            rowMenus[i] = mButton;
            MenuItem menuItem = new MenuItem("Full");
            mButton.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, this::rowMenuAction);
            menuItem = new MenuItem("First");
            mButton.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, this::rowMenuAction);
            menuItem = new MenuItem("Last");
            mButton.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, this::rowMenuAction);

        }
        filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        primaryToolbar.getItems().add(filler);
        primaryToolbar.getItems().add(complexStatus);
        complexStatus.setOnAction(this::complexStatusChanged);
        primaryToolbar.getItems().add(sliceStatusCheckBox);
        phaserButton.setOnAction(event -> controller.updatePhaser(phaserButton.isSelected()));
        phaserButton.disableProperty().bind(controller.processControllerVisibleProperty());

        primaryToolbar.getItems().add(phaserButton);

        controller.getActiveChart().getDisDimProperty().addListener(displayedDimensionsListener);
        PolyChartManager.getInstance().activeChartProperty().addListener(new WeakChangeListener<PolyChart>(this::setChart));
        sliceStatusCheckBox.setOnAction(e -> updateSlices(true));
        sliceStatusCheckBox.selectedProperty().bindBidirectional(controller.sliceStatusProperty());
    }

    private void initCrossText() {
        for (int index = 0; index < 2; index++) {
            for (int orientationIndex = 1; orientationIndex >= 0; orientationIndex--) {
                Orientation orientation = Orientation.values()[orientationIndex];
                crossText[index][orientationIndex] = new CustomNumberTextField();
                crossText[index][orientationIndex].setPrefWidth(75.0);
                crossText[index][orientationIndex].setFunction(controller.getCrossHairUpdateFunction(index, orientation));

                primaryToolbar.getItems().add(crossText[index][orientationIndex]);
                StackPane stackPane = makeIcon(index, orientation, false);
                crossTextIcons[index][orientationIndex] = stackPane;
                crossText[index][orientationIndex].setRight(stackPane);
                StackPane stackPane2 = makeIcon(index, orientation, true);
                limitTextIcons[index][orientationIndex] = stackPane2;

                if (index == 1) {
                    crossText[index][orientationIndex].setStyle("-fx-text-inner-color: red;");
                } else {
                    crossText[index][orientationIndex].setStyle("-fx-text-inner-color: black;");
                }
            }
        }
    }

    private void initSpinners() {
        for (int i = 0; i < planeSpinner.length; i++) {
            final int iDim = i + 2;
            for (int j = 0; j < 2; j++) {
                final int iSpin = j;
                Spinner<Integer> spinner = new Spinner<>(0, 127, 63);
                planeSpinner[i][j] = spinner;
                spinner.setEditable(true);
                spinner.getEditor().setPrefWidth(60);
                spinner.setPrefWidth(80);
                spinner.setOnScroll(e -> {
                    spinner.setUserData(e.isShiftDown());
                    scrollPlane(e, iDim - 2, iSpin);
                });
                spinner.addEventFilter(MouseEvent.MOUSE_PRESSED,
                        e -> spinner.setUserData(e.isShiftDown()));
                planeListeners[i][j] = (ObservableValue<? extends Integer> observableValue, Integer oldValue, Integer newValue) -> {
                    if (newValue != null && !newValue.equals(oldValue)) {
                        updatePlane(iDim, iSpin, newValue, iSpin == 1);
                        if (iSpin == 1) {
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

    private void initCursorButtonGroup() {
        Arrays.stream(CanvasCursor.values())
                .map(SpectrumStatusBar::createCursorToggleButton)
                .forEach(cursorButtons.getButtons()::add);
        cursorButtons.getButtons().get(CanvasCursor.SELECTOR.ordinal()).setSelected(true);
        cursorButtons.getToggleGroup().selectedToggleProperty()
                .addListener((observable, oldValue, newValue) -> cursorButtonToggled(newValue));
    }

    private void cursorButtonToggled(Toggle toggle) {
        if (toggle != null && toggle.getUserData() instanceof CanvasCursor selected) {
            controller.setCursor(selected.getCursor());
        }
    }

    @PluginAPI("parametric")
    public FXMLController getController() {
        return controller;
    }

    public List<Node> getToolbars() {
        return List.of(primaryToolbar, secondaryToolbar);
    }

    public void updateCursorBox() {
        for (var button : cursorButtons.getButtons()) {
            if (button.getUserData() instanceof CanvasCursor canvasCursor
                    && canvasCursor.getCursor() == controller.getCurrentCursor()) {
                button.setSelected(true);
                break;
            }
        }
        if (!CanvasCursor.isCrosshair(controller.getCurrentCursor())) {
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    crossText[i][j].resetMinMax();
                }
            }
        }
    }

    private void chartDisplayDimensionChanged(ObservableValue<? extends PolyChart.DISDIM> observable, PolyChart.DISDIM oldValue, PolyChart.DISDIM newValue) {
        if (newValue == PolyChart.DISDIM.OneDX) {
            displayModeComboBox.setValue(DisplayMode.TRACES);
        } else {
            displayModeComboBox.setValue(DisplayMode.CONTOURS);
        }
    }

    public void addToolBarButtons(ButtonBase... buttons) {
        Collections.addAll(specialButtons, buttons);
    }

    @PluginAPI("parametric")
    public void addToToolMenu(MenuItem menuItem) {
        toolButton.getItems().add(menuItem);
    }

    public void addToToolMenu(String menuText, MenuItem newItem) {
        for (MenuItem menuItem : toolButton.getItems()) {
            if (menuItem instanceof Menu menu && menu.getText().equals(menuText)) {
                menu.getItems().add(newItem);
            }
        }
    }

    private void setupTools() {
        Menu specToolMenu = new Menu("Spectrum Tools");

        MenuItem measureMenuItem = new MenuItem("Show Measure Bar");
        measureMenuItem.setOnAction(e -> controller.showSpectrumMeasureBar());
        MenuItem analyzerMenuItem = new MenuItem("Show Analyzer Bar");
        analyzerMenuItem.setOnAction(e -> controller.showAnalyzerBar());

        specToolMenu.getItems().addAll(measureMenuItem, analyzerMenuItem);
        addToToolMenu(specToolMenu);
    }

    private StackPane makeIcon(int i, Orientation orientation, boolean boundMode) {
        StackPane stackPane = new StackPane();
        stackPane.setPadding(Insets.EMPTY);
        Rectangle rect = new Rectangle(10, 10);
        rect.setFill(Color.LIGHTGREY);
        rect.setStroke(Color.LIGHTGREY);
        Line line = new Line();
        if (orientation == Orientation.HORIZONTAL) {
            line.setStartX(0.0f);
            line.setStartY(8.0f);
            line.setEndX(10.0f);
            line.setEndY(8.0f);
            if (boundMode) {
                if (i == 0) {
                    line.setTranslateY(4);
                } else {
                    line.setTranslateY(-4);
                }
            }
        } else {
            line.setStartX(8.0f);
            line.setStartY(0.0f);
            line.setEndX(8.0f);
            line.setEndY(10.0f);
            if (boundMode) {
                if (i == 0) {
                    line.setTranslateX(-4);
                } else {
                    line.setTranslateX(4);
                }
            }
        }
        stackPane.getChildren().add(rect);
        stackPane.getChildren().add(line);
        if (i == 1) {
            line.setStroke(Color.RED);
        } else {
            line.setStroke(Color.BLACK);
        }
        rect.setMouseTransparent(true);
        line.setMouseTransparent(true);
        stackPane.setOnMouseClicked(e -> {
            controller.toggleCrossHairState(i, orientation);
            e.consume();
        });
        return stackPane;
    }

    private void setChart(ObservableValue<? extends PolyChart> observable, PolyChart oldChart, PolyChart newChart) {
        if (controller.getCharts().contains(oldChart)) {
            oldChart.getDisDimProperty().removeListener(displayedDimensionsListener);
        } else if (controller.getCharts().contains(newChart)) {
            newChart.getDisDimProperty().removeListener(displayedDimensionsListener);
            newChart.getDisDimProperty().addListener(displayedDimensionsListener);
            if (!newChart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = newChart.getDatasetAttributes().get(0);
                for (int axNum = 2; axNum < dataAttr.nDim; axNum++) {
                    Axis axis = newChart.getAxes().get(axNum);
                    int indexL = newChart.getAxes().getMode(axNum).getIndex(dataAttr, axNum, axis.getLowerBound());
                    int indexU = newChart.getAxes().getMode(axNum).getIndex(dataAttr, axNum, axis.getUpperBound());
                    int dDim = dataAttr.dim[axNum];
                    int size = dataAttr.getDataset().getSizeReal(dDim);
                    setPlaneRanges(axNum, size);
                    updatePlaneSpinner(indexL, axNum, 0);
                    updatePlaneSpinner(indexU, axNum, 1);
                }
            }
        }
    }

    private void setPlaneRanges() {
        getDatasetAttributes().ifPresent(dataAttr -> {
            for (int axNum = 2; axNum < dataAttr.nDim; axNum++) {
                int dDim = dataAttr.dim[axNum];
                int size = dataAttr.getDataset().getSizeReal(dDim);
                setPlaneRanges(axNum, size);
            }
        });
    }
    private void updateSpinner(int iDim) {
        for (int j = 0; j < 2; j++) {
            SpinnerValueFactory<Integer> planeFactory = planeSpinner[iDim - 2][j].getValueFactory();
            int value = planeFactory.getValue();
            String text = planeFactory.getConverter().toString(value);
            planeSpinner[iDim - 2][j].getEditor().setText(text);
        }
    }

    public void updateRowSpinner(int row, int axNum) {
        SpinnerValueFactory<Integer> planeFactory = planeSpinner[axNum - 1][0].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[axNum - 1][0]);
        planeFactory.setValue(row + 1);
        planeFactory.valueProperty().addListener(planeListeners[axNum - 1][0]);
    }

    private Optional<DatasetAttributes> getDatasetAttributes() {
        PolyChart chart = controller.getActiveChart();
        Optional<DatasetAttributes> result;
        if (!chart.getDatasetAttributes().isEmpty()) {
            DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
            result = Optional.of(dataAttr);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private int findPlane(double value, int axNum) {
        PolyChart chart = controller.getActiveChart();
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

    private Optional<Double> getPlaneValue(int axNum, int plane) {
        var dataOpt = getDatasetAttributes();
        Double value = null;
        if (dataOpt.isPresent()) {
            DatasetAttributes dataAttr = dataOpt.get();
            PolyChart chart = controller.getActiveChart();
            if (chart.getAxes().getMode(axNum) == DatasetAttributes.AXMODE.PTS) {
                double[] values = dataAttr.getDataset().getValues(axNum);
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
        SpinnerValueFactory<Integer> planeFactory = planeSpinner[axNum - 2][spinNum].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[axNum - 2][spinNum]);
        planeFactory.setValue(plane + 1);
        planeFactory.valueProperty().addListener(planeListeners[axNum - 2][spinNum]);
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
        plane--;
        if (arrayMode) {
            controller.getActiveChart().setDrawlist(plane);
            controller.getActiveChart().refresh();
        } else {
            PolyChart chart = controller.getActiveChart();

            if (!chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                Axis axis = chart.getAxes().get(iDim);
                int[] pts = new int[2];
                pts[0] = chart.getAxes().getMode(iDim).getIndex(dataAttr, iDim, axis.getLowerBound());
                pts[1] = chart.getAxes().getMode(iDim).getIndex(dataAttr, iDim, axis.getUpperBound());
                int other = iSpin == 0 ? 1 : 0;
                int delta = pts[other] - pts[iSpin];
                pts[iSpin] = plane;
                if (!shiftDown) {
                    pts[other] = pts[iSpin] + delta;
                }
                double ppm1 = chart.getAxes().getMode(iDim).indexToValue(dataAttr, iDim, pts[0]);
                double ppm2 = chart.getAxes().getMode(iDim).indexToValue(dataAttr, iDim, pts[1]);
                ChartUndoLimits undo = new ChartUndoLimits(controller.getActiveChart());
                PolyChart polyChart = controller.getActiveChart();
                polyChart.getAxes().setMinMax(iDim, ppm1, ppm2);
                controller.getActiveChart().refresh();
                ChartUndoLimits redo = new ChartUndoLimits(controller.getActiveChart());
                controller.getUndoManager().add("plane", undo, redo);
            }
        }
    }

    private void complexStatusChanged(ActionEvent event) {
        controller.getActiveChart().layoutPlotChildren();
    }

    public void setCrossTextRange(int index, Orientation orientation, double min, double max) {
        if (CanvasCursor.isCrosshair(controller.getCurrentCursor())) {
            crossText[index][orientation.ordinal()].setMin(min);
            crossText[index][orientation.ordinal()].setMax(max);
        } else {
            crossText[index][orientation.ordinal()].resetMinMax();
        }
    }

    private void setPlaneRanges(int iDim, int max) {
        for (int j = 0; j < 2; j++) {
            setPlaneRange(iDim, j, max);
        }
    }

    private void setPlaneRange(int iDim, int iSpin, int max) {
        SpinnerValueFactory.IntegerSpinnerValueFactory planeFactory = (SpinnerValueFactory.IntegerSpinnerValueFactory) planeSpinner[iDim - 2][iSpin].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[iDim - 2][iSpin]);
        planeFactory.setMin(1);
        planeFactory.setMax(max);
        planeFactory.valueProperty().addListener(planeListeners[iDim - 2][iSpin]);
    }

    private void setPlaneRange(int iDim) {
        SpinnerValueFactory.IntegerSpinnerValueFactory planeFactory0 = (SpinnerValueFactory.IntegerSpinnerValueFactory) planeSpinner[iDim - 2][0].getValueFactory();
        SpinnerValueFactory.IntegerSpinnerValueFactory planeFactory1 = (SpinnerValueFactory.IntegerSpinnerValueFactory) planeSpinner[iDim - 2][1].getValueFactory();
        int delta = planeFactory1.getValue() - planeFactory0.getValue();
        int max0;
        int min0;
        planeFactory0.valueProperty().removeListener(planeListeners[iDim - 2][0]);
        if (delta < 0) {
            min0 = -delta + 1;
            max0 = planeFactory1.getMax();
        } else {
            min0 = 1;
            max0 = planeFactory1.getMax() - delta;
        }
        planeFactory0.setMin(min0);
        planeFactory0.setMax(max0);
        planeFactory0.valueProperty().addListener(planeListeners[iDim - 2][0]);
    }


    public void set1DArray(int nDim, int nRows) {
        arrayMode = true;
        setPlaneRanges(2, nRows);
        updatePrimaryToolbarFor1DArray(nDim);
        updateSecondaryToolbarFor1DArray();
    }

    private void updatePrimaryToolbarFor1DArray(int nDim) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(tableButton);
        if (isStacked()) {
            displayModeComboBox.getSelectionModel().select(DisplayMode.STACKPLOT);
        } else {
            displayModeComboBox.getSelectionModel().select(DisplayMode.TRACES);
        }
        nodes.add(displayModeComboBox);
        nodes.add(createHorizontalSpacer());

        nodes.add(new Label("Cursor:"));
        cursorButtons.getButtons().get(CanvasCursor.REGION.ordinal()).setDisable(false);
        nodes.add(cursorButtons);
        for (int j = 1; j >= 0; j--) {
            if (j == 1) {
                nodes.add(new Label("X:"));
            } else {
                nodes.add(new Label("I:"));
            }
            for (int i = 0; i < 2; i++) {
                nodes.add(crossText[i][j]);
            }
        }
        nodes.add(createHorizontalSpacer());
        PolyChart activeChart = controller.getActiveChart();
        List<Integer> drawList;
        for (int i = 1; i < nDim; i++) {
            ((SpinnerConverter) planeSpinner[i - 1][0].getValueFactory().getConverter()).setValueMode(false);
            drawList = activeChart.getDrawList();
            if (!drawList.isEmpty()) {
                // Use the current drawlist and update the spinner to the first number
                updateRowSpinner(drawList.get(0), i);
            }
            nodes.add(rowMenus[i - 1]);
            nodes.add(planeSpinner[i - 1][0]);
            Pane nodeFiller = createHorizontalSpacer();
            nodes.add(nodeFiller);
        }
        nodes.add(phaserButton);
        primaryToolbar.getItems().setAll(nodes);
    }

    private void updateSecondaryToolbarFor1DArray() {
        secondaryToolbar.getItems().clear();
        secondaryToolbar.getItems().add(toolButton);
    }

    public DataMode getMode() {
        return currentMode;
    }

    public void setMode(DataMode mode) {
        if (mode == DataMode.DATASET_ND_PLUS) {
            log.warn("Setting mode 3D+ without setting dimension, assuming 3D data.");
        }
        setMode(mode, mode.ordinal());
    }

    public int getModeDimensions() {
        return currentModeDimensions;
    }

    public void setMode(DataMode mode, int dimensions) {
        currentMode = mode;
        currentModeDimensions = dimensions;
        arrayMode = false;
        setupPrimaryToolbarForSelectedMode();
        setupSecondaryToolbarForSelectedMode();
        setPlaneRanges();
    }

    private void setupPrimaryToolbarForSelectedMode() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(tableButton);
        if (currentMode == DataMode.DATASET_1D) {
            cursorButtons.getButtons().get(CanvasCursor.REGION.ordinal()).setDisable(false);
        } else if (currentMode == DataMode.DATASET_2D || currentMode == DataMode.DATASET_ND_PLUS) {
            cursorButtons.getButtons().get(CanvasCursor.REGION.ordinal()).setDisable(true);
        }

        if (currentMode == DataMode.DATASET_2D) {
            displayModeComboBox.getSelectionModel().select(DisplayMode.CONTOURS);
            nodes.add(displayModeComboBox);
        }

        nodes.add(createHorizontalSpacer());
        nodes.add(new Label("Cursor:"));
        nodes.add(cursorButtons);

        //first dimension cross-hair
        if (currentMode == DataMode.DATASET_2D || currentMode == DataMode.DATASET_ND_PLUS) {
            nodes.add(dimMenus[0]);
        }
        nodes.add(crossText[0][1]);
        nodes.add(crossText[1][1]);

        //second dimension cross-hair
        if (currentMode == DataMode.DATASET_ND_PLUS) {
            nodes.add(dimMenus[1]);
        } else if (currentMode == DataMode.DATASET_2D) {
            nodes.add(new Label("Y:"));
        }
        nodes.add(crossText[0][0]);
        nodes.add(crossText[1][0]);
        nodes.add(createHorizontalSpacer());

        // additional dimension spinners
        for (int i = 2; i < currentModeDimensions; i++) {
            nodes.add(dimMenus[i]);
            nodes.add(planeSpinner[i - 2][0]);
            nodes.add(planeSpinner[i - 2][1]);
            ((SpinnerConverter) planeSpinner[i - 2][0].getValueFactory().getConverter()).setValueMode(true);
            ((SpinnerConverter) planeSpinner[i - 2][1].getValueFactory().getConverter()).setValueMode(true);
            nodes.add(valueModeBox[i - 2]);
            nodes.add(createHorizontalSpacer());
        }

        // complex checkbox, only for FID
        if (currentMode == DataMode.FID) {
            nodes.add(complexStatus);
        }
        if (currentMode == DataMode.DATASET_2D || currentMode == DataMode.DATASET_ND_PLUS) {
            nodes.add(sliceStatusCheckBox);
        }
        nodes.add(phaserButton);

        primaryToolbar.getItems().setAll(nodes);
    }

    private void setupSecondaryToolbarForSelectedMode() {
        List<Node> nodes = new ArrayList<>();
        if (currentMode != DataMode.FID) {
            nodes.add(toolButton);
            nodes.add(ToolBarUtils.makeFiller(10));
        }
        if (currentMode == DataMode.DATASET_1D) {
            nodes.addAll(specialButtons);
        }

        nodes.add(ToolBarUtils.makeFiller(10));
        secondaryToolbar.getItems().setAll(nodes);
    }

    public boolean isComplex() {
        return complexStatus.isSelected();
    }

    public void setCrossText(Orientation orientation, int index, Double value, boolean iconState) {
        String strValue = "";
        if (value != null) {
            strValue = String.format("%.3f", value);
        }
        if (iconState != iconStates[index][orientation.ordinal()]) {
            iconStates[index][orientation.ordinal()] = iconState;
            if (iconState) {
                crossText[index][orientation.ordinal()].setRight(limitTextIcons[index][orientation.ordinal()]);
                crossText[index][orientation.ordinal()].resetMinMax();
            } else {
                crossText[index][orientation.ordinal()].setRight(crossTextIcons[index][orientation.ordinal()]);
            }
        }
        crossText[index][orientation.ordinal()].setText(strValue);
    }

    public void setIconState(int iCross, Orientation orientation, boolean state) {
        Rectangle rect;
        Line line;
        StackPane pane = (StackPane) crossText[iCross][orientation.ordinal()].getRight();
        rect = (Rectangle) pane.getChildren().get(0);
        line = (Line) pane.getChildren().get(1);
        Color color = state ? Color.LIGHTGRAY : Color.BLACK;
        rect.setFill(color);
        color = state && iCross == 1 ? Color.RED : Color.BLACK;
        line.setStroke(color);
    }

    private void dimMenuAction(ActionEvent event, int iAxis) {
        MenuItem menuItem = (MenuItem) event.getSource();
        PolyChart chart = controller.getActiveChart();
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

    private void rowMenuAction(ActionEvent event) {
        MenuItem menuItem = (MenuItem) event.getSource();
        PolyChart chart = controller.getActiveChart();
        if (menuItem.getText().equals("Full")) {
            chart.clearDrawlist();
        } else if (menuItem.getText().equals("First")) {
            chart.setDrawlist(0);
        } else if (menuItem.getText().equals("Last")) {
            chart.setDrawlist(1000);
        }
        chart.refresh();
    }

    /**
     * Updates the spectrum status bar and the type of plot displayed in the active chart
     * based on the selected option.
     */
    private void displayModeComboBoxSelectionChanged() {
        PolyChart chart = controller.getActiveChart();
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
            set1DArray(maxNDim.getAsInt(), maxRows.getAsInt());
        } else if (selected == DisplayMode.CONTOURS) {
            chart.getDisDimProperty().set(PolyChart.DISDIM.TwoD);
            chart.getDatasetAttributes().get(0).drawList.clear();
            chart.updateProjections();
            chart.updateProjectionScale();
            int nDim = maxNDim.getAsInt();
            setMode(DataMode.fromDimensions(nDim), nDim);
        }
        chart.updateAxisType(true);
        chart.full();
        chart.autoScale();
    }

    private boolean isStacked() {
        PolyChart chart = controller.getActiveChart();
        return chart.getChartProperties().getStackX() > 0.01 ||
                chart.getChartProperties().getStackY() > 0.01;
    }

    private void dimAction(String rowName, String dimName) {
        controller.getCharts().forEach(chart -> chart.getFirstDatasetAttributes().ifPresent(attr -> {
            attr.setDim(rowName, dimName);
            setPlaneRanges();
            chart.updateProjections();
            chart.updateProjectionBorders();
            chart.updateProjectionScale();
            for (int i = 0; i < chart.getNDim(); i++) {
                // fixme  should be able to swap existing limits, not go to full
                chart.full(i);
            }
        }));
    }

    private void updateXYMenu(MenuButton dimMenu, int iAxis) {
        PolyChart chart = controller.getActiveChart();
        dimMenu.getItems().clear();
        chart.getFirstDatasetAttributes().ifPresent(attr -> {
            int nDim = attr.nDim;
            String rowName = DIM_NAMES[iAxis];
            for (int iDim = 0; iDim < nDim; iDim++) {
                String dimName = attr.getDataset().getLabel(iDim);
                MenuItem menuItem = new MenuItem(iDim + 1 + ":" + dimName);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimAction(rowName, dimName));
                dimMenu.getItems().add(menuItem);
                if (controller.isPhaseSliderVisible()) {
                    chart.updatePhaseDim();
                }
            }
        });
    }

    private static ToggleButton createCursorToggleButton(CanvasCursor cursor) {
        ToggleButton button = GlyphsDude.createIconToggleButton(cursor.getIcon(), cursor.getLabel(),
                AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.RIGHT);
        button.setUserData(cursor);
        button.setMinWidth(50);
        return button;
    }

    private static Pane createHorizontalSpacer() {
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public enum DataMode {
        FID, DATASET_1D, DATASET_2D, DATASET_ND_PLUS;

        public static DataMode fromDimensions(int nDim) {
            return switch (nDim) {
                case 0 -> throw new IllegalArgumentException("0 shouldn't be used as a number of dimension");
                case 1 -> DATASET_1D;
                case 2 -> DATASET_2D;
                default -> DATASET_ND_PLUS;
            };
        }
    }

    private enum DisplayMode {
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
            Spinner<Integer> spinner = planeSpinner[axNum - 2][spinNum];
            boolean showValue = valueMode && valueModeBox[axNum - 2].isSelected();
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
            boolean showValue = valueMode && valueModeBox[axNum - 2].isSelected();
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
    public void updateSlices(boolean saveState) {
        final boolean status = sliceStatusCheckBox.isSelected();
        if (saveState) {
            if (status) {
                Cursor crosshairCursor = CanvasCursor.CROSSHAIR.getCursor();
                preSliceCursor = controller.getCurrentCursor();
                if (preSliceCursor != crosshairCursor) {
                    controller.setCursor(crosshairCursor);
                }
            } else {
                if (preSliceCursor != null) {
                    controller.setCursor(preSliceCursor);
                }
            }
        }
        controller.getCharts().forEach(c -> c.setSliceStatus(status));
    }
}
