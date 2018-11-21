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
import org.nmrfx.processor.utilities.NvUtil;
import java.awt.Color;
import java.io.IOException;
import java.util.*;

public class FileDataGenerator extends DataGenerator implements Cloneable {

    public Dataset theFile;
    public String fileName = null;
    private Hashtable extremes = new Hashtable();
    public double level = 0.3e6;
    public double clm = 1.2;
    public int nlevels = 20;
    public int mChunk = 0;
    public Color[] color = {Color.black, Color.red};
    public boolean[] drawOn = {true, false};
    public boolean masked = false;
    public float[] lineWidth = {0.5f, 0.5f};
    int[] chunkSize;
    int[] chunkOffset;
    public int[] dim;
    int[][] iLim;
    int[] iSize;
    int[] iBlkSize;
    int[] iNBlks;
    int[] iVecGet;
    int[] iVecPut;
    int[] iBlkGet;
    int[] iBlkPut;
    int[] iWDim;
    public int[] drawList = null;
    public boolean[] selectionList = null;
    public boolean selected;
    public boolean intSelected;
    public String title = "";

    public FileDataGenerator(Dataset aFile, String fileName) {
        initialize(aFile, fileName);
    }

    public FileDataGenerator(Dataset aFile) {
        initialize(aFile, aFile.getFileName());
    }

    @Override
    public Object clone() {
        Object o = null;

        try {
            o = super.clone();
            ((FileDataGenerator) o).color = new Color[2];
            ((FileDataGenerator) o).color[0] = new Color(color[0].getRed(),
                    color[0].getGreen(), color[0].getBlue());
            ((FileDataGenerator) o).color[1] = new Color(color[1].getRed(),
                    color[1].getGreen(), color[1].getBlue());
            ((FileDataGenerator) o).lineWidth = lineWidth.clone();
            ((FileDataGenerator) o).level = level;
            ((FileDataGenerator) o).clm = clm;
            ((FileDataGenerator) o).nlevels = nlevels;
            ((FileDataGenerator) o).fileName = fileName;
            ((FileDataGenerator) o).theFile = theFile;
            ((FileDataGenerator) o).drawOn = drawOn.clone();
            if (drawList != null) {
                ((FileDataGenerator) o).drawList = drawList.clone();
            }
            if (selectionList != null) {
                ((FileDataGenerator) o).selectionList = selectionList.clone();
            }
            ((FileDataGenerator) o).selected = selected;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace(System.err);
        }

        return o;
    }

    private void initialize(Dataset aFile, String fileName) {
        theFile = aFile;
        this.fileName = fileName;
        pt = new int[theFile.getNDim()][2];
        ptd = new double[theFile.getNDim()][2];
        iLim = new int[theFile.getNDim()][2];
        iSize = new int[theFile.getNDim()];
        iBlkSize = new int[theFile.getNDim()];
        iNBlks = new int[theFile.getNDim()];
        iVecGet = new int[theFile.getNDim()];
        iBlkGet = new int[theFile.getNDim()];
        iVecPut = new int[theFile.getNDim()];
        iBlkPut = new int[theFile.getNDim()];
        iWDim = new int[theFile.getNDim()];
        title = aFile.getTitle();
        int i;

        for (i = 0; i < theFile.getNDim(); i++) {
            pt[i][0] = 0;
            ptd[i][0] = 0;
            pt[i][1] = theFile.getSize(i) - 1;
            ptd[i][1] = theFile.getSize(i) - 1;
        }

        pt[0][0] = 0;
        pt[0][1] = theFile.getSize(0) - 1;

        if (theFile.getNDim() > 1) {
            pt[1][0] = 0;
            ptd[1][0] = 0;
            pt[1][1] = theFile.getSize(1) - 1;
            ptd[1][1] = theFile.getSize(1) - 1;
        }

        chunkSize = new int[theFile.getNDim()];
        chunkOffset = new int[theFile.getNDim()];
        dim = new int[theFile.getNDim()];

        for (i = 0; i < theFile.getNDim(); i++) {
            dim[i] = i;
            chunkSize[i] = 1;
        }

        if (theFile.getLvl() > 0) {
            level = theFile.getLvl();
        }

        color[0] = NvUtil.color(theFile.getColor(true));
        color[1] = NvUtil.color(theFile.getColor(false));

        if ((theFile.getPosneg() & 2) == 2) {
            color[1] = Color.red;
            drawOn[1] = true;
        }

        if ((theFile.getPosneg() & 1) != 1) {
            color[0] = null;
            drawOn[0] = false;
        }
    }

    public boolean valid() {
        if ((Dataset.getDataset(theFile.getFileName()) == null) || (theFile.getVec() == null) && (theFile.dataFile == null)) {
            return false;
        } else {
            return true;
        }
    }

    public void setDrawListSize(final int size) {
        if (size == 0) {
            drawList = null;
            selectionList = null;
        } else {
            drawList = new int[size];
            selectionList = new boolean[size];
        }
    }

    public int getLastChunk(int iDim) {
        if (theFile.getNDim() < 2) {
            return (0);
        } else if (drawList == null) {
            int iLast = 0;
            for (int i = 0; i < pt.length; i++) {
                if (i != iDim) {
                    iLast += Math.abs(pt[i][1] - pt[i][0]);
                }
            }
            return iLast;
        } else {
            return drawList.length - 1;
        }
    }

    public boolean getSlice(Vec specVec, int iDim, double ppmx, double ppmy) throws IOException {
        int[][] ptC = new int[pt.length][2];
        int[] dimC = new int[pt.length];
        for (int i = 0; i < pt.length; i++) {
            ptC[i][0] = pt[i][0];
            ptC[i][1] = pt[i][1];
            dimC[i] = dim[i];
        }
        if (iDim != 1) {
            int jDim = dimC[1];
            int offset = theFile.ppmToPoint(jDim, ppmy);
            ptC[1][0] = offset;
            ptC[1][1] = offset;
        }
        if (iDim != 0) {
            int jDim = dimC[0];
            int offset = theFile.ppmToPoint(jDim, ppmx);
            ptC[0][0] = offset;
            ptC[0][1] = offset;
        }
        if (iDim == 2) {
            ptC[2][0] = 0;
            ptC[2][1] = theFile.getSize(dim[2]) - 1;
        } else if (iDim == 3) {
            ptC[3][0] = 0;
            ptC[3][1] = theFile.getSize(dim[3]) - 1;
        }
        rearrangeDim(dimC, ptC);
        specVec.resize(ptC[0][1] - ptC[0][0] + 1, theFile.getComplex_r(dimC[0]));
        theFile.readVectorFromDatasetFile(ptC, dimC, specVec);
        return true;
    }

    public void rearrangeDim(int[] dim, int[][] pt) {
        int iDim = 0;
        for (int i = 0; i < pt.length; i++) {
            int size = (int) Math.abs(pt[i][0] - pt[i][1]) + 1;
            if (size > 1) {
                iDim = i;
                break;
            }
        }

        int[][] ptHold = new int[pt.length][2];
        int[] dimHold = new int[pt.length];
        for (int i = 0; i < pt.length; i++) {
            ptHold[i][0] = pt[i][0];
            ptHold[i][1] = pt[i][1];
            dimHold[i] = dim[i];
        }
        pt[0][0] = ptHold[iDim][0];
        pt[0][1] = ptHold[iDim][1];
        dim[0] = dimHold[iDim];
        int j = 0;
        for (int i = 1; i < pt.length; i++) {
            if (j == iDim) {
                j++;
            }
            pt[i][0] = ptHold[j][0];
            pt[i][1] = ptHold[j][1];
            dim[i] = dimHold[j];
            j++;
        }
    }

    public int getRowIndex(int iDim, int iChunk) {
        int rowIndex = -1;
        if (theFile.getNDim() > 1) {
            if (drawList == null) {
                rowIndex = pt[iDim][0] + iChunk;
            } else if (iChunk < 0) {
                rowIndex = -1;
            } else {
                rowIndex = drawList[iChunk];
            }
        }
        return rowIndex;
    }

    public boolean VectorIntegral(Vec specVec, int iChunk, double[] ppms) throws IOException {
        return VectorIntegral(specVec, iChunk, ppms, null);
    }

    public boolean VectorIntegral(Vec specVec, int iChunk, double[] ppms, double[] offsets) throws IOException {
        int[][] ptC = new int[pt.length][2];
        int[] dimC = new int[pt.length];
        int iDim = 0;
        int minDimSize = Integer.MAX_VALUE;
        for (int i = 0; i < pt.length; i++) {
            ptC[i][0] = pt[i][0];
            ptC[i][1] = pt[i][1];
            int size = (int) Math.abs(pt[i][0] - pt[i][1]);
            if (size < minDimSize) {
                minDimSize = size;
                iDim = i;
            }
            dimC[i] = dim[i];
        }

        if (theFile.getNDim() > 1) {
            if (drawList == null) {
                ptC[iDim][0] = pt[iDim][0] + iChunk;
                ptC[iDim][1] = pt[iDim][0] + iChunk;
                if (ptC[iDim][1] > pt[iDim][1]) {
                    return (false);
                }
                if (ptC[iDim][0] < pt[iDim][0]) {
                    return (false);
                }
            } else if (iChunk < 0) {
                return (false);
            } else {
                ptC[1][0] = drawList[iChunk];
                ptC[1][1] = drawList[iChunk];
            }

        } else if ((iChunk < 0) || (iChunk > 1)) {
            return (false);
        }
        rearrangeDim(dimC, ptC);
        int dimSize = theFile.size(dimC[0]);
        specVec.resize(dimSize, false);
        int[] ptCOrig = new int[2];
        ptCOrig[0] = ptC[0][0];
        ptCOrig[1] = ptC[0][1];
        ptC[0][0] = 0;
        ptC[0][1] = dimSize - 1;
        specVec.resize(ptC[0][1] - ptC[0][0] + 1, false);
        Vec vec = theFile.getVec();
        if (vec == null) {
            theFile.readVectorFromDatasetFile(ptC, dimC, specVec);
        } else {
            int j = 0;

            if (vec.isComplex()) {
                for (int i = ptC[0][0]; i <= ptC[0][1]; i++) {
                    specVec.rvec[j++] = vec.getReal(i) / theFile.getScale();
                }
            } else {
                for (int i = ptC[0][0]; i <= ptC[0][1]; i++) {
                    if (vec.rvec[i] == Double.MAX_VALUE) {
                        specVec.rvec[j++] = vec.rvec[i];
                    } else {
                        specVec.rvec[j++] = vec.rvec[i] / theFile.getScale();
                    }
                }
            }
        }
        int lastPoint = 0;

        for (int i = (ppms.length - 1); i >= 0; i -= 2) {
            int pt1 = theFile.ppmToPoint(dimC[0], ppms[i]);
            int pt2 = theFile.ppmToPoint(dimC[0], ppms[i - 1]);
            for (int j = lastPoint; j < pt1; j++) {
                specVec.rvec[j] = Double.MAX_VALUE;
            }
            lastPoint = pt2 + 1;
            if (offsets != null) {
                specVec.integrate(pt1, pt2, offsets[i], offsets[i - 1]);
            } else {
                specVec.integrate(pt1, pt2);
            }
        }
        for (int j = lastPoint; j < dimSize; j++) {
            specVec.rvec[j] = Double.MAX_VALUE;
        }

        System.arraycopy(specVec.rvec, ptCOrig[0], specVec.rvec, 0, (ptCOrig[1] - ptCOrig[0] + 1));
        specVec.resize(ptC[0][1] - ptC[0][0] + 1, false);
        return true;
    }

    public boolean Vector(Vec specVec, int iChunk) throws IOException {
        int[][] ptC = new int[pt.length][2];
        int[] dimC = new int[pt.length];
        int iDim = 0;
        int minDimSize = Integer.MAX_VALUE;
        for (int i = 0; i < pt.length; i++) {
            ptC[i][0] = pt[i][0];
            ptC[i][1] = pt[i][1];
            int size = (int) Math.abs(pt[i][0] - pt[i][1]);
            if (size < minDimSize) {
                minDimSize = size;
                iDim = i;
            }
            dimC[i] = dim[i];
        }

        if (theFile.getNDim() > 1) {
            if (drawList == null) {
                ptC[iDim][0] = pt[iDim][0] + iChunk;
                ptC[iDim][1] = pt[iDim][0] + iChunk;
                if (ptC[iDim][1] > pt[iDim][1]) {
                    return (false);
                }
                if (ptC[iDim][0] < pt[iDim][0]) {
                    return (false);
                }
            } else if (iChunk < 0) {
                return (false);
            } else {
                ptC[1][0] = drawList[iChunk];
                ptC[1][1] = drawList[iChunk];
            }

        } else if ((iChunk < 0) || (iChunk > 1)) {
            return (false);
        }
        rearrangeDim(dimC, ptC);
        specVec.resize(ptC[0][1] - ptC[0][0] + 1, false);
        Vec vec = theFile.getVec();

        if (vec == null) {
            theFile.readVectorFromDatasetFile(ptC, dimC, specVec);
        } else {
            int j = 0;

            if (vec.isComplex()) {
                for (int i = ptC[0][0]; i <= ptC[0][1]; i++) {
                    specVec.rvec[j++] = vec.getReal(i) / theFile.getScale();
                }
            } else {
                for (int i = ptC[0][0]; i <= ptC[0][1]; i++) {
                    if (vec.rvec[i] == Double.MAX_VALUE) {
                        specVec.rvec[j++] = vec.rvec[i];
                    } else {
                        specVec.rvec[j++] = vec.rvec[i] / theFile.getScale();
                    }
                }
            }
        }

        return true;
    }

    public float[][] Matrix(int iChunk, int[] offset) throws IOException {
        chunkSize[0] = 64;

        if (theFile.getNDim() > 1) {
            chunkSize[1] = 64;
        }

        chunkOffset[0] = 1;

        int[] chunk = new int[theFile.getNDim()];
        int[][] apt = new int[theFile.getNDim()][2];
        int i;

        for (i = 1; i < theFile.getNDim(); i++) {
            chunkOffset[i] = chunkOffset[i - 1] * (((pt[i - 1][1]
                    - pt[i - 1][0] - 1) / chunkSize[i - 1]) + 1);
        }

        int jChunk;
        jChunk = iChunk;

        for (i = (theFile.getNDim() - 1); i >= 0; i--) {
            chunk[i] = jChunk / chunkOffset[i];
            jChunk = iChunk % chunkOffset[i];
            apt[i][0] = (chunk[i] * chunkSize[i]) + pt[i][0];
            apt[i][1] = apt[i][0] + chunkSize[i];

            if (i > 1) {
                apt[i][1]--;

                if (apt[i][0] > pt[i][1]) {
                    return (null);
                }
            } else if (apt[i][0] >= pt[i][1]) {
                return (null);
            }

            if (apt[i][1] >= pt[i][1]) {
                apt[i][1] = pt[i][1];
            }
        }

        offset[0] = apt[0][0] - pt[0][0];
        offset[1] = apt[1][0] - pt[1][0];

        float[][] matrix = new float[apt[1][1] - apt[1][0] + 1][apt[0][1]
                - apt[0][0] + 1];
        theFile.readMatrix(theFile, apt, dim, matrix);

        return (matrix);
    }

    public int getMatrixRegion(int iChunk, int maxChunkSize, int mode, int[][] apt,
            double[] offset, StringBuffer chunkLabel) {
        Float extremeValue;
        boolean fastMode = false;
        chunkLabel.append(dim[0] + ".");
        chunkSize[0] = maxChunkSize;

        if (theFile.getNDim() > 1) {
            chunkSize[1] = maxChunkSize;
        }

        chunkOffset[0] = 1;

        int[] chunk = new int[theFile.getNDim()];
        int i;

        for (i = 1; i < theFile.getNDim(); i++) {
            chunkLabel.append(dim[i] + ".");
            int dimSize = theFile.getSize(dim[i - 1]);

            if (i > 1) {
                chunkSize[i] = 1;
//                if (drawList != null) {
                //                   dimSize = drawList.length; 
                //              }
            }
            chunkOffset[i] = chunkOffset[i - 1] * (((dimSize - 1) / chunkSize[i - 1]) + 1);
        }

        int jChunk;

        while (true) {
            jChunk = iChunk;

            boolean ok = true;

            for (i = (theFile.getNDim() - 1); i >= 0; i--) {
                chunk[i] = jChunk / chunkOffset[i];
                jChunk = iChunk % chunkOffset[i];
                if (i == (theFile.getNDim() - 1)) {
                    if (drawList != null) {
                        if (chunk[i] >= drawList.length) {
                            return 1;
                        }
                        apt[i][0] = drawList[chunk[i]];
                    } else {
                        apt[i][0] = chunk[i] * chunkSize[i];
                        if (apt[i][0] > pt[i][1]) {
                            return 1;
                        }
                    }
                } else {
                    apt[i][0] = chunk[i] * chunkSize[i];
                }

                apt[i][1] = apt[i][0] + chunkSize[i];

                if (i > 1) {
                    apt[i][1]--;
                    if (apt[i][1] < pt[i][0]) {
                        ok = false;
                    }

                    if (apt[i][0] > pt[i][1]) {
                        ok = false;
                    }
                } else {
                    if (apt[i][1] < pt[i][0]) {
                        ok = false;
                    }

                    if (apt[i][0] > pt[i][1]) {
                        ok = false;
                    }
                }

                if (mode != 1) {
                    if (apt[i][1] > pt[i][1]) {
                        apt[i][1] = pt[i][1];
                    }

                    if (apt[i][0] < pt[i][0]) {
                        apt[i][0] = pt[i][0];
                    }
                }

                if (apt[i][1] >= theFile.getSize(dim[i])) {
                    apt[i][1] = theFile.getSize(dim[i]) - 1;
                }
            }

            mChunk = iChunk;

            if (ok) {
                if (!fastMode) {
                    break;
                }

                extremeValue = (Float) extremes.get(chunkLabel.toString()
                        + iChunk);

                if (extremeValue == null) {
                    break;
                } else if (extremeValue.floatValue() > level) {
                    break;
                }
            }

            iChunk++;
        }
        offset[0] = apt[0][0] - ptd[0][0];
        offset[1] = apt[1][0] - ptd[1][0];

        return (0);
    }

    public float[][] readMatrix(int iChunk, String chunkLabelStr, int[][] apt, float[][] matrix) throws IOException {
        int ny = apt[1][1] - apt[1][0] + 1;
        int nx = apt[0][1] - apt[0][0] + 1;
        if ((matrix == null) || (matrix.length != ny) || (matrix[0].length != nx)) {
            matrix = new float[ny][nx];
        }
        float maxValue = theFile.readMatrix(theFile, apt, dim, matrix);
        extremes.put(chunkLabelStr + iChunk, new Float(maxValue));

        return (matrix);
    }

    public synchronized int getDim(int userDim) {
        if ((userDim >= 0) && (userDim < dim.length)) {
            return (dim[userDim]);
        } else {
            return (userDim);
        }
    }

    public synchronized void setDim(int dataDim, int userDim) {
        if ((userDim >= 0) && (userDim < dim.length) && (dataDim >= 0)
                && (dataDim < theFile.getNDim())) {
            dim[userDim] = dataDim;
            fixDim();
        }
    }

    public synchronized void fixDim() {
        int i;
        int j;
        int k;
        boolean ok;

        for (j = 1; j < theFile.getNDim(); j++) {
            ok = true;

            for (i = 0; i < j; i++) {
                if (dim[j] == dim[i]) {
                    ok = false;

                    break;
                }
            }

            if (ok) {
                continue;
            }

            for (i = 0; i < theFile.getNDim(); i++) {
                ok = true;

                for (k = 0; k < j; k++) {
                    if (dim[k] == i) {
                        ok = false;

                        break;
                    }
                }

                if (ok) {
                    dim[j] = i;
                }
            }
        }
    }

    public int[] getPoint(int iDim) {
        int[] ptData = new int[2];
        ptData[0] = pt[iDim][0];
        ptData[1] = pt[iDim][1];
        return ptData;
    }

    public double[] getPointD(int iDim) {
        double[] ptData = new double[2];
        ptData[0] = ptd[iDim][0];
        ptData[1] = ptd[iDim][1];
        return ptData;
    }

    public int[][] bounds(int iChunk) {
        return (pt);
    }

    public DataCoordTransformer setBounds(double[][] limits) {
        int i;
        double hz[][] = new double[limits.length][2];
        for (i = 0; ((i < theFile.getNDim()) && (i < limits.length)); i++) {
            pt[i][0] = theFile.ppmToPoint(dim[i], limits[i][0]);
            ptd[i][0] = theFile.ppmToDPoint(dim[i], limits[i][0]);
            pt[i][1] = theFile.ppmToPoint(dim[i], limits[i][1]);
            ptd[i][1] = theFile.ppmToDPoint(dim[i], limits[i][1]);
            hz[i][0] = theFile.ppmToHz(dim[i], limits[i][0]);
            hz[i][1] = theFile.ppmToHz(dim[i], limits[i][1]);

            if (pt[i][0] > pt[i][1]) {
                int hold;
                double fhold;
                hold = pt[i][0];
                fhold = ptd[i][0];
                pt[i][0] = pt[i][1];
                ptd[i][0] = ptd[i][1];
                pt[i][1] = hold;
                ptd[i][1] = fhold;
            }

        }

        DataCoordTransformer dcT = new DataCoordTransformer(dim, theFile);
        return dcT;
    }

    public double[][] setPtBounds(int[][] ilimits, double[][] limits) {
        int i;
        double ptf;

        for (i = 0; ((i < theFile.getNDim()) && (i < ilimits.length)); i++) {
            pt[i][0] = ilimits[i][0];
            ptf = (double) pt[i][0];
            limits[i][0] = (double) theFile.pointToPPM(dim[i], ptf);
            pt[i][0] = theFile.ppmToPoint(dim[i], limits[i][0]);

            pt[i][1] = ilimits[i][1];
            ptf = (double) pt[i][1];
            limits[i][1] = (double) theFile.pointToPPM(dim[i], ptf);
            pt[i][1] = theFile.ppmToPoint(dim[i], limits[i][1]);

            if (pt[i][0] > pt[i][1]) {
                int hold;
                double fhold;
                hold = pt[i][0];
                fhold = limits[i][0];
                pt[i][0] = pt[i][1];
                limits[i][0] = limits[i][1];
                pt[i][1] = hold;
                limits[i][1] = fhold;
            }
        }

        return (limits);
    }

    public double[] getMaxLimits(int i) {
        double[] limit;
        limit = new double[2];

        if (i >= theFile.getNDim()) {
            limit[0] = 0.0;
            limit[1] = 0.0;
        } else {
            limit[0] = (double) theFile.pointToPPM(dim[i], 0.0);
            limit[1] = (double) theFile.pointToPPM(dim[i],
                    (double) (theFile.getSize(dim[i]) - 1));
        }

        return (limit);
    }

    public double getFoldPPM(int i) {
        double foldPPM = theFile.getSw(dim[i]) / theFile.getSf(dim[i]);
        return foldPPM;
    }

    public double getPlaneThickness(int i) {
        double thickness = theFile.getSw(dim[i]) / theFile.getSf(dim[i]) / (theFile.getSize(dim[i])
                - 1);

        return thickness;
    }

    public int[] getMaxLimitsPt(int i) {
        int[] limit;
        limit = new int[2];

        if (i >= theFile.getNDim()) {
            limit[0] = 0;
            limit[1] = 0;
        } else {
            limit[0] = 0;
            limit[1] = theFile.size(dim[i]) - 1;
        }

        return (limit);
    }

    public int nRows(int iChunk) {
        return (32);
    }

    public int nCols(int iChunk) {
        return (32);
    }

    public int scanGet() {
        int i;

        for (i = 1; i < theFile.getNDim(); i++) {
            iVecGet[i]++;

            if (iVecGet[i] >= iBlkSize[i]) {
                iVecGet[i] = 0;
            } else {
                break;
            }
        }

        if (i == theFile.getNDim()) {
            for (i = 1; i < theFile.getNDim(); i++) {
                iBlkGet[i]++;

                if (iBlkGet[i] >= iNBlks[i]) {
                    iBlkGet[i] = 0;
                } else {
                    break;
                }
            }
        }

        if (i == theFile.getNDim()) {
            return (0);
        } else {
            return (1);
        }
    }

    public void setSelected(boolean state) {
        selected = state;
        if (!state) {
            if (selectionList != null) {
                selectionList = new boolean[selectionList.length];
            }
        }
    }

    public void setSelectedElem(int iElem) {
        if (selectionList == null) {
            selectionList = new boolean[getLastChunk(0) + 1];
        }
        if ((selectionList != null) && (iElem < selectionList.length)) {
            selectionList[iElem] = true;
        }
    }

    public int[] getSelected() {
        int[] result = new int[0];
        if ((selectionList != null) && (selectionList.length != 0)) {
            ArrayList<Integer> resultList = new ArrayList<Integer>();
            for (int i = 0; i < selectionList.length; i++) {
                if (selectionList[i]) {
                    if (drawList != null) {
                        resultList.add(i);
                    } else if (pt.length > 1) {
                        resultList.add(i);
                    }
                }
            }
            result = new int[resultList.size()];
            int i = 0;
            for (Integer iVal : resultList) {
                result[i++] = iVal;

            }
        }

        return result;
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isSelected(int iElem) {
        boolean value = false;
        if ((selectionList != null) && (iElem < selectionList.length) && (iElem >= 0)) {
            value = selectionList[iElem];
        } else {
            value = selected;
        }
        return value;
    }

    public void setIntegralSelected(boolean state) {
        intSelected = state;
    }

    public boolean getIntegralSelected() {
        return intSelected;
    }

    /*
     static int
     DatasetScan(interp, argc, argv, mode)
     Tcl_Interp     *interp;
     int             argc;
     char           *argv[];
     int             mode;
     {
     #include "m_static.h"

     static DATASET *dataset;
     static int      fileDim;

     static int      iLim[NMR_NDIM][2];
     static int      iSize[NMR_NDIM];
     static int      iBlkSize[NMR_NDIM];
     static int      iNBlks[NMR_NDIM];
     static int      iVec[NMR_NDIM];
     static int      iBlk[NMR_NDIM];
     static int      iWDim[NMR_NDIM];
     static int      p[NMR_NDIM][2];

     static VEC     *Vec0;
     static MeshObject *meshObj0;
     Real           *ve, **me;

     static          endFile = 0;
     static int      firstTime = 1;

     int             i, j;
     int             hold;
     int             vecDim;

     int             rwMode = 0;
     me = (Real **) NULL;
     switch (mode) {
     case 0:
     if (argc < 4) {
     sprintf(interp->result, "%s %s: Insufficient arguments ", argv[0], argv[1]);
     return (TCL_ERROR);
     }
     dataset = Nv_FindDataset(argv[2]);
     if (dataset == (DATASET *) NULL) {
     sprintf(interp->result, "Invalid Dataset %s ", argv[2]);
     return (TCL_ERROR);
     }
     fileDim = dataset->matfile.ndim;
     vecDim = atoi(argv[3]) - 1;
     if ((vecDim < 0) || (vecDim >= fileDim)) {
     sprintf(interp->result, "%s %s Invalid Dataset Dimension (%d)", argv[0], argv[1], vecDim + 1);
     return (TCL_ERROR);
     }
     if ((meshObj0 = NvMeshObjectGet(argv[4])) == (MeshObject *) NULL) {
     sprintf(interp->result, "%s: No Such Object", argv[4]);
     return (TCL_ERROR);
     }
     iWDim[0] = vecDim;



     j = 0;
     if (vecDim == 0)
     j = 1;
     for (i = 1; i < NMR_NDIM; i++) {
     if (vecDim != j)
     iWDim[i] = j;
     else
     iWDim[i] = j + 1;

     j++;
     }
     for (i = 0; i < NMR_NDIM; i++) {
     iLim[i][0] = 0;
     iLim[i][1] = 0;
     iSize[i] = 0;
     iBlkSize[i] = 1;
     iVec[i] = 0;
     iBlk[i] = 0;
     }

     for (i = 0; i < fileDim; i++) {
     iLim[i][0] = 0;
     iLim[i][1] = dataset->matfile.size[iWDim[i]] - 1;
     if (iLim[i][1] < iLim[i][0]) {
     hold = iLim[i][1];
     iLim[i][1] = iLim[i][0];
     iLim[i][0] = hold;
     }
     iSize[i] = iLim[i][1] - iLim[i][0] + 1;
     if (iSize[i] <= 0) {
     ConsoleWrite("Bad Plot Limits\n");
     return (1);
     }
     iBlkSize[i] = dataset->matfile.blksize[iWDim[i]];
     iNBlks[i] = dataset->matfile.nblks[iWDim[i]];
     }
     endFile = 0;
     firstTime = 1;
     sprintf(interp->result, "%d", iSize[0]);
     return (TCL_OK);
     break;
     case 1:
     if (!firstTime) {
     for (i = 1; i < fileDim; i++) {
     iVec[i]++;
     if (iVec[i] >= iBlkSize[i]) {
     iVec[i] = 0;
     } else {
     break;
     }
     }

     if (i == fileDim) {
     for (i = 1; i < fileDim; i++) {
     iBlk[i]++;
     if (iBlk[i] >= iNBlks[i]) {
     iBlk[i] = 0;
     } else {
     break;
     }
     }
     }
     if (i == fileDim) {
     sprintf(interp->result, "0");
     endFile = 1;
     return (TCL_OK);
     }
     } else
     firstTime = 0;
     case 2:
     if (endFile == 1) {
     sprintf(interp->result, "%s %s: End of File", argv[0], argv[1]);
     return (TCL_ERROR);
     }
     if (mode == 1) {
     rwMode = NV_READ;
     } else {
     rwMode = NV_WRITE;
     }

     if ((dataset->matfile.rwMode == NV_READ) && (rwMode != NV_READ)) {
     sprintf(interp->result, "Can't write to read-only file!\n");
     return (TCL_ERROR);
     }
     for (i = 0; i < fileDim; i++) {
     j = iWDim[i];
     p[i][0] = p[i][1] = iBlk[i] * iBlkSize[i] + iVec[i];
     if (p[i][0] >= dataset->matfile.size[j]) {
     sprintf(interp->result, "%s %s: Out of Bounds dim %d is %d dataset is %d", argv[0], argv[1], j + 1, p[i][0], dataset->matfile.size[j]);
     return (TCL_ERROR);
     }
     }

     if (GetRWVector(interp, dataset, meshObj0, iSize[0], rwMode, &ve, iWDim[0]) == TCL_ERROR) {
     return TCL_ERROR;
     }
     p[0][0] = iLim[0][0];
     p[0][1] = iLim[0][1];
     GetNewRegion(interp, dataset, iWDim, p, ve, me, rwMode, 0);
     sprintf(interp->result, "1");
     return (TCL_OK);

     }
     return (TCL_ERROR);
     }


     */
}
