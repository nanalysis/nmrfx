package org.nmrfx.utilities;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;
import java.util.zip.*;

public class Zipper extends SimpleFileVisitor<Path> {

    // Print information about
    // each type of file.
    final byte[] buffer;
    final File zipFile;
    final FileOutputStream fos;
    final ZipOutputStream zos;
    final File startingDir;
    Pattern pattern = Pattern.compile("^\\d[ir]+$");

    public Zipper(File startingDir, String zipFileName) throws FileNotFoundException, IOException {
        buffer = new byte[16384];
        fos = new FileOutputStream(zipFileName);
        zos = new ZipOutputStream(fos);
        zipFile = new File(zipFileName);
        this.startingDir = startingDir;
    }

    public File getZipFile() {
        closeZip();
        return zipFile;
    }

    public void closeZip() {
        try {
            zos.close();
        } catch (IOException ioE) {
        }
    }

    private void writeEntry(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            ZipEntry ze = new ZipEntry(filePath.substring(startingDir.getAbsolutePath().length() + 1));
            zos.putNextEntry(ze);
            //read the file and write to ZipOutputStream
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            zos.closeEntry();
        } catch (IOException ioE) {
        }
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attr) {
        if (attr.isSymbolicLink()) {
        } else if (attr.isRegularFile()) {
            boolean ok = true;
            int nElems = path.getNameCount();
            if (nElems > 2) {
                if (path.getName(nElems - 2).toString().equals("pdata")) {
                    String fileName = path.getFileName().toString();
                    if (pattern.matcher(fileName).matches()) {
                        ok = false;
                    }
                }
            }

            if (ok) {
                writeEntry(path.toString());
            }
        } else {
        }
        return FileVisitResult.CONTINUE;
    }

    // Print each directory visited.
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        String filePath = dir.toString();
        int filePathLen = filePath.length();
        int startLen = startingDir.getAbsolutePath().length();
        if (startLen < filePathLen) {
            try {
                ZipEntry ze = new ZipEntry(filePath.substring(startingDir.getAbsolutePath().length() + 1, filePath.length()) + "/");
                zos.putNextEntry(ze);
                zos.closeEntry();
            } catch (IOException ioE) {
            }
        }
        return FileVisitResult.CONTINUE;
    }

    // If there is some error accessing
    // the file, let the user know.
    // If you don't override this method
    // and an error occurs, an IOException
    // is thrown.
    @Override
    public FileVisitResult visitFileFailed(Path file,
            IOException exc) {
        System.err.println(exc);
        return FileVisitResult.CONTINUE;
    }
}
