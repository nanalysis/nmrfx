package org.nmrfx.processor.optimization;

import org.apache.commons.math3.optim.PointValuePair;
import org.nmrfx.processor.datasets.peaks.PeakListTools;

/**
 * @author brucejohnson
 */
public class ZZFit2 extends FitEquation {
    PeakListTools.ZZFitPars zzFitPars;
    public ZZFit2(PeakListTools.ZZFitPars zzFitPars) {
        this.zzFitPars = zzFitPars;
    }
    @Override
    public String[] parNames() {
        if (zzFitPars.constrainR1()) {
            if (zzFitPars.fitInept()) {
                return new String[]{"Ia+Ib", "Ia/(Ia+Ib)", "R1", "KAB", "KBA", "IDelay"};
            } else {
                return new String[]{"Ia+Ib", "Ia/(Ia+Ib)", "R1", "KAB", "KBA"};
            }
        } else {
            if (zzFitPars.fitInept()) {
                return new String[]{"Ia+Ib", "Ia/(Ia+Ib)", "R1A", "R1B", "KAB", "KBA", "IDelay"};
            } else {
                return new String[]{"Ia+Ib", "Ia/(Ia+Ib)", "R1A", "R1B", "KAB", "KBA"};
            }
        }
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
        int nPar = 5;
        if (!zzFitPars.constrainR1()) {
            nPar++;
        }
        if (zzFitPars.fitInept()) {
            nPar++;
        }
        double[] start = new double[nPar];
        double[] lower = new double[nPar];
        double[] upper = new double[nPar];

        start[0] = intensity;
        lower[0] = intensity / 2.0;
        upper[0] = intensity * 2.0;
        start[1] = pA;
        lower[1] = 0.0;
        upper[1] = 1.0;
        start[2] = r1;
        lower[2] = r1 / 10.0;
        upper[2] = r1 * 2.0;
        int i = 2;
        if (!zzFitPars.constrainR1()) {
            i++;
            start[i] = r1;
            lower[i] = r1 / 10.0;
            upper[i] = r1 * 2.0;
        }
        i++;
        start[i] = kEx;
        lower[i] = kEx / 12.0;
        upper[i] = kEx * 3.0;
        i++;
        start[i] = kEx;
        lower[i] = kEx / 12.0;
        upper[i] = kEx * 3.0;
        if (zzFitPars.fitInept()) {
            i++;
            start[i] = 6.0e-3;
            lower[i] = 0.0;
            upper[i] = 12.0e-3;
        }

        return new Guesses(start, lower, upper);
    }

    public double[] calcValue(double[] xA, double[] pars) {
        double delay = xA[0];
        int i = 0;
        double intensity = pars[i++];
        double initialRatio = pars[i++];
        double r1A = pars[i++];
        double r1B = zzFitPars.constrainR1() ? r1A : pars[i++];
        double kAB = pars[i++];
        double kBA = pars[i++];
        double ineptCorr = zzFitPars.fitInept() ? pars[i] : 0.0;

        double[] y = new double[4];
        for (int iSig = 0; iSig < 4; iSig++) {
            y[iSig] = intensity * LorentzGaussND.zzAmplitude2(r1A, r1B, initialRatio, kAB, kBA, delay + ineptCorr, iSig);
        }
        return y;
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
