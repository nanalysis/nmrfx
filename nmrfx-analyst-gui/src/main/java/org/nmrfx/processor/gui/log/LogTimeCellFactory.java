package org.nmrfx.processor.gui.log;

import javafx.scene.control.TableCell;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


/**
 * Formatter for LocalDateTime table cells in a LogRecord table
 */
public class LogTimeCellFactory extends TableCell<LogRecord, LocalDateTime> {

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    protected void updateItem(LocalDateTime dateTime, boolean empty) {
        super.updateItem(dateTime, empty);
        if (empty) {
            setText(null);
        } else {
            setText(dateTimeFormatter.format(dateTime));
        }
    }
}
