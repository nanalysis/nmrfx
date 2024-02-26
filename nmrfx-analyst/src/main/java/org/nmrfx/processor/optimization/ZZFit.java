package org.nmrfx.processor.optimization;

import org.apache.commons.math3.optim.PointValuePair;

/**
 * @author brucejohnson
 */
public class ZZFit extends FitEquation {
    static final String[] parNames = {"I", "R1", "Kex", "P"};

    @Override
    public String[] parNames() {
        return parNames;
    }

    @Override
    public int nY() {
        return 4;
    }

    @Override
    public int nX() {
        return 1;
    }

    public Guesses guess() {
        double yMax0 = FitUtils.getMaxValue(yValues[0]);
        double yMax1 = FitUtils.getMaxValue(yValues[1]);
        double midX = FitUtils.getMidY0(xValues[0], yValues[0]);
        double midX2 = FitUtils.getMidY0(xValues[0], yValues[2]);
        double intensity = yMax0 + yMax1;
        double r1 = -Math.log(0.5) / midX;
        double kEx = -Math.log(0.5) / midX2;
        double pA = yMax0 / (yMax0 + yMax1);
        double[] start = {intensity, r1, kEx, pA};
        double[] lower = {intensity / 2.0, r1 / 2.0, kEx / 3.0, 0.0};
        double[] upper = {intensity * 2.0, r1 * 2.0, kEx * 3.0, 1.0};
        return new Guesses(start, lower, upper);
    }

    public double[] calcValue(double[] xA, double[] pars) {
        double delay = xA[0];
        double intensity = pars[0];
        double r1 = pars[1];
        double kEx = pars[2];
        double pA = pars[3];
        double[] y = new double[4];
        for (int iSig = 0; iSig < 4; iSig++) {
            y[iSig] = intensity * LorentzGaussND.zzAmplitude(r1, pA, kEx, delay, iSig);
        }
        return y;
    }

    @Override
    public PointValuePair fit() {
        Fitter2 fitter = Fitter2.getArrayFitter(this::value);
        fitter.setXYE(xValues, yValues, errValues);
        var guesses = guess();
        var optResult = fitter.fit(guesses.start(), guesses.lower(), guesses.upper(), 10.0);
        if (optResult.isPresent()) {
            PointValuePair result = optResult.get();
            bestPars = result.getPoint();
            var errResult = fitter.bootstrap(result.getPoint(), 100);
            if (errResult.isPresent()) {
                parErrs = errResult.get();
                return result;
            }
        }
        return null;
    }

}
