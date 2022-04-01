package org.nmrfx.chemistry.relax;

public class SpectralDensity {
    String name;
    // two dimensional array, first row is frequency, 2nd row is J value, third row is J error
    double[][] spectralDensities;

    public SpectralDensity(String name, double[][] spectralDensities) {
        this.name = name;
        this.spectralDensities = spectralDensities;
    }

    public String getName() {
        return name;
    }

    public double[][] getSpectralDensities() {
        return spectralDensities;
    }
}
