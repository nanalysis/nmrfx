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
            String firstLine = firstLine(path).strip();
            if (ccpnLine(firstLine)) {
                type = "ccpn";
            } else {
                type = "csv";
            }
        } else if (fileName.endsWith(".peaks")) {
            type = "xeasy";
        } else {
            String firstLine = firstLine(path).strip();
            if (nmrPipeLine(firstLine)) {
                type = "nmrpipe";
            } else if (firstLine.startsWith("Assignment")) {
                type = "sparky_assign";
            } else if (firstLine.contains(",")) {
                type = "csv";
            }
        }

        return type;
    }

    boolean ccpnLine(String line) {
        //Number,#,Position F1,Position F2,Assign F1,Assign F2,Height,Volume,Line Width F1 (Hz),Line Width F2 (Hz),Merit,Details,Fit Method,Vol. Method
        String[] ccpnFields = {"Number", "Position F", "Height", "Volume"};
        boolean isCCPN = false;
        String[] fields = line.split(",");
        if (fields.length > 0) {
            for (String ccpnField : ccpnFields) {
                for (String field : fields) {
                    if (field.startsWith(ccpnField)) {
                        isCCPN = true;
                        break;
                    }
                }
                if (isCCPN) {
                    break;
                }
            }
        }
        return isCCPN;
    }

    boolean nmrPipeLine(String line) {
        String[] lineStarts = {"REMARK", "DATA", "VARS", "FORMAT"};
        boolean isPipe = false;
        for (String lineStart : lineStarts) {
            if (line.startsWith(lineStart)) {
                isPipe = true;
                break;
            }
        }
        return isPipe;
    }

    String firstLine(Path path) {
        String firstLine = "";
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            firstLine = fileReader.readLine().trim();
            while (firstLine.isBlank() || firstLine.startsWith("#")) {
                firstLine = fileReader.readLine().trim();
            }
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
        return firstLine;

    }

}
