/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.compounds;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.math.Interpolator;
import org.nmrfx.processor.math.AmplitudeFitResult;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.VecUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;


/**
 * @author brucejohnson
 */
public class CompoundFitter implements MultivariateFunction {

    private static final Logger log = LoggerFactory.getLogger(CompoundFitter.class);

    public static final int MAX_SHIFT = 15;

    ArrayList<CompoundRegion> cList = new ArrayList<>();
    List<CompoundMatch> cMatches = new ArrayList<>();

    private RealMatrix A;
    private RealVector B;
    private RealVector X;
    private SingularValueDecomposition svd;
    Vec vec;
    private double[] vData = new double[0];
    private double[] maskData = new double[0];
    private int[] map = null;
    private int[] rmap = null;
    private int bcNum = 0;
    double ppmDeltaToPoint = 1;
    double vecRef = 0;
    double vecHzToPoint = 0;

    private final static int VALUE_AB_ABS = 0;
    private final static int VALUE_AB_ABS_NEGPEN = 1;
    private final static int VALUE_ABS = 2;
    private final static int VALUE_LS = 3;
    private final static int VALUE_LS_NONNEG = 4;
    private int valueMode = VALUE_ABS;

    /**
     *
     */
    public static final RandomGenerator DEFAULT_RANDOMGENERATOR = new MersenneTwister(1);

    /**
     *
     */
    public CompoundFitter() {
    }

    /**
     * @param vec Vec object to set
     */
    public void setVec(Vec vec) {
        this.vec = vec;
        if (vec == null) {
            throw new IllegalArgumentException("Vector  does not exist");
        }
        vData = vec.getReal();
        maskData = new double[vData.length];
        Arrays.fill(maskData, 1.0);
        setVScale(vec);
    }

    /**
     * @param vecName  name of Vec to lookup and use
     * @param maskName name of Vec to lookup and use as mask
     */
    public void setVecWithMask(String vecName, String maskName) {
        Vec vec = (Vec) Vec.get(vecName);
        Vec maskVec = (Vec) Vec.get(maskName);
        if (vec == null) {
            throw new IllegalArgumentException("Vector \"" + vecName + "\" does not exist");
        }
        if (maskVec == null) {
            throw new IllegalArgumentException("Vector \"" + maskName + "\" does not exist");
        }
        vData = vec.getReal();
        maskData = maskVec.getReal();
        setVScale(vec);
    }

    void setVScale(Vec vec) {
        double sf = vec.centerFreq;
        double sw = 1.0 / vec.dwellTime;
        double size = vec.getSize();
        ppmDeltaToPoint = size / (sw / sf);
        vecHzToPoint = size / sw;
        vecRef = vec.getZeroRefValue();
    }

    double vecPPMToPoint(final double ppm) {
        return (vecRef - ppm) * ppmDeltaToPoint;
    }

    int vecPPMToIntPoint(final double ppm) {
        return (int) Math.round(vecPPMToPoint(ppm));
    }

    double[] getNewRange(Region region, final double ppm1, final double ppm2) {
        double regionWidthHz = region.getWidthHz(Math.abs(ppm1 - ppm2));
        double centerPPM = (ppm1 + ppm2) / 2.0;
        double center = vecPPMToPoint(centerPPM);
        double deltaPoints = regionWidthHz * vecHzToPoint;
        double start = center - deltaPoints / 2.0;
        double end = start + deltaPoints;
        return new double[]{start, end};
    }

    /**
     * @param bcNum order of baseline correction polynomial
     */
    public void setBC(final int bcNum) {
        this.bcNum = Math.max(bcNum, 0);
    }

    static class CompoundRegion {

        private final CompoundMatch cMatch;
        private final int[] regions;
        private final double[] shifts;
        private final int[] minShifts;
        private final int[] maxShifts;

        CompoundRegion(CompoundMatch cMatch, int[] regions, double[] shifts, int[] minShifts, int[] maxShifts) {
            this.cMatch = cMatch;
            this.regions = regions;
            this.shifts = shifts;
            this.minShifts = minShifts;
            this.maxShifts = maxShifts;
        }
    }

    /**
     * @return the A
     */
    public RealMatrix getA() {
        return A;
    }

    /**
     * @return the B
     */
    public RealVector getB() {
        return B;
    }

    /**
     * @return the X
     */
    public RealVector getX() {
        return X;
    }

    /**
     * @return the svd
     */
    public SingularValueDecomposition getSvd() {
        return svd;
    }

    /**
     * @param cMatch
     * @param region
     * @param shift
     * @param minShift
     * @param maxShift
     */
    public void addCompoundRegion(CompoundMatch cMatch, int region, double shift, int minShift, int maxShift) {
        int[] regions = new int[1];
        double[] shifts = new double[1];
        int[] minShifts = new int[1];
        int[] maxShifts = new int[1];
        regions[0] = region;
        shifts[0] = shift;
        minShifts[0] = minShift;
        maxShifts[0] = maxShift;
        CompoundRegion cRegion = new CompoundRegion(cMatch, regions, shifts, minShifts, maxShifts);
        cList.add(cRegion);
    }

    /**
     * @param cMatch
     * @param cmpdID
     * @param regions
     * @param shifts
     * @param minShifts
     * @param maxShifts
     */
    public void addCompound(CompoundMatch cMatch, String cmpdID, int[] regions, double[] shifts, int[] minShifts, int[] maxShifts) {
        CompoundRegion cRegion = new CompoundRegion(cMatch, regions, shifts, minShifts, maxShifts);
        cList.add(cRegion);
        cMatches.add(cMatch);
    }

    private boolean[] zeroRegions(double[] data, int padding) {
        int n = data.length;
        boolean[] mask = new boolean[n];
        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                Region region = cData.getRegion(cR.regions[iRegion]);
                double shift = cR.shifts[iRegion];
                double start = region.getStart();
                double end = region.getEnd();
                double ppm1 = region.getPPM1();
                double ppm2 = region.getPPM2();

                double[] newRange = getNewRange(region, ppm1, ppm2);
                double vecStart = newRange[0] + shift - padding;
                double vecEnd = newRange[1] + shift + padding;

                int iVecStart = (int) Math.ceil(vecStart);
                int iVecEnd = (int) Math.floor(vecEnd);

                for (int i = iVecStart; i <= iVecEnd; i++) {
                    mask[i] = true;
                }
            }
        }
        return mask;
    }

    /**
     * @return
     */
    public double scoreLeastSq() {
        prepareAB();
        return score(null, false);
    }

    /**
     * @return
     */
    public double scoreLeastSqNonNeg() {
        prepareAB();
        return score(null, true);
    }

    /**
     * @param skipColumns
     * @param nonNeg
     * @return
     */
    public double score(boolean[] skipColumns, boolean nonNeg) {
        RealMatrix AR;
        if (skipColumns == null) {
            AR = A.copy();
        } else {
            int count = 0;
            for (boolean value : skipColumns) {
                if (!value) {
                    count++;
                }
            }
            AR = new Array2DRowRealMatrix(A.getRowDimension(), count);
            int i = 0;
            int j = 0;
            for (boolean value : skipColumns) {
                if (!value) {
                    AR.setColumn(j, A.getColumn(i));
                    j++;
                }
                i++;
            }

        }
        RealVector BR = B.copy();
        double[] x;
        if (nonNeg) {
            x = solveABNN(AR, BR);
        } else {
            x = solveAB(AR, BR);
        }
        X = new ArrayRealVector(x);
        RealVector Y = AR.operate(X);
        double norm2 = Y.subtract(B).getNorm();
        return norm2;
    }

    /**
     * @return
     */
    public double aicScore() {
        prepareAB();
        int nCols = A.getColumnDimension();
        int n = A.getRowDimension();
        double rms = score(null, true);
        double RSS = (rms * rms) * n;
        int k = nCols;
        double fullAIC = n * Math.log(RSS / n) + 2 * k;
        double fullBIC = n * Math.log(RSS / n) + k * Math.log(n);
        double iC = fullBIC;
        System.out.println("full " + fullAIC);
        double[] x = new double[nCols];
        for (int i = 0; i < nCols; i++) {
            boolean[] skipColumns = new boolean[nCols];
            skipColumns[i] = true;
            rms = score(skipColumns, true);
            k = nCols - 1;
            RSS = (rms * rms) * n;
            double AIC = n * Math.log(RSS / n) + 2 * k;
            double BIC = n * Math.log(RSS / n) + k * Math.log(n);
            System.out.println(i + " " + AIC);
            x[i] = BIC;

        }
        X = new ArrayRealVector(x);
        return iC;
    }

    /**
     *
     */
    public void genMaps() {
        int nPoints = 0;
        int padding = 50;
        boolean[] mask = zeroRegions(vData, padding);
        for (int i = 0; i < maskData.length; i++) {
            if ((maskData[i] > 1.0e-8) && mask[i]) {
                nPoints++;
            }
        }
        map = new int[nPoints];
        rmap = new int[maskData.length];
        for (int i = 0, j = 0; i < maskData.length; i++) {
            rmap[i] = -1;
            if ((maskData[i] > 1.0e-8) && mask[i]) {
                map[j] = i;
                rmap[i] = j;
                j++;
            }

        }
    }

    /**
     * @return
     */
    public int countSize() {
        int size = 0;
        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            size += cR.regions.length;
        }
        return size;
    }

    /**
     * @return
     */
    public double[] current() {
        int j = 0;
        double[] current = new double[countSize()];
        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                current[j++] = cR.shifts[iRegion];

            }
        }
        return current;
    }

    /**
     * @return
     */
    public double[][] currentWithBounds() {
        int j = 0;
        double[][] current = new double[3][countSize()];
        for (CompoundRegion cR : cList) {
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                double startShift = cR.shifts[iRegion];
                double minShift = cR.minShifts[iRegion];
                double maxShift = cR.maxShifts[iRegion];

                current[0][j] = cR.shifts[iRegion];
                current[1][j] = minShift;
                current[2][j] = maxShift;
                double delta = maxShift - minShift;

                if (current[0][j] < current[1][j]) {
                    current[0][j] = current[1][j] + delta * 0.1;
                }
                if (current[0][j] > current[2][j]) {
                    current[0][j] = current[2][j] - delta * 0.1;
                }
                j++;

            }
        }
        return current;
    }

    /**
     * @param x
     * @return
     */
    public double value(double[] x) {
        double value = 0.0;
        switch (valueMode) {
            case VALUE_AB_ABS:
                value = valueABAbs(x);
                break;
            case VALUE_AB_ABS_NEGPEN:
                value = valueABAbs(x);
                break;
            case VALUE_LS:
                value = valueLS(x);
                break;
            case VALUE_ABS:
                value = valueAbs(x);
                break;
        }
        return value;
    }

    /**
     * @param x
     * @return
     */
    public double valueAbs(double[] x) {
        int j = 0;
        for (CompoundRegion cR : cList) {
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                cR.shifts[iRegion] = x[j++];
            }
        }
        FitResult fitResult = fitXY();
        return fitResult.getDev();
    }

    /**
     * @param x
     * @return
     */
    public double valueLS(double[] x) {
        int j = 0;
        for (CompoundRegion cR : cList) {
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                cR.shifts[iRegion] = x[j++];
            }
        }
        double rmsd = scoreLeastSqNonNeg();
        return rmsd;

    }

    /**
     * @param x
     * @return
     */
    public double valueABAbs(double[] x) {
        X = new ArrayRealVector(x);
        RealVector Y = A.operate(X);
        double norm1 = Y.subtract(B).getL1Norm();
        RealVector delta = Y.subtract(B);
        int n = delta.getDimension();
        double viol = 0.0;
        if (valueMode == VALUE_AB_ABS_NEGPEN) {
            for (int i = 0; i < n; i++) {
                double value = delta.getEntry(i);
                if (value > 0.0) {
                    viol += value;
                }
            }
        }

        return norm1 + viol;
    }

    class UnivariateValue implements UnivariateFunction {

        private final CompoundFitter fitter;
        private final double[] current;

        public UnivariateValue(CompoundFitter fitter) {
            this.fitter = fitter;
            current = fitter.current();
        }

        @Override
        public double value(double x) {
            int j = 0;
            for (CompoundRegion cR : cList) {
                for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                    cR.shifts[iRegion] = current[j++] + x;
                }
            }
            double rmsd = scoreLeastSqNonNeg();
            return rmsd;
        }
    }

    /**
     * @param range
     * @return
     */
    public double optimize1D(double range) {
        valueMode = VALUE_ABS;
        BrentOptimizer brent = new BrentOptimizer(1.0e-9, 1.0e-11);
        UnivariateValue uValue = new UnivariateValue(this);
        UnivariatePointValuePair valuePair = brent.optimize(
                new ObjectiveFunction(this),
                new MaxEval(100),
                new SearchInterval(-range, range, 0.0),
                GoalType.MINIMIZE);

        return valuePair.getPoint();
    }

    /**
     * @param start
     * @return
     */
    public double[] optimizeByCMAES(int start) {
        valueMode = VALUE_ABS;

        double[][] starting = currentWithBounds();

        final int n = starting[0].length;
        int maxIterations = 1000;
        double stopFitness = 0.0;
        boolean isActiveCMA = true;
        int nDiagOnly = 0;
        int checkFeasableCount = 0;
        boolean genStat = false;
        ConvergenceChecker convergenceChecker = new SimpleValueChecker(1.0e-5, -1.0);

        CMAESOptimizer optimizer = new CMAESOptimizer(maxIterations, stopFitness, isActiveCMA, nDiagOnly, checkFeasableCount, DEFAULT_RANDOMGENERATOR, genStat, convergenceChecker);
        PointValuePair result = null;
        double lambdaMul = 1.0;
        int lambda = (int) (lambdaMul * FastMath.round(4 + 3 * FastMath.log(n)));

        double[] inputSigma = new double[n];
        for (int i = 0; i < n; i++) {
            inputSigma[i] = Math.abs(starting[1][i] - starting[2][i]) * 0.4;
        }

        result = optimizer.optimize(
                new CMAESOptimizer.PopulationSize(lambda),
                new CMAESOptimizer.Sigma(inputSigma),
                new MaxEval(2000000),
                new ObjectiveFunction(this), GoalType.MINIMIZE,
                new SimpleBounds(starting[1], starting[2]),
                new InitialGuess(starting[0]));
        return result.getPoint();
    }

    /**
     * @return
     */
    public double[] scoreAbsNegPen() {
        valueMode = VALUE_AB_ABS_NEGPEN;
        return scoreByCMAES();
    }

    /**
     * @return
     */
    public double[] scoreAbs() {
        valueMode = VALUE_AB_ABS;
        return scoreByCMAES();
    }

    /**
     * @return
     */
    public double[] scoreByCMAES() {
        scoreLeastSqNonNeg();
        double[] start = X.toArray();
        final int n = start.length;
        double[] lower = new double[n];
        double[] upper = new double[n];
        int maxIterations = 1000;
        double stopFitness = 0.0;
        boolean isActiveCMA = true;
        int nDiagOnly = 0;
        int checkFeasableCount = 0;
        boolean genStat = false;
        ConvergenceChecker convergenceChecker = new SimpleValueChecker(1.0e-6, -1.0);
        CMAESOptimizer optimizer = new CMAESOptimizer(maxIterations, stopFitness, isActiveCMA, nDiagOnly, checkFeasableCount, DEFAULT_RANDOMGENERATOR, genStat, convergenceChecker);
        PointValuePair result = null;
        double lambdaMul = 1.0;
        int lambda = (int) (lambdaMul * FastMath.round(4 + 3 * FastMath.log(n)));

        double[] inputSigma = new double[n];
        for (int i = 0; i < n; i++) {
            lower[i] = 0.0;
            upper[i] = start[i] * 3;
            if (start[i] < 1.0e-6) {
                upper[i] = 1.0e-3;
            }
            inputSigma[i] = Math.abs(upper[i] - lower[i]) * 0.4;
            System.out.println(i + " " + lower[i] + " " + start[i] + " " + upper[i]);
        }

        result = optimizer.optimize(
                new CMAESOptimizer.PopulationSize(lambda),
                new CMAESOptimizer.Sigma(inputSigma),
                new MaxEval(2000000),
                new ObjectiveFunction(this), GoalType.MINIMIZE,
                new SimpleBounds(lower, upper),
                new InitialGuess(start));

        System.out.println(optimizer.getEvaluations() + " " + result.getValue());
        double[] scales = result.getPoint();
        for (int i = 0; i < scales.length; i++) {
            cMatches.get(i).setScale(scales[i]);
        }
        return result.getPoint();
    }

    /**
     * @return
     */
    public FitResult fitXY() {

        genMaps();
        int nPoints = map.length;
        double[] measData = new double[nPoints];
        for (int i = 0, j = 0; i < map.length; i++) {
            measData[j++] = vData[map[i]];
        }

        double[] refData = new double[nPoints];
        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                Region region = cData.getRegion(cR.regions[iRegion]);
                double shift = cR.shifts[iRegion];
                double end = region.getEnd();
                double ppm1 = region.getPPM1();
                double ppm2 = region.getPPM2();

                double[] newRange = getNewRange(region, ppm1, ppm2);
                double vecStart = newRange[0] + shift;
                double vecEnd = newRange[1] + shift;

                double[] values = region.getIntensities();
                values = Interpolator.getInterpolated(values, vecStart, vecEnd);
                int iVecStart = (int) Math.ceil(vecStart);
                int vecRegionSize = values.length;

                for (int i = 0; i < vecRegionSize; i++) {
                    if (rmap[i + iVecStart] >= 0) {
                        refData[rmap[i + iVecStart]] += values[i];
                    }
                }
            }
        }
        FitResult fitResult = scaleByAbsDev(refData, measData, false);
        return fitResult;
    }

    /**
     * @param scale
     * @param offset
     * @return
     */
    public double[] compareXY(double scale, double offset) {
        genMaps();
        double minCorr = 1.0;
        double minFrac = 1.0e6;

        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                Region region = cData.getRegion(cR.regions[iRegion]);
                double shift = cR.shifts[iRegion];
                double ppm1 = region.getPPM1();
                double ppm2 = region.getPPM2();

                double[] newRange = getNewRange(region, ppm1, ppm2);
                double vecStart = newRange[0] + shift;
                double vecEnd = newRange[1] + shift;

                double[] values = region.getIntensities();
                values = Interpolator.getInterpolated(values, vecStart, vecEnd);
                int iVecStart = (int) Math.ceil(vecStart);
                int vecRegionSize = values.length;
                double[] x = new double[vecRegionSize];
                double[] y = new double[vecRegionSize];
                double xMax = 0.0;
                double yMax = 0.0;
                for (int i = 0; i < vecRegionSize; i++) {
                    if (rmap[i + iVecStart] >= 0) {
                        y[i] = values[i] * scale + offset;
                        x[i] = vData[i + iVecStart];
                        if (x[i] > xMax) {
                            xMax = x[i];
                        }
                        if (y[i] > yMax) {
                            yMax = y[i];
                        }
                    }
                }
                SpearmansCorrelation pCorr = new SpearmansCorrelation();
                double corr = pCorr.correlation(x, y);
                if (corr < minCorr) {
                    minCorr = corr;
                }
                double fRatio = xMax / yMax;
                if (fRatio < minFrac) {
                    minFrac = fRatio;
                }
            }
        }
        double[] result = {minCorr, minFrac};
        return result;
    }

    /**
     *
     */
    public void prepareAB() {
        genMaps();
        int nPoints = map.length;
        double[] b = new double[nPoints];
        for (int i = 0, j = 0; i < map.length; i++) {
            b[j++] = vData[map[i]];
        }

        B = new ArrayRealVector(b);
        A = new Array2DRowRealMatrix(nPoints, cList.size() + bcNum);
        int iCol = 0;
        for (int i = 0; i < bcNum; i++) {
            for (int iRow = 0; iRow < nPoints; iRow++) {
                double bcValue = Math.pow(map[iRow], (i + 1));
                A.setEntry(iRow, i, bcValue);
            }
            iCol++;
        }
        System.out.println("bc " + bcNum + " " + iCol + " np " + nPoints + " vdlen " + vData.length);
        for (CompoundRegion cR : cList) {
            double[] aCol = new double[nPoints];
            CompoundData cData = cR.cMatch.cData;
            for (int iRow = 0; iRow < nPoints; iRow++) {
                aCol[iRow] = 0.0;
            }
            System.out.println("fit " + cR.cMatch.cData.getId() + " " + cR.regions.length);
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                Region region = cData.getRegion(cR.regions[iRegion]);
                double shift = cR.shifts[iRegion];
                double end = region.getEnd();
                double ppm1 = region.getPPM1();
                double ppm2 = region.getPPM2();

                double[] newRange = getNewRange(region, ppm1, ppm2);
                double vecStart = newRange[0] + shift;
                double vecEnd = newRange[1] + shift;

                double[] values = region.getIntensities();
                values = Interpolator.getInterpolated(values, vecStart, vecEnd);
                int iVecStart = (int) Math.ceil(vecStart);
                int vecRegionSize = values.length;
                for (int i = 0; i < vecRegionSize; i++) {
                    if (rmap[i + iVecStart] >= 0) {
                        aCol[rmap[i + iVecStart]] += values[i];
                    }
                }
            }

            for (int iRow = 0; iRow < nPoints; iRow++) {
                A.setEntry(iRow, iCol, aCol[iRow]);
            }
            if (cR.regions.length > 0) {
                iCol++;
            }
        }
    }

    /**
     * @param fileName
     */
    public void dumpAB(String fileName) {
        try {
            FileWriter outFile = new FileWriter(fileName);
            try (PrintWriter out = new PrintWriter(outFile)) {
                int nPoints = map.length;
                int nCols = A.getColumnDimension();
                int nRows = A.getRowDimension();
                for (int iRow = 0; iRow < nRows; iRow++) {
                    out.printf("%5d %5d", iRow, map[iRow]);
                    for (int iCol = 0; iCol < nCols; iCol++) {
                        out.printf("%12.5f", A.getEntry(iRow, iCol));
                    }
                    out.printf("%12.5f\n", B.getEntry(iRow));
                }
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    double[] solveABNN(RealMatrix AR, RealVector BR) {
        AmplitudeFitResult fitResult = VecUtil.nnlsFit(AR, new Array2DRowRealMatrix(BR.toArray()));
        return fitResult.getCoefs();
    }

    double[] solveAB(RealMatrix AR, RealVector BR) {
        svd = new SingularValueDecomposition(A);
        DecompositionSolver solver = svd.getSolver();
        RealMatrix result = solver.solve(new Array2DRowRealMatrix(BR.toArray()));
        return result.getColumn(0);

    }

    /**
     * @param vecX
     * @param vecY
     * @param normalize
     * @return
     */
    public FitResult scaleByAbsDev(double[] vecX, double[] vecY, boolean normalize) {

        int n = vecX.length;
        if (n != vecY.length) {
            throw new IllegalArgumentException("vecX length and vecY length not equal");
        }
        double[] vecYN = new double[n];
        double maxX = 0.0;
        double maxY = 0.0;
        for (int i = 0; i < n; i++) {
            maxY = Math.max(vecY[i], maxY);
            maxX = Math.max(vecX[i], maxX);
            vecYN[i] = vecY[i];
        }
        if (normalize && (maxY != 0.0)) {
            for (int i = 0; i < n; i++) {
                vecYN[i] = vecY[i] * maxX / maxY;
            }
        }

        double[] parameters = VecUtil.fitAbsDev(vecX, vecYN, null);

        double sumAbsDev = 0.0;
        for (int i = 0; i < n; i++) {
            double y = vecX[i] * parameters[1] + parameters[0];
            sumAbsDev += Math.abs(y - vecYN[i]);
        }
        double meanDev = sumAbsDev / n;
        SpearmansCorrelation pCorr = new SpearmansCorrelation();
        double corr = pCorr.correlation(vecX, vecY);
        FitResult fitResult = new FitResult(parameters[0], parameters[1], meanDev, corr);
        return fitResult;
    }

    /**
     * @param vecX
     * @param vecY
     * @param scale
     * @param offset
     * @return
     */
    public double getAbsDev(final double[] vecX, final double[] vecY, final double scale, final double offset) {

        int n = vecX.length;
        if (n != vecY.length) {
            throw new IllegalArgumentException("vecX length and vecY length not equal");
        }

        double sumAbsDev = 0.0;
        for (int i = 0; i < n; i++) {
            double y = vecX[i] * scale + offset;
            sumAbsDev += Math.abs(y - vecY[i]);
        }
        double meanDev = sumAbsDev / n;
        return meanDev;
    }

    private double getCompoundMatch(final double[] parameters) {
        ArrayList<FitResult> bestShifts = new ArrayList<>();
        double sumDev = 0.0;
        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                Region region = cData.getRegion(cR.regions[iRegion]);
                int startShift = (int) cR.shifts[iRegion];
                int minShift = cR.minShifts[iRegion];
                int maxShift = cR.maxShifts[iRegion];
                double absDev = getRegionMatch(region, minShift, startShift, maxShift);
                sumDev += absDev;
            }
        }
        return sumDev;
    }

    private double getRegionMatch(Region region, double shift, double scale, double offset) {
        double[] values = region.getInterpolated(0);
        int nValues = values.length;
        double ppm1 = region.getPPM1();
        double ppm2 = region.getPPM2();
        double regionWidthHz = region.getWidthHz(Math.abs(ppm1 - ppm2));
        int vecRegionSize = (int) Math.round(regionWidthHz * vecHzToPoint);

        if (nValues != vecRegionSize) {
            values = Interpolator.getInterpolated(values, vecRegionSize);
        }
        int start = region.getStart();

        double[] x = new double[vecRegionSize];

        ppm1 = region.pointToPPM(start + shift);
        ppm2 = region.pointToPPM(start + shift + nValues - 1);
        int vecStart = vecPPMToIntPoint((ppm1 + ppm2) / 2) - vecRegionSize / 2;
        System.arraycopy(vData, vecStart, x, 0, vecRegionSize);
        double absDev = getAbsDev(x, values, (start + shift), 0.0);
        return absDev;

    }

    /**
     * @return
     */
    public double fitSections() {
        int nRegions = 0;
        double sumSlope = 0.0;
        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                Region region = cData.getRegion(cR.regions[iRegion]);
                double[] values = region.getInterpolated(0);
                int nValues = values.length;
                int start = region.getStart();
                int shift = (int) cR.shifts[iRegion];
                double[] x = new double[nValues];
                System.arraycopy(vData, start + shift, x, 0, nValues);
                FitResult fitResult = scaleByAbsDev(x, values, false);
                sumSlope += Math.max(0.0, fitResult.getScale());
                nRegions++;
            }
        }
        double slope = sumSlope / nRegions;
        return slope;
    }

    /**
     * @return
     */
    public ArrayList<FitResult> optimizeAlignment() {
        genMaps();
        ArrayList<FitResult> bestShifts = new ArrayList<>();
        for (CompoundRegion cR : cList) {
            CompoundData cData = cR.cMatch.cData;
            for (int iRegion = 0; iRegion < cR.regions.length; iRegion++) {
                Region region = cData.getRegion(cR.regions[iRegion]);
                int startShift = (int) cR.shifts[iRegion];
                int minShift = cR.minShifts[iRegion];
                int maxShift = cR.maxShifts[iRegion];
                FitResult fitResult = requireNonNull(optimizeRegion(region, minShift, startShift, maxShift), "Unable to find a best fit while optimizing alignment.");
                bestShifts.add(fitResult);
                cR.cMatch.setShift(cR.regions[iRegion], fitResult.getShift());
                cR.shifts[iRegion] = fitResult.getShift();
            }
        }
        return bestShifts;
    }

    private FitResult optimizeRegion(Region region, int minShift, int startShift, int maxShift) {
        double[] values = region.getInterpolated(0);
        int nValues = values.length;
        double ppm1 = region.getPPM1();
        double ppm2 = region.getPPM2();
        double regionWidthHz = region.getWidthHz(Math.abs(ppm1 - ppm2));
        int vecRegionSize = (int) Math.round(regionWidthHz * vecHzToPoint);

        if (nValues != vecRegionSize) {
            values = Interpolator.getInterpolated(values, vecRegionSize);
        }

        int start = region.getStart();
        double minDev = Double.MAX_VALUE;
        FitResult bestFit = null;
        for (int shift = minShift; shift <= maxShift; shift++) {
            int aShift = shift + startShift;
            double[] x = new double[vecRegionSize];

            ppm1 = region.pointToPPM((double) start + aShift);
            ppm2 = region.pointToPPM(start + aShift + nValues - 1.0);
            int vecStart = vecPPMToIntPoint((ppm1 + ppm2) / 2) - vecRegionSize / 2;
            System.arraycopy(vData, vecStart, x, 0, vecRegionSize);

            FitResult fitResult = scaleByAbsDev(x, values, true);
            double avgAbsDev = fitResult.getDev();
            if (avgAbsDev < minDev) {
                minDev = avgAbsDev;
                minShift = aShift;
                bestFit = fitResult;
                bestFit.setShift(minShift);
            }
        }
        return bestFit;
    }

    public static CompoundFitter setup(Collection<CompoundMatch> matches) {
        CompoundFitter fitter = new CompoundFitter();
        for (CompoundMatch cMatch : matches) {
            CompoundData cData = cMatch.getData();
            int nRegions = cMatch.getData().getRegionCount();
            int nActive = cMatch.getNActive();
            if (nActive > 0) {
                double[] shifts = new double[nActive];
                int[] jRegions = new int[nActive];
                int[] jMins = new int[nActive];
                int[] jMaxes = new int[nActive];
                int j = 0;
                for (int i = 0; i < nRegions; i++) {
                    if (cMatch.getActive(i)) {
                        jRegions[j] = i;
                        shifts[j] = cMatch.getShift(i);
                        jMins[j] = -MAX_SHIFT;
                        jMaxes[j] = MAX_SHIFT;
                        j++;
                    }
                }
                fitter.addCompound(cMatch, cData.getId(), jRegions, shifts, jMins, jMaxes);
            }
        }

        return fitter;
    }
    /*
    proc ::dcs::standards::setupCmpdFit {cmpdIDs} {
    global cFitter
    variable prefs
    variable pars
    variable cmpdRanges
    set cFitter [java::new com.onemoonsci.datachord.compoundLib.CompoundFitter]
    set nBC 0
    if {![info exists pars(maxshift)]  || ($pars(maxshift) eq "")} {
        set pars(maxshift) 15
    }
    set defaultShift $pars(maxshift)
    foreach cmpdID $cmpdIDs {
        if {![string match BC* $cmpdID]} {
            set cData [::dcs::standards::getCData $cmpdID]
            $cData createCompoundData
            set regions [::dcs::standards::getActiveRegionsList $cmpdID]
            set nRegions [expr {[llength $regions]/4}]
            set jShifts [java::new double\[\] $nRegions]
            set jRegions [java::new int\[\] $nRegions]
            set jMins [java::new int\[\] $nRegions]
            set jMaxes [java::new int\[\] $nRegions]

            set iR 0
            foreach "jRegion jShift j1 j2" $regions {
                if {![info exists cmpdRanges($cmpdID)]} {
                     set minShift -$defaultShift
                     set maxShift $defaultShift
                } else {
                     set minShift [lindex $cmpdRanges($cmpdID) [= jRegion*2]]
                     set maxShift [lindex $cmpdRanges($cmpdID) [= jRegion*2+1]]
                }
                $jRegions set $iR $jRegion
                $jShifts set $iR $jShift
                $jMins set $iR $minShift
                $jMaxes set $iR $maxShift
                incr iR
            }
            $cFitter addCompound $cmpdID $jRegions $jShifts $jMins $jMaxes
        } else {
            incr nBC
        }
    }
    $cFitter setBC $nBC
    return $cFitter
}

     */
}
