/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

/**
 *
 * @author brucejohnson
 */
public class RelMultipletComponent extends MultipletComponent {

    public RelMultipletComponent(Multiplet multiplet, double offset, double intensity, double volume, double lw) {
        super(multiplet, offset, intensity, volume, lw);
    }

    public AbsMultipletComponent toAbsolute() {
        double center = multiplet.getPeakDim().getChemShiftValue();
        double ppm = center + offset / multiplet.getPeakDim().getSpectralDimObj().getSf();
        return new AbsMultipletComponent(multiplet, ppm, intensity, volume, lineWidth);
    }

    public AbsMultipletComponent toAbsolute(double center, double sf) {
        double ppm = center + offset / sf;
        return new AbsMultipletComponent(multiplet, ppm, intensity, volume, lineWidth);
    }

}
