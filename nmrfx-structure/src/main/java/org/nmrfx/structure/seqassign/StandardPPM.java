package org.nmrfx.structure.seqassign;

/**
 * @author brucejohnson
 */
public class StandardPPM {

    private final double avg;
    private final double sdev;

    StandardPPM(double avg, double sdev) {
        this.avg = avg;
        this.sdev = sdev;
    }

    /**
     * @return the avg
     */
    public double getAvg() {
        return avg;
    }

    /**
     * @return the sdev
     */
    public double getSdev() {
        return sdev;
    }

}
