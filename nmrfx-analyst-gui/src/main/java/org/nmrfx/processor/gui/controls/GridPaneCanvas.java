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
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.util.converter.IntegerStringConverter;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * @author Bruce Johnson
 */
public class GridPaneCanvas extends GridPane {

    public enum ORIENTATION {
        VERTICAL,
        HORIZONTAL,
        GRID
    }

    private static final String GRID_DIM_VALID_INTEGER = "([1-9]|1[0-9]|20)";

    public record GridDimensions(Integer rows, Integer cols) {
    }

    FXMLController controller;
    final Canvas canvas;
    int nRows = 1;
    private ORIENTATION orientation;

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

    public static ORIENTATION parseOrientationFromString(String name) {
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

    public boolean setOrientation(ORIENTATION orient, boolean force) {
        orientation = orient;
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

    public ORIENTATION getOrientation() {
        return orientation;
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

    public void setPosition(PolyChart node, int iRow, int jColumn, int rowSpan, int columnSpan) {
        setRowIndex(node, iRow * 2);
        setRowSpan(node, rowSpan * 2);
        setColumnIndex(node, jColumn * 2);
        setColumnSpan(node, columnSpan * 2);

    }

    public void addChart(PolyChart chart) {
        getChildren().add(chart);
        updateGrid();
    }

    public void addCharts(int nRows, List<PolyChart> charts) {
        disableCharts(charts, true);
        getChildren().clear();
        getChildren().addAll(charts);
        setRows(nRows);
    }

    public void calculateAndSetOrientation() {
        if (nRows == 1) {
            orientation = ORIENTATION.HORIZONTAL;
        } else if (getColumns() == 1) {
            orientation = ORIENTATION.VERTICAL;
        } else {
            orientation = ORIENTATION.GRID;
        }
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
            nRows = Math.max(nRows, row + rowSpan);
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

    public static GridDimensions getGridDimensionInput() {
        Dialog<GridDimensions> dialog = new Dialog<>();
        dialog.setTitle("Grid");
        dialog.setHeaderText("Enter grid dimensions:");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        dialog.getDialogPane().setContent(grid);
        int comboBoxWidth = 60;
        ComboBox<Integer> comboBoxRows = new ComboBox<>(FXCollections.observableArrayList(IntStream.rangeClosed(1, 5).boxed().toList()));
        comboBoxRows.setValue(1);
        comboBoxRows.setMinWidth(comboBoxWidth);
        comboBoxRows.setMaxWidth(comboBoxWidth);
        comboBoxRows.setEditable(true);
        comboBoxRows.getEditor().setTextFormatter(new TextFormatter<>(GridPaneCanvas::gridDimFilter));
        comboBoxRows.setConverter(new IntegerStringConverter());
        grid.add(new Label("Number of rows"), 0, 0);
        grid.add(comboBoxRows, 1, 0);

        ComboBox<Integer> comboBoxCols = new ComboBox<>(FXCollections.observableArrayList(IntStream.rangeClosed(1, 5).boxed().toList()));
        comboBoxCols.setValue(1);
        comboBoxCols.setMinWidth(comboBoxWidth);
        comboBoxCols.setMaxWidth(comboBoxWidth);
        comboBoxCols.setEditable(true);
        comboBoxCols.getEditor().setTextFormatter(new TextFormatter<>(GridPaneCanvas::gridDimFilter));
        comboBoxCols.setConverter(new IntegerStringConverter());
        grid.add(new Label("Number of columns"), 0, 1);
        grid.add(comboBoxCols, 1, 1);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // The value set in the formatter may not have been set yet so commit the value before retrieving
                comboBoxRows.commitValue();
                comboBoxCols.commitValue();
                return new GridDimensions(comboBoxRows.getValue(), comboBoxCols.getValue());
            }
            return null;
        });

        GridDimensions gd = null;
        Optional<GridDimensions> result = dialog.showAndWait();
        if (result.isPresent()) {
            gd = result.get();
        }
        return gd;
    }

    private static TextFormatter.Change gridDimFilter(TextFormatter.Change change) {
        if (!change.getControlNewText().matches(GRID_DIM_VALID_INTEGER)) {
            change.setText("");
        }
        return change;
    }

}
