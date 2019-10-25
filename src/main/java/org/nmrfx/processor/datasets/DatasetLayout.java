/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets;

/**
 *
 * @author brucejohnson
 */
class DatasetLayout {

    int fileHeaderSize;
    int blockHeaderSize;
    int totalBlocks;
    int nDim;
    int[] sizes;
    int[] blockSize;
    int[] nBlocks;
    int[] offsetBlocks;
    int[] offsetPoints;
    long blockElements;
    long blockPoints;
    boolean subMatrix = false;

    DatasetLayout(int nDim) {
        resize(nDim);
    }

    DatasetLayout(int[] sizes) {
        resize(sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            this.sizes[i] = sizes[i];
        }
    }

    public final void resize(int nDim) {
        this.nDim = nDim;
        sizes = new int[nDim];
        blockSize = new int[nDim];
        nBlocks = new int[nDim];
        offsetBlocks = new int[nDim];
        offsetPoints = new int[nDim];
    }

    public static DatasetLayout createFullMatrix(int[] sizes) {
        DatasetLayout layout = new DatasetLayout(sizes);
        layout.setFileHeaderSize(0);
        layout.setBlockHeaderSize(0);
        layout.blockPoints = 1;
        layout.totalBlocks = 1;
        for (int i = 0; i < sizes.length; i++) {
            layout.blockSize[i] = sizes[i];
            layout.nBlocks[i] = 1;
            layout.blockPoints *= layout.blockSize[i];
        }
        layout.subMatrix = false;
        return layout;
    }

    public boolean isSubMatrix() {
        return subMatrix;
    }
    
    public long getTotalBlocks() {
        return totalBlocks;
    }

    public long getNPoints() {
        int nPoints = 1;
        for (int i = 0; i < sizes.length; i++) {
            nPoints *= sizes[i];
        }
        return nPoints;
    }

    public long getNDataBytes() {
        return getNPoints() * Float.BYTES;
    }

    public int getSize(int i) {
        return sizes[i];
    }

    public void setSize(int i, int value) {
        sizes[i] = value;
    }

    public int getBlockSize(int i) {
        return blockSize[i];
    }

    public void setBlockSize(int i, int value) {
        blockSize[i] = value;
    }

    public int getNBlocks(int i) {
        return nBlocks[i];
    }

    public int getOffsetPoints(int i) {
        return offsetPoints[i];
    }

    public int getOffsetBlocks(int i) {
        return offsetBlocks[i];
    }

    /**
     * @return the fileHeaderSize
     */
    public int getFileHeaderSize() {
        return fileHeaderSize;
    }

    /**
     * @return the blockHeaderSize
     */
    public int getBlockHeaderSize() {
        return blockHeaderSize;
    }

    /**
     * @param fileHeaderSize the fileHeaderSize to set
     */
    public void setFileHeaderSize(int fileHeaderSize) {
        this.fileHeaderSize = fileHeaderSize;
    }

    /**
     * @param blockHeaderSize the blockHeaderSize to set
     */
    public void setBlockHeaderSize(int blockHeaderSize) {
        this.blockHeaderSize = blockHeaderSize;
    }

    /**
     * @return the blockElements
     */
    public long getBlockElements() {
        return blockElements;
    }

    /**
     * @return the blockPoints
     */
    public long getBlockPoints() {
        return blockPoints;
    }

    final void dimDataset() {

        int iDim;

        blockElements = 4;

        for (iDim = 0; iDim < nDim; iDim++) {
            if ((sizes[iDim] == 0) || (blockSize[iDim] == 0)) {
                return;
            }
        }
        totalBlocks = 1;
        for (iDim = 0; iDim < nDim; iDim++) {
            nBlocks[iDim] = sizes[iDim] / blockSize[iDim];
            if (nBlocks[iDim] != 1) {
                subMatrix = true;
            }

            if ((blockSize[iDim] * nBlocks[iDim]) < sizes[iDim]) {
                nBlocks[iDim] += 1;
            }

            if (iDim > 0) {
                offsetBlocks[iDim] = nBlocks[iDim - 1] * offsetBlocks[iDim
                        - 1];
                offsetPoints[iDim] = blockSize[iDim - 1] * offsetPoints[iDim
                        - 1];
            } else {
                offsetBlocks[iDim] = 1;
                offsetPoints[iDim] = 1;
            }

            blockElements = blockElements * blockSize[iDim];
            totalBlocks *= nBlocks[iDim];
        }
        blockPoints = blockElements / 4;
    }

    /**
     * Set the block sizes based on the specified size of a block.
     *
     * @param blockPoints the size of the block
     */
    public void setBlockSize(int blockPoints) {
        System.out.println("set block size " + blockPoints);
        this.blockPoints = blockPoints;
        long npoints;
        int blksize;
        int blkspdim;
        int blog;
        int[] tsize;
        int[] nbdim;
        int[] bsize;
        int i;
        int j;
        int nblks;
        int imin = 0;
        int imax = 0;
        int bmax;
        int bmin;
        npoints = 1;
        tsize = new int[nDim];
        bsize = new int[nDim];
        nbdim = new int[nDim];

        //Calculate total points in file
        for (i = 0; i < nDim; i++) {
            tsize[i] = sizes[i];
            blog = (int) ((Math.log((double) tsize[i]) / Math.log(2.0)) + 0.5);
            bsize[i] = (int) (Math.exp(blog * Math.log(2.0)) + 0.5);

            if (bsize[i] < tsize[i]) {
                bsize[i] *= 2;
            }

            npoints *= bsize[i];
        }

        /*
         * Use blockPoints as number of points/block.
         */
        nblks = (int) (npoints / blockPoints);
        if ((nblks * blockPoints) < npoints) {
            nblks++;
        }

        /*
         * printf("nblks %d\n",nblks);
         */
 /*
         * With small files block size is dim size
         */
        if (nblks < 2) {
            for (i = 0; i < nDim; i++) {
                blockSize[i] = sizes[i];
            }

            return;
        }

        /*
         * Calculate trial blocks per dim based on total number of blocks and
         * the number of dimensions.
         */
        blkspdim = (int) (Math.exp((1.0 / nDim) * Math.log(
                (double) nblks)));
        blog = (int) ((Math.log((double) blkspdim) / Math.log(2.0)) + 0.5);
        blkspdim = (int) (Math.exp(blog * Math.log(2.0)) + 0.5);

        for (i = 0; i < nDim; i++) {
            nbdim[i] = blkspdim;

            if (nbdim[i] > bsize[i]) {
                nbdim[i] = bsize[i];
            }
        }

        /*
         * Adjust trial block sizes for each dimension so that the number of
         * points per block is blockPoints. If too big, divide the dimension
         * with largest block size in half. If too small, double the size of
         * dimension with largest block size.
         */
        for (j = 0; j < 2; j++) {
            blksize = 1;
            bmax = 0;
            bmin = 100000;

            for (i = 0; i < nDim; i++) {
                if ((bsize[i] / nbdim[i]) > 0) {
                    blksize *= (bsize[i] / nbdim[i]);
                }

                if (bsize[i] == 1) {
                    continue;
                }

                if (nbdim[i] > bmax) {
                    bmax = nbdim[i];
                    imax = i;
                }

                if (nbdim[i] < bmin) {
                    bmin = nbdim[i];
                    imin = i;
                }
            }

            /*
             * ConsoleWrite( "%d\n", blksize);
             */
            if (blksize > blockPoints) {
                nbdim[imax] = nbdim[imax] * 2;
            }

            if (blksize < blockPoints) {
                nbdim[imax] = nbdim[imax] / 2;
            }

            if (nbdim[imin] < 2) {
                nbdim[imin] = 2;
                nbdim[imax] = nbdim[imax] / 2;
            }
        }

        for (i = 0; i < nDim; i++) {
            blockSize[i] = bsize[i] / nbdim[i];
        }
    }

}
