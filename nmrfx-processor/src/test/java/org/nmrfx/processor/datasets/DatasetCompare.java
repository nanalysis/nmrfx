package org.nmrfx.processor.datasets;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

public class DatasetCompare {

    public static long compare(File refFile, File testFile) throws IOException {
        long refLen = refFile.length();
        long testLen = testFile.length();
        long result;

        if (refLen != testLen) {
            result = Math.min(refLen, testLen);
        } else {
            result = Files.mismatch(refFile.toPath(), testFile.toPath());
        }
        return result;
    }

    public static long compareFloat(File refFile, File testFile) throws IOException {
        long refLen = refFile.length();
        long testLen = testFile.length();
        long result = -1;

        if (refLen != testLen) {
            result = Math.min(refLen, testLen);
        } else {
            try (DataInputStream refStream = new DataInputStream(new FileInputStream(refFile));
                 DataInputStream testStream = new DataInputStream(new FileInputStream(testFile))) {
                int i = 0;
                while (refStream.available() > 0) {
                    if (Float.compare(refStream.readFloat(), testStream.readFloat()) != 0) {
                        result = i == 0? -1 : i;
                        break;
                    }
                    i++;
                }
            }
        }
        return result;
    }
}
