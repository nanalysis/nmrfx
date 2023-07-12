package org.nmrfx.processor.datasets;

import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public enum DatasetType {
    NMRFX(".nv"),
    UCSF(".ucsf"),
    SPINit("data.dat");

    String extension;

    DatasetType(String extension) {
        this.extension = extension;
    }

    /**
     * Determine the type of the file from the fileName.
     *
     * @param file the file to check
     * @return an Optional containing DatasetType if fileName is valid file or empty if not.
     */
    public static Optional<DatasetType> typeFromFile(File file) {
        if (file.isDirectory() && new File(file, RS2DData.DATA_FILE_NAME).isFile()) {
            return Optional.of(DatasetType.SPINit);
        }

        return typeFromName(file.getName());
    }

    /**
     * Determine the type of the file from the fileName.
     *
     * @param name the pathname string of the file to check
     * @return an Optional containing DatasetType if fileName is valid file or empty if not.
     */
    public static Optional<DatasetType> typeFromName(String name) {
        for (var ext : values()) {
            if (name.endsWith(ext.extension)) {
                return Optional.of(ext);
            }
        }
        return Optional.empty();
    }

    private String addExtension(String fileName) {
        if (typeFromName(fileName).isEmpty()) {
            fileName = fileName + extension;
        }
        return fileName;
    }

    /**
     * Adds, if not already present, the correct file extension for .nv and .ucsf files
     * or the filename (data.data) for SPINit files.
     *
     * @param file the file to check
     * @return the file with added extension (or SPINit fileName).
     */

    public File addExtension(File file) {
        String fileName = file.getName();
        if (this == SPINit) {
            if (!fileName.equals(extension)) {
                file = file.toPath().resolve(extension).toFile();
            }
        } else {
            fileName = addExtension(fileName);
            file = file.getParentFile().toPath().resolve(fileName).toFile();
        }
        return file;
    }

    public static List<String> names() {
        return Arrays.stream(values()).map(e -> e.toString()).collect(Collectors.toList());
    }
}
