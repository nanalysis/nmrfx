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

import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.utilities.NvUtil;

import java.util.ArrayList;

public class ScanRegion {

    private int nDim = 3;
    private int[][] block;
    private int[][] iPoint;
    private int[] pointOffset;
    private int[] iPointStart;
    private int[] iVec;
    private int[] iBlock;
    private int[] iPointAbs = null;
    private final ArrayList<int[]> scanVector = new ArrayList<>(512);
    private int scanIn = 0;
    private int[][] pt = null;
    private int[] dim = null;
    private final int[] blockSize;
    private final int[] nBlocks;
    private int[] offsetBlocks;
    private final int[] vecRange;
    ArrayList<int[]> indexList = null;

    public ScanRegion(final int[][] pt, final int[] dim, final String datasetName) {
        nDim = dim.length;
        this.pt = pt.clone();
        for (int i = 0; i < pt.length; i++) {
            this.pt[i] = (int[]) pt[i].clone();
        }
        vecRange = new int[2];
        vecRange[0] = pt[0][0];
        vecRange[1] = pt[0][1];
        this.dim = dim.clone();
        Dataset dataset = Dataset.getDataset(datasetName);
        DatasetLayout layout = dataset.getLayout();
        nDim = dataset.getNDim();
        this.blockSize = new int[nDim];
        this.nBlocks = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            this.blockSize[i] = layout.getBlockSize(i);
            this.nBlocks[i] = layout.getNBlocks(i);
        }
        setup();
    }

    public ScanRegion(final int[][] pt, final int[] dim, final Dataset dataset) {
        nDim = dataset.getNDim();
        this.dim = dim.clone();
        this.pt = pt.clone();
        for (int i = 0; i < pt.length; i++) {
            this.pt[i] = (int[]) pt[i].clone();
        }
        vecRange = new int[2];
        vecRange[0] = pt[0][0];
        vecRange[1] = pt[0][1];
        DatasetLayout layout = dataset.getLayout();
        this.blockSize = new int[nDim];
        this.nBlocks = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            this.blockSize[i] = layout.getBlockSize(i);
            this.nBlocks[i] = layout.getNBlocks(i);
        }
        setup();
    }

    public ScanRegion(final int[][] pt, final int[] dim, final int[] blockSize, final int[] nBlocks) {
        this.dim = dim.clone();
        nDim = dim.length;
        this.pt = pt.clone();
        for (int i = 0; i < pt.length; i++) {
            this.pt[i] = (int[]) pt[i].clone();
        }
        vecRange = new int[2];
        vecRange[0] = pt[0][0];
        vecRange[1] = pt[0][1];
        this.blockSize = blockSize.clone();
        this.nBlocks = nBlocks;
        setup();
    }

    private void setup() {
        scanIn = 0;
        offsetBlocks = new int[nDim];
        block = new int[nDim][2];
        iPoint = new int[nDim][2];
        pointOffset = new int[nDim];
        iPointStart = new int[nDim];
        iVec = new int[nDim];
        iBlock = new int[nDim];
        for (int ii = 0; ii < nDim; ii++) {
            NvUtil.swap(pt[ii]);
        }
        pt[0][0] = 0;
        pt[0][1] = 0;
        pointOffset[0] = 1;
        offsetBlocks[0] = 1;

        for (int i = 0; i < nDim; i++) {
            block[i][0] = pt[i][0] / blockSize[dim[i]];
            block[i][1] = pt[i][1] / blockSize[dim[i]];
            iBlock[i] = block[i][0];
            if (i > 0) {
                pointOffset[i] = (pt[i - 1][1] - pt[i - 1][0] + 1) * pointOffset[i - 1];
                offsetBlocks[i] = offsetBlocks[i - 1] * nBlocks[i - 1];
            }
        }
    }

    void setupNextBlock() {
        int blockNum = 0;
        for (int i = 0; i < nDim; i++) {
            iPoint[i][0] = 0;

            if ((iPoint[i][0] + (iBlock[i] * blockSize[dim[i]])) < pt[i][0]) {
                iPoint[i][0] = pt[i][0] % blockSize[dim[i]];
            }

            iPoint[i][1] = blockSize[dim[i]] - 1;

            if ((iPoint[i][1] + (iBlock[i] * blockSize[dim[i]])) > pt[i][1]) {
                iPoint[i][1] = pt[i][1] % blockSize[dim[i]];
            }

            iPointStart[i] = iBlock[i] * blockSize[dim[i]];
            iVec[i] = iPoint[i][0];
            blockNum += (iBlock[i] * offsetBlocks[dim[i]]);
        }
    }

    void getBlockVectorIndexes() {
        //Loop over vectors in block
        scanIn = 0;
        scanVector.clear();
        while (true) {
            iVec[0] = iPoint[0][0];
            iPointAbs = new int[nDim];

            for (int i = 0; i < nDim; i++) {
                iPointAbs[i] = iPointStart[i] + iVec[i];
            }
            scanVector.add(iPointAbs);
            int i;
            for (i = 1; i < nDim; i++) {
                iVec[i]++;

                if (iVec[i] > iPoint[i][1]) {
                    iVec[i] = iPoint[i][0];
                } else {
                    break;
                }
            }

            if (i == nDim) {
                break;
            }
        }
    }

    boolean nextBlock() {
        scanVector.clear();
        boolean result = true;
        //Increment to next block
        int i;
        for (i = 0; i < nDim; i++) {
            iBlock[i]++;
            if (iBlock[i] > block[i][1]) {
                iBlock[i] = block[i][0];
            } else {
                break;
            }
        }

        if (i == nDim) {
            //True if exhausted all blocks
            result = false;
        }
        return result;
    }

    public final int[][] nextPoint2() {
        if (!scanVector.isEmpty() && (scanIn >= scanVector.size())) {
            if (!nextBlock()) {
                scanVector.clear();
                scanIn = 0;
                return new int[0][0];
            }
        }
        if (scanVector.isEmpty()) {
            setupNextBlock();
            getBlockVectorIndexes();
        }

        int[] myVec = scanVector.get(scanIn);
        int[][] result = new int[myVec.length][2];
        result[0] = new int[2];
        result[0][0] = vecRange[0];
        result[0][1] = vecRange[1];
        for (int i = 1; i < myVec.length; i++) {
            result[i][0] = myVec[i];
            result[i][1] = myVec[i];
        }

        scanIn++;
        return result;
    }

    public final int[] nextPoint() {
        if (!scanVector.isEmpty() && (scanIn >= scanVector.size())) {
            if (!nextBlock()) {
                scanVector.clear();
                scanIn = 0;
                return new int[0];
            }
        }
        if (scanVector.isEmpty()) {
            setupNextBlock();
            getBlockVectorIndexes();
        }

        int[] myVec = scanVector.get(scanIn);
        myVec[0] = -1;

        scanIn++;
        return myVec;
    }

    public void reset() {
        for (int i = 0; i < nDim; i++) {
            iBlock[i] = 0;
        }
        scanVector.clear();
    }

    public int buildIndex() {
        indexList = new ArrayList<>(16384);
        reset();
        while (true) {
            int[] nextPoint = nextPoint();
            if (nextPoint.length == 0) {
                break;
            }
            indexList.add(nextPoint);
        }
        return indexList.size();
    }

    public int[] getIndexEntry(final int i) {
        if (indexList == null) {
            buildIndex();
        }
        return indexList.get(i);
    }
}
