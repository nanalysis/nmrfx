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
package org.nmrfx.processor.gui.spectra;

import org.nmrfx.processor.datasets.DataCoordTransformer;
import org.nmrfx.processor.datasets.DataGenerator;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;
import org.apache.commons.beanutils.PropertyUtils;
import org.nmrfx.processor.gui.PolyChart.DISDIM;

public class DatasetAttributes extends DataGenerator implements Cloneable {

    private Dataset theFile;
    private Hashtable extremes = new Hashtable();
    public int mChunk = 0;
    public boolean masked = false;

    // used to tell if dataset has a level value already so we don't need to call autoLevel.
    // used in processing same dataset multiple times, so it's easier to compare the processing without the level changing
    private boolean hasLevel = false;

    public static enum AXMODE {

        PTS() {
            public int getIndex(Vec vec, double value) {
                int index = (int) Math.round(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= vec.getSize()) {
                    index = vec.getSize() - 1;
                }
                return index;
            }

            public double getIncrement(Vec vec, double start, double end) {
                return 1;
            }

            public int getIndex(DatasetAttributes dataAttr, int jDim, double value) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];
                int index = (int) Math.round(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= dataset.getSize(iDim)) {
                    index = dataset.getSize(iDim) - 1;
                }
                return index;
            }

            public double indexToValue(DatasetAttributes dataAttr, int jDim, double value) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                if (value < 0) {
                    value = 0;
                }
                if (value >= dataset.getSize(iDim)) {
                    value = dataset.getSize(iDim) - 1;
                }
                return value;
            }

            public double getIncrement(DatasetAttributes dataAttr, int iDim, double start, double end) {
                return 1;
            }

            public String getLabel(DatasetAttributes dataAttr, int jDim) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                return dataset.getLabel(iDim) + " (pt)";
            }

            public String getLabel(Vec vec) {
                return "points";
            }

        },
        PPM() {
            public int getIndex(Vec vec, double value) {
                int index = vec.refToPt(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= vec.getSize()) {
                    index = vec.getSize() - 1;
                }
                return index;
            }

            public double getIncrement(Vec vec, double start, double end) {
                int iStart = getIndex(vec, start);
                int iEnd = getIndex(vec, end);
                double delta = (end - start) / (iEnd - iStart);
                return delta;
            }

            public int getIndex(DatasetAttributes dataAttr, int jDim, double value) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                int index = dataset.ppmToPoint(iDim, value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= dataset.getSize(iDim)) {
                    index = dataset.getSize(iDim) - 1;
                }
                return index;
            }

            public double indexToValue(DatasetAttributes dataAttr, int jDim, double value) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                double ppmValue = dataset.pointToPPM(iDim, value);
                return ppmValue;
            }

            public double getIncrement(DatasetAttributes dataAttr, int jDim, double start, double end) {
                return 1;
            }

            public String getLabel(DatasetAttributes dataAttr, int jDim) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                return dataset.getLabel(iDim) + " (ppm)";
            }

            public String getLabel(Vec vec) {
                return "ppm";
            }
        },
        TIME() {
            public int getIndex(Vec vec, double value) {
                int index = vec.timeToPt(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= vec.getSize()) {
                    index = vec.getSize() - 1;
                }
                return index;
            }

            public double getIncrement(Vec vec, double start, double end) {
                int iStart = getIndex(vec, start);
                int iEnd = getIndex(vec, end);
                double delta = (end - start) / (iEnd - iStart);
                return delta;
            }

            public int getIndex(DatasetAttributes dataAttr, int jDim, double value) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                Vec vec = dataset.getVec();
                if (vec != null) {
                    return getIndex(vec, value);
                } else {
                    // fixme is this right or should it be 1/sw
                    int index = (int) (value * dataset.getSw(iDim));
                    if (index < 0) {
                        index = 0;
                    }
                    if (index >= dataset.getSize(iDim)) {
                        index = dataset.getSize(iDim) - 1;
                    }
                    return index;
                }
            }

            public double indexToValue(DatasetAttributes dataAttr, int jDim, double value) {
                Dataset dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                value = value / dataset.getSw(iDim);
                return value;
            }

            public double getIncrement(DatasetAttributes dataAttr, int jDim, double start, double end) {
                return 1;
            }

            public String getLabel(DatasetAttributes dataAttr, int jDim) {
                return "seconds";
            }

            public String getLabel(Vec vec) {
                return "seconds";
            }
        };

        ;

        public abstract int getIndex(Vec vec, double value);

        public abstract double indexToValue(DatasetAttributes dataAttr, int iDim, double value);

        public abstract double getIncrement(Vec vec, double start, double end);

        public abstract int getIndex(DatasetAttributes dataAttr, int iDim, double value);

        public abstract double getIncrement(DatasetAttributes dataAttr, int iDim, double start, double end);

        public abstract String getLabel(DatasetAttributes dataAttr, int iDim);

        public String getDatasetLabel(DatasetAttributes dataAttr, int jDim) {
            Dataset dataset = dataAttr.getDataset();
            int iDim = dataAttr.dim[jDim];

            return dataset.getLabel(iDim);
        }

        public abstract String getLabel(Vec vec);

    }

    private ColorProperty posColor;

    public ColorProperty posColorProperty() {
        if (posColor == null) {
            posColor = new ColorProperty(this, "+color", Color.BLACK);
        }
        return posColor;
    }

    public void setPosColor(Color value) {
        posColorProperty().set(value);
    }

    public Color getPosColor() {
        return posColorProperty().get();
    }
    private ColorProperty negColor;

    public ColorProperty negColorProperty() {
        if (negColor == null) {
            negColor = new ColorProperty(this, "-color", Color.RED);
        }
        return negColor;
    }

    public void setNegColor(Color value) {
        negColorProperty().set(value);
    }

    public Color getNegColor() {
        return negColorProperty().get();
    }

    private DoubleProperty posLineWidth;

    public DoubleProperty posLineWidthProperty() {
        if (posLineWidth == null) {
            posLineWidth = new SimpleDoubleProperty(this, "+wid", 0.5);
        }
        return posLineWidth;
    }

    public void setPosLineWidth(double value) {
        posLineWidthProperty().set(value);
    }

    public double getPosLineWidth() {
        return posLineWidthProperty().get();
    }
    private DoubleProperty negLineWidth;

    public DoubleProperty negLineWidthProperty() {
        if (negLineWidth == null) {
            negLineWidth = new SimpleDoubleProperty(this, "-wid", 0.5);
        }
        return negLineWidth;
    }

    public void setNegLineWidth(double value) {
        negLineWidthProperty().set(value);
    }

    public double getNegLineWidth() {
        return negLineWidthProperty().get();
    }
    private DoubleProperty level;

    public DoubleProperty levelProperty() {
        if (level == null) {
            level = new SimpleDoubleProperty(this, "level", 1.0);
        }
        return level;
    }

    public void setLevel(double value) {
        levelProperty().set(value);
    }

    public double getLevel() {
        return levelProperty().get();
    }

    private DoubleProperty offset;

    public DoubleProperty offsetProperty() {
        if (offset == null) {
            offset = new SimpleDoubleProperty(this, "offset", 0.05);
        }
        return offset;
    }

    public void setOffset(double value) {
        offsetProperty().set(value);
    }

    public double getOffset() {
        return offsetProperty().get();
    }

    private BooleanProperty posDrawOn;

    public BooleanProperty posDrawOnProperty() {
        if (posDrawOn == null) {
            posDrawOn = new SimpleBooleanProperty(this, "+on", true);
        }
        return posDrawOn;
    }

    public void setPosDrawOn(boolean value) {
        posDrawOnProperty().set(value);
    }

    public boolean getPosDrawOn() {
        return posDrawOnProperty().get();
    }

    private BooleanProperty negDrawOn;

    public BooleanProperty negDrawOnProperty() {
        if (negDrawOn == null) {
            negDrawOn = new SimpleBooleanProperty(this, "-on", true);
        }
        return negDrawOn;
    }

    public void setNegDrawOn(boolean value) {
        negDrawOnProperty().set(value);
    }

    public boolean getNegDrawOn() {
        return negDrawOnProperty().get();
    }

    private IntegerProperty nlevels;

    public IntegerProperty nlevelsProperty() {
        if (nlevels == null) {
            nlevels = new SimpleIntegerProperty(this, "nlevels", 20);
        }
        return nlevels;
    }

    public void setNlevels(int value) {
        nlevelsProperty().set(value);
    }

    public int getNlevels() {
        return nlevelsProperty().get();
    }
    private DoubleProperty clm;

    public DoubleProperty clmProperty() {
        if (clm == null) {
            clm = new SimpleDoubleProperty(this, "clm", 1.2);
        }
        return clm;
    }

    public void setClm(double value) {
        clmProperty().set(value);
    }

    public double getClm() {
        return clmProperty().get();
    }

    private StringProperty fileName;

    public StringProperty fileNameProperty() {
        if (fileName == null) {
            fileName = new SimpleStringProperty(this, "fileName", "");
        }
        return fileName;
    }

    public void setFileName(String value) {
        fileNameProperty().set(value);
    }

    public String getFileName() {
        return fileNameProperty().get();
    }
    private BooleanProperty drawReal;

    public BooleanProperty drawRealProperty() {
        if (drawReal == null) {
            drawReal = new SimpleBooleanProperty(this, "real", true);
        }
        return drawReal;
    }

    public void setDrawReal(boolean value) {
        drawRealProperty().set(value);
    }

    public boolean getDrawReal() {
        return drawRealProperty().get();
    }

    public Dataset getDataset() {
        return theFile;
    }

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
    private StringProperty firstName;

    public DatasetAttributes(Dataset aFile, String fileName) {
        initialize(aFile, fileName);
    }

    public DatasetAttributes(Dataset aFile) {
        initialize(aFile, aFile.getFileName());
    }

    @Override
    public Object clone() {
        Object o = null;

        try {
            o = super.clone();
            ((DatasetAttributes) o).setPosColor(getPosColor());
            ((DatasetAttributes) o).setNegColor(getNegColor());
            ((DatasetAttributes) o).setPosLineWidth(getPosLineWidth());
            ((DatasetAttributes) o).setNegLineWidth(getNegLineWidth());
            ((DatasetAttributes) o).setLevel(getLevel());
            ((DatasetAttributes) o).clm = clm;
            ((DatasetAttributes) o).setNlevels(getNlevels());
            ((DatasetAttributes) o).nDim = nDim;
            ((DatasetAttributes) o).fileName = fileName;
            ((DatasetAttributes) o).theFile = theFile;
            ((DatasetAttributes) o).setPosDrawOn(getPosDrawOn());
            ((DatasetAttributes) o).setNegDrawOn(getNegDrawOn());
            if (drawList != null) {
                ((DatasetAttributes) o).drawList = drawList.clone();
            }
            if (selectionList != null) {
                ((DatasetAttributes) o).selectionList = selectionList.clone();
            }
            ((DatasetAttributes) o).selected = selected;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace(System.err);
        }

        return o;
    }

    public void setHasLevel(boolean value) {
        hasLevel = value;
    }

    public boolean getHasLevel() {
        return hasLevel;
    }

    public void setDataset(Dataset aFile) {
        hasLevel = false;
        theFile = aFile;
        nDim = aFile.getNDim();
        setFileName(aFile.getFileName());
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

    }

    private void initialize(Dataset aFile, String fileName) {
        theFile = aFile;
        nDim = aFile.getNDim();
        setFileName(fileName);
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
            setLevel(theFile.getLvl());
        }
        setPosColor(Color.valueOf(theFile.getPosColor()));
        setNegColor(Color.valueOf(theFile.getNegColor()));

        if ((theFile.getPosneg() & 2) == 2) {
            setNegColor(Color.RED);
            setNegDrawOn(true);
        }

        if ((theFile.getPosneg() & 1) != 1) {
            setPosColor(Color.BLACK);
            setPosDrawOn(false);
        }
        hasLevel = false;
    }

    public boolean valid() {
        if ((Dataset.getDataset(theFile.getFileName()) == null) || (theFile.getVec() == null) && !theFile.hasDataFile()) {
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
            //int offset = (int) Math.round(ppmy);
            ptC[1][0] = offset;
            ptC[1][1] = offset;
        }
        if (iDim != 0) {
            int jDim = dimC[0];
            int offset = theFile.ppmToPoint(jDim, ppmx);
            //int offset = (int) Math.round(ppmx);
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
        //System.out.println("get slice " + ptC[0][0] + " " + ptC[0][1] + " " + specVec.getSize());
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

    public void updateBounds(AXMODE[] axModes, NMRAxis[] axes, DISDIM disDim) {
        int[][] localPt;
        double[][] limits;
        localPt = new int[nDim][2];
        limits = new double[nDim][2];
        double[] planeThickness = new double[nDim];
        for (int i = 0; i < nDim; i++) {
            planeThickness[i] = getPlaneThickness(i);
            if (i == 0) {
                localPt[i][0] = axModes[i].getIndex(this, i, axes[0].getLowerBound());
                localPt[i][1] = axModes[i].getIndex(this, i, axes[0].getUpperBound());
            } else if (i == 1) {
                if (disDim == DISDIM.TwoD) {
                    localPt[i][0] = axModes[i].getIndex(this, i, axes[1].getLowerBound());
                    localPt[i][1] = axModes[i].getIndex(this, i, axes[1].getUpperBound());
                } else {
                    localPt[i][0] = 0;
                    localPt[i][0] = theFile.getSize(dim[i]) - 1;
                }
            } else if (axModes.length <= i) {
                localPt[i][0] = theFile.getSize(dim[i]) / 2;
                localPt[i][1] = theFile.getSize(dim[i]) / 2;
            } else {
                localPt[i][0] = axModes[i].getIndex(this, i, axes[i].getLowerBound());
                localPt[i][1] = axModes[i].getIndex(this, i, axes[i].getUpperBound());
            }
            if (localPt[i][0] > localPt[i][1]) {
                int hold = localPt[i][0];
                localPt[i][0] = localPt[i][1];
                localPt[i][1] = hold;
            }
            //System.out.println("dim " + i + " " + fileData.dim[i] + " " + pt[i][0] + " " + pt[i][1]);
        }
        setPtBounds(localPt, limits);
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
        if (theFile.getVec() == null) {
            theFile.readVectorFromDatasetFile(ptC, dimC, specVec);
        } else {
            int j = 0;
            Vec vec = theFile.getVec();

            if (vec.isComplex()) {
                for (int i = ptC[0][0]; i <= ptC[0][1]; i++) {
                    specVec.rvec[j++] = vec.getReal(i) / theFile.getScale();
                }
            } else {
                for (int i = ptC[0][0]; i <= ptC[0][1]; i++) {
                    if (vec.rvec[i] == Double.MAX_VALUE) {
                        specVec.rvec[j++] = vec.getReal(i);
                    } else {
                        specVec.rvec[j++] = vec.getReal(i) / theFile.getScale();
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

    @Override
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

    public int getMatrixRegion(int iChunk, int mode, int[][] apt,
            double[] offset, StringBuffer chunkLabel) {
        Float extremeValue;
        boolean fastMode = false;
        chunkLabel.append(dim[0] + ".");
        chunkSize[0] = 64;

        if (theFile.getNDim() > 1) {
            chunkSize[1] = 64;
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
//                System.out.println(iChunk + " chunk" + jChunk + " " + i + " " + pt[i][0] + " " + pt[i][1] + " " + apt[i][0] + " " + apt[i][1] + " " +ok);

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
                } else if (extremeValue > level.get()) {
                    break;
                }
            }

            iChunk++;
        }
        offset[0] = apt[0][0] - ptd[0][0];
        offset[1] = apt[1][0] - ptd[1][0];

        return (0);
    }

    public float[][] Matrix2(int iChunk, String chunkLabelStr, int[][] apt) throws IOException {
        float[][] matrix = new float[apt[1][1] - apt[1][0] + 1][apt[0][1]
                - apt[0][0] + 1];
//        for (int i=0;i<dim.length;i++) {
//            System.out.println(i + " " + dim[i] + " " + apt[i][1] + " " + apt[i][0]);
//        }
        float maxValue = theFile.readMatrix(theFile, apt, dim, matrix);
        extremes.put(chunkLabelStr + iChunk, new Float(maxValue));

        return (matrix);
    }

    public String getLabel(int iDim) {
        String label = "";
        if (iDim < dim.length) {
            label = theFile.getLabel(dim[iDim]);
        }
        return label;

    }

    public synchronized int getDim(int userDim) {
        if ((userDim >= 0) && (userDim < dim.length)) {
            return (dim[userDim]);
        } else {
            return (userDim);
        }
    }

    public void setDim(String axisName, int datasetDim) {
        int userDim = "XYZABCDEFG".indexOf(axisName);
        setDim(datasetDim, userDim);

    }

    public void setDim(String axisName, String dimName) {
        int userDim = "XYZABCDEFG".indexOf(axisName);
        int datasetDim = -1;
        for (int i = 0; i < theFile.getNDim(); i++) {
            if (dimName.equals(theFile.getLabel(i))) {
                datasetDim = i;
            }
        }
        if (datasetDim != -1) {
            setDim(datasetDim, userDim);
        }

    }

    public void syncDims(DatasetAttributes matchAttr) {
        int nMatch = 0;
        int[] matchDim = matchAttr.dim;
        int matchN = matchDim.length;
        matchN = matchN <= dim.length ? matchN : dim.length;

        for (int i = 0; i < matchN; i++) {
            boolean match = false;
            for (int j = 0; j < dim.length; j++) {
                if (theFile.getLabel(j).equals(matchAttr.theFile.getLabel(matchDim[i]))) {
                    dim[i] = j;
//                    System.out.println(i + " " + j + " " + theFile.getName() + " " + theFile.getLabel(j));
                    match = true;
                }
            }
            if (!match) {
                for (int j = 0; j < dim.length; j++) {
                    if (theFile.getNucleus(j).toString().equals(matchAttr.theFile.getNucleus(matchDim[i]).toString())) {
                        dim[i] = j;
                        match = true;
                    }
                }
            }
        }
        fixDim();
    }

    public synchronized void setDim(int dataDim, int userDim) {
        if ((userDim >= 0) && (userDim < dim.length) && (dataDim >= 0)
                && (dataDim < theFile.getNDim())) {
            dim[userDim] = dataDim;
            fixDim();
        }
    }

    public synchronized void rotateDim(int userDim, int increment) {
        if (theFile.getNDim() > 1) {
            int dataDim = dim[userDim];
            dataDim += increment;
            if (dataDim >= theFile.getNDim()) {
                dataDim = 1;
            } else if (dataDim < 1) {
                dataDim = theFile.getNDim() - 1;
            }
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
            //         System.out.println("set bounds " + i + " " + pt[i][0] + " " + pt[i][1] + " " + limits[i][0] + " " + limits[i][1]);

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
            //  System.out.println("set pt bounds " + i + " " + pt[i][0] + " " + pt[i][1] + " " + limits[i][0] + " " + limits[i][1]);

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

    public double[] checkLimits(AXMODE axMode, int dNum, double min, double max) {
        double[] range = getRange(axMode, dNum);
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        double delta = max - min;
        double maxDelta = range[1] - range[0];
        if (delta > maxDelta) {
            delta = maxDelta;
        }
        if (min < range[0]) {
            min = range[0];
            max = min + delta;
        }
        if (max > range[1]) {
            max = range[1];
            min = max - delta;
        }

        double[] result = {min, max};
        return result;

    }

    public void checkLimits(AXMODE axMode, int dNum, double[] values) {
        double[] range = getRange(axMode, dNum);
        double min = values[0];
        double max = values[1];
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        double delta = max - min;
        double maxDelta = range[1] - range[0];
        if (delta > maxDelta) {
            delta = maxDelta;
        }
        if (min < range[0]) {
            min = range[0];
            max = min + delta;
        }
        if (max > range[1]) {
            max = range[1];
            min = max - delta;
        }
        values[0] = min;
        values[1] = max;
    }

    public void checkRange(AXMODE axMode, int dNum, double[] values) {
        double[] range = getRange(axMode, dNum);
        if (range[0] < values[0]) {
            values[0] = range[0];
        }
        if (range[1] > values[1]) {
            values[1] = range[1];
        }
    }

    public double[] getRange(AXMODE mode, int i) {
        double[] limit;
        limit = new double[2];
        // System.out.printf("%s %.4f %.4f %.4f %.4f\n", "get range ", theFile.getRefPt(0), theFile.getRefPt_r(0), theFile.getRefValue(0), theFile.getRefValue_r(0));
        if (theFile.getVec() != null) {
            //System.out.printf("%.4f\n", theFile.vecMat.refValue);
        }
        if (i >= theFile.getNDim()) {
            limit[0] = 0.0;
            limit[1] = 0.0;
        } else if (mode == AXMODE.PTS) {
            limit[0] = 0;
            limit[1] = theFile.getSize(dim[i]) - 1.0;
        } else if (mode == AXMODE.TIME) {
            limit[0] = 0;
            limit[1] = (theFile.getSize(dim[i]) - 1) / theFile.getSw(dim[i]);
        } else if (mode == AXMODE.PPM) {
            limit[1] = (double) theFile.pointToPPM(dim[i], 0.0);
            limit[0] = (double) theFile.pointToPPM(dim[i],
                    (double) (theFile.getSize(dim[i]) - 1));
        }
        //System.out.println("range " + limit[0] + " " + limit[1]);

        return (limit);
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
                ((DatasetAttributes) o).setPosColor(getPosColor());
            ((DatasetAttributes) o).setNegColor(getNegColor());
            ((DatasetAttributes) o).setPosLineWidth(getPosLineWidth());
            ((DatasetAttributes) o).setNegLineWidth(getNegLineWidth());
            ((DatasetAttributes) o).setLevel(getLevel());
            ((DatasetAttributes) o).clm = clm;
            ((DatasetAttributes) o).setNLevels(getNLevels());
            ((DatasetAttributes) o).nDim = nDim;
            ((DatasetAttributes) o).fileName = fileName;
            ((DatasetAttributes) o).theFile = theFile;
            ((DatasetAttributes) o).setPosDrawOn(getPosDrawOn());
            ((DatasetAttributes) o).setNegDrawOn(getNegDrawOn());
            if (drawList != null) {
                ((DatasetAttributes) o).drawList = drawList.clone();
            }

     */
    public void config(String name, Object value) {
        if (Platform.isFxApplicationThread()) {
            try {
                PropertyUtils.setSimpleProperty(this, name, value);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                Logger.getLogger(DatasetAttributes.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            Platform.runLater(() -> {
                try {
                    PropertyUtils.setProperty(this, name, value);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                    Logger.getLogger(DatasetAttributes.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            );
        }
    }

    public Map<String, Object> config() {

//        BeanInfo info = null;
//        try {
//            info = Introspector.getBeanInfo(DatasetAttributes.class);
//        } catch (IntrospectionException ex) {
//            Logger.getLogger(DatasetAttributes.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        if (info != null) {
//            
//            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
//                System.out.println(pd.getName() + " : " + pd.getReadMethod() + " : " + pd.getWriteMethod());
//            }
//        }
        Map<String, Object> data = new HashMap<>();
        String[] beanNames = {"nlevels", "clm", "posColor", "negColor", "posLineWidth", "negLineWidth", "level", "posDrawOn", "negDrawOn"};
        for (String beanName : beanNames) {
            try {
                data.put(beanName, PropertyUtils.getSimpleProperty(this, beanName));
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                Logger.getLogger(DatasetAttributes.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return data;
    }
}
