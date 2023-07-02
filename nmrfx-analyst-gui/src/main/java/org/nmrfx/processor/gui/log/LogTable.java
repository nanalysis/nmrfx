package org.nmrfx.processor.gui.log;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A filterable tableview for displaying LogRecords.
 */
public class LogTable extends TableView<LogRecord> {

    private static final String TIME_COLUMN = "Time";
    private static final String SECTION = "Section";
    private static final String MESSAGE = "Message";

    /* Keeps track of the records, new records are added to this list. */
    private final ObservableList<LogRecord> unfilteredRecords;
    /* Keeps track of the filtered view of unfilteredRecords, new filters should be added to this list. */
    private final FilteredList<LogRecord> filteredRecords;

    public LogTable() {
        unfilteredRecords = FXCollections.observableList(new ArrayList<>());
        filteredRecords = new FilteredList<>(unfilteredRecords);

        TableColumn<LogRecord, LogLevel> logLevelCol = new TableColumn<>();
        logLevelCol.setCellValueFactory(new PropertyValueFactory<>("level"));
        logLevelCol.setCellFactory(column -> new LogLevelCellFormatter());
        logLevelCol.setMinWidth(20);
        logLevelCol.setMaxWidth(20);
        getColumns().add(logLevelCol);

        TableColumn<LogRecord, LocalDateTime> logTimeCol = new TableColumn<>(TIME_COLUMN);
        logTimeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        logTimeCol.setCellFactory(column -> new LogTimeCellFactory());
        logTimeCol.setMinWidth(75);
        logTimeCol.setMaxWidth(75);
        getColumns().add(logTimeCol);

        TableColumn<LogRecord, String> logSectionCol = new TableColumn<>(SECTION);
        logSectionCol.setCellValueFactory(new PropertyValueFactory<>("loggerName"));
        logSectionCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String section, boolean empty) {
                super.updateItem(section, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(LogSection.fromLoggerNameString(section).getSectionName());
                }
            }
        });
        logSectionCol.setMinWidth(100);
        logSectionCol.setMaxWidth(100);
        getColumns().add(logSectionCol);

        TableColumn<LogRecord, String> logMessageCol = new TableColumn<>(MESSAGE);
        logMessageCol.setCellValueFactory(new PropertyValueFactory<>("message"));
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        logMessageCol.prefWidthProperty().bind(this.widthProperty().subtract(logLevelCol.widthProperty().get() +
                logTimeCol.widthProperty().get() + logSectionCol.widthProperty().get()));
        getColumns().add(logMessageCol);

        setItems(filteredRecords);
    }

    /**
     * Replaces the current list of records with the provided list.
     *
     * @param logRecords The new LogRecords
     */
    public void setLogRecords(List<LogRecord> logRecords) {
        unfilteredRecords.clear();
        unfilteredRecords.addAll(logRecords);
    }

    /**
     * Adds a new LogRecord to the table. If the current list of records in the table is larger than the
     * provided length, the starting records are removed until the records list + 1 (for the new record) is the size
     * of the provided length.
     *
     * @param logRecord     The new LogRecord to add
     * @param recordsLength The max length of records to display
     */
    public void addLogRecord(LogRecord logRecord, int recordsLength) {
        int sizeDifference = unfilteredRecords.size() - recordsLength;
        while (sizeDifference >= 0 && !unfilteredRecords.isEmpty()) {
            unfilteredRecords.remove(0);
            sizeDifference = unfilteredRecords.size() - recordsLength;
        }
        unfilteredRecords.add(logRecord);
    }

    public void setFilter(Predicate<LogRecord> filter) {
        filteredRecords.setPredicate(filter);
    }

}
