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
package org.nmrfx.processor.gui.spectra;

import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.SpectrumMeasureBar;
import org.nmrfx.processor.gui.SpectrumStatusBar;

import java.util.List;

import static org.nmrfx.processor.gui.PolyChart.HORIZONTAL;
import static org.nmrfx.processor.gui.PolyChart.VERTICAL;
import static org.nmrfx.processor.gui.utils.ColorUtils.chooseBlackWhite;

/**
 *
 * @author Bruce Johnson
 */
public class CrossHairs {
    private static final int CROSSHAIR_TOL = 25;

    final PolyChart chart;
    FXMLController controller = null;
    final NMRAxis xAxis;
    final NMRAxis yAxis;
    double[][] crossHairPositions = new double[2][2];
    boolean[][] crossHairStates = new boolean[2][2];
    private final Line[][] crossHairLines = new Line[2][2];

    public CrossHairs(PolyChart chart) {
        this.chart = chart;
        xAxis = chart.getAxis(0);
        yAxis = chart.getAxis(1);
    }

    private FXMLController getController() {
        if (controller == null) {
            controller = chart.getController();
        }
        return controller;
    }

    public Line getLine(int i, int j) {
        return crossHairLines[i][j];
    }

    public Double[] getCrossHairPositions() {
        return getCrossHairPositions(0);
    }

    public Double[] getCrossHairPositions(int iCross) {
        Double x = crossHairStates[iCross][VERTICAL] ? crossHairPositions[iCross][VERTICAL] : null;
        Double y = crossHairStates[iCross][HORIZONTAL] ? crossHairPositions[iCross][HORIZONTAL] : null;
        Double[] result = {x, y};
        return result;
    }

    public void hideCrossHairs() {
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                hideCrossHair(iCross, jOrient);
            }
        }
    }

    public void hideCrossHair(int iCross, int jOrient) {
        SpectrumStatusBar statusBar = getController().getStatusBar();
        crossHairLines[iCross][jOrient].setVisible(false);
        int iAxis = jOrient == 0 ? 1 : 0;
        double value = iCross == 1 ? chart.getAxis(iAxis).getLowerBound() : chart.getAxis(iAxis).getUpperBound();
        statusBar.setCrossText(jOrient, iCross, value, true);
    }

    public void setState(boolean h1, boolean v1, boolean h2, boolean v2) {
        crossHairStates[0][0] = h1;
        crossHairStates[0][1] = v1;
        crossHairStates[1][0] = h2;
        crossHairStates[1][1] = v2;
    }

    public void setCrossHairState(boolean value) {
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                crossHairStates[iCross][jOrient] = getController().getCrossHairState(iCross, jOrient) && value;
            }
        }
        if (!value) {
            hideCrossHairs();
        }
    }

    public void setCrossHairState(int iCross, int jOrient, boolean value) {
        crossHairStates[iCross][jOrient] = getController().getCrossHairState(iCross, jOrient) && value;
        if (!value) {
            hideCrossHair(iCross, jOrient);
        }
    }

    public boolean getCrossHairState(int iCross, int jOrient) {
        return crossHairStates[iCross][jOrient];
    }

    public void refreshCrossHairs() {
        SpectrumStatusBar statusBar = getController().getStatusBar();
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                int iAxis = jOrient == 0 ? 1 : 0;
                NMRAxis axis = chart.getAxis(iAxis);
                if (!isCrossHairInRange(iCross, jOrient)) {
                    crossHairLines[iCross][jOrient].setVisible(false);
                    double value = iCross == 1 ? axis.getLowerBound() : axis.getUpperBound();
                    statusBar.setCrossText(jOrient, iCross, value, true);
                } else if (crossHairStates[iCross][jOrient] && crossHairLines[iCross][jOrient].isVisible()) {
                    drawCrossHair(iCross, jOrient);
                } else {
                    double value = iCross == 1 ? axis.getLowerBound() : axis.getUpperBound();
                    statusBar.setCrossText(jOrient, iCross, value, true);
                }
                statusBar.setCrossTextRange(iCross, jOrient, axis.getLowerBound(), axis.getUpperBound());
            }
        }
        chart.drawSlices();
    }

    public double getPosition(int i, int j) {
        return crossHairPositions[i][j];
    }

    public double[] getVerticalPositions() {
        double[] positions = new double[2];
        positions[0] = crossHairPositions[0][1];
        positions[1] = crossHairPositions[1][1];
        return positions;
    }

    public void setLineColors(Color fillColor, Color cross0Color, Color cross1Color) {
        Color color0 = cross0Color;
        if (color0 == null) {
            color0 = chooseBlackWhite(fillColor);
        }

        Color color1 = cross1Color;
        if (color1 == null) {
            if (color0 == Color.BLACK) {
                color1 = Color.RED;
            } else {
                color1 = Color.MAGENTA;
            }
        }

        setLineColors(color0, color1);
    }

    private void setLineColors(Color color0, Color color1) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                if (i == 0) {
                    crossHairLines[i][j].setStroke(color0);
                } else {
                    crossHairLines[i][j].setStroke(color1);
                }
            }
        }
    }

    public void updatePosition(int crossHairNum, int orientation, double value) {
        crossHairPositions[crossHairNum][orientation] = value;
        refreshCrossHairs();
    }

    public void init() {
        crossHairLines[0][0] = new Line(0, 50, 400, 50);
        crossHairLines[0][1] = new Line(100, 0, 100, 400);
        crossHairLines[1][0] = new Line(0, 50, 400, 50);
        crossHairLines[1][1] = new Line(100, 0, 100, 400);
        crossHairStates[0][0] = false;
        crossHairStates[0][1] = true;
        crossHairStates[1][0] = false;
        crossHairStates[1][1] = true;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                crossHairLines[i][j].setVisible(false);
                crossHairLines[i][j].setStrokeWidth(0.5);
                crossHairLines[i][j].setMouseTransparent(true);
                if (i == 0) {
                    crossHairLines[i][j].setStroke(Color.BLACK);
                } else {
                    crossHairLines[i][j].setStroke(Color.RED);
                }
            }
        }
    }

    private static void updateAllCharts(PolyChart source, int iCross, int iOrient, double position, String dimLabel) {
        PolyChart.CHARTS.stream().filter((c) -> (c != source)).forEach((c) -> {
            c.getCrossHairs().syncCrosshair(iCross, iOrient, dimLabel, position);
        });
    }

    public void moveCrosshair(int iCross, int iOrient, double value) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (dataAttrs.isEmpty()) {
            return;
        }
        value = crossHairInRange(iCross, iOrient, value);
        setCrossHairPosition(iCross, iOrient, value);
        drawCrossHair(iCross, iOrient);
        double aValue = crossHairPositions[iCross][iOrient];
        DatasetAttributes dataAttr = dataAttrs.get(0);
        String label;
        int axisDim = iOrient == VERTICAL ? 0 : 1;
        label = dataAttr.getLabel(axisDim);
        updateAllCharts(chart, iCross, iOrient, aValue, label);
        chart.drawSlices();
    }

    public void syncCrosshair(int iCross, int iOrient, String dimLabel, double value) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (dataAttrs.isEmpty()) {
            return;
        }
        DatasetAttributes dataAttr = dataAttrs.get(0);
        int jOrient = -1;
        if (dataAttr.getLabel(0).equals(dimLabel)) {
            if (value >= xAxis.getLowerBound() && (value <= xAxis.getUpperBound())) {
                jOrient = VERTICAL;
            }
        } else if (dataAttr.getLabel(1).equals(dimLabel)) {
            if (value >= yAxis.getLowerBound() && (value <= yAxis.getUpperBound())) {
                jOrient = HORIZONTAL;
            }
        }
        if (jOrient >= 0) {
            crossHairPositions[iCross][jOrient] = value;
            drawCrossHair(iCross, jOrient);
            if (iCross == 0) {
                chart.drawSlices();
            }
        }
    }

    public boolean isCrossHairInRange(int iCross, int iOrient) {
        double value = crossHairPositions[iCross][iOrient];

        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        double xOrigin = xAxis.getXOrigin();
        double yOrigin = xAxis.getYOrigin();
        boolean ok = true;

        if (iOrient == HORIZONTAL) {
            value = yAxis.getDisplayPosition(value);
            if (value > yOrigin) {
                ok = false;
            } else if (value < (yOrigin - height)) {
                ok = false;
            }
        } else {
            value = xAxis.getDisplayPosition(value);
            if (value > (xOrigin + width)) {
                ok = false;
            } else if (value < xOrigin) {
                ok = false;
            }
        }
        return ok;

    }

    public double crossHairInRange(int iCross, int iOrient, double value) {
        if (value < 0) {
            value = 1;
        }
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        double xOrigin = xAxis.getXOrigin();
        double yOrigin = xAxis.getYOrigin();

        if (iOrient == HORIZONTAL) {
            if (value > yOrigin) {
                value = yOrigin - 1;
            } else if (value < (yOrigin - height)) {
                value = yOrigin - height + 1;
            }
        } else {
            if (value > (xOrigin + width)) {
                value = xOrigin + width - 1;
            } else if (value < xOrigin) {
                value = xOrigin + 1;
            }
        }
        return value;

    }

    public void setCrossHairPosition(int iCross, int iOrient, double value) {
        if (iOrient == HORIZONTAL) {
            value = yAxis.getValueForDisplay(value).doubleValue();
        } else {
            value = xAxis.getValueForDisplay(value).doubleValue();
        }
        crossHairPositions[iCross][iOrient] = value;
    }

    public boolean hasCrosshairRegion() {
        boolean horizontalRegion = crossHairStates[0][VERTICAL] && crossHairLines[0][VERTICAL].isVisible()
                && crossHairStates[1][VERTICAL] && crossHairLines[1][VERTICAL].isVisible();
        boolean verticalRegion = crossHairStates[0][HORIZONTAL] && crossHairLines[0][HORIZONTAL].isVisible()
                && crossHairStates[1][HORIZONTAL] && crossHairLines[1][HORIZONTAL].isVisible();
        boolean hasRegion;
        if (chart.is1D()) {
            hasRegion = horizontalRegion;
        } else {
            hasRegion = horizontalRegion && verticalRegion;
        }
        return hasRegion;
    }

    public boolean hasCrosshairState(String state) {
        boolean v0 = crossHairStates[0][VERTICAL] && crossHairLines[0][VERTICAL].isVisible();
        boolean v1 = crossHairStates[1][VERTICAL] && crossHairLines[1][VERTICAL].isVisible();
        boolean h0 = crossHairStates[0][HORIZONTAL] && crossHairLines[0][HORIZONTAL].isVisible();
        boolean h1 = crossHairStates[1][HORIZONTAL] && crossHairLines[1][HORIZONTAL].isVisible();
        boolean result;
        if (state.equals("region")) {
            state = chart.is1D() ? "||" : "[]";
        }
        switch (state) {
            case "||":
                result = v0 & v1;
                break;
            case "=":
                result = h0 & h1;
                break;
            case "|_":
                result = h0 & v0;
                break;
            case "[]":
                result = h0 & v0 & h1 & v1;
                break;
            case "v0":
                result = v0;
                break;
            case "h0":
                result = h0;
                break;
            case "v1":
                result = v1;
                break;
            case "h1":
                result = h1;
                break;
            default:
                result = false;
        }
        return result;
    }

    public int[] getCrossHairNum(double x, double y, boolean hasMiddleMouseButton, boolean middleButton) {
        int[] srch0 = {0};
        int[] srch1 = {1};
        int[] srch01 = {0, 1};
        int[] searchCrosshairs;
        int[] orients = {HORIZONTAL, VERTICAL};
        if (hasMiddleMouseButton) {
            searchCrosshairs = middleButton ? srch1 : srch0;
        } else {
            searchCrosshairs = srch01;
        }
        double inf = Double.POSITIVE_INFINITY;

        double[][] deltas = {{inf, inf}, {inf, inf}};
        int[] result = {-1, -1};
        int[] closest = {-1, -1};

        for (int orient : orients) {
            for (int i : searchCrosshairs) {
                Line line = crossHairLines[i][orient];
                double value = orient == HORIZONTAL ? line.getStartY() : line.getStartX();
                double ref = orient == HORIZONTAL ? y : x;
                if (crossHairStates[i][orient] && crossHairLines[i][orient].isVisible()) {
                    deltas[i][orient] = Math.abs(value - ref);
                }
            }
            if (Double.isFinite(deltas[0][orient]) && (deltas[0][orient] < deltas[1][orient])) {
                closest[orient] = 0;
                if (deltas[0][orient] < CROSSHAIR_TOL) {
                    result[orient] = 0;
                }

            } else if (Double.isFinite(deltas[1][orient])) {
                closest[orient] = 1;
                if (deltas[1][orient] < CROSSHAIR_TOL) {
                    result[orient] = 1;
                }
            }
        }
        if ((result[0] == -1) && (result[1] == -1)) {
            if (!crossHairLines[0][HORIZONTAL].isVisible()) {
                result[HORIZONTAL] = 0;
            } else if (!crossHairLines[1][HORIZONTAL].isVisible()) {
                result[HORIZONTAL] = 1;
            } else if (closest[0] != -1) {
                result[HORIZONTAL] = closest[0];
            }
            if (!crossHairLines[0][VERTICAL].isVisible()) {
                result[VERTICAL] = 0;
            } else if (!crossHairLines[1][VERTICAL].isVisible()) {
                result[VERTICAL] = 1;
            } else if (closest[1] != -1) {
                result[VERTICAL] = closest[1];
            }
        }
        return result;
    }

    public void setSliceStatus(boolean state) {
        refreshCrossHairs();
    }

    public void drawCrossHair(int iCross, int iOrient) {
        DatasetBase dataset = chart.getDataset();
        if (dataset == null) {
            return;
        }
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        double xOrigin = xAxis.getXOrigin();
        double yOrigin = yAxis.getYOrigin();
        if (crossHairStates[iCross][iOrient]) {
            double value = crossHairPositions[iCross][iOrient];
            getController().getStatusBar().setCrossText(iOrient, iCross, value, false);
            updateMeasureBar(dataset, iOrient);
            if (iOrient == HORIZONTAL) {
                value = yAxis.getDisplayPosition(value);
                crossHairLines[iCross][iOrient].setStartX(xOrigin);
                crossHairLines[iCross][iOrient].setEndX(xOrigin + width);
                crossHairLines[iCross][iOrient].setStartY(value);
                crossHairLines[iCross][iOrient].setEndY(value);
            } else {
                value = xAxis.getDisplayPosition(value);
                crossHairLines[iCross][iOrient].setStartY(yOrigin);
                crossHairLines[iCross][iOrient].setEndY(yOrigin - height);
                crossHairLines[iCross][iOrient].setStartX(value);
                crossHairLines[iCross][iOrient].setEndX(value);
            }
            crossHairLines[iCross][iOrient].setVisible(true);
            crossHairLines[iCross][iOrient].setVisible(true);
        }
    }

    public void updateMeasureBar(DatasetBase dataset, int iOrient) {
        SpectrumMeasureBar measureBar = getController().getSpectrumMeasureBar();
        if (measureBar != null) {
            Double value0 = crossHairStates[0][iOrient] ? crossHairPositions[0][iOrient] : null;
            Double value1 = crossHairStates[1][iOrient] ? crossHairPositions[1][iOrient] : null;
            measureBar.setCrossText(chart, dataset, iOrient, value0, value1);
        }
    }

}
