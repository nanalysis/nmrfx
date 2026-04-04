package org.nmrfx.processor.optimization;

import org.apache.commons.math3.optim.PointValuePair;
import org.nmrfx.processor.datasets.peaks.PeakListTools;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class ZZFit2 extends FitEquation {
    PeakListTools.ZZFitPars zzFitPars;
    int nGroups = 1;
    int nSigPar = 0;
    int nCommonPar = 0;

    public ZZFit2(PeakListTools.ZZFitPars zzFitPars) {
        this.zzFitPars = zzFitPars;
    }

    @Override
    public void setXYE(double[][] xValues, double[][] yValues, double[][] errValues) {
        super.setXYE(xValues, yValues, errValues);
        nGroups = yValues.length / 4;
    }

    @Override
    public String[] parNames() {
        List<String> parNames = new ArrayList<>();
        parNames.add("Ia+Ib");
        parNames.add("Ia/(Ia+Ib)");
        if (zzFitPars.fitInept()) {
            parNames.add("IDelay");
        }
        if (zzFitPars.constrainR1()) {
            parNames.add("R1");
        } else {
            parNames.add("R1A");
            parNames.add("R1B");
        }
        parNames.add("KAB");
        parNames.add("KBA");
        return  parNames.toArray(new String[parNames.size()]);
    }

    @Override
    public int nY() {
        return 4 * nGroups;
    }

    @Override
    public int nX() {
        return 1;
    }

    public int nSigPar() {
        return nSigPar;
    }

    public int nCommonPar() {
        return nCommonPar;
    }

    public Guesses guess() {
        nSigPar = 3;
        if (!zzFitPars.constrainR1()) {
            nSigPar++;
        }
        if (zzFitPars.fitInept()) {
            nSigPar++;
        }

         nCommonPar = 2;
        int nPar = nSigPar * nGroups + nCommonPar;

        double[] start = new double[nPar];
        double[] lower = new double[nPar];
        double[] upper = new double[nPar];

        int i = 0;
        double kEx = 0.0;

        for (int iGroup = 0;iGroup<nGroups;iGroup++) {
            double yMax0 = FitUtils.getMaxValue(yValues[0]);
            double yMax1 = FitUtils.getMaxValue(yValues[1]);
            double midX = FitUtils.getMidY0(xValues[0], yValues[0]);
            double midX2 = FitUtils.getMidY0(xValues[0], yValues[2]);
            double intensity = yMax0 + yMax1;
            double r1 = -Math.log(0.5) / midX;
            kEx = -Math.log(0.5) / midX2;
            double fracA = yMax0 / (yMax0 + yMax1);

            start[i] = intensity;
            lower[i] = intensity / 2.0;
            upper[i++] = intensity * 2.0;

            start[i] = fracA;
            lower[i] = 0.0;
            upper[i++] = 1.0;

            if (zzFitPars.fitInept()) {
                start[i] = 6.0e-3;
                lower[i] = 0.0;
                upper[i++] = 12.0e-3;
            }

            start[i] = r1;
            lower[i] = r1 / 10.0;
            upper[i++] = r1 * 2.0;

            if (!zzFitPars.constrainR1()) {
                start[i] = r1;
                lower[i] = r1 / 10.0;
                upper[i++] = r1 * 2.0;
            }
        }

        start[i] = kEx;
        lower[i] = kEx / 12.0;
        upper[i++] = kEx * 3.0;

        start[i] = kEx;
        lower[i] = kEx / 12.0;
        upper[i++] = kEx * 3.0;


        return new Guesses(start, lower, upper);
    }

    public double[] calcValue(double[] xA, double[] pars) {
        double delay = xA[0];
        int i = 0;
        double[] y = new double[4 * nGroups];
        for (int iGroup = 0; iGroup < nGroups; iGroup++) {
            double intensity = pars[i++];
            double initialRatio = pars[i++];
            double ineptCorr = zzFitPars.fitInept() ? pars[i++] : 0.0;
            double r1A = pars[i++];
            double r1B = zzFitPars.constrainR1() ? r1A : pars[i++];

            double kAB = pars[pars.length - 2];
            double kBA = pars[pars.length - 1];

            for (int iSig = 0; iSig < 4; iSig++) {
                y[iGroup * 4 + iSig] = intensity * LorentzGaussND.zzAmplitude2(r1A, r1B, initialRatio, kAB, kBA, delay + ineptCorr, iSig);
            }
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
