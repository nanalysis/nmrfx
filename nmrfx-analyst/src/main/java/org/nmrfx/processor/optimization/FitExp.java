package org.nmrfx.processor.optimization;

/**
 * @author brucejohnson
 */
public class FitExp extends FitEquation {
    final boolean fitC;
    public FitExp() {
        this(false);
    }

    public FitExp(boolean fitC) {
        this.fitC = fitC;
        this.nY = 0;
    }

    @Override
    public String[] parNames() {
        int nPar = 1 + nY;
        if (fitC) {
            nPar += nY;
        }
        String[] parNames = new String[nPar];
        for (int i = 0; i < nY; i++) {
            parNames[i] = "A" + (i+1);
            if (fitC) {
                parNames[i + nY + 1] = "C" + (i + 1);
            }
        }
        parNames[nY] = "B";

        return  parNames;
    }

    @Override
    public int nY() {
        return nY;
    }
    @Override
    public int nX() {
        return 1;
    }

    public Guesses guess() {
        int nY = yValues.length;
        double[] yStarts = new double[nY];
        double[] yEnds = new double[nY];
        double sumB = 0.0;
        for (int i = 0; i < nY; i++) {
            yStarts[i] = FitUtils.getYAtMinX(xValues[0], yValues[i]);
            yEnds[i] = FitUtils.getYAtMaxX(xValues[0], yValues[i]);
            double midX = FitUtils.getMidY0(xValues[0], yValues[i]);
            sumB += -Math.log(0.5) / midX;
        }
        double b = sumB / nY;
        int nPar = 1 + nY;
        if (fitC) {
            nPar += nY;
        }
        double[] start = new double[nPar];
        double[] lower = new double[nPar];
        double[] upper = new double[nPar];
        for (int i = 0; i < nY; i++) {
            start[i] = yStarts[i];
            lower[i] = yStarts[i] / 5.0;
            upper[i] = yStarts[i] * 3.0;
            if (fitC) {
                start[i + nY + 1] = yEnds[i];
                lower[i + nY + 1] = yEnds[i] / 5.0;
                upper[i + nY + 1] = yEnds[i] * 3.0;
            }
        }
        start[nY] = b;
        lower[nY] = b / 2.0;
        upper[nY] = b * 3.0;

        return new Guesses(start, lower, upper);

    }

    public double[] calcValue(double[] xA, double[] pars) {
        double x = xA[0];
        double[] y = new double[nY];
        for (int i = 0;i<nY;i++) {
            double a = pars[i];
            double b = pars[nY];
            double c = 0.0;
            if (fitC) {
                c = pars[i + nY + 1];
            }
            y[i] =  (a - c) * Math.exp(-b * x) + c;
        }
        return y;
    }

}
