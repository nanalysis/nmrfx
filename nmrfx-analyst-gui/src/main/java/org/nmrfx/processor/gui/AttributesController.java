package org.nmrfx.processor.gui;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.controlsfx.control.RangeSlider;
import org.nmrfx.chart.Axis;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ColorSchemes;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import static org.nmrfx.processor.gui.utils.GUIColorUtils.toBlackOrWhite;

public class AttributesController implements Initializable, NmrControlRightSideContent {
    private static final Logger log = LoggerFactory.getLogger(AttributesController.class);
    static final DecimalFormat FORMATTER = new DecimalFormat();

    static {
        FORMATTER.setMaximumFractionDigits(3);
    }
    @FXML
    private VBox attributesVBox;

    enum SelectionChoice {
        CHART,
        WINDOW
    }

    @FXML
    VBox applyVBox;
    @FXML
    ScrollPane attributeScrollPane;
    @FXML
    ChoiceBox<SelectionChoice> itemChoiceState;
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
    TitledPane peakAppearancePane;
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
    @FXML
    Slider scaleSlider;
    @FXML
    private CheckBox offsetTrackingCheckBox;
    @FXML
    private CheckBox useDatasetColorCheckBox;
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
    Slider stackXSlider;
    @FXML
    TextField stackXField;
    @FXML
    Slider stackYSlider;
    @FXML
    TextField stackYField;

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
    ChartSliderListener stackXListener = new StackXSliderListener();
    ChartSliderListener stackYListener = new StackYSliderListener();

    PosColorListener posColorListener = new PosColorListener();
    NegColorListener negColorListener = new NegColorListener();
    PosDrawOnListener posDrawOnListener = new PosDrawOnListener();
    NegDrawOnListener negDrawOnListener = new NegDrawOnListener();
    @FXML
    Slider posWidthSlider;
    @FXML
    TextField posWidthField;
    @FXML
    Slider posWidthSlider1D;
    @FXML
    TextField posWidthField1D;
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
    @FXML
    private ComboBox<Integer> peakNPlanesComboBox;
    PeakOnColorListener peakOnColorListener = new PeakOnColorListener();
    PeakOffColorListener peakOffColorListener = new PeakOffColorListener();
    DrawPeaksListener drawPeaksListener = new DrawPeaksListener();
    DrawSimPeaksListener drawSimPeaksListener = new DrawSimPeaksListener();
    DrawLinkPeaksListener drawLinkPeaksListener = new DrawLinkPeaksListener();
    PeakDisplayTypeListener peakDisplayTypeListener = new PeakDisplayTypeListener();
    PeakColorTypeListener peakColorTypeListener = new PeakColorTypeListener();
    PeakLabelTypeListener peakLabelTypeListener = new PeakLabelTypeListener();

    PeakNPlanesListener peakNPlanesListener = new PeakNPlanesListener();

    Boolean accordionIn1D = null;
    PolyChart chart;
    PolyChart boundChart = null;

    FXMLController fxmlController;

    public static AttributesController create(FXMLController fxmlController) {
        Fxml.Builder builder = Fxml.load(AttributesController.class, "AttributesController.fxml");
        AttributesController controller = builder.getController();
        controller.fxmlController = fxmlController;
        controller.itemChoiceState.getItems().addAll(SelectionChoice.values());
        controller.itemChoiceState.setValue(SelectionChoice.CHART);
        controller.setChart(fxmlController.getActiveChart());
        return controller;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ticFontSizeComboBox.getItems().addAll(5, 6, 7, 8, 9,
                10, 11, 12, 14, 16, 18, 20, 22, 24, 26,
                28, 32, 36);
        labelFontSizeComboBox.getItems().addAll(5, 6, 7, 8, 9,
                10, 11, 12, 14, 16, 18, 20, 22, 24, 26,
                28, 32, 36);
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

        ticFontSizeComboBox.valueProperty().addListener(e -> updateCharts());
        labelFontSizeComboBox.valueProperty().addListener(e -> updateCharts());
        titlesCheckBox.selectedProperty().addListener(e -> updateCharts());
        parametersCheckBox.selectedProperty().addListener(e -> updateCharts());
        intensityAxisCheckBox.selectedProperty().addListener(e -> updateCharts());
        leftBorderSizeComboBox.valueProperty().addListener(e -> updateCharts());
        rightBorderSizeComboBox.valueProperty().addListener(e -> updateCharts());
        topBorderSizeComboBox.valueProperty().addListener(e -> updateCharts());
        bottomBorderSizeComboBox.valueProperty().addListener(e -> updateCharts());
        integralPosSlider.lowValueProperty().addListener(e -> setIntegralSliderText());
        integralPosSlider.highValueProperty().addListener(e -> setIntegralSliderText());
        regionCheckBox.selectedProperty().addListener(e -> refreshLater());
        integralCheckBox.selectedProperty().addListener(e -> refreshLater());
        gridCheckBox.selectedProperty().addListener(e -> updateCharts());
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
        aspectSlider.valueProperty().addListener(e -> updateAspectRatio());

        stackXSlider.setMin(0.0);
        stackXSlider.setMax(1.00);
        stackXSlider.setValue(0.0);
        stackXSlider.setBlockIncrement(0.01);
        stackXSlider.valueProperty().addListener(stackXListener);
        stackXSlider.setOnMouseReleased(e -> setStackXSlider());
        GUIUtils.bindSliderField(stackXSlider, stackXField);


        stackYSlider.setMin(0.0);
        stackYSlider.setMax(1.0);
        stackYSlider.setValue(0.0);
        stackYSlider.setBlockIncrement(0.01);
        stackYSlider.valueProperty().addListener(stackYListener);
        stackYSlider.setOnMouseReleased(e -> setStackYSlider());
        GUIUtils.bindSliderField(stackYSlider, stackYField);


        lvlSlider.valueProperty().addListener(lvlSliderListener);
        lvlSlider.setOnMouseReleased(e -> setLvlSlider());
        GUIUtils.bindSliderField(lvlSlider, lvlField, "0.##E0");
        lvlSlider1D.valueProperty().addListener(lvlSliderListener);
        lvlSlider1D.setOnMouseReleased(e -> setLvlSlider());
        GUIUtils.bindSliderField(lvlSlider1D, lvlField1D, "0.##E0");

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

        posWidthSlider1D.valueProperty().addListener(posWidthSliderListener);
        posWidthSlider1D.setOnMouseReleased(e -> setPosWidthSlider(true));
        GUIUtils.bindSliderField(posWidthSlider1D, posWidthField1D);

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
        peakNPlanesComboBox.getItems().addAll(0, 1, 2, 3, 4);
        peakNPlanesComboBox.valueProperty().addListener(peakNPlanesListener);
    }

    public Pane getPane() {
        return attributesVBox;
    }

    private void unBindChart(PolyChart polyChart) {
        offsetTrackingCheckBox.selectedProperty().unbindBidirectional(polyChart.getSliceAttributes().offsetTrackingProperty());
        useDatasetColorCheckBox.selectedProperty().unbindBidirectional(polyChart.getSliceAttributes().useDatasetColorProperty());
        slice1StateCheckBox.selectedProperty().unbindBidirectional(polyChart.getSliceAttributes().slice1StateProperty());
        slice2StateCheckBox.selectedProperty().unbindBidirectional(polyChart.getSliceAttributes().slice2StateProperty());
        xOffsetSlider.valueProperty().unbindBidirectional(polyChart.getSliceAttributes().offsetXValueProperty());
        yOffsetSlider.valueProperty().unbindBidirectional(polyChart.getSliceAttributes().offsetYValueProperty());
        scaleSlider.valueProperty().unbindBidirectional(polyChart.getSliceAttributes().scaleValueProperty());
        slice1ColorPicker.valueProperty().unbindBidirectional(polyChart.getSliceAttributes().slice1ColorProperty());
        slice2ColorPicker.valueProperty().unbindBidirectional(polyChart.getSliceAttributes().slice2ColorProperty());

        intensityAxisCheckBox.selectedProperty().unbindBidirectional(polyChart.getChartProperties().intensityAxisProperty());
        ticFontSizeComboBox.valueProperty().unbindBidirectional(polyChart.getChartProperties().ticFontSizeProperty());
        labelFontSizeComboBox.valueProperty().unbindBidirectional(polyChart.getChartProperties().labelFontSizeProperty());

        leftBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.getChartProperties().leftBorderSizeProperty());
        rightBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.getChartProperties().rightBorderSizeProperty());
        topBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.getChartProperties().topBorderSizeProperty());
        bottomBorderSizeComboBox.valueProperty().unbindBidirectional(polyChart.getChartProperties().bottomBorderSizeProperty());


        gridCheckBox.selectedProperty().unbindBidirectional(polyChart.getChartProperties().gridProperty());
        regionCheckBox.selectedProperty().unbindBidirectional(polyChart.getChartProperties().regionsProperty());
        integralCheckBox.selectedProperty().unbindBidirectional(polyChart.getChartProperties().integralsProperty());

        integralPosSlider.lowValueProperty().unbindBidirectional(polyChart.getChartProperties().integralLowPosProperty());
        integralPosSlider.highValueProperty().unbindBidirectional(polyChart.getChartProperties().integralHighPosProperty());

        titlesCheckBox.selectedProperty().unbindBidirectional(polyChart.getChartProperties().titlesProperty());
        parametersCheckBox.selectedProperty().unbindBidirectional(polyChart.getChartProperties().parametersProperty());

        stackXSlider.valueProperty().unbindBidirectional(polyChart.getChartProperties().stackXProperty());
        stackYSlider.valueProperty().unbindBidirectional(polyChart.getChartProperties().stackYProperty());

        aspectSlider.valueProperty().unbindBidirectional(polyChart.getChartProperties().aspectRatioProperty());
        aspectCheckBox.selectedProperty().unbindBidirectional((polyChart.getChartProperties().aspectProperty()));
        chart.getDatasetAttributes().removeListener((ListChangeListener<? super DatasetAttributes>) e -> datasetsChanged());
        chart.getPeakListAttributes().removeListener((ListChangeListener<? super PeakListAttributes>) e -> peakListsChanged());

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
        offsetTrackingCheckBox.selectedProperty().bindBidirectional(polyChart.getSliceAttributes().offsetTrackingProperty());
        useDatasetColorCheckBox.selectedProperty().bindBidirectional(polyChart.getSliceAttributes().useDatasetColorProperty());
        slice1StateCheckBox.selectedProperty().bindBidirectional(polyChart.getSliceAttributes().slice1StateProperty());
        slice2StateCheckBox.selectedProperty().bindBidirectional(polyChart.getSliceAttributes().slice2StateProperty());
        xOffsetSlider.valueProperty().bindBidirectional(polyChart.getSliceAttributes().offsetXValueProperty());
        yOffsetSlider.valueProperty().bindBidirectional(polyChart.getSliceAttributes().offsetYValueProperty());
        scaleSlider.valueProperty().bindBidirectional(polyChart.getSliceAttributes().scaleValueProperty());
        slice1ColorPicker.valueProperty().bindBidirectional(polyChart.getSliceAttributes().slice1ColorProperty());
        slice2ColorPicker.valueProperty().bindBidirectional(polyChart.getSliceAttributes().slice2ColorProperty());

        if (chart.getChartProperties().getAxesColor() == null) {
            axisColorCheckBox.setSelected(false);
        } else {
            axisColorPicker.setValue(polyChart.getChartProperties().axesColorProperty().get());
        }
        if (chart.getChartProperties().getBgColor() == null) {
            bgColorCheckBox.setSelected(false);
        } else {
            bgColorPicker.setValue(polyChart.getChartProperties().bgColorProperty().get());
        }
        if (chart.getChartProperties().getCross0Color() == null) {
            cross0ColorCheckBox.setSelected(false);
        } else {
            cross0ColorPicker.setValue(polyChart.getChartProperties().cross0ColorProperty().get());
        }
        if (chart.getChartProperties().getCross1Color() == null) {
            cross1ColorCheckBox.setSelected(false);
        } else {
            cross1ColorPicker.setValue(polyChart.getChartProperties().cross1ColorProperty().get());
        }


        intensityAxisCheckBox.selectedProperty().bindBidirectional(polyChart.getChartProperties().intensityAxisProperty());
        ticFontSizeComboBox.valueProperty().bindBidirectional(polyChart.getChartProperties().ticFontSizeProperty());
        labelFontSizeComboBox.valueProperty().bindBidirectional(polyChart.getChartProperties().labelFontSizeProperty());

        leftBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.getChartProperties().leftBorderSizeProperty());
        rightBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.getChartProperties().rightBorderSizeProperty());
        topBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.getChartProperties().topBorderSizeProperty());
        bottomBorderSizeComboBox.valueProperty().bindBidirectional(polyChart.getChartProperties().bottomBorderSizeProperty());


        gridCheckBox.selectedProperty().bindBidirectional(polyChart.getChartProperties().gridProperty());
        regionCheckBox.selectedProperty().bindBidirectional(polyChart.getChartProperties().regionsProperty());
        integralCheckBox.selectedProperty().bindBidirectional(polyChart.getChartProperties().integralsProperty());

        integralPosSlider.lowValueProperty().bindBidirectional(polyChart.getChartProperties().integralLowPosProperty());
        integralPosSlider.highValueProperty().bindBidirectional(polyChart.getChartProperties().integralHighPosProperty());

        titlesCheckBox.selectedProperty().bindBidirectional(polyChart.getChartProperties().titlesProperty());
        parametersCheckBox.selectedProperty().bindBidirectional(polyChart.getChartProperties().parametersProperty());

        stackXSlider.valueProperty().bindBidirectional(polyChart.getChartProperties().stackXProperty());
        stackYSlider.valueProperty().bindBidirectional(polyChart.getChartProperties().stackYProperty());

        aspectSlider.valueProperty().bindBidirectional(polyChart.getChartProperties().aspectRatioProperty());
        aspectCheckBox.selectedProperty().bindBidirectional((polyChart.getChartProperties().aspectProperty()));

        chart.getDatasetAttributes().addListener((ListChangeListener<? super DatasetAttributes>) e -> datasetsChanged());
        chart.getPeakListAttributes().addListener((ListChangeListener<? super PeakListAttributes>) e -> peakListsChanged());
    }

    private void peakListsChanged() {
        setPeakControls();
    }

    private void datasetsChanged() {
        setDatasetControls();
    }

    private void refreshCharts() {
        if (itemChoiceState.getValue() == SelectionChoice.WINDOW) {
            for (var controllerChart : fxmlController.getCharts()) {
                controllerChart.refresh();
            }
        } else {
            chart.refresh();
        }
    }

    private List<DatasetAttributes> getDatasetAttributes() {
        List<DatasetAttributes> result;
        result = new ArrayList<>();
        for (var aChart : getCharts(allCharts())) {
            result.addAll(aChart.getDatasetAttributes());
        }
        return result;
    }

    private List<PeakListAttributes> getPeakListAttributes() {
        List<PeakListAttributes> result;
        result = new ArrayList<>();
        for (var aChart : getCharts(allCharts())) {
            result.addAll(aChart.getPeakListAttributes());
        }
        return result;
    }

    void setLimits() {
        for (int i = 0; i < chart.getAxes().count(); i++) {
            Axis axis = chart.getAxes().get(i);
            double lower = axis.getLowerBound();
            double upper = axis.getUpperBound();
            if (i > 1 && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                int lowPt = chart.getAxes().getMode(i).getIndex(dataAttr, i, lower);
                int upPt = chart.getAxes().getMode(i).getIndex(dataAttr, i, upper);

                chart.getFXMLController().getStatusBar().updatePlaneSpinner(lowPt, i, 0);
                chart.getFXMLController().getStatusBar().updatePlaneSpinner(upPt, i, 1);
            }
        }
    }

    @FXML
    private void updateSlices() {
        fxmlController.getStatusBar().updateSlices(false);
    }

    abstract class ChartSliderListener implements ChangeListener<Number> {

        boolean active = true;

        abstract void update(PolyChart chart, double value);

        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            if (active) {
                List<PolyChart> aCharts = getCharts(allCharts());
                for (PolyChart aChart : aCharts) {
                    update(aChart, newValue.doubleValue());
                }
                refreshCharts();
            }
        }
    }

    public class StackXSliderListener extends ChartSliderListener {
        void update(PolyChart aChart, double value) {
            aChart.getChartProperties().setStackX(value);
        }
    }

    public class StackYSliderListener extends ChartSliderListener {
        void update(PolyChart aChart, double value) {
            aChart.getChartProperties().setStackY(value);
        }
    }

    void setChartSlider(ChartSliderListener sliderListener, Slider slider, double value) {
        if (!slider.isValueChanging()) {
            sliderListener.active = false;
            slider.setValue(value);
            sliderListener.active = true;
        }
    }

    void setStackXSlider() {
        setChartSlider(stackXListener, stackXSlider, chart.getChartProperties().getStackX());
    }

    void setStackYSlider() {
        setChartSlider(stackYListener, stackYSlider, chart.getChartProperties().getStackY());
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
        if (!slider.isValueChanging()) {
            sliderListener.active = false;
            slider.setMin(min);
            slider.setMax(max);
            slider.setBlockIncrement(incrValue);
            slider.setValue(value);
            sliderListener.active = true;
        }
    }

    void setLvlSlider() {
        List<DatasetAttributes> dataAttrs = getDatasetAttributes();
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
        List<DatasetAttributes> dataAttrs = getDatasetAttributes();
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
        List<DatasetAttributes> dataAttrs = getDatasetAttributes();
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
        List<DatasetAttributes> dataAttrs = getDatasetAttributes();
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
        List<DatasetAttributes> dataAttrs = getDatasetAttributes();
        if (!dataAttrs.isEmpty()) {
            DatasetAttributes dataAttr = dataAttrs.get(0);
            double value = posMode ? dataAttr.getPosWidth() : dataAttr.getNegWidth();
            double min = 0.1;
            double max = 3.0;
            double incrValue = 0.1;
            if (posMode) {
                setSlider(posWidthSliderListener, posWidthSlider, min, max, incrValue, value);
                setSlider(posWidthSliderListener, posWidthSlider1D, min, max, incrValue, value);
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
        List<DatasetAttributes> dataAttrs = getDatasetAttributes();
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
        List<DatasetAttributes> dataAttrs = getDatasetAttributes();
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
    class PeakNPlanesListener extends PeakTypeListener<Integer> {
        void update(PeakListAttributes peakListAttr, Integer value) {
            peakListAttr.setNplanes(value);
        }
    }

    void setPeakDisplayComboBoxes() {
        List<PeakListAttributes> peakListAttrs = chart.getPeakListAttributes();
        if (!peakListAttrs.isEmpty()) {
            PeakListAttributes peakListAttr = peakListAttrs.get(0);
            PeakTypeListener[] listeners = {peakDisplayTypeListener, peakLabelTypeListener, peakColorTypeListener, peakNPlanesListener};
            for (var listener : listeners) {
                listener.active = false;
                if (listener instanceof PeakColorTypeListener) {
                    peakColorModeComboBox.setValue(peakListAttr.getColorType());
                } else if (listener instanceof PeakDisplayTypeListener) {
                    peakDisplayModeComboBox.setValue(peakListAttr.getDisplayType());
                } else if (listener instanceof PeakLabelTypeListener) {
                    peakLabelModeComboBox.setValue(peakListAttr.getLabelType());
                } else if (listener instanceof PeakNPlanesListener) {
                    peakNPlanesComboBox.setValue(peakListAttr.getNplanes());
                }
                listener.active = true;
            }
        }
    }

    public void setChart(PolyChart chart) {
        this.chart = chart;
        setAttributeControls();
    }

    private void setContourSliders() {
        setLvlSlider();
        setNlvlSlider();
        setClmSliderValue();
        setOffsetsSlider();
        setPosWidthSlider(true);
        setPosWidthSlider(false);
    }

    private void setTraceSliders() {
        setOffsetsSlider();
        setStackYSlider();
        setStackXSlider();
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

    public void setAttributeControls() {
        if ((chart != null) && getPane().isVisible()) {
            chart = fxmlController.getActiveChart();
            chart.setChartDisabled(true);
            datasetsChanged();
            peakListsChanged();

            setDatasetControls();
            setPeakControls();

            bindToChart(chart);
            setLimits();
            setDimControls();
            chart.setChartDisabled(false);
        }
    }

    public void updateDatasetAttributeControls() {
        setDatasetControls();
    }

    private void setDatasetControls() {
        setContourSliders();
        setContourColorControls(true);
        setContourColorControls(false);
        setDrawOnControls(true);
        setDrawOnControls(false);
        setTraceSliders();
    }

    private void setPeakControls() {
        setPeakCheckBoxes();
        setPeakColorControls(true);
        setPeakColorControls(false);
        setPeakDisplayComboBoxes();
    }

    @FXML
    private void sliceAction() {
        getCharts(allCharts()).forEach(aChart -> {
            aChart.getSliceAttributes().setSlice1Color(slice1ColorPicker.getValue());
            aChart.getSliceAttributes().setSlice2Color(slice2ColorPicker.getValue());
            aChart.getCrossHairs().refresh();
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
            if (!getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = getDatasetAttributes().get(0);
                Color posColor = dataAttr.getPosColor();
                if ((posColor != null) && (color != null)) {
                    double diff = Math.abs(posColor.getRed() - color.getRed());
                    diff += Math.abs(posColor.getGreen() - color.getGreen());
                    diff += Math.abs(posColor.getBlue() - color.getBlue());
                    if (diff < 0.05) {
                        dataAttr.setPosColor(toBlackOrWhite(color));
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
        for (var aChart : getCharts(allCharts())) {
            if (bgColorCheckBox.isSelected()) {
                aChart.getChartProperties().setBgColor(bgColorPicker.getValue());
            } else {
                aChart.getChartProperties().setBgColor(null);
            }
            if (axisColorCheckBox.isSelected()) {
                aChart.getChartProperties().setAxesColor(axisColorPicker.getValue());
            } else {
                aChart.getChartProperties().setAxesColor(null);
            }
            if (cross0ColorCheckBox.isSelected()) {
                aChart.getChartProperties().setCross0Color(cross0ColorPicker.getValue());
            } else {
                aChart.getChartProperties().setCross0Color(null);
            }
            if (cross1ColorCheckBox.isSelected()) {
                aChart.getChartProperties().setCross1Color(cross1ColorPicker.getValue());
            } else {
                aChart.getChartProperties().setCross1Color(null);
            }
        }
    }

    void setIntegralSliderText() {
        integralLowValue.setText(String.format("%.2f", integralPosSlider.getLowValue()));
        integralHighValue.setText(String.format("%.2f", integralPosSlider.getHighValue()));
        refreshLater();
    }

    boolean allCharts() {
        return itemChoiceState.getValue() == SelectionChoice.WINDOW;
    }

    List<PolyChart> getCharts(boolean all) {
        if (all) {
            return chart.getFXMLController().getCharts();
        } else {
            return Collections.singletonList(chart);
        }
    }

    void updateAspectRatio() {
        List<PolyChart> applyCharts = getCharts(allCharts());
        for (PolyChart applyChart : applyCharts) {
            applyChart.getChartProperties().setAspect(aspectCheckBox.isSelected());
            double aspectRatio = aspectSlider.getValue();
            applyChart.getChartProperties().setAspectRatio(aspectRatio);
            aspectRatioValue.setText(String.format("%.2f", aspectRatio));
            applyChart.refresh();
        }
    }

    private void refreshSlices(PolyChart aChart) {
        aChart.getCrossHairs().refresh();
    }

    private void updateCharts() {
        PauseTransition wait = new PauseTransition(Duration.millis(5.0));
        wait.setOnFinished(e -> Fx.runOnFxThread(this::updateChartsNow));
        wait.play();
    }

    private void updateChartsNow() {
        for (var aChart : getCharts(allCharts())) {
            if (aChart != chart) {
                chart.getChartProperties().copyTo(aChart);
            }
        }
    }

    private void updateSlicesAndRefresh() {
        PauseTransition wait = new PauseTransition(Duration.millis(5.0));
        wait.setOnFinished(e -> Fx.runOnFxThread(this::updateSlicesAndRefreshNow));
        wait.play();
    }

    private void updateSlicesAndRefreshNow() {
        for (var aChart : getCharts(allCharts())) {
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
            wait.setOnFinished(e -> Fx.runOnFxThread(aChart::refresh));
            wait.play();
        }
    }
}
