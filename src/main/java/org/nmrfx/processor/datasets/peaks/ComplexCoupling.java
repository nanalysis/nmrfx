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
import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class ComplexCoupling extends Coupling {

    List<RelMultipletComponent> components = new ArrayList<>();

    @Override
    public String getMultiplicity() {
        return "m";
    }

    @Override
    public boolean isCoupled() {
        return true;
    }

    ComplexCoupling(final Multiplet multiplet, List<AbsMultipletComponent> absComponents) {
        this.multiplet = multiplet;
        double sumpPPM = 0.0;
        double sumVolume = 0.0;
        double maxIntensity = 0.0;
        for (MultipletComponent comp : absComponents) {
            sumpPPM += comp.getOffset();
            sumVolume += comp.getVolume();
            maxIntensity = Math.max(maxIntensity, comp.getIntensity());
        }
        double sf = multiplet.getPeakDim().getSpectralDimObj().getSf();
        double center = sumpPPM / absComponents.size();
        for (AbsMultipletComponent comp : absComponents) {
            components.add(comp.toRelative(center, sf));
        }
        multiplet.getPeakDim().setChemShiftValue((float) center);
        multiplet.getPeakDim().setLineWidthValue((float) absComponents.get(0).getLineWidth());
        multiplet.getPeakDim().getPeak().setVolume1((float) sumVolume);
        multiplet.getPeakDim().getPeak().setIntensity((float) maxIntensity);
    }

    ComplexCoupling(final Multiplet multiplet, final double[] deltaPPMs,
            final double[] intensities, final double[] volumes, final double lineWidthPPM) {
        this.multiplet = multiplet;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < intensities.length; i++) {
            if (intensities[i] > max) {
                max = intensities[i];
            }
        }
        double sf = multiplet.getPeakDim().getSpectralDimObj().getSf();
        multiplet.setIntensity(max);
        double centerPPM = multiplet.getCenter();
        double lineWidthHz = lineWidthPPM * sf;
        for (int i = 0; i < intensities.length; i++) {
            double offset = deltaPPMs[i] * sf;
            RelMultipletComponent comp = new RelMultipletComponent(multiplet, offset, intensities[i], volumes[i], lineWidthHz);
            components.add(comp);
        }
        sortByFreq();
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
        return components.size();
    }

    @Override
    public ArrayList<Line2D> getSplittingGraph() {
        ArrayList<Line2D> lines = new ArrayList<>();
        PeakDim peakDimRef = multiplet.getPeakDim();
        double sf = peakDimRef.getPeak().peakList.getSpectralDim(peakDimRef.getSpectralDim()).getSf();
        components.stream().map((comp) -> (-comp.getOffset() / sf)).forEachOrdered((deltaPPM) -> {
            lines.add(new Line2D.Double(0.0, 0.0, deltaPPM, 0.0));
        });
        return lines;
    }

    final void sortByFreq() {
        components.sort((a, b) -> Double.compare(b.getOffset(), a.getOffset()));
    }

    @Override
    List<AbsMultipletComponent> getAbsComponentList() {
        PeakDim peakDimRef = multiplet.getPeakDim();
        double sf = peakDimRef.getPeak().peakList.getSpectralDim(peakDimRef.getSpectralDim()).getSf();
        double centerPPM = peakDimRef.getChemShiftValue();
        List<AbsMultipletComponent> absComps = new ArrayList<>();
        for (RelMultipletComponent comp : components) {
            absComps.add(comp.toAbsolute(centerPPM, sf));
        }
        return absComps;
    }

    @Override
    List<RelMultipletComponent> getRelComponentList() {
        return components;
    }

}
