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

import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import org.controlsfx.control.ListSelectionView;
import org.controlsfx.control.SegmentedButton;
import org.nmrfx.processor.dataops.Align;
import org.nmrfx.processor.dataops.Normalize;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart.DISDIM;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import org.nmrfx.processor.gui.spectra.NMRAxis;
/**
 *
 * @author johnsonb
 */
public class MinerController implements Initializable {

    static final DecimalFormat formatter = new DecimalFormat();

    private Stage stage;
    @FXML
    private BorderPane attrBorderPane;
    @FXML
    private ToolBar viewToolBar;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView<DatasetAttributes> datasetTableView;
    @FXML
    private TableView<PeakListAttributes> peakListTableView;
    @FXML
    private GridPane viewGrid;
    @FXML
    private ComboBox<DISDIM> disDimCombo;
    @FXML
    private Tab datasetTab;

    StringProperty[][] limitFields;
    @FXML
    Slider scaleSlider;

    PolyChart chart;
    @FXML
    private CheckBox offsetTrackingCheckBox;
    @FXML
    private ColorPicker sliceColorPicker;
    @FXML
    Slider xOffsetSlider;
    @FXML
    Slider yOffsetSlider;
    @FXML
    private CheckBox peakStatusCheckBox;
    SegmentedButton groupButton;
    private ComboBox[] dimCombos;
    Label[] axisLabels;
    Label[] dimLabels;
    ListSelectionView<String> datasetView;
    static String[] rowNames = {"X", "Y", "Z", "A"};

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
    }

    public Stage getStage() {
        return stage;
    }

    public static MinerController create() {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/MinerScene.fxml"));
        MinerController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<MinerController>getController();
            controller.stage = stage;
            stage.setTitle("dataChord Miner Tools");
            stage.setAlwaysOnTop(true);
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    void initToolBar() {
        String iconSize = "16px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.REFRESH, "Refresh", iconSize, fontSize, ContentDisplay.TOP);
        //bButton.setOnAction(e -> refreshAction());
        buttons.add(bButton);
        for (Button button : buttons) {
            button.getStyleClass().add("toolButton");
        }
        toolBar.getItems().addAll(buttons);
    }

    @FXML
    public void alignToMax(ActionEvent event) {
        PolyChart polyChart = PolyChart.activeChart;
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            try {
                aligner.alignByMaxStream(dataset, 0, pt1, pt2);
                polyChart.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    @FXML
    public void reorderByCorr(ActionEvent event) {
        PolyChart polyChart = PolyChart.activeChart;
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            try {
                aligner.sortByCorr(dataset, -1, pt1, pt2);
                polyChart.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @FXML
    public void alignByCov(ActionEvent event) {
        PolyChart polyChart = PolyChart.activeChart;
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Align aligner = new Align();
            int sectionLength = 0;
            int pStart = pt1;
            int iWarp = 0;
            int tStart = 0;

            try {
                aligner.alignByCovStream(dataset, pt1, pt2, pStart, sectionLength, iWarp, tStart);
                polyChart.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    @FXML
    public void normalizeByMedian(ActionEvent event) {
        normalize("Median");
    }

    @FXML
    public void normalizeBySum(ActionEvent event) {
        normalize("Sum");
    }

    @FXML
    public void normalizeByMax(ActionEvent event) {
        normalize("Max");
    }

    public void normalize(String mode) {
        PolyChart polyChart = PolyChart.activeChart;
        Dataset dataset = polyChart.getDataset();
        if (dataset != null) {
            double[] ppms = polyChart.getVerticalCrosshairPositions();
            DatasetAttributes dataAttr = (DatasetAttributes) polyChart.getDatasetAttributes().get(0);
            NMRAxis axis = (NMRAxis) polyChart.getXAxis();
            AXMODE axMode = polyChart.getAxMode(0);
            int ptw1 = axMode.getIndex(dataAttr, 0, axis.getLowerBound());
            int ptw2 = axMode.getIndex(dataAttr, 0, axis.getUpperBound());
            int pt1 = axMode.getIndex(dataAttr, 0, ppms[0]);
            int pt2 = axMode.getIndex(dataAttr, 0, ppms[1]);
            Normalize normalizer = new Normalize();
            try {
                normalizer.normalizeByStream(dataset, 0, pt1, pt2, mode);
                polyChart.refresh();
            } catch (IOException ex) {
                Logger.getLogger(MinerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}
