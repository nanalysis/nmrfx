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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets;

import org.apache.commons.math3.util.FastMath;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.math.VecBase.IndexValue;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.IDBaseline2;
import org.nmrfx.processor.operations.TestBasePoints;
import org.nmrfx.processor.operations.Util;

import java.io.IOException;
import java.util.Iterator;

/**
 * @author Bruce Johnson
 */
@PythonAPI("pyproc")
public class DatasetPhaser {

    final Dataset dataset;
    final int nDim;
    TestBasePoints testBase = null;

    public DatasetPhaser(Dataset dataset) {
        this.dataset = dataset;
        nDim = dataset.getNDim();
    }

    class Index {

        final double amax;
        final int[][] pt;

        Index(double max, int[][] pt) {
            this.amax = max;
            this.pt = new int[nDim][2];
            for (int i = 0; i < nDim; i++) {
                this.pt[i][0] = pt[i][0];
                this.pt[i][1] = pt[i][1];
            }
        }

        @Override
        public String toString() {
            StringBuilder sBuilder = new StringBuilder();
            for (int i = 0; i < nDim; i++) {
                sBuilder.append(pt[i][0]);
                sBuilder.append(" ");
            }
            sBuilder.append(amax);
            return sBuilder.toString();

        }
    }

    /**
     * Calculate phasing along the specified dataset dimension.
     *
     * @param iDim         index of the dataset dimension
     * @param phaseWinSize size of window to use in analysis
     * @param phaseRatio   ratio of signal to noise to use in finding baseline
     * @throws java.io.IOException if an I/O error occurs
     */
    public void setup(int iDim, int phaseWinSize, double phaseRatio, IDBaseline2.ThreshMode threshMode) throws IOException {
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        int[] dimSize = new int[nDim];
        dim[0] = iDim;
        pt[0][0] = 0;
        pt[0][1] = 0;
        int nSegments = 16;
        int nTotal = 1;

        int j = 0;
        for (int i = 1; i < nDim; i++) {
            if (j == iDim) {
                j++;
            }

            dim[i] = j;
            pt[i][0] = 0;
            pt[i][1] = dataset.getSizeTotal(dim[i]) - 1;
            dimSize[i] = dataset.getSizeTotal(dim[i]);
            nTotal *= nSegments;
            j++;
        }
        Index[] regionMax = new Index[nTotal];
        for (int i = 0; i < nTotal; i++) {
            regionMax[i] = null;

        }
        pt[0][1] = dataset.getSizeTotal(iDim) - 1;
        int newSize = pt[0][1] - pt[0][0] + 1;

        Vec testVec = new Vec(newSize, false);
        Vec phaseVec = new Vec(newSize, false);
        testBase = new TestBasePoints(phaseWinSize);
        TestBasePoints.add("test", testBase);

        ScanRegion scanRegion = new ScanRegion(pt, dim, dataset);
        int nEntries = scanRegion.buildIndex();

        int winSize = dataset.getSizeTotal(iDim) / 32;
        int nWin = 4;
        int origSize = pt[0][1];
        for (int iEntry = 0; iEntry < nEntries; iEntry++) {
            int[] iE = scanRegion.getIndexEntry(iEntry);
            pt[0][1] = origSize;
            for (int jDim = 1; jDim < nDim; jDim++) {
                pt[jDim][0] = iE[jDim];
                pt[jDim][1] = iE[jDim];
            }
            dataset.readVectorFromDatasetFile(pt, dim, testVec);
            testVec.hft();
            testVec.abs();
            double sdev = Util.sdev(testVec, winSize, nWin);
            int dSize = 1;
            int index = 0;
            boolean ok = true;
            for (int i = 1; i < pt.length; i++) {
                int offset = 16 * pt[i][0] / dimSize[i];
                if ((offset == 0) || (offset == (nSegments - 1))) {
                    ok = false;
                    break;
                }
                index += dSize * offset;
                dSize *= 16;
            }
            if (!ok) {
                continue;
            }

            IndexValue indexVal = testVec.maxIndex();
            double max = indexVal.getValue();
            double aMax = FastMath.abs(max);
            double threshold = 30.0 * sdev;
            if ((aMax > threshold) && ((regionMax[index] == null) || (regionMax[index].amax < aMax))) {
                regionMax[index] = new Index(aMax, pt);
            }
        }
        for (int i = 0; i < nTotal; i++) {
            if (regionMax[i] != null) {
                dataset.readVectorFromDatasetFile(regionMax[i].pt, dim, phaseVec);
                testBase.addVector(phaseVec, false, phaseRatio, threshMode);
            }

        }
    }

    public double[] getPhase(double ph1Limit) {
        double[] result = testBase.autoPhase(ph1Limit);
        System.out.printf("phases are %7.1f %7.1f\n", result[0], result[1]);
        return result;
    }

    public double getPhaseZero() {
        double[] result = testBase.autoPhaseZero();
        System.out.printf("phase is %7.1f\n", result[0]);
        return result[0];
    }

    public void applyPhases(int iDim, double ph0, double ph1) throws IOException {
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        dim[0] = iDim;
        pt[0][0] = 0;
        pt[0][1] = 0;

        int j = 0;
        for (int i = 1; i < nDim; i++) {
            if (j == iDim) {
                j++;
            }

            dim[i] = j;
            pt[i][0] = 0;
            pt[i][1] = dataset.getSizeTotal(dim[i]) - 1;
            j++;
        }
        pt[0][1] = dataset.getSizeTotal(iDim) - 1;
        int newSize = pt[0][1] - pt[0][0] + 1;

        Vec phaseVec = new Vec(newSize, false);

        ScanRegion scanRegion = new ScanRegion(pt, dim, dataset);
        int nEntries = scanRegion.buildIndex();

        int origSize = pt[0][1];
        for (int iEntry = 0; iEntry < nEntries; iEntry++) {
            int[] iE = scanRegion.getIndexEntry(iEntry);
            pt[0][1] = origSize;
            for (int jDim = 1; jDim < nDim; jDim++) {
                pt[jDim][0] = iE[jDim];
                pt[jDim][1] = iE[jDim];
            }
            dataset.readVectorFromDatasetFile(pt, dim, phaseVec);
            phaseVec.hft();
            phaseVec.phase(ph0, ph1);
            phaseVec.makeReal();
            dataset.writeVector(phaseVec);
        }
    }

    public void applyPhases2(int iDim, double ph0, double ph1) throws IOException {
        Iterator<Vec> vectors = dataset.vectors(iDim);
        double dataPh0 = dataset.getPh0(iDim) + ph0;
        double dataPh1 = dataset.getPh1(iDim) + ph1;
        while (vectors.hasNext()) {
            Vec phaseVec = vectors.next();
            phaseVec.hft();
            phaseVec.phase(ph0, ph1);
            phaseVec.makeReal();
            dataset.writeVector(phaseVec);
        }
        dataset.setPh0(iDim, dataPh0);
        dataset.setPh1(iDim, dataPh1);
    }
}
