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

import javafx.beans.property.*;
import javafx.scene.paint.Color;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.math.VecBase;
import org.nmrfx.processor.DatasetUtils;
import org.nmrfx.processor.datasets.DataCoordTransformer;
import org.nmrfx.processor.datasets.DataGenerator;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart.DISDIM;
import org.nmrfx.processor.gui.PolyChartAxes;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.utils.properties.ColorProperty;
import org.nmrfx.utils.properties.PublicPropertyContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@PluginAPI("parametric")
public class DatasetAttributes extends DataGenerator implements PublicPropertyContainer, Cloneable {
    private static final Logger log = LoggerFactory.getLogger(DatasetAttributes.class);

    private final IntegerProperty nlvls = new SimpleIntegerProperty(this, "nlvls", 20);
    private final ColorProperty posColor = new ColorProperty(this, "posColor", Color.BLACK);
    private final ColorProperty negColor = new ColorProperty(this, "negColor", Color.RED);
    private final DoubleProperty posWidth = new SimpleDoubleProperty(this, "posWidth", 0.5);
    private final DoubleProperty negWidth = new SimpleDoubleProperty(this, "negWidth", 0.5);
    private final DoubleProperty lvl = new SimpleDoubleProperty(this, "lvl", 1.0);
    private final DoubleProperty clm = new SimpleDoubleProperty(this, "clm", 1.2);
    private final DoubleProperty offset = new SimpleDoubleProperty(this, "offset", 0.0);
    private final DoubleProperty integralScale = new SimpleDoubleProperty(this, "integralScale", 100.0);
    private final BooleanProperty pos = new SimpleBooleanProperty(this, "pos", true);
    private final BooleanProperty neg = new SimpleBooleanProperty(this, "neg", true);
    private final StringProperty fileName = new SimpleStringProperty(this, "fileName", "");
    private final BooleanProperty drawReal = new SimpleBooleanProperty(this, "drawReal", true);

    private final Map<String, Float> extremes = new HashMap<>();
    private final Map<Integer, Color> colorMap = new HashMap<>();
    private final Map<Integer, Double> offsetMap = new HashMap<>();
    private final Set<Integer> selectionSet = new HashSet<>();
    public boolean selected;

    public int mChunk = 0;
    public int[] dim;
    public List<Integer> drawList = new ArrayList<>();
    public boolean intSelected;
    public String title = "";
    int[] chunkSize;
    int[] chunkOffset;
    int[][] iLim;
    int[] iSize;
    int[] iBlkSize;
    int[] iNBlks;
    int[] iVecGet;
    int[] iVecPut;
    int[] iBlkGet;
    int[] iBlkPut;
    int[] iWDim;
    private Dataset theFile;
    private IntegralHit activeRegion = null;

    // used to tell if dataset has a level value already so we don't need to call autoLevel.
    // used in processing same dataset multiple times, so it's easier to compare the processing without the level changing
    private boolean hasLevel = false;
    private int projectionAxis = -1;

    public DatasetAttributes(DatasetBase aFile, String fileName) {
        initialize(aFile, fileName);
    }

    public DatasetAttributes(DatasetBase aFile) {
        initialize(aFile, aFile.getFileName());
    }

    @Override
    public Collection<Property<?>> getPublicProperties() {
        // Some properties were not exposed before refactoring.
        // It's not clear whether this was by design or by error.
        // I've opted to keep the smallest set of properties possible exposed, so I've only kept the original list.
        return Set.of(nlvls, clm, posColor, negColor, posWidth, negWidth, lvl, pos, neg);
    }

    public ColorProperty posColorProperty() {
        return posColor;
    }

    public void setPosColor(Color value, int row) {
        if (!colorMap.isEmpty()) {
            colorMap.put(row, value);
        } else {
            setPosColor(value);
        }
    }

    public Color getPosColor() {
        return posColorProperty().get();
    }

    public void setPosColor(Color value) {
        posColorProperty().set(value);
    }

    public Color getPosColor(int rowIndex) {
        Color color = null;
        if (rowIndex != -1) {
            color = colorMap.get(rowIndex);
        }
        if (color == null) {
            color = getPosColor();
        }
        return color;
    }

    public ColorProperty negColorProperty() {
        return negColor;
    }

    public Color getNegColor() {
        return negColorProperty().get();
    }

    public void setNegColor(Color value) {
        negColorProperty().set(value);
    }

    public DoubleProperty posWidthProperty() {
        return posWidth;
    }

    public double getPosWidth() {
        return posWidthProperty().get();
    }

    public void setPosWidth(double value) {
        posWidthProperty().set(value);
    }

    public DoubleProperty negWidthProperty() {
        return negWidth;
    }

    public double getNegWidth() {
        return negWidthProperty().get();
    }

    public void setNegWidth(double value) {
        negWidthProperty().set(value);
    }

    public DoubleProperty lvlProperty() {
        return lvl;
    }

    public double getLvl() {
        return lvlProperty().get();
    }

    public void setLvl(double value) {
        lvlProperty().set(value);
    }

    public DoubleProperty offsetProperty() {
        return offset;
    }

    public double getOffset() {
        return offsetProperty().get();
    }

    public void setOffset(double value) {
        offsetProperty().set(value);
    }

    public DoubleProperty integralScaleProperty() {
        return integralScale;
    }

    public double getIntegralScale() {
        return integralScaleProperty().get();
    }

    public void setIntegralScale(double value) {
        integralScaleProperty().set(value);
    }

    public BooleanProperty posProperty() {
        return pos;
    }

    public void setPos(Boolean value, int row) {
        if (!drawList.isEmpty()) {
            if (Boolean.TRUE.equals(value)) {
                addToDrawList(row);
                setPos(value);
            } else {
                drawList.remove(Integer.valueOf(row));
            }
        } else {
            setPos(value);
        }
    }

    public boolean getPos() {
        return posProperty().get();
    }

    public void setPos(boolean value) {
        posProperty().set(value);
    }

    public boolean getPos(int index) {
        if (!drawList.isEmpty()) {
            return posProperty().get() && drawList.contains(index);
        } else {
            return posProperty().get();
        }
    }

    public BooleanProperty negProperty() {
        return neg;
    }

    public void setNeg(Boolean value, int row) {
        if (!drawList.isEmpty()) {
            if (value) {
                setNeg(value);
            }
        } else {
            setNeg(value);
        }
    }

    public boolean getNeg() {
        return negProperty().get();
    }

    public void setNeg(boolean value) {
        negProperty().set(value);
    }

    public boolean getNeg(int index) {
        if (!drawList.isEmpty()) {
            return negProperty().get() && drawList.contains(index);
        } else {
            return negProperty().get();
        }
    }

    public IntegerProperty nlvlsProperty() {
        return nlvls;
    }

    public int getNlvls() {
        return nlvlsProperty().get();
    }

    public void setNlvls(int value) {
        nlvlsProperty().set(value);
    }

    public DoubleProperty clmProperty() {
        return clm;
    }

    public double getClm() {
        return clmProperty().get();
    }

    public void setClm(double value) {
        clmProperty().set(value);
    }

    public void projection(int value) {
        projectionAxis = value;
    }

    public boolean isProjection() {
        return projectionAxis != -1;
    }

    public int projection() {
        return projectionAxis;
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public String getFileName() {
        return fileNameProperty().get();
    }

    public void setFileName(String value) {
        fileNameProperty().set(value);
    }

    public BooleanProperty drawRealProperty() {
        return drawReal;
    }

    public boolean getDrawReal() {
        return drawRealProperty().get();
    }

    public void setDrawReal(boolean value) {
        drawRealProperty().set(value);
    }

    public DatasetBase getDataset() {
        return theFile;
    }

    public void setDataset(DatasetBase aFile) {
        hasLevel = false;
        theFile = (Dataset) aFile;
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
        setActiveRegion(null);
        int i;

        for (i = 0; i < theFile.getNDim(); i++) {
            pt[i][0] = 0;
            ptd[i][0] = 0;
            pt[i][1] = theFile.getSizeReal(i) - 1;
            ptd[i][1] = theFile.getSizeReal(i) - 1.0;
        }

        pt[0][0] = 0;
        pt[0][1] = theFile.getSizeReal(0) - 1;

        if (theFile.getNDim() > 1) {
            pt[1][0] = 0;
            ptd[1][0] = 0;
            pt[1][1] = theFile.getSizeReal(1) - 1;
            ptd[1][1] = theFile.getSizeReal(1) - 1.0;
        }

        chunkSize = new int[theFile.getNDim()];
        chunkOffset = new int[theFile.getNDim()];
        dim = new int[theFile.getNDim()];

        for (i = 0; i < theFile.getNDim(); i++) {
            dim[i] = i;
            chunkSize[i] = 1;
        }

    }

    public Optional<IntegralHit> getActiveRegion() {
        return Optional.ofNullable(activeRegion);
    }

    public void setActiveRegion(IntegralHit activeRegion) {
        this.activeRegion = activeRegion;
    }

    public void setMapColor(int index, String colorName) {
        Color color = Color.web(colorName);
        colorMap.put(index, color);
    }

    public void setMapColor(int index, Color color) {
        colorMap.put(index, color);
    }

    public void setMapColors(Map<Integer, Color> map) {
        colorMap.clear();
        colorMap.putAll(map);
    }

    public void clearColors() {
        colorMap.clear();
    }

    public Color getMapColor(int index) {
        Color color = null;
        if (index != -1) {
            color = colorMap.get(index);
        }
        if (color == null) {
            color = getPosColor();
        }
        return color;
    }

    public void setMapOffset(int index, double offset) {
        offsetMap.put(index, offset);
    }

    public void clearOffsets() {
        offsetMap.clear();
    }

    public void setMapOffsets(Map<Integer, Double> map) {
        offsetMap.clear();
        offsetMap.putAll(map);
    }

    public double getMapOffset(int index) {
        Double offset = null;
        if (index != -1) {
            offset = offsetMap.get(index);
        }
        if (offset == null) {
            offset = 0.0;
        }
        return offset;
    }

    public void copyTo(DatasetAttributes dAttr) {
        dAttr.dim = getDims();
        dAttr.setPosColor(getPosColor());
        dAttr.setNegColor(getNegColor());
        dAttr.setPosWidth(getPosWidth());
        dAttr.setNegWidth(getNegWidth());
        dAttr.setLvl(getLvl());
        dAttr.setClm(getClm());
        dAttr.setNlvls(getNlvls());
        dAttr.setFileName(getFileName());
        dAttr.setPos(getPos());
        dAttr.setNeg(getNeg());
        dAttr.nDim = nDim;
        dAttr.theFile = theFile;
        if (drawList.isEmpty()) {
            dAttr.drawList = new ArrayList<>();
        } else {
            dAttr.drawList = new ArrayList<>();
            dAttr.drawList.addAll(drawList);
        }
        dAttr.selectionSet.clear();
        dAttr.selectionSet.addAll(selectionSet);
        dAttr.selected = selected;
    }

    @Override
    public Object clone() {
        Object o;
        DatasetAttributes dAttr = null;
        try {
            o = super.clone();
            dAttr = (DatasetAttributes) o;
            copyTo(dAttr);
        } catch (CloneNotSupportedException e) {
            log.warn(e.getMessage(), e);
        }

        return dAttr;
    }

    @Override
    public String toString() {
        return getDataset() != null ? getDataset().getName() : "";
    }

    public boolean getHasLevel() {
        return hasLevel;
    }

    public void setHasLevel(boolean value) {
        hasLevel = value;
    }

    private void initialize(DatasetBase aFile, String fileName) {
        theFile = (Dataset) aFile;
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
            pt[i][1] = theFile.getSizeReal(i) - 1;
            ptd[i][1] = theFile.getSizeReal(i) - 1.0;
        }

        pt[0][0] = 0;
        pt[0][1] = theFile.getSizeReal(0) - 1;

        if (theFile.getNDim() > 1) {
            pt[1][0] = 0;
            ptd[1][0] = 0;
            pt[1][1] = theFile.getSizeReal(1) - 1;
            ptd[1][1] = theFile.getSizeReal(1) - 1.0;
        }

        chunkSize = new int[theFile.getNDim()];
        chunkOffset = new int[theFile.getNDim()];
        dim = new int[theFile.getNDim()];

        for (i = 0; i < theFile.getNDim(); i++) {
            dim[i] = i;
            chunkSize[i] = 1;
        }

        if (theFile.getLvl() > 0) {
            setLvl(theFile.getLvl());
        }
        setPosColor(Color.valueOf(theFile.getPosColor()));
        setNegColor(Color.valueOf(theFile.getNegColor()));
        setNeg(theFile.getNegDrawOn());
        setPos(theFile.getPosDrawOn());
        hasLevel = false;
    }

    public boolean valid() {
        return (Dataset.getDataset(theFile.getFileName()) != null) && ((theFile.getVec() != null) || theFile.hasDataFile());
    }

    public int getDrawListIndex(int i) {
        if (drawList.isEmpty() || i >= drawList.size()) {
            return 0;
        } else {
            return drawList.get(i);
        }
    }
    public void setDrawListSize(final int size) {
        drawList.clear();
    }

    public void incrDrawList(int delta) {

        if (drawList.isEmpty()) {
            setDrawList(0);
        } else {
            int value = drawList.get(0) + delta;

            if (value < 0) {
                value = 0;
            }
            if (value >= theFile.getSizeReal(1)) {
                value = theFile.getSizeReal(1) - 1;
            }
            setDrawList(value);
        }
    }

    public void setDrawList(int index) {
        drawList.clear();
        drawList.add(index);
    }

    public void addToDrawList(int index) {
        if (!drawList.contains(index)) {
            drawList.add(index);
            Collections.sort(drawList);
        }
    }

    public void setDrawList(List<Integer> indices) {
        drawList.clear();
        if (!indices.isEmpty()) {
            if (dim.length > 1) {
                indices.stream().filter(i -> i >= 0 && i < theFile.getSizeReal(dim[1])).
                        forEach(drawList::add);
            }
        }
    }

    public int getLastChunk(int iDim) {
        if (theFile.getNDim() < 2) {
            return (0);
        } else if (drawList.isEmpty()) {
            int iLast = 0;
            for (int i = 0; i < pt.length; i++) {
                if (i != iDim) {
                    iLast += Math.abs(pt[i][1] - pt[i][0]);
                }
            }
            return iLast;
        } else {
            return drawList.size() - 1;
        }
    }

    public void getProjection(Dataset dataset, Vec specVec, int iDim) throws IOException {
        int[][] ptC = new int[1][2];
        int[] dimC = new int[pt.length];
        double ppm0 = theFile.pointToPPM(dim[iDim], pt[iDim][0]);
        double ppm1 = theFile.pointToPPM(dim[iDim], pt[iDim][1]);
        dimC[0] = 0;
        ptC[0][0] = dataset.ppmToPoint(0, ppm0);
        ptC[0][1] = dataset.ppmToPoint(0, ppm1);
        specVec.resize(ptC[0][1] - ptC[0][0] + 1, dataset.getComplex(0));
        dataset.readVectorFromDatasetFile(DatasetUtils.generateRawIndices(ptC, dataset.getComplex(0)), dimC, specVec);
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
            if (theFile.getComplex(jDim)) {
                offset *= 2;
            }
            ptC[0][0] = offset;
            ptC[0][1] = offset;
        }
        if (iDim == 2) {
            ptC[2][0] = 0;
            ptC[2][1] = theFile.getSizeReal(dim[2]) - 1;
        } else if (iDim == 3) {
            ptC[3][0] = 0;
            ptC[3][1] = theFile.getSizeReal(dim[3]) - 1;
        }
        rearrangeDim(dimC, ptC);
        int size = ptC[0][1] - ptC[0][0] + 1;
        if ((iDim == 0) && theFile.getComplex(0)) {
            ptC[0][0] *= 2;
            ptC[0][1] *= 2;
        }

        specVec.resize(size, theFile.getComplex_r(dimC[0]));
        theFile.readVectorFromDatasetFile(ptC, dimC, specVec);
        return true;
    }

    public void rearrangeDim(int[] dim, int[][] pt) {
        int iDim = 0;
        for (int i = 0; i < pt.length; i++) {
            int size = Math.abs(pt[i][0] - pt[i][1]) + 1;
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
            if (drawList.isEmpty()) {
                rowIndex = pt[iDim][0] + iChunk;
            } else if (iChunk < 0) {
            } else {
                rowIndex = drawList.get(iChunk);
            }
        }
        return rowIndex;
    }

    public void updateBounds(PolyChartAxes axes, DISDIM disDim) {
        int[][] localPt;
        double[][] limits;
        localPt = new int[nDim][2];
        double[][] localPtD = new double[nDim][2];
        limits = new double[nDim][2];
        for (int i = 0; i < nDim; i++) {
            if (i == 0) {
                localPtD[i][0] = axes.getMode(i).getIndexD(this, i, axes.get(0).getLowerBound());
                localPtD[i][1] = axes.getMode(i).getIndexD(this, i, axes.get(0).getUpperBound());
            } else if (i == 1) {
                if (disDim == DISDIM.TwoD) {
                    localPtD[i][0] = axes.getMode(i).getIndexD(this, i, axes.get(1).getLowerBound());
                    localPtD[i][1] = axes.getMode(i).getIndexD(this, i, axes.get(1).getUpperBound());
                } else {
                    localPtD[i][0] = 0;
                    localPtD[i][1] = theFile.getSizeReal(dim[i]) - 1.0;
                }
            } else if (axes.count() <= i) {
                localPtD[i][0] = theFile.getSizeReal(dim[i]) / 2.0;
                localPtD[i][1] = theFile.getSizeReal(dim[i]) / 2.0;
            } else {
                if (Objects.nonNull(axes.getMode(i)) && Objects.nonNull(axes.get(i))) {
                    localPtD[i][0] = axes.getMode(i).getIndexD(this, i, axes.get(i).getLowerBound());
                    localPtD[i][1] = axes.getMode(i).getIndexD(this, i, axes.get(i).getUpperBound());
                }
            }
            if (localPtD[i][0] > localPtD[i][1]) {
                double holdD = localPtD[i][0];
                localPtD[i][0] = localPtD[i][1];
                localPtD[i][1] = holdD;
            }
            if (i > 1) {
                if (Math.abs(localPtD[i][0] - localPtD[i][1]) < 0.5) {
                    localPt[i][0] = (int) Math.round(localPtD[i][0]);
                    localPt[i][1] = (int) Math.round(localPtD[i][1]);
                } else {
                    localPt[i][0] = (int) Math.floor(localPtD[i][0]);
                    localPt[i][1] = (int) Math.ceil(localPtD[i][1]);
                }
            } else {

                localPt[i][0] = (int) Math.floor(localPtD[i][0]);
                localPt[i][1] = (int) Math.ceil(localPtD[i][1]);
            }
        }
        setPtBounds(localPt, localPtD, limits);
    }

    public boolean getIntegralVec(Vec specVec, int iChunk, double ppm1, double ppm2, double[] offsets) throws IOException {
        int[][] ptC = new int[pt.length][2];
        int[] dimC = new int[pt.length];
        int iDim = 1;
        int minDimSize = Integer.MAX_VALUE;
        for (int i = 0; i < pt.length; i++) {
            ptC[i][0] = pt[i][0];
            ptC[i][1] = pt[i][1];
            int size = Math.abs(pt[i][0] - pt[i][1]);
            if ((i > 0) && (size < minDimSize)) {
                minDimSize = size;
                iDim = i;
            }
            dimC[i] = dim[i];
        }

        if (theFile.getNDim() > 1) {
            if (drawList.isEmpty()) {
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
                ptC[1][0] = drawList.get(iChunk);
                ptC[1][1] = drawList.get(iChunk);
            }

        } else if ((iChunk < 0) || (iChunk > 1)) {
            return (false);
        }
        rearrangeDim(dimC, ptC);
        int pt1 = theFile.ppmToPoint(dimC[0], ppm1);
        int pt2 = theFile.ppmToPoint(dimC[0], ppm2);
        if (pt2 < pt1) {
            int hold = pt1;
            pt1 = pt2;
            pt2 = hold;
        }

        int dimSize = pt2 - pt1 + 1;
        specVec.resize(dimSize, false);
        ptC[0][0] = pt1;
        ptC[0][1] = pt2;

        if (theFile.getVec() == null) {
            theFile.readVectorFromDatasetFile(DatasetUtils.generateRawIndices(ptC, theFile.getComplex(dim[0])), dimC, specVec);
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

        if (offsets != null) {
            specVec.integrate(0, dimSize, offsets[1], offsets[0]);
        } else {
            specVec.integrate(0, dimSize);
        }
        return true;
    }

    public boolean VectorIntegral(Vec specVec, int iChunk, double[] ppms, double[] offsets) throws IOException {
        int[][] ptC = new int[pt.length][2];
        int[] dimC = new int[pt.length];
        int iDim = 1;
        int minDimSize = Integer.MAX_VALUE;
        for (int i = 0; i < pt.length; i++) {
            ptC[i][0] = pt[i][0];
            ptC[i][1] = pt[i][1];
            int size = Math.abs(pt[i][0] - pt[i][1]);
            if ((i > 0) && (size < minDimSize)) {
                minDimSize = size;
                iDim = i;
            }
            dimC[i] = dim[i];
        }

        if (theFile.getNDim() > 1) {
            if (drawList.isEmpty()) {
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
                ptC[1][0] = drawList.get(iChunk);
                ptC[1][1] = drawList.get(iChunk);
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

    public boolean Vector(VecBase specVec, int iChunk) throws IOException {
        int[][] ptC = new int[pt.length][2];
        int[] dimC = new int[pt.length];
        int iDim = 1;
        int minDimSize = Integer.MAX_VALUE;
        for (int i = 0; i < pt.length; i++) {
            ptC[i][0] = pt[i][0];
            ptC[i][1] = pt[i][1];
            int size = Math.abs(pt[i][0] - pt[i][1]);
            if ((i > 0) && (size < minDimSize)) {
                minDimSize = size;
                iDim = i;
            }
            dimC[i] = dim[i];
        }

        if (theFile.getNDim() > 1) {
            if (drawList.isEmpty()) {
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
                ptC[1][0] = drawList.get(iChunk);
                ptC[1][1] = drawList.get(iChunk);
            }

        } else if ((iChunk < 0) || (iChunk > 1)) {
            return (false);
        }
        rearrangeDim(dimC, ptC);
        specVec.resize(ptC[0][1] - ptC[0][0] + 1, theFile.getComplex(dimC[0]));
        Vec vec = theFile.getVec();
        if (vec == null) {
            if (specVec.isComplex()) {
                ptC[0][0] *= 2;
                ptC[0][1] *= 2;
            }

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
        theFile.readMatrix(apt, dim, matrix);

        return (matrix);
    }

    public float[][] readMatrix(int iChunk, String chunkLabelStr, int[][] apt, float[][] matrix) throws IOException {
        int ny = apt[1][1] - apt[1][0] + 1;
        int nx = apt[0][1] - apt[0][0] + 1;
        if ((matrix == null) || (matrix.length != ny) || (matrix[0].length != nx)) {
            matrix = new float[ny][nx];
        }

        float maxValue = theFile.readMatrix(apt, dim, matrix);
        extremes.put(chunkLabelStr + iChunk, maxValue);

        return (matrix);
    }

    public int getMatrixRegion(int iChunk, int maxChunk, int mode, int[][] apt,
                               double[] offset, StringBuffer chunkLabel) {
        Float extremeValue;
        boolean fastMode = false;
        chunkLabel.append(dim[0]).append(".");
        chunkSize[0] = maxChunk;

        if (theFile.getNDim() > 1) {
            chunkSize[1] = maxChunk;
        }

        chunkOffset[0] = 1;

        int[] chunk = new int[theFile.getNDim()];
        int i;

        for (i = 1; i < theFile.getNDim(); i++) {
            chunkLabel.append(dim[i]).append(".");
            int dimSize = theFile.getSizeReal(dim[i - 1]);

            if (i > 1) {
                chunkSize[i] = 1;
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
                    if (!drawList.isEmpty()) {
                        if (chunk[i] >= drawList.size()) {
                            return 1;
                        }
                        apt[i][0] = drawList.get(chunk[i]);
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
                }
                if (apt[i][1] < pt[i][0]) {
                    ok = false;
                }
                if (apt[i][0] > pt[i][1]) {
                    ok = false;
                }

                if (mode != 1) {
                    if (apt[i][1] > pt[i][1]) {
                        apt[i][1] = pt[i][1];
                    }

                    if (apt[i][0] < pt[i][0]) {
                        apt[i][0] = pt[i][0];
                    }
                }

                if (apt[i][1] >= theFile.getSizeReal(dim[i])) {
                    apt[i][1] = theFile.getSizeReal(dim[i]) - 1;
                }

            }

            mChunk = iChunk;

            if (ok) {
                if (!fastMode) {
                    break;
                }

                extremeValue = extremes.get(chunkLabel.toString()
                        + iChunk);

                if (extremeValue == null) {
                    break;
                } else if (extremeValue > lvl.get()) {
                    break;
                }
            }

            iChunk++;
        }
        offset[0] = apt[0][0] - ptd[0][0];
        offset[1] = apt[1][0] - ptd[1][0];

        return (0);
    }

    public int[][] bounds(int iChunk) {
        return (pt);
    }

    public DataCoordTransformer setBounds(double[][] limits) {
        for (int i = 0; ((i < theFile.getNDim()) && (i < limits.length)); i++) {
            pt[i][0] = theFile.ppmToPoint(dim[i], limits[i][0]);
            ptd[i][0] = theFile.ppmToDPoint(dim[i], limits[i][0]);
            pt[i][1] = theFile.ppmToPoint(dim[i], limits[i][1]);
            ptd[i][1] = theFile.ppmToDPoint(dim[i], limits[i][1]);

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

        return new DataCoordTransformer(dim, theFile);
    }

    public int nRows(int iChunk) {
        return (32);
    }

    public int nCols(int iChunk) {
        return (32);
    }

    public String getLabel(int iDim) {
        String label = "";
        if (iDim < dim.length) {
            label = theFile.getLabel(dim[iDim]);
        }
        return label;

    }

    public int[] getDims() {
        return dim.clone();
    }

    public void setDims(int[] newDims) {
        for (int i = 0; i < newDims.length; i++) {
            setDim(newDims[i], i);
        }
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
        int[] matchDim = matchAttr.dim;
        int matchN = matchDim.length;
        matchN = Math.min(matchN, dim.length);

        for (int i = 0; i < matchN; i++) {
            boolean match = false;
            for (int j = 0; j < dim.length; j++) {
                if (theFile.getLabel(j).equals(matchAttr.theFile.getLabel(matchDim[i]))) {
                    dim[i] = j;
                    match = true;
                }
            }
            if (!match) {
                for (int j = 0; j < dim.length; j++) {
                    if (theFile.getNucleus(j).toString().equals(matchAttr.theFile.getNucleus(matchDim[i]).toString())) {
                        dim[i] = j;
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

    public double[][] setPtBounds(int[][] ilimits, double[][] dLimits, double[][] limits) {
        int i;
        double ptf;

        for (i = 0; ((i < theFile.getNDim()) && (i < ilimits.length)); i++) {
            pt[i][0] = ilimits[i][0];
            ptf = pt[i][0];
            limits[i][0] = theFile.pointToPPM(dim[i], ptf);
            pt[i][0] = theFile.ppmToPoint(dim[i], limits[i][0]);
            pt[i][1] = ilimits[i][1];
            ptf = pt[i][1];
            limits[i][1] = theFile.pointToPPM(dim[i], ptf);
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
            ptd[i][0] = dLimits[i][0];
            ptd[i][1] = dLimits[i][1];
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

        return new double[]{min, max};

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
        if (i >= theFile.getNDim()) {
            limit[0] = 0.0;
            limit[1] = 0.0;
        } else if (mode == AXMODE.PTS) {
            limit[0] = 0;
            limit[1] = theFile.getSizeReal(dim[i]) - 1.0;
        } else if (mode == AXMODE.TIME) {
            limit[0] = 0;
            limit[1] = (theFile.getSizeReal(dim[i]) - 1) / theFile.getSw(dim[i]);
        } else if (mode == AXMODE.PPM) {
            limit[1] = theFile.pointToPPM(dim[i], 0.0);
            limit[0] = theFile.pointToPPM(dim[i],
                    (theFile.getSizeReal(dim[i]) - 1));
        }

        return (limit);
    }

    public double[] getMaxLimits(int i) {
        double[] limit;
        limit = new double[2];

        if (i >= theFile.getNDim()) {
            limit[0] = 0.0;
            limit[1] = 0.0;
        } else {
            limit[0] = theFile.pointToPPM(dim[i], 0.0);
            limit[1] = theFile.pointToPPM(dim[i],
                    (theFile.getSizeReal(dim[i]) - 1));
        }

        return (limit);
    }

    public double getFoldPPM(int i) {
        return theFile.getSw(dim[i]) / theFile.getSf(dim[i]);
    }

    public double getPlaneThickness(int i) {

        return theFile.getSw(dim[i]) / theFile.getSf(dim[i]) / (theFile.getSizeReal(dim[i])
                - 1);
    }

    public int[] getMaxLimitsPt(int i) {
        int[] limit;
        limit = new int[2];

        if (i < theFile.getNDim()) {
            limit[1] = theFile.size(dim[i]) - 1;
        }

        return (limit);
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

    public void setSelectedElem(int iElem, boolean state) {
        if (state) {
            selectionSet.add(iElem);
        } else {
            selectionSet.remove(iElem);
        }
        selected = state;
    }

    public int[] getSelected() {
        int[] result = new int[0];
        List<Integer> resultList = new ArrayList<>();
        for (int i : selectionSet) {
            if (!drawList.isEmpty()) {
                resultList.add(i);
            } else if (pt.length > 1) {
                resultList.add(i);
            }
        }
        result = new int[resultList.size()];
        int i = 0;
        for (Integer iVal : resultList) {
            result[i++] = iVal;

        }
        return result;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean state) {
        selected = state;
        if (!state) {
            selectionSet.clear();
        }
    }

    public boolean isSelected(int iElem) {
        boolean value;
        if ((iElem >= 0) && !selectionSet.isEmpty()) {
            value = selectionSet.contains(iElem);
        } else {
            value = selected;
        }
        return value;
    }

    public boolean getIntegralSelected() {
        return intSelected;
    }

    public void setIntegralSelected(boolean state) {
        intSelected = state;
    }

    public double[] getRegionAsArray() {
        List<DatasetRegion> regions = theFile.getReadOnlyRegions();
        double[] ppms = null;
        if (regions != null) {
            ppms = new double[regions.size() * 2];

            int i = 0;
            for (DatasetRegion region : regions) {
                ppms[i++] = region.getRegionStart(0);
                ppms[i++] = region.getRegionEnd(0);
            }
        }
        return ppms;
    }

    public double[] getOffsetsAsArray() {
        List<DatasetRegion> regions = theFile.getReadOnlyRegions();
        double[] offsets = null;
        if (regions != null) {
            offsets = new double[regions.size() * 2];
            int i = 0;
            for (DatasetRegion region : regions) {
                offsets[i++] = region.getRegionStartIntensity(0);
                offsets[i++] = region.getRegionEndIntensity(0);
            }
        }
        return offsets;
    }

    public void moveRegion(IntegralHit iHit, PolyChartAxes axes, double[] newValue) {
        int handle = iHit.handle;
        DatasetRegion r = iHit.getDatasetRegion();
        double newX = axes.getX().getValueForDisplay(newValue[0]).doubleValue();
        double newY = axes.getY().getValueForDisplay(newValue[1]).doubleValue();
        switch (handle) {
            case 1:
                double oldEnd = r.getRegionEndIntensity(0);
                double deltaEnd = oldEnd - r.getRegionStartIntensity(0);
                r.setRegionStartIntensity(0, newY);
                r.setRegionEndIntensity(0, newY + deltaEnd);
                measureRegion(r, "Error encountered moving region start and end intensity.");
                break;
            case 2:
                r.setRegionEndIntensity(0, newY);
                measureRegion(r, "Error encountered moving region end intensity.");
                break;
            case 3:
                r.setRegionEnd(0, newX);
                measureRegion(r, "Error encountered moving region end.");
                break;
            case 4:
                r.setRegionStart(0, newX);
                measureRegion(r, "Error encountered moving region start.");
                break;
            default:
                break;
        }
    }

    private void measureRegion(DatasetRegion region, String errMsg) {
        try {
            region.measure(getDataset());
        } catch (IOException e) {
            log.warn("{} {}", errMsg, e.getMessage(), e);
        }
        region.setAuto(false);
    }

    public int[] getMatchDim(DatasetAttributes dataAttr2, boolean looseMode) {
        int nMatchDim = dataAttr2.nDim;
        int[] dimMatches = new int[nDim];
        // Assume all dims are initially not matched
        Arrays.fill(dimMatches, -1);
        int nMatch = 0;
        int nShouldMatch = 0;
        boolean[] used = new boolean[nMatchDim];
        int nAxes = 2;

        for (int i = 0; (i < nAxes) && (i < dimMatches.length); i++) {
            dimMatches[i] = -1;
            nShouldMatch++;
            for (int j = 0; j < nMatchDim; j++) {
                if (getLabel(i).equals(dataAttr2.getLabel(j))) {
                    dimMatches[i] = j;
                    nMatch++;
                    used[j] = true;
                    break;
                }
            }
        }

        if ((nMatch != nShouldMatch) && looseMode) {
            for (int i = 0; (i < nAxes) && (i < dimMatches.length); i++) {
                if (dimMatches[i] == -1) {
                    for (int j = 0; j < nMatchDim; j++) {
                        if (!used[j]) {
                            String dNuc = getDataset().getNucleus(i).getNumberName();
                            String pNuc = dataAttr2.getDataset().getNucleus(j).getNumberName();
                            if (dNuc.equals(pNuc)) {
                                dimMatches[i] = j;
                                used[j] = true;
                                nMatch++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return dimMatches;
    }

    public enum AXMODE {

        PTS() {
            public int getIndex(VecBase vec, double value) {
                int index = (int) Math.round(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= vec.getSize()) {
                    index = vec.getSize() - 1;
                }
                return index;
            }

            public double getIncrement(VecBase vec, double start, double end) {
                return 1;
            }

            public int getIndex(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];
                int index = (int) Math.round(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= dataset.getSizeReal(iDim)) {
                    index = dataset.getSizeReal(iDim) - 1;
                }
                return index;
            }

            public double getIndexD(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];
                double index = value;
                if (index < 0.0) {
                    index = 0.0;
                }
                if (index >= dataset.getSizeReal(iDim)) {
                    index = dataset.getSizeReal(iDim) - 1.0;
                }
                return index;
            }

            public double indexToValue(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                if (value < 0) {
                    value = 0;
                }
                if (value >= dataset.getSizeReal(iDim)) {
                    value = dataset.getSizeReal(iDim) - 1;
                }
                return value;
            }

            public double getIncrement(DatasetAttributes dataAttr, int iDim, double start, double end) {
                return 1;
            }

            public String getLabel(DatasetAttributes dataAttr, int jDim) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                return dataset.getLabel(iDim) + " (pt)";
            }

            public String getLabel(Vec vec) {
                return "points";
            }

        },
        PPM() {
            public int getIndex(VecBase vec, double value) {
                int index = vec.refToPt(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= vec.getSize()) {
                    index = vec.getSize() - 1;
                }
                return index;
            }

            public double getIncrement(VecBase vec, double start, double end) {
                int iStart = getIndex(vec, start);
                int iEnd = getIndex(vec, end);
                return (end - start) / (iEnd - iStart);
            }

            public int getIndex(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                int index = dataset.ppmToPoint(iDim, value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= dataset.getSizeReal(iDim)) {
                    index = dataset.getSizeReal(iDim) - 1;
                }
                return index;
            }

            public double getIndexD(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                double index = dataset.ppmToDPoint(iDim, value);
                if (index < 0) {
                    index = 0.0;
                }
                if (index >= dataset.getSizeReal(iDim)) {
                    index = dataset.getSizeReal(iDim) - 1.0;
                }
                return index;
            }

            public double indexToValue(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                return dataset.pointToPPM(iDim, value);
            }

            public double getIncrement(DatasetAttributes dataAttr, int jDim, double start, double end) {
                return 1;
            }

            public String getLabel(DatasetAttributes dataAttr, int jDim) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                return dataset.getDlabel(iDim) + " (ppm)";
            }

            public String getLabel(Vec vec) {
                return "ppm";
            }
        },
        HZ() {
            public int getIndex(VecBase vec, double value) {
                int index = (int) vec.lwToPtD(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= vec.getSize()) {
                    index = vec.getSize() - 1;
                }
                return index;
            }

            public double getIncrement(VecBase vec, double start, double end) {
                int iStart = getIndex(vec, start);
                int iEnd = getIndex(vec, end);
                return (end - start) / (iEnd - iStart);
            }

            public int getIndex(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                int index = (int) dataset.hzWidthToPoints(iDim, value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= dataset.getSizeReal(iDim)) {
                    index = dataset.getSizeReal(iDim) - 1;
                }
                return index;
            }

            public double getIndexD(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                double index = dataset.hzWidthToPoints(iDim, value);
                if (index < 0.0) {
                    index = 0.0;
                }
                if (index >= dataset.getSizeReal(iDim)) {
                    index = dataset.getSizeReal(iDim) - 1.0;
                }
                return index;
            }

            public double indexToValue(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                return dataset.ptWidthToHz(iDim, value);
            }

            public double getIncrement(DatasetAttributes dataAttr, int jDim, double start, double end) {
                return 1;
            }

            public String getLabel(DatasetAttributes dataAttr, int jDim) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                return dataset.getLabel(iDim) + " (Hz)";
            }

            public String getLabel(Vec vec) {
                return "Hz";
            }
        },
        TIME() {
            public int getIndex(VecBase vec, double value) {
                int index = vec.timeToPt(value);
                if (index < 0) {
                    index = 0;
                }
                if (index >= vec.getSize()) {
                    index = vec.getSize() - 1;
                }
                return index;
            }

            public double getIncrement(VecBase vec, double start, double end) {
                int iStart = getIndex(vec, start);
                int iEnd = getIndex(vec, end);
                return (end - start) / (iEnd - iStart);
            }

            public int getIndex(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                Vec vec = null;
                if (dataset instanceof Dataset) {
                    vec = ((Dataset) dataset).getVec();
                }
                if (vec != null) {
                    return getIndex(vec, value);
                } else {
                    // fixme is this right or should it be 1/sw
                    int index = (int) (value * dataset.getSw(iDim));
                    if (index < 0) {
                        index = 0;
                    }
                    if (index >= dataset.getSizeReal(iDim)) {
                        index = dataset.getSizeReal(iDim) - 1;
                    }
                    return index;
                }
            }

            public double getIndexD(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
                int iDim = dataAttr.dim[jDim];

                Vec vec = null;
                if (dataset instanceof Dataset) {
                    vec = ((Dataset) dataset).getVec();
                }
                if (vec != null) {
                    return getIndex(vec, value);
                } else {
                    // fixme is this right or should it be 1/sw
                    double index = (value * dataset.getSw(iDim));
                    if (index < 0.0) {
                        index = 0.0;
                    }
                    if (index >= dataset.getSizeReal(iDim)) {
                        index = dataset.getSizeReal(iDim) - 1.0;
                    }
                    return index;
                }
            }

            public double indexToValue(DatasetAttributes dataAttr, int jDim, double value) {
                DatasetBase dataset = dataAttr.getDataset();
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

        public abstract int getIndex(VecBase vec, double value);

        public abstract double indexToValue(DatasetAttributes dataAttr, int iDim, double value);

        public abstract double getIncrement(VecBase vec, double start, double end);

        public abstract int getIndex(DatasetAttributes dataAttr, int iDim, double value);

        public abstract double getIndexD(DatasetAttributes dataAttr, int iDim, double value);

        public abstract double getIncrement(DatasetAttributes dataAttr, int iDim, double start, double end);

        public abstract String getLabel(DatasetAttributes dataAttr, int iDim);

        public String getDatasetLabel(DatasetAttributes dataAttr, int jDim) {
            DatasetBase dataset = dataAttr.getDataset();
            int iDim = dataAttr.dim[jDim];

            return dataset.getLabel(iDim);
        }

        public abstract String getLabel(Vec vec);

    }

}
