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

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author brucejohnson
 */
public class ComplexCoupling extends Coupling {

    private double[] intensities = new double[0];
    private double[] frequencyOffsets = new double[0];

    public String getMultiplicity() {
        return "m";
    }

    public boolean isCoupled() {
        return true;
    }

    ComplexCoupling(final Multiplet multiplet, final double[] frequencyOffsets, final double[] intensities) {
        this.multiplet = multiplet;
        this.frequencyOffsets = frequencyOffsets;
        this.intensities = intensities;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < intensities.length; i++) {
            if (intensities[i] > max) {
                max = intensities[i];
            }
        }
        multiplet.setIntensity(multiplet.getMultipletMax());
    }

//    public TclObject getCouplingsAsTclObject(Interp interp)
//            throws TclException {
//        int nFreq = getFrequencyCount();
//        TclObject list = TclList.newInstance();
//        TclList.append(interp, list, TclString.newInstance("m"));
//
//        for (int i = 0; i < nFreq; i++) {
//            TclList.append(interp, list,
//                    TclString.newInstance(String.format("%.4f", frequencyOffsets[i])));
//            TclList.append(interp, list,
//                    TclString.newInstance(String.format("%.6g", intensities[i])));
//        }
//        return list;
//
//    }
    public String getCouplingsAsString() {
        return "m";
    }

    public String getCouplingsAsSimpleString() {
        return "";
    }

    protected Coupling adjustCouplings(final Multiplet multiplet, final int iCoupling, final double newValue) {
        double[] fo = multiplet.getFrequencyOffsets();
        Arrays.sort(fo);
        return new ComplexCoupling(multiplet, fo, intensities);
    }

    Coupling update(double[] newFO, double[] newIntensities) {
        return new ComplexCoupling(multiplet, newFO, newIntensities);

    }

    Coupling update(double[] newFO, double intensity, double[] sin2Thetas) {
        return new ComplexCoupling(multiplet, newFO, null);

    }

    FreqIntensities getFreqIntensitiesFromSplittings() {
        FreqIntensities fiValues;
        int nFreqs = frequencyOffsets.length;
        PeakDim peakDim = multiplet.getPeakDim();
        double sf = peakDim.getPeak().peakList.getSpectralDim(peakDim.getSpectralDim()).getSf();
        fiValues = new FreqIntensities(nFreqs);

        for (int iFreq = 0; iFreq < nFreqs; iFreq++) {
            fiValues.freqs[iFreq] = frequencyOffsets[iFreq] / sf;
            fiValues.intensities[iFreq] = intensities[iFreq];
        }
        return fiValues;
    }

    int getFrequencyCount() {
        return frequencyOffsets.length;
    }

    public double getFrequencyOffset(int i) {
        return frequencyOffsets[i];
    }

    public ArrayList<Line2D> getSplittingGraph() {
        ArrayList<Line2D> lines = new ArrayList<Line2D>();
        int nFreqs = getFrequencyCount();
        PeakDim peakDim = multiplet.getPeakDim();
        double sf = peakDim.myPeak.peakList.getSpectralDim(peakDim.getSpectralDim()).getSf();

        for (int iFreq = 0; iFreq < nFreqs; iFreq++) {
            double freq = frequencyOffsets[iFreq] / sf;
            lines.add(new Line2D.Double(0.0, 0.0, freq, 0.0));
        }
        return lines;
    }
}
