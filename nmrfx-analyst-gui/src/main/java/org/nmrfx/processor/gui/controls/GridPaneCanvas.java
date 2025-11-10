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
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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
        double width = getWidth();
        double height = getHeight();
        disableCharts(true);

        GraphicsContext gC = canvas.getGraphicsContext2D();
        gC.clearRect(0, 0, width, height);
        if (controller.getBgColor() != null) {
            gC.setFill(controller.getBgColor());
            gC.fillRect(0, 0, width, height);
        }
        GridValue gridSize = getGridSize();
        int nChartColumns = gridSize.columns;
        int nChartRows = gridSize.rows;

        double[][] borderGrid = controller.prepareChildren(nChartRows, nChartColumns);

        double[] widths = new double[nChartColumns];
        double[] xStarts = new double[nChartColumns];
        double[] widthPercents = new double[nChartColumns];
        double[] heights = new double[nChartRows];
        double[] yStarts = new double[nChartRows];
        double[] heightPercents = new double[nChartRows];
        var columnConstraints = getColumnConstraints();
        var rowConstraints = getRowConstraints();
        double sumColumnBorders = 0.0;
        double sumColumnPercent = 0.0;
        for (int i=0;i<nChartColumns;i++) {
            widthPercents[i] = 100.0;
            widths[i] = borderGrid[0][i] + borderGrid[1][i];
            sumColumnBorders += widths[i];
            if (i < columnConstraints.size()) {
                widthPercents[i] = columnConstraints.get(i).getPercentWidth();
            }
            sumColumnPercent += widthPercents[i];
        }

        double sumRowBorders = 0.0;
        double sumRowPercent = 0.0;
        for (int i=0;i<nChartRows;i++) {
            heightPercents[i] = 100.0;
            heights[i] = borderGrid[2][i] + borderGrid[3][i];
            sumRowBorders += heights[i];
            if (i < rowConstraints.size()) {
                heightPercents[i] = rowConstraints.get(i).getPercentHeight();
            }
            sumRowPercent += heightPercents[i];
        }

        double flexWidth = width - sumColumnBorders;
        double columnNorm = sumColumnPercent >= 99.9 ? sumColumnPercent : 100.0;
        for (int i=0;i<nChartColumns;i++) {
            double coreWidth =  flexWidth * widthPercents[i] / columnNorm;
            System.out.println(i + " " + widths[i] + " " + coreWidth + " " + (widths[i] + coreWidth) + " " + widthPercents[i] + " " + (widthPercents[i] / columnNorm));
            widths[i] += coreWidth;
            if (i < nChartColumns-1) {
                xStarts[i + 1] = xStarts[i] + widths[i];
            }
        }
        double rowNorm = sumRowPercent >= 99.9 ? sumRowPercent : 100.0;

        double flexHeight = height - sumRowBorders;
        for (int i=0;i<nChartRows;i++) {
            heights[i] += flexHeight * heightPercents[i] / rowNorm;
            if (i < nChartRows-1) {
                yStarts[i + 1] = yStarts[i] + heights[i];
            }
        }

        layoutChildren(xStarts, widths, heights, yStarts);
    }

    private void layoutChildren(double[] xStarts, double[] widths, double[] heights, double[] yStarts) {
        double width;
        double height;
        for (var node : getChildren()) {
            Integer column = getColumnIndex(node);
            Integer row = getRowIndex(node);
            if ((column == null) || (row == null)) {
                break;
            }
            Integer columnSpan = getColumnSpan(node);
            Integer rowSpan = getRowSpan(node);
            System.out.println(node + " " + column + " " + xStarts[column] + " " + widths[column]);
            width = widths[column];
            for (int i=1;i < columnSpan;i++) {
                width += widths[column + i];
            }
            height = heights[row];
            for (int i=1;i < rowSpan;i++) {
                height += heights[row + i];
            }
            Region.layoutInArea(node, xStarts[column], yStarts[row], width, height, 0.0,null, true, true, HPos.CENTER, VPos.CENTER, true);
        }
        disableCharts(false);
        for (var child : getChildren()) {
            if (child instanceof PolyChart chart) {
                chart.refresh();
            }
        }

    }

    public boolean setOrientation(ORIENTATION orient, boolean force) {
        orientation = orient;
        int nChildren = getChildren().size();
        int newRows;
        switch (orient) {
            case ORIENTATION.VERTICAL -> {
                newRows = nChildren;
            }
            case ORIENTATION.HORIZONTAL -> {
                newRows = 1;
            }
            default -> {
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
                setRowIndex(node, iRow );
                setRowSpan(node, 1);
                setColumnIndex(node, jColumn );
                setColumnSpan(node, 1);
                iChild++;
            }
        }
        updateConstraints();
        disableCharts(false);
        layoutChildren();
    }

    public void setPosition(PolyChart node, int iRow, int jColumn, int rowSpan, int columnSpan) {
        setRowIndex(node, iRow );
        setRowSpan(node, rowSpan);
        setColumnIndex(node, jColumn);
        setColumnSpan(node, columnSpan);
        updateConstraints();
        disableCharts(false);
        layoutChildren();
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

    public record GridValue(int rows, int columns) {
    }

    public record GridPosition(int rows, int columns, int rowSpan, int columnSpan) {
    }

    public GridValue getGridSize() {
        int columns = 0;
        int rows = 0;
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
            columns = Math.max(columns, column + columnSpan);
            rows = Math.max(rows, row + rowSpan);
        }

        return new GridValue(rows, columns);

    }

    public GridPosition getGridLocation(PolyChart chart) {
        Integer column = getColumnIndex(chart);
        Integer row = getRowIndex(chart);
        Integer columnSpan = getColumnSpan(chart);
        columnSpan = columnSpan == null ? 1 : columnSpan;
        Integer rowSpan = getRowSpan(chart);
        rowSpan = rowSpan == null ? 1 : rowSpan;
        column = column == null ? 0 : column;
        row = row == null ? 0 : row;
        return new GridPosition(row, column, rowSpan, columnSpan);
    }

    public void gridColumn(int column, double percent) {
        var columnConstraints = getColumnConstraints();
        for (int i = columnConstraints.size();i< column+1;i++) {
            ColumnConstraints columnConstraint = new ColumnConstraints();
            columnConstraint.setPercentWidth(100.0);
        }
        ColumnConstraints columnConstraint = columnConstraints.get(column);
        columnConstraint.setPercentWidth(percent);
    }

    public void gridRow(int row, double percent) {
        var rowConstraints = getRowConstraints();
        for (int i = rowConstraints.size();i< row+1;i++) {
            RowConstraints rowConstraint = new RowConstraints();
            rowConstraint.setPercentHeight(100.0);
        }
        RowConstraints rowConstraint = rowConstraints.get(row);
        rowConstraint.setPercentHeight(percent);
    }

    public void updateConstraints() {
        disableCharts(true);
        GridValue gridSize = getGridSize();
        int nChartColumns = gridSize.columns;
        int nChartRows = gridSize.rows;
        if ((nChartColumns > 0) && (nChartRows > 0)) {
            var columnConstraints = getColumnConstraints();
            for (int i = 0; i < nChartColumns; i++) {
                if (i >= columnConstraints.size()) {
                    ColumnConstraints chartConstraint = new ColumnConstraints();
                    chartConstraint.setPercentWidth(100);
                    columnConstraints.add(chartConstraint);
                }
            }
            var rowConstraints = getRowConstraints();
            for (int i = 0; i < nChartRows; i++) {
                if (i >= rowConstraints.size()) {
                    RowConstraints chartConstraint = new RowConstraints();
                    chartConstraint.setPercentHeight(100.0);
                    rowConstraints.add(chartConstraint);
                }
            }
        }
        disableCharts(false);
    }

    private void disableCharts(boolean state) {
        for (var node : getChildren()) {
            if (node instanceof PolyChart chart) {
                chart.setChartDisabled(state);
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
