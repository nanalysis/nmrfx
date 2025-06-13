/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.utils;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.FormatStringConverter;
import javafx.util.converter.IntegerStringConverter;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.function.UnaryOperator;

/**
 * @author brucejohnson
 */
//TODO add annotations once core and utils are merged
// @PluginAPI({"parametric", "ring"})
public class GUIUtils {
    public enum AlertRespones {
        YES,
        NO,
        CANCEL,
        DELETE,
        APPEND;
    }
    static final Background ERROR_BACKGROUND = new Background(new BackgroundFill(Color.YELLOW, null, null));
    static final Background DEFAULT_BACKGROUND = new Background(new BackgroundFill(Color.WHITE, null, null));
    public static class FixedDecimalFilter implements UnaryOperator<TextFormatter.Change> {

        @Override
        public TextFormatter.Change apply(TextFormatter.Change change) {
            if (change.getControlNewText().matches("-?([0-9]+)?(\\.[0-9]*)?")) {
                return change;
            }
            return null;
        }
    }
    public static class FixedDecimalConverter extends DoubleStringConverter {

        private final int decimalPlaces;

        public FixedDecimalConverter(int decimalPlaces) {
            this.decimalPlaces = decimalPlaces;
        }

        @Override
        public String toString(Double value) {
            return String.format("%." + decimalPlaces + "f", value);
        }

        @Override
        public Double fromString(String valueString) {
            if (valueString.isEmpty()) {
                return 0d;
            }
            return super.fromString(valueString);
        }
    }

    private GUIUtils() {
    }

    public static Background getErrorBackground() {
        return ERROR_BACKGROUND;
    }

    public static Background getDefaultBackground() {
        return DEFAULT_BACKGROUND;
    }

    public static boolean affirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.YES);
        boolean result = false;
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && (response.get() == ButtonType.YES)) {
            result = true;
        }
        return result;
    }
    public static void acknowledge(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.showAndWait();
    }

    public static AlertRespones deleteAppendCancel(String message) {
        ButtonType deleteType = new ButtonType("Delete");
        ButtonType appendType = new ButtonType("Append");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, deleteType, appendType);
        AlertRespones result = AlertRespones.CANCEL;
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && (response.get() == deleteType)) {
            result = AlertRespones.DELETE;
        } else if (response.isPresent() && (response.get() == appendType)) {
            result = AlertRespones.APPEND;
        }
        return result;
    }

    public static void warn(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static Object choice(Collection choices, String message) {
        ChoiceDialog choiceDialog = new ChoiceDialog(null, choices);
        choiceDialog.setHeaderText(message);
        choiceDialog.setContentText("Value:");
        Optional result = choiceDialog.showAndWait();
        return result.orElse(null);

    }

    public static String input(String message) {
        return input(message, "");
    }

    public static String input(String message, String defaultValue) {
        TextInputDialog textDialog = new TextInputDialog(defaultValue);
        textDialog.setHeaderText(message);
        textDialog.setContentText("Value:");
        Optional<String> result = textDialog.showAndWait();
        return result.orElse("");
    }

    public static double getTextWidth(String s, Font font) {
        Text text = new Text(s);
        text.setFont(font);
        return text.getLayoutBounds().getWidth();
    }

    /**
     * Splits the provided string segment into a list of strings where each string is shorter than the region length.
     * The segment string is split by words unless the region width is smaller than the width of the longest word in the
     * segment. In that case the segment string will be split by character.
     * If the regionWidth is less than the average character list, the segment string will be returned as a list of
     * strings containing a single character only.
     *
     * @param regionWidth The width of the display region.
     * @param segment     The string segment to be displayed.
     * @param font        The font being used.
     * @return A list of strings that fit within the region width.
     */
    public static List<String> splitToWidth(double regionWidth, String segment, Font font) {
        double width = GUIUtils.getTextWidth(segment, font);

        double charWidth = width / segment.length();
        int start = 0;
        int end;
        boolean splitByWord = true;
        List<String> result = new ArrayList<>();
        Double longestWord = Arrays.stream(segment.split(" "))
                .map(word -> GUIUtils.getTextWidth(word, font))
                .max(Comparator.naturalOrder()).orElse(0.0);
        // If the region width is smaller than the width of the longest word, split by character instead.
        if (regionWidth < longestWord) {
            splitByWord = false;
        }
        do {
            // Get initial estimate for index close to region width
            end = start + (int) (regionWidth / charWidth);
            if (end > segment.length()) {
                end = segment.length();
            } else if (end <= 0) {
                // If region is so small that it has less than one character in it, just return a list of strings of
                // 1 character each
                return List.of(segment.split(""));
            }

            if (splitByWord) {
                end = GUIUtils.getEndingIndexByWord(segment, start, end, regionWidth, font);
            } else {
                end = GUIUtils.getEndingIndexByCharacter(segment, start, end, regionWidth, charWidth, font);
            }

            String subStr = segment.substring(start, end);
            result.add(subStr);
            start = end;
        } while (start < segment.length());
        return result;
    }

    /**
     * Splits the portion of the fullString provided between start and end indexes by word. The initial end index is
     * an estimate and will be adjusted to fit within the region width without splitting a word.
     *
     * @param fullString  The string to be split.
     * @param start       The starting index to look at.
     * @param end         The ending index to look at.
     * @param regionWidth The width of the display region
     * @param font        The font to use.
     * @return The adjusted end index value for the appropriate width of substring.
     */
    private static int getEndingIndexByWord(String fullString, int start, int end, double regionWidth, Font font) {
        // adjust end to last word
        int indexEndWord = fullString.indexOf(" ", end);
        if (indexEndWord != -1) {
            end = indexEndWord;
        } else {
            end = fullString.length();
        }

        double testWidth = GUIUtils.getTextWidth(fullString.substring(start, end), font);
        while (testWidth > regionWidth) {
            int indexPrevWord = fullString.substring(start, end).lastIndexOf(" ") + start;
            if (indexPrevWord <= start) {
                break;
            }
            end = indexPrevWord;
            testWidth = GUIUtils.getTextWidth(fullString.substring(start, end), font);
        }
        return end;
    }

    /**
     * Splits the portion of the fullString provided between start and end indexes by character. The initial end index is
     * an estimate and will be adjusted to fit within the region width.
     *
     * @param fullString  The string to be split.
     * @param start       The starting index to look at.
     * @param end         The ending index to look at.
     * @param regionWidth The width of the display region
     * @param charWidth   The average width of a char in the fullString.
     * @param font        The font to use.
     * @return The adjusted end index value for the appropriate width of substring.
     */
    private static int getEndingIndexByCharacter(String fullString, int start, int end, double regionWidth, double charWidth, Font font) {
        double testWidth = GUIUtils.getTextWidth(fullString.substring(start, end), font);
        while (testWidth > regionWidth) {
            if (end < 2) {
                break;
            }
            end--;

            testWidth = GUIUtils.getTextWidth(fullString.substring(start, end), font);
        }
        while (testWidth < regionWidth - charWidth) {
            end++;
            if (end > fullString.length()) {
                end = fullString.length();
                break;
            }
            testWidth = GUIUtils.getTextWidth(fullString.substring(start, end), font);
        }
        return end;
    }

    /**
     * Utility function to adjust the height of toolbars used in ControllerTools. The height of each toolbar is adjusted
     * to the largest prefHeight. The height of all the toolbar items are adjusted to the prefHeight of the largest
     * item in the first toolbar in the list.
     *
     * @param toolBarList A list of toolbars to adjust
     */
    public static void toolbarAdjustHeights(List<ToolBar> toolBarList) {
        if (toolBarList.isEmpty()) {
            return;
        }
        // Set height of all toolbars to be the same
        double heightToolBar = Collections.max(toolBarList.stream().map(node -> node.prefHeight(Region.USE_COMPUTED_SIZE)).toList());
        for (ToolBar toolBar : toolBarList) {
            toolBar.setPrefHeight(heightToolBar);
        }

        List<Node> toolBarsItems = new ArrayList<>();
        for (ToolBar toolBar : toolBarList) {
            toolBarsItems.addAll(toolBar.getItems());
        }
        nodeAdjustHeights(toolBarsItems);
    }

    public static void nodeAdjustHeights(List<Node> nodeList) {
        // Set height of controls within a toolbar to be the same.
        Optional<Double> height = nodeList.stream().map(node -> node.prefHeight(Region.USE_COMPUTED_SIZE)).max(Double::compare);
        if (height.isEmpty()) {
            return;
        }
        for (Node node : nodeList) {
            if (node instanceof Control control) {
                control.setPrefHeight(height.get());
            }
        }
    }

    public static void nodeAdjustWidths(List<Node> nodeList) {
        // Set width of controls within a toolbar to be the same.
        Optional<Double> width = nodeList.stream().map(node -> node.prefWidth(Region.USE_COMPUTED_SIZE)).max(Double::compare);
        if (width.isEmpty()) {
            return;
        }
        for (Node node : nodeList) {
            if (node instanceof Control control) {
                control.setPrefWidth(width.get());
            }
        }
    }

    public static String getPassword() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Password");
        dialog.setHeaderText("");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        PasswordField pwd = new PasswordField();
        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(10);
        content.getChildren().addAll(new Label("Password:"), pwd);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return pwd.getText();
            }
            return null;
        });
        String pw = null;
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            pw = result.get();
        }
        return pw;
    }

    public static void snapNode(Node node, File file) throws IOException {
        double scale = 4.0;
        final Bounds bounds = node.getLayoutBounds();
        final WritableImage image = new WritableImage(
                (int) Math.round(bounds.getWidth() * scale),
                (int) Math.round(bounds.getHeight() * scale));
        final SnapshotParameters spa = new SnapshotParameters();
        spa.setTransform(javafx.scene.transform.Transform.scale(scale, scale));
        node.snapshot(spa, image);
        javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
    }

    public static void bindSliderField(Slider slider, TextField field) {
        DecimalFormat numberFormat = new DecimalFormat();
        numberFormat.setMaximumFractionDigits(2);
        FormatStringConverter<Number> converter = new FormatStringConverter<>(numberFormat);
        TextFormatter<Number> formatter = new TextFormatter<>(converter, 0);
        field.setTextFormatter(formatter);
        slider.valueProperty().bindBidirectional(formatter.valueProperty());
    }

    public static void bindSliderField(Slider slider, TextField field, String pattern) {
        DecimalFormat numberFormat = new DecimalFormat(pattern);
        FormatStringConverter<Number> converter = new FormatStringConverter<>(numberFormat);
        TextFormatter<Number> formatter = new TextFormatter<>(converter, 0);
        field.setTextFormatter(formatter);
        slider.valueProperty().bindBidirectional(formatter.valueProperty());
    }
    public static void bindSliderField(Slider slider, TextField field, String pattern, double range) {
        DecimalFormat numberFormat = new DecimalFormat(pattern);
        FormatStringConverter<Number> converter = new FormatStringConverter<>(numberFormat);
        TextFormatter<Number> formatter = new TextFormatter<>(converter, 0);
        field.setTextFormatter(formatter);
        formatter.valueProperty().addListener(e -> resetRange(formatter, slider, range));
        slider.valueProperty().bindBidirectional(formatter.valueProperty());
    }

    public record SliderText(Slider slider, TextField textField, HBox hBox) {}

    public static SliderText sliderWithText(double min, double max, double value, String pattern) {
        HBox hBox = new HBox();
        Slider slider = new Slider(min, max, value);
        slider.setShowTickLabels(true);
        TextField textField = new TextField();
        textField.setPrefWidth(50);
        hBox.getChildren().addAll(slider, textField);
        bindSliderField(slider, textField,pattern);
        slider.setValue(value);
        return new SliderText(slider, textField, hBox);
    }

    private static void resetRange(TextFormatter<Number> formatter, Slider slider, double range) {
        double formatterValue = formatter.getValue().doubleValue();
        double sliderValue = slider.getValue();
        double delta = Math.abs(formatterValue - sliderValue);
        if (delta > 1.0) {
            formatterValue = Math.round(formatterValue * 10) / 10.0;
            double halfRange = range / 2.0;
            double start = halfRange * Math.round(formatterValue / halfRange) - 2.0 * halfRange;
            double end = start + 4 * halfRange;
            slider.setMin(start);
            slider.setMax(end);
        }
        slider.setValue(formatterValue);
    }

    public static TextField getDoubleTextField(SimpleDoubleProperty prop) {
        return getDoubleTextField(prop, 2);
    }
    public static TextField getDoubleTextField(SimpleDoubleProperty prop, int decimalPlaces) {
        TextField textField = new TextField();
        TextFormatter<Double> textFormatter = new TextFormatter<>(new FixedDecimalConverter(decimalPlaces), 0.0, new FixedDecimalFilter());
        textFormatter.valueProperty().bindBidirectional((Property) prop);
        textField.setTextFormatter(textFormatter);
        return textField;
    }
    public static TextField getIntegerTextField(SimpleIntegerProperty prop) {
        TextField textField = new TextField();
        TextFormatter<Integer> textFormatter = new TextFormatter<>(new IntegerStringConverter());
        textFormatter.valueProperty().bindBidirectional((Property) prop);
        textField.setTextFormatter(textFormatter);
        return textField;
    }
    public static Color getColor(String colorString) {
        Color color = null;
        if (colorString != null && !colorString.isBlank()) {
            try {
                color = Color.web(colorString);
            } catch(Exception e) {
                color = Color.web("black");
            }
        }
        return color;

    }

    public record SliderRange(double min, double value, double max, double incrValue) {}

    public static Optional<Double> getSliderValue(String name, double x, double y, SliderRange sliderRange,  DoubleConsumer applyValue) {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setX(x);
        dialog.setY(y);
        dialog.setTitle(name);
        dialog.setHeaderText("");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        Slider slider = new Slider(sliderRange.min, sliderRange.max, sliderRange.value);
        slider.setBlockIncrement(sliderRange.incrValue);
        Label valueLabel = new Label();
        slider.setMinWidth(200);
        valueLabel.setMinWidth(80);
        int nDigits = Math.max(1, (int) Math.ceil(Math.log10(1.0/sliderRange.max))) + 1;
        String format = "%." + nDigits + "f";
        valueLabel.setText(String.format(format, sliderRange.value));

        if (applyValue != null) {
            slider.valueProperty().addListener((a,b,c) -> applyValue.accept(c.doubleValue()));
        }
        slider.valueProperty().addListener((a,b,c) -> {
            valueLabel.setText(String.format(format, c));
        });

        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(10);
        content.getChildren().addAll(new Label("Value:"), slider, valueLabel);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.APPLY) {
                return slider.getValue();
            }
            return null;
        });
        return dialog.showAndWait();
    }
}
