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
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.utilities.Format;
import java.io.IOException;

import java.util.*;
import static java.util.Comparator.comparing;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.paint.Color;
import org.nmrfx.processor.datasets.RegionData;

public class Peak implements Comparable, PeakOrMulti {

    static String peakStrings[] = {
        "_Peak.ID",
        "_Peak.Figure_of_merit",
        "_Peak.Details",
        "_Peak.Type",
        "_Peak.Status",
        "_Peak.Color",
        "_Peak.Flag",
        "_Peak.Label_corner",};
    static String peakGeneralCharStrings[] = {
        "_Peak_general_char.Peak_ID",
        "_Peak_general_char.Intensity_val",
        "_Peak_general_char.Intensity_val_err",
        "_Peak_general_char.Measurement_method",};
    static String peakCharStrings[] = {
        "_Peak_char.Peak_ID",
        "_Peak_char.Peak_contribution_ID",
        "_Peak_char.Spectral_dim_ID",
        "_Peak_char.Chem_shift_val",
        "_Peak_char.Chem_shift_val_err",
        "_Peak_char.Bounding_box_val",
        "_Peak_char.Bounding_box_val_err",
        "_Peak_char.Line_width_val",
        "_Peak_char.Line_width_val_err",
        "_Peak_char.Phase_val",
        "_Peak_char.Phase_val_err",
        "_Peak_char.Decay_rate_val",
        "_Peak_char.Decay_rate_val_err",
        "_Peak_char.Derivation_method_ID",
        "_Peak_char.Peak_error",
        "_Peak_char.Detail",
        "_Peak_char.Coupling_detail",};
    static final public int NFLAGS = 16;
    static final public int COMPOUND = 1;
    static final public int MINOR = 2;
    static final public int SOLVENT = 4;
    static final public int ARTIFACT = 8;
    static final public int IMPURITY = 16;
    static final public int CHEMSHIFT_REF = 32;
    static final public int QUANTITY_REF = 64;
    static final public int COMBO_REF = 128;
    static final public int WATER = 256;
    static final private int nTypes = 9;
    static private String[] peakTypes = new String[nTypes];

    static final public Color[] FREEZE_COLORS = {Color.ORANGE, Color.MAGENTA, Color.RED};

    static {
        int j = 1;

        for (int i = 0; i < nTypes; i++) {
            peakTypes[i] = typeToString(j);
            j *= 2;
        }
    }
    private float figureOfMerit = 1.0f;
    private boolean valid = true;
    private int idNum;
    private int index = -1;
    private float volume1;
    private float intensity;
    private float volume2;
    private int type = COMPOUND;
    private int status;
    private Color color;
    private String comment;
    private boolean[] flag;
    public PeakDim[] peakDim;
    private Corner corner = new Corner("ne");
    public PeakList peakList;

    public Peak(PeakList peakList, int nDim) {
        peakDim = new PeakDim[nDim];
        flag = new boolean[NFLAGS];
        setComment("");
        this.peakList = peakList;
        idNum = peakList.idLast + 1;
        peakList.idLast += 1;

        for (int i = 0; i < NFLAGS; i++) {
            setFlag(i, false);
        }

        for (int i = 0; i < nDim; i++) {
            peakDim[i] = new PeakDim(this, i);
        }

        setStatus(0);
    }

    public class Corner {

        private final double x;
        private final double y;
        private final char[] cornerChars;

        Corner(double x, double y) {
            this.x = x;
            this.y = y;
            cornerChars = null;
        }

        Corner(char[] cornerChars) {
            x = 0;
            y = 0;
            this.cornerChars = new char[2];
            if ((cornerChars == null) || (cornerChars.length != 2)) {
                this.cornerChars[0] = 'n';
                this.cornerChars[1] = 'e';
            } else {
                this.cornerChars[0] = cornerChars[0];
                this.cornerChars[1] = cornerChars[1];
            }
        }

        Corner(String cornerStr) {
            x = 0;
            y = 0;
            this.cornerChars = new char[2];
            if ((cornerStr == null) || (cornerStr.length() != 2)) {
                this.cornerChars[0] = 'n';
                this.cornerChars[1] = 'e';
            } else {
                this.cornerChars[0] = cornerStr.charAt(0);
                this.cornerChars[1] = cornerStr.charAt(1);
            }

        }

        @Override
        public String toString() {
            String stringRep;
            if (cornerChars != null) {
                stringRep = cornerChars[0] + "" + cornerChars[1];
            } else {
                stringRep = Format.format2(x) + " " + Format.format2(y);
            }
            return stringRep;
        }

        public String toFracString() {
            String stringRep;
            if (cornerChars != null) {
                double[] xy = getPosition(0.5, -0.5, 0.5, -0.5);
                stringRep = Format.format2(xy[0]) + " " + Format.format2(xy[1]);
            } else {
                stringRep = Format.format2(x) + " " + Format.format2(y);
            }
            return stringRep;
        }

        public double[] getPosition(double px1, double py1, double px2, double py2) {
            double[] position = new double[2];
            if (cornerChars != null) {
                if (cornerChars[0] == 'n') {
                    position[1] = py2;
                } else if (cornerChars[0] == ' ') {
                    position[1] = (py2 + py1) / 2.0;
                } else {
                    position[1] = py1;
                }

                if (cornerChars[1] == 'e') {
                    position[0] = px2;
                } else if (cornerChars[1] == ' ') {
                    position[0] = (px1 + px2) / 2.0;
                } else {
                    position[0] = px1;
                }
            } else {
                position[0] = Math.abs(px2 - px1) * x + ((px1 + px2) / 2);
                position[1] = Math.abs(py2 - py1) * y + ((py1 + py2) / 2);

            }
            return position;
        }

        public double[] getPosition() {
            double[] position = new double[2];
            if (cornerChars != null) {
                if (cornerChars[0] == 'n') {
                    position[1] = -0.5;
                } else if (cornerChars[0] == ' ') {
                    position[1] = 0.0;
                } else {
                    position[1] = 0.5;
                }

                if (cornerChars[1] == 'e') {
                    position[0] = 0.5;
                } else if (cornerChars[1] == ' ') {
                    position[0] = 0.0;
                } else {
                    position[0] = -0.5;
                }
            } else {
                position[0] = x;
                position[1] = y;

            }
            return position;
        }

        public char[] getAnchor(double px1, double py1, double px2, double py2) {
            char[] anchor = new char[2];
            if (cornerChars != null) {
                if (cornerChars[0] == 'n') {
                    anchor[0] = 's';
                } else if (cornerChars[0] == ' ') {
                    anchor[0] = ' ';
                } else {
                    anchor[0] = 'n';
                }

                if (cornerChars[1] == 'e') {
                    anchor[1] = 'w';
                } else if (cornerChars[1] == ' ') {
                    anchor[1] = ' ';
                } else {
                    anchor[1] = 'e';
                }
            } else {
                if (y < -0.25) {
                    anchor[0] = 's';
                } else if (y < 0.25) {
                    anchor[0] = ' ';
                } else {
                    anchor[0] = 'n';
                }

                if (x < -0.25) {
                    anchor[1] = 'w';
                } else if (x < 0.25) {
                    anchor[1] = ' ';
                } else {
                    anchor[1] = 'e';
                }

            }
            return anchor;
        }
    }

    public Peak copy(PeakList peakList) {
        Peak newPeak = new Peak(peakList, peakDim.length);
        newPeak.figureOfMerit = figureOfMerit;
        newPeak.valid = valid;
        newPeak.volume1 = volume1;
        newPeak.intensity = intensity;
        newPeak.volume2 = volume2;
        newPeak.type = type;
        newPeak.status = status;
        newPeak.comment = comment;
        newPeak.flag = flag.clone();
        newPeak.corner = new Corner(corner.cornerChars);
        for (int i = 0; i < peakDim.length; i++) {
            newPeak.peakDim[i] = peakDim[i].copy(newPeak);
        }
        return newPeak;
    }

    public void copyLabels(Peak newPeak) {
        for (int i = 0; i < peakDim.length; i++) {
            peakDim[i].copyLabels(newPeak.peakDim[i]);
        }
    }

    public int compareTo(Object o) {
        int result = 1;
        if (o instanceof Peak) {
            Peak peak2 = (Peak) o;
            result = peakList.getName().compareTo(peak2.peakList.getName());
            if (result == 0) {
                if (idNum > peak2.idNum) {
                    result = 1;
                } else if (idNum < peak2.idNum) {
                    result = -1;
                } else {
                    result = 0;
                }
            }
        }
        return result;
    }

    void peakUpdated(Object object) {
        if (peakList != null) {
            peakList.peakListUpdated(this);
        }
    }

    public static String[] getSTAR3Strings() {
        return peakStrings;
    }

    public static String[] getSTAR3GeneralCharStrings() {
        return peakGeneralCharStrings;
    }

    public static String[] getSTAR3CharStrings() {
        return peakCharStrings;
    }

    public static String[] getPeakTypes() {
        return peakTypes;
    }

    public boolean isValid() {
        return valid;
    }

    public PeakList getPeakList() {
        return peakList;
    }

    public void markDeleted() {
        valid = false;

        for (int i = 0; i < peakDim.length; i++) {
            peakDim[i].getMultiplet().removePeakDim(peakDim[i]);
            peakDim[i].remove();
        }

    }

    public String getName() {
        return peakList.getName() + "." + getIdNum();
    }

    public int getNDim() {
        return peakList.nDim;
    }

    public PeakDim[] getPeakDims() {
        return peakDim;
    }

    public PeakDim getPeakDim(int iDim) throws IllegalArgumentException {
        PeakDim iPeakDim = null;
        if ((iDim >= 0) && (iDim < peakList.nDim)) {
            iPeakDim = peakDim[iDim];
        }
        if (iPeakDim == null) {
            throw new IllegalArgumentException("Invalid peak dimension \"" + iDim + "\"");
        }
        return iPeakDim;
    }

    public PeakDim getPeakDim(String label) {
        PeakDim matchDim = null;

        for (int i = 0; i < peakList.nDim; i++) {
            if (peakList.getSpectralDim(i).getDimName().equals(label)) {
                matchDim = peakDim[i];
            }
        }

        return matchDim;
    }

    public void initPeakDimContribs() {
        for (int i = 0; i < peakDim.length; i++) {
            peakDim[i].initResonance();
        }
    }

    public void getPeakRegion(Dataset theFile, int[] pdim, int[][] p,
            int[] cpt, double[] width) {
        double p1;
        double p2;
        double p1d;
        double p2d;

        for (int i = 0; i < peakList.nDim; i++) {
            double pc = peakDim[i].getChemShiftValue();

            p1 = pc + Math.abs(peakDim[i].getBoundsValue()) / 2;
            p[pdim[i]][0] = theFile.ppmToFoldedPoint(pdim[i], p1);

            p2 = pc - Math.abs(peakDim[i].getBoundsValue()) / 2;
            p[pdim[i]][1] = theFile.ppmToFoldedPoint(pdim[i], p2);
            cpt[pdim[i]] = theFile.ppmToFoldedPoint(pdim[i], pc);

            p1 = peakDim[i].getChemShiftValue() + (Math.abs(peakDim[i].getLineWidthValue()) / 2.0);
            p1d = theFile.ppmToDPoint(pdim[i], p1);
            p2 = peakDim[i].getChemShiftValue() - (Math.abs(peakDim[i].getLineWidthValue()) / 2.0);
            p2d = theFile.ppmToDPoint(pdim[i], p2);
            width[pdim[i]] = Math.abs(p2d - p1d);
            p1 = peakDim[i].getChemShiftValue();
            p1d = theFile.ppmToDPoint(pdim[i], p1);
        }
    }

    void fold(int iDim, String foldDir)
            throws IllegalArgumentException {
        int iUpDown = 0;

        if (foldDir.equals("up")) {
            iUpDown = 1;
        } else if (foldDir.equals("down")) {
            iUpDown = -1;
        } else {
            throw new IllegalArgumentException(
                    "nv_peak fold: Invalid direction " + foldDir);
        }

        peakDim[iDim].setChemShiftValueNoCheck((float) (peakDim[iDim].getChemShiftValue()
                + ((iUpDown * peakList.getSpectralDim(iDim).getSw()) / peakList.getSpectralDim(iDim).getSf())));
    }

    public double measurePeak(Dataset dataset, int[] planes, Function<RegionData, Double> f) throws IOException {
        RegionData regionData = analyzePeakRegion(dataset, planes);
        return f.apply(regionData);
    }

    public void quantifyPeak(Dataset dataset, Function<RegionData, Double> f, String mode) throws IOException, IllegalArgumentException {
        int[] planes = new int[0];
        RegionData regionData = analyzePeakRegion(dataset, planes);
        double value = f.apply(regionData);
        if (mode.contains("volume")) {
            volume1 = (float) value;
        } else {
            intensity = (float) value;
        }

    }

    public static Function<RegionData, Double> getMeasureFunction(String mode) {
        Function<RegionData, Double> f;
        switch (mode) {
            case "center":
                f = RegionData::getCenter;
                break;
            case "jitter":
                f = RegionData::getJitter;
                break;
            case "max":
                f = RegionData::getMax;
                break;
            case "min":
                f = RegionData::getMin;
                break;
            case "extreme":
                f = RegionData::getExtreme;
                break;
            case "volume":
                f = RegionData::getVolume_r;
                break;
            case "evolume":
                f = RegionData::getVolume_e;
                break;
            case "tvolume":
                f = RegionData::getVolume_t;
                break;
            default:
                f = null;
        }
        return f;
    }

    public void tweak(Dataset dataset) throws IOException {
        int[] planes = new int[0];
        RegionData regionData = analyzePeakRegion(dataset, planes);
        double[] maxPoint = regionData.getMaxDPoint();
        for (int i = 0; i < maxPoint.length; i++) {
            boolean frozen = getFlag(8 + i);
            if (!frozen) {
                double position = dataset.pointToPPM(i, maxPoint[i]);
                getPeakDim(i).setChemShiftValue((float) position);
            }
        }
    }

    public RegionData analyzePeakRegion(Dataset theFile, int[] planes)
            throws IOException {
        int dataDim = theFile.getNDim();
        int[][] p = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];
        int[] dim = new int[dataDim];
        int[] pdim = new int[peakList.nDim];

        if (dataDim != (peakList.nDim + planes.length)) {
            throw new IllegalArgumentException("Number of peak list dimensions not equal to number of dataset dimensions");
        }

        for (int j = 0; j < peakList.nDim; j++) {
            boolean ok = false;

            for (int i = 0; i < dataDim; i++) {
                if (peakList.getSpectralDim(j).getDimName().equals(theFile.getLabel(i))) {
                    pdim[j] = i;
                    ok = true;

                    break;
                }
            }

            if (!ok) {
                throw new IllegalArgumentException(
                        "Can't find match for peak dimension \""
                        + peakList.getSpectralDim(j).getDimName() + "\"");
            }
        }

        int k = 0;
        getPeakRegion(theFile, pdim, p, cpt, width);

        for (int i = 0; i < dataDim; i++) {
            dim[i] = i;

            boolean ok = false;

            for (int j = 0; j < peakList.nDim; j++) {
                if (pdim[j] == i) {
                    ok = true;
                }
            }

            if (!ok) {
                cpt[i] = p[i][1] = p[i][0] = planes[k];
                width[i] = 0.0;
                k++;
            }
        }

        RegionData regionData = theFile.analyzeRegion(p, cpt, width, dim);
        return regionData;
    }

    public int getClusterOriginPeakID(int searchDim) {
        int origin = -1;
        int nDim = getNDim();
        int startDim = searchDim;
        int lastDim = searchDim;
        if (searchDim < 0) {
            startDim = 0;
            lastDim = nDim - 1;
        }
        if (PeakList.clusterOrigin != null) {
            for (int iDim = startDim; iDim <= lastDim; iDim++) {
                List<PeakDim> linkedPeakDims = PeakList.getLinkedPeakDims(this, iDim);
                for (int i = 0, n = linkedPeakDims.size(); i < n; i++) {
                    PeakDim linkedDim = (PeakDim) linkedPeakDims.get(i);
                    if (linkedDim.getPeak().peakList == PeakList.clusterOrigin) {
                        origin = linkedDim.getPeak().getIdNum();
                        break;
                    }
                }
                if (origin != -1) {
                    break;
                }
            }
        }
        return origin;
    }

    public int getType() {
        return type;
    }

    public static int getType(String typeString) {
        int type = COMPOUND;

        if ("compound".startsWith(typeString.toLowerCase())) {
            type = COMPOUND;
        } else if ("minor".startsWith(typeString.toLowerCase())) {
            type = MINOR;
        } else if ("solvent".startsWith(typeString.toLowerCase())) {
            type = SOLVENT;
        } else if ("contaminant".startsWith(typeString.toLowerCase())) {
            type = IMPURITY;
        } else if ("impurity".startsWith(typeString.toLowerCase())) {
            type = IMPURITY;
        } else if ("chemshiftref".startsWith(typeString.toLowerCase())) {
            type = CHEMSHIFT_REF;
        } else if ("quantityref".startsWith(typeString.toLowerCase())) {
            type = QUANTITY_REF;
        } else if ("comboref".startsWith(typeString.toLowerCase())) {
            type = COMBO_REF;
        } else if ("water".startsWith(typeString.toLowerCase())) {
            type = WATER;
        } else if ("artifact".startsWith(typeString.toLowerCase())) {
            type = ARTIFACT;
        } else {
            type = -1;
        }

        return type;
    }

    public static String typesToString(int iTypes) {
        int j = 1;
        int n = 0;
        StringBuffer sBuf = new StringBuffer();

        for (int i = 0; i < nTypes; i++) {
            if ((iTypes & j) != 0) {
                if (n > 0) {
                    sBuf.append(" ");
                }

                sBuf.append(typeToString(j));
                n++;
            }

            j *= 2;
        }

        return sBuf.toString();
    }

    public String typeToString() {
        return typeToString(getType());
    }

    public static String typeToString(int type) {
        if (type == Peak.COMPOUND) {
            return "compound";
        } else if (type == Peak.MINOR) {
            return "minor";
        } else if (type == Peak.SOLVENT) {
            return "solvent";
        } else if (type == Peak.IMPURITY) {
            return "impurity";
        } else if (type == Peak.CHEMSHIFT_REF) {
            return "chemshiftRef";
        } else if (type == Peak.QUANTITY_REF) {
            return "quantityRef";
        } else if (type == Peak.COMBO_REF) {
            return "comboRef";
        } else if (type == Peak.WATER) {
            return "water";
        } else {
            return "artifact";
        }
    }

    public void setType(int type, int flagLoc) {
        this.setType(type);

        if ((flagLoc >= 0) && (flagLoc < NFLAGS)) {
            List<Peak> lPeaks = PeakList.getLinks(this);

            for (int i = 0, n = lPeaks.size(); i < n; i++) {
                Peak lPeak = (Peak) lPeaks.get(i);
                if (type != Peak.COMPOUND) {
                    lPeak.setFlag(flagLoc, true);
                } else {
                    lPeak.setFlag(flagLoc, false);
                }
                lPeak.setType(type);
            }
        }
    }

    public String toSTAR3LoopPeakString() {
        StringBuffer result = new StringBuffer();
        String sep = " ";
        char stringQuote = '"';
        result.append(String.valueOf(getIdNum()) + sep);
        result.append(String.valueOf(getFigureOfMerit()) + sep);
        result.append(stringQuote);
        result.append(getComment());
        result.append(stringQuote);
        result.append(sep);
        result.append(typeToString());
        result.append(sep);
        result.append(getStatus());
        result.append(sep);
        result.append(stringQuote);
        result.append(getColorName());
        result.append(stringQuote);
        result.append(sep);
        result.append(getFlag());
        result.append(sep);
        result.append(stringQuote);
        result.append(String.valueOf(getCorner()));
        result.append(stringQuote);
        return result.toString();
    }

    public String toSTAR3LoopIntensityString(int mode) {
        StringBuffer result = new StringBuffer();
        String sep = " ";
//FIXME  need to add intensity object list to Peak
        if (mode == 0) {
            result.append(String.valueOf(getIdNum()) + sep);
            result.append(String.valueOf(getIntensity()) + sep);
            result.append("0.0" + sep);
            result.append("height");
        } else if (mode == 1) {
            result.append(String.valueOf(getIdNum()) + sep);
            result.append(String.valueOf(getVolume1()) + sep);
            result.append("0.0" + sep);
            result.append("volume");
        } else if (mode == 2) {
            result.append(String.valueOf(getIdNum()) + sep);
            result.append(String.valueOf(getVolume2()) + sep);
            result.append("0.0" + sep);
            result.append("volume2");
        }
        return result.toString();
    }

    public String toNEFString(int id) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        // need to fix ambiguous
        result.append(String.valueOf(getIdNum()) + sep);
        result.append(String.valueOf(getIdNum()) + sep);
        result.append(String.valueOf(getVolume1()) + sep);
        result.append("." + sep); // uncertainty fixme
        result.append(String.valueOf(getIntensity()) + sep);
        result.append("." + sep); // uncertainty fixme
        for (PeakDim apeakDim : peakDim) {
            result.append(apeakDim.toNEFString(COMPOUND));
            result.append(sep);
        }
        for (PeakDim apeakDim : peakDim) {
            result.append("." + sep);
            result.append("." + sep);
            result.append("." + sep);
            result.append("." + sep);
        }

        return result.toString();
    }

    public String toMyString() {
        StringBuffer result = new StringBuffer();
        String sep = " ";
        char stringQuote = '"';
        result.append(String.valueOf(getIdNum()) + sep);
        result.append(String.valueOf(getIntensity()) + sep);
        result.append(String.valueOf(getVolume1()) + sep);
        result.append(String.valueOf(getVolume2()) + sep);
        result.append(String.valueOf(getStatus()) + sep);
        result.append(String.valueOf(typeToString()) + sep);

        int i;
        boolean nonZero = false;
        StringBuffer flagResult = new StringBuffer();
        for (i = 0; i < NFLAGS; i++) {
            if (getFlag(i)) {
                flagResult.append(1);
                nonZero = true;
            } else {
                flagResult.append(0);
            }
        }

        if (nonZero) {
            result.append(flagResult);
        } else {
            result.append(0);
        }

        result.append(sep + stringQuote + getComment() + stringQuote + sep);
        result.append(stringQuote);
        result.append(String.valueOf(getCorner()));
        result.append(stringQuote);
        result.append(sep);
        result.append("\n");

        for (i = 0; i < getNDim(); i++) {

            result.append(stringQuote + String.valueOf(peakDim[i].getLabel())
                    + stringQuote + sep);
            result.append(String.valueOf(peakDim[i].getChemShiftValue()) + sep);
            result.append(String.valueOf(peakDim[i].getLineWidthValue()) + sep);
            result.append(String.valueOf(peakDim[i].getBoundsValue()) + sep);
            if (peakDim[i].getError()[0] == ' ') {
                result.append("+");
            } else {
                result.append(String.valueOf(peakDim[i].getError()[0]));
            }

            if (peakDim[i].getError()[1] == ' ') {
                result.append("+");
            } else {
                result.append(String.valueOf(peakDim[i].getError()[1]));
            }

            result.append(sep);
            result.append(String.valueOf(peakDim[i].getMultiplet().getCouplingsAsString())
                    + sep);

            result.append(stringQuote);
            result.append(String.valueOf(peakDim[i].getResonanceIDsAsString()));
            result.append(stringQuote);
            result.append(sep);
            result.append(sep + stringQuote + peakDim[i].getUser()
                    + stringQuote + sep);
            result.append("\n");
        }

        return (result.toString());
    }

    public String toXPKString() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        //id  V I 
        result.append(String.valueOf(getIdNum())).append(sep);

//P W B
        for (int i = 0; i < getNDim(); i++) {
            String label = peakDim[i].getLabel();
            if (label.contains(" ") || label.equals("")) {
                label = "{" + label + "}";
            }
            result.append(label).append(sep);
            result.append(String.valueOf(peakDim[i].getChemShiftValue())).append(sep);
            result.append(String.valueOf(peakDim[i].getLineWidthValue())).append(sep);
            result.append(String.valueOf(peakDim[i].getBoundsValue())).append(sep);
        }
        result.append(String.valueOf(getVolume1())).append(sep);
        result.append(String.valueOf(getIntensity()));

        return (result.toString().trim());
    }

    public String toXPK2String(int index) {
        StringBuilder result = new StringBuilder();
        String sep = "\t";
        result.append(String.valueOf(getIdNum())).append(sep);
        String formatString = "%.5f";

        for (int i = 0; i < getNDim(); i++) {
            double sf = peakDim[i].getSpectralDimObj().getSf();
            String label = peakDim[i].getLabel();
            result.append(label).append(sep);
            result.append(String.format(formatString, peakDim[i].getChemShiftValue())).append(sep);
            result.append(String.format(formatString, peakDim[i].getLineWidth() * sf)).append(sep);
            result.append(String.format(formatString, peakDim[i].getBoundsValue() * sf)).append(sep);
            result.append(peakDim[i].getError()).append(sep);
            result.append(peakDim[i].getMultiplet().getCouplingsAsSimpleString()).append(sep);
            result.append(peakDim[i].getUser()).append(sep);
            result.append(peakDim[i].getResonanceIDsAsString()).append(sep);
        }
        result.append(String.valueOf(getVolume1())).append(sep);
        result.append(String.valueOf(getIntensity())).append(sep);
        result.append(String.valueOf(getStatus())).append(sep);
        result.append(String.valueOf(getComment())).append(sep);
        result.append(String.valueOf(getFlag()));

        return (result.toString().trim());
    }

    public String toXMLString() {
        StringBuffer result = new StringBuffer();

        /*
         _Peak_list_number
         _Intensity_height
         _Intensity_volume
         _Intensity_volume2
         _Peak_status
         _flag
         _comment
         _Dim_1_label
         _Dim_1_chem_shift
         _Dim_1_line_width
         _Dim_1_bounds
         _Dim_1_error
         _Dim_1_j
         _Dim_1_link0
         _Dim_1_link1
         _Dim_1_thread
         _Dim_1_user
         */
        result.append("<peak _Peak_list_number=\"" + getIdNum() + "\">");
        result.append("<_Intensity_height>" + getIntensity()
                + "</_Intensity_height>");
        result.append("<_Intensity_volume>" + getVolume1() + "</_Intensity_volume>");
        result.append("<_Intensity_volume2>" + getVolume2()
                + "</_Intensity_volume2>");
        result.append("<_Peak_status>" + getStatus() + "</_Peak_status>");
        result.append("<_Peak_type>" + typeToString() + "</_Peak_type>");

        int i;
        boolean nonZero = false;
        result.append("<_flag>");

        for (i = 0; i < NFLAGS; i++) {
            if (getFlag(i)) {
                result.append(1);
                nonZero = true;
            } else if (nonZero) {
                result.append(0);
            }
        }

        if (!nonZero) {
            result.append(0);
        }

        result.append("</_flag>");

        result.append("<_comment>" + getComment() + "</_comment>");

        for (i = 0; i < getNDim(); i++) {
            result.append("<_label dim=\"" + i + "\">" + peakDim[i].getLabel()
                    + "</_label>" + "\n");
            result.append("<_chem_shift dim=\"" + i + "\">"
                    + peakDim[i].getChemShiftValue() + "</_chem_shift>" + "\n");
            result.append("<_line_width dim=\"" + i + "\">"
                    + peakDim[i].getLineWidthValue() + "</_line_width>" + "\n");
            result.append("<_bounds dim=\"" + i + "\">" + peakDim[i].getBoundsValue()
                    + "</_bounds>" + "\n");
            result.append("<_error dim=\"" + i + "\">" + String.valueOf(peakDim[i].getError())
                    + "</_error>" + "\n");
            result.append("<_j dim=\"" + i + "\">"
                    + peakDim[i].getMultiplet().getCouplingsAsString() + "</j>" + "\n");
            result.append("<_j dim=\"" + i + "\">"
                    + peakDim[i].getResonanceIDsAsString() + "</j>" + "\n");

            result.append("<_user dim=\"" + i + "\">" + peakDim[i].getUser()
                    + "</_user>" + "\n");
            result.append("\n");
        }

        return (result.toString());
    }

    public static Peak LinkStringToPeak(String string) {
        Peak peak = null;
        int start = 0;

        if (string.length() > 3) {
            // System.out.println(string);
            if (string.charAt(1) == ':') {
                start = 2;
            } else {
                start = 0;
            }

            peak = PeakList.getAPeak(string.substring(start));
        }

        return (peak);
    }

    public static int LinkStringToDim(String string) {
        int start = 0;

        if (string.charAt(2) == ':') {
            start = 2;
        } else {
            start = 0;
        }

        if (string.length() < 3) {
            return (0);
        }

        return (PeakList.getPeakDimNum(string.substring(start)));
    }

    public double distance(Peak bPeak, double[] scale)
            throws IllegalArgumentException {
        double sum = 0.0;

        if (peakDim.length != bPeak.peakDim.length) {
            throw new IllegalArgumentException(
                    "peaks don't have same number of dimensions");
        }

        for (int i = 0; i < peakDim.length; i++) {
            double dif = peakDim[i].getChemShiftValue() - bPeak.peakDim[i].getChemShiftValue();
            sum += ((dif / scale[i]) * (dif / scale[i]));
        }

        return Math.sqrt(sum);
    }

    public boolean isLinked(int dim) {
        if ((dim < 0) || (dim >= peakDim.length)) {
            return false;
        } else {
            return peakDim[dim].isLinked();
        }
    }

    class CouplingUpdate {

        double origValue;
        int direction;
        int iCoupling;
    }

    public CouplingUpdate startUpdateCouplings(int iDim) {
        CouplingUpdate cUpdate = null;
        PeakDim pDim = peakDim[iDim];

        List<PeakDim> links = pDim.getLinkedPeakDims();
        links.sort(comparing(PeakDim::getChemShift));

        int iPos = 0;
        for (Iterator it = links.iterator(); it.hasNext();) {
            PeakDim itDim = (PeakDim) it.next();
            if (itDim == pDim) {
                break;
            }
            iPos++;
        }
        Coupling coupling = pDim.getMultiplet().getCoupling();
        double[] values = null;
        if (coupling != null) {
            if (coupling instanceof CouplingPattern) {
                values = ((CouplingPattern) coupling).getValues();
            }
        }
        cUpdate = new CouplingUpdate();
        int nLinks = links.size();
        int iCoupling = 0;
        if (iPos < (nLinks / 2)) {
            iCoupling = iPos;
            cUpdate.direction = 1;
        } else {
            iCoupling = nLinks - iPos - 1;
            cUpdate.direction = -1;
        }
        if (values != null) {
            if (iCoupling < values.length) {
                cUpdate.iCoupling = values.length - iCoupling - 1;
                cUpdate.origValue = values[cUpdate.iCoupling];
            } else {
                cUpdate.iCoupling = -1;
                cUpdate.origValue = 0.0;
            }

        }

        return cUpdate;
    }

    public void updateCouplings(int iDim, int iCoupling, double value) {
        PeakDim pDim = peakDim[iDim];

        pDim.getMultiplet().adjustCouplings(iCoupling, value);

    }

    /*
     public void updateCouplings() {
     if (!getFlag(5)) {
     for (int i = 0; i < peakDim.length; i++) {
     PeakDim pDim = peakDim[i];
     Peak origPeak = pDim.getOrigin();

     if (origPeak != null) {
     ArrayList links = pDim.getLinkedPeakDims();
     PeakDim.sortPeakDims(links, true);
     pDim.adjustCouplings(origPeak);
     }

     peakDim[i].updateCouplings();

     }
     }
     }
     */
    public int getIdNum() {
        return idNum;
    }

    public void setIdNum(int idNum) {
        this.idNum = idNum;
        peakUpdated(this);
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public float getVolume1() {
        return volume1;
    }

    public void setVolume1(float volume1) {
        this.volume1 = volume1;
        peakUpdated(this);
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
        peakUpdated(this);
    }

    public float getVolume2() {
        return volume2;
    }

    public void setVolume2(float volume2) {
        this.volume2 = volume2;
        peakUpdated(this);
    }

    public void setType(int type) {
        this.type = type;
        peakUpdated(this);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
        peakUpdated(this);
    }

    public Color getColor() {
        return color;
    }

    public String getColorName() {
        return color.toString();
    }

    public void setColor(Color color) {
        this.color = color;
        peakUpdated(this);
    }

    public void setColor(String colorName) {
        if (colorName != null && (colorName.length() != 0)) {
            colorName = colorName.trim();
        }
        if ((colorName == null) || (colorName.length() == 0)) {
            this.color = null;
        } else {
            Color color = Color.web(colorName);
            this.color = color;
        }
        peakUpdated(this);
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
        peakUpdated(this);
    }
    
    public void updateFrozenColor() {
        int index = 0;
        if (peakDim[0].isFrozen()) {
            index++;
        }
        if ((peakDim.length > 1) && peakDim[1].isFrozen()) {
            index += 2;
        }
        if (index == 0) {
            color = null;
        } else {
            color = FREEZE_COLORS[index-1];
        }
    }
    public boolean getFlag(int index) {
        return flag[index];
    }

    public void setFlag(int index, boolean value) {
        this.flag[index] = value;
        peakUpdated(this);
    }

    public void setFlag(String flagString) {
        for (int i = 0; i < flag.length; i++) {
            flag[i] = false;
        }
        for (int i = 0, n = flagString.length(); i < n; i++) {
            if (flagString.charAt(i) != '0') {
                this.flag[i] = true;
            }
        }
        peakUpdated(this);
    }

    public String getFlag() {
        StringBuffer flagResult = new StringBuffer();
        boolean nonZero = false;
        for (int i = 0; i < NFLAGS; i++) {
            if (getFlag(i)) {
                flagResult.append(1);
                nonZero = true;
            } else {
                flagResult.append(0);
            }
        }
        String result = "0";
        if (nonZero) {
            result = flagResult.toString();
        }
        return result;
    }

    public Corner getCorner() {
        return corner;
    }

    public void setCorner(char[] corner) {
        this.corner = new Corner(corner);
        peakUpdated(this);
    }

    public void setCorner(double x, double y) {
        this.corner = new Corner(x, y);
    }

    public void setCorner(String cornerStr) {
        cornerStr = cornerStr.trim();
        if (cornerStr.length() == 2) {
            setCorner(cornerStr.toCharArray());
        } else if (cornerStr.length() == 1) {
            char[] corner = {' ', ' '};
            if (cornerStr.equals("w") || cornerStr.equals("e")) {
                corner[1] = cornerStr.charAt(0);
                setCorner(corner);
            } else if (cornerStr.equals("n") || cornerStr.equals("s")) {
                corner[0] = cornerStr.charAt(0);
                setCorner(corner);
            } else {
                // fixme throw exception ?
                corner[0] = 'n';
                corner[0] = 'e';
                setCorner(corner);
            }
        } else {
            int index = cornerStr.indexOf(' ');
            if (index == -1) {
                index = cornerStr.indexOf('\t');
            }
            if (index == -1) {
                setCorner("ne");
            } else {
                try {
                    double x = Double.parseDouble(cornerStr.substring(0, index));
                    double y = Double.parseDouble(cornerStr.substring(index + 1));
                    this.corner = new Corner(x, y);
                } catch (Exception e) {
                    setCorner("ne");
                }

            }
        }
    }

    public float getFigureOfMerit() {
        return figureOfMerit;
    }

    public void setFigureOfMerit(float newValue) {
        figureOfMerit = newValue;
    }

    public int getDerivationSet() {
        return 0;
    }

    public boolean inRegion(double[][] limits, double[][] foldLimits, int[] dim) {
        int nSearchDim = limits.length;
        if (false) {
            return true;
        }
        boolean ok = true;
        for (int j = 0; j < nSearchDim; j++) {
            if ((dim.length <= j) || (dim[j] == -1) || (dim[j] >= peakDim.length)) {
                continue;
            }
            double ctr = peakDim[dim[j]].getChemShiftValue();
            if ((foldLimits != null) && (foldLimits[j] != null)) {
                double fDelta = Math.abs(foldLimits[j][0] - foldLimits[j][1]);
                ctr = peakList.foldPPM(ctr, fDelta, foldLimits[j][0], foldLimits[j][1]);
            }

            if ((ctr < limits[j][0]) || (ctr > limits[j][1])) {
                ok = false;
//                System.out.println(j + " " + limits[j][0] + " " + limits[j][1] + " " + ctr);
                break;
            }

        }
        return ok;

    }

    public boolean overlaps(final Peak peak, final int dim) {
        return overlaps(peak, dim, 1.0);
    }

    public boolean overlaps(final Peak peak, final int dim, final double scale) {
        boolean result = false;
        PeakDim pdimA = getPeakDim(dim);
        PeakDim pdimB = peak.getPeakDim(dim);
        double ctrA = pdimA.getChemShiftValue();
        double bouA = pdimA.getBoundsValue();
        double ctrB = pdimB.getChemShiftValue();
        double bouB = pdimB.getBoundsValue();
        if (ctrA > ctrB) {
            if ((ctrA - scale * bouA / 2.0) < (ctrB + scale * bouB / 2.0)) {
                result = true;
            }
        } else if ((ctrA + scale * bouA / 2.0) > (ctrB - scale * bouB / 2.0)) {
            result = true;
        }
        return result;
    }

    public Set<Peak> getOverlappingPeaks(Set<Peak> overlaps) {
        Set<Peak> result = new HashSet<Peak>();
        result.addAll(overlaps);
        for (Peak peak : overlaps) {
            Set<Peak> newSet = peak.getOverlappingPeaks();
            result.addAll(newSet);
        }
        return result;
    }

    public Set<Peak> getAllOverlappingPeaks() {
        Set<Peak> overlaps = new HashSet<Peak>();
        overlaps.add(this);
        int size = 0;
        while (overlaps.size() > size) {
            size = overlaps.size();
            overlaps = getOverlappingPeaks(overlaps);
        }
        return overlaps;
    }

    public Set<Peak> getOverlappingPeaks() {
        Set<Peak> overlaps = new HashSet<Peak>();
        int nDim = peakList.nDim;
        for (int i = 0; i < peakList.size(); i++) {
            Peak peak = peakList.getPeak(i);
            if ((peak.getStatus() < 0) || (this == peak)) {
                continue;
            }
            boolean ok = true;
            for (int iDim = 0; iDim < nDim; iDim++) {
                if (!overlaps(peak, iDim, 1.0)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                overlaps.add(peak);
            }
        }
        return overlaps;
    }

    public void fit() {
        Dataset dataset = Dataset.getDataset(this.peakList.fileName);
        try {
            PeakList.peakFit(dataset, this);
        } catch (IllegalArgumentException | IOException | PeakFitException ex) {
            Logger.getLogger(Peak.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
