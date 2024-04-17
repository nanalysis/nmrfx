package org.nmrfx.processor.datasets;

import org.apache.commons.math3.util.MultidimensionalCounter;

import java.io.File;
import java.io.IOException;

public class DatasetUtilities {

    private DatasetUtilities() {

    }
    public static void extractIndirect(Dataset dataset, int index, String filePath) throws DatasetException, IOException {
        File file = new File(filePath);
        int nDimNew = dataset.getNDim() - 1;
        int[] newDims = new int[nDimNew];
        for (int i = 0; i < nDimNew; i++) {
            newDims[i] = dataset.getSizeTotal(i + 1);
        }
        Dataset newDataset = Dataset.createDataset(file.toString(), file.toString(), file.getName(), newDims, false, true);
        for (int i = 0; i < nDimNew; i++) {
            newDataset.setComplex(i, dataset.getComplex(i + 1));
            newDataset.setComplex(i, true);
            newDataset.setFreqDomain(i, dataset.getFreqDomain(i + 1));
            System.out.println(newDataset.getComplex(i )+ " " + newDataset.getFreqDomain(i));
            newDataset.setSw(i, dataset.getSw(i + 1));
            newDataset.setSf(i, dataset.getSf(i + 1));
            newDataset.setLabel(i, dataset.getLabel(i + 1));
            newDataset.setNucleus(i, dataset.getNucleus(i + 1));
            newDataset.setRefValue(i, dataset.getRefValue(i + 1));
        }
        newDataset.setNFreqDims(0);
        int[] point1 = new int[nDimNew + 1];
        point1[0] = index;
        MultidimensionalCounter counter = new MultidimensionalCounter(newDims);
        var iterator = counter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            for (int i=0;i< counts.length;i++) {
                point1[i + 1] = counts[i];
            }
            double value = dataset.readPointRaw(point1);
            newDataset.writePoint(counts, value);

        }
        if (newDataset.isMemoryFile()) {
            newDataset.saveMemoryFile();
        }
        newDataset.writeHeader();
        newDataset.writeParFile();
        newDataset.close();
    }
}
