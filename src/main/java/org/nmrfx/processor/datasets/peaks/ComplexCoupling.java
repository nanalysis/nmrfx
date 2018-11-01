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

/**
 *
 * @author brucejohnson
 */
public class ComplexCoupling extends Coupling {

    @Override
    public String getMultiplicity() {
        return "m";
    }

    @Override
    public boolean isCoupled() {
        return true;
    }

    ComplexCoupling(final Multiplet multiplet, double center, final double[] frequencyOffsets, final double[] intensities) {
        this.multiplet = multiplet;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < intensities.length; i++) {
            if (intensities[i] > max) {
                max = intensities[i];
            }
        }
        double sf = multiplet.getPeakDim().getSpectralDimObj().getSf();
        multiplet.setIntensity(multiplet.getMultipletMax());
        int i = 0;
        //System.out.println("setup cmplex center " + center);
        for (PeakDim peakDim : multiplet.getPeakDims()) {
            double shift = center - frequencyOffsets[i] / sf;
            //System.out.println("peakshift " + shift + " hz " + frequencyOffsets[i] + " int " + intensities[i]);
            peakDim.setChemShiftValueNoCheck((float) shift);
            peakDim.getPeak().setIntensity((float) intensities[i]);
            i++;
        }
    }

    @Override
    public String getCouplingsAsString() {
        return "m";
    }

    @Override
    public String getCouplingsAsSimpleString() {
        return "";
    }

    public int getFrequencyCount() {
        return multiplet.getPeakDims().size();
    }

    @Override
    FreqIntensities getFreqIntensitiesFromSplittings() {
        FreqIntensities fiValues;
        int nFreqs = multiplet.getPeakDims().size();
        PeakDim peakDimRef = multiplet.getPeakDim();
        double sf = peakDimRef.getPeak().peakList.getSpectralDim(peakDimRef.getSpectralDim()).getSf();
        fiValues = new FreqIntensities(nFreqs);
        double centerPPM = multiplet.getCenter();

        int iFreq = 0;
        for (PeakDim peakDim : multiplet.getPeakDims()) {
            fiValues.freqs[iFreq] = (peakDim.getChemShift() - centerPPM) * sf;
            fiValues.intensities[iFreq] = peakDim.getPeak().getIntensity();
            iFreq++;
        }

        return fiValues;
    }

    @Override
    public ArrayList<Line2D> getSplittingGraph() {
        ArrayList<Line2D> lines = new ArrayList<>();
        double centerPPM = multiplet.getCenter();

        multiplet.getPeakDims().stream().map((peakDim) -> (centerPPM - peakDim.getChemShift())).forEachOrdered((deltaPPM) -> {
            lines.add(new Line2D.Double(0.0, 0.0, deltaPPM, 0.0));
        });
        return lines;
    }
}
