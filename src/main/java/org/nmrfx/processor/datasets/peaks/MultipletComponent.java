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
public class MultipletComponent {

    double offset;
    double lineWidth;
    double intensity;

    public MultipletComponent(double offset, double intensity, double lw) {
        this.offset = offset;
        this.intensity = intensity;
        this.lineWidth = lw;
    }

    public double getOffset() {
        return offset;
    }

    public double getIntensity() {
        return intensity;
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public MultipletComponent toRelative(double center, double sf) {
        double relOffset = (offset - center) / sf;
        return new MultipletComponent(relOffset, intensity, lineWidth);
    }

    public MultipletComponent toAbsolute(double center, double sf) {
        double ppm = center + offset / sf;
        return new MultipletComponent(ppm, intensity, lineWidth);
    }

}
