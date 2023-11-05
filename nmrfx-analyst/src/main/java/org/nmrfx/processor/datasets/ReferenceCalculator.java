package org.nmrfx.processor.datasets;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import java.util.Map;

public class ReferenceCalculator {
    static Map<String, Double> ratiosAcqueous = Map.of("C", 0.251449530, "N", 0.101329118, "P", 0.404808636,
            "D", 0.15350608, "H", 1.0);
    static Map<String, Double> ratiosNonAcqueous = Map.of("C", 0.25145020, "N", 0.10136767, "P", 0.40480742,
            "D", 0.15350609, "H", 1.0);

    static double DSS_rel_TMS = -0.074;

    public static boolean hasRatio(String nucName) {
        return ratiosAcqueous.containsKey(nucName);
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
        double refPPM = h2OFunction.value(tempC) - dssFunction.value(tempC);
        double a = -0.009552;
        double b = 5.011718;
        double refPPM2 =  a * tempC + b;
        System.out.println(tempK + " " + tempC + " " + refPPM + " " + refPPM2);
        return refPPM;

    }

    public static double calcRef(double baseFreq, double offset, double lockPPM, String solvent, double tempK) {
        double actualLockPPM = getH2ORefPPM(tempK);
        return calcRef(baseFreq, offset, lockPPM, actualLockPPM);
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

    /*
    def refByRatio(refSF,refCenter,sf,nucleus):
    nucleus = nucleus.upper()
    ratios = {'C':0.251449530, 'N':0.101329118, 'P':0.404808636, 'D':0.15306088, 'H':1.0}
    refZero = refSF/(1.0+refCenter/1.0e6)
    zeroC = refZero*ratios[nucleus]
    refCenterC = (sf-zeroC)*1.0e6/zeroC
    return refCenterC

     */

    public static double refByRatio(double refSF, double refCenter, double sf, String nucleus, String solvent) {
        boolean isAcqueous = isAcqueous(solvent);
        double refZero = refSF / (1.0 + refCenter / 1.0e6);
        var map = isAcqueous ? ratiosAcqueous : ratiosNonAcqueous;
        Double ratio = map.get(nucleus);
        double zeroC = refZero * ratio;
        return (sf -zeroC) * 1.0e6 / zeroC;
    }

    public static boolean isAcqueous(String solvent) {
        solvent = solvent.toUpperCase();
        return solvent.startsWith("H2O") || solvent.startsWith("D2O")
                || solvent.substring(1).startsWith("H2O")
                || solvent.substring(1).startsWith("D2O")
                || solvent.contains("URINE")
                || solvent.contains("JUICE");
    }
}
