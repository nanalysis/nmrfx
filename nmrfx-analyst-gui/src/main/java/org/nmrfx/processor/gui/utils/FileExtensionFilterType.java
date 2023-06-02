package org.nmrfx.processor.gui.utils;

import javafx.stage.FileChooser.ExtensionFilter;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;

import java.util.List;

/**
 * Enum to keep file chooser extension descriptions consistent for each set of extension filter.
 */
public enum FileExtensionFilterType {
    ALL_FILES("All Files", "*.*"),
    NMR_FID("NMR Fid", "fid", "ser", "*.nv", "*.dx", "*.jdx", "*.jdf", RS2DData.DATA_FILE_NAME),
    NMR_DATASET("NMR Dataset", "*.nv", "*.ucsf", "*.dx", "*.jdx", "1r", "2rr", "3rrr", "4rrrr", RS2DData.DATA_FILE_NAME),
    NMR_FILES("NMR Files", "fid", "ser", "*.nv", "*.ucsf", "*.dx", "*.jdx", "*.jdf", "1r", "2rr", "3rrr", "4rrrr", RS2DData.DATA_FILE_NAME),
    PDF("PDF", "*.pdf"),
    PNG("PNG", "*.png"),
    SVG("SVG", "*.svg"),
    TXT("TXT", "*.txt");

    private final String description;
    private final List<String> filters;

    FileExtensionFilterType(String description, String... filters) {
        this.description = description;
        this.filters = List.of(filters);
    }

    public ExtensionFilter getFilter() {
        return new ExtensionFilter(description, filters);
    }
}



