/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.peaks.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

/**
 * @author brucejohnson
 */
public class PeakFileDetector extends FileTypeDetector {
    private static final Logger log = LoggerFactory.getLogger(PeakFileDetector.class);

    @Override
    public String probeContentType(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        String type = "txt";
        if (fileName.endsWith(".xpk2")) {
            type = "xpk2";
        } else if (fileName.endsWith(".xpk")) {
            type = "xpk";
        } else if (fileName.endsWith(".save")) {
            type = "sparky_save";
        } else if (fileName.endsWith(".csv")) {
            type = "csv";
        } else {
            String firstLine = firstLine(path).strip();
            if (firstLine.startsWith("Assignment")) {
                type = "sparky_assign";
            } else if (firstLine.contains(",")) {
                type = "csv";
            }
        }

        return type;
    }

    String firstLine(Path path) {
        String firstLine = "";
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            firstLine = fileReader.readLine();
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
        return firstLine;

    }

}
