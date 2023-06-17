package org.nmrfx.processor.gui.log;

import ch.qos.logback.classic.Level;

import java.util.Arrays;

/**
 * All log levels. Wrapper to hide actual logger implementation.
 */
public enum LogLevel {
    OFF(Level.OFF),
    ERROR(Level.ERROR),
    WARNING(Level.WARN),
    INFO(Level.INFO),
    DEBUG(Level.DEBUG),
    TRACE(Level.TRACE),
    ALL(Level.ALL);

    private final Level logbackLevel;

    LogLevel(Level logbackLevel) {
        this.logbackLevel = logbackLevel;
    }

    Level getLogbackLevel() {
        return logbackLevel;
    }

    static LogLevel fromLogback(Level level) {
        return Arrays.stream(LogLevel.values())
                .filter(x -> x.getLogbackLevel() == level)
                .findFirst().orElse(INFO);
    }
}
