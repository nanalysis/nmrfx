package org.nmrfx.processor.gui.utils;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Dimension2D;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.nmrfx.utils.GUIUtils;

import java.util.List;
import java.util.Optional;

public class OuputSizeDialog {
    static Double cmToIn = 1.0/ 2.54;

    public  record OutputDimensions(Double width, String widthUnits, Double height, String heightUnits) {}

    public static OutputDimensions getOutputDimensions(double chartWidth, double chartHeight, double dpi) {
        Dialog<OutputDimensions> dialog = new Dialog<>();
        dialog.setTitle("Grid");
        dialog.setHeaderText("Specify SVG dimensions");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        dialog.getDialogPane().setContent(grid);
        int comboBoxWidth = 80;

        ComboBox<String>  widthUnits = new ComboBox<>();
        widthUnits.getItems().addAll(List.of("px", "in", "cm"));
        widthUnits.setValue("px");
        widthUnits.setMinWidth(comboBoxWidth);
        widthUnits.setMaxWidth(comboBoxWidth);
        widthUnits.setEditable(false);
        grid.add(new Label("Width"), 0, 0);
        SimpleDoubleProperty widthProp = new SimpleDoubleProperty(chartWidth);
        var widthField = GUIUtils.getDoubleTextField(widthProp, 2);
        grid.add(widthField, 1, 0);
        grid.add(widthUnits, 2, 0);

        ComboBox<String>  heightUnits = new ComboBox<>();
        heightUnits.getItems().addAll(List.of("px", "in", "cm"));
        heightUnits.setValue("px");
        heightUnits.setMinWidth(comboBoxWidth);
        heightUnits.setMaxWidth(comboBoxWidth);
        heightUnits.setEditable(false);
        CheckBox linkCheckBox = new CheckBox("link");
        linkCheckBox.setSelected(true);
        Text lockIcon = GlyphsDude.createIcon(FontAwesomeIcon.LOCK,
                "16.0");
        linkCheckBox.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        linkCheckBox.setGraphic(lockIcon);

        grid.add(linkCheckBox, 0, 1);
        grid.add(new Label("Height"), 0, 2);
        SimpleDoubleProperty heightProp = new SimpleDoubleProperty(chartHeight);
        var heightField = GUIUtils.getDoubleTextField(heightProp, 2);
        grid.add(heightField, 1, 2);
        grid.add(heightUnits, 2, 2);
        double factor = chartHeight / chartWidth;

        widthProp.addListener((obs, oldVal, newVal) -> {
            if (linkCheckBox.isSelected()) {
                int widthPix = toPix(newVal.doubleValue(), widthUnits.getValue(), dpi);
                int targetHeightPix = (int) Math.round(widthPix * factor);
                int currentHeightPix = toPix(heightProp.getValue(), heightUnits.getValue(), dpi);
                if (currentHeightPix != targetHeightPix) {
                    heightProp.set(fromPix((double) targetHeightPix, heightUnits.getValue(), dpi));
                }
            }

        });

        heightProp.addListener((obs, oldVal, newVal) -> {
            if (linkCheckBox.isSelected()) {
                int heightPix = toPix(newVal.doubleValue(), heightUnits.getValue(), dpi);
                int targetWidthPix = (int) Math.round(heightPix / factor);
                int currentWidthPix = toPix(widthProp.getValue(), widthUnits.getValue(), dpi);
                if (currentWidthPix != targetWidthPix) {
                    widthProp.set(fromPix((double) targetWidthPix, widthUnits.getValue(), dpi));
                }
            }
        });
        heightUnits.valueProperty().addListener((obs, oldVal, newVal) -> updateValue(heightProp, oldVal, newVal, dpi));
        widthUnits.valueProperty().addListener((obs, oldVal, newVal) -> updateValue(widthProp, oldVal, newVal, dpi));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // The value set in the formatter may not have been set yet so commit the value before retrieving
                widthUnits.commitValue();
                heightUnits.commitValue();
                return new OutputDimensions(widthProp.getValue(), widthUnits.getValue(), heightProp.getValue(), heightUnits.getValue());
            }
            return null;
        });

        OutputDimensions gd = null;
        Optional<OutputDimensions> result = dialog.showAndWait();
        if (result.isPresent()) {
            gd = result.get();
        }
        return gd;
    }

    static Integer toPix(Double value, String units, double dpi) {
        int newValue;
        if (units.equals("in")) {
            newValue = (int) Math.round(value * dpi);
        } else if (units.equals("cm")) {
            newValue = (int) Math.round(value * cmToIn * dpi);
        } else {
            newValue = value.intValue();
        }
        return newValue;
    }
    static Double fromPix(Double value, String units, double dpi) {
        Double newValue;
        if (units.equals("in")) {
            newValue = value / dpi;
        } else if (units.equals("cm")) {
            newValue = (value / dpi) / cmToIn;
        } else {
            newValue = value;
        }
        return newValue;
    }

    static void updateValue(SimpleDoubleProperty prop, String oldUnits, String newUnits, double dpi) {
        Double pxValue = toPix(prop.getValue(), oldUnits, dpi).doubleValue();
        Double unitValuie = fromPix(pxValue, newUnits, dpi);
        prop.setValue(unitValuie);
    }

    public static Dimension2D getPDFDimensions(OutputDimensions outputDimensions) {
        Double width = (double) PDRectangle.LETTER.getWidth();
        Double height = (double) PDRectangle.LETTER.getHeight();
        if (outputDimensions != null) {
            width = switch (outputDimensions.widthUnits()) {
                case "in" -> outputDimensions.width() * 72;
                case "cm" -> outputDimensions.width() / 2.54 * 72;
                default -> outputDimensions.width();
            };
            height = switch (outputDimensions.heightUnits()) {
                case "in" -> outputDimensions.height() * 72;
                case "cm" -> outputDimensions.height() / 2.54 * 72;
                default -> outputDimensions.height();
            };
        }
        return new Dimension2D(width, height);
    }
}
