package org.nmrfx.processor.math;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author brucejohnson
 */
public class RelaxEquations {

    static final int S = 1;
    static final int ImS = 2;
    static final int I = 3;
    static final int IpS = 4;
    public final static double SQRT2 = Math.sqrt(2.0);

    public final static double MU0 = 4.0e-7 * Math.PI;
    public final static double GAMMA_N = -2.71e7;
    public final static double GAMMA_C = 6.72828e7;
    public final static double GAMMA_H = 2.68e8;

    public final static double PLANCK = 1.054e-34;
    public final static double R_HN = 1.02e-10;
    public final static double R_HC = 1.104e-10;
    public final static Map<String, Double> GAMMA_MAP = new HashMap<>();
    public final static Map<String, Double> R_MAP = new HashMap<>();
    public final static Map<String, Double> CSA_MAP = new HashMap<>();

    static {
        GAMMA_MAP.put("H", GAMMA_H);
        GAMMA_MAP.put("N", GAMMA_N);
        GAMMA_MAP.put("C", GAMMA_C);
        R_MAP.put("HN", R_HN);
        R_MAP.put("NH", R_HN);
        R_MAP.put("HC", R_HC);
        R_MAP.put("CH", R_HC);
        // from Berlin, K et al 2013 doi:10.1007/s10858-013-9791-1)
        CSA_MAP.put("H_N", 160.0);
        CSA_MAP.put("H1_N1", 130.0);
        CSA_MAP.put("H3_N3", 95.0);
        CSA_MAP.put("HA_CA", 30.0);
        CSA_MAP.put("H1'_C1'", 29.0);
        CSA_MAP.put("H2'_C2'", 23.1);
        CSA_MAP.put("H3'_C3'", 83.0);
        CSA_MAP.put("H4'_C4'", 79.5);
        CSA_MAP.put("H5'_C5'", 55.5);
        CSA_MAP.put("H5\"_C5'", 55.5);
        CSA_MAP.put("H2_C2", 150.0);
        CSA_MAP.put("H5_C5", 144.0);
        CSA_MAP.put("H6_C6", 180.0);
        CSA_MAP.put("H8_C8", 140.0);

    }

    private final double r;
    private final double d;
    private final double d2;
    private final double c;
    private final double c2;
    private final double gammaS;
    private final double gammaI;
    private final double sf;
    private final double wI;
    private final double wS;
    private final double sigma;

    //   consider using scaled versions (smaller exponents)

    /**
     * @param sf    double. 1H NMR Spectrometer frequency.
     * @param elem1 String. First element ("H" for 1H NMR).
     * @param elem2 String. Second element (C, N, etc.)
     */
    public RelaxEquations(double sf, String elem1, String elem2) {
        String nuc1 = elem1.substring(0, 1);
        String nuc2 = elem2.substring(0, 1);
        gammaI = GAMMA_MAP.get(nuc1);
        gammaS = GAMMA_MAP.get(nuc2);
        wI = sf * 2.0 * Math.PI;
        wS = wI * gammaS / gammaI;
        r = R_MAP.get(nuc1 + nuc2);
        d = MU0 * (gammaI * gammaS * PLANCK) / (4.0 * Math.PI * r * r * r);
        d2 = d * d;
        sigma = CSA_MAP.get(elem1 + "_" + elem2) * 1.0e-6;

        c = wS * sigma / Math.sqrt(3.0);
        c2 = c * c;

        this.sf = sf;
    }

    // Note: tauM = tm in Art Palmer's code, and taui in Relax. 

    /**
     * Model Free spectral density function, J(omega), calculation using Model
     * 1.
     *
     * @param w    double. The frequency, omega.
     * @param tauM double. The overall correlation time.
     * @param s2   double. The order parameter S^2.
     * @return J(w) value.
     */
    public double JModelFree(double w, double tauM, double s2) {
        double value1 = s2 / (1.0 + w * w * tauM * tauM);
        double value = 0.4 * tauM * (value1);
        return value;
    }

    // Note: tauM = tm in Art Palmer's code, and taui in Relax. 
    // tau = ts in Art Palmer's code (taue in the paper: Phys Chem Chem Phys, 2016, 18, 5839-5849), and taue in Relax.

    /**
     * Model Free spectral density function, J(omega), calculation using Model
     * 2.
     *
     * @param w    double. The frequency, omega.
     * @param tau  double. The internal correlation time.
     * @param tauM double. The overall correlation time.
     * @param s2   double. The order parameter S^2.
     * @return J(w) value.
     */
    public double JModelFree(double w, double tau, double tauM, double s2) {
        double value1 = s2 / (1.0 + w * w * tauM * tauM);
        double value2 = ((1.0 - s2) * (tau + tauM) * tau) / ((tau + tauM) * (tau + tauM) + w * w * tauM * tauM * tau * tau);
        double value = 0.4 * tauM * (value1 + value2);
        return value;
    }

    // Note: tauM = tm in Art Palmer's code. tau = ts in Art Palmer's code.

    /**
     * Model Free spectral density function, J(omega), calculation using Model
     * 5.
     *
     * @param w    double. The frequency, omega.
     * @param tau  double. The internal correlation time.
     * @param tauM double. The overall correlation time.
     * @param s2   double. The order parameter S^2.
     * @param sf2  double. The order parameter for intramolecular motions with
     *             fast correlation times.
     * @return J(w) value.
     */
    public double JModelFree(double w, double tau, double tauM, double s2, double sf2) {
        double value1 = s2 / (1.0 + w * w * tauM * tauM);
        double value2 = ((sf2 - s2) * (tau + tauM) * tau) / ((tau + tauM) * (tau + tauM) + w * w * tauM * tauM * tau * tau);
        double value = 0.4 * tauM * (value1 + value2);
        return value;
    }

    /**
     * Model Free spectral density function, J(omega), calculation using Model
     * 6.
     *
     * @param w    double. The frequency, omega.
     * @param tauF double. The internal fast correlation time.
     * @param tauM double. The overall correlation time.
     * @param tauS double. The internal slow correlation time.
     * @param s2   double. The order parameter S^2.
     * @param sf2  double. The order parameter for intramolecular motions with
     *             fast correlation times.
     * @return J(w) value.
     */
    public double JModelFree(double w, double tauF, double tauM, double tauS, double s2, double sf2) {
        double value1 = s2 / (1.0 + w * w * tauM * tauM);
        double value2 = ((1 - sf2) * (tauF + tauM) * tauF) / ((tauF + tauM) * (tauF + tauM) + w * w * tauM * tauM * tauF * tauF);
        double value3 = ((sf2 - s2) * (tauS + tauM) * tauS) / ((tauS + tauM) * (tauS + tauM) + w * w * tauM * tauM * tauS * tauS);
        double value = 0.4 * tauM * (value1 + value2 + value3);
        return value;
    }

    // Note: tauM = tm in Art Palmer's code, and taui in Relax. 

    /**
     * Model Free spectral density function, J(omega), calculations using Model
     * 1.
     *
     * @param tauM double. The overall correlation time.
     * @param s2   double. The order parameter S^2.
     * @return double[]. Array of J(w) values.
     */
    public double[] getJModelFree(double tauM, double s2) {
        double J0 = JModelFree(0.0, tauM, s2);
        double JIplusS = JModelFree(wI + wS, tauM, s2); //R1, R2, NOE
        double JIminusS = JModelFree(wI - wS, tauM, s2); // R1, R2, NOE
        double JI = JModelFree(wI, tauM, s2); // R2
        double JS = JModelFree(wS, tauM, s2); //R1, R2
        double[] result = {J0, JS, JIminusS, JI, JIplusS};
        return result;
    }

    // Note: tauM = tm in Art Palmer's code, and taui in Relax. tau = ts in Art Palmer's code (taue in the paper), and taue in Relax.

    /**
     * Model Free spectral density function, J(omega), calculations using Model
     * 2.
     *
     * @param tau  double. The internal correlation time.
     * @param tauM double. The overall correlation time.
     * @param s2   double. The order parameter S^2.
     * @return double[]. Array of J(w) values.
     */
    public double[] getJModelFree(double tau, double tauM, double s2) {
        double J0 = JModelFree(0.0, tau, tauM, s2);
        double JIplusS = JModelFree(wI + wS, tau, tauM, s2); //R1, R2, NOE
        double JIminusS = JModelFree(wI - wS, tau, tauM, s2); // R1, R2, NOE
        double JI = JModelFree(wI, tau, tauM, s2); // R2
        double JS = JModelFree(wS, tau, tauM, s2); //R1, R2
        double[] result = {J0, JS, JIminusS, JI, JIplusS};
        return result;
    }

    // Note: tauM = tm in Art Palmer's code. tau = ts in Art Palmer's code.

    /**
     * Model Free spectral density function, J(omega), calculations using Model
     * 5.
     *
     * @param tau  double. The internal correlation time.
     * @param tauM double. The overall correlation time.
     * @param s2   double. The order parameter S^2.
     * @param sf2  double. The order parameter for intramolecular motions with
     *             fast correlation times.
     * @return double[]. Array of J(w) values.
     */
    public double[] getJModelFree(double tau, double tauM, double s2, double sf2) {
        double J0 = JModelFree(0.0, tau, tauM, s2, sf2);
        double JIplusS = JModelFree(wI + wS, tau, tauM, s2, sf2); //R1, R2, NOE
        double JIminusS = JModelFree(wI - wS, tau, tauM, s2, sf2); // R1, R2, NOE
        double JI = JModelFree(wI, tau, tauM, s2, sf2); // R2
        double JS = JModelFree(wS, tau, tauM, s2, sf2); //R1, R2
        double[] result = {J0, JS, JIminusS, JI, JIplusS};
        return result;
    }

    /**
     * Model Free spectral density function, J(omega), calculations using Model
     * 6.
     *
     * @param tauF double. The internal fast correlation time.
     * @param tauM double. The overall correlation time.
     * @param tauS double. The internal slow correlation time.
     * @param s2   double. The order parameter S^2.
     * @param sf2  double. The order parameter for intramolecular motions with
     *             fast correlation times.
     * @return double[]. Array of J(w) values.
     */
    public double[] getJModelFree(double tauF, double tauM, double tauS, double s2, double sf2) {
        double J0 = JModelFree(0.0, tauF, tauM, tauS, s2, sf2);
        double JIplusS = JModelFree(wI + wS, tauF, tauM, tauS, s2, sf2); //R1, R2, NOE
        double JIminusS = JModelFree(wI - wS, tauF, tauM, tauS, s2, sf2); // R1, R2, NOE
        double JI = JModelFree(wI, tauF, tauM, tauS, s2, sf2); // R2
        double JS = JModelFree(wS, tauF, tauM, tauS, s2, sf2); //R1, R2
        double[] result = {J0, JS, JIminusS, JI, JIplusS};
        return result;
    }

    /**
     * Spectral density function calculation.
     *
     * @param w   double. The frequency, omega.
     * @param tau double. The correlation time.
     * @return double. The spectral density, J(w).
     */
    public double J(double w, double tau) {
        double value = 0.4 * tau / (1.0 + w * w * tau * tau);
        return value;
    }

    /**
     * Spectral density function calculation.
     *
     * @param w   double. The frequency, omega.
     * @param tau double. The correlation time.
     * @param S2  double. The order parameter.
     * @return double. The spectral density, J(w).
     */
    public double J(double w, double tau, double S2) {
        double value = 0.4 * S2 * tau / (1.0 + w * w * tau * tau);
        return value;
    }

    public double[] getDiffusionConstants(String type) {
        String[] types = {"sphere", "spheroid", "ellipsoid"};
        double[][] constants = {{1}};//, 
//            {0.25*(3.0*dz2 - 1)*(3.0*dz2 - 1), 3*dz2*(1 - dz2), 0.75*(3.0*dz2 - 1)*(3.0*dz2 - 1)},
//            {0.25*(dtot - e), 3*dy2*dz2, 3*dx2*dz2, 3*dx2*dy2, 0.25*(dtot + e)}};
        return constants[Arrays.asList(types).indexOf(type)];
    }

    public double[] getCorrelationTimes(String type, double Diso, double Da, double Dr) {
        String[] types = {"sphere", "spheroid", "ellipsoid"};
        double[][] tauInv = {{6 * Diso}};//, 
//            {6*Diso - 2*Da, 6*Diso - Da, 6*Diso + 2*Da},
//            {6*Diso - 2*Da*R, 6*Diso - Da*(1 + 3*Dr), 6*Diso - Da*(1 - 3*Dr), 6*Diso + 2*Da, 6*Diso + 2*Da*R}};
        int index = Arrays.asList(types).indexOf(type);
        double[] taus = new double[tauInv[index].length];
        for (int i = 0; i < taus.length; i++) {
            taus[i] = 1 / tauInv[index][i];
        }
        return taus;
    }

    public double calcS2(double J0, double bN, double mN) {
        return (5 / 2) * Math.sqrt((J0 - bN) * mN);
    }

    /**
     * Spectral density function calculations.
     *
     * @param tau double. The correlation time.
     * @return double[]. Array of J(w) values.
     */
    public double[] getJ(double tau) {
        double J0 = J(0.0, tau); // R2
        double JIplusS = J(wI + wS, tau); //R1, R2, NOE
        double JIminusS = J(wI - wS, tau); // R1, R2, NOE
        double JI = J(wI, tau); // R2
        double JS = J(wS, tau); //R1, R2
        double[] result = {J0, JS, JIminusS, JI, JIplusS};
        return result;
    }

    /**
     * Spectral density function calculations.
     *
     * @param tau double. The correlation time.
     * @param S   double. The order parameter.
     * @return double[]. Array of J(w) values.
     */
    public double[] getJ(double tau, double S) {
        double[] result = getJ(tau);
        for (int i = 0; i < result.length; i++) {
            result[i] *= S;
        }
        return result;
    }

    /**
     * R2:R1 ratio calculation.
     *
     * @param tau double. The correlation time.
     * @return double. R2/R1 value.
     */
    public double r2r1Ratio(double tau) {
        double num = 4.0 * J(0, tau) + J(wS - wI, tau) + 3.0 * J(wS, tau)
                + 6.0 * J(wI, tau) + 6.0 * J(wS + wI, tau)
                + (c2 / (3.0 * d2) * (4.0 * J(0, tau) + 3.0 * J(wS, tau)));

        double denom = 2.0 * J(wS - wI, tau) + 6.0 * J(wS, tau) + 12.0 * J(wS + wI, tau) + 2.0 * (c2 / (3.0 * d2) * J(wS, tau));
        return num / denom;
    }

    /**
     * R1 calculation
     *
     * @param tau double. The correlation time.
     * @return double. R1 value.
     */
    public double R1(double tau) {
        double dipolarContrib = d2 / 4.0 * (J(wI - wS, tau) + 3.0 * J(wS, tau) + 6.0 * J(wI + wS, tau));
        double csaContrib = c2 * J(wS, tau);
        return dipolarContrib + csaContrib;
    }

    /**
     * R2 calculation
     *
     * @param tau double. The correlation time.
     * @return double. R2 value.
     */
    public double R2(double tau) {
        double dipolarContrib = d2 / 8.0 * (4.0 * J(0.0, tau) + J(wI - wS, tau) + 3.0 * J(wS, tau)
                + 6.0 * J(wI, tau) + 6.0 * J(wI + wS, tau));
        double csaContrib = c2 / 6 * (4.0 * J(0.0, tau) + 3.0 * J(wS, tau));
        return dipolarContrib + csaContrib;
    }

    /**
     * NOE calculation
     *
     * @param tau double. The correlation time.
     * @return double. NOE value.
     */
    public double NOE(double tau) {
        double R1 = R1(tau);
        return 1.0 + (d2 / (4.0 * R1)) * (gammaI / gammaS)
                * (6.0 * J(wI + wS, tau) - J(wI - wS, tau));
    }

    /**
     * R1 calculation
     *
     * @param J double[]. Array of spectral density function values, J(w).
     * @return double. R1 value.
     */
    public double R1(double[] J) {
        double dipolarContrib = d2 / 4.0 * (J[ImS] + 3.0 * J[S]
                + 6.0 * J[IpS]);
        double csaContrib = c2 * J[S];
        return dipolarContrib + csaContrib;
    }

    /**
     * R2 calculation
     *
     * @param J   double[]. Array of spectral density function values, J(w).
     * @param Rex double. Rate of exchange, Rex, value.
     * @return double. R2 value.
     */
    public double R2(double[] J, double Rex) {
        double dipolarContrib = d2 / 8.0 * (4.0 * J[0] + J[ImS]
                + 3.0 * J[S]
                + 6.0 * J[I] + 6.0 * J[IpS]);
        double csaContrib = c2 / 6 * (4.0 * J[0] + 3.0 * J[S]);
        return dipolarContrib + csaContrib + Rex;
    }

    /**
     * NOE calculation
     *
     * @param J double[]. Array of spectral density function values.
     * @return double. NOE value.
     */
    public double NOE(double[] J) {
        double R1 = R1(J);
        return 1.0 + (d2 / (4.0 * R1)) * (gammaI / gammaS)
                * (6.0 * J[IpS] - J[ImS]);
    }

    /**
     * Sigma calculation
     *
     * @param J double[]. Array of spectral density function values.
     * @return double. Sigma value.
     */
    public double sigmaSI(double[] J) {
        double R1 = R1(J);
        double NOE = NOE(J);
        return R1 * (NOE - 1) * (gammaS / gammaI);
    }

    /**
     * Gamma calculation
     *
     * @param J   double[]. Array of spectral density function values, J(w).
     * @param Rex double. Rate of exchange, Rex, value.
     * @return double. Gamma value.
     */
    public double gamma(double[] J, double Rex) {
        double R1 = R1(J);
        double R2 = R2(J, Rex);
        double sigmaSI = sigmaSI(J);
        return R2 - 0.5 * R1 - 0.454 * sigmaSI;
    }

    public double TRACTdeltaAlphaBeta(double tauC) {
        return TRACTdeltaAlphaBeta(tauC, sigma, 17.0);
    }

    public double TRACTdeltaAlphaBeta(double tauC, double sigmaVal, double theta) {
        double B0 = wI / GAMMA_H;

        double p = MU0 * gammaI * gammaS * PLANCK / (8.0 * Math.PI * SQRT2 * r * r * r);
        double dN = gammaS * B0 * sigmaVal / (3.0 * SQRT2);

        double cosTheta = Math.cos(theta * Math.PI / 180.0);
        double[] J = getJ(tauC);
        double nuxy2 = 2.0 * p * dN * (4.0 * J[0] + 3.0 * J[S]) * (3.0 * cosTheta * cosTheta - 1.0);
        return nuxy2;
    }

}
