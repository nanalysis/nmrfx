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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import javafx.scene.control.*;
import org.nmrfx.utils.properties.CustomNumberTextField;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Callback;
import org.apache.commons.lang3.SystemUtils;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.gui.spectra.NMRAxis;
import org.nmrfx.processor.gui.undo.ChartUndoLimits;

/**
 *
 * @author Bruce Johnson
 */
public class SpectrumStatusBar {
    public static final String ANALYSIS_1D_MENU_STRING = "Analysis (1D)";
    static final DecimalFormat formatter = new DecimalFormat();

    static {
        formatter.setMaximumFractionDigits(2);
    }
    public final static Cursor SEL_CURSOR = SystemUtils.IS_OS_LINUX ? Cursor.HAND : Cursor.MOVE;
    static final int maxSpinners = 4;
    CustomNumberTextField[][] crossText = new CustomNumberTextField[2][2];
    FXMLController controller;
    CheckBox sliceStatus = new CheckBox("Slices");
    CheckBox complexStatus = new CheckBox("Complex");
    CheckBox phaserStatus = new CheckBox("Phasing");
    MenuButton toolButton = new MenuButton("Tools");
    List<Button> specialButtons = new ArrayList<>();
    Button peakPickButton;



    TextField[] planePPMField = new TextField[maxSpinners];
    Spinner[] planeSpinner = new Spinner[maxSpinners];
    MenuButton[] dimMenus = new MenuButton[maxSpinners + 2];
    MenuButton[] rowMenus = new MenuButton[maxSpinners];
    ChangeListener<Integer>[] planeListeners = new ChangeListener[maxSpinners];
    ToolBar btoolBar;
    StackPane[][] crossTextIcons = new StackPane[2][2];
    StackPane[][] limitTextIcons = new StackPane[2][2];
    boolean[][] iconStates = new boolean[2][2];
    Pane filler1 = new Pane();
    Pane filler2 = new Pane();
    static String[] rowNames = {"X", "Y", "Z", "A", "B", "C", "D", "E"};
    MenuButton cursorMenuButton = new MenuButton();
    Cursor currentCursor = Cursor.CROSSHAIR;
    HashMap<Cursor, Text> cursorMap = new HashMap<>();
    HashMap<String, Cursor> cursorNameMap = new HashMap<>();
    static Background errorBackground = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    boolean arrayMode = false;
    int currentMode = 0;

    public SpectrumStatusBar(FXMLController controller) {
        this.controller = controller;
    }

    public FXMLController getController() {
        return controller;
    }

    public void buildBar(ToolBar btoolBar) {
        String iconSize = "16px";
        String fontSize = "7pt";
        this.btoolBar = btoolBar;
        peakPickButton = GlyphsDude.createIconButton(FontAwesomeIcon.BULLSEYE, "Pick", iconSize, fontSize, ContentDisplay.LEFT);
        peakPickButton.setOnAction(e -> PeakPicking.peakPickActive(controller, false, null));

        setupTools();

        for (int i = 0; i < 2; i++) {
            for (int j = 1; j >= 0; j--) {
                crossText[i][j] = new CustomNumberTextField();
                crossText[i][j].setPrefWidth(75.0);

                crossText[i][j].setFunction(controller.getActiveChart().getCrossHairUpdateFunction(i, j));

                btoolBar.getItems().add(crossText[i][j]);
                StackPane stackPane = makeIcon(i, j, false);
                crossTextIcons[i][j] = stackPane;
                crossText[i][j].setRight(stackPane);
                StackPane stackPane2 = makeIcon(i, j, true);
                limitTextIcons[i][j] = stackPane2;

                if (i == 1) {
                    crossText[i][j].setStyle("-fx-text-inner-color: red;");
                } else {
                    crossText[i][j].setStyle("-fx-text-inner-color: black;");
                }

            }
        }
        for (int i = 0; i < planePPMField.length; i++) {
            planePPMField[i] = new TextField();
            planeSpinner[i] = new Spinner(0, 127, 63);
        }

        Pane filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        btoolBar.getItems().add(filler);

        for (int i = 0; i < planePPMField.length; i++) {
            Spinner spinner = planeSpinner[i];
            spinner.setEditable(true);
            spinner.setPrefWidth(75);
            spinner.setOnScroll(e -> scrollPlane(e, spinner));
            planePPMField[i].setPrefWidth(60);
            final int iPlane = i + 2;
            planePPMField[i].setOnKeyReleased(e -> planeKeyReleased(e, iPlane));
            planeListeners[i] = (ObservableValue<? extends Integer> observableValue, Integer oldValue, Integer newValue) -> {
                if ((newValue != null) && !newValue.equals(oldValue)) {
                    updatePlane(iPlane, newValue);
                }
            };

            SpinnerValueFactory<Integer> planeFactory = (SpinnerValueFactory<Integer>) planeSpinner[i].getValueFactory();
            planeFactory.valueProperty().addListener(planeListeners[i]);

            planePPMField[i].setOnScroll(e -> scrollPlane(e, spinner));
        }
        for (int i = 0; i < dimMenus.length; i++) {
            final int iAxis = i;
            String rowName = rowNames[iAxis];

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
        for (int i = 0; i < rowMenus.length; i++) {
            final int iAxis = i + 1;
            String rowName = rowNames[iAxis];

            MenuButton mButton = new MenuButton(rowName);
            rowMenus[i] = mButton;
            MenuItem menuItem = new MenuItem("Full");
            mButton.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> rowMenuAction(event, iAxis));
            menuItem = new MenuItem("First");
            mButton.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> rowMenuAction(event, iAxis));
            menuItem = new MenuItem("Last");
            mButton.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> rowMenuAction(event, iAxis));

        }
//        rangeSlider.lowValueProperty().addListener(refDimListener);
        filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        btoolBar.getItems().add(filler);
        phaserStatus.setOnAction(this::phaserStatus);
        btoolBar.getItems().add(sliceStatus);
        btoolBar.getItems().add(complexStatus);
        btoolBar.getItems().add(phaserStatus);
        controller.sliceStatus.bind(sliceStatus.selectedProperty());
        sliceStatus.setOnAction(this::sliceStatus);
        complexStatus.setOnAction(this::complexStatus);
        Text crosshairIcon = GlyphsDude.createIcon(FontAwesomeIcon.PLUS, "16");
        Text arrowIcon = GlyphsDude.createIcon(FontAwesomeIcon.MOUSE_POINTER, "16");
        cursorMap.put(Cursor.CROSSHAIR, crosshairIcon);
        cursorMap.put(SEL_CURSOR, arrowIcon);
        cursorNameMap.put("CrossHair", Cursor.CROSSHAIR);
        cursorNameMap.put("Selector", SEL_CURSOR);
        String[] cursorModes = {"CrossHair", "Selector"};
        for (String cursorMode : cursorModes) {
            MenuItem cursorMenuItem = new MenuItem(cursorMode);
            cursorMenuButton.getItems().add(cursorMenuItem);
            cursorMenuItem.setOnAction(e -> setCursor(cursorMode));
        }
        cursorMenuButton.setGraphic(cursorMap.get(Cursor.CROSSHAIR));

        Callback<ListView<Cursor>, ListCell<Cursor>> cellFactory = new Callback<ListView<Cursor>, ListCell<Cursor>>() {
            @Override
            public ListCell<Cursor> call(ListView<Cursor> p) {
                return new ListCell<>() {
                    Text icon;

                    {
                        icon = new Text();
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }

                    @Override
                    protected void updateItem(Cursor item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item == null || empty) {
                            setGraphic(null);
                        } else {
                            if (item.toString().equals("MOVE")) {
                                icon = GlyphsDude.createIcon(FontAwesomeIcon.MOUSE_POINTER, "16");
                            } else {
                                icon = GlyphsDude.createIcon(FontAwesomeIcon.PLUS, "16");

                            }
                            setGraphic(icon);
                        }
                    }
                };
            }
        };
    }

    public void addToolBarButtons(Button... buttons) {
        for (var button:buttons) {
            specialButtons.add(button);
        }
    }

    public void addToToolMenu(MenuItem menuItem) {
        toolButton.getItems().add(menuItem);
    }

    public void addToToolMenu(String menuText, MenuItem newItem) {
        for (MenuItem menuItem : toolButton.getItems()) {
            if (menuItem.getText().equals(menuText)) {
                if (menuItem instanceof Menu) {
                    Menu menu = (Menu) menuItem;
                    menu.getItems().add(newItem);
                }
            }
        }
    }

    public void setupTools() {
        Menu specToolMenu = new Menu("Spectrum Tools");

        MenuItem measureMenuItem = new MenuItem("Show Measure Bar");
        measureMenuItem.setOnAction(e -> controller.showSpectrumMeasureBar());
        MenuItem analyzerMenuItem = new MenuItem("Show Analyzer Bar");
        analyzerMenuItem.setOnAction(e -> controller.showAnalyzerBar());

        specToolMenu.getItems().addAll(measureMenuItem, analyzerMenuItem);
        addToToolMenu(specToolMenu);
    }

    public void setCursor(Cursor cursor) {
        cursorMenuButton.setGraphic(cursorMap.get(cursor));
        for (PolyChart chart : controller.charts) {
            chart.setCanvasCursor(cursor);
            if (cursor.toString().equals("CROSSHAIR")) {
                chart.getCrossHairs().setCrossHairState(true);
            } else {
                chart.getCrossHairs().setCrossHairState(false);
            }
        }
        currentCursor = cursor;
    }

    public void setCursor(String cursorName) {
        setCursor(cursorNameMap.get(cursorName));
    }

    private StackPane makeIcon(int i, int j, boolean boundMode) {
        StackPane stackPane = new StackPane();
        stackPane.setPadding(Insets.EMPTY);
        Rectangle rect = new Rectangle(10, 10);
        rect.setFill(Color.LIGHTGREY);
        rect.setStroke(Color.LIGHTGREY);
        Line line = new Line();
        if (j == 0) {
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
            controller.toggleCrossHairState(i, j);
            e.consume();
        });
        return stackPane;
    }

    public void planeKeyReleased(KeyEvent event, int axNum) {
        TextField planeField = (TextField) event.getSource();
        if (defaultBackground == null) {
            defaultBackground = planeField.getBackground();
        }

        String text = planeField.getText().trim();
        try {
            if (text.length() > 0) {
                double planePPM = Double.parseDouble(text);
                PolyChart chart = controller.getActiveChart();
                ObservableList<DatasetAttributes> dataAttrList = chart.getDatasetAttributes();
                if (!dataAttrList.isEmpty()) {
                    DatasetAttributes dataAttr = dataAttrList.get(0);
                    int planeIndex = DatasetAttributes.AXMODE.PPM.getIndex(dataAttr, axNum, planePPM);
                    if (event.getCode() == KeyCode.ENTER) {
                        updatePlane(axNum, planeIndex);
                    }
                }
            }
            planeField.setBackground(defaultBackground);
        } catch (NumberFormatException nfE) {
            planeField.setBackground(errorBackground);
        }

    }

    public void updatePlanePPM(double ppm, int axNum) {
        formatter.setMaximumFractionDigits(2);
        String s = formatter.format(ppm);
        planePPMField[axNum - 2].setText(s);
    }

    public void setChart(PolyChart chart) {
        DatasetBase dataset = chart.getDataset();
        if (!chart.getDatasetAttributes().isEmpty()) {
            DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
            for (int axNum = 2; axNum < dataAttr.nDim; axNum++) {
                NMRAxis axis = chart.axes[axNum];
                int indexL = chart.axModes[axNum].getIndex(dataAttr, axNum, axis.getLowerBound());
                int indexU = chart.axModes[axNum].getIndex(dataAttr, axNum, axis.getUpperBound());

                int center = (indexL + indexU) / 2;
                int dDim = dataAttr.dim[axNum];
                int size = dataAttr.getDataset().getSizeReal(dDim);
                setPlaneRanges(axNum, size);
                updatePlaneSpinner(center, axNum);
            }
        }

        chart.setCanvasCursor(currentCursor);

    }

    public void updateRowSpinner(int row, int axNum) {
        SpinnerValueFactory<Integer> planeFactory = (SpinnerValueFactory<Integer>) planeSpinner[axNum - 1].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[axNum - 1]);
        planeFactory.setValue(row);
        planeFactory.valueProperty().addListener(planeListeners[axNum - 1]);

    }

    public void updatePlaneSpinner(int plane, int axNum) {
        SpinnerValueFactory<Integer> planeFactory = (SpinnerValueFactory<Integer>) planeSpinner[axNum - 2].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[axNum - 2]);
        planeFactory.setValue(plane);
        PolyChart chart = controller.getActiveChart();
        ObservableList<DatasetAttributes> dataAttrList = chart.getDatasetAttributes();
        if (!dataAttrList.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrList.get(0);
            double ppm = DatasetAttributes.AXMODE.PPM.indexToValue(dataAttr, axNum, plane);
            updatePlanePPM(ppm, axNum);
        }
        planeFactory.valueProperty().addListener(planeListeners[axNum - 2]);

    }

    void scrollPlane(ScrollEvent e, Spinner spinner) {
        double delta = e.getDeltaY();
        int nPlanes = (int) Math.round(delta / 10.0);
        if (nPlanes == 0) {
            nPlanes = delta < 0.0 ? -1 : 1;
        }
        nPlanes *= -1;  // scrolling up should increase.  Is this dependent on Mac scrolling settting
        SpinnerValueFactory<Integer> planeFactory = (SpinnerValueFactory<Integer>) spinner.getValueFactory();
        planeFactory.increment(nPlanes);
    }

    void updatePlane(int iDim, int plane) {
        if (arrayMode) {
            int newValue = controller.getActiveChart().setDrawlist(plane);
            controller.getActiveChart().refresh();
        } else {
            PolyChart chart = controller.getActiveChart();

            if (!chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                NMRAxis axis = chart.axes[iDim];
                int pt1 = chart.axModes[iDim].getIndex(dataAttr, iDim, axis.getLowerBound());
                int pt2 = chart.axModes[iDim].getIndex(dataAttr, iDim, axis.getUpperBound());

                int center = (pt1 + pt2) / 2;
                int delta = center - pt1;
                if (pt1 != (plane - delta)) {
                    pt1 = plane - delta;
                    pt2 = plane + delta;
                    ChartUndoLimits undo = new ChartUndoLimits(controller.getActiveChart());
                    double ppm1 = chart.axModes[iDim].indexToValue(dataAttr, iDim, pt1);
                    double ppm2 = chart.axModes[iDim].indexToValue(dataAttr, iDim, pt2);

                    controller.getActiveChart().setAxis(iDim, ppm1, ppm2);
                    controller.getActiveChart().refresh();
                    ChartUndoLimits redo = new ChartUndoLimits(controller.getActiveChart());
                    controller.undoManager.add("plane", undo, redo);
                }
            }
        }
    }

    @FXML
    private void phaserStatus(ActionEvent event) {
        CheckBox checkBox = (CheckBox) event.getSource();
        controller.updatePhaser(checkBox.isSelected());
    }

    @FXML
    private void sliceStatus(ActionEvent event) {
        CheckBox checkBox = (CheckBox) event.getSource();
        final boolean status = checkBox.isSelected();
        controller.charts.forEach(chart -> chart.setSliceStatus(status));
    }

    @FXML
    private void complexStatus(ActionEvent event) {
        controller.getActiveChart().layoutPlotChildren();
    }

    public void setCrossTextRange(int iCross, int jOrient, double min, double max) {
        crossText[iCross][jOrient].setMin(min);
        crossText[iCross][jOrient].setMax(max);
    }

    public void setPlaneRanges(int iDim, int max) {
        SpinnerValueFactory.IntegerSpinnerValueFactory planeFactory = (SpinnerValueFactory.IntegerSpinnerValueFactory) planeSpinner[iDim - 2].getValueFactory();
        planeFactory.valueProperty().removeListener(planeListeners[iDim - 2]);
        planeFactory.setMin(0);
        planeFactory.setMax(max);
        planeFactory.valueProperty().addListener(planeListeners[iDim - 2]);
    }

    public void set1DArray(int nDim, int nRows) {
        arrayMode = true;
        setPlaneRanges(2, nRows);
        List<Node> nodes = new ArrayList<>();
        nodes.add(cursorMenuButton);
        nodes.add(toolButton);

        HBox.setHgrow(filler1, Priority.ALWAYS);
        HBox.setHgrow(filler2, Priority.ALWAYS);
        nodes.add(filler1);

        for (int i = 0; i < 2; i++) {
            for (int j = 1; j >= 0; j--) {
                nodes.add(crossText[i][j]);
            }
        }
        nodes.add(filler2);
        for (int i = 1; i < nDim; i++) {
            nodes.add(rowMenus[i - 1]);
            nodes.add(planeSpinner[i - 1]);
            Pane nodeFiller = new Pane();
            HBox.setHgrow(nodeFiller, Priority.ALWAYS);
            nodes.add(nodeFiller);
        }
        //  nodes.add(phaserStatus);
        btoolBar.getItems().clear();

        btoolBar.getItems().addAll(nodes);
        updateRowSpinner(0, 1);
        disable1DAnalysisTools(true);

    }

    public int getMode() {
        return currentMode;
    }

    public void setMode(int mode) {
        currentMode = mode;
        arrayMode = false;
        List<Node> nodes = new ArrayList<>();
        nodes.add(cursorMenuButton);
        if (mode != 0) {
            nodes.add(toolButton);
        }
        if (mode == 1) {
            for (var button:specialButtons) {
                nodes.add(button);
            }
        } else if (mode > 1) {
            nodes.add(peakPickButton);
        }
        HBox.setHgrow(filler1, Priority.ALWAYS);
        HBox.setHgrow(filler2, Priority.ALWAYS);
        if (mode > 1) {
            nodes.add(dimMenus[0]);
        }
        if (mode > 2) {
            nodes.add(dimMenus[1]);
        }
        nodes.add(filler1);

        for (int i = 0; i < 2; i++) {
            for (int j = 1; j >= 0; j--) {
                nodes.add(crossText[i][j]);
            }
        }
        nodes.add(filler2);
        for (int i = 2; i < mode; i++) {
            nodes.add(dimMenus[i]);
            nodes.add(planePPMField[i - 2]);
            nodes.add(planeSpinner[i - 2]);
            Pane nodeFiller = new Pane();
            HBox.setHgrow(nodeFiller, Priority.ALWAYS);
            nodes.add(nodeFiller);
        }
        if (mode == 0) {
            nodes.add(complexStatus);
        } else {
            nodes.add(sliceStatus);
        }
        nodes.add(phaserStatus);
        btoolBar.getItems().clear();

        btoolBar.getItems().addAll(nodes);
        disable1DAnalysisTools(mode != 1);

    }

    public void disable1DAnalysisTools(boolean disable) {
        for (MenuItem menuItem: toolButton.getItems()) {
            if (ANALYSIS_1D_MENU_STRING.equals(menuItem.getText())) {
                menuItem.setDisable(disable);
            }
        }
    }

    public void setCrossText(int iOrient, int iCross, Double value, boolean iconState) {
        String strValue = "";
        if (value != null) {
            strValue = String.format("%.3f", value);
        }
        crossText[iCross][iOrient].setText(strValue);
        if (iconState != iconStates[iCross][iOrient]) {
            iconStates[iCross][iOrient] = iconState;
            if (iconState) {
                crossText[iCross][iOrient].setRight(limitTextIcons[iCross][iOrient]);
            } else {
                crossText[iCross][iOrient].setRight(crossTextIcons[iCross][iOrient]);
            }
        }
    }

    public void setIconState(int iCross, int jOrient, boolean state) {
        Rectangle rect;
        Line line;
        StackPane pane = (StackPane) crossText[iCross][jOrient].getRight();
        rect = (Rectangle) pane.getChildren().get(0);
        line = (Line) pane.getChildren().get(1);
        Color color = state ? Color.LIGHTGRAY : Color.BLACK;
        rect.setFill(color);
        color = state & (iCross == 1) ? Color.RED : Color.BLACK;
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

    private void rowMenuAction(ActionEvent event, int iAxis) {
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

    private void dimAction(String rowName, String dimName) {
        controller.charts.forEach((chart) -> {
            if (!chart.datasetAttributesList.isEmpty()) {
                DatasetAttributes datasetAttr = chart.datasetAttributesList.get(0);
                datasetAttr.setDim(rowName, dimName);

                for (int i = 0; i < chart.getNDim(); i++) {
                    // fixme  should be able to swap existing limits, not go to full
                    chart.full(i);
                }
            }
        });
    }

    private void updateXYMenu(MenuButton dimMenu, int iAxis) {
        PolyChart chart = controller.getActiveChart();
        dimMenu.getItems().clear();
        if (!chart.datasetAttributesList.isEmpty()) {
            DatasetAttributes datasetAttr = chart.datasetAttributesList.get(0);
            int nDim = datasetAttr.nDim;
            String rowName = rowNames[iAxis];
            for (int iDim = 0; iDim < nDim; iDim++) {
                String dimName = datasetAttr.getDataset().getLabel(iDim);
                MenuItem menuItem = new MenuItem(iDim + 1 + ":" + dimName);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimAction(rowName, dimName));
                dimMenu.getItems().add(menuItem);
                if (controller.isPhaseSliderVisible()) {
                    chart.updatePhaseDim();
                }
            }
        }
    }
}
