package org.nmrfx.utils;

import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.paint.Color;
import javafx.util.converter.DoubleStringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

public class TableUtils {

    private TableUtils() {
    }

    public static String getTableAsString(TableView<?> tableView, boolean forceCopyAll) {
        List<Integer> rowsToCopy = new ArrayList<>(tableView.getSelectionModel().getSelectedIndices());
        if (rowsToCopy.isEmpty() || forceCopyAll) {
            rowsToCopy = IntStream.range(0, tableView.getItems().size()).boxed().toList();

        }
        List<String> headerColumns = tableView.getColumns().stream().map(TableColumn::getText).toList();
        StringBuilder tabSeparatedString = new StringBuilder();
        tabSeparatedString.append(String.join("\t", headerColumns)).append(System.lineSeparator());
        int lastColumnIndex = headerColumns.size() - 1;
        for (Integer rowIndex : rowsToCopy) {
            for (int colIndex = 0; colIndex < headerColumns.size(); colIndex++) {
                Object value = tableView.getColumns().get(colIndex).getCellData(rowIndex);
                String s = value == null ? "" : value.toString();
                tabSeparatedString.append(s);
                if (colIndex != lastColumnIndex) {
                    tabSeparatedString.append("\t");
                }
            }
            tabSeparatedString.append(System.lineSeparator());
        }
        return tabSeparatedString.toString();
    }

    /**
     * Copies the contents of a table, including the headers, to the clipboard as a tab separated string.
     * If no rows are selected or copyAll is true, then all rows will be copied. Otherwise, only the
     * selected rows will be copied.
     *
     * @param tableView    The TableView to copy.
     * @param forceCopyAll If true, all rows will be copied regardless of selections
     */
    public static void copyTableToClipboard(TableView<?> tableView, boolean forceCopyAll) {
        String text = getTableAsString(tableView, forceCopyAll);
        Clipboard clipBoard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.PLAIN_TEXT, text);
        clipBoard.setContent(content);
    }


    public static <T> void addDatasetTextEditor(TableColumn<T, Double> doubleColumn, DoubleStringConverter dsConverter,
                                                BiConsumer<T, Double> applyValue) {
        doubleColumn.setCellFactory(tc -> new TextFieldTableCell<>(dsConverter));
        doubleColumn.setOnEditCommit(
                (TableColumn.CellEditEvent<T, Double> t) -> {
                    Double value = t.getNewValue();
                    if (value != null) {
                        T item = t.getRowValue();
                        applyValue.accept(item, value);
                    }
                });
        doubleColumn.setEditable(true);
    }

    public static <T> void addColorColumnEditor(TableColumn<T, Color> posColorCol, BiConsumer<T, Color> applyColor) {
        posColorCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Color item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || (item == null)) {
                    setGraphic(null);
                } else {
                    final ColorPicker cp = new ColorPicker();
                    cp.getStyleClass().add("button");
                    cp.setStyle("-fx-color-label-visible:false;");
                    cp.setValue(item);
                    setGraphic(cp);
                    cp.setOnAction(t -> {
                        getTableView().edit(getTableRow().getIndex(), column);
                        commitEdit(cp.getValue());
                    });
                }
            }

            @Override
            public void commitEdit(Color color) {
                super.commitEdit(color);
                T item = getTableRow().getItem();
                applyColor.accept(item, color);
            }
        });
    }

    public static <T> void addCheckBoxEditor(TableColumn<T, Boolean> posColorCol, BiConsumer<T, Boolean> applyValue) {
        posColorCol.setCellFactory(column -> new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || (item == null)) {
                    setGraphic(null);
                } else {
                    final CheckBox cp = new CheckBox();
                    cp.setSelected(item);
                    setGraphic(cp);
                    cp.setOnAction(t -> {
                        getTableView().edit(getTableRow().getIndex(), column);
                        commitEdit(cp.isSelected());
                    });

                }
            }

            @Override
            public void commitEdit(Boolean value) {
                super.commitEdit(value);
                T item = getTableRow().getItem();
                applyValue.accept(item, value);
            }
        });
    }

    /**
     * Formatter to change between Double and Strings in editable columns of Doubles
     */
    private static class DoubleColumnFormatter extends javafx.util.converter.DoubleStringConverter {
        String formatString;

        public DoubleColumnFormatter(int decimalPlaces) {
            formatString = "%." + decimalPlaces + "f";
        }

        @Override
        public String toString(Double object) {
            return String.format(formatString, object);
        }

        @Override
        public Double fromString(String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static DoubleColumnFormatter getDoubleColumnFormatter(int decimalPlaces) {
        return new DoubleColumnFormatter(decimalPlaces);
    }

}
