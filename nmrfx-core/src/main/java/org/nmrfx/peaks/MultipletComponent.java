/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.peaks;

/**
 * @author brucejohnson
 */
public abstract class MultipletComponent {

    Multiplet multiplet;
    double offset;
    double lineWidth;
    double intensity;
    double volume;

    public MultipletComponent(Multiplet multiplet, double offset, double intensity, double volume, double lw) {
        this.multiplet = multiplet;
        this.offset = offset;
        this.intensity = intensity;
        this.lineWidth = lw;
        this.volume = volume;
    }

    public MultipletComponent(Multiplet multiplet, MultipletComponent component) {
        this.multiplet = multiplet;
        this.offset = component.offset;
        this.intensity = component.intensity;
        this.lineWidth = component.lineWidth;
        this.volume = component.volume;
    }

    public double getOffset() {
        return offset;
    }

    public Multiplet getMultiplet() {
        return multiplet;
    }

    public double getIntensity() {
        return intensity;
    }

    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }
}
