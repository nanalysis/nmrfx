package org.nmrfx.processor.gui.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A log appender that keeps event in memory, rotating them to avoid consuming all the memory.
 */
public class MemoryAppender extends AppenderBase<ILoggingEvent> {
    private static final int MAX_SIZE = 100;

    private final List<LogRecord> records = new LinkedList<>();
    private final List<LogListener> listeners = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
        if (records.size() >= MAX_SIZE) {
            records.remove(0);
        }

        LogRecord record = new LogRecord(event);
        records.add(record);
        listeners.forEach(listener -> listener.logPublished(record));
    }

    public void addLogListener(LogListener listener) {
        listeners.add(listener);
    }

    public void removeLogListener(LogListener listener) {
        listeners.remove(listener);
    }

    public List<LogRecord> getRecords() {
        return records;
    }

    public void clearRecords() {
        records.clear();
    }
}
