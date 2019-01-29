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

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.nmrfx.processor.math.Vec;

/**
 *
 * @author Bruce Johnson
 */
public class DatasetMerger {

    public void merge(List<String> fileNames, String outFileName) throws IOException, DatasetException {
        Dataset outputDataset = null;
        int nInputFiles = fileNames.size();
        int iFile = 0;
        Vec inVec = null;
        int[] indices = null;
        int nDim = 0;
        for (String fileName : fileNames) {
            Dataset inputDataset = new Dataset(fileName, fileName, false);
            if (outputDataset == null) {
                nDim = inputDataset.getNDim();
                File outFile = new File(outFileName);
                int[] dimSizes = new int[nDim + 1];
                for (int i = 0; i < nDim; i++) {
                    dimSizes[i] = inputDataset.getSize(i);
                }
                dimSizes[nDim] = nInputFiles;
                Dataset.createDataset(outFileName, outFile.getName(), dimSizes);
                outputDataset = new Dataset(outFileName, outFileName, true);
                for (int i = 0; i < outputDataset.getNDim(); i++) {
                    outputDataset.setComplex(i, false);
                    outputDataset.syncPars(i);
                }
                outputDataset.setNFreqDims(nDim);
                inVec = new Vec(inputDataset.getSize(0));
                indices = new int[nDim];
            }
            if (nDim == 1) {
                inputDataset.readVector(inVec, 0, 0);
                indices[0] = iFile;
                outputDataset.writeVector(inVec, indices, 0);
            } else if (nDim == 2) {
                int nRows = inputDataset.getSize(1);
                for (int iRow = 0; iRow < nRows; iRow++) {
                    inputDataset.readVector(inVec, iRow, 0);
                    indices[0] = iRow;
                    indices[1] = iFile;
                    outputDataset.writeVector(inVec, indices, 0);
                }
                inputDataset.copyHeader(outputDataset, 0);
                inputDataset.copyHeader(outputDataset, 1);
            }
            iFile++;
        }
        if (outputDataset != null) {
            outputDataset.writeHeader();
            outputDataset.writeParFile();
            outputDataset.close();
        }
    }

}
