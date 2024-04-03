package org.nmrfx.processor.math;

import org.apache.commons.math3.optim.PointValuePair;
import org.nmrfx.processor.optimization.Fitter;

import java.util.stream.IntStream;

/**
 * @author brucejohnson
 */
public class TRACTSimFit {

    boolean reportFitness = true;
    int reportAt = 10;
    long startTime = 0;
    double rA;
    double rB;
    double[][] xValues;
    double[] yValues;
    double[] errValues;
    double[] bestPars;
    double[] parErrs;
    RelaxEquations relaxEquations;

    public TRACTSimFit(double sf, String elemI, String elemS) {
        relaxEquations = new RelaxEquations(sf, elemI, elemS);
    }

    public void setXYE(double[][] xValues, double[] yValues, double[] errValues) {
        this.xValues = xValues;
        this.yValues = yValues;
        this.errValues = errValues;
    }

    public double[][] getSimValues(double first, double last, int n, boolean adjust) {
        double[][] result = new double[2][n];
        double delta = (last - first) / (n - 1);
        double a0 = bestPars[0];
        double r0 = bestPars[1];
        double a1 = bestPars[2];
        double tauC = bestPars[3];
        double a = a0;
        double r = r0;
        if (adjust) {
            a = a1;
            double nab2 = relaxEquations.TRACTdeltaAlphaBeta(tauC * 1.0e-9);
            r = r - nab2;
        }
        for (int i = 0; i < n; i++) {
            double x = first + delta * i;
            double y = a * Math.exp(-r * x);
            result[0][i] = x;
            result[1][i] = y;
        }
        return result;
    }

    public double value(double[] pars, double[][] values) {
        double a0 = pars[0];
        double r0 = pars[1];
        double a1 = pars[2];
        double tauC = pars[3];
        int n = values[0].length;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double r;
            double a;
            if (values[1][i] < 0.5) {
                r = r0;
                a = a0;
            } else {
                double nab2 = relaxEquations.TRACTdeltaAlphaBeta(tauC * 1.0e-9);
                r = r0 - nab2;
                a = a1;

            }
            double y = a * Math.exp(-r * values[0][i]);
            double delta = y - values[2][i];
            sum += delta * delta;
        }
        return Math.sqrt(sum / n);

    }

    public double getR1(double[] pars) {
        double r0 = pars[1];
        double tauC = pars[3];
        double nab2 = relaxEquations.TRACTdeltaAlphaBeta(tauC * 1.0e-9);
        double r1 = r0 - nab2;
        return r1;
    }

    public double[] getPars() {
        return bestPars;
    }

    public double[] getParErrs() {
        return parErrs;
    }

    public PointValuePair fit() {
        double max0 = IntStream.range(0, yValues.length).filter(i -> (i % 2) == 0).mapToDouble(i -> yValues[i]).max().getAsDouble();
        double max1 = IntStream.range(0, yValues.length).filter(i -> (i % 2) == 1).mapToDouble(i -> yValues[i]).max().getAsDouble();
        double halfMax = max0 / 2.0;
        double midDelta = Double.MAX_VALUE;
        double midX = 0.0;
        for (int i = 0; i < yValues.length; i += 2) {
            double y = yValues[i];
            double delta = Math.abs(y - halfMax);
            if (delta < midDelta) {
                midDelta = delta;
                midX = xValues[0][i];
            }
        }
        double r0 = -Math.log(0.5) / midX;
        Fitter fitter = Fitter.getArrayFitter(this::value);
        double[][] xValues2 = {xValues[0], xValues[1]};
        fitter.setXYE(xValues2, yValues, errValues);
        double[] start = {max0, r0, max1, 10.0};
        double[] lower = {max0 / 2.0, r0 / 2.0, max1 / 2.0, 1.0};
        double[] upper = {max0 * 2.0, r0 * 2.0, max1 * 2.0, 100.0};
        try {
            PointValuePair result = fitter.fit(start, lower, upper, 10.0,3);
            bestPars = result.getPoint();
            parErrs = fitter.bootstrap(result.getPoint(), 300);
            return result;
        } catch (Exception ex) {
            return null;
        }

    }
}
