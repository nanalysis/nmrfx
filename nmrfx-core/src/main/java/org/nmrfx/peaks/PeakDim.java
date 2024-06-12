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
package org.nmrfx.peaks;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.STAR3;
import org.nmrfx.utilities.ConvUtil;
import org.nmrfx.utilities.Format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PeakDim {
    private int spectralDim = 0;
    private Float chemShift = null;
    private Float chemShiftError = null;
    private Float lineWidth = null;
    private Float lineWidthError = null;
    private Float shapeFactor = null;
    private Float shapeFactorError = null;
    private Float bounds = null;
    private Float boundsError = null;
    private Float phase = null;
    private Float phaseError = null;
    private Float decayRate = null;
    private Float decayRateError = null;
    private Multiplet multiplet = null;
    private char[] error = {'+', '+'};
    private String user = "";
    private Peak myPeak = null;
    private AtomResonance resonance;
    private boolean frozen = false;
    private boolean linksDrawn = false;  // used in drawing link lines

    public void peakDimUpdated() {
        if (myPeak != null) {
            myPeak.peakUpdated(this);
        }
    }

    public PeakDim(Peak peak, int iDim) {
        myPeak = peak;
        setSpectralDim(iDim);
    }
    @Override
    public String toString() {
        return myPeak.getName() + "." + spectralDim;
    }

    public PeakDim copy(Peak peak) {
        PeakDim newPeakDim = new PeakDim(peak, spectralDim);
        newPeakDim.chemShift = chemShift;
        newPeakDim.chemShiftError = chemShiftError;
        newPeakDim.lineWidth = lineWidth;
        newPeakDim.lineWidthError = lineWidthError;
        newPeakDim.shapeFactor = shapeFactor;
        newPeakDim.shapeFactorError = shapeFactorError;
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

    public void copyTo(PeakDim targetPeakDim) {
        targetPeakDim.chemShift = chemShift;
        targetPeakDim.chemShiftError = chemShiftError;
        targetPeakDim.lineWidth = lineWidth;
        targetPeakDim.lineWidthError = lineWidthError;
        targetPeakDim.shapeFactor = shapeFactor;
        targetPeakDim.shapeFactorError = shapeFactorError;
        targetPeakDim.bounds = bounds;
        targetPeakDim.boundsError = boundsError;
        targetPeakDim.phase = phase;
        targetPeakDim.phaseError = phaseError;
        targetPeakDim.decayRate = decayRate;
        targetPeakDim.decayRateError = decayRateError;
        targetPeakDim.error = error.clone();
        targetPeakDim.user = user;
        if (multiplet != null) {
            Multiplet newMultiplet = new Multiplet(targetPeakDim);
            multiplet.copyTo(newMultiplet);
            newMultiplet.myPeakDim = targetPeakDim;
            targetPeakDim.multiplet = newMultiplet;
        }
        targetPeakDim.resonance = resonance.copy();
    }

    public void restoreFrom(PeakDim peakDim) {
        chemShift = peakDim.chemShift;
        chemShiftError = peakDim.chemShiftError;
        lineWidth = peakDim.lineWidth;
        lineWidthError = peakDim.lineWidthError;
        shapeFactor = peakDim.shapeFactor;
        shapeFactorError = peakDim.shapeFactorError;
        bounds = peakDim.bounds;
        boundsError = peakDim.boundsError;
        phase = peakDim.phase;
        phaseError = peakDim.phaseError;
        decayRate = peakDim.decayRate;
        decayRateError = peakDim.decayRateError;
        error = peakDim.error.clone();
        user = peakDim.user;
    }

    public Multiplet getMultiplet() {
        if (multiplet == null) {
            multiplet = new Multiplet(this);
        }
        return multiplet;
    }

    public void copyLabels(PeakDim newPeakDim) {
        AtomResonance resOld = getResonance();
        AtomResonance resNew = newPeakDim.getResonance();
        resNew.setName(resOld.getName());
    }

    public String getDimName() {
        return myPeak.peakList.getSpectralDim(getSpectralDim()).getDimName();
    }

    public String getName() {
        return myPeak.getName() + "." + getDimName();
    }

    public void initResonance() {
        ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
        resonance = resFactory.build();
        resonance.add(this);
    }

    public AtomResonance getResonance() {
        return resonance;
    }

    public String getResonanceIDsAsString() {
        return resonance.getIDString();
    }

    public List<PeakDim> getLinkedPeakDims() {
        if (resonance == null) {
            // fixme should this contain this peakdim (and in general should result contain this dim plus linked)
            return Collections.emptyList();
        } else {
            return resonance.getPeakDims();
        }
    }

    public double getAverageShift() {
        double shift = getLinkedPeakDims().stream().mapToDouble(p -> p.getChemShiftValue()).average().getAsDouble();
        return shift;
    }

    public String getSummary() {
        double normVal = 0;
        // FIXME  make precision in ctr a function of dig resolution  sw/sfrq/size
        if (myPeak.peakList.scale > 0.0) {
            normVal = myPeak.getVolume1() / myPeak.peakList.scale;
        }

        return Format.format3(getChemShiftValue()) + " " + Format.format2(normVal);
    }

    public boolean hasMultiplet() {
        return multiplet != null;
    }

    public void unLink() {
        resonance.remove(this);
        var oldNames = resonance.getNames();
        Atom atom = resonance.getAtom();
        initResonance();
        resonance.setName(oldNames);
        resonance.setAtom(atom);
        if (multiplet != null) {
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
        ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
        resonance = resFactory.get(resID);
    }

    public void setResonance(AtomResonance newResonance) {
        resonance = newResonance;
    }

    public String toSTAR3LoopAssignedPeakChemShiftString(int iContrib, long resID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(getPeak().getIdNum()).append(sep);
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
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(getPeak().getIdNum()).append(sep);
        result.append(contributionID);
        result.append(sep);
        result.append((spectralDim + 1));
        result.append(sep);
        result.append(STAR3.valueOf(getChemShift())).append(sep);
        result.append(STAR3.valueOf(getChemShiftError())).append(sep);
        result.append(STAR3.valueOf(getBounds())).append(sep);
        result.append(STAR3.valueOf(getBoundsError())).append(sep);
        SpectralDim sDim = getPeak().peakList.getSpectralDim(spectralDim);
        Float lw = getLineWidth();
        if (lw == null) {
            result.append(".");
        } else {
            float lwf = (float) (sDim.getSf() * lw);
            result.append(lwf);
        }
        result.append(sep);
        result.append(STAR3.valueOf(getLineWidthError())).append(sep);
        Float shapeFactor = getShapeFactor();
        if (shapeFactor == null) {
            result.append(".");
        } else {
            result.append(shapeFactor);
        }
        result.append(sep);
        result.append(STAR3.valueOf(getShapeFactorError())).append(sep);
        result.append(STAR3.valueOf(getPhase())).append(sep);
        result.append(STAR3.valueOf(getPhaseError())).append(sep);
        result.append(STAR3.valueOf(getDecayRate())).append(sep);
        result.append(STAR3.valueOf(getDecayRateError())).append(sep);
        result.append(".").append(sep); // fixme derivation method
        result.append(getError()[0]).append("").append(getError()[1]);
        result.append(sep);
        result.append(STAR3.quote(getUser()));// fixme only quote if more than one
        result.append(sep);
        if (multiplet != null) {
            Coupling coupling = multiplet.getCoupling();
            if (coupling instanceof Singlet) {
                result.append("s");
            } else if (coupling instanceof ComplexCoupling) {
                result.append("m");
            } else {
                result.append(STAR3.quote(multiplet.getCouplingsAsString())); // fixme only quote if more than one
            }
        } else {
            result.append(".");
        }
        result.append(sep);
        result.append(isFrozen() ? "1" : "0");

        return result.toString();
    }

    public String toSTAR3LoopSpectralTransitionCharString(AbsMultipletComponent comp, int specTransID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        double sf = getSpectralDimObj().getSf();
        result.append(specTransID).append(sep);
        result.append(getPeak().getIdNum()).append(sep);
        result.append((spectralDim + 1)).append(sep);
        result.append(comp.offset).append(sep).append(".").append(sep);
        result.append(".").append(sep).append(".").append(sep);
        result.append(comp.lineWidth * sf).append(sep).append(".").append(sep);
        result.append(".").append(sep).append(".").append(sep);
        result.append(".").append(sep).append(".").append(sep);
        result.append(".");
        return result.toString();
    }

    public String toSTAR3LoopSpectralTransitionGeneralCharString(AbsMultipletComponent comp, int specTransID, boolean intensityMode) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(specTransID).append(sep);
        result.append(getPeak().getIdNum()).append(sep);
        if (intensityMode) {
            result.append(comp.intensity).append(sep).append(".").append(sep);
            result.append("height");
        } else {
            result.append(comp.volume).append(sep).append(".").append(sep);
            result.append("volume");

        }
        return result.toString();
    }

    public List<String> toSTAR3ComplexCouplingString(int index) {
        List<String> values = new ArrayList<>();
        Multiplet multiplet = getMultiplet();
        if (multiplet != null) {
            Coupling coupling = multiplet.getCoupling();
            if ((coupling != null) && (coupling instanceof ComplexCoupling)) {
                ComplexCoupling complexCoupling = (ComplexCoupling) coupling;
                int nComps = coupling.getRelComponentList().size();
                for (int iComp = 0; iComp < nComps; iComp++) {
                    String s = toSTAR3LoopMultipletCharString(spectralDim, complexCoupling, iComp, index);
                    values.add(s);
                    index++;
                }
            }

        }
        return values;
    }

    public List<String> toSTAR3CouplingPatternString(int index) {
        List<String> values = new ArrayList<>();
        Multiplet multiplet = getMultiplet();
        if (multiplet != null) {
            Coupling coupling = multiplet.getCoupling();
            if ((coupling != null) && (coupling instanceof CouplingPattern)) {
                CouplingPattern couplingPattern = (CouplingPattern) coupling;
                int nComps = couplingPattern.getNCouplingValues();
                for (int iComp = 0; iComp < nComps; iComp++) {
                    String s = toSTAR3LoopMultipletCharString(spectralDim, couplingPattern, iComp, index);
                    values.add(s);
                    index++;
                }
            }

        }
        return values;
    }

    public String toSTAR3LoopMultipletCharString(int contributionID, ComplexCoupling coupling, int iComp, int index) {
        RelMultipletComponent comp = coupling.getRelComponentList().get(iComp);
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(index).append(sep);
        result.append(getPeak().getIdNum()).append(sep);
        result.append((spectralDim + 1)).append(sep);
        result.append((iComp + 1)).append(sep);
        result.append(STAR3.valueOf(comp.getOffset())).append(sep);
        result.append(".").append(sep);
        result.append(STAR3.valueOf(comp.getIntensity())).append(sep);
        result.append(".").append(sep);
        result.append(STAR3.valueOf(comp.getVolume())).append(sep);
        result.append(".").append(sep);
        result.append(STAR3.valueOf(comp.getLineWidth())).append(sep);
        result.append(".");

        return result.toString();
    }

    public String toSTAR3LoopMultipletCharString(int contributionID, CouplingPattern coupling, int iComp, int index) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(index).append(sep);
        result.append(getPeak().getIdNum()).append(sep);
        result.append((spectralDim + 1)).append(sep);
        result.append((iComp + 1)).append(sep);
        result.append(CouplingPattern.toCouplingChar(coupling.getNValue(iComp))).append(sep);
        result.append(STAR3.valueOf(coupling.getValueAt(iComp))).append(sep);
        result.append(".").append(sep);
        result.append(STAR3.valueOf(coupling.getSin2Theta(iComp))).append(sep);
        result.append(".").append(sep);
        result.append(STAR3.valueOf(coupling.getIntensity())).append(sep);
        result.append(".").append(sep);
        result.append(".");
        return result.toString();
    }

    public String toNEFString(int contributionID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(STAR3.valueOf(getChemShift())).append(sep);
        result.append(STAR3.valueOf(getChemShiftError())).append(sep);
        return result.toString();
    }

    public void setFrozen(boolean state) {
        setFrozen(state, false);
    }

    public void setFrozen(boolean state, boolean allConditions) {
        frozen = state;
        if (myPeak.peakList.isSlideable()) {
            myPeak.updateFrozenColor();
            freezeDims(allConditions);
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
            value = chemShift;
        }
        return value;
    }

    public Float getChemShift() {
        return chemShift;
    }

    public float getChemShiftValue() {
        float value = 0.0f;
        if (chemShift != null) {
            value = chemShift;
        }
        return value;
    }

    public Float getChemShiftError() {
        return chemShiftError;
    }

    public void setChemShiftValueNoCheck(float ctr) {
        this.chemShift = ctr;
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
        this.chemShift = ctr;
        if (myPeak.peakList.isSlideable() && !frozen) {
            slideDims();
        }

        if (myPeak.getFlag(5)) {
            //fixme setMultipletComponentValues();
        }
        peakDimUpdated();
    }

    public void setChemShiftErrorValue(float value) {
        this.chemShiftError = value;
        peakDimUpdated();
    }

    public Float getLineWidth() {
        return lineWidth;
    }

    public float getLineWidthValue() {
        float value = 0.0f;

        if (lineWidth != null) {
            return lineWidth;
        } else {
            return value;
        }
    }

    public Float getLineWidthError() {
        return lineWidthError;
    }

    public void setLineWidthValue(float wid) {
        this.lineWidth = wid;
        peakDimUpdated();
    }

    public void setLineWidthHz(float wid) {
        wid /= getSpectralDimObj().getSf();
        this.lineWidth = wid;
        peakDimUpdated();
    }

    public void setLineWidthErrorValue(float wid) {
        this.lineWidthError = wid;
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
        this.bounds = bounds;
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
            float bValue = bounds;
            float csValue = chemShift;
            float lValue = csValue - bValue / 2;
            lower = lValue;
        }
        return lower;
    }

    public Float getBoundsUpper() {
        Float upper = null;
        if ((bounds != null) && (chemShift != null)) {
            float bValue = bounds;
            float csValue = chemShift;
            float uValue = csValue + bValue / 2;
            upper = uValue;
        }
        return upper;
    }

    public float getBoundsValue() {
        float value = 0.0f;

        if (bounds != null) {
            return bounds;
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
        this.bounds = bValue;
        peakDimUpdated();
    }

    public void setBoundsValue(float bou) {
        this.bounds = bou;
        peakDimUpdated();
    }

    public void setBoundsErrorValue(float value) {
        this.boundsError = value;
        peakDimUpdated();
    }

    public Float getShapeFactor() {
        return shapeFactor;
    }

    public float getShapeFactorValue() {
        float value = 0.0f;

        if (shapeFactor != null) {
            return shapeFactor;
        } else {
            return value;
        }
    }

    public Float getShapeFactorError() {
        return shapeFactorError;
    }

    public void setShapeFactorValue(float wid) {
        this.shapeFactor = wid;
        peakDimUpdated();
    }

    public void setShapeFactorErrorValue(float value) {
        this.shapeFactorError = value;
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
        return phase;
    }

    public Float getPhaseError() {
        return phaseError;
    }

    public void setPhaseValue(float decayRate) {
        this.decayRate = decayRate;
        peakDimUpdated();
    }

    public void setPhaseErrorValue(float value) {
        this.phaseError = value;
        peakDimUpdated();
    }

    public Float getDecayRate() {
        return decayRate;
    }

    public float getDecayRateValue() {
        float value = 0.0f;

        if (lineWidth != null) {
            return decayRate;
        } else {
            return value;
        }
    }

    public Float getDecayRateError() {
        return phaseError;
    }

    public void setDecayRateValue(float decayRate) {
        this.decayRate = decayRate;
        peakDimUpdated();
    }

    public void setDecayRateErrorValue(float value) {
        this.decayRateError = value;
        peakDimUpdated();
    }

    public int getThread() {
        // FIXME
        return 0;
    }

    public void setThread(int thread) {
        // FIXME
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

    public boolean isLabelValid() {
        return resonance.isLabelValid();
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

    public void setLinkDrawn(boolean state) {
        linksDrawn = state;
    }

    public boolean isLinkDrawn() {
        return linksDrawn;
    }

    public Peak getPeak() {
        return myPeak;
    }

    public PeakList getPeakList() {
        return myPeak.peakList;
    }

    public String getSampleConditionLabel() {
        return myPeak.peakList.getSampleConditionLabel();
    }

    public String getSampleLabel() {
        return myPeak.peakList.getSampleLabel();
    }

    public boolean isLinked() {
        return (getLinkedPeakDims().size() > 2);
    }

    public boolean isCoupled() {
        if (multiplet == null) {
            return false;
        } else {
            return (multiplet.isCoupled());
        }
    }

    public int getSpectralDim() {
        return spectralDim;
    }

    public SpectralDim getSpectralDimObj() {
        return myPeak.peakList.getSpectralDim(spectralDim);
    }

    private void setSpectralDim(int spectralDim) {
        this.spectralDim = spectralDim;
    }

    public void setAttribute(String name, String value) {
        switch (name) {
            case "Chem_shift_val": {
                float fvalue = ConvUtil.getFloatValue(value);
                setChemShiftValueNoCheck(fvalue);
                break;
            }
            case "Detail":
                setUser(value);
                break;
            case "Peak_err":
                setError(value);
                break;
            // fixme getMultiplet().setCouplingValues(value);
            case "Coupling_detail":
                break;
            case "Bounding_box_val": {
                float fvalue = ConvUtil.getFloatValue(value);
                setBoundsValue(fvalue);
                break;
            }
            case "Line_width_val": {
                float fvalue = ConvUtil.getFloatValue(value);
                SpectralDim sDim = getPeak().peakList.getSpectralDim(spectralDim);
                float lwPPM = (float) (fvalue / (sDim.getSf()));
                setLineWidthValue(lwPPM);
                break;
            }
            case "Bounding_box_val_err": {
                float fvalue = ConvUtil.getFloatValue(value);
                setBoundsErrorValue(fvalue);
                break;
            }
            case "Chem_shift_val_err": {
                float fvalue = ConvUtil.getFloatValue(value);
                setChemShiftErrorValue(fvalue);
                break;
            }
            case "Line_width_val_err": {
                float fvalue = ConvUtil.getFloatValue(value);
                setLineWidthErrorValue(fvalue);
                break;
            }
            case "Shape_factor_val": {
                float fvalue = ConvUtil.getFloatValue(value);
                setShapeFactorValue(fvalue);
                break;
            }
            case "Shape_factor_val_err": {
                float fvalue = ConvUtil.getFloatValue(value);
                setShapeFactorErrorValue(fvalue);
                break;
            }
            case "Phase_val": {
                float fvalue = ConvUtil.getFloatValue(value);
                setPhaseValue(fvalue);
                break;
            }
            case "Phase_val_err": {
                float fvalue = ConvUtil.getFloatValue(value);
                setPhaseErrorValue(fvalue);
                break;
            }
            case "Decay_rate_val": {
                float fvalue = ConvUtil.getFloatValue(value);
                setDecayRateValue(fvalue);
                break;
            }
            case "Decay_rate_val_err": {
                float fvalue = ConvUtil.getFloatValue(value);
                setDecayRateErrorValue(fvalue);
                break;
            }
            case "Frozen":
                frozen = value.equals("1");
                // fixme unused } else if (name.equals("Derivation_method")) {
                break;
            default:
                break;
        }
    }

    void freezeDims(boolean useAllConditions) {
        List<PeakDim> links = getLinkedPeakDims();
        String condition = myPeak.peakList.getSampleConditionLabel();
        String sample = myPeak.peakList.getSampleLabel();
        for (PeakDim peakDim : links) {
            if ((peakDim != this) && (useAllConditions || (peakDim.myPeak.peakList.getSampleConditionLabel().equals(condition)
                    && peakDim.myPeak.peakList.getSampleLabel().equals(sample)))) {
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
                if (!peakDim.myPeak.peakList.requireSliderCondition() || peakDim.myPeak.peakList.getSampleConditionLabel().equals(condition)) {
                    // use field so we don't fire recursive slideDims
                    peakDim.chemShift = chemShift;
                    peakDim.peakDimUpdated();
                }
            }
        }
    }
}
