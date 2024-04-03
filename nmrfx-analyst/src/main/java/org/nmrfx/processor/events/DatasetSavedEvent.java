package org.nmrfx.processor.events;

import org.nmrfx.processor.datasets.DatasetType;

import java.nio.file.Path;

/**
 * Notification sent when a processed dataset is saved.
 */
public class DatasetSavedEvent {
    private final DatasetType type;
    private final Path path;

    public DatasetSavedEvent(DatasetType type, Path path) {
        this.type = type;
        this.path = path;
    }

    public DatasetType getType() {
        return type;
    }

    public Path getPath() {
        return path;
    }
}
