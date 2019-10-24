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

/**
 *
 * @author brucejohnson
 */
public abstract class SubMatrixFile {

     final int[] blockSize;
     final int[] nBlocks;
     final int[] offsetBlocks;
     final int[] offsetPoints;
     long blockElements;
     final long blockPoints;

    public SubMatrixFile(int[] sizes) {
        int nDim = sizes.length;
        blockSize = new int[nDim];
        nBlocks = new int[nDim];
        offsetBlocks = new int[nDim];
        offsetPoints = new int[nDim];
        setBlockSize(4096, nDim, sizes);
        dimDataset(nDim, sizes);
        blockPoints = blockElements / Float.BYTES;

    }

    final void dimDataset(int nDim, int[] sizes) {
        int iDim;

        blockElements = 4;

        for (iDim = 0; iDim < nDim; iDim++) {
            if ((sizes[iDim] == 0) || (blockSize[iDim] == 0)) {
                return;
            }
        }

        for (iDim = 0; iDim < nDim; iDim++) {
            nBlocks[iDim] = sizes[iDim] / blockSize[iDim];

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

        }
    }

    /**
     * Set the block sizes based on the specified size of a block.
     *
     * @param blockPoints the size of the block
     */
    public void setBlockSize(int blockPoints, int nDim, int[] sizes) {
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
