package org.nmrfx.processor.gui.log;

/**
 * Listener to be used to refresh a log console when new logs are written.
 */
@FunctionalInterface
public interface LogListener {
    void logPublished(LogRecord record);
}
