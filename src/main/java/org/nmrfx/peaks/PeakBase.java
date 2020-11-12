package org.nmrfx.peaks;

import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.utilities.ColorUtil;
import org.nmrfx.processor.utilities.Format;

import java.util.List;
import java.util.Optional;

public class PeakBase implements Comparable, PeakOrMulti {

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
        "_Peak_char.Coupling_detail",
        "_Peak_char.Frozen"};

    static String spectralTransitionStrings[] = {
        "_Spectral_transition.ID",
        "_Spectral_transition.Peak_ID",
        "_Spectral_transition.Figure_of_merit",
        "_Spectral_transition.Details",};

    static String spectralTransitionGeneralCharStrings[] = {
        "_Spectral_transition_general_char.Spectral_transition_ID",
        "_Spectral_transition_general_char.Peak_ID",
        "_Spectral_transition_general_char.Intensity_val",
        "_Spectral_transition_general_char.Intensity_val_err",
        "_Spectral_transition_general_char.Measurement_method",};

    static String spectralTransitionCharStrings[] = {
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

    static String peakComplexCouplingStrings[] = {
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
    static String peakCouplingPatternStrings[] = {
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
    static final public int[][] FREEZE_COLORS = {{255, 165, 0}, {255, 0, 255}, {255, 0, 0}};
    protected static final int N_TYPES = 9;
    protected static String[] peakTypes = new String[N_TYPES];
    public PeakDim[] peakDims;
    protected float figureOfMerit = 1.0f;
    protected boolean valid = true;
    protected int idNum;
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
    public PeakListBase peakList;

    public PeakBase(int nDim) {
        peakDims = new PeakDim[nDim];
    }

    public PeakBase(PeakListBase peakList, int nDim) {
        this(nDim);
        this.peakList = peakList;
        idNum = peakList.idLast + 1;
        peakList.idLast += 1;
    }

    @Override
    public int compareTo(Object o) {
        int result = 1;
        if (o instanceof Peak) {
            PeakBase peak2 = (PeakBase) o;
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
        return PeakBase.peakStrings;
    }

    public static String[] getSTAR3GeneralCharStrings() {
        return PeakBase.peakGeneralCharStrings;
    }

    public static String[] getSTAR3CharStrings() {
        return PeakBase.peakCharStrings;
    }

    public static String[] getSTAR3SpectralTransitionStrings() {
        return PeakBase.spectralTransitionStrings;
    }

    public static String[] getSTAR3SpectralTransitionGeneralCharStrings() {
        return PeakBase.spectralTransitionGeneralCharStrings;
    }

    public static String[] getSTAR3SpectralTransitionCharStrings() {
        return PeakBase.spectralTransitionCharStrings;
    }

    public static String[] getSTAR3ComplexCouplingStrings() {
        return PeakBase.peakComplexCouplingStrings;
    }

    public static String[] getSTAR3CouplingPatternStrings() {
        return PeakBase.peakCouplingPatternStrings;
    }

    public static String[] getPeakTypes() {
        return peakTypes;
    }

    @Override
    public PeakListBase getPeakList() {
        return (PeakList) peakList;
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

                sBuf.append(PeakBase.typeToString(j));
                n++;
            }

            j *= 2;
        }

        return sBuf.toString();
    }

    public static String typeToString(int type) {
        switch (type) {
            case PeakBase.COMPOUND:
                return "compound";
            case PeakBase.MINOR:
                return "minor";
            case PeakBase.SOLVENT:
                return "solvent";
            case PeakBase.IMPURITY:
                return "impurity";
            case PeakBase.CHEMSHIFT_REF:
                return "chemshiftRef";
            case PeakBase.QUANTITY_REF:
                return "quantityRef";
            case PeakBase.COMBO_REF:
                return "comboRef";
            case PeakBase.WATER:
                return "water";
            default:
                return "artifact";
        }
    }

    @Override
    public String toString() {
        return getName();
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
        return PeakBase.typeToString(getType());
    }

    public void setType(int type, int flagLoc) {
        this.setType(type);

        if ((flagLoc >= 0) && (flagLoc < NFLAGS)) {
            List<Peak> lPeaks = PeakListBase.getLinks(this);

            for (int i = 0, n = lPeaks.size(); i < n; i++) {
                Peak lPeak = (Peak) lPeaks.get(i);
                if (type != PeakBase.COMPOUND) {
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
        char stringQuote = '"';
        result.append(String.valueOf(getIdNum())).append(sep);
        result.append(String.valueOf(getFigureOfMerit())).append(sep);
        result.append(stringQuote);
        result.append(getComment());
        result.append(stringQuote);
        result.append(sep);
        result.append(typeToString());
        result.append(sep);
        result.append(getStatus());
        result.append(sep);
        String colorName = getColorName();
        if (colorName.equals("")) {
            result.append(".");
        } else {
            result.append(stringQuote);
            result.append(colorName);
            result.append(stringQuote);
        }
        result.append(sep);
        result.append(getFlag());
        result.append(sep);
        result.append(stringQuote);
        result.append(String.valueOf(getCorner()));
        result.append(stringQuote);
        return result.toString();
    }

    public String toSTAR3LoopSpectralTransitionString(int id) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        char stringQuote = '"';
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
        char stringQuote = '"';
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

        result.append(sep).append(stringQuote).append(getComment()).
                append(stringQuote).append(sep);
        result.append(stringQuote);
        result.append(String.valueOf(getCorner()));
        result.append(stringQuote);
        result.append(sep);
        result.append("\n");

        for (i = 0; i < getNDim(); i++) {

            result.append(stringQuote).
                    append(String.valueOf(peakDims[i].getLabel())).
                    append(stringQuote).append(sep);
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

            result.append(stringQuote);
            result.append(String.valueOf(peakDims[i].getResonanceIDsAsString()));
            result.append(stringQuote);
            result.append(sep);
            result.append(sep).append(stringQuote).append(peakDims[i].getUser()).
                    append(stringQuote).append(sep);
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
    public String getName() {
        return peakList.getName() + "." + getIdNum();
    }

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
            colorArray = ColorUtil.fromRGBCode(colorName);
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
}
