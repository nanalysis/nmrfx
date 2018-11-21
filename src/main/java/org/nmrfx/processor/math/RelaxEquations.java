package org.nmrfx.processor.math;

/**
 *
 * @author brucejohnson
 */
public class RelaxEquations {

    public final static double MU0 = 4.0e-7 * Math.PI;
    public final static double GAMMA_N = -2.713e7;
    public final static double GAMMA_H = 2.6752e8;
    public final static double H = 6.626070040e-34;
    public final static double H_BAR = H / (2.0 * Math.PI);
    public final static double SQRT2 = Math.sqrt(2.0);

    double r = 1.02e-10;
    // don't use this yet.  Various inconsistencies with various different presentations of equations
    //   consider using scaled versions (smaller exponents)

    public RelaxEquations() {

    }

    public double JModelFree(double w, double tau, double taui, double s2) {
        double value1 = s2 / (1.0 + w * w * taui * taui);
        double value2 = ((1.0 - s2) * (tau + taui) * tau) / ((tau + taui) * (tau + taui) + w * w * taui * taui * tau * tau);
        double value = 0.4 * taui * (value1 + value2);
        return value;
    }

    public double J(double w, double tau) {
        double value = 0.4 * tau / (1.0 + w * w * tau * tau);
        return value;
    }

    public double J0(double tau) {
        double value = 0.4 * tau;
        return value;
    }

    public double T1(double tau) {
        double wH = 800.0e6 * 2.0 * Math.PI;
        double wN = Math.abs(wH * GAMMA_N / GAMMA_H);
        double A = (J(wH - wN, tau) + 3.0 * J(wN, tau) + 6.0 * J(wH + wN, tau));
        double d = MU0 * (GAMMA_H * GAMMA_N * H_BAR) / (4.0 * Math.PI * r * r * r);
        double d2 = 0.1 * d * d;

        double invT1 = d2 * A;
        return invT1;

    }

    public double T2(double tau) {
        double wH = 800.0e6 * 2.0 * Math.PI;
        double wN = Math.abs(wH * GAMMA_N / GAMMA_H);
        double A = (4.0 * J(0.0, tau) + J(wH - wN, tau) + 3.0 * J(wN, tau) + 6.0 * J(wH, tau) + 6.0 * J(wH + wN, tau));
        double d = MU0 * (GAMMA_H * GAMMA_N * H_BAR) / (4.0 * Math.PI * r * r * r);
        double d2 = 0.1 * d * d;

        double invT1 = 0.5 * d2 * A;
        return invT1;

    }

    public double T2s(double tau) {
        double A = (1.0 / Math.pow((2.0 * Math.PI), 2)) * Math.pow(MU0 / (4.0 * Math.PI), 2);
        double x = Math.pow(GAMMA_N * GAMMA_H * H_BAR, 2) / Math.pow(r, 6);
        double invT2 = A * x * tau;
        return invT2;
    }

    public double TRACTdeltaAlphaBeta(double B0, double tauC) {
        double ddN = 160.0e-6;
        double theta = 17.0 * Math.PI / 180.0;
        double p = MU0 * GAMMA_H * GAMMA_N * H / (16.0 * Math.PI * Math.PI * SQRT2 * r * r * r);
        double dN = GAMMA_N * B0 * ddN / (3.0 * SQRT2);
        double wN = GAMMA_N * B0;

        double cosTheta = Math.cos(theta);
        double nuxy2 = 2.0 * p * dN * (4.0 * J0(tauC) + 3.0 * J(wN, tauC)) * (3.0 * cosTheta * cosTheta - 1.0);
        return nuxy2;
    }
}
