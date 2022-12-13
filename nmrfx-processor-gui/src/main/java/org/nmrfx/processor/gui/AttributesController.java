package org.nmrfx.processor.gui;

import javafx.animation.PauseTransition;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.controlsfx.control.RangeSlider;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.NMRAxis;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ColorSchemes;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;

public class AttributesController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AttributesController.class);
    static final DecimalFormat FORMATTER = new DecimalFormat();

    static {
        FORMATTER.setMaximumFractionDigits(3);
    }

    enum SelectionChoice {
        DATASET,
        CHART,
        WINDOW
    }

    @FXML
    ChoiceBox<SelectionChoice> datasetChoiceState;
    @FXML
    ChoiceBox<DatasetAttributes> datasetChoiceBox;
    @FXML
    Accordion attributesAccordion;
    @FXML
    TitledPane contourLevelPane;
    @FXML
    TitledPane contourAppearancePane;
    @FXML
    TitledPane oneDPane;
    @FXML
    TitledPane integralsPane;
    @FXML
    private VBox viewGrid;
    @FXML
    private CheckBox integralCheckBox;
    @FXML
    private RangeSlider integralPosSlider;
    @FXML
    private Label integralLowValue;
    @FXML
    private Label integralHighValue;
    @FXML
    private CheckBox regionCheckBox;
    @FXML
    CheckBox aspectCheckBox;
    @FXML
    Slider aspectSlider;
    @FXML
    Label aspectRatioValue;

    StringProperty[][] limitFields;
    Label[] labelFields;
    @FXML
    Slider scaleSlider;
    @FXML
    private CheckBox offsetTrackingCheckBox;
    @FXML
    private CheckBox useDatasetColorCheckBox;
    @FXML
    private CheckBox sliceStatusCheckBox;
    @FXML
    private CheckBox slice1StateCheckBox;
    @FXML
    private CheckBox slice2StateCheckBox;
    @FXML
    private ColorPicker slice1ColorPicker;
    @FXML
    private ColorPicker slice2ColorPicker;
    @FXML
    Slider xOffsetSlider;
    @FXML
    Slider yOffsetSlider;

    @FXML
    CheckBox axisColorCheckBox;
    @FXML
    CheckBox cross0ColorCheckBox;
    @FXML
    CheckBox cross1ColorCheckBox;
    @FXML
    CheckBox bgColorCheckBox;

    @FXML
    private ColorPicker axisColorPicker;
    @FXML
    private ColorPicker cross0ColorPicker;
    @FXML
    private ColorPicker cross1ColorPicker;
    @FXML
    private ColorPicker bgColorPicker;

    @FXML
    private ComboBox<Number> ticFontSizeComboBox;
    @FXML
    private ComboBox<Number> labelFontSizeComboBox;
    @FXML
    private CheckBox gridCheckBox;
    @FXML
    private CheckBox intensityAxisCheckBox;
    @FXML
    private ComboBox<Number> leftBorderSizeComboBox;
    @FXML
    private ComboBox<Number> rightBorderSizeComboBox;
    @FXML
    private ComboBox<Number> topBorderSizeComboBox;
    @FXML
    private ComboBox<Number> bottomBorderSizeComboBox;
    @FXML
    private CheckBox titlesCheckBox;
    @FXML
    private CheckBox parametersCheckBox;

    @FXML
    Slider lvlSlider;
    @FXML
    TextField lvlField;
    @FXML
    Slider lvlSlider1D;
    @FXML
    TextField lvlField1D;

    @FXML
    Slider clmSlider;
    @FXML
    TextField clmField;

    @FXML
    Slider nlvlsSlider;
    @FXML
    TextField nlvlsField;

    @FXML
    Slider offsetSlider;
    @FXML
    TextField offsetField;
    @FXML
    private ColorPicker posColorPicker;
    @FXML
    private ColorPicker posColorPicker1D;
    @FXML
    private ColorPicker negColorPicker;

    @FXML
    private CheckBox posOnCheckbox;
    @FXML
    private CheckBox posOnCheckbox1D;
    @FXML
    private CheckBox negOnCheckbox;

    ParSliderListener lvlSliderListener = new LvlSliderListener();
    ParSliderListener nlvlsSliderListener = new NlvlSliderListener();
    ParSliderListener clmSliderListener = new ClmSliderListener();
    ParSliderListener offsetSliderListener = new OffsetSliderListener();
    ParSliderListener posWidthSliderListener = new PosWidthSliderListener();
    ParSliderListener negWidthSliderListener = new NegWidthSliderListener();

    PosColorListener posColorListener = new PosColorListener();
    NegColorListener negColorListener = new NegColorListener();
    PosDrawOnListener posDrawOnListener = new PosDrawOnListener();
    NegDrawOnListener negDrawOnListener = new NegDrawOnListener();
    @FXML
    Slider posWidthSlider;
    @FXML
    TextField posWidthField;
    @FXML
    Slider negWidthSlider;
    @FXML
    TextField negWidthField;

    @FXML
    CheckBox peakDisplayCheckBox;
    @FXML
    CheckBox simPeakDisplayCheckBox;
    @FXML
    CheckBox linkPeakDisplayCheckBox;
    @FXML
    private ColorPicker peakOnColorPicker;
    @FXML
    private ColorPicker peakOffColorPicker;
    @FXML
    private ComboBox<PeakDisplayParameters.ColorTypes> peakColorModeComboBox;
    @FXML
    private ComboBox<PeakDisplayParameters.DisplayTypes> peakDisplayModeComboBox;
    @FXML
    private ComboBox<PeakDisplayParameters.LabelTypes> peakLabelModeComboBox;
    PeakOnColorListener peakOnColorListener = new PeakOnColorListener();
    PeakOffColorListener peakOffColorListener = new PeakOffColorListener();
    DrawPeaksListener drawPeaksListener = new DrawPeaksListener();
    DrawSimPeaksListener drawSimPeaksListener = new DrawSimPeaksListener();
    DrawLinkPeaksListener drawLinkPeaksListener = new DrawLinkPeaksListener();
    PeakDisplayTypeListener peakDisplayTypeListener = new PeakDisplayTypeListener();
    PeakColorTypeListener peakColorTypeListener = new PeakColorTypeListener();
    PeakLabelTypeListener peakLabelTypeListener = new PeakLabelTypeListener();

    boolean shiftState = false;
    Boolean accordionIn1D = null;
    PolyChart chart;
    PolyChart boundChart = null;
    private ComboBox[] dimCombos;
    Label[] dimLabels;
    HBox[] viewBoxes;

    static String[] rowNames = {"X", "Y", "Z", "A", "B", "C"};
    Label[] axisLabels;

    FXMLController fxmlController;
    Pane pane;

    public static AttributesController create(FXMLController fxmlController, Pane processorPane) {
        FXMLLoader loader = new FXMLLoader(AttributesController.class.getResource("/fxml/AttributesController.fxml"));
        final AttributesController controller;
        try {
            Pane pane = loader.load();
            processorPane.getChildren().add(pane);

            controller = loader.getController();
            controller.fxmlController = fxmlController;
            controller.pane = pane;
            controller.sliceStatusCheckBox.selectedProperty().bindBidirectional(fxmlController.sliceStatus);
            controller.datasetChoiceState.getItems().addAll(SelectionChoice.values());
            controller.datasetChoiceState.setValue(SelectionChoice.CHART);
            controller.datasetChoiceBox.disableProperty()
                    .bind(controller.datasetChoiceState.valueProperty().isNotEqualTo(SelectionChoice.DATASET));
            controller.setChart(fxmlController.getActiveChart());

            return controller;
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
            return null;
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ticFontSizeComboBox.getItems().addAll(5.5, 6.0, 6.5, 7.0, 8.0, 9.0,
                10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0,
                28.0, 32.0, 36.0);
        labelFontSizeComboBox.getItems().addAll(5.5, 6.0, 6.5, 7.0, 8.0, 9.0,
                10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0,
                28.0, 32.0, 36.0);
        leftBorderSizeComboBox.getItems().addAll(0, 1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 125, 150);
        rightBorderSizeComboBox.getItems().addAll(0, 1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 125, 150);
        topBorderSizeComboBox.getItems().addAll(0, 1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 125, 150);
        bottomBorderSizeComboBox.getItems().addAll(0, 1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 125, 150);

        bgColorPicker.disableProperty().bind(bgColorCheckBox.selectedProperty().not());
        bgColorPicker.valueProperty().addListener(e -> updateBGColor());
        bgColorCheckBox.selectedProperty().addListener(e -> updateBGColor());
        axisColorPicker.disableProperty().bind(axisColorCheckBox.selectedProperty().not());
        axisColorPicker.valueProperty().addListener(e -> updateBGColor());
        axisColorCheckBox.selectedProperty().addListener(e -> updateBGColor());
        cross0ColorPicker.disableProperty().bind(cross0ColorCheckBox.selectedProperty().not());
        cross0ColorPicker.valueProperty().addListener(e -> updateBGColor());
        cross0ColorCheckBox.selectedProperty().addListener(e -> updateBGColor());
        cross1ColorPicker.disableProperty().bind(cross1ColorCheckBox.selectedProperty().not());
        cross1ColorPicker.valueProperty().addListener(e -> updateBGColor());
        cross1ColorCheckBox.selectedProperty().addListener(e -> updateBGColor());

        integralPosSlider.setMin(0.0);
        integralPosSlider.setMax(1.0);
        integralPosSlider.setLowValue(0.8);
        integralPosSlider.setHighValue(0.95);

        ticFontSizeComboBox.valueProperty().addListener(e -> updateChartsAndRefresh());
        labelFontSizeComboBox.valueProperty().addListener(e -> updateChartsAndRefresh());
        titlesCheckBox.selectedProperty().addListener(e -> updateChartsAndRefresh());
        parametersCheckBox.selectedProperty().addListener(e -> updateChartsAndRefresh());
        intensityAxisCheckBox.selectedProperty().addListener(e -> updateChartsAndRefresh());
        leftBorderSizeComboBox.valueProperty().addListener(e -> updateChartsAndRefresh());
        rightBorderSizeComboBox.valueProperty().addListener(e -> updateChartsAndRefresh());
        topBorderSizeComboBox.valueProperty().addListener(e -> updateChartsAndRefresh());
        bottomBorderSizeComboBox.valueProperty().addListener(e -> updateChartsAndRefresh());
        integralPosSlider.lowValueProperty().addListener(e -> setIntegralSliderText());
        integralPosSlider.highValueProperty().addListener(e -> setIntegralSliderText());
        regionCheckBox.selectedProperty().addListener(e -> refreshLater());
        integralCheckBox.selectedProperty().addListener(e -> refreshLater());
        gridCheckBox.selectedProperty().addListener(e -> updateChartsAndRefresh());
        offsetTrackingCheckBox.selectedProperty().addListener(e -> updateSlicesAndRefresh());
        useDatasetColorCheckBox.selectedProperty().addListener(e -> updateSlicesAndRefresh());
        slice1StateCheckBox.selectedProperty().addListener(e -> updateSlicesAndRefresh());
        slice2StateCheckBox.selectedProperty().addListener(e -> updateSlicesAndRefresh());
        xOffsetSlider.valueProperty().addListener(e -> updateSlicesAndRefresh());
        yOffsetSlider.valueProperty().addListener(e -> updateSlicesAndRefresh());
        scaleSlider.valueProperty().addListener(e -> updateSlicesAndRefresh());
        slice1ColorPicker.valueProperty().addListener(e -> updateSlicesAndRefresh());
        slice2ColorPicker.valueProperty().addListener(e -> updateSlicesAndRefresh());

        aspectCheckBox.selectedProperty().addListener(e -> updateAspectRatio());
        aspectSlider.setMin(0.1);
        aspectSlider.setMax(3.0);
        aspectSlider.setValue(1.0);
        aspectSlider.setBlockIncrement(0.01);
        aspectSlider.setOnMousePressed(e -> shiftState = e.isShiftDown());
        aspectSlider.valueProperty().addListener(e -> updateAspectRatio());
        lvlSlider.valueProperty().addListener(lvlSliderListener);
        lvlSlider.setOnMouseReleased(e -> setLvlSlider());
        GUIUtils.bindSliderField(lvlSlider, lvlField);
        lvlSlider1D.valueProperty().addListener(lvlSliderListener);
        lvlSlider1D.setOnMouseReleased(e -> setLvlSlider());
        GUIUtils.bindSliderField(lvlSlider1D, lvlField1D);

        clmSlider.valueProperty().addListener(clmSliderListener);
        clmSlider.setOnMouseReleased(e -> setClmSliderValue());
        GUIUtils.bindSliderField(clmSlider, clmField);

        nlvlsSlider.valueProperty().addListener(nlvlsSliderListener);
        nlvlsSlider.setOnMouseReleased(e -> setNlvlSlider());

        offsetSlider.valueProperty().addListener(offsetSliderListener);
        offsetSlider.setOnMouseReleased(e -> setOffsetsSlider());
        GUIUtils.bindSliderField(offsetSlider, offsetField);

        posColorPicker.valueProperty().addListener(posColorListener);
        posColorPicker1D.valueProperty().addListener(posColorListener);
        negColorPicker.valueProperty().addListener(negColorListener);

        posWidthSlider.valueProperty().addListener(posWidthSliderListener);
        posWidthSlider.setOnMouseReleased(e -> setPosWidthSlider(true));
        GUIUtils.bindSliderField(posWidthSlider, posWidthField);

        negWidthSlider.valueProperty().addListener(negWidthSliderListener);
        negWidthSlider.setOnMouseReleased(e -> setPosWidthSlider(false));
        GUIUtils.bindSliderField(negWidthSlider, negWidthField);

        posOnCheckbox.selectedProperty().addListener(posDrawOnListener);
        posOnCheckbox1D.selectedProperty().addListener(posDrawOnListener);
        negOnCheckbox.selectedProperty().addListener(negDrawOnListener);

        peakDisplayModeComboBox.getItems().addAll(PeakDisplayParameters.DisplayTypes.values());
        peakDisplayModeComboBox.valueProperty().addListener(peakDisplayTypeListener);
        peakLabelModeComboBox.getItems().addAll(PeakDisplayParameters.LabelTypes.values());
        peakLabelModeComboBox.valueProperty().addListener(peakLabelTypeListener);
        peakColorModeComboBox.getItems().addAll(PeakDisplayParameters.ColorTypes.values());
        peakColorModeComboBox.valueProperty().addListener(peakColorTypeListener);
        peakDisplayCheckBox.selectedProperty().addListener(drawPeaksListener);
        simPeakDisplayCheckBox.selectedProperty().addListener(drawSimPeaksListener);
        linkPeakDisplayCheckBox.selectedProperty().addListener(drawLinkPeaksListener);
        peakOnColorPicker.valueProperty().addListener(peakOnColorListener);
        peakOffColorPicker.valueProperty().addListener(peakOffColorListener);
        createViewGrid();
    }

    private void unBindChart(PolyChart polyChart) {
        offsetTrackingCheckBox.selectedProperty().unbindBidirectional(polyChart.sliceAttributes.offsetTrackingProperty());
        useDatasetColorCheckBox.selectedProperty().unbindBidirectional(polyChart.sliceAttributes.useDatasetColorProperty());
        slice1StateCheckBox.selectedProperty().unbindBidirectional(polyChart.sliceAttributes.slice1StateProperty());
        slice2StateCheckBox.selectedProperty().unbindBidirectional(polyChart.sliceAttributes.slice2StateProperty());
        xOffsetSlider.valueProperty().unbindBidirectional(polyChart.sliceAttributes.offsetXValueProperty());
        yOffsetSlider.valueProperty().unbindBidirectional(polyChart.sliceAttributes.offsetYValueProperty());
        scaleSlider.valueProperty().unbindBidirectional(polyChart.sliceAttributes.scaleValueProperty());
        slice1ColorPicker.valueProperty().unbindBidirectional(polyChart.sliceAttributes.slice1ColorProperty());
        slice2ColorPicker.valueProperty().unbindBidirectional(polyChart.sliceAttributes.slice2ColorProperty());

        intensityAxisCheckBox.selectedProperty().unbindBidirectional(polyChart.chartProps.intensityAxisProperty());
        ticFontSizeComboBox.valueProperty().unbindBidirectional(polyChart.chartProps.ticFontSizeProperty());
        labelFontSizeComboBox.valueProperty().unbindBidirectional(polyChart.chartProps.labelFontSizeProperty());

        leftBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.chartProps.leftBorderSizeProperty());
        rightBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.chartProps.rightBorderSizeProperty());
        topBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.chartProps.topBorderSizeProperty());
        bottomBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.chartProps.bottomBorderSizeProperty());


        gridCheckBox.selectedProperty().unbindBidirectional(polyChart.chartProps.gridProperty());
        regionCheckBox.selectedProperty().unbindBidirectional(polyChart.chartProps.regionsProperty());
        integralCheckBox.selectedProperty().unbindBidirectional(polyChart.chartProps.integralsProperty());

        integralPosSlider.lowValueProperty().unbindBidirectional(polyChart.chartProps.integralLowPosProperty());
        integralPosSlider.highValueProperty().unbindBidirectional(polyChart.chartProps.integralHighPosProperty());

        titlesCheckBox.selectedProperty().unbindBidirectional(polyChart.chartProps.titlesProperty());
        parametersCheckBox.selectedProperty().unbindBidirectional(polyChart.chartProps.parametersProperty());

        aspectSlider.valueProperty().unbindBidirectional(polyChart.chartProps.aspectRatioProperty());
        aspectCheckBox.selectedProperty().unbindBidirectional((polyChart.chartProps.aspectProperty()));
        chart.getDatasetAttributes().removeListener((ListChangeListener<? super DatasetAttributes>) e -> datasetsChanged());
    }

    public void bindToChart(PolyChart polyChart) {
        if (boundChart != null) {
            if (boundChart == polyChart) {
                return;
            } else {
                unBindChart(boundChart);
            }
        }
        boundChart = polyChart;
        offsetTrackingCheckBox.selectedProperty().bindBidirectional(polyChart.sliceAttributes.offsetTrackingProperty());
        useDatasetColorCheckBox.selectedProperty().bindBidirectional(polyChart.sliceAttributes.useDatasetColorProperty());
        slice1StateCheckBox.selectedProperty().bindBidirectional(polyChart.sliceAttributes.slice1StateProperty());
        slice2StateCheckBox.selectedProperty().bindBidirectional(polyChart.sliceAttributes.slice2StateProperty());
        xOffsetSlider.valueProperty().bindBidirectional(polyChart.sliceAttributes.offsetXValueProperty());
        yOffsetSlider.valueProperty().bindBidirectional(polyChart.sliceAttributes.offsetYValueProperty());
        scaleSlider.valueProperty().bindBidirectional(polyChart.sliceAttributes.scaleValueProperty());
        slice1ColorPicker.valueProperty().bindBidirectional(polyChart.sliceAttributes.slice1ColorProperty());
        slice2ColorPicker.valueProperty().bindBidirectional(polyChart.sliceAttributes.slice2ColorProperty());

        if (chart.chartProps.getAxesColor() == null) {
            axisColorCheckBox.setSelected(false);
        } else {
            axisColorPicker.setValue(polyChart.chartProps.axesColorProperty().get());
        }
        if (chart.chartProps.getBgColor() == null) {
            bgColorCheckBox.setSelected(false);
        } else {
            bgColorPicker.setValue(polyChart.chartProps.bgColorProperty().get());
        }
        if (chart.chartProps.getCross0Color() == null) {
            cross0ColorCheckBox.setSelected(false);
        } else {
            cross0ColorPicker.setValue(polyChart.chartProps.cross0ColorProperty().get());
        }
        if (chart.chartProps.getCross1Color() == null) {
            cross1ColorCheckBox.setSelected(false);
        } else {
            cross1ColorPicker.setValue(polyChart.chartProps.cross1ColorProperty().get());
        }


        intensityAxisCheckBox.selectedProperty().bindBidirectional(polyChart.chartProps.intensityAxisProperty());
        ticFontSizeComboBox.valueProperty().bindBidirectional(polyChart.chartProps.ticFontSizeProperty());
        labelFontSizeComboBox.valueProperty().bindBidirectional(polyChart.chartProps.labelFontSizeProperty());

        leftBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.chartProps.leftBorderSizeProperty());
        rightBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.chartProps.rightBorderSizeProperty());
        topBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.chartProps.topBorderSizeProperty());
        bottomBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.chartProps.bottomBorderSizeProperty());


        gridCheckBox.selectedProperty().bindBidirectional(polyChart.chartProps.gridProperty());
        regionCheckBox.selectedProperty().bindBidirectional(polyChart.chartProps.regionsProperty());
        integralCheckBox.selectedProperty().bindBidirectional(polyChart.chartProps.integralsProperty());

        integralPosSlider.lowValueProperty().bindBidirectional(polyChart.chartProps.integralLowPosProperty());
        integralPosSlider.highValueProperty().bindBidirectional(polyChart.chartProps.integralHighPosProperty());

        titlesCheckBox.selectedProperty().bindBidirectional(polyChart.chartProps.titlesProperty());
        parametersCheckBox.selectedProperty().bindBidirectional(polyChart.chartProps.parametersProperty());

        aspectSlider.valueProperty().bindBidirectional(polyChart.chartProps.aspectRatioProperty());
        aspectCheckBox.selectedProperty().bindBidirectional((polyChart.chartProps.aspectProperty()));

        chart.getDatasetAttributes().addListener((ListChangeListener<? super DatasetAttributes>) e -> datasetsChanged());
    }

    private void datasetsChanged() {
        OptionalInt maxNDim = chart.getDatasetAttributes().stream().mapToInt(d -> d.nDim).max();
        if (maxNDim.isPresent()) {
            setViewDims(Math.max(2, maxNDim.getAsInt()));
        }
        datasetChoiceBox.setItems(chart.getDatasetAttributes());
        if (!datasetChoiceBox.getItems().isEmpty()) {
            datasetChoiceBox.setValue(datasetChoiceBox.getItems().get(0));
        }
        updateDimensions();
    }

    private void refreshCharts() {
        if (datasetChoiceState.getValue() == SelectionChoice.WINDOW) {
            for (var controllerChart : fxmlController.getCharts()) {
                controllerChart.refresh();
            }
        } else {
            chart.refresh();
        }
    }

    private List<DatasetAttributes> getDatasetAttributes() {
        List<DatasetAttributes> result;
        if (datasetChoiceState.getValue() == SelectionChoice.DATASET) {
            if (datasetChoiceBox.getItems().isEmpty()) {
                result = Collections.emptyList();
            } else {
                result = List.of(datasetChoiceBox.getValue());
            }
        } else {
            result = new ArrayList<>();
            for (var aChart : getCharts(allCharts())) {
                result.addAll(aChart.getDatasetAttributes());
            }
        }
        return result;
    }

    private List<PeakListAttributes> getPeakListAttributes() {
        List<PeakListAttributes> result = new ArrayList<>();
        if (datasetChoiceState.getValue() != SelectionChoice.DATASET) {
            for (var aChart : getCharts(allCharts())) {
                result.addAll(aChart.getPeakListAttributes());
            }
        }
        return result;
    }

    private void updateDimensions() {
        int dim = 0;
        if (chart != null && chart.getDataset() != null) {
            chart.updateAxisType(true);
            dim = chart.getNDim();
        }
        setAxisControlValues();
        if (dim > 2) {
            setLimits();
        }
    }

    private void createViewGrid() {
        limitFields = new StringProperty[rowNames.length][2];
        labelFields = new Label[rowNames.length];
        int iRow = 1;
        dimCombos = new ComboBox[rowNames.length];
        axisLabels = new Label[rowNames.length];
        dimLabels = new Label[rowNames.length];
        viewBoxes = new HBox[rowNames.length];
        for (String rowName : rowNames) {
            int iRow0 = iRow - 1;
            MenuButton mButton = new MenuButton(rowName);
            MenuItem menuItem = new MenuItem("Full");
            mButton.getItems().add(menuItem);
            menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iRow0));
            if (iRow > 2) {
                menuItem = new MenuItem("First");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iRow0));
                menuItem = new MenuItem("Center");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iRow0));
                menuItem = new MenuItem("Last");
                mButton.getItems().add(menuItem);
                menuItem.addEventHandler(ActionEvent.ACTION, event -> dimMenuAction(event, iRow0));
            }

            TextField minField = new TextField();
            minField.setPrefWidth(60.0);
            limitFields[iRow - 1][0] = minField.textProperty();
            TextField maxField = new TextField();
            maxField.setPrefWidth(60.0);
            Label label = new Label("ppm");
            labelFields[iRow0] = label;
            label.setPrefWidth(40);
            Label axisLabel = new Label("");
            axisLabel.setPrefWidth(40);
            HBox hBox = new HBox();
            hBox.getChildren().addAll(mButton, minField, maxField, label);
            viewGrid.getChildren().add(hBox);
            viewBoxes[iRow - 1] = hBox;
            // viewGrid.add(axisLabel, 5, iRow);
            axisLabels[iRow - 1] = axisLabel;
            limitFields[iRow - 1][1] = maxField.textProperty();
            iRow++;
        }
    }

    void setViewDims(int nDim) {
        int currentN = viewGrid.getChildren().size();
        if (currentN < nDim) {
            for (int i = currentN; i < nDim; i++) {
                viewGrid.getChildren().add(viewBoxes[i]);
            }
        } else if (currentN > nDim) {
            viewGrid.getChildren().remove(nDim, currentN);
        }
    }

    public void setViewBound(int dim, int minMax, double value) {
        StringProperty limitProp = limitFields[dim][minMax];
        limitProp.setValue(FORMATTER.format(value));
    }

    void setLimits() {
        int i = 0;
        for (NMRAxis axis : chart.axes) {
            double lower = axis.getLowerBound();
            double upper = axis.getUpperBound();
            setViewBound(i, 0, lower);
            setViewBound(i, 1, upper);
            labelFields[i].setText(chart.axModes[i].name().toLowerCase());
            if ((i > 1) && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                int lowPt = chart.axModes[i].getIndex(dataAttr, i, lower);
                int upPt = chart.axModes[i].getIndex(dataAttr, i, upper);

                int center = ((lowPt + upPt) / 2);
                chart.controller.getStatusBar().updatePlaneSpinner(center, i);
            }
            i++;
        }
    }

    private void dimMenuAction(ActionEvent event, int iAxis) {
        MenuItem menuItem = (MenuItem) event.getSource();
        if (menuItem.getText().equals("Full")) {
            chart.full(iAxis);
        } else if (menuItem.getText().equals("Center")) {
            chart.center(iAxis);
        } else if (menuItem.getText().equals("First")) {
            chart.firstPlane(iAxis);
        } else if (menuItem.getText().equals("Last")) {
            chart.lastPlane(iAxis);
        }
    }

    private void dimAction(String rowName, Event e) {
        DatasetAttributes datasetAttr = chart.datasetAttributesList.get(0);
        ComboBox cBox = (ComboBox) e.getSource();
        int iDim = cBox.getSelectionModel().getSelectedIndex();
        datasetAttr.setDim(rowName, iDim);
        for (int i = 0; i < chart.getNDim(); i++) {
            if (i < 2) {
                dimCombos[i].getSelectionModel().select(datasetAttr.dim[i]);
            } else {
                dimLabels[i].setText(String.valueOf(datasetAttr.dim[i] + 1));
            }
            axisLabels[i].setText(chart.axModes[i].getDatasetLabel(datasetAttr, i));
            // fixme  should be able to swap existing limits, not go to full
            chart.full(i);
        }
    }

    @FXML
    private void updateSlices() {
        final boolean status = sliceStatusCheckBox.isSelected();
        fxmlController.charts.forEach(c -> c.setSliceStatus(status));
    }

    abstract class ParSliderListener implements ChangeListener<Number> {

        boolean active = true;

        abstract void update(DatasetAttributes dataAttr, double value);

        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            if (active) {
                List<DatasetAttributes> dataAttrs = getDatasetAttributes();
                for (DatasetAttributes dataAttr : dataAttrs) {
                    update(dataAttr, newValue.doubleValue());
                }
                refreshCharts();
            }
        }
    }

    public class LvlSliderListener extends ParSliderListener {
        void update(DatasetAttributes dataAttr, double value) {
            dataAttr.setLvl(value);
        }
    }

    public class ClmSliderListener extends ParSliderListener {
        void update(DatasetAttributes dataAttr, double value) {
            dataAttr.setClm(value);
        }
    }

    public class OffsetSliderListener extends ParSliderListener {
        void update(DatasetAttributes dataAttr, double value) {
            dataAttr.setOffset(value);
        }
    }

    public class NlvlSliderListener extends ParSliderListener {
        void update(DatasetAttributes dataAttr, double value) {
            int iValue = (int) Math.round(value);
            nlvlsField.setText(String.format("%d", iValue));
            dataAttr.setNlvls(iValue);
        }
    }

    public class PosWidthSliderListener extends ParSliderListener {
        void update(DatasetAttributes dataAttr, double value) {
            dataAttr.setPosWidth(value);
        }
    }

    public class NegWidthSliderListener extends ParSliderListener {
        void update(DatasetAttributes dataAttr, double value) {
            dataAttr.setNegWidth(value);
        }
    }

    void setSlider(ParSliderListener sliderListener, Slider slider, double min, double max, double incrValue, double value) {
        sliderListener.active = false;
        slider.setMin(min);
        slider.setMax(max);
        slider.setBlockIncrement(incrValue);
        slider.setValue(value);
        sliderListener.active = true;
    }

    void setLvlSlider() {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            double value = dataAttr.getLvl();
            double min = value / 5.0;
            double max = value * 5.0;
            double incrValue = value / 50.0;
            setSlider(lvlSliderListener, lvlSlider, min, max, incrValue, value);
            setSlider(lvlSliderListener, lvlSlider1D, min, max, incrValue, value);
        }
    }

    void setClmSliderValue() {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            double min = 1.01;
            double max = 4.0;
            double incrValue = 0.01;
            double value = dataAttr.getClm();
            setSlider(clmSliderListener, clmSlider, min, max, incrValue, value);
        }
    }

    void setOffsetsSlider() {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            double min = 0.0;
            double max = 1.0;
            double incrValue = 0.01;
            double value = dataAttr.getOffset();
            setSlider(offsetSliderListener, offsetSlider, min, max, incrValue, value);
        }
    }

    void setNlvlSlider() {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            double min = 1.0;
            double max = 50.0;
            double incrValue = 1.0;
            double value = dataAttr.getNlvls();
            setSlider(nlvlsSliderListener, nlvlsSlider, min, max, incrValue, value);
            int iValue = (int) Math.round(value);
            nlvlsField.setText(String.format("%d", iValue));
        }
    }

    void setPosWidthSlider(boolean posMode) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            double value = posMode ? dataAttr.getPosWidth() : dataAttr.getNegWidth();
            double min = 0.1;
            double max = 3.0;
            double incrValue = 0.1;
            if (posMode) {
                setSlider(posWidthSliderListener, posWidthSlider, min, max, incrValue, value);
            } else {
                setSlider(negWidthSliderListener, negWidthSlider, min, max, incrValue, value);
            }
        }
    }

    abstract class ColorListener implements ChangeListener<Color> {

        boolean active = true;

        abstract void update(DatasetAttributes dataAttr, Color value);

        @Override
        public void changed(ObservableValue<? extends Color> observable, Color oldValue, Color newValue) {
            if (active) {
                List<DatasetAttributes> dataAttrs = getDatasetAttributes();
                for (DatasetAttributes dataAttr : dataAttrs) {
                    update(dataAttr, newValue);
                }
                refreshCharts();
            }
        }
    }

    public class PosColorListener extends ColorListener {
        void update(DatasetAttributes dataAttr, Color value) {
            dataAttr.setPosColor(value);
        }
    }

    public class NegColorListener extends ColorListener {
        void update(DatasetAttributes dataAttr, Color value) {
            dataAttr.setNegColor(value);
        }
    }

    void setContourColorControls(boolean posMode) {
        posColorListener.active = false;
        negColorListener.active = false;
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            if (posMode) {
                Color posColor = dataAttr.getPosColor();
                posColorPicker.setValue(posColor);
                posColorPicker1D.setValue(posColor);
            } else {
                Color negColor = dataAttr.getNegColor();
                negColorPicker.setValue(negColor);
            }
        }
        posColorListener.active = true;
        negColorListener.active = true;
    }

    abstract class DrawOnListener implements ChangeListener<Boolean> {

        boolean active = true;

        abstract void update(DatasetAttributes dataAttr, Boolean value);

        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            if (active) {
                List<DatasetAttributes> dataAttrs = getDatasetAttributes();
                for (DatasetAttributes dataAttr : dataAttrs) {
                    update(dataAttr, newValue);
                }
                refreshCharts();
            }
        }
    }

    public class PosDrawOnListener extends DrawOnListener {
        void update(DatasetAttributes dataAttr, Boolean value) {
            dataAttr.setPos(value);
        }
    }

    public class NegDrawOnListener extends DrawOnListener {
        void update(DatasetAttributes dataAttr, Boolean value) {
            dataAttr.setNeg(value);
        }
    }

    void setDrawOnControls(boolean posMode) {
        posDrawOnListener.active = false;
        negDrawOnListener.active = false;
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            if (posMode) {
                posOnCheckbox.setSelected(dataAttr.getPos());
                posOnCheckbox1D.setSelected(dataAttr.getPos());
            } else {
                negOnCheckbox.setSelected(dataAttr.getNeg());
            }
        }
        posDrawOnListener.active = true;
        negDrawOnListener.active = true;
    }

    abstract class PeakColorListener implements ChangeListener<Color> {

        boolean active = true;

        abstract void update(PeakListAttributes peakListAttr, Color value);

        @Override
        public void changed(ObservableValue<? extends Color> observable, Color oldValue, Color newValue) {
            if (active) {
                List<PeakListAttributes> peakListAttrs = getPeakListAttributes();
                for (PeakListAttributes peakListAttr : peakListAttrs) {
                    update(peakListAttr, newValue);
                }
                refreshCharts();
            }
        }
    }

    public class PeakOnColorListener extends PeakColorListener {
        void update(PeakListAttributes peakListAttr, Color value) {
            peakListAttr.setOnColor(value);
        }
    }

    public class PeakOffColorListener extends PeakColorListener {
        void update(PeakListAttributes peakListAttr, Color value) {
            peakListAttr.setOffColor(value);
        }
    }

    void setPeakColorControls(boolean onMode) {
        peakOnColorListener.active = false;
        peakOffColorListener.active = false;
        List<PeakListAttributes> peakListAttrs = chart.getPeakListAttributes();
        if (!peakListAttrs.isEmpty()) {
            PeakListAttributes peakListAttr = peakListAttrs.get(0);
            if (onMode) {
                Color onColor = peakListAttr.getOnColor();
                peakOnColorPicker.setValue(onColor);
            } else {
                Color offColor = peakListAttr.getOffColor();
                peakOffColorPicker.setValue(offColor);
            }
        }
        peakOnColorListener.active = true;
        peakOffColorListener.active = true;
    }

    abstract class PeakCheckBoxListener implements ChangeListener<Boolean> {

        boolean active = true;

        abstract void update(PeakListAttributes peakListAttr, Boolean value);

        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            if (active) {
                List<PeakListAttributes> peakListAttrs = getPeakListAttributes();
                for (PeakListAttributes peakListAttr : peakListAttrs) {
                    update(peakListAttr, newValue);
                }
                refreshCharts();
            }
        }
    }

    public class DrawPeaksListener extends PeakCheckBoxListener {
        void update(PeakListAttributes peakListAttr, Boolean value) {
            peakListAttr.setDrawPeaks(value);
        }
    }

    public class DrawSimPeaksListener extends PeakCheckBoxListener {
        void update(PeakListAttributes peakListAttr, Boolean value) {
            peakListAttr.setSimPeaks(value);
        }
    }

    public class DrawLinkPeaksListener extends PeakCheckBoxListener {
        void update(PeakListAttributes peakListAttr, Boolean value) {
            peakListAttr.setDrawLinks(value);
        }
    }

    void setPeakCheckBoxes() {
        List<PeakListAttributes> peakListAttrs = chart.getPeakListAttributes();
        if (!peakListAttrs.isEmpty()) {
            PeakListAttributes peakListAttr = peakListAttrs.get(0);
            PeakCheckBoxListener[] listeners = {drawPeaksListener, drawSimPeaksListener, drawLinkPeaksListener};
            for (var listener : listeners) {
                listener.active = false;
                if (listener instanceof DrawPeaksListener) {
                    peakDisplayCheckBox.setSelected(peakListAttr.getDrawPeaks());
                } else if (listener instanceof DrawSimPeaksListener) {
                    simPeakDisplayCheckBox.setSelected(peakListAttr.getSimPeaks());
                } else if (listener instanceof DrawLinkPeaksListener) {
                    linkPeakDisplayCheckBox.setSelected(peakListAttr.getDrawLinks());
                }
                listener.active = true;
            }
        }
    }

    abstract class PeakTypeListener<T> implements ChangeListener {
        boolean active = true;

        abstract void update(PeakListAttributes peakListAttributes, T obj);

        @Override
        public void changed(ObservableValue observable,
                            Object oldValue, Object newValue) {
            if (active) {
                List<PeakListAttributes> peakListAttrs = getPeakListAttributes();
                for (PeakListAttributes peakListAttr : peakListAttrs) {
                    update(peakListAttr, (T) newValue);
                }
                refreshCharts();
            }
        }
    }

    class PeakColorTypeListener extends PeakTypeListener<PeakDisplayParameters.ColorTypes> {
        void update(PeakListAttributes peakListAttr, PeakDisplayParameters.ColorTypes value) {
            peakListAttr.setColorType(value);
        }
    }


    class PeakDisplayTypeListener extends PeakTypeListener<PeakDisplayParameters.DisplayTypes> {
        void update(PeakListAttributes peakListAttr, PeakDisplayParameters.DisplayTypes value) {
            peakListAttr.setDisplayType(value);
        }
    }

    class PeakLabelTypeListener extends PeakTypeListener<PeakDisplayParameters.LabelTypes> {
        void update(PeakListAttributes peakListAttr, PeakDisplayParameters.LabelTypes value) {
            peakListAttr.setLabelType(value);
        }
    }

    void setPeakDisplayComboBoxes() {
        List<PeakListAttributes> peakListAttrs = chart.getPeakListAttributes();
        if (!peakListAttrs.isEmpty()) {
            PeakListAttributes peakListAttr = peakListAttrs.get(0);
            PeakTypeListener[] listeners = {peakDisplayTypeListener, peakLabelTypeListener, peakColorTypeListener};
            for (var listener : listeners) {
                listener.active = false;
                if (listener instanceof PeakColorTypeListener) {
                    peakColorModeComboBox.setValue(peakListAttr.getColorType());
                } else if (listener instanceof PeakDisplayTypeListener) {
                    peakDisplayModeComboBox.setValue(peakListAttr.getDisplayType());
                } else if (listener instanceof PeakLabelTypeListener) {
                    peakLabelModeComboBox.setValue(peakListAttr.getLabelType());
                }
                listener.active = true;
            }
        }
    }

    public void setChart(PolyChart chart) {
        this.chart = chart;
        setAttributeControls();
    }

    private boolean isShowing() {
        return fxmlController.isSideBarAttributesShowing();
    }

    private void setContourSliders() {
        setLvlSlider();
        setNlvlSlider();
        setClmSliderValue();
        setOffsetsSlider();
        setPosWidthSlider(true);
        setPosWidthSlider(false);
    }

    void setDimControls() {
        if (chart.is1D() && (accordionIn1D != Boolean.TRUE)) {
            attributesAccordion.getPanes().removeAll(contourLevelPane, contourAppearancePane, oneDPane, integralsPane);
            attributesAccordion.getPanes().add(2, integralsPane);
            attributesAccordion.getPanes().add(2, oneDPane);
            accordionIn1D = true;
        } else if (!chart.is1D() && (accordionIn1D != Boolean.FALSE)) {
            attributesAccordion.getPanes().removeAll(contourLevelPane, contourAppearancePane, oneDPane, integralsPane);
            attributesAccordion.getPanes().add(2, contourAppearancePane);
            attributesAccordion.getPanes().add(2, contourLevelPane);
            accordionIn1D = false;
        }
    }

    void setAxisControlValues() {
        // fix me is this right
        int start = 0;
        if (!chart.getDatasetAttributes().isEmpty()) {
            DatasetAttributes datasetAttr = chart.datasetAttributesList.get(0);
            for (int i = 0; i < chart.getNDim(); i++) {
                if ((i < 2) && (dimCombos[i] != null)) {
                    dimCombos[i].getSelectionModel().select(datasetAttr.dim[i]);
                }
                axisLabels[i].setText(chart.axModes[i].getDatasetLabel(datasetAttr, i));
            }
            start = chart.getNDim();
        }
        for (int i = start; i < limitFields.length; i++) {
            limitFields[i][0].set("");
            limitFields[i][1].set("");
            axisLabels[i].setText("");

        }
    }

    public void setAttributeControls() {
        if ((chart != null) && isShowing()) {
            chart = fxmlController.getActiveChart();
            chart.setChartDisabled(true);
            datasetsChanged();
            setContourSliders();
            setContourColorControls(true);
            setContourColorControls(false);
            setDrawOnControls(true);
            setDrawOnControls(false);
            setPeakCheckBoxes();
            setPeakColorControls(true);
            setPeakColorControls(false);
            setPeakDisplayComboBoxes();

//            updatePeakListTableView(false);
            //           clearDimActions();
            bindToChart(chart);
            setLimits();
            setDimControls();

//            updateDatasetView();
//            updatePeakView();
//            updateDims();
//            setupDimActions();
//            datasetTableView.getSelectionModel().clearSelection();
            chart.setChartDisabled(false);
        }
    }

    @FXML
    private void sliceAction() {
        getCharts(allCharts()).forEach(aChart -> {
            aChart.sliceAttributes.setSlice1Color(slice1ColorPicker.getValue());
            aChart.sliceAttributes.setSlice2Color(slice2ColorPicker.getValue());
            aChart.getCrossHairs().refreshCrossHairs();
        });
    }

    @FXML
    private void showPosSchema(Event event) {
        showSchema(event, true);
    }

    @FXML
    private void showNegSchema(Event event) {
        showSchema(event, false);
    }

    private void showSchema(Event event, boolean posMode) {
        Node node = (Node) event.getSource();
        Bounds bounds = node.getBoundsInLocal();
        Bounds screenBounds = node.localToScreen(bounds);
        double x = screenBounds.getMinX() - 100.0;
        double y = screenBounds.getMaxY();
        if (posMode) {
            updatePositiveColorsWithSchema(x, y);
        } else {
            updateNegativeColorsWithSchema(x, y);
        }
    }

    void updatePositiveColorsWithSchema(double x, double y) {
        ColorSchemes.showSchemaChooser(this::updatePositiveColorsWithSchema, x, y);
    }

    void updateNegativeColorsWithSchema(double x, double y) {
        ColorSchemes.showSchemaChooser(this::updateNegativeColorsWithSchema, x, y);
    }

    public void updateNegativeColorsWithSchema(String colorName) {
        updateColorsWithSchema(colorName, false);

    }

    public void updatePositiveColorsWithSchema(String colorName) {
        updateColorsWithSchema(colorName, true);
    }

    public void updateColorsWithSchema(String colorName, boolean posColors) {
        List<PolyChart> applyCharts = getCharts(allCharts());
        for (PolyChart applyChart : applyCharts) {

            var items = applyChart.getDatasetAttributes();
            if (items.size() < 2) {
                return;
            }
            int i = 0;
            List<Color> colors = ColorSchemes.getColors(colorName, items.size());
            for (DatasetAttributes dataAttr : items) {
                Color color = colors.get(i++);
                if (posColors) {
                    dataAttr.setPosColor(color);
                } else {
                    dataAttr.setNegColor(color);
                }
            }
            applyChart.refresh();
        }
    }

    private void updateBGColor() {
        // check to see if the new background color is the same as the pos color
        // for first dataset.  If it is, change the color of the first dataset
        // so it is visible.
        if (bgColorCheckBox.isSelected()) {
            Color color = bgColorPicker.getValue();
            if (!chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = getDatasetAttributes().get(0);
                Color posColor = dataAttr.getPosColor();
                if ((posColor != null) && (color != null)) {
                    double diff = Math.abs(posColor.getRed() - color.getRed());
                    diff += Math.abs(posColor.getGreen() - color.getGreen());
                    diff += Math.abs(posColor.getBlue() - color.getBlue());
                    if (diff < 0.05) {
                        dataAttr.setPosColor(PolyChart.chooseBlackWhite(color));
                    }
                }
            }
        }
        redraw();
    }

    void redraw() {
        updateChartProperties();
    }

    @FXML
    private void updateChartProperties() {
        for (var aChart: getCharts(allCharts())) {
            if (bgColorCheckBox.isSelected()) {
                aChart.chartProps.setBgColor(bgColorPicker.getValue());
            } else {
                aChart.chartProps.setBgColor(null);
            }
            if (axisColorCheckBox.isSelected()) {
                aChart.chartProps.setAxesColor(axisColorPicker.getValue());
            } else {
                aChart.chartProps.setAxesColor(null);
            }
            if (cross0ColorCheckBox.isSelected()) {
                aChart.chartProps.setCross0Color(cross0ColorPicker.getValue());
            } else {
                aChart.chartProps.setCross0Color(null);
            }
            if (cross1ColorCheckBox.isSelected()) {
                aChart.chartProps.setCross1Color(cross1ColorPicker.getValue());
            } else {
                aChart.chartProps.setCross1Color(null);
            }
            refreshLater(aChart);
        }
    }

    void setIntegralSliderText() {
        integralLowValue.setText(String.format("%.2f", integralPosSlider.getLowValue()));
        integralHighValue.setText(String.format("%.2f", integralPosSlider.getHighValue()));
        refreshLater();
    }

    boolean allCharts() {
        return datasetChoiceState.getValue() == SelectionChoice.WINDOW;
    }

    List<PolyChart> getCharts(boolean all) {
        if (all) {
            return chart.getController().getCharts();
        } else {
            return Collections.singletonList(chart);
        }
    }

    void updateAspectRatio() {
        List<PolyChart> applyCharts = getCharts(allCharts());
        for (PolyChart applyChart : applyCharts) {
            applyChart.chartProps.setAspect(aspectCheckBox.isSelected());
            double aspectRatio = aspectSlider.getValue();
            applyChart.chartProps.setAspectRatio(aspectRatio);
            aspectRatioValue.setText(String.format("%.2f", aspectRatio));
            applyChart.refresh();
        }
    }

    private void refreshSlices(PolyChart aChart) {
        aChart.getCrossHairs().refreshCrossHairs();
    }
    private void updateChartsAndRefresh() {
        PauseTransition wait = new PauseTransition(Duration.millis(5.0));
        wait.setOnFinished(e -> ConsoleUtil.runOnFxThread(this::updateChartsAndRefreshNow));
        wait.play();
    }

    private void updateChartsAndRefreshNow() {
        for (var aChart:getCharts(allCharts())) {
            if (aChart != chart) {
                chart.chartProps.copyTo(aChart);
            }
            refreshLater(aChart);
        }
    }

    private void updateSlicesAndRefresh() {
        PauseTransition wait = new PauseTransition(Duration.millis(5.0));
        wait.setOnFinished(e -> ConsoleUtil.runOnFxThread(this::updateSlicesAndRefreshNow));
        wait.play();
    }

    private void updateSlicesAndRefreshNow() {
        for (var aChart:getCharts(allCharts())) {
            if (aChart != chart) {
                chart.getSliceAttributes().copyTo(aChart);
            }
            refreshSlices(aChart);
        }
    }

    private void refreshLater() {
        refreshLater(chart);
    }

    // add delay so bindings between properties and controls activate before refresh
    private void refreshLater(PolyChart aChart) {
        if (!aChart.isChartDisabled()) {
            PauseTransition wait = new PauseTransition(Duration.millis(5.0));
            wait.setOnFinished(e -> ConsoleUtil.runOnFxThread(aChart::refresh));
            wait.play();
        }
    }
}
