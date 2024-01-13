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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.peaks;

import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.utilities.Format;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author brucejohnson
 */
public class Multiplet implements PeakOrMulti, Comparable {

    private double intensity = 0.0;
    private double max = 0.0;
    private Coupling coupling = new Singlet(this);
    PeakDim myPeakDim;

    @Override
    public int compareTo(Object o) {
        int result = 1;
        double center = getCenter();
        if (o instanceof Multiplet) {
            Multiplet multiplet2 = (Multiplet) o;
            if (center < multiplet2.getCenter()) {
                result = 1;
            } else if (center > multiplet2.getCenter()) {
                result = -1;
            } else {
                result = 0;
            }
        }
        return result;
    }

    public Multiplet(PeakDim peakDim) {
        myPeakDim = peakDim;
        max = peakDim.getPeak().getIntensity();
    }

    public void copyTo(Multiplet multiplet) {
        multiplet.intensity = intensity;
        multiplet.max = max;
        multiplet.myPeakDim = myPeakDim;
        if (coupling != null) {
            multiplet.coupling = coupling.copy(multiplet);
        }
    }

    public void restoreFrom(Multiplet multiplet) {
        intensity = multiplet.intensity;
        max = multiplet.max;
        myPeakDim = multiplet.myPeakDim;
        coupling = multiplet.coupling.copy(this);
    }

        @Override
    public int getStatus() {
        int status = -1;
        if (myPeakDim != null) {
            status = myPeakDim.getPeak().getStatus();
        }
        return status;
    }

    @Override
    public boolean isValid() {
        boolean valid = false;
        if (myPeakDim != null) {
            valid = myPeakDim.getPeak().isValid();
        }
        return valid;
    }

    public int getIDNum() {
        return myPeakDim.getPeak().getIdNum();
    }

    @Override
    public PeakList getPeakList() {
        PeakList peakList = null;
        if (myPeakDim != null) {
            peakList = myPeakDim.getPeak().peakList;
        }
        return peakList;

    }

    public double getCenter() {
        return myPeakDim.getChemShiftValue();
    }

    public void set(double centerPPM, double[] deltaPPMs, double[] amplitudes, double[] volumes, double lineWidthPPM) {

        myPeakDim.setChemShiftValue((float) centerPPM);
        coupling = new ComplexCoupling(this, deltaPPMs, amplitudes, volumes, lineWidthPPM);

        max = getMultipletMax();
    }

    public void set(double centerPPM, double[] couplingValues, double amplitude, double[] sin2thetas) {
        if (coupling instanceof Singlet) {
            getOrigin().setIntensity((float) amplitude);
        } else {
            CouplingPattern cPat = (CouplingPattern) coupling;
            int[] nValues = cPat.getNValues();
            coupling = new CouplingPattern(this, couplingValues, nValues, amplitude, sin2thetas);
            intensity = amplitude;
        }
        setCenter(centerPPM);
        max = getMultipletMax();
    }

    public String getCouplingsAsString() {
        return coupling.getCouplingsAsString();
    }

    public String getCouplingsAsSimpleString() {
        return coupling.getCouplingsAsSimpleString();
    }

    public void setCenter(final double value) {
        myPeakDim.setChemShiftValue((float) value);
    }

    public double getIntensity() {
        if (coupling instanceof Singlet) {
            intensity = myPeakDim.getPeak().getIntensity();
        }
        return intensity;
    }

    public void setIntensity(final double value) {
        if (coupling instanceof Singlet) {
            myPeakDim.getPeak().setIntensity((float) value);
        }
        intensity = value;
    }

    public PeakDim getPeakDim() {
        return myPeakDim;
    }

    public double getVolume() {
        return myPeakDim.getPeak().getVolume1();
    }

    public static Multiplet groupPeakDims(List<PeakDim> peakDims) {
        List<AbsMultipletComponent> comps = new ArrayList<>();
        PeakDim firstPeakDim = peakDims.get(0);
        Multiplet multiplet = firstPeakDim.getMultiplet();
        for (PeakDim peakDim : peakDims) {
            double ppm = peakDim.getChemShiftValue();
            double compIntensity = peakDim.getPeak().getIntensity();
            double volume = peakDim.getPeak().getVolume1();
            double lw = peakDim.getLineWidthValue();
            AbsMultipletComponent comp = new AbsMultipletComponent(multiplet, ppm, compIntensity, volume, lw);
            comps.add(comp);
            if (peakDim != firstPeakDim) {
                peakDim.getPeak().setStatus(-1);
            }
        }
        multiplet.updateCoupling(comps);
        return multiplet;
    }

    public void addPeakDim(PeakDim peakDim) {
        double ppm = peakDim.getChemShiftValue();
        double compIntensity = peakDim.getPeak().getIntensity();
        double volume = peakDim.getPeak().getVolume1();
        double lw = peakDim.getLineWidthValue();
        List<AbsMultipletComponent> comps = getAbsComponentList();
        AbsMultipletComponent comp = new AbsMultipletComponent(this, ppm,
                volume, compIntensity, lw);
        comps.add(comp);
        updateCoupling(comps);
        peakDim.getPeak().setStatus(-1);
    }

    public void updateCoupling(List<AbsMultipletComponent> comps) {
        if (comps.size() == 1) {
            coupling = new Singlet(this);
            this.getPeakDim().setChemShift((float) comps.get(0).offset);
            getOrigin().setIntensity((float) comps.get(0).getIntensity());
            getOrigin().setVolume1((float) comps.get(0).getVolume());
            getOrigin().getPeakDim(0).setLineWidthValue((float) comps.get(0).getLineWidth());
        } else {
            coupling = new ComplexCoupling(this, comps);
        }
        max = getMultipletMax();
    }

    public static void merge(PeakDim peakDimA, PeakDim peakDimB) {
        Multiplet mA = peakDimA.getMultiplet();
        Multiplet mB = peakDimB.getMultiplet();
        merge(mA, mB);
    }

    public static void merge(Multiplet mA, Multiplet mB) {
        List<AbsMultipletComponent> compsA = mA.getAbsComponentList();
        List<AbsMultipletComponent> compsB = mB.getAbsComponentList();

        if (compsA.size() >= compsB.size()) {
            compsA.addAll(compsB);
            mA.updateCoupling(compsA);
            mB.getPeakDim().getPeak().setStatus(-1);
        } else {
            compsB.addAll(compsA);
            mB.updateCoupling(compsB);
            mA.getPeakDim().getPeak().setStatus(-1);
        }
    }

    public Optional<Multiplet> split(double ppm) {
        List<RelMultipletComponent> relComps = getRelComponentList();
        List<AbsMultipletComponent> removeAbsComps = new ArrayList<>();
        List<AbsMultipletComponent> keepAbsComps = new ArrayList<>();
        for (RelMultipletComponent comp : relComps) {
            AbsMultipletComponent absComp = comp.toAbsolute();
            if (absComp.getOffset() < ppm) {
                removeAbsComps.add(absComp);
            } else {
                keepAbsComps.add(absComp);
            }
        }
        Optional<Multiplet> result = Optional.empty();
        if (!removeAbsComps.isEmpty()) {
            Peak peak = getOrigin();
            PeakList peakList = peak.getPeakList();
            Peak newPeak = peakList.getNewPeak();
            PeakDim newPeakDim = newPeak.getPeakDim(0);
            Multiplet newMultiplet = newPeakDim.getMultiplet();
            moveCouplings(this, keepAbsComps, newMultiplet, removeAbsComps);
            result = Optional.of(newMultiplet);
        }
        return result;
    }

    public static void moveCouplings(Multiplet keepMultiplet, List<AbsMultipletComponent> keepAbsComps,
                                     Multiplet newMultiplet, List<AbsMultipletComponent> moveAbsComps) {
        for (var comp : keepAbsComps) {
            comp.multiplet = keepMultiplet;
        }
        for (var comp : moveAbsComps) {
            comp.multiplet = newMultiplet;
        }
        keepMultiplet.updateCoupling(keepAbsComps);
        newMultiplet.updateCoupling(moveAbsComps);
    }

    public void removePeakComponent(int index) {
        List<AbsMultipletComponent> comps = getAbsComponentList();
        comps.remove(index);
        updateCoupling(comps);
    }

    public void removePeakComponents(List<RelMultipletComponent> removeComps) {
        List<RelMultipletComponent> currentComps = getRelComponentList();
        List<AbsMultipletComponent> newComps = new ArrayList<>();
        for (RelMultipletComponent currentComp : currentComps) {
            if (!removeComps.contains(currentComp)) {
                newComps.add(currentComp.toAbsolute());
            }
        }
        updateCoupling(newComps);
    }

    public String getMultiplicity() {
        return coupling.getMultiplicity();
    }

    public String getSummary() {
        double normVal = 0;
        String summary = "";
        // FIXME  make precision in ctr a function of dig resolution  sw/sfrq/size
        PeakList peakList = myPeakDim.getPeak().getPeakList();
        if (peakList.scale > 0.0) {
            normVal = getVolume() / peakList.scale;
        }

        String couplings = getCouplingsAsSimpleString();
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(Format.format3(getCenter()));
        sBuilder.append(" ");
        sBuilder.append(getMultiplicity());
        sBuilder.append(" ");
        sBuilder.append(couplings);
        sBuilder.append(" ");
        sBuilder.append(Format.format2(normVal));

        return sBuilder.toString();
    }

    public Peak getOrigin() {
        Peak origin = myPeakDim.getPeak();
        return origin;
    }

    public void setGenericMultiplet() {
        coupling = new ComplexCoupling(this, getAbsComponentList());
        myPeakDim.peakDimUpdated();
    }

    public void setSinglet() {
        coupling = new Singlet(this);
    }

    public boolean isGenericMultiplet() {
        boolean result = false;

        if (coupling != null) {
            result = coupling instanceof ComplexCoupling;
        }

        return result;
    }

    public boolean isCoupled() {
        if (getCoupling() == null) {
            return false;
        } else {
            return (getCoupling() instanceof CouplingPattern);
        }
    }

    public void setCouplingValues(double[] values, int[] n, double intensity, double[] sin2Thetas) {
        coupling = new CouplingPattern(this, values, n, intensity, sin2Thetas);
        myPeakDim.peakDimUpdated();
    }

    public void setCoupling(Coupling coupling) {
        this.coupling = coupling;
        myPeakDim.peakDimUpdated();
    }

    public Coupling getCoupling() {
        return coupling;
    }

    public double getMax() {
        return max;
    }

    public double getMultipletMax() {
        List<RelMultipletComponent> comps = getRelComponentList();
        double maxIntensity = 0.0;
        for (MultipletComponent comp : comps) {
            double hSum = 0.0;
            double ctr = comp.getOffset();
            for (MultipletComponent comp2 : comps) {
                double ctr2 = comp2.getOffset();
                double halfWid2 = comp2.getLineWidth() / 2.0;

                double h2 = comp2.getIntensity();
                double contrib = h2 * halfWid2 * halfWid2 / (halfWid2 * halfWid2 + (ctr2 - ctr) * (ctr2 - ctr));
                hSum += contrib;
            }
            if (hSum > maxIntensity) {
                maxIntensity = hSum;
            }
        }
        return maxIntensity;

    }

    public List<AbsMultipletComponent> getAbsComponentList() {
        return coupling.getAbsComponentList();
    }

    public List<RelMultipletComponent> getRelComponentList() {
        return coupling.getRelComponentList();
    }

    public ArrayList<TreeLine> getSplittingGraph() {
        return coupling.getSplittingGraph();
    }

    public boolean inRegion(double[][] limits, double[][] foldLimits, int[] dim) {
        int nSearchDim = limits.length;
        boolean ok = true;
        for (int j = 0; j < nSearchDim; j++) {
            if ((dim.length <= j) || (dim[j] == -1)) {
                continue;
            }
            double ctr = getCenter();

            if ((ctr < limits[j][0]) || (ctr > limits[j][1])) {
                ok = false;
                break;
            }

        }
        return ok;

    }

    public float getBoundsValue() {
        List<PeakDim> links = myPeakDim.getLinkedPeakDims();
        float max = Float.NEGATIVE_INFINITY;
        float min = Float.MAX_VALUE;
        for (PeakDim peakDim : links) {
            float value = peakDim.getChemShiftValue();
            float width = Math.abs(peakDim.getLineWidthValue());
            if ((value + width) > max) {
                max = (value + width);
            }
            if ((value - width) < min) {
                min = value - width;
            }
        }
        return (max - min);
    }

    public void makeSinglet() {
        coupling = new Singlet(this);
    }

    public void removeCoupling() {
        if (coupling instanceof CouplingPattern) {
            CouplingPattern cPattern = (CouplingPattern) coupling;
            int[] nValues = cPattern.getNValues();
            double[] values = cPattern.getValues();
            double[] sin2thetas = cPattern.getSin2Thetas();
            int nSplittings = nValues.length - 1;
            if (nSplittings > 0) {
                int[] newN = new int[nSplittings];
                System.arraycopy(nValues, 0, newN, 0, nSplittings);
                double[] newValues = new double[nSplittings];
                System.arraycopy(values, 0, newValues, 0, nSplittings);
                double[] newSin2thetas = new double[nSplittings];
                System.arraycopy(sin2thetas, 0, newSin2thetas, 0, nSplittings);
                setCouplingValues(newValues, newN, cPattern.getIntensity(), newSin2thetas);
            } else {
                coupling = new Singlet(this);
            }
        }
    }

    public void addCoupling(Coupling oldCoupling, String couplingType, double couplingValue) throws IllegalArgumentException {
        int nType = "sdtqph".indexOf(couplingType) + 1;
        if (nType < 1) {
            throw new IllegalArgumentException("Invalid coupling type: " + couplingType);
        }
        if (oldCoupling == null) {
            oldCoupling = coupling;
        }
        if (oldCoupling instanceof CouplingPattern) {
            CouplingPattern cPattern = (CouplingPattern) oldCoupling;
            int[] nValues = cPattern.getNValues();
            double[] values = cPattern.getValues();
            double[] sin2thetas = cPattern.getSin2Thetas();
            int nSplittings = nValues.length + 1;
            int[] newN = new int[nSplittings];
            double[] newValues = new double[nSplittings];
            double[] newSin2thetas = new double[nSplittings];
            System.arraycopy(nValues, 0, newN, 0, nValues.length);
            System.arraycopy(values, 0, newValues, 0, nValues.length);
            System.arraycopy(sin2thetas, 0, newSin2thetas, 0, nValues.length);
            newN[nSplittings - 1] = nType;
            newValues[nSplittings - 1] = couplingValue;
            setCouplingValues(newValues, newN, cPattern.getIntensity(), newSin2thetas);
        } else if (oldCoupling instanceof Singlet) {
            int[] newN = {nType};
            double[] newValues = {couplingValue};
            double[] newSin2thetas = {0.0};
            setCouplingValues(newValues, newN, 1.0, newSin2thetas);
        }
    }

    public void expandCoupling(int limit) throws IllegalArgumentException {
        if (coupling instanceof CouplingPattern) {
            CouplingPattern cPattern = (CouplingPattern) coupling;
            int[] nValues = cPattern.getNValues();
            int newLength = 0;
            double[] values = cPattern.getValues();
            for (int nValue : nValues) {
                if (nValue > limit) {
                    nValue = limit;
                }
                newLength += nValue - 1;
            }
            int[] newN = new int[newLength];
            double[] newValues = new double[newLength];
            double[] newSin2thetas = new double[newLength];
            for (int i = 0, j = 0; i < nValues.length; i++) {
                int nValue = nValues[i];
                if (nValue > limit) {
                    nValue = limit;
                }
                double mult = 1.1;
                for (int k = 1; k < nValue; k++) {
                    newN[j] = 2;
                    newValues[j] = values[i] * mult;
                    newSin2thetas[j] = 0.0;
                    mult *= 0.9;
                    j++;
                }
            }
            setCouplingValues(newValues, newN, cPattern.getIntensity(), newSin2thetas);
        }
    }

    public static List<AbsMultipletComponent> getAllComps(List<PeakDim> peakDims) {
        List<AbsMultipletComponent> allComps = new ArrayList<>();
        for (PeakDim peakDim : peakDims) {
            allComps.addAll(peakDim.getMultiplet().getAbsComponentList());
        }
        return allComps;
    }

    /**
     * Get the boundaries, center and widths of the region of a multiplet in a
     * specified dataset. The indices of the arrays containing this information
     * are the dataset dimensions. So p[0][0] and p[0][1] will contain borders
     * of the peak along dimension 0 of the dataset, which may be a different
     * dimension than dimension 0 of the peak.
     *
     * @param theFile The dataset to use for translating ppm to pts
     * @param pdim    An integer mapping of peak dimension to dataset dimension.
     *                For example, pdim[0] contains the dataset dimension that corresponds to
     *                peak dimension 0.
     * @param p       Two-dimensional pre-allocated array of int that will contain the
     *                boundaries of the peak dimension. The boundaries are determined by the
     *                peak foot print (bounds).
     * @param cpt     Array of ints specifying the center of the peak region.
     * @param width   Array of doubles containing the widths of the peak in units
     *                of dataset points. The width is determined by the peak linewidth
     */
    public void getMultipletRegion(DatasetBase theFile, int[] pdim, int[][] p,
                                   int[] cpt, double[] width) {
        double p1 = Double.NEGATIVE_INFINITY;
        double p2 = Double.MAX_VALUE;

        List<AbsMultipletComponent> comps = getAbsComponentList();
        for (AbsMultipletComponent comp : comps) {
            double pc = comp.getOffset();
            double lw = comp.getLineWidth();

            p1 = Math.max(p1, pc + Math.abs(lw));
            p2 = Math.min(p2, pc - Math.abs(lw));
        }
        p[0][0] = theFile.ppmToFoldedPoint(0, p1);
        p[0][1] = theFile.ppmToFoldedPoint(0, p2);

        cpt[0] = theFile.ppmToFoldedPoint(0, getCenter());

        width[0] = Math.abs(p[0][0] - p[0][1]);

    }

    public double[] simulate(int m, double min, double max) {
        double widthLimit = 0.0001;
        List<AbsMultipletComponent> comps = getAbsComponentList();
        double delta = (max - min) / (m - 1);
        double f = min;
        double[] bpCoords = new double[m];
        for (int i = 0; i < m; i++) {
            for (AbsMultipletComponent comp : comps) {
                double c = comp.getOffset();
                double w = comp.getLineWidth();
                if (w < widthLimit) {
                    w = widthLimit;
                }
                double a = comp.getIntensity();
                double v = ((a * ((w * w) / 4)) / (((w * w) / 4)
                        + ((f - c) * (f - c))));
                bpCoords[i] += v;
            }

            f += delta;
        }
        return bpCoords;
    }
}
