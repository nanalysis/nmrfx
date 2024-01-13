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
package org.nmrfx.processor.processing;

import org.apache.commons.math3.util.MultidimensionalCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 * <p>
 * The MultiVecCounter class converts an index (from 0 to nGroups-1) to a
 * VecIndex object that stores the input positions in FID at which to read the
 * vectors and the output positions in the dataset at which to write the
 * vectors. A group represents all the vectors that have the same time value in
 * the indirect dimensions.
 */
public class MultiVecCounter {
    private static final Logger log = LoggerFactory.getLogger(MultiVecCounter.class);

    int[] osizes;
    int[] isizes;
    int[] inPhases;
    int[] inPoints;
    int[] outPhases;
    int[] outPoints;
    int[] swap;
    int groupSize = 1;
    int nDim;
    int datasetNDim;
    MultidimensionalCounter outCounter;
    MultidimensionalCounter inCounter;
    MultidimensionalCounter.Iterator iterator;

    /**
     * Construct a new MultiVecCounter with specified parameters describing
     * input and output data.
     *
     * @param tdSizes     an array of integers representing the size of the input
     *                    data in each dimension. Output data sizes are set equal to the input data
     *                    sizes.
     * @param complex     an array of booleans representing whether the input FID is
     *                    complex in each dimension.
     * @param modes       an array of string values representing the order in which
     *                    data was acquired. The first character of each mode is either a 'p',
     *                    representing phase information, or 'd' representing time delay. The
     *                    second character represents the dimension, with '1' representing the
     *                    first indirect dimension. For example, "p1","p2","d1","d2" represents a
     *                    typical Agilent 3D dataset with array value = "phase2,phase" and
     *                    "p1","d1","p2","d2" would represent a typical Bruker 3D dataset.
     * @param datasetNDim number of dimensions in final dataset, could be
     *                    smaller than original data dimensions.
     */
    public MultiVecCounter(int[] tdSizes, int[] groupSizes, boolean[] complex, String[] modes, int datasetNDim) {
        nDim = tdSizes.length;
        osizes = new int[(nDim - 1) * 2];
        isizes = new int[(nDim - 1) * 2];
        this.datasetNDim = datasetNDim;
        init(tdSizes, tdSizes, groupSizes, complex, modes, null);
    }

    /**
     * Construct a new MultiVecCounter with specified parameters describing
     * input and output data.
     *
     * @param tdSizes     an array of integers representing the size of the input
     *                    data in each dimension
     * @param outSizes    an array of integers representing the size of the output
     *                    dataset in each dimension
     * @param groupSizes  an array of integers representing the number of input rows used for each dimension
     *                    typically 2 for phase-sensitive data (hyper complex or echo-antiecho) or 1 otherwise.
     * @param oComplex    an array of booleans representing whether the output dataset is
     *                    complex in each dimension.
     * @param modes       an array of string values representing the order in which
     *                    data was acquired. The first character of each mode is either a 'p',
     *                    representing phase information, or 'd' representing time delay. The
     *                    second character represents the dimension, with '1' representing the
     *                    first indirect dimension. For example, "p1","p2","d1","d2" represents a
     *                    typical Agilent 3D dataset with array value = "phase2,phase" and
     *                    "p1","d1","p2","d2" would represent a typical Bruker 3D dataset.
     * @param swapIn      an array of integers indicating input dimensions to swap to output dimensions
     * @param datasetNDim number of dimensions in final dataset, could be
     *                    smaller than original data dimensions.
     */
    public MultiVecCounter(int[] tdSizes, int[] outSizes, int[] groupSizes, boolean[] oComplex, String[] modes, int[] swapIn, int datasetNDim) {
        nDim = tdSizes.length;
        osizes = new int[(nDim - 1) * 2];
        isizes = new int[(nDim - 1) * 2];
        this.datasetNDim = datasetNDim;
        init(tdSizes, outSizes, groupSizes, oComplex, modes, swapIn);
    }

    void init(int[] tdSizes, int[] outSizes, int[] groupSizes, boolean[] oComplex, String[] modes, int[] swapIn) {
        int nIDim = tdSizes.length - 1;  // number of indirect dimensions

        // the index of the values in the multi-dimensional counter that references the phase increment
        //  of the input data
        inPhases = new int[nIDim];
        // the index of the values in the multi-dimensional counter that references the time increment 
        //  of the input data
        inPoints = new int[nIDim];

        // the index of the values in the multi-dimensional counter that references the phase increment
        //  of the output dataset
        outPhases = new int[nIDim];
        // the index of the values in the multi-dimensional counter that references the time increment 
        //  of the output data
        outPoints = new int[nIDim];
        int iArg = 0;
        groupSize = 1;
        if (swapIn == null) {
            swapIn = new int[nIDim + 1];
            for (int i = 0; i < nIDim + 1; i++) {
                swapIn[i] = i;
            }
        }
        swap = swapIn.clone();

        for (String mode : modes) {
            // dim is the indirect dimension index running from 1 (for indirect dim 1, 2nd dim) up
            int dim = Integer.parseInt(mode.substring(1));
            // argIndex runs backwards from 2*niDim-1 downto 0
            // so for a 3D file it would be 3,2,1,0

            int argIndex = 2 * nIDim - 1 - iArg;
            if (mode.charAt(0) == 'd') {
                inPoints[dim - 1] = argIndex;
                isizes[argIndex] = tdSizes[dim];
            } else if (mode.charAt(0) == 'p') {
                inPhases[dim - 1] = argIndex;
                isizes[argIndex] = groupSizes[dim];
                groupSize *= groupSizes[dim];
            } else if (mode.charAt(0) == 'a') {
                if (inPhases.length >= dim) {
                    inPhases[dim - 1] = argIndex;
                    inPoints[dim - 1] = argIndex - 1;
                    isizes[argIndex] = 1;
                    isizes[argIndex - 1] = tdSizes[dim];
                    iArg++;
                }
            } else {
                throw new IllegalArgumentException("bad mode " + mode);
            }
            iArg++;
        }

        for (int i = 0; i < nIDim; i++) {
            outPhases[i] = 2 * nIDim - 1 - i;
            outPoints[i] = nIDim - 1 - i;
            osizes[nIDim - 1 - i] = outSizes[i + 1];
        }
        for (int i = 0; i < nIDim; i++) {
            if (oComplex[i + 1]) {
                osizes[2 * nIDim - 1 - i] = groupSizes[i + 1];
            } else {
                osizes[2 * nIDim - 1 - i] = 1;
            }
        }

        if (log.isDebugEnabled()) {
            var sBuilder = new StringBuilder();

            sBuilder.append("  MultiVecCounter: \n");
            for (int i = 0; i < outPhases.length; i++) {
                sBuilder.append("ouPh[").append(i).append("]=").append(outPhases[i]).append(" ");
            }
            sBuilder.append('\n');


            for (int i = 0; i < outPoints.length; i++) {
                sBuilder.append("ouPt[").append(i).append("]=").append(outPoints[i]).append(" ");
            }
            sBuilder.append('\n');

            for (int i = 0; i < inPhases.length; i++) {
                sBuilder.append("inPh[").append(i).append("]=").append(inPhases[i]).append(" ");
            }
            sBuilder.append('\n');

            for (int i = 0; i < inPoints.length; i++) {
                sBuilder.append("inPt[").append(i).append("]=").append(inPoints[i]).append(" ");
            }
            sBuilder.append('\n');

            for (int i = 0; i < isizes.length; i++) {
                sBuilder.append("inSz[").append(i).append("]=").append(isizes[i]).append(" ");
            }
            sBuilder.append('\n');
            for (int i = 0; i < osizes.length; i++) {
                sBuilder.append("ouSz[").append(i).append("]=").append(osizes[i]).append(" ");
            }
            sBuilder.append('\n');
            sBuilder.append("groupsize ").append(groupSize);
            log.debug(sBuilder.toString());
        }
        outCounter = new MultidimensionalCounter(osizes);
        inCounter = new MultidimensionalCounter(isizes);
        iterator = outCounter.iterator();
    }

    /**
     * Return the output sizes array. Used with SampleSchedule calculation.
     *
     * @return output sizes array
     * @see SampleSchedule
     */
    public int[] getOutSizes() {
        return osizes;
    }

    public int[] getIndirectSizes() {
        int[] dimSizes = new int[outPoints.length];
        for (int i = 0; i < outPoints.length; i++) {
            dimSizes[i] = osizes[outPoints[i]] * osizes[outPhases[i]];
        }
        return dimSizes;
    }

    /**
     * Returns the size of a group of vectors that should be loaded together so
     * that they can be combined together with various schemes.
     *
     * @return the group size
     */
    public int getGroupSize() {
        return groupSize;
    }

    /**
     * Converts an array of positions that represent output indices in the new
     * dataset to the corresponding locations of the raw data in the FID file.
     *
     * @param counts an array of integers corresponding to output dataset
     *               indices (with a phase and time increment position for each dimension)
     * @return an integer array of input positions at which to load vector from
     * FID file.
     */
    public int[] outToInCounter(int[] counts) {
        int[] icounts = new int[counts.length];
        for (int i = 0; i < inPhases.length; i++) {
            icounts[inPhases[swap[i + 1] - 1]] = counts[outPhases[i]];
            icounts[inPoints[swap[i + 1] - 1]] = counts[outPoints[i]];
        }
        return icounts;
    }

    /**
     * Converts an array of positions that represent output groups in the new
     * dataset to the corresponding output positions (row, plane etc.) in the
     * dataset.
     *
     * @param counts an array of integers corresponding to output dataset
     *               indices (with a phase and time increment position for each dimension)
     * @return an array of output positions in dataset.
     */
    public int[] getOffsets(int[] counts) {
        int[] offsets = new int[nDim - 1];
        for (int i = 0; i < offsets.length; i++) {
            int ph = counts[outPhases[i]];
            int phsize = osizes[outPhases[i]];
            int index = counts[outPoints[i]];
            offsets[i] = index * phsize + ph;
        }
        return offsets;
    }

    /**
     * Returns a VecIndex object containing the output positions in new dataset
     * and input positions in raw FID file that correspond to a particular
     * group. A group represents all the vectors that have the same time value
     * in the indirect dimensions.
     *
     * @param vecNum index of the group to be returned
     * @return VecIndex with positions corresponding to specified group number.
     */
    public VecIndex getNextGroup(final int vecNum) {
        int[] inVecs = new int[groupSize];
        int[][][] outVecs = new int[groupSize][datasetNDim][2]; // output 4 vecs per group, 3 dimensions, pt

        try {
            for (int i = 0; i < groupSize; i++) {
                int[] counts;
                int index = groupSize * vecNum + i;
                if (index >= outCounter.getSize()) {
                    return null;
                }
                counts = outCounter.getCounts(groupSize * vecNum + i);
                int[] iCounts = outToInCounter(counts);
                inVecs[i] = inCounter.getCount(iCounts);
                int[] offsets = getOffsets(counts);
                int jDim = 1;
                for (int iDim = 1; iDim < nDim; iDim++) {
                    if ((datasetNDim < nDim) && (osizes[nDim - iDim - 1] < 2)) {
                        if (offsets[iDim - 1] > 0) {
                            outVecs[i][datasetNDim - 1][0] = -1;
                            outVecs[i][datasetNDim - 1][1] = -1;
                            break;
                        }
                        continue;
                    }
                    outVecs[i][jDim][0] = offsets[iDim - 1];
                    outVecs[i][jDim][1] = offsets[iDim - 1];
                    jDim++;
                }
            }
        } catch (Exception ex) {
            throw ex;
        }
        return new VecIndex(inVecs, outVecs);
    }

    /**
     * Returns a VecIndex object containing the output positions in new dataset
     * and input positions in raw FID file that correspond to a particular
     * group. A group represents all the vectors that have the same time value
     * in the indirect dimensions.
     *
     * @param counts indices of positions in output
     * @return VecIndex with positions corresponding to specified group number.
     */
    public VecIndex getNextGroup(final int[] counts) {
        int[] inVecs = new int[groupSize];
        int[][][] outVecs = new int[groupSize][datasetNDim][2]; // output 4 vecs per group, 3 dimensions, pt

        for (int i = 0; i < groupSize; i++) {
            int[] iCounts = outToInCounter(counts);
            inVecs[i] = inCounter.getCount(iCounts);
            int[] offsets = getOffsets(counts);
            int jDim = 1;
            for (int iDim = 1; iDim < nDim; iDim++) {
                if ((datasetNDim < nDim) && (osizes[nDim - iDim - 1] < 2)) {
                    if (offsets[iDim - 1] > 0) {
                        outVecs[i][datasetNDim - 1][0] = -1;
                        outVecs[i][datasetNDim - 1][1] = -1;
                        break;
                    }
                    continue;
                }
                outVecs[i][jDim][0] = offsets[iDim - 1];
                outVecs[i][jDim][1] = offsets[iDim - 1];
                jDim++;
            }
        }
        return new VecIndex(inVecs, outVecs);
    }

    public int findOutGroup(int... values) {
        int i = 0;
        try {
            while (true) {
                VecIndex vecIndex = getNextGroup(i);
                boolean ok = true;
                for (int k = 0; k < values.length; k++) {
                    int phsIndex = outPhases[k];
                    int mul = osizes[phsIndex];
                    if (mul * values[k] != vecIndex.outVecs[0][k + 1][0]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return i;
                }
                i++;
            }

        } catch (Exception e) {
            System.out.println("failed ");

        }
        return i;
    }

    /**
     * @param args optional command line arguments
     */
    public static void main(String[] args) {
        int[] sizes = {64, 3, 4};
        boolean[] complex = {true, true, true};
        int[] groupSizes = {2, 2, 2};
        String[] modes = {"p1", "d1", "p2", "d2"};
        MultiVecCounter tmult = new MultiVecCounter(sizes, groupSizes, complex, modes, sizes.length);
        tmult.getNextGroup(0);
    }
}
