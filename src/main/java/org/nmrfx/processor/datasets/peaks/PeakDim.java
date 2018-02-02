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

import org.nmrfx.processor.star.STAR3;
import org.nmrfx.processor.utilities.ConvUtil;
import org.nmrfx.processor.utilities.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class PeakDim {

    public static ResonanceFactory resFactory;

    static {
        try {
            Class c = Class.forName("org.nmrfx.processor.datasets.peaks.AtomResonanceFactory");
            try {
                resFactory = (ResonanceFactory) c.newInstance();
            } catch (InstantiationException | IllegalAccessException ex) {
                resFactory = new ResonanceFactory();
            }
        } catch (ClassNotFoundException ex) {
            resFactory = new ResonanceFactory();
        }
        resFactory.init();

    }
    public static List<PeakDim> EMPTY_LIST = new ArrayList<>();

    private int spectralDim = 0;
    private Float chemShift = null;
    private Float chemShiftError = null;
    private Float lineWidth = null;
    private Float lineWidthError = null;
    private Float bounds = null;
    private Float boundsError = null;
    private Float phase = null;
    private Float phaseError = null;
    private Float decayRate = null;
    private Float decayRateError = null;
    private Multiplet multiplet = null;
//    private Coupling coupling = null;
    private char[] error = {'+', '+'};
    private String user = "";
    Peak myPeak = null;
    private Resonance resonance;
    private boolean frozen = false;

    void peakDimUpdated() {
        if (myPeak != null) {
            myPeak.peakUpdated(this);
        }
    }

    PeakDim(Peak peak, int iDim) {
        myPeak = peak;
        setSpectralDim(iDim);
        //peakDimContribs = new ArrayList();
    }

    public static void setResonanceFactory(ResonanceFactory newFactory) {
        resFactory = newFactory;
    }

    public PeakDim copy(Peak peak) {
        PeakDim newPeakDim = new PeakDim(peak, spectralDim);
        newPeakDim.chemShift = chemShift;
        newPeakDim.chemShiftError = chemShiftError;
        newPeakDim.lineWidth = lineWidth;
        newPeakDim.lineWidthError = lineWidthError;
        newPeakDim.bounds = bounds;
        newPeakDim.boundsError = boundsError;
        newPeakDim.phase = phase;
        newPeakDim.phaseError = phaseError;
        newPeakDim.decayRate = decayRate;
        newPeakDim.decayRateError = decayRateError;
        newPeakDim.error = error.clone();
        newPeakDim.user = user;
        return newPeakDim;
    }

    public void copyLabels(PeakDim newPeakDim) {
        Resonance resOld = getResonance();
        Resonance resNew = newPeakDim.getResonance();
        resNew.setName(resOld.getName());
    }

    public String getDimName() {
        return myPeak.peakList.getSpectralDim(getSpectralDim()).getDimName();
    }

    public String getName() {
        return myPeak.getName() + "." + getDimName();
    }

    public void initResonance() {
        resonance = resFactory.build();
        resonance.add(this);
    }

    public Resonance getResonance() {
        return resonance;
    }

    public String getResonanceIDsAsString() {
        return resonance.getIDString();
    }

    public List<PeakDim> getLinkedPeakDims() {
        if (resonance == null) {
            // fixme should this contain this peakdim (and in general should result contain this dim plus linked)
            return EMPTY_LIST;
        } else {
            return resonance.getPeakDims();
        }
    }

    public String getSummary() {
        double normVal = 0;
        String summary = "";
        // FIXME  make precision in ctr a function of dig resolution  sw/sfrq/size
        if (myPeak.peakList.scale > 0.0) {
            normVal = myPeak.getVolume1() / myPeak.peakList.scale;
        }

        summary = Format.format3(getChemShiftValue()) + " " + Format.format2(normVal);
        return summary;
    }

    public Set<PeakDim> getCoupledPeakDims() {
        if (multiplet == null) {
            Set<PeakDim> set = new HashSet<PeakDim>();
            set.add(this);
            return set;
        } else {
            return multiplet.getPeakDims();
        }
    }

    public boolean hasMultiplet() {
        return multiplet != null;
    }

    public Multiplet getMultiplet() {
        Multiplet aMultiplet = multiplet;
        if (aMultiplet == null) {
            List<PeakDim> peakDims = getLinkedPeakDims();
            for (PeakDim peakDim : peakDims) {
                if (peakDim.multiplet != null) {
                    aMultiplet = peakDim.multiplet;
                }
            }
        }
        if (aMultiplet == null) {
            multiplet = new Multiplet(this);
            aMultiplet = multiplet;
        }

        return aMultiplet;
    }

    public void setMultiplet(Multiplet newMultiplet) {
        if (multiplet == newMultiplet) {
            return;
        }
        if (multiplet != null) {
            multiplet.removePeakDim(this);
        }
        this.multiplet = newMultiplet;
    }

    public void unLink() {
        resonance.remove(this);
        initResonance();
        if (multiplet != null) {
            multiplet.removePeakDim(this);
            multiplet = new Multiplet(this);
        }

        peakDimUpdated();
    }

    public void remove() {
        if (resonance != null) {
            resonance.remove(this);
        }
    }

    public void setResonance(long resID) {
        remove();
        resonance = resFactory.get(resID);
    }

    public void setResonance(Resonance newResonance) {
        resonance = newResonance;
    }

    public String toSTAR3LoopAssignedPeakChemShiftString(int iContrib, long resID) {
        StringBuffer result = new StringBuffer();
        String sep = " ";
        result.append(getPeak().getIdNum() + sep);
        result.append(sep);
        result.append((spectralDim + 1));
        result.append(sep);
        result.append(STAR3.valueOf(getChemShift()));
        result.append(sep);
        result.append(resID);
        result.append(sep);
        result.append(getPeak().peakList.getId());
        return result.toString();
    }

    public String toSTAR3LoopPeakCharString(int contributionID) {
        StringBuffer result = new StringBuffer();
        String sep = " ";
        char stringQuote = '"';
        result.append(getPeak().getIdNum() + sep);
        result.append(contributionID);
        result.append(sep);
        result.append((spectralDim + 1));
        result.append(sep);
        result.append(STAR3.valueOf(getChemShift()) + sep);
        result.append(STAR3.valueOf(getChemShiftError()) + sep);
        result.append(STAR3.valueOf(getBounds()) + sep);
        result.append(STAR3.valueOf(getBoundsError()) + sep);
        SpectralDim sDim = getPeak().peakList.getSpectralDim(spectralDim);
        Float lw = getLineWidth();
        if (lw == null) {
            result.append(".");
        } else {
            float lwf = (float) (sDim.getSf() * lw.floatValue());
            result.append(lwf);
        }
        result.append(sep);
        result.append(STAR3.valueOf(getLineWidthError()) + sep);
        result.append(STAR3.valueOf(getPhase()) + sep);
        result.append(STAR3.valueOf(getPhaseError()) + sep);
        result.append(STAR3.valueOf(getDecayRate()) + sep);
        result.append(STAR3.valueOf(getDecayRateError()) + sep);
        result.append(0 + sep); // fixme derivation method
        result.append(getError()[0] + "" + getError()[1]);
        result.append(sep);
        result.append(stringQuote);
        result.append(getUser()); // fixme only quote if more than one
        result.append(stringQuote);
        result.append(sep);
        result.append(stringQuote);
        // result.append(getCouplingsAsString()); // fixme only quote if more than one
        result.append("0.0");
        result.append(stringQuote);
        return result.toString();
    }

    String toNEFString(int contributionID) {
        StringBuffer result = new StringBuffer();
        String sep = " ";
        result.append(STAR3.valueOf(getChemShift()) + sep);
        result.append(STAR3.valueOf(getChemShiftError()) + sep);
        return result.toString();
    }

    public void setFrozen(boolean state) {
        frozen = state;
        if (myPeak.peakList.isSlideable()) {
            myPeak.updateFrozenColor();
            freezeDims();
        }
        peakDimUpdated();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Float getAdjustedChemShift() {
        return chemShift;
    }

    public float getAdjustedChemShiftValue() {
        float value = 0.0f;
        if (chemShift != null) {
            value = chemShift.floatValue();
        }
        return value;
    }

    public Float getChemShift() {
        return chemShift;
    }

    public float getChemShiftValue() {
        float value = 0.0f;
        if (chemShift != null) {
            value = chemShift.floatValue();
        }
        return value;
    }

    public Float getChemShiftError() {
        return chemShiftError;
    }

    public void setChemShiftValueNoCheck(float ctr) {
        this.chemShift = new Float(ctr);
        if (myPeak.peakList.isSlideable() && !frozen) {
            slideDims();
        }
        peakDimUpdated();
    }

    public void setChemShift(Float value) {
        this.chemShift = value;
        if (myPeak.peakList.isSlideable() && !frozen) {
            slideDims();
        }
        peakDimUpdated();
    }

    public void setChemShiftValue(float ctr) {
        this.chemShift = new Float(ctr);
        if (myPeak.peakList.isSlideable() && !frozen) {
            slideDims();
        }

        if (myPeak.getFlag(5)) {
            //fixme setMultipletComponentValues();
        }
        peakDimUpdated();
    }

    public void setChemShiftErrorValue(float value) {
        this.chemShiftError = new Float(value);
        peakDimUpdated();
    }

    public Float getLineWidth() {
        return lineWidth;
    }

    public float getLineWidthValue() {
        float value = 0.0f;

        if (lineWidth != null) {
            return lineWidth.floatValue();
        } else {
            return value;
        }
    }

    public Float getLineWidthError() {
        return lineWidthError;
    }

    public void setLineWidthValue(float wid) {
        this.lineWidth = new Float(wid);
        peakDimUpdated();
    }

    public void setLineWidthHz(float wid) {
        wid /= getSpectralDimObj().getSf();
        this.lineWidth = new Float(wid);
        peakDimUpdated();
    }

    public void setLineWidthErrorValue(float wid) {
        this.lineWidthError = new Float(wid);
        peakDimUpdated();
    }

    public float getLineWidthHz() {
        float value = 0.0f;

        if (lineWidth != null) {
            return lineWidth * (float) getSpectralDimObj().getSf();
        } else {
            return value;
        }
    }

    public void setBoundsHz(float bounds) {
        bounds /= getSpectralDimObj().getSf();
        this.bounds = new Float(bounds);
        peakDimUpdated();
    }

    public float getBoundsHz() {
        float value = 0.0f;

        if (bounds != null) {
            return bounds * (float) getSpectralDimObj().getSf();
        } else {
            return value;
        }
    }

    public Float getBounds() {
        return bounds;
    }

    public Float getBoundsLower() {
        Float lower = null;
        if ((bounds != null) && (chemShift != null)) {
            float bValue = bounds.floatValue();
            float csValue = chemShift.floatValue();
            float lValue = csValue - bValue / 2;
            lower = new Float(lValue);
        }
        return lower;
    }

    public Float getBoundsUpper() {
        Float upper = null;
        if ((bounds != null) && (chemShift != null)) {
            float bValue = bounds.floatValue();
            float csValue = chemShift.floatValue();
            float uValue = csValue + bValue / 2;
            upper = new Float(uValue);
        }
        return upper;
    }

    public float getBoundsValue() {
        float value = 0.0f;

        if (bounds != null) {
            return bounds.floatValue();
        } else {
            return value;
        }
    }

    public Float getBoundsError() {
        return boundsError;
    }

    public void setBoundsValue(float lower, float upper, float cShift) {
        float dUpper = Math.abs(upper - cShift);
        float dLower = Math.abs(lower - cShift);
        float bValue = 2.0f * (dUpper > dLower ? dLower : dUpper);
        this.bounds = new Float(bValue);
        peakDimUpdated();
    }

    public void setBoundsValue(float bou) {
        this.bounds = new Float(bou);
        peakDimUpdated();
    }

    public void setBoundsErrorValue(float value) {
        this.boundsError = new Float(value);
        peakDimUpdated();
    }

    public double getDeltaHz(double delta) {
        delta /= getSpectralDimObj().getSf();
        return delta;
    }

    public Float getPhase() {
        return phase;
    }

    public float getPhaseValue() {
        return phase.floatValue();
    }

    public Float getPhaseError() {
        return phaseError;
    }

    public void setPhaseValue(float decayRate) {
        this.decayRate = new Float(decayRate);
        peakDimUpdated();
    }

    public void setPhaseErrorValue(float value) {
        this.phaseError = new Float(value);
        peakDimUpdated();
    }

    public Float getDecayRate() {
        return decayRate;
    }

    public float getDecayRateValue() {
        float value = 0.0f;

        if (lineWidth != null) {
            return decayRate.floatValue();
        } else {
            return value;
        }
    }

    public Float getDecayRateError() {
        return phaseError;
    }

    public void setDecayRateValue(float decayRate) {
        this.decayRate = new Float(decayRate);
        peakDimUpdated();
    }

    public void setDecayRateErrorValue(float value) {
        this.decayRateError = new Float(value);
        peakDimUpdated();
    }


    /*
     public void updateCouplings() {
     if (!myPeak.getFlag(5)) {
     Peak origPeak = getOrigin();

     if (origPeak != null) {
     adjustCouplings(origPeak);
     }
     }
     }
     */
 /*
     * 
     double[] fo = origPeak.peakDim[0].getFrequencyOffsets();
     Arrays.sort(fo);
     FreqIntensities fiValues = origPeak.peakDim[0].getFreqIntensitiesFromSplittings();
     Arrays.sort(fiValues.freqs);
        
     double sf = myPeak.peakList.getSpectralDim(getSpectralDim()).getSf();

     int nExtra = fiValues.freqs.length - fo.length;
     // System.out.println(fiValues.freqs.length+" "+fo.length);
     if (nExtra < 0) {
     //  System.out.println("adjust couplings, nExtra negative");

     return;
     } else if (nExtra > 0) {
     double[] amplitudeJunk = new double[fiValues.freqs.length];
     PeakList.trimFreqs(fiValues.freqs, amplitudeJunk, nExtra);
     }

     double delta = (fiValues.freqs[iPos] * sf) - fo[iPos];


     int iCoupling = 0;
     double sign = 1.0;

     if (iPos < (fo.length - iPos - 1)) {
     iCoupling = iPos;
     sign = -1;
     } else {
     iCoupling = (fo.length - iPos - 1);
     sign = 1;
     }


     // System.out.println("adjust Couplings "+iPos+" "+iCoupling+" "+sign+" "+delta);
 
 
     * 
     */
    public int getThread() {
        // FIXME
        return 0;
    }

    public void setThread(int thread) {
        // FIXME
        //this.thread = thread;
        peakDimUpdated();
    }

    public String getLabel() {
        return resonance.getName();
    }

    public String getAtomLabel() {
        return resonance.getAtomName();
    }

    public void setLabel(String label) {
        List<String> labelArgs = new ArrayList<>();
        labelArgs.add(label);
        setLabel(labelArgs);
    }

    public void setLabel(List<String> labelArgs) {
        resonance.setName(labelArgs);
        peakDimUpdated();
    }

    public char[] getError() {
        return error.clone();
    }

    public void setError(char[] error) {
        this.error = error.clone();
        peakDimUpdated();
    }

    public void setError(String error) {
        this.error[0] = error.charAt(0);
        this.error[1] = error.charAt(1);
        peakDimUpdated();
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
        peakDimUpdated();
    }

    public Peak getPeak() {
        return myPeak;
    }

    public boolean isLinked() {
        return (getLinkedPeakDims().size() > 2);
    }

    public boolean isCoupled() {
        if (multiplet == null) {
            return false;
        } else {
            return (getCoupledPeakDims().size() > 1);
        }
    }

    class FreqIntensities {

        double[] freqs = null;
        double[] intensities = null;
    }

    public int getSpectralDim() {
        return spectralDim;
    }

    public SpectralDim getSpectralDimObj() {
        return myPeak.peakList.getSpectralDim(spectralDim);
    }

    public void setSpectralDim(int spectralDim) {
        this.spectralDim = spectralDim;
    }

    public void setAttribute(String name, String value) {
        if (name.equals("Chem_shift_val")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setChemShiftValueNoCheck(fvalue);
        } else if (name.equals("Detail")) {
            setUser(value);
        } else if (name.equals("Peak_err")) {
            setError(value);
        } else if (name.equals("Coupling_detail")) {
            // fixme getMultiplet().setCouplingValues(value);
        } else if (name.equals("Bounding_box_val")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setBoundsValue(fvalue);
        } else if (name.equals("Line_width_val")) {
            float fvalue = ConvUtil.getFloatValue(value);
            SpectralDim sDim = getPeak().peakList.getSpectralDim(spectralDim);
            float lwPPM = (float) (fvalue / (sDim.getSf()));
            setLineWidthValue(lwPPM);
        } else if (name.equals("Bounding_box_val_err")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setBoundsErrorValue(fvalue);
        } else if (name.equals("Chem_shift_val_err")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setChemShiftErrorValue(fvalue);
        } else if (name.equals("Line_width_val_err")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setLineWidthErrorValue(fvalue);
        } else if (name.equals("Phase_val")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setPhaseValue(fvalue);
        } else if (name.equals("Phase_val_err")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setPhaseErrorValue(fvalue);
        } else if (name.equals("Decay_rate_val")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setDecayRateValue(fvalue);
        } else if (name.equals("Decay_rate_val_err")) {
            float fvalue = ConvUtil.getFloatValue(value);
            setDecayRateErrorValue(fvalue);
            // fixme unused } else if (name.equals("Derivation_method")) {
        }
    }

    void freezeDims() {
        List<PeakDim> links = getLinkedPeakDims();
        String condition = myPeak.peakList.getSampleConditionLabel();
        for (PeakDim peakDim : links) {
            if ((peakDim != this) && peakDim.myPeak.peakList.getSampleConditionLabel().equals(condition)) {
                // use field so we don't fire recursive freezeDims
                peakDim.frozen = frozen;
                peakDim.myPeak.updateFrozenColor();
                peakDimUpdated();
            }
        }

    }

    void slideDims() {
        List<PeakDim> links = getLinkedPeakDims();
        String condition = myPeak.peakList.getSampleConditionLabel();
        for (PeakDim peakDim : links) {
            if ((peakDim != this) && !peakDim.frozen) {
                if (peakDim.myPeak.peakList.getSampleConditionLabel().equals(condition)) {
                    // use field so we don't fire recursive slideDims
                    peakDim.chemShift = chemShift;
                    peakDim.peakDimUpdated();
                }
            }
        }
    }
}
