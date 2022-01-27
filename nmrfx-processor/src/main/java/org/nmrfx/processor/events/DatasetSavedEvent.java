package org.nmrfx.processor.events;

import java.nio.file.Path;

/**
 * Notification sent when a processed dataset is saved.
 */
public class DatasetSavedEvent {
    private final String type;
    private final Path path;

    public DatasetSavedEvent(String type, Path path) {
        this.type = type;
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public Path getPath() {
        return path;
    }
}
