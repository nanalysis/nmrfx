package org.nmrfx.processor.optimization;

/**
 * @author brucejohnson
 */
public class FitExp extends FitEquation {
    final boolean fitC;
    static final String[] abParNames = {"A", "B"};
    static final String[] abcParNames = {"A", "B", "C"};

    public FitExp() {
        this(false);
    }

    public FitExp(boolean fitC) {
        this.fitC = fitC;
    }

    @Override
    public String[] parNames() {
        return fitC ? abcParNames : abParNames;
    }

    @Override
    public int nY() {
        return 1;
    }

    @Override
    public int nX() {
        return 1;
    }

    public Guesses guess() {
        double yStart = FitUtils.getYAtMinX(xValues[0], yValues[0]);
        double yEnd = FitUtils.getYAtMaxX(xValues[0], yValues[0]);
        double midX = FitUtils.getMidY0(xValues[0], yValues[0]);
        double b = -Math.log(0.5) / midX;
        double[] start;
        double[] lower;
        double[] upper;
        if (fitC) {
            start = new double[]{yStart, b, yEnd};
            lower = new double[]{yStart / 5.0, b / 2.0, yEnd / 5.0};
            upper = new double[]{yStart * 3.0, b * 3.0, yEnd * 3.0};
        } else {
            start = new double[]{yStart, b};
            lower = new double[]{yStart / 5.0, b / 2.0};
            upper = new double[]{yStart * 3.0, b * 3.0};
        }
        return new Guesses(start, lower, upper);

    }

    public double[] calcValue(double[] xA, double[] pars) {
        double x = xA[0];
        double a = pars[0];
        double b = pars[1];
        double c = fitC ? pars[2] : 0.0;
        double y = (a - c) * Math.exp(-b * x) + c;
        return new double[]{y};
    }
}
