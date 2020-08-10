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

/**
 *
 * @author brucejohnson
 *
 * The MultiVecCounter class converts an index (from 0 to nGroups-1) to a
 * VecIndex object that stores the input positions in FID at which to read the
 * vectors and the output positions in the dataset at which to write the
 * vectors. A group represents all the vectors that have the same time value in
 * the indirect dimensions.
 */
public class MultiVecCounter {

    public static boolean showDebugInfo = true;
    int[] osizes;
    int[] isizes;
    int[] inPhases;
    int[] inPoints;
    int[] outPhases;
    int[] outPoints;
    int groupSize = 1;
    int nDim = 1;
    int datasetNDim;
    MultidimensionalCounter outCounter;
    MultidimensionalCounter inCounter;
    MultidimensionalCounter.Iterator iterator;

    /**
     * Construct a new MultiVecCounter with specified parameters describing
     * input and output data.
     *
     * @param tdSizes an array of integers representing the size of the input
     * data in each dimension. Output data sizes are set equal to the input data
     * sizes.
     * @param complex an array of booleans representing whether the input FID is
     * complex in each dimension.
     * @param modes an array of string values representing the order in which
     * data was acquired. The first character of each mode is either a 'p',
     * representing phase information, or 'd' representing time delay. The
     * second character represents the dimension, with '1' representing the
     * first indirect dimension. For example, "p1","p2","d1","d2" represents a
     * typical Agilent 3D dataset with array value = "phase2,phase" and
     * "p1","d1","p2","d2" would represent a typical Bruker 3D dataset.
     * @param datasetNDim number of dimensions in final dataset, could be
     * smaller than original data dimensions.
     */
    public MultiVecCounter(int[] tdSizes, boolean[] complex, String[] modes, int datasetNDim) {
        nDim = tdSizes.length;
        osizes = new int[(nDim - 1) * 2];
        isizes = new int[(nDim - 1) * 2];
        this.datasetNDim = datasetNDim;
        init(tdSizes, tdSizes, complex, modes);
    }

    /**
     * Construct a new MultiVecCounter with specified parameters describing
     * input and output data.
     *
     * @param tdSizes an array of integers representing the size of the input
     * data in each dimension
     * @param outSizes an array of integers representing the size of the output
     * dataset in each dimension
     * @param complex an array of booleans representing whether the input FID is
     * complex in each dimension.
     * @param modes an array of string values representing the order in which
     * data was acquired. The first character of each mode is either a 'p',
     * representing phase information, or 'd' representing time delay. The
     * second character represents the dimension, with '1' representing the
     * first indirect dimension. For example, "p1","p2","d1","d2" represents a
     * typical Agilent 3D dataset with array value = "phase2,phase" and
     * "p1","d1","p2","d2" would represent a typical Bruker 3D dataset.
     * @param datasetNDim number of dimensions in final dataset, could be
     * smaller than original data dimensions.
     */
    public MultiVecCounter(int[] tdSizes, int[] outSizes, boolean[] complex, String[] modes, int datasetNDim) {
        nDim = tdSizes.length;
        System.out.println("ndim " + nDim);
        osizes = new int[(nDim - 1) * 2];
        isizes = new int[(nDim - 1) * 2];
        this.datasetNDim = datasetNDim;
        init(tdSizes, outSizes, complex, modes);
    }

    void init(int[] tdSizes, int[] outSizes, boolean[] complex, String[] modes) {
        int nIDim = tdSizes.length - 1;  // number of indirect dimensions
        System.out.println("nidim " + nIDim);

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
        boolean matchIn = false;

        int iArg = 0;
        int iSize = 1;
        int iPhase = 1;
        groupSize = 1;
//        for (int i=0;i<tdSizes.length;i++) {
//            System.out.println("in size " + i + " " + tdSizes[i]);
//        }
//        for (int i=0;i<outSizes.length;i++) {
//            System.out.println("ou size " + i + " " + outSizes[i]);
//        }

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
                isizes[argIndex] = complex[dim] ? 2 : 1;
                groupSize *= complex[iPhase] ? 2 : 1;
            } else if (mode.charAt(0) == 'a') {
                if (inPhases.length >= dim) {
                    inPhases[dim - 1] = argIndex;
                    inPoints[dim - 1] = argIndex - 1;
                    isizes[argIndex] = 1;
                    isizes[argIndex - 1] = tdSizes[dim];
                    groupSize *= 1;
                    iArg++;
                }
            } else {
                throw new IllegalArgumentException("bad mode " + mode);
            }
            iArg++;
        }

        if (matchIn) {
            for (int i = 0; i < nIDim; i++) {
                outPhases[i] = inPhases[i];
                outPoints[i] = inPoints[i];
            }
            for (int i = 0; i < isizes.length; i++) {
                osizes[i] = isizes[i];
            }
        } else {
            for (int i = 0; i < nIDim; i++) {
                outPhases[i] = 2 * nIDim - 1 - i;
                outPoints[i] = nIDim - 1 - i;
                osizes[nIDim - 1 - i] = outSizes[i + 1];
            }
            groupSize = 1;
            for (int i = 0; i < nIDim; i++) {
                if (complex[i + 1]) {
                    groupSize *= 2;
                    osizes[2 * nIDim - 1 - i] = 2;
                } else {
                    osizes[2 * nIDim - 1 - i] = 1;
                }
            }
        }
//        int[] isizeA = {1, 1, 310, 2};
//        int[] iPtA = {0, 2};
//        int[] iPhA = {1, 3};
//        int[] osizeA = {310, 1, 2, 1};
//        int[] oPtA = {1, 0};
//        int[] oPhA = {3, 2};
        int[] isizeA = {155, 1, 2, 1, 2, 1};
        int[] iPtA = {2, 0, 1};
        int[] iPhA = {5, 3, 4};
        int[] osizeA = {155, 1, 2, 1, 2, 1};
        int[] oPtA = {2, 0, 1};
        int[] oPhA = {5, 3, 4};
//        isizes = isizeA;
//        osizes = osizeA;
//        inPhases = iPhA;
//        inPoints = iPtA;
//        outPhases = oPhA;
//        outPoints = oPtA;
        if (showDebugInfo) {
            System.out.println("  MultiVecCounter: ");
            for (int i = 0; i < outPhases.length; i++) {
                System.out.print("ouPh[" + i + "]=" + outPhases[i] + " ");
            }
            System.out.println("");

            for (int i = 0; i < outPoints.length; i++) {
                System.out.print("ouPt[" + i + "]=" + outPoints[i] + " ");
            }
            System.out.println("");

            for (int i = 0; i < inPhases.length; i++) {
                System.out.print("inPh[" + i + "]=" + inPhases[i] + " ");
            }
            System.out.println("");

            for (int i = 0; i < inPoints.length; i++) {
                System.out.print("inPt[" + i + "]=" + inPoints[i] + " ");
            }
            System.out.println("");

            for (int i = 0; i < isizes.length; i++) {
                System.out.print(" inSz[" + i + "]=" + isizes[i]);
            }
            System.out.println("");
            for (int i = 0; i < osizes.length; i++) {
                System.out.print(" ouSz[" + i + "]=" + osizes[i]);
            }
            System.out.println("");
            System.out.println("groupsize " + groupSize);
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
     * indices (with a phase and time increment position for each dimension)
     * @return an integer array of input positions at which to load vector from
     * FID file.
     */
    public int[] outToInCounter(int[] counts) {
        int[] icounts = new int[counts.length];
        for (int i = 0; i < inPhases.length; i++) {
            icounts[inPhases[i]] = counts[outPhases[i]];
            icounts[inPoints[i]] = counts[outPoints[i]];
        }
        return icounts;
    }

    /**
     * Converts an array of positions that represent output groups in the new
     * dataset to the corresponding output positions (row, plane etc.) in the
     * dataset.
     *
     * @param counts an array of integers corresponding to output dataset
     * indices (with a phase and time increment position for each dimension)
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
     * @param vecNum
     * @return VecIndex with positions corresponding to specified group number.
     */
    public VecIndex getNextGroup(final int vecNum) {
        int[] inVecs = new int[groupSize];
        int[][][] outVecs = new int[groupSize][datasetNDim][2]; // output 4 vecs per group, 3 dimensions, pt

        for (int i = 0; i < groupSize; i++) {
            int[] counts = outCounter.getCounts(groupSize * vecNum + i);
            int[] iCounts = outToInCounter(counts);
            inVecs[i] = inCounter.getCount(iCounts);
            int[] offsets = getOffsets(counts);
            //System.out.println(vecNum + " " + inVecs[i] + " " + offsets[0] + " " + offsets[1] + " " + iCounts[0] + " " + iCounts[1] + " " + iCounts[2] + " " + iCounts[3]);
            //System.out.println(vecNum + " " + outCount + " " + offsets[0] + " " + offsets[1] + " " + counts[0] + " " + counts[1] + " " + counts[2] + " " + counts[3]);
            int jDim = 1;
            for (int iDim = 1; iDim < nDim; iDim++) {
                //System.out.println(nDim + " " + datasetNDim + " " + i + " " + iDim + " " + offsets[iDim-1] + " " + osizes[nDim-iDim-1]);
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
                    if (2 * values[k] != vecIndex.outVecs[0][k + 1][0]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    System.out.println("found " + i + " ");
                    vecIndex.printMe(i, 1);
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
     * Unused
     *
     */
    public void getNextGroup() {
        int i = 0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            int j = 0;
            for (int value : counts) {
                System.out.print(" " + value);
            }
            System.out.println("");
            int[] iCounts = outToInCounter(counts);
            int iCount = outCounter.getCount(counts);
            for (int value : iCounts) {
                System.out.print(" " + value);
            }
            System.out.println("");
            int inVec = inCounter.getCount(iCounts);
            System.out.println(" i " + i + " " + inVec);
            getOffsets(counts);
            i++;
        }
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        int[] sizes = {64, 3, 4};
        boolean[] complex = {true, true, true};
        String[] modes = {"p1", "d1", "p2", "d2"};
        MultiVecCounter tmult = new MultiVecCounter(sizes, complex, modes, sizes.length);
        tmult.getNextGroup(0);
    }
}
