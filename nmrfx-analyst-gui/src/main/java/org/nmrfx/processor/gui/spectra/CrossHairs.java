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

import javafx.geometry.Orientation;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.SpectrumMeasureBar;
import org.nmrfx.processor.gui.SpectrumStatusBar;

import java.util.List;
import java.util.stream.Stream;

import static org.nmrfx.processor.gui.utils.ColorUtils.chooseBlackWhite;

/**
 * @author Bruce Johnson
 */
public class CrossHairs {
    private static final int SELECTION_TOLERANCE = 25;

    class CrossHairLine {
        private final Line line = new Line(0, 0, 0, 0);
        private final Orientation orientation;
        private boolean enabled;
        private double position;

        public CrossHairLine(Orientation orientation) {
            this.orientation = orientation;

            line.setVisible(false);
            line.setStrokeWidth(0.5);
            line.setMouseTransparent(true);
        }

        public void setLine(double startX, double startY, double endX, double endY) {
            line.setStartX(startX);
            line.setStartY(startY);
            line.setEndX(endX);
            line.setEndY(endY);
        }

        public boolean isDisplayed() {
            return enabled && line.isVisible();
        }
    }

    class CrossHair {
        // XXX temporary, for compatibility with code working with cursor indexes
        private final int id;

        private final CrossHairLine horizontal = new CrossHairLine(Orientation.HORIZONTAL);
        private final CrossHairLine vertical = new CrossHairLine(Orientation.VERTICAL);

        public CrossHair(int id) {
            this.id = id;
        }

        public void setColor(Color color) {
            horizontal.line.setStroke(color);
            vertical.line.setStroke(color);
        }
    }

    private final PolyChart chart;
    private final FXMLController controller;
    private final NMRAxis xAxis;
    private final NMRAxis yAxis;

    private final CrossHair primary = new CrossHair(0);
    private final CrossHair secondary = new CrossHair(1);

    public CrossHairs(PolyChart chart) {
        this.chart = chart;
        this.controller = chart.getController();
        xAxis = chart.getAxis(0);
        yAxis = chart.getAxis(1);

        initialize();
    }

    private void initialize() {
        primary.setColor(Color.BLACK);
        primary.horizontal.setLine(0, 50, 400, 50);
        primary.vertical.setLine(100, 0, 100, 400);
        primary.vertical.enabled = true;

        primary.setColor(Color.RED);
        secondary.horizontal.setLine(0, 50, 400, 50);
        secondary.vertical.setLine(100, 0, 100, 400);
        secondary.vertical.enabled = true;
    }


    private FXMLController getController() {
        return controller;
    }

    private Orientation orientationFromInt(int index) {
        return switch (index) {
            case 0 -> Orientation.HORIZONTAL;
            case 1 -> Orientation.VERTICAL;
            default -> throw new IllegalArgumentException("Unknown orientation: " + index);
        };
    }

    private CrossHair getCrossHair(int index) {
        return switch (index) {
            case 0 -> primary;
            case 1 -> secondary;
            default -> throw new IllegalArgumentException("Unknown crosshair: " + index);
        };
    }

    private CrossHairLine getCrossHairLine(int index, Orientation orientation) {
        return switch (orientation) {
            case HORIZONTAL -> getCrossHair(index).horizontal;
            case VERTICAL -> getCrossHair(index).vertical;
        };
    }

    private CrossHairLine getCrossHairLine(int index, int orientation) {
        return getCrossHairLine(index, orientationFromInt(orientation));
    }

    public Line getLine(int index, int orientation) {
        return getCrossHairLine(index, orientation).line;
    }

    public Double[] getPositions() {
        return getPositions(0);
    }

    public Double[] getPositions(int index) {
        CrossHair crossHair = getCrossHair(index);
        Double x = crossHair.vertical.enabled ? crossHair.vertical.position : null;
        Double y = crossHair.horizontal.enabled ? crossHair.horizontal.position : null;
        return new Double[]{x, y};
    }

    public void hideAll() {
        for (int index = 0; index < 2; index++) {
            hide(index, Orientation.HORIZONTAL);
            hide(index, Orientation.VERTICAL);
        }
    }

    private void hide(int index, Orientation orientation) {
        SpectrumStatusBar statusBar = getController().getStatusBar();
        getCrossHairLine(index, orientation).line.setVisible(false);
        int iAxis = orientation == Orientation.HORIZONTAL ? 1 : 0;
        double value = index == 1 ? chart.getAxis(iAxis).getLowerBound() : chart.getAxis(iAxis).getUpperBound();
        statusBar.setCrossText(orientation.ordinal(), index, value, true);
    }

    public void setStates(boolean h1, boolean v1, boolean h2, boolean v2) {
        primary.horizontal.enabled = h1;
        primary.vertical.enabled = v1;
        secondary.horizontal.enabled = h2;
        secondary.vertical.enabled = v2;
    }

    public void setAllStates(boolean value) {
        Stream.of(primary, secondary).forEach(c -> {
            c.horizontal.enabled = value && getController().getCrossHairState(c.id, Orientation.HORIZONTAL.ordinal());
            c.vertical.enabled = value && getController().getCrossHairState(c.id, Orientation.VERTICAL.ordinal());
        });

        if (!value) {
            hideAll();
        }
    }

    public void setState(int index, int orientationIndex, boolean value) {
        Orientation orientation = orientationFromInt(orientationIndex);
        getCrossHairLine(index, orientation).enabled = value && getController().getCrossHairState(index, orientation.ordinal());

        if (!value) {
            hide(index, orientation);
        }
    }

    public boolean getState(int index, int orientation) {
        return getCrossHairLine(index, orientation).enabled;
    }

    public void refresh() {
        SpectrumStatusBar statusBar = getController().getStatusBar();
        for (int index = 0; index < 2; index++) {
            for (int orientation = 0; orientation < 2; orientation++) {
                CrossHairLine line = getCrossHairLine(index, orientation);
                int iAxis = orientation == 0 ? 1 : 0;
                NMRAxis axis = chart.getAxis(iAxis);
                if (!isInRange(index, line.orientation)) {
                    line.line.setVisible(false);
                    double value = index == 1 ? axis.getLowerBound() : axis.getUpperBound();
                    statusBar.setCrossText(orientation, index, value, true);
                } else if (line.isDisplayed()) {
                    draw(index, line.orientation);
                } else {
                    double value = index == 1 ? axis.getLowerBound() : axis.getUpperBound();
                    statusBar.setCrossText(orientation, index, value, true);
                }
                statusBar.setCrossTextRange(index, orientation, axis.getLowerBound(), axis.getUpperBound());
            }
        }
        chart.drawSlices();
    }

    public double getPosition(int index, int orientation) {
        return getCrossHairLine(index, orientation).position;
    }

    public double[] getVerticalPositions() {
        return new double[]{
                primary.vertical.position,
                secondary.vertical.position,
        };
    }

    public void setLineColors(Color fillColor, Color primaryColor, Color secondaryColor) {
        if (primaryColor == null) {
            primaryColor = chooseBlackWhite(fillColor);
        }
        if (secondaryColor == null) {
            secondaryColor = primaryColor == Color.BLACK ? Color.RED : Color.MAGENTA;
        }
        primary.setColor(primaryColor);
        secondary.setColor(secondaryColor);
    }

    public void updatePosition(int index, int orientation, double value) {
        getCrossHairLine(index, orientation).position = value;
        refresh();
    }

    public void move(int index, int orientationIndex, double value) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (dataAttrs.isEmpty()) {
            return;
        }

        Orientation orientation = orientationFromInt(orientationIndex);
        value = valueInRange(index, orientation, value);
        setPosition(index, orientation, value);
        draw(index, orientation);
        DatasetAttributes dataAttr = dataAttrs.get(0);
        int axisDim = orientation == Orientation.VERTICAL ? 0 : 1;
        String label = dataAttr.getLabel(axisDim);

        double position = getCrossHairLine(index, orientationIndex).position;
        updateAllCharts(chart, index, orientationIndex, position, label);
        chart.drawSlices();
    }

    private void sync(int index, int ignoredOrientation, String dimLabel, double value) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (dataAttrs.isEmpty()) {
            return;
        }
        DatasetAttributes dataAttr = dataAttrs.get(0);
        Orientation orientation = null;
        if (dataAttr.getLabel(0).equals(dimLabel)) {
            if (value >= xAxis.getLowerBound() && (value <= xAxis.getUpperBound())) {
                orientation = Orientation.VERTICAL;
            }
        } else if (dataAttr.getLabel(1).equals(dimLabel)) {
            if (value >= yAxis.getLowerBound() && (value <= yAxis.getUpperBound())) {
                orientation = Orientation.HORIZONTAL;
            }
        }
        if (orientation != null) {
            getCrossHairLine(index, orientation).position = value;
            draw(index, orientation);
            if (index == 0) {
                chart.drawSlices();
            }
        }
    }

    private boolean isInRange(int index, Orientation orientation) {
        double value = getCrossHairLine(index, orientation).position;

        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        double xOrigin = xAxis.getXOrigin();
        double yOrigin = xAxis.getYOrigin();
        boolean ok = true;

        if (orientation == Orientation.HORIZONTAL) {
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

    private double valueInRange(int ignoredIndex, Orientation orientation, double value) {
        if (value < 0) {
            value = 1;
        }
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        double xOrigin = xAxis.getXOrigin();
        double yOrigin = xAxis.getYOrigin();

        if (orientation == Orientation.HORIZONTAL) {
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

    private void setPosition(int index, Orientation orientation, double value) {
        NMRAxis axis = orientation == Orientation.HORIZONTAL ? yAxis : xAxis;
        value = axis.getValueForDisplay(value).doubleValue();
        getCrossHairLine(index, orientation).position = value;
    }

    public boolean hasRegion() {
        boolean horizontalRegion = primary.vertical.isDisplayed() && secondary.vertical.isDisplayed();
        boolean verticalRegion = primary.horizontal.isDisplayed() && secondary.horizontal.isDisplayed();
        return horizontalRegion && (verticalRegion || chart.is1D());
    }

    public boolean hasState(String state) {
        boolean v0 = primary.vertical.isDisplayed();
        boolean v1 = secondary.vertical.isDisplayed();
        boolean h0 = primary.horizontal.isDisplayed();
        boolean h1 = secondary.horizontal.isDisplayed();

        if (state.equals("region")) {
            state = chart.is1D() ? "||" : "[]";
        }

        return switch (state) {
            case "||" -> v0 & v1;
            case "=" -> h0 & h1;
            case "|_" -> h0 & v0;
            case "[]" -> h0 & v0 & h1 & v1;
            case "v0" -> v0;
            case "h0" -> h0;
            case "v1" -> v1;
            case "h1" -> h1;
            default -> false;
        };
    }

    //TODO return crosshair instance instead of index
    public int[] findAtPosition(double x, double y, boolean hasMiddleMouseButton, boolean middleButton) {
        int[] srch0 = {0};
        int[] srch1 = {1};
        int[] srch01 = {0, 1};
        int[] searchCrosshairs;
        if (hasMiddleMouseButton) {
            searchCrosshairs = middleButton ? srch1 : srch0;
        } else {
            searchCrosshairs = srch01;
        }
        double inf = Double.POSITIVE_INFINITY;

        double[][] deltas = {{inf, inf}, {inf, inf}};
        int[] result = {-1, -1};
        int[] closest = {-1, -1};

        for (Orientation orientation : Orientation.values()) {
            for (int i : searchCrosshairs) {
                CrossHairLine crossHairLine = getCrossHairLine(i, orientation);
                Line line = crossHairLine.line;
                double value = orientation == Orientation.HORIZONTAL ? line.getStartY() : line.getStartX();
                double ref = orientation == Orientation.HORIZONTAL ? y : x;

                if (crossHairLine.isDisplayed()) {
                    deltas[i][orientation.ordinal()] = Math.abs(value - ref);
                }
            }
            if (Double.isFinite(deltas[0][orientation.ordinal()]) && (deltas[0][orientation.ordinal()] < deltas[1][orientation.ordinal()])) {
                closest[orientation.ordinal()] = 0;
                if (deltas[0][orientation.ordinal()] < SELECTION_TOLERANCE) {
                    result[orientation.ordinal()] = 0;
                }

            } else if (Double.isFinite(deltas[1][orientation.ordinal()])) {
                closest[orientation.ordinal()] = 1;
                if (deltas[1][orientation.ordinal()] < SELECTION_TOLERANCE) {
                    result[orientation.ordinal()] = 1;
                }
            }
        }

        if ((result[0] == -1) && (result[1] == -1)) {
            if (!primary.horizontal.isDisplayed()) {
                result[Orientation.HORIZONTAL.ordinal()] = 0;
            } else if (!secondary.horizontal.isDisplayed()) {
                result[Orientation.HORIZONTAL.ordinal()] = 1;
            } else if (closest[0] != -1) {
                result[Orientation.HORIZONTAL.ordinal()] = closest[0];
            }
            if (!primary.vertical.isDisplayed()) {
                result[Orientation.VERTICAL.ordinal()] = 0;
            } else if (!secondary.vertical.isDisplayed()) {
                result[Orientation.VERTICAL.ordinal()] = 1;
            } else if (closest[1] != -1) {
                result[Orientation.VERTICAL.ordinal()] = closest[1];
            }
        }

        return result;
    }

    private void draw(int index, Orientation orientation) {
        DatasetBase dataset = chart.getDataset();
        if (dataset == null) {
            return;
        }
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        double xOrigin = xAxis.getXOrigin();
        double yOrigin = yAxis.getYOrigin();

        CrossHairLine line = getCrossHairLine(index, orientation);

        if (line.enabled) {
            double value = line.position;
            getController().getStatusBar().setCrossText(orientation.ordinal(), index, value, false);
            updateMeasureBar(dataset, orientation);
            if (orientation == Orientation.HORIZONTAL) {
                value = yAxis.getDisplayPosition(value);
                line.setLine(xOrigin, value, xOrigin + width, value);
            } else {
                value = xAxis.getDisplayPosition(value);
                line.setLine(value, yOrigin, value, yOrigin - height);
            }

            line.line.setVisible(true);
        }
    }

    private void updateMeasureBar(DatasetBase dataset, Orientation orientation) {
        SpectrumMeasureBar measureBar = getController().getSpectrumMeasureBar();
        if (measureBar != null) {
            CrossHairLine primary = getCrossHairLine(0, orientation);
            CrossHairLine secondary = getCrossHairLine(1, orientation);
            Double value0 = primary.enabled ? primary.position : null;
            Double value1 = secondary.enabled ? secondary.position : null;
            measureBar.setCrossText(chart, dataset, orientation.ordinal(), value0, value1);
        }
    }

    private static void updateAllCharts(PolyChart source, int iCross, int iOrient, double position, String dimLabel) {
        PolyChart.CHARTS.stream().filter((c) -> (c != source)).forEach((c) -> {
            c.getCrossHairs().sync(iCross, iOrient, dimLabel, position);
        });
    }
}
