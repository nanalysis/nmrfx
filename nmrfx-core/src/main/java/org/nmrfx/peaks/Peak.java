package org.nmrfx.peaks;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.star.STAR3Base;
import org.nmrfx.utilities.ColorUtil;
import org.nmrfx.utilities.Format;
import org.nmrfx.utilities.NMRFxColor;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.util.Comparator.comparing;

@PythonAPI("pscript")
@PluginAPI("ring")
public class Peak implements Comparable, PeakOrMulti {

    private static final String[] peakStrings = {
            "_Peak.ID",
            "_Peak.Figure_of_merit",
            "_Peak.Details",
            "_Peak.Type",
            "_Peak.Status",
            "_Peak.Color",
            "_Peak.Flag",
            "_Peak.Label_corner",};
    private static final String[] peakGeneralCharStrings = {
            "_Peak_general_char.Peak_ID",
            "_Peak_general_char.Intensity_val",
            "_Peak_general_char.Intensity_val_err",
            "_Peak_general_char.Measurement_method",};
    private static final String[] peakCharStrings = {
            "_Peak_char.Peak_ID",
            "_Peak_char.Peak_contribution_ID",
            "_Peak_char.Spectral_dim_ID",
            "_Peak_char.Chem_shift_val",
            "_Peak_char.Chem_shift_val_err",
            "_Peak_char.Bounding_box_val",
            "_Peak_char.Bounding_box_val_err",
            "_Peak_char.Line_width_val",
            "_Peak_char.Line_width_val_err",
            "_Peak_char.Shape_factor_val",
            "_Peak_char.Shape_factor_val_err",
            "_Peak_char.Phase_val",
            "_Peak_char.Phase_val_err",
            "_Peak_char.Decay_rate_val",
            "_Peak_char.Decay_rate_val_err",
            "_Peak_char.Derivation_method_ID",
            "_Peak_char.Peak_error",
            "_Peak_char.Detail",
            "_Peak_char.Coupling_detail",
            "_Peak_char.Frozen"};

    private static final String[] spectralTransitionStrings = {
            "_Spectral_transition.ID",
            "_Spectral_transition.Peak_ID",
            "_Spectral_transition.Figure_of_merit",
            "_Spectral_transition.Details",};

    private static final String[] spectralTransitionGeneralCharStrings = {
            "_Spectral_transition_general_char.Spectral_transition_ID",
            "_Spectral_transition_general_char.Peak_ID",
            "_Spectral_transition_general_char.Intensity_val",
            "_Spectral_transition_general_char.Intensity_val_err",
            "_Spectral_transition_general_char.Measurement_method",};

    private static final String[] spectralTransitionCharStrings = {
            "_Spectral_transition_char.Spectral_transition_ID",
            "_Spectral_transition_char.Peak_ID",
            "_Spectral_transition_char.Spectral_dim_ID",
            "_Spectral_transition_char.Chem_shift_val",
            "_Spectral_transition_char.Chem_shift_val_err",
            "_Spectral_transition_char.Bounding_box_val",
            "_Spectral_transition_char.Bounding_box_val_err",
            "_Spectral_transition_char.Line_width_val",
            "_Spectral_transition_char.Line_width_val_err",
            "_Spectral_transition_char.Phase_val",
            "_Spectral_transition_char.Phase_val_err",
            "_Spectral_transition_char.Decay_rate_val",
            "_Spectral_transition_char.Decay_rate_val_err",
            "_Spectral_transition_char.Derivation_method_ID",};

    private static final String[] peakComplexCouplingStrings = {
            "_Peak_complex_multiplet.ID",
            "_Peak_complex_multiplet.Peak_ID",
            "_Peak_complex_multiplet.Spectral_dim_ID",
            "_Peak_complex_multiplet.Multiplet_component_ID",
            "_Peak_complex_multiplet.Offset_val",
            "_Peak_complex_multiplet.Offset_val_err",
            "_Peak_complex_multiplet.Intensity_val",
            "_Peak_complex_multiplet.Intensity_val_err",
            "_Peak_complex_multiplet.Volume_val",
            "_Peak_complex_multiplet.Volume_val_err",
            "_Peak_complex_multiplet.Line_width_val",
            "_Peak_complex_multiplet.Line_width_val_err"};
    private static final String[] peakCouplingPatternStrings = {
            "_Peak_coupling.ID",
            "_Peak_coupling.Peak_ID",
            "_Peak_coupling.Spectral_dim_ID",
            "_Peak_coupling.Multiplet_component_ID",
            "_Peak_coupling.Type",
            "_Peak_coupling.Coupling_val",
            "_Peak_coupling.Coupling_val_err",
            "_Peak_coupling.Strong_coupling_effect_val",
            "_Peak_coupling.Strong_coupling_effect_err",
            "_Peak_coupling.Intensity_val",
            "_Peak_coupling.Intensity_val_err",
            "_Peak_coupling.Partner_Peak_coupling_ID"
    };
    public static final int NFLAGS = 16;
    public static final int COMPOUND = 1;
    public static final int MINOR = 2;
    public static final int SOLVENT = 4;
    public static final int ARTIFACT = 8;
    public static final int IMPURITY = 16;
    public static final int CHEMSHIFT_REF = 32;
    public static final int QUANTITY_REF = 64;
    public static final int COMBO_REF = 128;
    public static final int WATER = 256;
    public static final int[][] FREEZE_COLORS = {{255, 165, 0}, {255, 0, 255}, {255, 0, 0}};
    private static final int N_TYPES = 9;
    private static final String[] PEAK_TYPES = new String[N_TYPES];

    static {
        int j = 1;

        for (int i = 0; i < N_TYPES; i++) {
            PEAK_TYPES[i] = typeToString(j);
            j *= 2;
        }
    }


    public PeakDim[] peakDims;
    protected float figureOfMerit = 1.0f;
    protected boolean valid = true;
    private int idNum;
    protected float volume1;
    protected float volume1Err;
    protected float intensity;
    protected float intensityErr;
    protected float volume2;
    protected float volume2Err;
    protected int type = COMPOUND;
    protected int status;
    protected String comment;
    protected boolean[] flag;
    protected Optional<double[][]> measures = Optional.empty();
    protected Corner corner = new Corner("ne");
    private int index = -1;
    private int[] colorArray = null;
    public PeakList peakList;

    public Peak(PeakList peakList, int nDim) {
        this(nDim);
        this.peakList = peakList;
        idNum = peakList.idLast + 1;
        peakList.idLast += 1;
    }

    public Peak(int nDim) {
        peakDims = new PeakDim[nDim];
        flag = new boolean[NFLAGS];
        setComment("");
        for (int i = 0; i < NFLAGS; i++) {
            setFlag(i, false);
        }
        for (int i = 0; i < nDim; i++) {
            peakDims[i] = new PeakDim(this, i);
        }
        setStatus(0);
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

    public static RegionData analyzePeakRegion(Peak peak, DatasetBase theFile, int[] planes, int[] pdim)
            throws IOException {
        int dataDim = theFile.getNDim();
        int[][] p = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];
        int[] dim = new int[dataDim];

        if (dataDim != (peak.peakList.nDim + planes.length)) {
            throw new IllegalArgumentException("Number of peak list dimensions not equal to number of dataset dimensions");
        }

        int k = 0;
        peak.getPeakRegion(theFile, pdim, p, cpt, width, null, 1.0);

        for (int i = 0; i < dataDim; i++) {
            dim[i] = i;

            boolean ok = false;

            for (int j = 0; j < peak.peakList.nDim; j++) {
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

    public static Peak LinkStringToPeak(String string) {
        Peak peak = null;

        if (string.length() > 3) {
            int start;
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
        int start;

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

    @Override
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

    public static String[] getSTAR3Strings() {
        return Peak.peakStrings;
    }

    public static String[] getSTAR3GeneralCharStrings() {
        return Peak.peakGeneralCharStrings;
    }

    public static String[] getSTAR3CharStrings() {
        return Peak.peakCharStrings;
    }

    public static String[] getSTAR3SpectralTransitionStrings() {
        return Peak.spectralTransitionStrings;
    }

    public static String[] getSTAR3SpectralTransitionGeneralCharStrings() {
        return Peak.spectralTransitionGeneralCharStrings;
    }

    public static String[] getSTAR3SpectralTransitionCharStrings() {
        return Peak.spectralTransitionCharStrings;
    }

    public static String[] getSTAR3ComplexCouplingStrings() {
        return Peak.peakComplexCouplingStrings;
    }

    public static String[] getSTAR3CouplingPatternStrings() {
        return Peak.peakCouplingPatternStrings;
    }

    public static String[] getPeakTypes() {
        return PEAK_TYPES;
    }

    @Override
    public PeakList getPeakList() {
        return peakList;
    }

    public int getNDim() {
        return peakList.nDim;
    }

    public static int getType(String typeString) {
        int type;

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
        StringBuilder sBuf = new StringBuilder();

        for (int i = 0; i < N_TYPES; i++) {
            if ((iTypes & j) != 0) {
                if (n > 0) {
                    sBuf.append(" ");
                }

                sBuf.append(Peak.typeToString(j));
                n++;
            }

            j *= 2;
        }

        return sBuf.toString();
    }

    public static String typeToString(int type) {
        switch (type) {
            case Peak.COMPOUND:
                return "compound";
            case Peak.MINOR:
                return "minor";
            case Peak.SOLVENT:
                return "solvent";
            case Peak.IMPURITY:
                return "impurity";
            case Peak.CHEMSHIFT_REF:
                return "chemshiftRef";
            case Peak.QUANTITY_REF:
                return "quantityRef";
            case Peak.COMBO_REF:
                return "comboRef";
            case Peak.WATER:
                return "water";
            default:
                return "artifact";
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    public Peak copy() {
        Peak newPeak = new Peak(peakDims.length);
        newPeak.figureOfMerit = figureOfMerit;
        newPeak.valid = valid;
        newPeak.volume1 = volume1;
        newPeak.intensity = intensity;
        newPeak.volume2 = volume2;
        newPeak.volume1Err = volume1Err;
        newPeak.intensityErr = intensityErr;
        newPeak.volume2Err = volume2Err;
        newPeak.type = type;
        newPeak.status = status;
        newPeak.comment = comment;
        newPeak.flag = flag.clone();
        newPeak.idNum = idNum;
        newPeak.corner = new Corner(corner.getCornerChars());
        for (int i = 0; i < peakDims.length; i++) {
            peakDims[i].copyTo(newPeak.peakDims[i]);
        }
        return newPeak;
    }

    public Peak copyTo(Peak targetPeak) {
        targetPeak.figureOfMerit = figureOfMerit;
        targetPeak.valid = valid;
        targetPeak.volume1 = volume1;
        targetPeak.intensity = intensity;
        targetPeak.volume2 = volume2;
        targetPeak.volume1Err = volume1Err;
        targetPeak.intensityErr = intensityErr;
        targetPeak.volume2Err = volume2Err;
        targetPeak.type = type;
        targetPeak.status = status;
        targetPeak.comment = comment;
        targetPeak.flag = flag.clone();
        targetPeak.corner = new Corner(corner.getCornerChars());
        for (int i = 0; i < peakDims.length; i++) {
            peakDims[i].copyTo(targetPeak.peakDims[i]);
        }
        targetPeak.peakUpdated(targetPeak);
        return targetPeak;
    }

    public Peak copy(PeakList peakList) {
        Peak newPeak = new Peak(peakList, peakDims.length);
        newPeak.figureOfMerit = figureOfMerit;
        newPeak.valid = valid;
        newPeak.volume1 = volume1;
        newPeak.intensity = intensity;
        newPeak.volume2 = volume2;
        newPeak.volume1Err = volume1Err;
        newPeak.intensityErr = intensityErr;
        newPeak.volume2Err = volume2Err;
        newPeak.type = type;
        newPeak.status = status;
        newPeak.comment = comment;
        newPeak.flag = flag.clone();
        newPeak.corner = new Corner(corner.getCornerChars());
        for (int i = 0; i < peakDims.length; i++) {
            newPeak.peakDims[i] = peakDims[i].copy(newPeak);
        }
        return newPeak;
    }

    public void copyLabels(Peak newPeak) {
        for (int i = 0; i < peakDims.length; i++) {
            peakDims[i].copyLabels(newPeak.peakDims[i]);
        }
    }

    public void peakUpdated(Object object) {
        if (peakList != null) {
            peakList.peakListUpdated(this);
        }
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public void markDeleted() {
        valid = false;

        for (PeakDim peakDim : peakDims) {
            peakDim.remove();
        }

    }

    public int getType() {
        return type;
    }

    public String typeToString() {
        return Peak.typeToString(getType());
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
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(getIdNum()).append(sep);
        result.append(getFigureOfMerit()).append(sep);
        result.append(STAR3Base.quote(getComment()));
        result.append(sep);
        result.append(typeToString());
        result.append(sep);
        result.append(getStatus());
        result.append(sep);
        String colorName = getColorName();
        if (colorName.equals("")) {
            result.append(".");
        } else {
            result.append(STAR3Base.quote(colorName));
        }
        result.append(sep);
        result.append(getFlag());
        result.append(sep);
        result.append(STAR3Base.quote(String.valueOf(getCorner())));
        return result.toString();
    }

    public String toSTAR3LoopSpectralTransitionString(int id) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(id).append(sep);
        result.append(String.valueOf(getIdNum())).append(sep);
        result.append(".").append(sep);
        result.append(".");
        return result.toString();
    }

    public String toSTAR3LoopIntensityString(int mode) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
//FIXME  need to add intensity object list to Peak
        switch (mode) {
            case 0:
                result.append(String.valueOf(getIdNum())).append(sep);
                result.append(String.valueOf(getIntensity())).append(sep);
                result.append(getIntensityErr()).append(sep);
                result.append("height");
                break;
            case 1:
                result.append(String.valueOf(getIdNum())).append(sep);
                result.append(String.valueOf(getVolume1())).append(sep);
                result.append(getVolume1Err()).append(sep);
                result.append("volume");
                break;
            case 2:
                result.append(String.valueOf(getIdNum())).append(sep);
                result.append(String.valueOf(getVolume2())).append(sep);
                result.append(getVolume2Err()).append(sep);
                result.append("volume2");
                break;
            default:
                break;
        }
        return result.toString();
    }

    public String toNEFString(int id) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        // need to fix ambiguous
        result.append(String.valueOf(getIdNum())).append(sep);
        result.append(String.valueOf(getIdNum())).append(sep);
        result.append(String.valueOf(getVolume1())).append(sep);
        if (getVolume1Err() == 0.0) {
            result.append(".").append(sep); // uncertainty fixme
        } else {
            result.append(getVolume1Err()).append(sep); // uncertainty fixme
        }
        result.append(String.valueOf(getIntensity())).append(sep);
        if (getIntensityErr() == 0.0) {
            result.append(".").append(sep); // uncertainty fixme
        } else {
            result.append(getIntensityErr()).append(sep); // uncertainty fixme
        }
        for (PeakDim apeakDim : peakDims) {
            result.append(apeakDim.toNEFString(COMPOUND));
            result.append(sep);
        }
        for (PeakDim apeakDim : peakDims) {
            result.append(".").append(sep);
            result.append(".").append(sep);
            result.append(".").append(sep);
            result.append(".").append(sep);
        }

        return result.toString();
    }

    public String toMyString() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(String.valueOf(getIdNum())).append(sep);
        result.append(String.valueOf(getIntensity())).append(sep);
        result.append(String.valueOf(getVolume1())).append(sep);
        result.append(String.valueOf(getVolume2())).append(sep);
        result.append(String.valueOf(getStatus())).append(sep);
        result.append(String.valueOf(typeToString())).append(sep);

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

        result.append(sep).append(STAR3Base.quote(getComment())).append(sep);
        result.append(STAR3Base.quote(String.valueOf(getCorner())));
        result.append(sep);
        result.append("\n");

        for (i = 0; i < getNDim(); i++) {

            result.append(STAR3Base.quote(String.valueOf(peakDims[i].getLabel()))).append(sep);
            result.append(String.valueOf(peakDims[i].getChemShiftValue())).append(sep);
            result.append(String.valueOf(peakDims[i].getLineWidthValue())).append(sep);
            result.append(String.valueOf(peakDims[i].getBoundsValue())).append(sep);
            if (peakDims[i].getError()[0] == ' ') {
                result.append("+");
            } else {
                result.append(String.valueOf(peakDims[i].getError()[0]));
            }

            if (peakDims[i].getError()[1] == ' ') {
                result.append("+");
            } else {
                result.append(String.valueOf(peakDims[i].getError()[1]));
            }

            result.append(sep);
            if (peakDims[i].hasMultiplet()) {
                result.append(String.valueOf(peakDims[i].getMultiplet().getCouplingsAsString())).append(sep);
            } else {
                result.append(sep);
            }

            result.append(STAR3Base.quote(String.valueOf(peakDims[i].getResonanceIDsAsString())));
            result.append(sep);
            result.append(sep).append(STAR3Base.quote(peakDims[i].getUser())).append(sep);
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
            String label = peakDims[i].getLabel();
            if (label.contains(" ") || label.equals("")) {
                label = "{" + label + "}";
            }
            result.append(label).append(sep);
            result.append(String.valueOf(peakDims[i].getChemShiftValue())).append(sep);
            result.append(String.valueOf(peakDims[i].getLineWidthValue())).append(sep);
            result.append(String.valueOf(peakDims[i].getBoundsValue())).append(sep);
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
            double sf = peakDims[i].getSpectralDimObj().getSf();
            String label = peakDims[i].getLabel();
            result.append(label).append(sep);
            result.append(String.format(formatString, peakDims[i].getChemShiftValue())).append(sep);
            result.append(String.format(formatString, peakDims[i].getLineWidthValue() * sf)).append(sep);
            result.append(String.format(formatString, peakDims[i].getBoundsValue() * sf)).append(sep);
            result.append(peakDims[i].getError()).append(sep);
            if (peakDims[i].hasMultiplet()) {
                result.append(peakDims[i].getMultiplet().getMultiplicity()).append(sep);
                result.append(peakDims[i].getMultiplet().getIDNum()).append(sep);
            } else {
                result.append(sep).append(sep);
            }
            result.append(peakDims[i].getUser()).append(sep);
            result.append(peakDims[i].getResonanceIDsAsString()).append(sep);
            int frozen = peakDims[i].isFrozen() ? 1 : 0;
            result.append(frozen).append(sep);
        }
        result.append(String.valueOf(getVolume1())).append(sep);
        result.append(String.valueOf(getVolume1Err())).append(sep);
        result.append(String.valueOf(getIntensity())).append(sep);
        result.append(String.valueOf(getIntensityErr())).append(sep);
        result.append(String.valueOf(getType())).append(sep);
        result.append(String.valueOf(getComment())).append(sep);
        String colorString = colorArray == null ? "" : ColorUtil.toRGBCode(colorArray);
        result.append(colorString).append(sep);
        result.append(getFlag2()).append(sep);
        result.append(String.valueOf(getStatus()));

        return (result.toString().trim());
    }

    public String toMeasureString(int index) {
        StringBuilder result = new StringBuilder();
        String sep = "\t";
        result.append(String.valueOf(getIdNum())).append(sep);
        String formatString = "%.5f";

        for (int i = 0; i < getNDim(); i++) {
            String label = peakDims[i].getLabel();
            result.append(label).append(sep);
        }
        if (measures.isPresent()) {
            double[][] values = measures.get();
            for (int i = 0; i < values[0].length; i++) {
                result.append(String.format(formatString, values[0][i])).append(sep);
                result.append(String.format(formatString, values[1][i])).append(sep);
            }
        }
        return (result.toString().trim());
    }

    public String toXMLString() {
        StringBuilder result = new StringBuilder();

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
        result.append("<peak _Peak_list_number=\"").append(getIdNum()).
                append("\">");
        result.append("<_Intensity_height>").append(getIntensity()).
                append("</_Intensity_height>");
        result.append("<_Intensity_volume>").append(getVolume1()).
                append("</_Intensity_volume>");
        result.append("<_Intensity_volume2>").append(getVolume2()).
                append("</_Intensity_volume2>");
        result.append("<_Peak_status>").append(getStatus()).
                append("</_Peak_status>");
        result.append("<_Peak_type>").append(typeToString()).
                append("</_Peak_type>");

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

        result.append("<_comment>").append(getComment()).append("</_comment>");

        for (i = 0; i < getNDim(); i++) {
            result.append("<_label dim=\"").append(i).append("\">").
                    append(peakDims[i].getLabel()).append("</_label>\n");
            result.append("<_chem_shift dim=\"").append(i).append("\">").
                    append(peakDims[i].getChemShiftValue()).append("</_chem_shift>\n");
            result.append("<_line_width dim=\"").append(i).append("\">").
                    append(peakDims[i].getLineWidthValue()).append("</_line_width>\n");
            result.append("<_bounds dim=\"").append(i).append("\">").
                    append(peakDims[i].getBoundsValue()).append("</_bounds>\n");
            result.append("<_error dim=\"").append(i).append("\">").
                    append(String.valueOf(peakDims[i].getError())).append("</_error>\n");
            result.append("<_j dim=\"").append(i).append("\">").
                    append(peakDims[i].getMultiplet().getCouplingsAsString()).append("</j>\n");
            result.append("<_j dim=\"").append(i).append("\">").
                    append(peakDims[i].getResonanceIDsAsString()).append("</j>\n");

            result.append("<_user dim=\"").append(i).append("\">").
                    append(peakDims[i].getUser()).append("</_user>\n");
            result.append("\n");
        }

        return (result.toString());
    }

    public String getName() {
        if (peakList != null) {
            return peakList.getName() + "." + getIdNum();
        } else {
            return "." + idNum;
        }
    }

    public int getIdNum() {
        return idNum;
    }

    public void setIdNum(int idNum) {
        this.idNum = idNum;
        if (idNum >= peakList.idLast) {
            peakList.idLast = idNum;

        }
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

    public float getVolume1Err() {
        return volume1Err;
    }

    public void setVolume1(float volume1) {
        this.volume1 = volume1;
        peakUpdated(this);
    }

    public void setVolume1Err(float err) {
        this.volume1Err = err;
        peakUpdated(this);
    }

    public float getIntensity() {
        return intensity;
    }

    public float getIntensityErr() {
        return intensityErr;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
        peakUpdated(this);
    }

    public void setIntensityErr(float err) {
        this.intensityErr = err;
        peakUpdated(this);
    }

    public float getVolume2() {
        return volume2;
    }

    public float getVolume2Err() {
        return volume2Err;
    }

    public void setVolume2(float volume2) {
        this.volume2 = volume2;
        peakUpdated(this);
    }

    public void setType(int type) {
        this.type = type;
        peakUpdated(this);
    }

    public boolean isDeleted() {
        return status < 0;
    }

    public void delete() {
        setStatus(-1);
    }

    @Override
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
        peakUpdated(this);
    }

    public String getColorName() {
        String colorString = colorArray == null ? "" : ColorUtil.toRGBCode(colorArray);
        return colorString;
    }

    public int[] getColor() {
        return colorArray;
    }

    public void setColorInt(int[] colors) {
        if (colors != null) {
            colorArray = colors.clone();
        }
        peakUpdated(this);
    }

    public void setColor(String colorName) {
        if (colorName != null) {
            colorArray = NMRFxColor.parseColor(colorName);
        }
        peakUpdated(this);
    }

    public void unSetColor() {
        colorArray = null;
        peakUpdated(this);
    }

    public String getComment() {
        return comment;
    }

    public final void setComment(String comment) {
        this.comment = comment;
        peakUpdated(this);
    }

    public void updateFrozenColor() {
        int colorIndex = 0;
        if (peakDims[0].isFrozen()) {
            colorIndex++;
        }
        if ((peakDims.length > 1) && peakDims[1].isFrozen()) {
            colorIndex += 2;
        }
        if (colorIndex == 0) {
            colorArray = null;
        } else {
            colorArray = FREEZE_COLORS[colorIndex - 1].clone();
        }
    }

    public boolean getFlag(int index) {
        return flag[index];
    }

    public void setFrozen(boolean state, boolean allConditions) {
        for (PeakDim pDim : peakDims) {
            pDim.setFrozen(state, allConditions);
        }
    }

    public final void setFlag(int index, boolean value) {
        this.flag[index] = value;
        peakUpdated(this);
    }

    public final void setFlag2(int index, String valueStr) {
        boolean value = valueStr.equals("1");
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
        StringBuilder flagResult = new StringBuilder();
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

    public String getFlag2() {
        StringBuilder flagResult = new StringBuilder();
        boolean firstEntry = true;
        for (int i = 0; i < NFLAGS; i++) {
            if (getFlag(i)) {
                if (!firstEntry) {
                    flagResult.append("_");
                }
                flagResult.append(i);
                firstEntry = false;
            }
        }
        return flagResult.toString();
    }

    public void setFlag2(String flagString) {
        for (int i = 0; i < flag.length; i++) {
            flag[i] = false;
        }
        if (flagString.length() > 0) {
            String[] fields = flagString.split("_");
            for (String field : fields) {
                int i = Integer.parseInt(field);
                flag[i] = true;

            }
        }
        peakUpdated(this);
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
        switch (cornerStr.length()) {
            case 2:
                setCorner(cornerStr.toCharArray());
                break;
            case 1:
                char[] newCorner = {' ', ' '};
                switch (cornerStr) {
                    case "w":
                    case "e":
                        newCorner[1] = cornerStr.charAt(0);
                        setCorner(newCorner);
                        break;
                    case "n":
                    case "s":
                        newCorner[0] = cornerStr.charAt(0);
                        setCorner(newCorner);
                        break;
                    default:
                        // fixme throw exception ?
                        newCorner[0] = 'n';
                        newCorner[0] = 'e';
                        setCorner(newCorner);
                        break;
                }
                break;
            default:
                int cornerIndex = cornerStr.indexOf(' ');
                if (cornerIndex == -1) {
                    cornerIndex = cornerStr.indexOf('\t');
                }
                if (cornerIndex == -1) {
                    setCorner("ne");
                } else {
                    try {
                        double x = Double.parseDouble(cornerStr.substring(0, cornerIndex));
                        double y = Double.parseDouble(cornerStr.substring(cornerIndex + 1));
                        this.corner = new Corner(x, y);
                    } catch (NumberFormatException e) {
                        setCorner("ne");
                    }

                }
                break;
        }
    }

    public float getFigureOfMerit() {
        return figureOfMerit;
    }

    public void setFigureOfMerit(float newValue) {
        figureOfMerit = newValue;
    }

    /**
     * Get the boundaries, center and widths of the region of a peak in a
     * specified dataset. The indices of the arrays containing this information
     * are the dataset dimensions. So p[0][0] and p[0][1] will contain borders
     * of the peak along dimension 0 of the dataset, which may be a different
     * dimension than dimension 0 of the peak.
     *
     * @param theFile        The dataset to use for translating ppm to pts
     * @param pkToData           An integer mapping of peak dimension to dataset dimension.
     *                       For example, pkToData[0] contains the dataset dimension that corresponds to
     *                       peak dimension 0.
     * @param p              Two-dimensional pre-allocated array of int that will contain the
     *                       boundaries of the peak dimension. The boundaries are determined by the
     *                       peak foot print (bounds).
     * @param cpt            Array of ints specifying the center of the peak region.
     * @param width          Array of doubles containing the widths of the peak in units
     *                       of dataset points. The width is determined by the peak linewidth
     * @param meanLineWidths Array of mean widths of peaks in list. If null, the
     *                       line width of individual peak is used.
     */
    public void getPeakRegion(DatasetBase theFile, int[] pkToData, int[][] p,
                              int[] cpt, double[] width, double[] meanLineWidths, double widthScale) {
        double p1;
        double p2;
        double p1d;
        double p2d;

        for (int i = 0; i < peakList.nDim; i++) {
            double pc = peakDims[i].getChemShiftValue();

            p1 = pc + Math.abs(peakDims[i].getBoundsValue() * widthScale) / 2;
            p[pkToData[i]][0] = theFile.ppmToFoldedPoint(pkToData[i], p1);

            p2 = pc - Math.abs(peakDims[i].getBoundsValue() * widthScale) / 2;
            p[pkToData[i]][1] = theFile.ppmToFoldedPoint(pkToData[i], p2);
            cpt[pkToData[i]] = theFile.ppmToFoldedPoint(pkToData[i], pc);
            double lineWidth = meanLineWidths == null ? peakDims[i].getLineWidthValue() : meanLineWidths[i];
            lineWidth *= widthScale;
            p1 = peakDims[i].getChemShiftValue() + (Math.abs(lineWidth) / 2.0);
            p1d = theFile.ppmToDPoint(pkToData[i], p1);
            p2 = peakDims[i].getChemShiftValue() - (Math.abs(lineWidth) / 2.0);
            p2d = theFile.ppmToDPoint(pkToData[i], p2);
            width[pkToData[i]] = Math.abs(p2d - p1d);
        }
    }

    void fold(int iDim, String foldDir)
            throws IllegalArgumentException {
        int iUpDown = 0;

        switch (foldDir) {
            case "up":
                iUpDown = 1;
                break;
            case "down":
                iUpDown = -1;
                break;
            default:
                throw new IllegalArgumentException(
                        "nv_peak fold: Invalid direction " + foldDir);
        }

        peakDims[iDim].setChemShiftValueNoCheck((float) (peakDims[iDim].getChemShiftValue()
                + ((iUpDown * peakList.getSpectralDim(iDim).getSw()) / peakList.getSpectralDim(iDim).getSf())));
    }

    public double[] measurePeak(DatasetBase dataset, int[] pdim, int[] planes, Function<RegionData, Double> f, String mode) throws IOException {
        RegionData regionData = Peak.analyzePeakRegion(this, dataset, planes, pdim);
        double value = f.apply(regionData);
        Double noise = dataset.getNoiseLevel();
        double err = 0.0;
        if (noise != null) {
            int nPoints = regionData.getNpoints(mode);
            err = nPoints == 1 ? noise.floatValue() : Math.sqrt(nPoints) * noise.floatValue();
        }
        double[] result = {value, err};
        return result;
    }

    public void setMeasures(double[][] values) {
        measures = Optional.of(values);
    }

    public Optional<double[][]> getMeasures() {
        return measures;
    }

    public double getFirstIntensity() {
        if (measures.isPresent()) {
            return measures.get()[0][0];
        } else {
            return getIntensity();
        }
    }

    public void quantifyPeak(DatasetBase dataset, int[] pdim, Function<RegionData, Double> f, String mode) throws IOException, IllegalArgumentException {
        int[] planes = new int[0];
        RegionData regionData = Peak.analyzePeakRegion(this, dataset, planes, pdim);
        double value = f.apply(regionData);
        if (mode.contains("volume")) {
            volume1 = (float) value;
            Double noise = dataset.getNoiseLevel();
            if (noise != null) {
                int nPoints = mode.equals("evolume") ? regionData.getNEllipticalPoints() : regionData.getNpoints();
                volume1Err = (float) Math.sqrt(nPoints) * noise.floatValue();
            }
        } else {
            intensity = (float) value;
            Double noise = dataset.getNoiseLevel();
            if (noise != null) {
                intensityErr = noise.floatValue();
            }
        }
    }

    public void tweak(DatasetBase dataset, int[] pdim, int[] planes) throws IOException {
        RegionData regionData = Peak.analyzePeakRegion(this, dataset, planes, pdim);
        double[] maxPoint = regionData.getMaxDPoint();
        for (int i = 0; i < peakDims.length; i++) {
            PeakDim tweakDim = peakDims[i];
            if (!tweakDim.isFrozen()) {
                //int iDim = regionData
                double position = dataset.pointToPPM(pdim[i], maxPoint[pdim[i]]);
                tweakDim.setChemShiftValue((float) position);
            }
        }
    }

    public RegionData analyze() throws IOException {
        DatasetBase dataset = DatasetBase.getDataset(getPeakList().getDatasetName());
        if (dataset == null) {
            throw new IllegalArgumentException("No dataset");
        }
        return analyze(dataset);
    }

    public RegionData analyze(DatasetBase dataset) throws IOException {
        int[] planes = new int[0];
        return analyzePeakRegion(dataset, planes);
    }

    public RegionData analyzePeakRegion(DatasetBase theFile, int[] planes)
            throws IOException {
        int dataDim = theFile.getNDim();
        if (dataDim != (peakList.nDim + planes.length)) {
            throw new IllegalArgumentException("Number of peak list dimensions not equal to number of dataset dimensions");
        }
        int[] pdim = peakList.getDimsForDataset(theFile);
        return Peak.analyzePeakRegion(this, theFile, planes, pdim);
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

    public String toSparkyString() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
//      ?-?-?  125.395   55.758    8.310      2164733.500
        result.append("   ");
        for (int i = 0; i < getNDim(); i++) {
            String label = peakDims[i].getLabel();
            if (label.equals("")) {
                label = "?";
            }
            if (i > 0) {
                result.append("-");
            }
            result.append(label);
        }
        result.append(sep);
        for (int i = 0; i < getNDim(); i++) {
            result.append(String.format("%8.4f", peakDims[i].getChemShiftValue())).append(sep);
        }
        result.append(String.format("%14.3f", 1.0e6 * getIntensity()));
        return (result.toString());
    }

    public double distance(Peak bPeak, double[] scale)
            throws IllegalArgumentException {
        double sum = 0.0;

        if (peakDims.length != bPeak.peakDims.length) {
            throw new IllegalArgumentException(
                    "peaks don't have same number of dimensions");
        }

        for (int i = 0; i < peakDims.length; i++) {
            double dif = peakDims[i].getChemShiftValue() - bPeak.peakDims[i].getChemShiftValue();
            sum += ((dif / scale[i]) * (dif / scale[i]));
        }

        return Math.sqrt(sum);
    }

    public boolean isLinked(int dim) {
        if ((dim < 0) || (dim >= peakDims.length)) {
            return false;
        } else {
            return peakDims[dim].isLinked();
        }
    }

    private CouplingUpdate startUpdateCouplings(int iDim) {
        CouplingUpdate cUpdate;
        PeakDim pDim = peakDims[iDim];

        List<PeakDim> links = pDim.getLinkedPeakDims();
        links.sort(comparing(PeakDim::getChemShift));

        int iPos = 0;
        for (PeakDim itDim : links) {
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
        int iCoupling;
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

    public int getDerivationSet() {
        return 0;
    }

    public boolean inRegion(double[][] limits, double[][] foldLimits, int[] dim) {
        int nSearchDim = limits.length;
        boolean ok = true;
        for (int j = 0; j < nSearchDim; j++) {
            if ((dim.length <= j) || (dim[j] == -1) || (dim[j] >= peakDims.length)) {
                continue;
            }
            double ctr = peakDims[dim[j]].getChemShiftValue();
            if ((foldLimits != null) && (foldLimits[j] != null)) {
                double fDelta = Math.abs(foldLimits[j][0] - foldLimits[j][1]);
                ctr = peakList.foldPPM(ctr, fDelta, foldLimits[j][0], foldLimits[j][1]);
            }

            if ((ctr < limits[j][0]) || (ctr > limits[j][1])) {
                ok = false;
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

    public boolean overlapsLineWidth(final Peak peak, final int dim, final double scale) {
        boolean result = false;
        PeakDim pdimA = getPeakDim(dim);
        PeakDim pdimB = peak.getPeakDim(dim);
        double ctrA = pdimA.getChemShiftValue();
        double widA = pdimA.getLineWidthValue();
        double ctrB = pdimB.getChemShiftValue();
        double widB = pdimB.getLineWidthValue();
        if (ctrA > ctrB) {
            if ((ctrA - scale * widA / 2.0) < (ctrB + scale * widB / 2.0)) {
                result = true;
            }
        } else if ((ctrA + scale * widA / 2.0) > (ctrB - scale * widB / 2.0)) {
            result = true;
        }
        return result;
    }

    public List<Set<Peak>> getOverlapLayers(double scale) {
        List<Set<Peak>> result = new ArrayList<>();
        Set<Peak> firstLayer = getOverlappingPeaks(scale);
        Set<Peak> secondLayer = new HashSet<>();
        for (Peak peak : firstLayer) {
            Set<Peak> overlaps = peak.getOverlappingPeaks(scale);
            for (Peak peak2 : overlaps) {
                if ((peak2 != this) && !firstLayer.contains(peak2)) {
                    secondLayer.add(peak2);
                }
            }
        }
        Set<Peak> centerLayer = new HashSet<>();
        centerLayer.add(this);
        result.add(centerLayer);
        result.add(firstLayer);
        result.add(secondLayer);
        return result;
    }

    public Set<Peak> getOverlappingPeaks(Set<Peak> overlaps) {
        Set<Peak> result = new HashSet<>();
        result.addAll(overlaps);
        for (Peak peak : overlaps) {
            Set<Peak> newSet = peak.getOverlappingPeaks();
            result.addAll(newSet);
        }
        return result;
    }

    public Set<Peak> getAllOverlappingPeaks() {
        Set<Peak> overlaps = new HashSet<>();
        overlaps.add(this);
        int size = 0;
        while (overlaps.size() > size) {
            size = overlaps.size();
            overlaps = getOverlappingPeaks(overlaps);
        }
        return overlaps;
    }

    public Set<Peak> getOverlappingPeaks() {
        return getOverlappingPeaks(1.0);
    }

    public Set<Peak> getOverlappingPeaks(double scale) {
        Set<Peak> overlaps = new HashSet<>();
        int nDim = peakList.nDim;
        for (int i = 0; i < peakList.size(); i++) {
            Peak peak = (Peak) getPeakList().getPeak(i);
            if ((peak.getStatus() < 0) || (this == peak)) {
                continue;
            }
            boolean ok = true;
            for (int iDim = 0; iDim < nDim; iDim++) {
                if (!overlapsLineWidth(peak, iDim, scale)) {
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

    public class Corner {

        private final double x;
        private final double y;
        private final char[] cornerChars;

        Corner(double x, double y) {
            this.x = x;
            this.y = y;
            cornerChars = null;
        }

        public Corner(char[] cornerChars) {
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

        public Corner(String cornerStr) {
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

        public char[] getCornerChars() {
            return cornerChars;
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
                switch (cornerChars[0]) {
                    case 'n':
                        position[1] = py2;
                        break;
                    case ' ':
                        position[1] = (py2 + py1) / 2.0;
                        break;
                    default:
                        position[1] = py1;
                        break;
                }

                switch (cornerChars[1]) {
                    case 'e':
                        position[0] = px2;
                        break;
                    case ' ':
                        position[0] = (px1 + px2) / 2.0;
                        break;
                    default:
                        position[0] = px1;
                        break;
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
                switch (cornerChars[0]) {
                    case 'n':
                        position[1] = -0.5;
                        break;
                    case ' ':
                        position[1] = 0.0;
                        break;
                    default:
                        position[1] = 0.5;
                        break;
                }

                switch (cornerChars[1]) {
                    case 'e':
                        position[0] = 0.5;
                        break;
                    case ' ':
                        position[0] = 0.0;
                        break;
                    default:
                        position[0] = -0.5;
                        break;
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
                switch (cornerChars[0]) {
                    case 'n':
                        anchor[0] = 's';
                        break;
                    case ' ':
                        anchor[0] = ' ';
                        break;
                    default:
                        anchor[0] = 'n';
                        break;
                }

                switch (cornerChars[1]) {
                    case 'e':
                        anchor[1] = 'w';
                        break;
                    case ' ':
                        anchor[1] = ' ';
                        break;
                    default:
                        anchor[1] = 'e';
                        break;
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

    public PeakDim[] getPeakDims() {
        return peakDims;
    }

    public void initPeakDimContribs() {
        for (PeakDim peakDim : peakDims) {
            peakDim.initResonance();
        }
    }

    public PeakDim getPeakDim(int iDim) throws IllegalArgumentException {
        PeakDim iPeakDim = null;
        if ((iDim >= 0) && (iDim < peakList.nDim)) {
            iPeakDim = peakDims[iDim];
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
                matchDim = peakDims[i];
            }
        }

        return matchDim;
    }

    public enum AssignmentLevel {
        DELETED("Deleted"),
        UNASSIGNED("None assigned"),
        AVM("All Assigned, Valid, Ambiguous"),
        AVU("All Assigned, Valid, Unambiguous"),
        AIM("All Assigned, Invalid, Ambiguous"),
        AIU("All Assigned, Invalid, Unambiguous"),
        SVM("Some Assigned, Valid, Ambiguous"),
        SVU("Some Assigned, Valid, Unambiguous"),
        SIM("Some Assigned, Invalid, Ambiguous"),
        SIU("Some Assigned, Invalid, Unambiguous");

        String description;

        AssignmentLevel(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }

        public static boolean match(AssignmentLevel level, String mode) {
            return switch (mode) {
                case "all" -> true;
                case "ok" -> level != DELETED;
                case "deleted" -> level == DELETED;
                case "assigned" -> level == AVU || level == AVM;
                case "partial" -> level == SVM || level == SVU;
                case "unassigned" -> level == UNASSIGNED;
                case "ambiguous" -> level == AVM || level == SVM;
                case "invalid" -> level == AIM || level == AIU || level == SIM || level == SIU;
                default -> true;
            };
        }
    }

    public AssignmentLevel getAssignmentLevel() {
        boolean assigned = true;
        boolean someAssigned = false;
        boolean invalid = false;
        boolean ambiguous = false;
        MoleculeBase molecule = MoleculeFactory.getActive();
        if (isDeleted()) {
            return AssignmentLevel.DELETED;
        }
        for (var peakDim : peakDims) {
            String labels = peakDim.getLabel();
            if (labels.isBlank()) {
                assigned = false;
            } else {
                String[] fields = labels.split(" ");
                if (fields.length > 1) {
                    ambiguous = true;
                }
                for (String label : fields) {
                    if (label.isBlank()) {
                        assigned = false;
                    } else {
                        if ((molecule != null) && (molecule.findAtom(label) == null)) {
                            invalid = true;
                        }
                        someAssigned = true;
                    }
                }
            }
        }
        AssignmentLevel result;
        if (!someAssigned) {
            result = AssignmentLevel.UNASSIGNED;
        } else if (assigned && !invalid && ambiguous) {
            result = AssignmentLevel.AVM;
        } else if (assigned && !invalid && !ambiguous) {
            result = AssignmentLevel.AVU;
        } else if (assigned && invalid && ambiguous) {
            result = AssignmentLevel.AIM;
        } else if (assigned && invalid && !ambiguous) {
            result = AssignmentLevel.AIU;
        } else if (someAssigned && !invalid && ambiguous) {
            result = AssignmentLevel.SVM;
        } else if (someAssigned && !invalid && !ambiguous) {
            result = AssignmentLevel.SVU;
        } else if (someAssigned && invalid && ambiguous) {
            result = AssignmentLevel.SIM;
        } else {
            result = AssignmentLevel.SIU;
        }
        return result;
    }

    class CouplingUpdate {

        double origValue;
        int direction;
        int iCoupling;
    }
}
