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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.dialog.ExceptionDialog;
import org.kordamp.ikonli.material2.Material2MZ;
import org.nmrfx.analyst.gui.tools.SliderLayout;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chart.Axis;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.CustomNumberTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author Bruce Johnson
 */
@PluginAPI("parametric")
public class SpectrumStatusBar {
    private static final Logger log = LoggerFactory.getLogger(SpectrumStatusBar.class);
    private static final int MAX_SPINNERS = 4;
    public static final String[] DIM_NAMES = {"X", "Y", "Z", "A", "B", "C", "D", "E"};
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
    private final MenuButton[] rowMenus = new MenuButton[MAX_SPINNERS];
    private final ChangeListener<PolyChart.DISDIM> displayedDimensionsListener = this::chartDisplayDimensionChanged;
    private final ChoiceBox<CanvasCursor> cursorChoice = new ChoiceBox();
    private final ToggleButton tableButton = new RadioButton("Table");
   // private final ToggleButton tableButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.TABLE, "Table",
     //       AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.LEFT);

    // tools & additional buttons
    private final ToolBar secondaryToolbar = new ToolBar();
    private final MenuButton toolButton = new MenuButton("Tools");
    private final List<ButtonBase> specialButtons = new ArrayList<>();
    List<Control> extractControls = new ArrayList<>();


    private boolean arrayMode = false;
    private DataMode currentMode = DataMode.FID;
    private int currentModeDimensions = 0;

    private Cursor preSliceCursor = null;
    public SpectrumStatusBar(FXMLController controller) {
        this.controller = controller;
    }

    ComboBox<ViewController.DisplayMode> getDisplayModeComboBox() {
        return controller.getViewController().getDisplayModeComboBox();
    }

    // can't be called from constructor: relies on controller.getActiveChart(), which returns null at construction
    public void init() {
        tableButton.setOnAction(e -> controller.updateScannerTool(tableButton));
        initCursorButtonGroup();
        setupTools();
        initCrossText();

        Pane filler = createHorizontalSpacer();
        primaryToolbar.getItems().add(filler);

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


        controller.getActiveChart().getDisDimProperty().addListener(displayedDimensionsListener);
        PolyChartManager.getInstance().activeChartProperty().addListener(new WeakChangeListener<PolyChart>(this::setChart));
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

    private void setExtractSpinner(Spinner<Integer> spinner, Dataset dataset, int iDim) {
        Vec vec = dataset.getVec();
        int[][] pt = vec.getPt();
        int size = dataset.getSizeTotal(iDim);
        SpinnerValueFactory.IntegerSpinnerValueFactory factory = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
        factory.setMax(size -1);
        factory.setValue(pt[iDim][0]);
    }

    public void extractionSliceTools() {
        PolyChart chart = getController().getActiveChart();
        for (Control control : extractControls) {
            secondaryToolbar.getItems().remove(control);
        }
        extractControls.clear();
        List<DatasetAttributes> datasetAttributes = chart.getDatasetAttributes();
        if (datasetAttributes.isEmpty()) {
            return;
        }
        Dataset dataset = datasetAttributes.getLast().getDataset();
        if ((dataset != null) && (dataset.getVec()) != null) {
            Vec vec = dataset.getVec();
            int[][] pt = vec.getPt();
            if (pt == null) {
                return;
            }
            int nDim = pt.length;
            var sourceOpt = dataset.getExtractSource();
            String[] labels = {"X","Y","Z"};
            if (sourceOpt.isPresent()) {
                Spinner<Integer> sliceIndexSpinner = new Spinner<>(0,datasetAttributes.size() - 1, datasetAttributes.size() - 1);
                sliceIndexSpinner.getValueFactory().valueProperty().addListener(e -> updateIndex(chart, sliceIndexSpinner));
                sliceIndexSpinner.getEditor().setPrefWidth(45);
                sliceIndexSpinner.setPrefWidth(65);

                Label indexLabel = new Label("Dataset Index:");
                secondaryToolbar.getItems().add(indexLabel);
                secondaryToolbar.getItems().add(sliceIndexSpinner);
                extractControls.add(indexLabel);
                extractControls.add(sliceIndexSpinner);
                for (int i = 1; i < nDim; i++) {
                    int size = sourceOpt.get().getSizeTotal(i);
                    Spinner<Integer> spinner = new Spinner(0, size - 1, pt[i][0]);
                    spinner.setEditable(true);
                    spinner.getEditor().setPrefWidth(45);
                    spinner.setPrefWidth(65);
                    int jDim = i;
                    ChangeListener<Integer> extractSliceListener = (ObservableValue<? extends Integer> observableValue, Integer oldValue, Integer newValue) -> {
                        if (newValue != null && !newValue.equals(oldValue)) {
                            updateExtractSlice(chart, sliceIndexSpinner, jDim, newValue);
                        }
                    };

                    spinner.getValueFactory().valueProperty().addListener(extractSliceListener);
                    Label dimLabel = new Label(labels[i]);
                    secondaryToolbar.getItems().add(dimLabel);
                    secondaryToolbar.getItems().add(spinner);
                    extractControls.add(spinner);
                    extractControls.add(dimLabel);
                }
            }
        }
    }

    void updateIndex(PolyChart chart, Spinner<Integer> indexSpinner) {
        List<DatasetAttributes> datasetAttributes = chart.getDatasetAttributes();
        DatasetAttributes dataAttr = datasetAttributes.get(indexSpinner.getValue());
        Dataset dataset = dataAttr.getDataset();
        int iDim = 0;
        for (Control control : extractControls) {
            if (control instanceof Spinner spinner) {
                if (iDim > 0) {
                    setExtractSpinner((Spinner<Integer>) spinner, dataset, iDim);
                }
                iDim++;
            }
        }
    }

    void updateExtractSlice(PolyChart chart, Spinner<Integer> indexSpinner, int iDim, int slicePos) {
        List<DatasetAttributes> datasetAttributes = chart.getDatasetAttributes();
        DatasetAttributes dataAttr = datasetAttributes.get(indexSpinner.getValue());
        Dataset dataset = dataAttr.getDataset();
        try {
            dataset.reloadVector(iDim, slicePos);
            chart.refresh();
        } catch (IOException e) {
            log.error("Error with slice",e);
        }
    }

    private void initCursorButtonGroup() {
        Arrays.stream(CanvasCursor.values())
                .forEach(tb -> cursorChoice.getItems().add(tb));
        cursorChoice.setValue(CanvasCursor.SELECTOR);
        cursorChoice.setPrefWidth(100);
        cursorChoice.valueProperty()
                .addListener((observable, oldValue, newValue) -> cursorButtonToggled((CanvasCursor) newValue));
    }

    private void cursorButtonToggled(CanvasCursor canvasCursor) {
            controller.setCursor(canvasCursor.getCursor());
            updateSlices(true);
    }

    @PluginAPI("parametric")
    public FXMLController getController() {
        return controller;
    }

    public List<Node> getToolbars() {
        return List.of(primaryToolbar, secondaryToolbar);
    }

    public void updateCursorBox() {
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
            getDisplayModeComboBox().setValue(ViewController.DisplayMode.TRACES);
        } else {
            getDisplayModeComboBox().setValue(ViewController.DisplayMode.CONTOURS);
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

    public void updateLayoutMenu(Menu menu) {
        var names = SliderLayout.getLayoutNames();
        menu.getItems().clear();
        MenuItem loadLayoutsItem = new MenuItem("Open...");
        menu.getItems().add(loadLayoutsItem);
        loadLayoutsItem.setOnAction(e -> {SliderLayout.loadLayoutFromFile();
            updateLayoutMenu(menu);
        });
        for (String name : names) {
            MenuItem item = new MenuItem(name);
            menu.getItems().add(item);
            item.setOnAction(e -> loadLayout(name));
        }
    }

    private void loadLayout(String name) {
        SliderLayout sliderLayout = new SliderLayout();
        try {
            sliderLayout.apply(name, controller);
        } catch (IOException e) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(e);
            exceptionDialog.showAndWait();
        }
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
                }
            }
        }
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

    private void complexStatusChanged(ActionEvent event) {
        controller.getActiveChart().layoutPlotChildren();
    }

    void setCursor(Cursor cursor) {
        cursorChoice.setValue(CanvasCursor.getCanvasCursor(cursor));
    }

    public void setCrossTextRange(int index, Orientation orientation, double min, double max) {
        if (CanvasCursor.isCrosshair(controller.getCurrentCursor())) {
            crossText[index][orientation.ordinal()].setMin(min);
            crossText[index][orientation.ordinal()].setMax(max);
        } else {
            crossText[index][orientation.ordinal()].resetMinMax();
        }
    }

    public void set1DArray(int nDim, int nRows) {
        arrayMode = true;
       // setPlaneRanges(2, nRows);
        updatePrimaryToolbarFor1DArray(nDim);
        updateSecondaryToolbarFor1DArray();
    }

    private void updatePrimaryToolbarFor1DArray(int nDim) {
        System.out.println("update primary");
        List<Node> nodes = new ArrayList<>();
        nodes.add(tableButton);
        if (isStacked()) {
            getDisplayModeComboBox().getSelectionModel().select(ViewController.DisplayMode.STACKPLOT);
        } else {
            getDisplayModeComboBox().getSelectionModel().select(ViewController.DisplayMode.TRACES);
        }
        nodes.add(createHorizontalSpacer());

        nodes.add(new Label("Cursor:"));
        nodes.add(cursorChoice);
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
      //  setPlaneRanges();
    }

    private void setupPrimaryToolbarForSelectedMode() {
       List<Node> nodes = new ArrayList<>();
        nodes.add(tableButton);

        if (currentMode == DataMode.DATASET_2D) {
            getDisplayModeComboBox().getSelectionModel().select(ViewController.DisplayMode.CONTOURS);
        }

        nodes.add(createHorizontalSpacer());
        nodes.add(new Label("Cursor:"));
        nodes.add(cursorChoice);

        //first dimension cross-hair
        if (currentMode == DataMode.DATASET_2D || currentMode == DataMode.DATASET_ND_PLUS) {
            //nodes.add(dimMenus[0]);
        }
        nodes.add(new Label("X:"));
        nodes.add(crossText[0][1]);
        nodes.add(crossText[1][1]);

        //second dimension cross-hair
        nodes.add(new Label("Y:"));

        nodes.add(crossText[0][0]);
        nodes.add(crossText[1][0]);
        nodes.add(createHorizontalSpacer());

        // complex checkbox, only for FID
        if (currentMode == DataMode.FID) {
            nodes.add(complexStatus);
        }

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
        extractionSliceTools();
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


    private boolean isStacked() {
        PolyChart chart = controller.getActiveChart();
        return chart.getChartProperties().getStackX() > 0.01 ||
                chart.getChartProperties().getStackY() > 0.01;
    }

    private void dimAction(String rowName, String dimName) {
        controller.setDim(rowName, dimName);
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
        ToggleButton button = GUIUtils.toggleButton(cursor.getIcon(), cursor.getLabel(), ContentDisplay.RIGHT);
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

    public void updateSlices(boolean saveState) {
        final boolean status = getController().getCursor() == CanvasCursor.SLICE.getCursor();
        controller.sliceStatusProperty().set(status);
        controller.getCharts().forEach(c -> c.setSliceStatus(status));
    }
}
