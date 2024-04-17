package org.nmrfx.peaks;

public class PeakDistance implements Comparable<PeakDistance> {

    final Peak peak;
    final double distance;
    final double[] deltas;

    public PeakDistance(Peak peak, double distance, double[] deltas) {
        this.peak = peak;
        this.distance = distance;
        this.deltas = deltas;
    }

    public PeakDistance(Peak peak, double distance) {
        this.peak = peak;
        this.distance = distance;
        this.deltas = new double[0];
    }

    public Peak getPeak() {
        return peak;
    }

    public double getDistance() {
        return distance;
    }

    public double getDelta(int i) {
        return deltas[i];
    }

    public double[] getDeltas() {
        return deltas;
    }

    @Override
    public int compareTo(PeakDistance peakDis2) {
        return Double.compare(distance, peakDis2.distance);
    }
}
