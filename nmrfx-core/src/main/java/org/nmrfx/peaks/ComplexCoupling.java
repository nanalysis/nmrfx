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

import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparing;

/**
 * @author brucejohnson
 */
public class ComplexCoupling extends Coupling {

    List<RelMultipletComponent> components = new ArrayList<>();

   @Override
   public ComplexCoupling copy(Multiplet multiplet) {
        ComplexCoupling newCoupling = new ComplexCoupling(this, multiplet);
        return newCoupling;
    }

    @Override
    public String getMultiplicity() {
        return "m";
    }

    @Override
    public boolean isCoupled() {
        return true;
    }

    public ComplexCoupling(ComplexCoupling complexCoupling, Multiplet multiplet) {
        this.multiplet = multiplet;
        components = new ArrayList<>();
        for (var comp:complexCoupling.components) {
                components.add(new RelMultipletComponent(multiplet, comp));
        }
    }

    public ComplexCoupling(final Multiplet multiplet, List<AbsMultipletComponent> absComponents) {
        this.multiplet = multiplet;
        double sumpPPM = 0.0;
        double sumVolume = 0.0;
        double maxIntensity = 0.0;
        for (MultipletComponent comp : absComponents) {
            sumpPPM += comp.getOffset();
            sumVolume += comp.getVolume();
            maxIntensity = Math.max(maxIntensity, comp.getIntensity());
            comp.multiplet = multiplet;
        }
        double sf = multiplet.getPeakDim().getSpectralDimObj().getSf();
        double center = sumpPPM / absComponents.size();
        for (AbsMultipletComponent comp : absComponents) {
            components.add(comp.toRelative(center, sf));
        }
        sortByFreq();

        multiplet.getPeakDim().setChemShiftValue((float) center);
        multiplet.getPeakDim().setLineWidthValue((float) absComponents.get(0).getLineWidth());
        multiplet.getPeakDim().getPeak().setVolume1((float) sumVolume);
        multiplet.getPeakDim().getPeak().setIntensity((float) maxIntensity);
        multiplet.setIntensity(maxIntensity);
    }

    ComplexCoupling(final Multiplet multiplet, final double[] deltaPPMs,
                    final double[] intensities, final double[] volumes, final double lineWidthPPM) {
        this.multiplet = multiplet;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (int i = 0; i < intensities.length; i++) {
            if (intensities[i] > max) {
                max = intensities[i];
            }
            sum += volumes[i];
        }
        double sf = multiplet.getPeakDim().getSpectralDimObj().getSf();
        multiplet.getOrigin().setVolume1((float) sum);
        multiplet.setIntensity(max);
        double lineWidthHz = lineWidthPPM * sf;
        for (int i = 0; i < intensities.length; i++) {
            double offset = -deltaPPMs[i] * sf;
            RelMultipletComponent comp = new RelMultipletComponent(multiplet, offset, intensities[i], volumes[i], lineWidthHz);
            components.add(comp);
        }
        sortByFreq();
    }

    public ComplexCoupling(final Multiplet multiplet, final List<Double> offsets, final List<Double> intensities, final List<Double> volumes, final List<Double> lineWidths) {
        this.multiplet = multiplet;
        components = new ArrayList<>();
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (int i = 0; i < offsets.size(); i++) {
            if (intensities.get(i) > max) {
                max = intensities.get(i);
            }
            sum += volumes.get(i);
            RelMultipletComponent comp = new RelMultipletComponent(multiplet,
                    offsets.get(i),
                    intensities.get(i),
                    volumes.get(i),
                    lineWidths.get(i)
            );
            components.add(comp);
        }
        multiplet.setIntensity(max);
        // fixme  should count lines and make sure values.length, n.length and intensities.length are appropriate
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
    public ArrayList<TreeLine> getSplittingGraph() {
        ArrayList<TreeLine> lines = new ArrayList<>();
        PeakDim peakDimRef = multiplet.getPeakDim();
        double sf = peakDimRef.getPeak().peakList.getSpectralDim(peakDimRef.getSpectralDim()).getSf();
        components.stream().map((comp) -> (-comp.getOffset() / sf)).forEachOrdered((deltaPPM) -> {
            lines.add(new TreeLine(0.0, 0.0, deltaPPM, 0.0));
        });
        return lines;
    }

    final void sortByFreq() {
        components.sort(comparing((p) -> p.getOffset()));
    }

    @Override
    public List<AbsMultipletComponent> getAbsComponentList() {
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
    public List<RelMultipletComponent> getRelComponentList() {
        return components;
    }

}
