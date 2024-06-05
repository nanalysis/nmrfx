/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.datasets;

import org.nmrfx.processor.math.Vec;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class DatasetMerger {
    Dataset outputDataset = null;
    Vec inVec = null;
    int[] indices = null;
    int nDim = 0;

    void openOutput(Dataset inputDataset, int nInputFiles, File outFile) throws DatasetException {
        nDim = inputDataset.getNDim();
        int[] dimSizes = new int[nDim + 1];
        for (int i = 0; i < nDim; i++) {
            dimSizes[i] = inputDataset.getSizeTotal(i);
        }
        dimSizes[nDim] = nInputFiles;
        outputDataset = Dataset.createDataset(outFile.toString(), outFile.getName(), outFile.getName(), dimSizes, false, true);
        for (int i = 0; i < outputDataset.getNDim(); i++) {
            outputDataset.setComplex(i, false);
            outputDataset.syncPars(i);
        }
        outputDataset.setNFreqDims(nDim);
        inVec = new Vec(inputDataset.getSizeTotal(0));
        indices = new int[nDim];
    }

    void readInput(Dataset inputDataset, int iFile) throws IOException {
        if (nDim == 1) {
            inputDataset.readVector(inVec, 0, 0);
            indices[0] = iFile;
            outputDataset.writeVector(inVec, indices, 0);
        } else if (nDim == 2) {
            int nRows = inputDataset.getSizeTotal(1);
            for (int iRow = 0; iRow < nRows; iRow++) {
                inputDataset.readVector(inVec, iRow, 0);
                indices[0] = iRow;
                indices[1] = iFile;
                outputDataset.writeVector(inVec, indices, 0);
            }
            inputDataset.copyHeader(outputDataset, 0);
            inputDataset.copyHeader(outputDataset, 1);
        }

    }

    public void mergeFiles(List<String> fileNames, File outFile) throws IOException, DatasetException {
        int iFile = 0;
        int nInput = fileNames.size();
        for (String fileName : fileNames) {
            Dataset inputDataset = new Dataset(fileName, fileName, false, true, false);
            if (outputDataset == null) {
                openOutput(inputDataset, nInput, outFile);
            }
            readInput(inputDataset, iFile);
            iFile++;
        }
        if (outputDataset != null) {
            outputDataset.writeHeader();
            outputDataset.writeParFile();
            outputDataset.close();
        }
    }

    public void mergeDatasets(List<Dataset> inputDatasets, File outFile) throws IOException, DatasetException {
        int iFile = 0;
        int nInput = inputDatasets.size();
        for (Dataset inputDataset : inputDatasets) {
            if (outputDataset == null) {
                openOutput(inputDataset, nInput, outFile);
            }
            readInput(inputDataset, iFile);
            iFile++;
        }
        if (outputDataset != null) {
            outputDataset.writeHeader();
            outputDataset.writeParFile();
            outputDataset.close();
        }
    }

}
