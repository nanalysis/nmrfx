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
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.RegionData;

/**
 *
 * @author brucejohnson
 */
public class AnalyzerBar {

    FXMLController controller;
    Consumer closeAction = null;
    GridPane gridPane;
    DatasetBase dataset;
    private final Map<String, TextField> fieldMap = new HashMap<>();
    Optional<RegionData> result = Optional.empty();
    String[] fieldNames = {"Max", "Min", "RVolume", "N", "Mean", "RMS", "S/N", "DNoise"};

    public AnalyzerBar(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public void buildBar(GridPane gridPane) {
        this.gridPane = gridPane;
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        closeButton.setOnAction(e -> close());
        gridPane.add(closeButton, 0, 0);
        Button analyzeButton = new Button("Analyze");
        analyzeButton.setOnAction(e -> analyze(e));
        analyzeButton.setPrefWidth(75.0);
        gridPane.add(analyzeButton, 1, 0);
        Button setRMSButton = new Button("Set RMS");
        setRMSButton.setOnAction(e -> setRMS(e));
        setRMSButton.setPrefWidth(75.0);
        gridPane.add(setRMSButton, 1, 1);
        int iCol = 2;
        int iRow = 0;
        for (String field : fieldNames) {
            Label label = new Label(field);
            label.setPrefWidth(75.0);
            label.setAlignment(Pos.CENTER_RIGHT);
            TextField textField = new TextField();
            textField.setPrefWidth(100.0);
            textField.setAlignment(Pos.CENTER_RIGHT);
            fieldMap.put(field, textField);
            gridPane.add(label, iCol, iRow);
            gridPane.add(textField, iCol + 1, iRow);
            iRow++;
            if (iRow > 1) {
                iRow = 0;
                iCol += 2;
            }
        }
    }

    public GridPane getToolBar() {
        return gridPane;
    }

    public void close() {
        closeAction.accept(this);
    }

    public AnalyzerBar onClose(Consumer closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    private void analyze(ActionEvent event) {
        PolyChart chart = controller.getActiveChart();
        dataset = chart.getDataset();
        result = chart.analyzeFirst();
        update();
    }

    private void update() {
        if (result.isPresent()) {
            RegionData rData = result.get();
            Double datasetNoise = dataset.getNoiseLevel();
            double scaleNoise = datasetNoise != null ? datasetNoise : rData.getRMS();
            int nDigits = 0;
            if (scaleNoise > 1.0) {
                nDigits = 0;
            } else {
                double logNoise = Math.floor(Math.log10(scaleNoise));
                nDigits = -(int) logNoise;
            }
            nDigits++;
            String format = "%." + nDigits + "f";
            for (String field : fieldNames) {
                String sValue;
                TextField textField = fieldMap.get(field);
                switch (field) {
                    case "DNoise": {
                        if (datasetNoise != null) {
                            sValue = String.format(format, datasetNoise);
                        } else {
                            sValue = "Not Set";
                        }
                        break;
                    }
                    case "S/N": {
                        double value = rData.getMax();
                        if (datasetNoise != null) {
                            sValue = String.format("%.1f", value / datasetNoise);
                        } else {
                            sValue = "Not Set";
                        }
                        break;
                    }
                    case "N": {
                        int value = rData.getNpoints();
                        sValue = String.valueOf(value);
                        break;
                    }
                    case "MaxPt": {
                        int[] value = rData.getMaxPoint();
                        StringBuilder sBuilder = new StringBuilder();
                        for (int val : value) {
                            sBuilder.append(val);
                            sBuilder.append(" ");
                        }
                        sValue = sBuilder.toString().trim();
                        break;
                    }
                    default: {
                        double value = rData.getValue(field);
                        sValue = String.format(format, value);
                        break;
                    }
                }
                textField.setText(sValue);
            }
        }
    }

    private void setRMS(ActionEvent event) {
        result.ifPresent(regionData -> {
            if (dataset != null) {
                dataset.setNoiseLevel(regionData.getRMS());
                update();
            }
        });
    }

}
