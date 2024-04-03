package org.nmrfx.processor.optimization;

/**
 * @author brucejohnson
 */
public class FitReactionAB extends FitEquation {
    static final String[] parNames = {"A", "B", "f", "k"};

    @Override
    public String[] parNames() {
        return parNames;
    }

    @Override
    public int nY() {
        return 2;
    }

    @Override
    public int nX() {
        return 1;
    }

    public Guesses guess() {
        double yMax0 = FitUtils.getMaxValue(yValues[0]);
        double yMax1 = FitUtils.getMaxValue(yValues[1]);
        double midX = FitUtils.getMidY0(xValues[0], yValues[0]);
        double r0 = -Math.log(0.5) / midX;
        double[] start = {yMax0, yMax1, 0.5, r0};
        double[] lower = {yMax0 / 5.0, yMax1 / 5.0, 0.0, r0 / 2.0};
        double[] upper = {yMax0 * 3.0, yMax1 * 3.0, 1.0, r0 * 3.0};
        return new Guesses(start, lower, upper);
    }

    public double[] calcValue(double[] xA, double[] pars) {
        double x = xA[0];
        double rScale = pars[0];
        double pScale = pars[1];
        double fEq = pars[2];
        double r = pars[3];
        double rY = (rScale - fEq * rScale) * Math.exp(-r * x) + fEq * rScale;
        double pY = ((1.0 - fEq) * pScale) * (1.0 - Math.exp(-r * x));
        return new double[]{rY, pY};
    }
}
