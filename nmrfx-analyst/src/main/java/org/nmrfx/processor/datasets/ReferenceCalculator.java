package org.nmrfx.processor.datasets;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.nmrfx.datasets.Nuclei;

public class ReferenceCalculator {

    private ReferenceCalculator() {

    }

    public static double getCorrectedBaseFreq(double baseFreq, double lockPPM, double actualLockPPM) {
        return baseFreq * (lockPPM + 1.0e6) / (actualLockPPM + 1.0e6);
    }

    public static double calcRef(double baseFreq, double offset, double lockPPM, double actualLockPPM) {
        double correctedBaseFreq = getCorrectedBaseFreq(baseFreq, lockPPM, actualLockPPM);
        return 1.0e6 * (baseFreq + offset / 1.0e6 - correctedBaseFreq) / correctedBaseFreq;
    }

    public static double calcRef(double correctedBaseFreq, double centerFreq) {
        return 1.0e6 * (centerFreq - correctedBaseFreq) / correctedBaseFreq;
    }

    public static double getH2ORefPPM(double tempK) {
        double[] coefficients = {4.995, -1.1685e-2, 2.433e-5, -5.61e-8, 0.0};
        double[] dssCoef = {-0.071, -9.9e-5, -5.9e-7, 0.0, 0.0};
        PolynomialFunction h2OFunction = new PolynomialFunction(coefficients);
        PolynomialFunction dssFunction = new PolynomialFunction(dssCoef);
        double tempC = tempK - 273.15;
        return h2OFunction.value(tempC) - dssFunction.value(tempC);
    }

     /*
    Actual measured frequency of lock signal: actual_lock_hz

actual_lock_hz = (lock_ppm/1e6)*bf1+bf1

Actual spectrometer field: actual_bf1

actual_lock_offset_hz = actual_lock_hz - actual_bf1
actual_lock_ppm = 1e6*actual_lock_offset_hz/actual_bf1

actual_bf1 = 1e6*actual_lock_offset_hz/actual_lock_ppm
actual_bf1 = 1e6*(actual_lock_hz - actual_bf1)/actual_lock_ppm

actual_bf1 + 1e6*actual_bf1/actual_lock_ppm = 1e6*actual_lock_hz / actual_lock_ppm

actual_bf1 = 1e6*(actual_lock_hz / actual_lock_ppm) / (1 + 1e6/actual_lock_ppm)

actual_bf1 = 1e6*((lock_ppm/1e6)*bf1+bf1)/actual_lock_ppm)/(1 + 1e6/actual_lock_ppm)

Simplifies to:

actual_bf1 =((lock_ppm+1e6)*bf1)/(actual_lock_ppm+1e6)

actual_bf1 = bf1*((lock_ppm+1e6)/(actual_lock_ppm+1e6))

Then actual center of the spectrum in ppm:

1e6*(bf1+o1 - actual_bf1)/actual_bf1
     */

    public static double refByRatio(double refSF, double refCenter, double sf, Nuclei nucleus, String solvent) {
        double refZero = refSF / (1.0 + refCenter / 1.0e6);
        return refByRatio(refZero, sf, nucleus, solvent);
    }

    public static double refByRatio(double refZero, double sf, Nuclei nucleus, String solvent) {
        boolean isAcqueous = isAcqueous(solvent);
        double ratio = isAcqueous ? nucleus.getRatioAcqueous() : nucleus.getRatio();
        double zeroC = refZero * ratio / 100.0;
        return (sf - zeroC) * 1.0e6 / zeroC;
    }

    public static boolean isAcqueous(String solvent) {
        solvent = solvent.toUpperCase();
        return ((solvent.indexOf("H2O") >= 0) && (solvent.indexOf("H2O") < 2))
                || ((solvent.indexOf("D2O") >= 0) && (solvent.indexOf("D2O") < 2))
                || solvent.contains("URINE")
                || solvent.contains("JUICE");
    }
}
