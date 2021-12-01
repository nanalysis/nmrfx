package org.nmrfx.utilities;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.Path;
import java.util.zip.*;
import java.util.Enumeration;

public class UnZipper extends SimpleFileVisitor<Path> {

    // Print information about
    // each type of file.
    final byte[] buffer;
    final File zipFile;
    final File destDir;
    final static int BUFFER_SIZE = 16384;

    public UnZipper(File destDir, String zipFileName) throws FileNotFoundException, IOException {
        buffer = new byte[16384];
        zipFile = new File(zipFileName);
        this.destDir = destDir;
    }

    public void unzip() throws IOException {
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipFile inFile = new ZipFile(zipFile);
        final Enumeration<? extends ZipEntry> entries = inFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File file = Path.of(destDir.getPath(), entry.getName()).toFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(inFile, entry, file.toString());
            } else {
                // if the entry is a directory, make the directory
                file.mkdirs();
            }
        }
        inFile.close();
    }

    private void extractFile(ZipFile inFile, ZipEntry zipEntry, String filePath) throws IOException {
        InputStream zipIn = inFile.getInputStream(zipEntry);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    public static void main(String[] argv) {
        File destDir = new File(argv[0]);
        try {
            UnZipper unzip = new UnZipper(destDir, argv[1]);
            unzip.unzip();
        } catch (FileNotFoundException fnfE) {
            System.out.println(fnfE.getMessage());
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

}
