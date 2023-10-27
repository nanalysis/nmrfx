/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.peaks;

/**
 * @author brucejohnson
 */
public class RelMultipletComponent extends MultipletComponent {

    public RelMultipletComponent(Multiplet multiplet, double offset, double intensity, double volume, double lw) {
        super(multiplet, offset, intensity, volume, lw);
    }

    public RelMultipletComponent(Multiplet multiplet, RelMultipletComponent component) {
        super(multiplet,component);
    }

    public AbsMultipletComponent toAbsolute() {
        double center = multiplet.getPeakDim().getChemShiftValue();
        double sf = multiplet.getPeakDim().getSpectralDimObj().getSf();
        double ppm = center - offset / sf;
        return new AbsMultipletComponent(multiplet, ppm, intensity, volume, lineWidth / sf);
    }

    public AbsMultipletComponent toAbsolute(double center, double sf) {
        double ppm = center - offset / sf;
        return new AbsMultipletComponent(multiplet, ppm, intensity, volume, lineWidth / sf);
    }

}
