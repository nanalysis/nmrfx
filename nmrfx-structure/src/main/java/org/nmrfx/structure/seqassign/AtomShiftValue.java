package org.nmrfx.structure.seqassign;

/**
 * @author brucejohnson
 */
public class AtomShiftValue {

    String aName = "";
    double ppm = 0.0;
    String peakString = "";

    public AtomShiftValue(String aName, double ppm, String peakString) {
        this.aName = aName;
        this.ppm = ppm;
        this.peakString = peakString;
    }

    public String getAName() {
        return aName;
    }

    public double getPPM() {
        return ppm;
    }

    public String toString() {
        return peakString + " " + aName + " " + ppm;
    }
}
