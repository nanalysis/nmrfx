/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.utils;

import javafx.beans.property.*;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author brucejohnson
 */
public class ColorSchemes {
    private static final Map<String, Map<String, String>> maps = new HashMap<>();
    private static final DoubleProperty lowValue = new SimpleDoubleProperty(0);
    private static final DoubleProperty highValue = new SimpleDoubleProperty(100);
    private static final BooleanProperty reverse = new SimpleBooleanProperty(false);
    private static final StringProperty selectedColorClass = new SimpleStringProperty();
    private static Stage stage = null;
    private static GridPane gridPane = null;
    private static Consumer<String> consumer = null;

    public static void loadColors(String fileName) throws IOException {
        BufferedReader bf = null;
        if (fileName.startsWith("resource:")) {
            InputStream inputStream = ColorSchemes.class.getResourceAsStream(fileName.substring(9));
            if (inputStream == null) {
                System.out.println("color resource is null for " + fileName);
            } else {
                bf = new BufferedReader(new InputStreamReader(inputStream));
            }
        } else {
            bf = new BufferedReader(new FileReader(fileName));
        }
        if (bf == null) {
            System.out.println("bf null");
            return;
        }
        bf.lines().forEach(line -> {
            line = line.trim();
            if (line.length() != 0) {
                if (line.charAt(0) != '#') {
                    String[] fields = line.split(" +");
                    if (fields.length == 3) {
                        if (!maps.containsKey(fields[0])) {
                            maps.put(fields[0], new LinkedHashMap<>());
                        }
                        Map<String, String> map = maps.get(fields[0]);
                        map.put(fields[1], fields[2]);
                    }
                }
            }
        });
        bf.close();
    }

    public static Set<String> getColorClasses() {
        return maps.keySet();
    }

    public static String getColorString(String name) {
        String colorString = "";
        for (Map<String, String> map : maps.values()) {
            if (map.containsKey(name)) {
                colorString = map.get(name);
                break;
            }
        }
        return colorString;
    }

    public static String getColorClass(String name) {
        String colorClass = "";
        for (Map.Entry<String, Map<String, String>> entry : maps.entrySet()) {
            if (entry.getValue().containsKey(name)) {
                colorClass = entry.getKey();
                break;
            }
        }
        return colorClass;
    }

    public static List<Color> getColors(String name) {
        return getColors(name, 0);
    }

    public static List<Color> convertColorString(String colorString) {
        List<Color> colors = new ArrayList<>();
        if (!reverse.get()) {
            for (int i = 0; i < colorString.length(); i += 6) {
                String colorStr = colorString.substring(i, i + 6);
                Color color = Color.web(colorStr);
                colors.add(color);
            }
        } else {
            for (int i = colorString.length() - 6; i >= 0; i -= 6) {
                String colorStr = colorString.substring(i, i + 6);
                Color color = Color.web(colorStr);
                colors.add(color);
            }
        }
        return colors;
    }

    public static List<Color> getColors(String name, int nColors) {
        if (maps.isEmpty()) {
            try {
                loadColors("resource:/palettes.txt");
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            }
        }
        String colorString = getColorString(name);
        List<Color> colors;
        if (!colorString.equals("")) {
            colors = convertColorString(colorString);
            int nClassColors = colors.size();
            if (nColors == 0) {
                nColors = nClassColors;
            }
            String colorClass = getColorClass(name);
            if (colorClass.equals("categorical")) {
                if (nColors != nClassColors) {
                    List<Color> colors2 = new ArrayList<>();
                    for (int i = 0; i < nColors; i++) {
                        colors2.add(colors.get(i % nClassColors));
                    }
                    colors = colors2;
                }
            } else {
                if (true || (nColors != nClassColors)) {
                    List<Color> colors2 = new ArrayList<>();
                    for (int i = 0; i < nColors; i++) {
                        double f1 = (double) i / (nColors - 1);
                        double fStart = lowValue.get() / 100.0;
                        double fEnd = highValue.get() / 100.0;
                        double fDelta = (fEnd - fStart);
                        double dIndex = ((f1 * fDelta) + fStart) * (colors.size() - 1);
                        int i1 = (int) Math.floor(dIndex);
                        int i2 = (int) Math.ceil(dIndex);
                        double f2 = dIndex - i1;
                        Color color1 = colors.get(i1);
                        Color color2 = colors.get(i2);
                        Color color = color1.interpolate(color2, f2);
                        colors2.add(color);
                    }
                    colors = colors2;
                }
            }
        } else {
            colors = Collections.EMPTY_LIST;
        }
        return colors;
    }

    static void updateColors(double gridWidth, String className) {
        gridPane.getChildren().clear();
        if (maps.containsKey(className)) {
            Map<String, String> colorEntries = maps.get(className);
            int row = 0;
            Font font = Font.font(10);
            double buttonHeight = 23;
            for (String colorName : colorEntries.keySet()) {
                List<Color> colors = getColors(colorName);
                double width = gridWidth / colors.size() - 1;
                HBox hBox = new HBox();
                for (Color color : colors) {
                    Rectangle rect = new Rectangle(width, 15);
                    rect.setFill(color);
                    rect.setStroke(color);
                    hBox.getChildren().add(rect);
                }
                Button applyButton = new Button(colorName);
                applyButton.setPrefWidth(100.0);
                applyButton.setFont(font);
                applyButton.setOnAction(e -> apply(colorName));
                HBox.setHgrow(applyButton, Priority.ALWAYS);
                gridPane.add(applyButton, 0, row);
                gridPane.add(hBox, 1, row++);
            }
            stage.setHeight(buttonHeight * colorEntries.size() + 45);
        }
        stage.toFront();
    }

    static void apply(String colorName) {
        consumer.accept(colorName);

    }

    public static void showSchemaChooser(Consumer<String> newConsumer, double x, double y) {
        consumer = newConsumer;

        if (maps.isEmpty()) {
            try {
                loadColors("resource:/palettes.txt");
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            }
        }
        double buttonWidth = 120;
        double gridWidth = 400;
        String colorClassName = getColorClasses().stream().findAny().get();
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Color Schemes");

            BorderPane borderPane = new BorderPane();
            gridPane = new GridPane();
            gridPane.getColumnConstraints().add(new ColumnConstraints(buttonWidth)); // column 0 is 100 wide
            gridPane.getColumnConstraints().add(new ColumnConstraints(gridWidth)); // column 1 is 200 wide

            Scene scene = new Scene(borderPane);
            stage.setScene(scene);
            stage.setWidth(gridWidth + buttonWidth + 20);

            GridPane topGrid = new GridPane();
            topGrid.getColumnConstraints().add(new ColumnConstraints(buttonWidth)); // column 0 is 100 wide
            topGrid.getColumnConstraints().add(new ColumnConstraints(gridWidth)); // column 1 is 200 wide

            ChoiceBox<String> schemeTypeChoice = new ChoiceBox<>();
            for (String schemeName : getColorClasses()) {
                schemeTypeChoice.getItems().add(schemeName);
            }
            schemeTypeChoice.setValue(colorClassName);
            schemeTypeChoice.valueProperty().bindBidirectional(selectedColorClass);
            schemeTypeChoice.setValue(colorClassName);
            schemeTypeChoice.setOnAction(e -> updateColors(gridWidth, schemeTypeChoice.getValue()));

            RangeSlider slider = new RangeSlider(0.0, 100.0, 0.0, 100.0);
            slider.lowValueProperty().bindBidirectional(lowValue);
            slider.highValueProperty().bindBidirectional(highValue);
            CheckBox reverseBox = new CheckBox("Reverse");
            topGrid.add(schemeTypeChoice, 0, 0);
            topGrid.add(reverseBox, 0, 1);
            topGrid.add(slider, 1, 1);
            reverseBox.selectedProperty().bindBidirectional(reverse);
            reverseBox.setOnAction(e -> updateColors(gridWidth, selectedColorClass.get()));
            slider.setShowTickMarks(true);
            slider.setShowTickLabels(true);
            slider.setBlockIncrement(5);
            slider.setPrefWidth(gridWidth);
            slider.lowValueProperty().addListener(e -> updateColors(gridWidth, selectedColorClass.get()));
            slider.highValueProperty().addListener(e -> updateColors(gridWidth, selectedColorClass.get()));

            borderPane.setTop(topGrid);

            borderPane.setCenter(gridPane);
            stage.setOnCloseRequest(e -> {
                consumer = null;
            });
        }

        updateColors(gridWidth, selectedColorClass.get());
        stage.show();
        stage.setX(x);
        stage.setY(y);
    }
}
