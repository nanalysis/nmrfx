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

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.nmrfx.processor.datasets.RegionData;

/**
 *
 * @author johnsonb
 */
public class AnalyzerController implements Initializable {

    Stage stage = null;
    @FXML
    Button closeButton;

    @FXML
    GridPane gridPane;

    private AutoComplete autoComplete;

    private final Map<String, TextField> fieldMap = new HashMap<>();

    /*
    protected int npoints = 0;
    protected double value = 0.0;
    protected double center = 0.0;
    protected double jitter = 0.0;
    protected double volume_e = 0.0;
    protected double volume_r = 0.0;
    protected double i_max = Double.NEGATIVE_INFINITY;
    protected double i_min = Double.MAX_VALUE;
    protected double i_extreme = 0.0;
    protected double s = 0.0;
    protected double volume_t = 0.0;
    protected double svar = 0.0;
    protected double rms = 0.0;
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        int iCol = 0;
        int iRow = 0;
        String[] fields = RegionData.getFields();
        for (String field : fields) {
            Label label = new Label(field);
            TextField textField = new TextField();
            fieldMap.put(field, textField);
            gridPane.add(label, iCol, iRow);
            gridPane.add(textField, iCol + 1, iRow);
            iRow++;
            if (iRow > 4) {
                iRow = 0;
                iCol += 2;
            }
        }

    }

    public void load() throws IOException {
        if (stage != null) {
            stage.show();
            stage.toFront();
        } else {
            MainApp.analyzerController = this;

            Parent root = FXMLLoader.load(DocWindowController.class.getResource("/fxml/AnalyzerScene.fxml"));

            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/Styles.css");

            stage = new Stage();

            stage.setTitle("Analyzer");
            stage.setScene(scene);
            stage.show();
            stage.toFront();
        }
    }

    @FXML
    private void closeAction(ActionEvent event) {
        stage.close();
    }

    @FXML
    private void analyze(ActionEvent event) {
        Optional<RegionData> result = PolyChart.getActiveChart().analyzeFirst();
        String[] fields = RegionData.getFields();
        if (result.isPresent()) {
            RegionData rData = result.get();
            for (String field : fields) {
                String sValue;
                TextField textField = fieldMap.get(field);
                switch (field) {
                    case "N":
                        {
                            int value = rData.getNpoints();
                            sValue = String.valueOf(value);
                            break;
                        }
                    case "MaxPt":
                        {
                            int[] value = rData.getMaxPoint();
                            StringBuilder sBuilder = new StringBuilder();
                            for (int val : value) {
                                sBuilder.append(val);
                                sBuilder.append(" ");
                            }       sValue = sBuilder.toString().trim();
                            break;
                        }
                    default:
                        {
                            double value = rData.getValue(field);
                            sValue = String.format("%f", value);
                            break;
                        }
                }
                textField.setText(sValue);
            }
        }
    }
}
