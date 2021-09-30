package org.nmrfx.analyst.compounds;

public class FitResult {

    private final double scale;
    private final double offset;
    private final double dev;
    private final double corr;
    private double shift = 0;

    FitResult(double offset, double scale, double dev, double corr) {
        super();
        this.scale = scale;
        this.offset = offset;
        this.corr = corr;
        this.dev = dev;
    }

    /**
     * @return the scale
     */
    public double getScale() {
        return scale;
    }

    public double getOffset() {
        return offset;
    }

    public double getDev() {
        return dev;
    }

    public double getCorr() {
        return corr;
    }

    public double getShift() {
        return shift;
    }

    public void setShift(double shift) {
        this.shift = shift;
    }
}
