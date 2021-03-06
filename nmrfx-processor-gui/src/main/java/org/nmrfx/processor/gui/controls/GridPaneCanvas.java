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
        GRID
    }

    FXMLController controller;
    final Canvas canvas;
    int nRows = 1;

    public GridPaneCanvas(FXMLController controller, Canvas canvas) {
        this.controller = controller;
        this.canvas = canvas;
        layoutBoundsProperty().addListener((ObservableValue<? extends Bounds> arg0, Bounds arg1, Bounds arg2) -> {
            if (arg2.getWidth() < 1.0 || arg2.getHeight() < 1.0) {
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
        Point2D result;
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
        Point2D result;
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
        int newRows;
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
        updateGrid();
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

    private void updateGrid() {
        int nChildren = getChildren().size();
        int nColumns = getColumns();
        disableCharts(true);
        int iChild = 0;

        for (int iRow = 0; iRow < nRows; iRow++) {
            for (int jColumn = 0; jColumn < nColumns; jColumn++) {
                if (iChild >= nChildren) {
                    break;
                }
                var node = getChildren().get(iChild);
                setRowIndex(node, iRow * 2);
                setRowSpan(node, 2);
                setColumnIndex(node, jColumn * 2);
                setColumnSpan(node, 2);
                iChild++;
            }
        }
        updateConstraints();
        disableCharts(false);
        layoutChildren();
    }

    public void addChart(PolyChart chart) {
        getChildren().add(chart);
        updateGrid();
    }

    public void addChart(int position, PolyChart chart) {
        if (position == 0) {
            getChildren().add(0, chart);
        } else {
            getChildren().add(chart);
        }
        updateGrid();
    }

    public void addCharts(int nRows, List<PolyChart> charts) {
        disableCharts(charts, true);
        getChildren().clear();
        getChildren().addAll(charts);
        setRows(nRows);
        updateGrid();
    }

    public void addChart(PolyChart chart, int chartColumn, int chartRow, int columnSpan, int rowSpan) {
        add(chart, chartColumn * 2, chartRow * 2, columnSpan * 2, rowSpan * 2);
    }

    public void updateConstraints() {
        disableCharts(true);
        int nColumns = 0;
        int nRows = 0;
        for (var node : getChildren()) {
            Integer column = getColumnIndex(node);
            Integer row = getRowIndex(node);
            if ((column == null) || (row == null)) {
                break;
            }
            Integer columnSpan = getColumnSpan(node);
            Integer rowSpan = getRowSpan(node);
            columnSpan = columnSpan == null ? 1 : columnSpan;
            rowSpan = rowSpan == null ? 1 : rowSpan;
            nColumns = Math.max(nColumns, column + columnSpan);
            nRows = Math.max(nRows, row + rowSpan );
         }
        if ((nColumns > 0) && (nRows > 0)) {
            int nChartColumns = nColumns / 2;
            int nChartRows = nRows / 2;
            getRowConstraints().clear();
            getColumnConstraints().clear();
            double[][] borderGrid = controller.prepareChildren(getRows(), getColumns());
            for (int i = 0; i < nChartColumns; i++) {
                ColumnConstraints borderConstraint = new ColumnConstraints();
                borderConstraint.setPrefWidth(borderGrid[0][i]);
                borderConstraint.setHgrow(Priority.NEVER);
                ColumnConstraints chartConstraint = new ColumnConstraints();
                chartConstraint.setHgrow(Priority.ALWAYS);
                getColumnConstraints().addAll(borderConstraint, chartConstraint);
            }
            for (int i = 0; i < nChartRows; i++) {
                RowConstraints borderConstraint = new RowConstraints();
                borderConstraint.setPrefHeight(borderGrid[2][i]);
                borderConstraint.setVgrow(Priority.NEVER);
                RowConstraints chartConstraint = new RowConstraints();
                chartConstraint.setVgrow(Priority.ALWAYS);
                getRowConstraints().addAll(borderConstraint, chartConstraint);
            }
        }
        disableCharts(false);
    }

    private void disableCharts(boolean state) {
        for (var node : getChildren()) {
            if (node instanceof PolyChart) {
                ((PolyChart) node).setChartDisabled(state);
            }
        }
    }


    private void disableCharts(List<PolyChart> nodes, boolean state) {
        for (var chart : nodes) {
            chart.setChartDisabled(state);
        }
    }

}
