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
package org.nmrfx.processor.gui.controls;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Transform;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;

import java.util.List;

/**
 * @author Bruce Johnson
 */
public class GridPaneCanvas extends GridPane {

    public enum ORIENTATION {
        VERTICAL,
        HORIZONTAL,
        GRID;
    }

    FXMLController controller;
    final Canvas canvas;
    int nRows = 1;

    public GridPaneCanvas(FXMLController controller, Canvas canvas) {
        this.controller = controller;
        this.canvas = canvas;
        layoutBoundsProperty().addListener((ObservableValue<? extends Bounds> arg0, Bounds arg1, Bounds arg2) -> {
            if (arg2.getWidth() == 0 || arg2.getHeight() == 0) {
                return;
            }
            requestLayout();
        });
    }

    public static ORIENTATION getOrientation(String name) {
        name = name.toUpperCase();
        if ("HORIZONTAL".startsWith(name)) {
            return ORIENTATION.HORIZONTAL;
        } else if ("VERTICAL".startsWith(name)) {
            return ORIENTATION.VERTICAL;
        } else if ("GRID".startsWith(name)) {
            return ORIENTATION.GRID;
        } else {
            throw new IllegalArgumentException("Invalid orientation: " + name);
        }
    }



    @Override
    public void layoutChildren() {
        super.layoutChildren();
        double width = getWidth();
        double height = getHeight();
        controller.resizeCanvases(width, height);
        GraphicsContext gC = canvas.getGraphicsContext2D();
        gC.clearRect(0, 0, width, height);
        if (controller.getBgColor() != null) {
            gC.setFill(controller.getBgColor());
            gC.fillRect(0, 0, width, height);
        }
        for (var child : getChildren()) {
            if (child instanceof PolyChart) {
                PolyChart chart = (PolyChart) child;
                chart.refresh();
            }
        }
    }


    Point2D getLocal(double x, double y) {
        Transform transform = getLocalToSceneTransform();
        Point2D result = null;
        try {
            Transform inverseTrans = transform.createInverse();
            result = inverseTrans.transform(x, y);
        } catch (NonInvertibleTransformException ex) {
            result = new Point2D(0.0, 0.0);
        }

        return result;
    }

    Point2D getFraction(double x, double y) {
        Transform transform = getLocalToSceneTransform();
        Point2D result = null;
        try {
            Transform inverseTrans = transform.createInverse();
            Point2D point = inverseTrans.transform(x, y);
            double width = getWidth();
            double height = getHeight();
            double fx = point.getX() / width;
            double fy = point.getY() / height;
            result = new Point2D(fx, fy);

        } catch (NonInvertibleTransformException ex) {
            result = new Point2D(0.0, 0.0);
        }

        return result;
    }

    public boolean setOrientation(ORIENTATION orient, boolean force) {
        int nChildren = getChildren().size();
        int newRows = 0;
        if (orient == ORIENTATION.VERTICAL) {
            newRows = nChildren;
        } else if (orient == ORIENTATION.HORIZONTAL) {
            newRows = 1;
        } else {
            if (nChildren < 3) {
                newRows = 1;
                setRows(1);
            } else if (nChildren < 9) {
                newRows = 2;
            } else if (nChildren < 16) {
                newRows = 3;
            } else {
                newRows = 4;
            }
        }
        if (force || (newRows != nRows)) {
            setRows(newRows);
            return true;
        } else {
            return false;
        }
    }

    public void setRows(int nRows) {
        this.nRows = nRows;
        updateConstraints();
    }

    public int getRows() {
        return nRows;
    }

    public int getColumns() {
        int nChildren = getChildren().size();
        int nColumns = nChildren / nRows;
        if (nColumns * nRows < nChildren) {
            nColumns++;
        }
        return nColumns;
    }

    public void updateConstraints() {
        double[][] borderGrid = controller.prepareChildren(nRows, getColumns());

        int nChildren = getChildren().size();
        int nColumns = getColumns();
        disableCharts(true);
        int iChild = 0;
        int borderExtra = 50;
        ColumnConstraints column1 = new ColumnConstraints();
        column1.setHgrow(Priority.ALWAYS);
        RowConstraints row1 = new RowConstraints();
        row1.setVgrow(Priority.ALWAYS);
        ColumnConstraints columnExtra = new ColumnConstraints();
        columnExtra.setPrefWidth(borderGrid[0][0]);
        columnExtra.setHgrow(Priority.NEVER);
        RowConstraints rowExtra = new RowConstraints();
        rowExtra.setPrefHeight(borderGrid[2][nRows -1]);
        rowExtra.setVgrow(Priority.NEVER);

        boolean minBorders = controller.getMinBorders();
        for (int iRow = 0; iRow < nRows; iRow++) {
            for (int jColumn = 0; jColumn < nColumns; jColumn++) {
                if (iChild >= nChildren) {
                    break;
                }
                var node = getChildren().get(iChild);
                int columnOffset = (minBorders && (jColumn != 0)) ? 1 : 0;
                int columnSpan = (minBorders && (jColumn == 0)) ? 2 : 1;
                int rowSpan = (minBorders && (iRow == (nRows-1))) ? 2 : 1;

                setRowIndex(node, iRow);
                setRowSpan(node, rowSpan);
                setColumnIndex(node, jColumn + columnOffset);
                setColumnSpan(node, columnSpan);
                iChild++;
            }
        }
        getRowConstraints().clear();
        getColumnConstraints().clear();
        for (int iRow = 0; iRow < nRows; iRow++) {
            getRowConstraints().add(row1);
        }
        if (minBorders) {
            getRowConstraints().add(rowExtra);
            getColumnConstraints().add(columnExtra);
        }
        for (int jColumn = 0; jColumn < nColumns; jColumn++) {
            getColumnConstraints().add(column1);
        }
        disableCharts(false);
        layoutChildren();
    }

    public void addChart(PolyChart chart) {
        getChildren().add(chart);
        updateConstraints();
    }

    public void addChart(int position, PolyChart chart) {
        if (position == 0) {
            getChildren().add(0, chart);
        } else {
            getChildren().add(chart);
        }
        updateConstraints();
    }

    private void disableCharts(boolean state) {
        for (var node : getChildren()) {
            if (node instanceof PolyChart) {
                ((PolyChart) node).setChartDisable(state);
            }
        }
    }


    private void disableCharts(List<PolyChart> nodes, boolean state) {
        for (var chart : nodes) {
            chart.setChartDisable(state);
        }
    }

    public void addCharts(int nRows, List<PolyChart> charts) {
        disableCharts(charts, true);
        getChildren().clear();
        getChildren().addAll(charts);
        setRows(nRows);
        disableCharts(charts, false);
        layoutChildren();
    }
}
