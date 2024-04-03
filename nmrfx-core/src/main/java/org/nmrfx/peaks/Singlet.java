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

/**
 * @author brucejohnson
 */
public class Singlet extends Coupling {

    public Singlet(Multiplet multiplet) {
        this.multiplet = multiplet;
    }
    @Override
    Coupling copy(Multiplet multiplet) {
        return new Singlet(multiplet);
    }

    Coupling update(double[] newValues, double[] newIntensities) {
        if (newIntensities.length == 1) {
            multiplet.setIntensity(newIntensities[0]);
        }
        return this;
    }

    Coupling update(double[] newValues, double intensity, double[] sin2Thetas) {
        multiplet.setIntensity(intensity);
        return this;
    }

    @Override
    public boolean isCoupled() {
        return false;
    }


    @Override
    public String getMultiplicity() {
        return "s";
    }

    @Override
    public String getCouplingsAsString() {
        return String.valueOf(0.0);

    }

    @Override
    public String getCouplingsAsSimpleString() {
        return "";
    }

    protected Coupling adjustCouplings(final Multiplet multiplet, final int iCoupling, final double newValue) {
        return this;
    }

    @Override
    List<AbsMultipletComponent> getAbsComponentList() {
        List<AbsMultipletComponent> comps = new ArrayList<>();
        PeakDim peakDim = multiplet.getPeakDim();
        AbsMultipletComponent comp = new AbsMultipletComponent(multiplet, peakDim.getChemShiftValue(),
                peakDim.getPeak().getIntensity(), peakDim.getPeak().getVolume1(), peakDim.getLineWidthValue());
        comps.add(comp);
        return comps;
    }

    @Override
    List<RelMultipletComponent> getRelComponentList() {
        List<RelMultipletComponent> comps = new ArrayList<>();
        PeakDim peakDim = multiplet.getPeakDim();
        double sf = peakDim.getSpectralDimObj().getSf();
        RelMultipletComponent comp = new RelMultipletComponent(multiplet, 0.0,
                peakDim.getPeak().getIntensity(), peakDim.getPeak().getVolume1(), peakDim.getLineWidthValue() * sf);
        comps.add(comp);
        return comps;
    }

    @Override
    public ArrayList<TreeLine> getSplittingGraph() {
        ArrayList<TreeLine> lines = new ArrayList<>();
        lines.add(new TreeLine(0.0, 0.0, 0.0, 0.0));
        return lines;
    }

}
