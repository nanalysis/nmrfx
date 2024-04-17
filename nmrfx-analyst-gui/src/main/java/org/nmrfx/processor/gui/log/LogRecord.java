package org.nmrfx.processor.gui.log;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Record of a log event.
 * This small wrapper is needed to avoid exposing implementation details.
 */
public class LogRecord {
    private final ILoggingEvent logbackEvent;
    private LogLevel level;
    private LocalDateTime time;

    public LogRecord(ILoggingEvent logbackEvent) {
        this.logbackEvent = logbackEvent;
    }

    public LogLevel getLevel() {
        if (level == null) {
            this.level = LogLevel.fromLogback(logbackEvent.getLevel());
        }

        return level;
    }

    public LocalDateTime getTime() {
        if (time == null) {
            time = LocalDateTime.ofInstant(Instant.ofEpochMilli(logbackEvent.getTimeStamp()), ZoneId.systemDefault());
        }

        return time;
    }

    public String getMessage() {
        return logbackEvent.getFormattedMessage();
    }

    public String getLoggerName() {
        return logbackEvent.getLoggerName();
    }

    /**
     * Gets the LogRecord's source method name from the caller data.
     *
     * @return A String of the caller's method name or an empty string if there is no caller data.
     */
    public String getSourceMethodName() {
        String sourceMethodName = "";
        if (logbackEvent.hasCallerData() && logbackEvent.getCallerData().length > 0) {
            sourceMethodName = logbackEvent.getCallerData()[0].getMethodName();
        }
        return sourceMethodName;
    }

    @Override
    public String toString() {
        return String.format("LogRecord{level=%s, time=%s, message=%s}", getLevel(), getTime(), getMessage());
    }
}
