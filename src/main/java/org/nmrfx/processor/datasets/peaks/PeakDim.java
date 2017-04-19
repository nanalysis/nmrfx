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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class PeakDim {

    public static ResonanceFactory resFactory = new ResonanceFactory();

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
    private PeakDimContrib pdC = null;
    private ArrayList peakDimContribs = null;

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
        Resonance resOld = pdC.getResonance();
        Resonance resNew = newPeakDim.pdC.getResonance();
        resNew.setName(resOld.getName());
    }

    public void initPeakDimContribs() {
        if (pdC == null) {
            pdC = new PeakDimContrib(this);
        }
        if ((peakDimContribs != null) && (peakDimContribs.size() == 0)) {
            PeakDimContrib peakDimContrib = new PeakDimContrib(this);
            peakDimContribs.add(peakDimContrib);
        }
        multiplet = new Multiplet(this);
    }

    class ResonanceIterator implements Iterator {

        PeakDimContrib pdC;

        public ResonanceIterator(PeakDimContrib pdC) {
            this.pdC = pdC;
        }

        public boolean hasNext() {
            boolean hasNext = false;
            if (pdC != null) {
                hasNext = true;
            }
            return hasNext;
        }

        public PeakDimContrib next() {
            PeakDimContrib result = pdC;
            pdC = null;
            return result;
        }

        public void remove() {
            pdC = null;
        }
    }

    public Iterator getIterator() {
        if (peakDimContribs != null) {
            return peakDimContribs.iterator();
        } else {
            ResonanceIterator ri = new ResonanceIterator(pdC);
            return ri;
        }
    }

    public String getDimName() {
        return myPeak.peakList.getSpectralDim(getSpectralDim()).getDimName();
    }

    public String getName() {
        return myPeak.getName() + "." + getDimName();
    }

    public void addResonance(long resID) {
        boolean alreadyHasResonance = false;
        Iterator iter = getIterator();
        while (iter.hasNext()) {
            PeakDimContrib pdc = (PeakDimContrib) iter.next();
            Resonance resonance = pdc.getResonance();
            if (resonance.getID() == resID) {
                alreadyHasResonance = true;
                break;
            }
        }

        if (!alreadyHasResonance) {
            Resonance resonance = resFactory.get(resID);
            if (resonance == null) {
                resonance = resFactory.build();
            }
            PeakDimContrib peakDimContrib = new PeakDimContrib(this, resonance);
            addPeakDimContrib(peakDimContrib);
        }
    }

    void addPeakDimContrib(PeakDimContrib peakDimContrib) {
        if (peakDimContribs == null) {
            if (pdC == null) {
                pdC = peakDimContrib;
            } else {
                peakDimContribs = new ArrayList();
                peakDimContribs.add(pdC);
                pdC = null;
                if (!peakDimContribs.contains(peakDimContrib)) {
                    peakDimContribs.add(peakDimContrib);
                }
            }
        } else if (!peakDimContribs.contains(peakDimContrib)) {
            peakDimContribs.add(peakDimContrib);
        }
    }

    /*
     public ArrayList getPeakDimContribs() {
     return peakDimContribs;
     }*/
    public ArrayList getResonances() {
        ArrayList resonances = new ArrayList();
        Iterator iter = getIterator();
        while (iter.hasNext()) {
            PeakDimContrib pdc = (PeakDimContrib) iter.next();
            resonances.add(pdc.getResonance());
        }
        return (resonances);
    }

    public List<String> getResonanceNames() {
        ArrayList resonances = getResonances();
        List<String> list = new ArrayList<>();
        Iterator iter = resonances.iterator();
        while (iter.hasNext()) {
            Resonance resonance = (Resonance) iter.next();
            list.add(resonance.getName());
        }
        return list;
    }

    public String getResonanceIDsAsString() {
        ArrayList resonances = getResonances();
        Iterator iter = resonances.iterator();
        StringBuffer sBuf = new StringBuffer();
        int i = 0;
        while (iter.hasNext()) {
            Resonance resonance = (Resonance) iter.next();
            if (i > 0) {
                sBuf.append(" ");
            }
            sBuf.append(resonance.getIDString());
            i++;
        }
        return sBuf.toString();
    }

    public List<String> getResonanceIDs() {
        ArrayList resonances = getResonances();
        List<String> list = new ArrayList<>();
        Iterator iter = resonances.iterator();
        while (iter.hasNext()) {
            Resonance resonance = (Resonance) iter.next();
            if (resonance != null) {
                list.add(resonance.getIDString());
            }
        }
        return list;
    }

    public ArrayList<PeakDim> getLinkedPeakDims() {
        ArrayList resonances = getResonances();
        ArrayList<PeakDim> peakDims = new ArrayList<PeakDim>();
        Iterator iter = resonances.iterator();
        while (iter.hasNext()) {
            Resonance resonance = (Resonance) iter.next();
            Iterator iter2 = resonance.getIterator();
            while (iter2.hasNext()) {
                PeakDimContrib peakDimContrib = (PeakDimContrib) iter2.next();
                peakDims.add(peakDimContrib.getPeakDim());
            }

        }
        return peakDims;
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
            return new HashSet<PeakDim>();
        } else {
            return multiplet.getPeakDims();
        }
    }

    public Multiplet getMultiplet() {
        /*
         Multiplet aMultiplet = multiplet;
         if (aMultiplet == null) {
         ArrayList<PeakDim> peakDims = getLinkedPeakDims();
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
         */
        if (multiplet == null) {
            multiplet = new Multiplet(this);
        }
        return multiplet;
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
        Iterator iter = getIterator();
        while (iter.hasNext()) {
            PeakDimContrib pdc = (PeakDimContrib) iter.next();
            Resonance resonance = pdc.getResonance();
            String name = resonance.getName();
            resonance.removePeakDimContrib(pdc);
            Resonance newResonance = resFactory.build(pdc);
            newResonance.setName(name);
            pdc.setResonance(newResonance);
        }
        if (multiplet != null) {
            multiplet.removePeakDim(this);
        }
        multiplet = new Multiplet(this);

        peakDimUpdated();
    }

    public void remove() {
        Iterator iter = getIterator();
        while (iter.hasNext()) {
            PeakDimContrib pdc = (PeakDimContrib) iter.next();
            Resonance resonance = pdc.getResonance();
            resonance.removePeakDimContrib(pdc);
        }
    }

    public void setResonance(long resID) {

        Iterator iter = getIterator();
        while (iter.hasNext()) {
            PeakDimContrib pdc = (PeakDimContrib) iter.next();
            Resonance resonance = pdc.getResonance();
            resonance.removePeakDimContrib(pdc);
        }

        //  peakDimContribs.clear();
        addResonance(resID);
        peakDimUpdated();
    }

    String toSTAR3LoopAssignedPeakChemShiftString(int iContrib, long resID) {
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

    String toSTAR3LoopPeakCharString(int contributionID) {
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
        peakDimUpdated();
    }

    public void setChemShiftValue(float ctr) {
        this.chemShift = new Float(ctr);

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

    public void setLineWidthErrorValue(float wid) {
        this.lineWidthError = new Float(wid);
        peakDimUpdated();
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
        ArrayList resonances = getResonances();
        Iterator iter = resonances.iterator();
        StringBuffer sBuf = new StringBuffer();
        int i = 0;
        while (iter.hasNext()) {
            Resonance resonance = (Resonance) iter.next();
            if (i > 0) {
                sBuf.append(" ");
            }
            if (resonance != null) {
                sBuf.append(resonance.getName());
            }
            i++;
        }
        return sBuf.toString();
    }

    public String getAtomLabel() {
        ArrayList resonances = getResonances();
        Iterator iter = resonances.iterator();
        StringBuilder sBuf = new StringBuilder();
        int i = 0;
        while (iter.hasNext()) {
            Resonance resonance = (Resonance) iter.next();
            if (i > 0) {
                sBuf.append(" ");
            }
            sBuf.append(resonance.getAtomName());
            i++;
        }
        return sBuf.toString();
    }

    public void setLabel(List<String> labelArgs) {
        ArrayList resonances = getResonances();
        if (labelArgs.size() == 0) {
            labelArgs.add("");
        }
        Iterator iter = getIterator();
        if (labelArgs.size() < resonances.size()) {
            if (labelArgs.size() == 0) {
                labelArgs.add("");
            }
            boolean[] used = new boolean[labelArgs.size()];
            while (iter.hasNext()) {
                PeakDimContrib peakDimContrib = (PeakDimContrib) iter.next();
                boolean matched = false;
                for (int j = 0; j < labelArgs.size(); j++) {
                    if (!used[j] && peakDimContrib.getResonance().getName().equals(labelArgs.get(j).toString())) {
                        matched = true;
                        used[j] = true;
                        break;
                    }
                }
                if (!matched) {
                    peakDimContrib.remove();
                    iter.remove();
                }
            }
        }

        resonances = getResonances();
        if (labelArgs.size() >= resonances.size()) {
            for (int i = 0; i < resonances.size(); i++) {
                Resonance resonance = (Resonance) resonances.get(i);
                resonance.setName(labelArgs.get(i).toString());
            }
            for (int i = resonances.size(); i < labelArgs.size(); i++) {
                PeakDimContrib peakDimContrib = new PeakDimContrib(this);
                addPeakDimContrib(peakDimContrib);
                Resonance resonance = peakDimContrib.getResonance();
                resonance.setName(labelArgs.get(i).toString());
            }
        }
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
}
