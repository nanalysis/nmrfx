package org.nmrfx.processor.gui.utils;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    private FileUtils() {
    }

    /**
     * Checks if the new file has an extension and if it doesn't a new file is created from the file with the extension
     * added.
     *
     * @param file      The file to check the extension from
     * @param extension The string extension to add
     * @return A file
     */
    public static File addFileExtensionIfMissing(File file, String extension) {
        if (FilenameUtils.getExtension(file.toString()).isEmpty()) {
            file = new File(file + "." + extension);
            log.info("Updated the filename to: {}", file);
        }
        return file;
    }
}
