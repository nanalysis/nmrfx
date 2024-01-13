/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2023 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.spectra.crosshair;

import javafx.geometry.Orientation;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.nmrfx.chart.Axis;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.nmrfx.processor.gui.utils.GUIColorUtils.toBlackOrWhite;

/**
 * Cross-hair cursor management, used by PolyChart.
 */
public class CrossHairs {
    private static final int SELECTION_TOLERANCE = 25;

    private final PolyChart chart;
    private final FXMLController controller;
    private final Axis xAxis;
    private final Axis yAxis;

    private final CrossHair primary = new CrossHair(0);
    private final CrossHair secondary = new CrossHair(1);

    public CrossHairs(PolyChart chart) {
        this.chart = chart;
        this.controller = chart.getFXMLController();
        xAxis = chart.getAxes().getX();
        yAxis = chart.getAxes().getY();

        initialize();
    }

    private void initialize() {
        primary.setColor(Color.BLACK);
        primary.setHorizontalLine(0, 50, 400, 50);
        primary.setVerticalLine(100, 0, 100, 400);
        primary.setVerticalActive(true);

        primary.setColor(Color.RED);
        secondary.setHorizontalLine(0, 50, 400, 50);
        secondary.setVerticalLine(100, 0, 100, 400);
        secondary.setVerticalActive(true);
    }

    private FXMLController getController() {
        return controller;
    }

    private CrossHair getCrossHair(int index) {
        return switch (index) {
            case 0 -> primary;
            case 1 -> secondary;
            default -> throw new IllegalArgumentException("Unknown crosshair: " + index);
        };
    }

    private CrossHairLine getCrossHairLine(int index, Orientation orientation) {
        return getCrossHair(index).getLine(orientation);
    }

    public Collection<Line> getAllGraphicalLines() {
        return List.of(
                primary.getLine(Orientation.HORIZONTAL).getLine(),
                primary.getLine(Orientation.VERTICAL).getLine(),
                secondary.getLine(Orientation.HORIZONTAL).getLine(),
                secondary.getLine(Orientation.VERTICAL).getLine()
        );
    }

    public Double[] getPositions() {
        return getPositions(0);
    }

    public Double[] getPositions(int index) {
        CrossHair crossHair = getCrossHair(index);
        Double x = crossHair.getNullablePosition(Orientation.VERTICAL);
        Double y = crossHair.getNullablePosition(Orientation.HORIZONTAL);
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
        getCrossHairLine(index, orientation).setVisible(false);
        int iAxis = orientation == Orientation.HORIZONTAL ? 1 : 0;
        Axis axis = chart.getAxes().get(iAxis);
        double value = index == 1 ? axis.getLowerBound() : axis.getUpperBound();
        statusBar.setCrossText(orientation, index, value, true);
    }

    public void setStates(boolean h1, boolean v1, boolean h2, boolean v2) {
        primary.setHorizontalActive(h1);
        primary.setVerticalActive(v1);
        secondary.setHorizontalActive(h2);
        secondary.setVerticalActive(v2);
    }

    public void setAllStates(boolean value) {
        Stream.of(primary, secondary).forEach(c -> {
            c.setHorizontalActive(value && getController().getCrossHairState(c.getId(), Orientation.HORIZONTAL));
            c.setVerticalActive(value && getController().getCrossHairState(c.getId(), Orientation.VERTICAL));
        });

        if (!value) {
            hideAll();
        }
    }

    public void setState(int index, Orientation orientation, boolean value) {
        getCrossHairLine(index, orientation).setActive(value && getController().getCrossHairState(index, orientation));

        if (!value) {
            hide(index, orientation);
        }
    }

    public boolean getState(int index, Orientation orientation) {
        return getCrossHairLine(index, orientation).isActive();
    }

    public boolean isVisible(int index, Orientation orientation) {
        return getCrossHairLine(index, orientation).isDisplayed();
    }

    public void refresh() {
        SpectrumStatusBar statusBar = getController().getStatusBar();
        for (int index = 0; index < 2; index++) {
            for (Orientation orientation : Orientation.values()) {
                CrossHairLine line = getCrossHairLine(index, orientation);
                int iAxis = orientation == Orientation.HORIZONTAL ? 1 : 0;
                Axis axis = chart.getAxes().get(iAxis);
                if (!isInRange(index, line.getOrientation())) {
                    line.setVisible(false);
                    double value = index == 1 ? axis.getLowerBound() : axis.getUpperBound();
                    statusBar.setCrossText(orientation, index, value, true);
                } else if (line.isDisplayed()) {
                    draw(index, line.getOrientation());
                } else {
                    double value = index == 1 ? axis.getLowerBound() : axis.getUpperBound();
                    statusBar.setCrossText(orientation, index, value, true);
                }
                statusBar.setCrossTextRange(index, orientation, axis.getLowerBound(), axis.getUpperBound());
            }
        }
        chart.drawSlices();
    }

    public double getPosition(int index, Orientation orientation) {
        return getCrossHairLine(index, orientation).getPosition();
    }

    public double[] getVerticalPositions() {
        return new double[]{
                primary.getVerticalPosition(),
                secondary.getVerticalPosition()
        };
    }

    public void setLineColors(Color fillColor, Color primaryColor, Color secondaryColor) {
        if (primaryColor == null) {
            primaryColor = toBlackOrWhite(fillColor);
        }
        if (secondaryColor == null) {
            secondaryColor = primaryColor == Color.BLACK ? Color.RED : Color.MAGENTA;
        }
        primary.setColor(primaryColor);
        secondary.setColor(secondaryColor);
    }

    public void updatePosition(int index, Orientation orientation, double value) {
        getCrossHairLine(index, orientation).setPosition(value);
        refresh();
    }

    public void move(int index, Orientation orientation, double value) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (dataAttrs.isEmpty()) {
            return;
        }

        value = valueInRange(index, orientation, value);
        setPosition(index, orientation, value);
        draw(index, orientation);
        DatasetAttributes dataAttr = dataAttrs.get(0);
        int axisDim = orientation == Orientation.VERTICAL ? 0 : 1;
        String label = dataAttr.getLabel(axisDim);

        double position = getCrossHairLine(index, orientation).getPosition();
        updateAllCharts(chart, index, position, label);
        chart.drawSlices();
    }

    private void sync(int index, String dimLabel, double value) {
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
            getCrossHairLine(index, orientation).setPosition(value);
            draw(index, orientation);
            if (index == 0) {
                chart.drawSlices();
            }
        }
    }

    private boolean isInRange(int index, Orientation orientation) {
        double value = getCrossHairLine(index, orientation).getPosition();

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
        Axis axis = orientation == Orientation.HORIZONTAL ? yAxis : xAxis;
        value = axis.getValueForDisplay(value).doubleValue();
        getCrossHairLine(index, orientation).setPosition(value);
    }

    public boolean hasRegion() {
        boolean horizontalRegion = primary.isVerticalDisplayed() && secondary.isVerticalDisplayed();
        boolean verticalRegion = primary.isHorizontalDisplayed() && secondary.isHorizontalDisplayed();
        return horizontalRegion && (verticalRegion || chart.is1D());
    }

    public boolean hasState(String state) {
        boolean v0 = primary.isVerticalDisplayed();
        boolean v1 = secondary.isVerticalDisplayed();
        boolean h0 = primary.isHorizontalDisplayed();
        boolean h1 = secondary.isHorizontalDisplayed();

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
                double value = orientation == Orientation.HORIZONTAL ? crossHairLine.getStartY() : crossHairLine.getStartX();
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
            if (!primary.isHorizontalDisplayed()) {
                result[Orientation.HORIZONTAL.ordinal()] = 0;
            } else if (!secondary.isHorizontalDisplayed()) {
                result[Orientation.HORIZONTAL.ordinal()] = 1;
            } else if (closest[0] != -1) {
                result[Orientation.HORIZONTAL.ordinal()] = closest[0];
            }
            if (!primary.isVerticalDisplayed()) {
                result[Orientation.VERTICAL.ordinal()] = 0;
            } else if (!secondary.isVerticalDisplayed()) {
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

        if (line.isActive()) {
            double value = line.getPosition();
            getController().getStatusBar().setCrossText(orientation, index, value, false);
            updateMeasureBar(dataset, orientation);
            if (orientation == Orientation.HORIZONTAL) {
                value = yAxis.getDisplayPosition(value);
                line.setLine(xOrigin, value, xOrigin + width, value);
            } else {
                value = xAxis.getDisplayPosition(value);
                line.setLine(value, yOrigin, value, yOrigin - height);
            }

            line.setVisible(true);
        }
    }

    private void updateMeasureBar(DatasetBase dataset, Orientation orientation) {
        SpectrumMeasureBar measureBar = getController().getSpectrumMeasureBar();
        if (measureBar != null) {
            CrossHairLine primary = getCrossHairLine(0, orientation);
            CrossHairLine secondary = getCrossHairLine(1, orientation);
            Double value0 = primary.getNullablePosition();
            Double value1 = secondary.getNullablePosition();
            measureBar.setCrossText(chart, dataset, orientation, value0, value1);
        }
    }

    private static void updateAllCharts(PolyChart source, int index, double position, String dimLabel) {
        PolyChartManager.getInstance().getAllCharts().stream()
                .filter(c -> c != source)
                .forEach(c -> c.getCrossHairs().sync(index, dimLabel, position));
    }
}
