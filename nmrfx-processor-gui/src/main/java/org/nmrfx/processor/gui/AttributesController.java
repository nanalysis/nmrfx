package org.nmrfx.processor.gui;

import javafx.animation.PauseTransition;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ColorSchemes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AttributesController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AttributesController.class);
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
    private CheckBox slice1StateCheckBox;
    @FXML
    private CheckBox slice2StateCheckBox;
    @FXML
    private ColorPicker slice1ColorPicker;
    @FXML
    private ColorPicker slice2ColorPicker;



    FXMLController fxmlController;
    Pane pane;

    public static AttributesController create(FXMLController fxmlController, Pane processorPane) {
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/AttributesController.fxml"));
        final AttributesController controller;
        try {
            Pane pane = loader.load();
            processorPane.getChildren().add(pane);

            controller = loader.getController();
            controller.fxmlController = fxmlController;
            controller.pane = pane;
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
        ticFontSizeComboBox.valueProperty().addListener(e -> refreshLater());
        labelFontSizeComboBox.valueProperty().addListener(e -> refreshLater());
        titlesCheckBox.selectedProperty().addListener(e -> refreshLater());
        parametersCheckBox.selectedProperty().addListener(e -> refreshLater());
        intensityAxisCheckBox.selectedProperty().addListener(e -> refreshLater());
        leftBorderSizeComboBox.valueProperty().addListener(e -> refreshLater());
        rightBorderSizeComboBox.valueProperty().addListener(e -> refreshLater());
        topBorderSizeComboBox.valueProperty().addListener(e -> refreshLater());
        bottomBorderSizeComboBox.valueProperty().addListener(e -> refreshLater());
//        integralPosSlider.lowValueProperty().addListener(e -> updateIntegralState());
//        integralPosSlider.highValueProperty().addListener(e -> updateIntegralState());
//        regionCheckBox.selectedProperty().addListener(e -> refreshLater());
//        integralCheckBox.selectedProperty().addListener(e -> refreshLater());
//        gridCheckBox.selectedProperty().addListener(e -> refreshLater());
//        offsetTrackingCheckBox.selectedProperty().addListener(e -> refreshLater());
//        useDatasetColorCheckBox.selectedProperty().addListener(e -> refreshLater());
//        slice1StateCheckBox.selectedProperty().addListener(e -> refreshLater());
//        slice2StateCheckBox.selectedProperty().addListener(e -> refreshLater());
//        xOffsetSlider.valueProperty().addListener(e -> refreshLater());
//        yOffsetSlider.valueProperty().addListener(e -> refreshLater());
//        scaleSlider.valueProperty().addListener(e -> refreshLater());
//        slice1ColorPicker.valueProperty().addListener(e -> refreshLater());

    }

    @FXML
    private void sliceAction(Event event) {
        PolyChart chart = fxmlController.getActiveChart();
        chart.sliceAttributes.setSlice1Color(slice1ColorPicker.getValue());
        chart.sliceAttributes.setSlice2Color(slice2ColorPicker.getValue());
        chart.getCrossHairs().refreshCrossHairs();
    }

    @FXML
    private void showPosSchema() {
        setPosColorsToSchema();
    }

    void setPosColorsToSchema() {
        Stage stage = fxmlController.getStage();
        double x = stage.getX();
        double y = stage.getY() + stage.getHeight() + 10;
        ColorSchemes.showSchemaChooser(this::updatePosColorsWithSchema, x, y);
    }

    void setNegColorsToSchema() {
        Stage stage = fxmlController.getStage();
        double x = stage.getX();
        double y = stage.getY() + stage.getHeight() + 10;
        ColorSchemes.showSchemaChooser(this::updateNegColorsWithSchema, x, y);
    }

    public void updateNegColorsWithSchema(String colorName) {
        updateColorsWithSchema(colorName, false);

    }

    public void updatePosColorsWithSchema(String colorName) {
        updateColorsWithSchema(colorName, true);
    }

    public void updateColorsWithSchema(String colorName, boolean posColors) {
        PolyChart chart = fxmlController.getActiveChart();
        var items = chart.getDatasetAttributes();
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
       // datasetTableView.refresh();
        chart.refresh();
    }
    private void updateBGColor() {
        // check to see if the new background color is the same as the pos color
        // for first dataset.  If it is, change the color of the first dataset
        // so it is visible.
        PolyChart chart = fxmlController.getActiveChart();
        if (bgColorCheckBox.isSelected()) {
            Color color = bgColorPicker.getValue();
            if (!chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                Color posColor = dataAttr.getPosColor();
                if ((posColor != null) && (color != null)) {
                    double diff = Math.abs(posColor.getRed() - color.getRed());
                    diff += Math.abs(posColor.getGreen() - color.getGreen());
                    diff += Math.abs(posColor.getBlue() - color.getBlue());
                    System.out.println("color a " + diff);
                    if (diff < 0.05) {
                        dataAttr.setPosColor(PolyChart.chooseBlackWhite(color));
                    }
                }
            }
        }
        updateProps(chart);
        refreshLater();
    }

    @FXML
    private void updateProps(PolyChart chart) {
        if (bgColorCheckBox.isSelected()) {
            chart.chartProps.setBgColor(bgColorPicker.getValue());
        } else {
            chart.chartProps.setBgColor(null);
        }
        if (axisColorCheckBox.isSelected()) {
            chart.chartProps.setAxesColor(axisColorPicker.getValue());
        } else {
            chart.chartProps.setAxesColor(null);
        }
        if (cross0ColorCheckBox.isSelected()) {
            chart.chartProps.setCross0Color(cross0ColorPicker.getValue());
        } else {
            chart.chartProps.setCross0Color(null);
        }
        if (cross1ColorCheckBox.isSelected()) {
            chart.chartProps.setCross1Color(cross1ColorPicker.getValue());
        } else {
            chart.chartProps.setCross1Color(null);
        }
    }

    // add delay so bindings between properties and controsl activate before refresh
    private void refreshLater() {
        PolyChart chart = fxmlController.getActiveChart();
        if (!chart.isChartDisabled()) {
            PauseTransition wait = new PauseTransition(Duration.millis(50.0));
            wait.setOnFinished(e -> ConsoleUtil.runOnFxThread(chart::refresh));
            wait.play();
        }
    }

}
