package org.nmrfx.utils;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class TableUtils {

    private TableUtils() {}

    /**
     * Copies the contents of a table, including the headers, to the clipboard as a tab separated string.
     * If no rows are selected or copyAll is true, then all rows will be copied. Otherwise only the
     * selected rows will be copied.
     * @param tableView The TableView to copy.
     * @param forceCopyAll If true, all rows will be copied regardless of selections
     */
    public static void copyTableToClipboard(TableView<?> tableView, boolean forceCopyAll) {
        List<Integer> rowsToCopy = new ArrayList<>(tableView.getSelectionModel().getSelectedIndices());
        if (rowsToCopy.isEmpty() || forceCopyAll) {
            rowsToCopy = IntStream.range(0, tableView.getItems().size()).boxed().toList();

        }
        List<String> headerColumns = tableView.getColumns().stream().map(TableColumn::getText).toList();
        StringBuilder tabSeparatedString = new StringBuilder();
        tabSeparatedString.append(String.join("\t", headerColumns)).append(System.lineSeparator());
        int lastColumnIndex = headerColumns.size() - 1;
        for(Integer rowIndex: rowsToCopy) {
            for (int colIndex = 0; colIndex < headerColumns.size(); colIndex++) {
                tabSeparatedString.append(tableView.getColumns().get(colIndex).getCellData(rowIndex));
                if (colIndex != lastColumnIndex) {
                    tabSeparatedString.append("\t");
                }
            }
            tabSeparatedString.append(System.lineSeparator());
        }
        Clipboard clipBoard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.PLAIN_TEXT, tabSeparatedString.toString());
        clipBoard.setContent(content);
    }
}
