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

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import org.nmrfx.processor.datasets.Dataset;
import java.text.DecimalFormat;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.nmrfx.processor.math.Vec;

/**
 *
 * @author Bruce Johnson
 */
public class SpectrumMeasureBar {

    static final DecimalFormat formatter = new DecimalFormat();

    static {
        formatter.setMaximumFractionDigits(2);
    }
    TextField[][][] crossText = new TextField[3][2][2];
    TextField[] intensityField = new TextField[2];
    FXMLController controller;
    GridPane gridPane;
    boolean[][] iconStates = new boolean[2][2];
    ChangeListener<String> vecNumListener;
    Pane filler1 = new Pane();
    Pane filler2 = new Pane();
    static Background errorBackground = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    Consumer closeAction = null;
    ToggleButton absModeButton;
    ToggleButton gridModeButton;
    PolyChart chart = null;
    Dataset dataset = null;

    public SpectrumMeasureBar(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public void buildBar(GridPane gridPane) {
        this.gridPane = gridPane;
        String iconSize = "12px";
        String fontSize = "7pt";
        Font font = new Font(10);
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        closeButton.setOnAction(e -> close());
        absModeButton = new ToggleButton("SF");
        gridModeButton = new ToggleButton("Grid");
        absModeButton.setFont(font);
        gridModeButton.setFont(font);
        absModeButton.setOnAction(e -> update());
        gridModeButton.setOnAction(e -> update());

        gridPane.add(closeButton, 0, 0);
        gridPane.add(absModeButton, 0, 1);
        gridPane.add(gridModeButton, 0, 2);
        double[] prefWidths = {100.0, 150.0};
        String[] rowNames = {"1", "2", "\u0394"};

        String[] xys = {"x", "y"};
        for (int row = 0; row < rowNames.length; row++) {
            for (int col = 0; col < xys.length; col++) {
                Label label = new Label(rowNames[row] + xys[col] + ":");
                label.setFont(font);
                label.setPrefWidth(40);
                label.setTextAlignment(TextAlignment.RIGHT);
                label.setAlignment(Pos.CENTER_RIGHT);
                gridPane.add(label, col * 3 + 1, row);

            }
        }

        for (int iCross = 0; iCross < 3; iCross++) {
            for (int jOrient = 1; jOrient >= 0; jOrient--) {
                int jDim = jOrient == 0 ? 1 : 0;
                for (int kType = 0; kType < 2; kType++) {
                    crossText[iCross][jOrient][kType] = new TextField();
                    crossText[iCross][jOrient][kType].setPrefWidth(prefWidths[kType]);
                    crossText[iCross][jOrient][kType].setFont(font);
                    gridPane.add(crossText[iCross][jOrient][kType], 2 + jDim * 3 + kType, iCross);
                }
            }
        }
        for (int i = 0; i < intensityField.length; i++) {
            intensityField[i] = new TextField();
            intensityField[i].setPrefWidth(125);
            intensityField[i].setFont(font);
            Label label = new Label("Int " + (i + 1) + ":");
            label.setFont(font);
            label.setPrefWidth(60);
            label.setTextAlignment(TextAlignment.RIGHT);
            label.setAlignment(Pos.CENTER_RIGHT);
            gridPane.add(label, 7, i);
            gridPane.add(intensityField[i], 8, i);
        }

    }

    public GridPane getToolBar() {
        return gridPane;
    }

    public void close() {
        closeAction.accept(this);
    }

    public SpectrumMeasureBar onClose(Consumer closeAction) {
        this.closeAction = closeAction;
        return this;
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
        return stackPane;
    }

    public void update() {
        if (chart != null) {
            for (int iOrient = 0; iOrient < 2; iOrient++) {
                Double value0 = chart.crossHairStates[0][iOrient] ? chart.crossHairPositions[0][iOrient] : null;
                Double value1 = chart.crossHairStates[1][iOrient] ? chart.crossHairPositions[1][iOrient] : null;
                setCrossText(chart, dataset, iOrient, value0, value1);
            }
        }
    }

    public void getIntensity(PolyChart chart, Dataset dataset, int iCross) {
        int nDim = dataset.getNDim();
        int[] pts = new int[nDim];
        boolean ok = true;
        for (int i = 0; i < dataset.getNDim(); i++) {
            int disDim0 = chart.getDatasetAttributes().get(0).getDim(i);
            int jOrient = i == 0 ? 1 : 0;
            Double value = null;
            if (i > 1) {
                int pt1 = (int) chart.axes[i].getLowerBound();
                int pt2 = (int) chart.axes[i].getUpperBound();
                pts[disDim0] = (pt1 + pt2) / 2;
            } else {
                value = chart.crossHairStates[iCross][jOrient] ? chart.crossHairPositions[iCross][jOrient] : null;
                if (value == null) {
                    ok = false;
                    break;
                }
                pts[disDim0] = dataset.ppmToPoint(disDim0, value);
            }
        }
        if (ok) {
            String strValue;
            try {
                double value = dataset.readPoint(pts);
                strValue = String.format("%.7f", value);
                intensityField[iCross].setText(strValue);
            } catch (IOException | IllegalArgumentException ex) {
                strValue = "";
            }
            intensityField[iCross].setText(strValue);
        }
    }

    public void setCrossText(PolyChart chart, Dataset dataset, int iOrient, Double... values) {
        this.chart = chart;
        this.dataset = dataset;
        double[] pts = new double[2];
        double[] hzs = new double[2];
        double[] mHzs = new double[2];
        Vec vec = dataset.getVec();
        boolean gridMode = gridModeButton.isSelected();
        boolean absMode = absModeButton.isSelected();

        for (int iCross = 0; iCross < 3; iCross++) {
            Double value = iCross < 2 ? values[iCross] : 0.0;
            String strValue = "";
            String strPtValue = "";
            if (value != null) {
                int chartDim = iOrient == 0 ? 1 : 0;
                if ((chartDim == 0) || (chart.getNDim() > 1)) {
                    int disDim = chart.getDatasetAttributes().get(0).getDim(chartDim);
                    boolean freqMode = dataset.getFreqDomain(disDim);
                    if (iCross < 2) {
                        if (freqMode) {
                            double hz;
                            int pt = dataset.ppmToPoint(disDim, value);
                            if (gridMode) {
                                pts[iCross] = pt;
                                hz = dataset.pointToHz(disDim, pts[iCross]);
                                hz = -(hz - dataset.getSw(disDim) / 2.0);
                                strPtValue = String.format("%d pts", pt);
                            } else {
                                pts[iCross] = dataset.ppmToDPoint(disDim, value);
                                hz = dataset.pointToHz(disDim, pts[iCross]);
                                hz = -(hz - dataset.getSw(disDim) / 2.0);
                                strPtValue = String.format("%.1f pts", pts[iCross]);
                            }

                            hzs[iCross] = hz;
                            mHzs[iCross] = dataset.getSf(disDim) * 1.0e6 + hz;
                            if (absMode) {
                                strValue = String.format("%,.1f Hz", mHzs[iCross]);
                            } else {
                                strValue = String.format("%.1f Hz", hz);
                            }
                        } else {
                            pts[iCross] = (int) Math.round(value * dataset.getSw(disDim));
                            hzs[iCross] = value;
                            mHzs[iCross] = value;
                            strPtValue = String.valueOf(pts[iCross]) + " pts";
                            strValue = String.format("%.1f Hz", value);
                        }
                    } else {
                        if ((values[0] != null) && (values[1] != null)) {
                            strValue = String.format("%.1f Hz", Math.abs(hzs[1] - hzs[0]));
                            strPtValue = String.format("%.0f pts", Math.abs(pts[1] - pts[0]));
                        }
                    }
                }
            }
            crossText[iCross][iOrient][0].setText(strPtValue);
            crossText[iCross][iOrient][1].setText(strValue);
        }
        getIntensity(chart, dataset, 0);
        getIntensity(chart, dataset, 1);

    }

}
