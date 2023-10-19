package org.nmrfx.processor.optimization;

import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * @author brucejohnson
 */
public class ZZFitRatio extends FitEquation {
    static final String[] parNames = {"KK"};

    @Override
    public String[] parNames() {
        return parNames;
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

        double xMax = FitUtils.getMaxValue(xValues[0]);
        DescriptiveStatistics dStat = new DescriptiveStatistics();
        for (int i=0;i<xValues[0].length;i++) {
            double delay = xValues[0][i];
            if (delay > xMax / 10.0) {
                double v = yValues[0][i] / (delay * delay);
                dStat.addValue(v);
            }
        }
        double median = dStat.getPercentile(50);

        double[] start = {median};
        double[] lower = {0.0};
        double[] upper = {median * 5.0};
        return new Guesses(start, lower, upper);
    }

    public double[] calcValue(double[] xA, double[] pars) {
        double delay = xA[0];
        double kk = pars[0];
        return new double[]{kk * delay * delay};
    }

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
                for (int i = 0; i < guesses.lower().length; i++) {
                    System.out.printf("%.3f %.3f %.3f %.3f %.3f\n", guesses.lower()[i], guesses.start()[i], guesses.upper()[i], bestPars[i], parErrs[i]);
                }
                return result;
            }
        }
        return null;
    }

}
