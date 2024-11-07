package org.nmrfx.utilities;

import java.io.*;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UnZipper {
    private static final int BUFFER_SIZE = 16384;
    private final File zipFile;
    private final File destDir;

    public UnZipper(File destDir, String zipFileName) throws IOException {
        zipFile = new File(zipFileName);
        this.destDir = destDir;
    }
    public UnZipper(File destDir, File zipFile) throws IOException {
        this.zipFile = zipFile;
        this.destDir = destDir;
    }

    public void unzip() throws IOException {
        if (!destDir.exists() && !destDir.mkdir()) {
            throw new IOException("Unable to create destination directory: " + destDir.getAbsolutePath());
        }

        try(ZipFile inFile = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = inFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                extractZipEntry(inFile, entry);
            }
        }
    }

    private void extractZipEntry(ZipFile inFile, ZipEntry entry) throws IOException {
        File file = Path.of(destDir.getPath(), entry.getName()).toFile();
        File parentDir = file.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Unable to create directory: " + parentDir.getAbsolutePath());
        }

        if (entry.isDirectory()) {
            // if the entry is a directory, make the directory
            if (!file.mkdirs()) {
                throw new IOException("Unable to create directory: " + file.getAbsolutePath());
            }
        } else {
            // if the entry is a file, extracts it
            extractFile(inFile, entry, file.toString());
        }
    }

    private void extractFile(ZipFile inFile, ZipEntry zipEntry, String filePath) throws IOException {
        try (InputStream zipIn = inFile.getInputStream(zipEntry);
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }
}
