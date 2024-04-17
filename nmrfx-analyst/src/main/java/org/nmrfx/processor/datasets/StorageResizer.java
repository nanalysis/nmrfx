package org.nmrfx.processor.datasets;

import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.datasets.DatasetStorageInterface;
import org.nmrfx.processor.processing.Processor;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class StorageResizer {

    private StorageResizer() {

    }

    public static DatasetStorageInterface resizeDim(Dataset dataset, DatasetLayout sourceLayout, DatasetStorageInterface source, int[] newSizes) throws IOException {
        DatasetStorageInterface target = null;
        DatasetLayout targetLayout = null;
        Path targetPath = null;
        File origFile = null;
        if (source instanceof MemoryFile) {
            targetLayout = DatasetLayout.createFullMatrix(0, newSizes);
            long nPoints = targetLayout.getNPoints();
            if (Processor.useMemoryMode(nPoints * Float.BYTES)) {
                target = new MemoryFile(dataset, targetLayout, true);
                target.zero();
            }
        }
        if (target == null) {
            String fullName;
            if (dataset.getFile() == null) {
                File file = new File(dataset.getFileName());
                dataset.setFile(file);
                fullName = dataset.getCanonicalFile();
            } else {
                fullName = dataset.getCanonicalFile() + ".tmp";
                origFile = dataset.getFile();
            }
            File file = new File(fullName);
            targetPath = file.toPath();
            RandomAccessFile raFile = new RandomAccessFile(fullName, "rw");
            targetLayout = DatasetLayout.createBlockMatrix(dataset.getFileHeaderSize(file.getName()), newSizes);
            target = Dataset.createDataFile(dataset, raFile, file, targetLayout, true);
            target.zero();
            target.force();
        }
        copyTo(sourceLayout, source, target);
        if ((targetPath != null) && (origFile != null)) {
            source.close();
            Files.move(targetPath, origFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            target.writeHeader(true);
        }
        return target;
    }

    public static void copyTo(DatasetLayout sourceLayout, DatasetStorageInterface source, DatasetStorageInterface target) throws IOException {
        int nDim = sourceLayout.nDim;
        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            counterSizes[i] = sourceLayout.getSize(i);
        }
        DimCounter counter = new DimCounter(counterSizes);
        for (int[] counts : counter) {
            float value = source.getFloat(counts);
            target.setFloat(value, counts);
        }
        target.force();
    }

}
