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
 * @author Bruce Johnson
 */
public class CrossHairs {
    private static final int SELECTION_TOLERANCE = 25;

    private final PolyChart chart;
    private final FXMLController controller;
    private final NMRAxis xAxis;
    private final NMRAxis yAxis;
    private final double[][] positions = new double[2][2];
    private final boolean[][] states = new boolean[2][2];
    private final Line[][] lines = new Line[2][2];

    public CrossHairs(PolyChart chart) {
        this.chart = chart;
        this.controller = chart.getController();
        xAxis = chart.getAxis(0);
        yAxis = chart.getAxis(1);

        initialize();
    }

    private void initialize() {
        lines[0][0] = new Line(0, 50, 400, 50);
        lines[0][1] = new Line(100, 0, 100, 400);
        lines[1][0] = new Line(0, 50, 400, 50);
        lines[1][1] = new Line(100, 0, 100, 400);
        states[0][0] = false;
        states[0][1] = true;
        states[1][0] = false;
        states[1][1] = true;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                lines[i][j].setVisible(false);
                lines[i][j].setStrokeWidth(0.5);
                lines[i][j].setMouseTransparent(true);
                if (i == 0) {
                    lines[i][j].setStroke(Color.BLACK);
                } else {
                    lines[i][j].setStroke(Color.RED);
                }
            }
        }
    }


    private FXMLController getController() {
        return controller;
    }

    public Line getLine(int i, int j) {
        return lines[i][j];
    }

    public Double[] getPositions() {
        return getPositions(0);
    }

    public Double[] getPositions(int iCross) {
        Double x = states[iCross][VERTICAL] ? positions[iCross][VERTICAL] : null;
        Double y = states[iCross][HORIZONTAL] ? positions[iCross][HORIZONTAL] : null;
        return new Double[]{x, y};
    }

    public void hideAll() {
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                hide(iCross, jOrient);
            }
        }
    }

    public void hide(int iCross, int jOrient) {
        SpectrumStatusBar statusBar = getController().getStatusBar();
        lines[iCross][jOrient].setVisible(false);
        int iAxis = jOrient == 0 ? 1 : 0;
        double value = iCross == 1 ? chart.getAxis(iAxis).getLowerBound() : chart.getAxis(iAxis).getUpperBound();
        statusBar.setCrossText(jOrient, iCross, value, true);
    }

    public void setStates(boolean h1, boolean v1, boolean h2, boolean v2) {
        states[0][0] = h1;
        states[0][1] = v1;
        states[1][0] = h2;
        states[1][1] = v2;
    }

    public void setAllStates(boolean value) {
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                states[iCross][jOrient] = getController().getCrossHairState(iCross, jOrient) && value;
            }
        }
        if (!value) {
            hideAll();
        }
    }

    public void setState(int iCross, int jOrient, boolean value) {
        states[iCross][jOrient] = getController().getCrossHairState(iCross, jOrient) && value;
        if (!value) {
            hide(iCross, jOrient);
        }
    }

    public boolean getState(int iCross, int jOrient) {
        return states[iCross][jOrient];
    }

    public void refresh() {
        SpectrumStatusBar statusBar = getController().getStatusBar();
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                int iAxis = jOrient == 0 ? 1 : 0;
                NMRAxis axis = chart.getAxis(iAxis);
                if (!isInRange(iCross, jOrient)) {
                    lines[iCross][jOrient].setVisible(false);
                    double value = iCross == 1 ? axis.getLowerBound() : axis.getUpperBound();
                    statusBar.setCrossText(jOrient, iCross, value, true);
                } else if (states[iCross][jOrient] && lines[iCross][jOrient].isVisible()) {
                    draw(iCross, jOrient);
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
        return positions[i][j];
    }

    public double[] getVerticalPositions() {
        double[] positions = new double[2];
        positions[0] = this.positions[0][1];
        positions[1] = this.positions[1][1];
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
                    lines[i][j].setStroke(color0);
                } else {
                    lines[i][j].setStroke(color1);
                }
            }
        }
    }

    public void updatePosition(int crossHairNum, int orientation, double value) {
        positions[crossHairNum][orientation] = value;
        refresh();
    }

    public void move(int iCross, int iOrient, double value) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (dataAttrs.isEmpty()) {
            return;
        }
        value = valueInRange(iCross, iOrient, value);
        setPosition(iCross, iOrient, value);
        draw(iCross, iOrient);
        double aValue = positions[iCross][iOrient];
        DatasetAttributes dataAttr = dataAttrs.get(0);
        String label;
        int axisDim = iOrient == VERTICAL ? 0 : 1;
        label = dataAttr.getLabel(axisDim);
        updateAllCharts(chart, iCross, iOrient, aValue, label);
        chart.drawSlices();
    }

    public void sync(int iCross, int iOrient, String dimLabel, double value) {
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
            positions[iCross][jOrient] = value;
            draw(iCross, jOrient);
            if (iCross == 0) {
                chart.drawSlices();
            }
        }
    }

    public boolean isInRange(int iCross, int iOrient) {
        double value = positions[iCross][iOrient];

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

    private double valueInRange(int iCross, int iOrient, double value) {
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

    public void setPosition(int iCross, int iOrient, double value) {
        if (iOrient == HORIZONTAL) {
            value = yAxis.getValueForDisplay(value).doubleValue();
        } else {
            value = xAxis.getValueForDisplay(value).doubleValue();
        }
        positions[iCross][iOrient] = value;
    }

    public boolean hasRegion() {
        boolean horizontalRegion = states[0][VERTICAL] && lines[0][VERTICAL].isVisible()
                && states[1][VERTICAL] && lines[1][VERTICAL].isVisible();
        boolean verticalRegion = states[0][HORIZONTAL] && lines[0][HORIZONTAL].isVisible()
                && states[1][HORIZONTAL] && lines[1][HORIZONTAL].isVisible();
        boolean hasRegion;
        if (chart.is1D()) {
            hasRegion = horizontalRegion;
        } else {
            hasRegion = horizontalRegion && verticalRegion;
        }
        return hasRegion;
    }

    public boolean hasState(String state) {
        boolean v0 = states[0][VERTICAL] && lines[0][VERTICAL].isVisible();
        boolean v1 = states[1][VERTICAL] && lines[1][VERTICAL].isVisible();
        boolean h0 = states[0][HORIZONTAL] && lines[0][HORIZONTAL].isVisible();
        boolean h1 = states[1][HORIZONTAL] && lines[1][HORIZONTAL].isVisible();
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
                Line line = lines[i][orient];
                double value = orient == HORIZONTAL ? line.getStartY() : line.getStartX();
                double ref = orient == HORIZONTAL ? y : x;
                if (states[i][orient] && lines[i][orient].isVisible()) {
                    deltas[i][orient] = Math.abs(value - ref);
                }
            }
            if (Double.isFinite(deltas[0][orient]) && (deltas[0][orient] < deltas[1][orient])) {
                closest[orient] = 0;
                if (deltas[0][orient] < SELECTION_TOLERANCE) {
                    result[orient] = 0;
                }

            } else if (Double.isFinite(deltas[1][orient])) {
                closest[orient] = 1;
                if (deltas[1][orient] < SELECTION_TOLERANCE) {
                    result[orient] = 1;
                }
            }
        }
        if ((result[0] == -1) && (result[1] == -1)) {
            if (!lines[0][HORIZONTAL].isVisible()) {
                result[HORIZONTAL] = 0;
            } else if (!lines[1][HORIZONTAL].isVisible()) {
                result[HORIZONTAL] = 1;
            } else if (closest[0] != -1) {
                result[HORIZONTAL] = closest[0];
            }
            if (!lines[0][VERTICAL].isVisible()) {
                result[VERTICAL] = 0;
            } else if (!lines[1][VERTICAL].isVisible()) {
                result[VERTICAL] = 1;
            } else if (closest[1] != -1) {
                result[VERTICAL] = closest[1];
            }
        }
        return result;
    }

    public void draw(int iCross, int iOrient) {
        DatasetBase dataset = chart.getDataset();
        if (dataset == null) {
            return;
        }
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        double xOrigin = xAxis.getXOrigin();
        double yOrigin = yAxis.getYOrigin();
        if (states[iCross][iOrient]) {
            double value = positions[iCross][iOrient];
            getController().getStatusBar().setCrossText(iOrient, iCross, value, false);
            updateMeasureBar(dataset, iOrient);
            if (iOrient == HORIZONTAL) {
                value = yAxis.getDisplayPosition(value);
                lines[iCross][iOrient].setStartX(xOrigin);
                lines[iCross][iOrient].setEndX(xOrigin + width);
                lines[iCross][iOrient].setStartY(value);
                lines[iCross][iOrient].setEndY(value);
            } else {
                value = xAxis.getDisplayPosition(value);
                lines[iCross][iOrient].setStartY(yOrigin);
                lines[iCross][iOrient].setEndY(yOrigin - height);
                lines[iCross][iOrient].setStartX(value);
                lines[iCross][iOrient].setEndX(value);
            }
            lines[iCross][iOrient].setVisible(true);
            lines[iCross][iOrient].setVisible(true);
        }
    }

    public void updateMeasureBar(DatasetBase dataset, int iOrient) {
        SpectrumMeasureBar measureBar = getController().getSpectrumMeasureBar();
        if (measureBar != null) {
            Double value0 = states[0][iOrient] ? positions[0][iOrient] : null;
            Double value1 = states[1][iOrient] ? positions[1][iOrient] : null;
            measureBar.setCrossText(chart, dataset, iOrient, value0, value1);
        }
    }

    private static void updateAllCharts(PolyChart source, int iCross, int iOrient, double position, String dimLabel) {
        PolyChart.CHARTS.stream().filter((c) -> (c != source)).forEach((c) -> {
            c.getCrossHairs().sync(iCross, iOrient, dimLabel, position);
        });
    }
}
