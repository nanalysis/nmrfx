package org.nmrfx.analyst.dataops;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.nmrfx.processor.math.PositionValue;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 */
public class VecCorrelation {

    private static final Logger log = LoggerFactory.getLogger(VecCorrelation.class);


    /**
     * ****************************************************************************
     * *
     * Aligns chromatographic profiles P and T using correlation optimized *
     * warping (COW) * *
     * ****************************************************************************
     */
    public static int[] cow(Vec src, Vec target, int m, int t) {
        int nwl = 1;
        int x, u;
        double fsum;
        double popr, decimal;
        int pl, ph;
        /* Get - section length           m
         - slack                    t
         - chromatographic profile  P
         - length of P              LP
         - target profile           T
         - length of T              LT
         - number of wavelengths    nwl */

        double[] T = target.getReal();
        int LT = target.getSize() - 1;  //The target profile: 0,1,...,LT, therefore, target.size = LT + 1

        double[] P = src.getReal();
        int LP = src.getSize() - 1; //The unligned profile: 0,1,...,LP

        //P_aligned is used for Output:
        double[] P_aligned = new double[LT];

        /* Calculate variable values derived from m and t */
        if (LT < LP) {
            throw new IllegalArgumentException("LT<LP");
        }
        int N = LP / m;
        double exactN = (double) LP / (double) m;
        int delta = (int) ((double) LT / exactN + 0.5) - m;
        int residual = LT - (int) ((double) LT / exactN + 0.5) * N;
        LT = LT - residual;
        System.out.println(N + " " + delta);
        /* Allocate memory for matrices and vectors.
         Due to memory limitations F has only two rows
         The value in F may be re-writen time and time again, until finding the optimal u for each position x.*/
        double[][] F = new double[2][LT + 1];
        int[][] U = new int[N + 1][LT + 1]; //Matrix U: The optimal u for each candidate node position x

        //A total of N section, N+1 nodes.
        int[] uopt = new int[N];
        int[] xopt = new int[N + 1];


        /* Perform the dynamic programming.
         cowCorr is a function, returning the benefit function value*/
        for (int i = 0; i < LT + 1; i++) {
            F[0][i] = Double.NEGATIVE_INFINITY; //F[0][i] keeps the final values of benifit function.
            F[1][i] = Double.NEGATIVE_INFINITY;
        }
        for (int i = 0; i <= N; i++) {
            for (int j = 0; j <= LT; j++) {
                U[i][j] = -m - 1;
            }
        }
        F[0][LT] = 0.;//The benifit function for the final node is zero (start point of the backward dynamic programming).
        for (int i = N - 1; i >= 0; i--) {   //i is the node number, which is from N-1 downto 1
            for (x = 0; x <= LT; x++) {
                F[1][x] = F[0][x]; //F[1,x]=F[0,x]
                F[0][x] = Double.NEGATIVE_INFINITY; //Clear the final values of benifit function.
            }
            //Decide the range of node i,
            int xstart = (i) * (m + delta - t);
            int xend = (i) * (m + delta + t);
            System.out.println(xstart + " " + (LT - (N - i) * (m + delta + t)) + " " + xend + " " + (LT - (N - i) * (m + delta - t)));
            if (xstart < LT - (N - i) * (m + delta + t)) {
                xstart = LT - (N - i) * (m + delta + t);
            }
            if (xend > LT - (N - i) * (m + delta - t)) {
                xend = LT - (N - i) * (m + delta - t);
            }
            int sectionStart = i * m;
            System.out.println(xstart + " " + xend);
            for (x = xstart; x <= xend; x++) {
                for (u = delta - t; u <= delta + t; u++) {
                    if (((0 <= x + m + u) && (x + m + u <= LT)) && (x <= x + m + u) && (F[1][x + m + u] > -N)) {
                        double cowCorr = cowCorr(P, T, x, m, u, sectionStart, nwl);
                        System.out.println(x + " " + m + " " + u + " " + i + " " + cowCorr);
                        fsum = F[1][x + m + u] + cowCorr;
                        if (fsum > F[0][x]) {
                            F[0][x] = fsum;
                            U[i][x] = u;
                            /*U[i,x]=u, the value 'u'that gives the highest correlation
                             value is saved in matrix U on position (i,x) */

                        }
                    }
                }
            }
        }

        /* Reconstruct optimal solution */
        for (x = 0; x <= LT; x++) {
            if (U[0][x] > -m - 1) //Check if the start point of the first section of optimal solution is not in 0.
            {
                xopt[0] = x;
                break;
            }
        }

        for (int i = 0; i < N; i++) {
            uopt[i] = U[i][xopt[i]]; //That's the optimum u for node i when the position is xopt[i]
            xopt[i + 1] = xopt[i] + m + uopt[i];
        }

        /* Warping the unaligned profile into an aligned one. */
        for (int i = 0; i < N; i++) { //i denotes the section number: 1,2,...,N
            int xstart1 = i * m; //LP
            int xend1 = xstart1 + m;
            int xstart2 = xopt[i]; //LT
            int xend2 = xopt[i + 1];
            int dj = xend2 - xstart2;
            // fixme double dX = ((double) (xend1-xstart1))/(xend2-xstart2);
            for (int j = 0; j < dj; j++) {
                // fixme double p = j*dX+xstart1;

                popr = (double) (j) * (double) (m) / (double) (xend2 - xstart2);// xend1-xstart=m

                if (Math.floor(popr) < 0) {
                    popr = 0.;
                }
                if (Math.ceil(popr) > m) {
                    popr = m;
                }

                decimal = popr - (double) Math.floor(popr);
                pl = (int) (i * m + Math.floor(popr));//xstart1=i*m
                ph = (int) (i * m + Math.ceil(popr));
                P_aligned[xstart2 + j] = (1.0 - decimal) * P[pl] + decimal * P[ph];
            }

        }
        for (int i = 0; i < LT; i++) {
            target.set(i, P_aligned[i]);
        }
        return xopt;
    }

    public static double cowCorr(Vec fixedVec, Vec movingVec, int moveStart, int m, int iWarp, int tStart) {
        double[] movingData = movingVec.getReal();
        double[] fixedData = fixedVec.getReal();
        int nwl = 1;
        int t = 0;
        double cowCorr = cowCorr(fixedData, movingData, moveStart, m, iWarp, tStart, nwl);
        return cowCorr;

    }

    static double cowCorr(double[] fixD, double[] moveD, int moveStart, int m, int u, int sectionStart, int nwl) {
        int nelm, j, k, pl, ph;
        double popr, frak, m1, m2, mid1, mid2, mid1x1, mid2x2, mid1x2, res;
        /* Initialize variables */
        nelm = (m + u) * nwl;
        mid1 = 0.;
        mid2 = 0.;
        mid1x1 = 0.;
        mid2x2 = 0.;
        mid1x2 = 0.;
        /* Calculate correlation coefficient after warping
         position after warping         j
         position before warping        popr */
 /*w1=(i-1)*m+1; w2=i*m+1 (unaligned)
         wt=T(x:x+m+u)  (Target profile)
         ws=P(w1:w2)
         Warping=>ws2:  j=0~m+u; popr=j/(m+u)*(m)+ w1;
         Correlation: cc=corr(wt,ws2)
         */
        for (j = 0; j < m + u; j++) {
            popr = (double) (j * m) / (double) (m + u);

            if (Math.floor(popr) < 0) {
                popr = 0.;
            }
            if (Math.ceil(popr) > m) {
                popr = m;
            }

            frak = popr - Math.floor(popr);
            pl = (int) (sectionStart + Math.floor(popr));
            ph = (int) (sectionStart + Math.ceil(popr));
            for (k = 0; k < nwl; k++) {
                m1 = moveD[(moveStart + j) * nwl + k]; //wt(x+j)
                m2 = (1 - frak) * fixD[pl * nwl + k] + frak * fixD[ph * nwl + k]; //Linear interpolation => ws2(x+j)
                mid1 += m1;
                mid2 += m2;
                mid1x1 += m1 * m1;
                mid2x2 += m2 * m2;
                mid1x2 += m1 * m2;
            }
        }
        if (mid1x1 == mid1 * mid1) {
            res = 1.0;
        } else {
            res = (mid1x2 - (mid1 * mid2) / nelm) / Math.sqrt((mid1x1 - (mid1 * mid1) / nelm) * (mid2x2 - (mid2 * mid2) / nelm));
        }
        return (res);
    }

    public static void unifyVarByBin(double[] vecX, double[] vecY, int bin_size) {
        double[] vecW = new double[vecX.length];
        for (int i = 0; i < vecW.length; i++) {
            vecW[i] = 1.0;
        }
        unifyVarByBin(vecX, vecY, vecW, bin_size);
    }

    public static Complex[] getConjugate(Complex[] cvec) {   //get Conjugate of complex vec
        Complex[] cvec_new = new Complex[cvec.length];
        for (int i = 0; i < cvec.length; i++) {
            cvec_new[i] = cvec[i].conjugate();
        }
        return cvec_new;
    }

    public static double[] getMagnitude(Complex[] cvec) { //get magnitude of complex vec
        double[] cvec_new = new double[cvec.length];
        for (int i = 0; i < cvec.length; i++) {
            cvec_new[i] = cvec[i].abs();
        }
        return cvec_new;
    }

    public static void standardize(double[] vecX, int n) {
        if (n > vecX.length) {
            throw new IllegalArgumentException("vecX length less than n");
        }
        double sumX = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += vecX[i];
        }
        double meanX = sumX / n;
        double varX = variance(vecX, n);
        double sdev = Math.sqrt(varX);
        for (int i = 0; i < n; i++) {
            vecX[i] = (vecX[i] - meanX) / sdev;
        }
    }

    public static double variance(double[] vecX, double[] vecW) {

        int n = vecX.length;
        if (n != vecW.length) {
            throw new IllegalArgumentException("vecX length and vecW length not equal");
        }
        double sumX = 0.0, sumW = 0.0, mean = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += vecW[i] * vecX[i];
            sumW += vecW[i];
        }
        mean = sumX / sumW;
        sumX = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += vecW[i] * (vecX[i] - mean) * (vecX[i] - mean);
        }
        return sumX * (double) (n) / ((double) (n - 1) * sumW);
    }

    public static double mean(double[] vecX, double[] vecW) {

        int n = vecX.length;
        if (n != vecW.length) {
            throw new IllegalArgumentException("vecX length and vecW length not equal");
        }
        double sumX = 0.0, sumW = 0.0, mean = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += vecW[i] * vecX[i];
            sumW += vecW[i];
        }
        mean = sumX / sumW;
        return mean;
    }

    public static double variance(double[] vecX) {
        return variance(vecX, vecX.length);
    }

    public static double variance(double[] vecX, int n) {
        if (n > vecX.length) {
            throw new IllegalArgumentException("vecX length less than n");
        }
        double sumX = 0.0;
        double mean = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += vecX[i];
        }
        mean = sumX / n;
        sumX = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += (vecX[i] - mean) * (vecX[i] - mean);
        }
        return sumX / ((double) (n - 1));
    }

    // Weighted covariance of two 1D arrays of doubles, vecX and vecY with weights vecW
    public static double covariance(double[] vecX, double[] vecY, double[] vecW) {
        int n = vecX.length;
        if (n != vecY.length) {
            throw new IllegalArgumentException("vecX length and vecY length not equal");
        }
        if (n != vecW.length) {
            throw new IllegalArgumentException("vecX length and vecW length not equal");
        }
        double sumX = 0.0;
        double sumY = 0.0;
        double sumW = 0.0;
        double meanX = 0.0;
        double meanY = 0.0;

        for (int i = 0; i < n; i++) {
            sumX += vecX[i] * vecW[i];
            sumY += vecY[i] * vecW[i];
            sumW += vecW[i];
        }
        meanX = sumX / sumW;
        meanY = sumY / sumW;

        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += vecW[i] * (vecX[i] - meanX) * (vecY[i] - meanY);
        }
        return sum * (double) (n) / ((double) (n - 1) * sumW);
    }

    public static double correlation(double[] vecX, double[] vecY, double[] vecW) {
        int n = vecX.length;
        if (n != vecY.length) {
            throw new IllegalArgumentException("vecX length and vecY length not equal");
        }
        if (n != vecW.length) {
            throw new IllegalArgumentException("vecX length and vecW length not equal");
        }

        double sxy = covariance(vecX, vecY, vecW);
        double sx = variance(vecX, vecW);
        double sy = variance(vecY, vecW);
        return sxy / Math.sqrt(sx * sy);
    }

    public static void unifyVarByBin(double[] vecX, double[] vecY, double[] vecW, int bin_size) {
        /* Inputs:
         *    Step size (number of data points with
         regard to different ? values)             - bin_size
         *                                    xList     - Target spectrum (could be a reference spectrum)
         *                                    yList     - Test Spectrum
         *                                    wList     - Spectrum normalizing weight
         * */
        int n = vecX.length;
        if (n != vecY.length) {
            throw new IllegalArgumentException("vecX length and vecY length not equal");
        }
        if (n != vecW.length) {
            throw new IllegalArgumentException("vecX length and vecW length not equal");
        }

        Double temp_x, temp_y, temp_w;
        int pos_in_list;
        int bin_number = n / bin_size;
        double[] vecX_bin = new double[bin_size];
        double[] vecY_bin = new double[bin_size];
        double[] vecW_bin = new double[bin_size];

        for (int i = 0; i < bin_number; i++) {
            for (int j = 0; j < bin_size; j++) {
                pos_in_list = i * bin_size + j;
                vecX_bin[j] = vecX[pos_in_list];
                vecY_bin[j] = vecY[pos_in_list];
                vecW_bin[j] = vecW[pos_in_list];

            }
            double meanX = mean(vecX_bin, vecW_bin);
            double meanY = mean(vecY_bin, vecW_bin);
            double varX = variance(vecX_bin, vecW_bin);
            double varY = variance(vecY_bin, vecW_bin);
            if ((varX > Double.MIN_VALUE) && (varY > Double.MIN_VALUE)) {
                for (int j = 0; j < bin_size; j++) {
                    pos_in_list = i * bin_size + j;
                    vecX[pos_in_list] = (vecX[pos_in_list] - meanX) / Math.sqrt(varX);
                    vecY[pos_in_list] = (vecY[pos_in_list] - meanY) / Math.sqrt(varY);
                }
            }
        }
    }

    public static double binnedCorrelation(Vec vecMatX, Vec vecMatY, int binSize) {
        return binnedCorrelation(vecMatX, vecMatY, null, binSize);
    }

    public static double binnedCorrelation(Vec vecMatX, Vec vecMatY, Vec vecMatW, int binSize) {
        /* Inputs:
         *     rList   -   Reference spectrum
         *     sList   -   Testing sample spectrum to be alighed
         *
         * */
        int n = vecMatX.getSize();
        if (n != vecMatY.getSize()) {
            throw new IllegalArgumentException("Reference vec length and testing vec length not equal");
        }
        double[] vecX = new double[n];
        double[] vecY = new double[n];
        double[] vecW = new double[n];
        System.arraycopy(vecMatX.getReal(), 0, vecX, 0, n);
        System.arraycopy(vecMatY.getReal(), 0, vecY, 0, n);
        if (vecMatW != null) {
            if (n != vecMatW.getSize()) {
                throw new IllegalArgumentException("Reference vec length and weight vec length not equal");
            }
            System.arraycopy(vecMatW.getReal(), 0, vecW, 0, n);
        } else {
            for (int i = 0; i < n; i++) {
                vecW[i] = 1.0;
            }
        }

        return correlation_bin(vecX, vecY, vecW, binSize);
    }

    /**
     * *******************************************************************************************************
     * Closeness index of the Spectral Alignment: The original closeness index:
     * correlation coefficient (cc) may be influenced by a small number of large
     * peaks. Therefore, as suggested by (Nicholson et al. 2009), local areas
     * must be scaled to an equal variance and calculated separately. The
     * redefined similarity index is the production of cc in segments. Also,
     * it's suggest that bin size ?=0.02ppm (a few times the full width of half
     * maximum of a typical peak) is more suitable if we need to ensure the
     * equal contribution of both minor and major peaks in the alignment quality
     * measure, whereas the bin size ?=0.08ppm is sufficient to minimize the
     * contribution of minor peaks and is more suitable to evaluate the
     * alignment of major peaks
     * ******************************************************************************************************
     */
    public static double correlation_bin(double[] vecX, double[] vecY, double[] vecW, int bin_size) {
        /* Inputs:
         *    Step size (number of data points with
         regard to different ? values)             - bin_size
         *                                    xList     - Target spectrum (could be a reference spectrum)
         *                                    yList     - Test Spectrum
         *                                    wList     - Spectrum normalizing weight
         * */
        int n = vecX.length;
        if (n != vecY.length) {
            throw new IllegalArgumentException("vecX length and vecY length not equal");
        }
        if (n != vecW.length) {
            throw new IllegalArgumentException("vecX length and vecW length not equal");
        }
        unifyVarByBin(vecX, vecY, vecW, bin_size);
        double cc_bin = correlation(vecX, vecY, vecW);
        return cc_bin;
    }

    /**
     * *******************************************************************************************************
     * FFT Correlation Spectral Alignment Algorithm: Given spectrum functions of
     * the reference: r(x) and the testing sample s(x), calculate the
     * correlation and optimal shift. Since FFT will be applied to the spectrum,
     * rather than time-serial data, the resolution of spectrum will becomes an
     * important issue. Just in case of information lose, it will be nice if the
     * sampling rate meets Nyquist-Shannon sampling theorem.
     * <p>
     * FFT(Corr_r,s_(u)-> R*(w)S(w) (a.k.a Discrete Correlation Theorem), after
     * performing reverse Fourier transform, the maximal correlation will appear
     * at the the optimal shift
     * ******************************************************************************************************
     */
    public static PositionValue fftCorr(Vec vec1, Vec vecMatS, int binSize, int maxShift) {
        /* Inputs:
         *     rList   -   Reference spectrum
         *     sList   -   Testing sample spectrum to be alighed
         *
         * */
        int shifts = 0;
        int n = vec1.getSize();
        int m = vecMatS.getSize();
        int newN = n;
        if (n > m) {
            newN = n + maxShift;
        } else {
            newN = m + maxShift;
        }
        newN = (int) Math.round(Math.pow(2, Math.ceil((Math.log(newN) / Math.log(2)))));

        double[] vecR = new double[newN];
        double[] vecS = new double[newN];
        System.arraycopy(vec1.getReal(), 0, vecR, 0, n);
        System.arraycopy(vecMatS.getReal(), 0, vecS, 0, m);
        standardize(vecR, n);
        standardize(vecS, m);
        PositionValue pValue = fftCorr(vecR, vecS, maxShift);
        return pValue;
    }

    public static PositionValue fftCorr(Vec vec1, Vec vecMatS, int maxShift, int pt1, int pt2) {
        /* Inputs:
         *     rList   -   Reference spectrum
         *     sList   -   Testing sample spectrum to be alighed
         *
         * */
        int n = pt2 - pt1 + 1;
        int newN = n;

        newN = (int) Math.round(Math.pow(2, Math.ceil((Math.log(newN) / Math.log(2)))));

        double[] vecR = new double[newN];
        double[] vecS = new double[newN];
        System.arraycopy(vec1.getReal(), pt1, vecR, 0, n);
        System.arraycopy(vecMatS.getReal(), pt1, vecS, 0, n);
        standardize(vecR, n);
        standardize(vecS, n);
        PositionValue pValue = fftCorr(vecR, vecS, maxShift);
        return pValue;
    }

    public static PositionValue fftCorr(double[] vecR, double[] vecS, int maxShift) {
        /* Inputs:
         *     rList   -   Reference spectrum
         *     sList   -   Testing sample spectrum to be alighed
         *
         * */
        int n = vecR.length;
        int m = vecS.length;
        if (n != m) {
            throw new IllegalArgumentException("Reference vec length and testing vec length not equal");
        }
        PositionValue pValue = null;

        //Fast Fourier transform. Import org.apache.commons.math3.transform.FastFourierTransformer
        FastFourierTransformer ffTrans = new FastFourierTransformer(DftNormalization.STANDARD);
        try {
            Complex[] cvecR = ffTrans.transform(vecR, TransformType.FORWARD);  //FFT: r(x) -> R(w)
            Complex[] cvecS = ffTrans.transform(vecS, TransformType.FORWARD);  //FFT: s(x) -> S(w)
            Complex[] cvecR_conjugate = getConjugate(cvecR);
            //Get R*(w)S(w)
            for (int i = 0; i < cvecR_conjugate.length; i++) {
                cvecR_conjugate[i] = cvecR_conjugate[i].multiply(cvecS[i]);
            }
            //IFT to obtain the cross-correlation function of r(x) and s(x), which is a function of the shift: u
            Complex[] corr_rs = ffTrans.transform(cvecR_conjugate, TransformType.INVERSE);
            double[] corr_rs_power = getMagnitude(corr_rs);
            //Find the position with maximal correlation
            double max_corr = 0.0;
            int shift = 0;
            if (true) {
                for (int i = -maxShift; i <= maxShift; i++) {
                    int j = i >= 0 ? i : n + i;
                    if (corr_rs_power[j] > max_corr) {
                        max_corr = corr_rs_power[j];
                        shift = i;
                    }
                }
            } else {
                for (int i = 0; i < corr_rs_power.length; i++) {
                    if (corr_rs_power[i] > max_corr) {
                        max_corr = corr_rs_power[i];
                        shift = i;
                    }
                }
                if (shift >= n / 2) {
                    shift = shift - n;
                }
            }
            pValue = new PositionValue(shift, max_corr);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(-1);
        }
        return pValue;
    }

}
