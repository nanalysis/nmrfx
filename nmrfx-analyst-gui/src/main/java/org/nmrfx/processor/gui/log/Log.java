package org.nmrfx.processor.gui.log;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

/**
 * Utility class to manipulate logging framework at runtime, and hide implementation details.
 */
public class Log {
    private static final String LOGBACK_CONFIGURATION_FILE_PROPERTY = "logback.configurationFile";
    private static MemoryAppender memoryAppender;

    private static final Map<String, LogLevel> updatedLoggers = new HashMap<>();

    /**
     * Specify where to find the log configuration file.
     *
     * @param path the path to the configuration file
     */
    public static void setConfigFile(String path) {
        System.setProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY, path);
    }

    /**
     * Check if the log configuration file has already been set by a system property
     *
     * @return true if the log configuration property has been set.
     */
    public static boolean isConfigFileSet() {
        return System.getProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY) != null;
    }

    /**
     * Adds a memory appender to store log messages.
     * This can be used to display the latest logs in a graphical console.
     */
    public static synchronized void setupMemoryAppender() {
        if (memoryAppender != null) {
            return; // already initialized
        }

        memoryAppender = new MemoryAppender();

        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext) {
            LoggerContext context = (LoggerContext) factory;
            memoryAppender.setContext(context);
            memoryAppender.start();
            context.getLogger(ROOT_LOGGER_NAME).addAppender(memoryAppender);
        }
    }

    /**
     * Add a listener to be notified each time a new log record is stored in the memory appender.
     * This can be used to refresh a graphical console when new messages should be displayed.
     *
     * @param listener a listener
     */
    public static void addLogListener(LogListener listener) {
        if (memoryAppender == null) {
            throw new IllegalStateException("Trying to add a log listener before memory appender was initialized!");
        }

        memoryAppender.addLogListener(listener);
    }

    public static void removeLogListener(LogListener listener) {
        if (memoryAppender == null) {
            throw new IllegalStateException("Trying to remove a log listener before memory appender was initialized!");
        }

        memoryAppender.removeLogListener(listener);
    }

    /**
     * Get a non-modifiable list of records, stored in memory.
     *
     * @return a list of log records.
     */
    public static List<LogRecord> getRecordsFromMemory() {
        if (memoryAppender == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(memoryAppender.getRecords());
    }

    public static void clearRecordsFromMemory() {
        if (memoryAppender != null) {
            memoryAppender.clearRecords();
        }
    }

    /**
     * Change a log level dynamically.
     *
     * @param packageName all loggers which inherits from this package will be affected
     * @param level       the new level for this package
     */
    public static void setLogLevel(String packageName, LogLevel level) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext) {
            LoggerContext context = (LoggerContext) factory;
            context.getLogger(packageName).setLevel(level.getLogbackLevel());
            updatedLoggers.put(packageName, level);
        }
    }

    /**
     * Gets a copy of the updateLoggers.
     *
     * @return A SortedMap of logger name to LogLevel pairings.
     */
    public static SortedMap<String, LogLevel> getModifiedLogLevels() {
        return new TreeMap<>(updatedLoggers);
    }
}
