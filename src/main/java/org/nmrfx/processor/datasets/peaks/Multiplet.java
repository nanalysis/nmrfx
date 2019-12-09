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
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.utilities.Format;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class Multiplet implements PeakOrMulti, Comparable {

    private double intensity = 0.0;
    private double volume;
    private int idNum;
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

    public void setIDNum(int value) {
        idNum = value;
    }

    public int getIDNum() {
        return idNum;
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

    public void set(double centerPPM, double[] freqs, double[] amplitudes, double lineWidth) {

        if (coupling instanceof ComplexCoupling) {
            myPeakDim.setChemShiftValue((float) centerPPM);
            coupling = new ComplexCoupling(this, freqs, amplitudes, lineWidth);
        }
    }

    public void set(double centerPPM, double[] couplingValues, double amplitude, double[] sin2thetas) {
        if (coupling instanceof CouplingPattern) {
            CouplingPattern cPat = (CouplingPattern) coupling;
            int[] nValues = cPat.getNValues();
            coupling = new CouplingPattern(this, couplingValues, nValues, amplitude, sin2thetas);
        }
        setCenter(centerPPM);
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
        if (coupling instanceof Singlet) {
            volume = myPeakDim.getPeak().getVolume1();
        }
        return volume;
    }

    public void setVolume(final double value) {
        volume = value;
    }

    public void addPeakDim(PeakDim peakDim) {
        double ppm = peakDim.getChemShiftValue();
        double compIntensity = peakDim.getPeak().getIntensity();
        double lw = peakDim.getLineWidthValue();
        List<MultipletComponent> comps = getAbsComponentList();
        MultipletComponent comp = new MultipletComponent(ppm, compIntensity, lw);
        comps.add(comp);
        updateCoupling(comps);
        peakDim.getPeak().setStatus(-1);
    }

    public void updateCoupling(List<MultipletComponent> comps) {
        coupling = new ComplexCoupling(this, comps);
    }

    public static void merge(PeakDim peakDimA, PeakDim peakDimB) {
        Multiplet mA = peakDimA.getMultiplet();
        Multiplet mB = peakDimB.getMultiplet();
        merge(mA, mB);
    }

    public static void merge(Multiplet mA, Multiplet mB) {
        List<MultipletComponent> compsA = mA.getAbsComponentList();
        List<MultipletComponent> compsB = mB.getAbsComponentList();

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

    public void removePeakComponent(int index) {
        List<MultipletComponent> comps = getAbsComponentList();
        comps.remove(index);
        updateCoupling(comps);
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
        Peak origin = myPeakDim.myPeak;
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

        if (coupling == null) {
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

    /*
    public void updateVolumes() {
        intensity = 0.0;
        double sumV = 0.0;
        for (PeakDim lPeakDim : peakDims) {
            //lPeakDim.myPeak.setFlag(4, true);
            // XXX 1.05 is a fudge factor to make the volume calculated here closer to those from
            // summing data points across peak, not unreasonable because you have to sum out to infinity
            // to get the whole integral
            lPeakDim.myPeak.setVolume1((float) (lPeakDim.myPeak.getIntensity() * Math.abs(lPeakDim.myPeak.peakDims[0].getLineWidthValue()) * Math.PI / 2.0 / 1.05));
            sumV += lPeakDim.myPeak.getVolume1();
        }
        volume = sumV;
        intensity = getMultipletMax();
        myPeakDim.peakDimUpdated();
    }
     */
    public double getMultipletMax() {
        List<MultipletComponent> comps = getRelComponentList();
        double maxIntensity = 0.0;
        for (MultipletComponent comp : comps) {
            double hSum = 0.0;
            double ctr = comp.getOffset();
            for (MultipletComponent comp2 : comps) {
                double ctr2 = comp2.getOffset();
                double lw2 = comp2.getLineWidth();
                double h2 = comp2.getIntensity();
                double contrib = h2 * lw2 * lw2 / (lw2 * lw2 + (ctr2 - ctr) * (ctr2 - ctr) * 4.0);
                hSum += contrib;
                // System.out.println(ctr+" "+ctr2+" "+lw2+" "+h2+" "+contrib+" "+hSum);
            }
            if (hSum > maxIntensity) {
                maxIntensity = hSum;
            }
        }
        return maxIntensity;

    }

    List<MultipletComponent> getAbsComponentList() {
        return coupling.getAbsComponentList();
    }

    List<MultipletComponent> getRelComponentList() {
        return coupling.getRelComponentList();
    }

    public ArrayList<Line2D> getSplittingGraph() {
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
//                System.out.println(j + " " + limits[j][0] + " " + limits[j][1] + " " + ctr);
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
}
