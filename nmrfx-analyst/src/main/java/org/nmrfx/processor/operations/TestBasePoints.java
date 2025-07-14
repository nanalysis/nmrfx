/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.operations;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author brucejohnson
 */
public class TestBasePoints implements MultivariateFunction {
    private static final Logger log = LoggerFactory.getLogger(TestBasePoints.class);
    private static final double DEGTORAD = Math.PI / 180.0;

    private static final Map<String, TestBasePoints> tbMap = new HashMap<>();

    int winSize;
    double negativePenalty = 1.0e-5;
    Vec testVec = null;
    double[] rvec = null;
    double[] ivec = null;
    double[] values = null;

    Vec dVec = null;
    Vec vector = null;
    int start = 0;
    int end = 0;
    Double ppmStart;
    Double ppmEnd;
    boolean useRegion;
    boolean[] hasSignal = null;
    double p1Penalty = 0.0;
    double p1PenaltyWeight = 0.02;
    int mode = 0;
    boolean useRegionSign = false;

    // list of start and end of baseline regions
    ArrayList<RegionPositions> bList = new ArrayList<>();
    ArrayList<BRegionData> b2List = new ArrayList<>();

    public TestBasePoints(Vec vector, int winSize, double ratio, int mode, double negativePenalty, boolean useRegion, Double ppmStart, Double ppmEnd) {
        this.winSize = winSize;
        this.mode = mode;
        this.negativePenalty = negativePenalty;
        this.ppmStart = ppmStart;
        this.ppmEnd = ppmEnd;
        this.useRegion = useRegion;
        if (useRegion) {
            this.mode = 0;
        }
        addVector(vector, false, ratio);
    }

    public TestBasePoints(int winSize) {
        this.winSize = winSize;
        useRegionSign = true;
    }

    public static TestBasePoints get(String name) {
        return tbMap.get(name);
    }

    public static void remove(String name) {
        tbMap.remove(name);
    }

    public static void add(String name, TestBasePoints tbPoints) {
        tbMap.put(name, tbPoints);
    }

    public double[] autoPhaseZero() {
        double minPhase0 = 0.0;
        double minRMSD = Double.MAX_VALUE;
        double stepSize0 = 22.5;
        int nSteps0 = (int) Math.round(180.0 / stepSize0);

        for (int i = -nSteps0; i < nSteps0; i++) {
            double phase0 = i * stepSize0;
            double aVal = testEnds(phase0);

            if (aVal < minRMSD) {
                minRMSD = aVal;
                minPhase0 = phase0;
            }

        }
        double[] phases = minZero(minPhase0 - stepSize0, minPhase0 + stepSize0);
        int checkSign = getSign(phases[0], 0.0);

        if (checkSign < 0) {
            phases[0] += 180.0;
        }

        while (phases[0] > 180) {
            phases[0] -= 360.0;
        }
        while (phases[0] < -180) {
            phases[0] += 360.0;
        }

        return phases;

    }

    public void setP1PenaltyWeight(double value) {
        p1PenaltyWeight = value;
    }

    public double[] autoPhase(double ph1Limit) {
        if (bList.size() == 1) {
            if (mode == 0) {
                return autoPhaseZero();
            } else {
                var reg = bList.get(0);
                double width = (double) reg.sig2 - reg.sig1;
                if ((width / vector.getSize()) < 0.1) {
                    return autoPhaseZero();
                }
            }
        }
        double minPhase0 = 0.0;
        double minPhase1 = 0.0;
        double minRMSD = Double.MAX_VALUE;
        double stepSize0 = 11.25;
        double stepSize1 = 11.25;
        int nSteps0 = (int) Math.round(180.0 / stepSize0);
        int nSteps1 = (int) Math.round(180.0 / stepSize1);

        for (int i = -nSteps0; i < nSteps0; i++) {
            double phase0 = i * stepSize0;
            for (int j = -nSteps1; j < nSteps1; j++) {
                double phase1 = j * stepSize1;
                if (FastMath.abs(phase1) > ph1Limit) {
                    continue;
                }
                double aVal = testEnds(phase0, phase1);

                if (aVal < minRMSD) {
                    minRMSD = aVal;
                    minPhase0 = phase0;
                    minPhase1 = phase1;
                }
            }
        }
        // add penalty of 5% per degree to value of p1
        p1Penalty = Math.abs(minRMSD) * p1PenaltyWeight / 360.0;
        double[] phases = doNMMin(minPhase0 - stepSize0 / 2,
                minPhase0 + stepSize0 / 2, minPhase1 - stepSize1 / 2,
                minPhase1 + stepSize1 / 2);
        int checkSign = getSign(phases[0], phases[1]);
        if (checkSign < 0) {
            phases[0] += 180.0;
        }

        while (phases[0] > 180) {
            phases[0] -= 360.0;
        }
        while (phases[0] < -180) {
            phases[0] += 360.0;
        }

        return phases;

    }

    public double testFit(double phase0, double phase1) {
        return testEnds(phase0, phase1);
    }

    void useRegions(Vec vector, boolean maxMode) {
        boolean[] signalRegion;
        if (useRegion && ((ppmStart != null) && (ppmEnd != null))) {
            int pt0 = vector.refToPt(ppmStart);
            int pt1 = vector.refToPt(ppmEnd);
            if (pt0 > pt1) {
                int hold = pt0;
                pt0 = pt1;
                pt1 = hold;
            }
            pt0 = Math.max(0, pt0);
            pt1 = Math.min(pt1, vector.getSize() - 1);
            signalRegion = new boolean[vector.getSize()];
            Arrays.fill(signalRegion, true);
            for (int i = pt0; i <= pt1; i++) {
                signalRegion[i] = false;
            }
        } else {
            signalRegion = vector.getSignalRegion();
        }
        ArrayList<Integer> bListTemp = new ArrayList<>();
        int startBase = 0;
        start = 0;
        end = vector.getSize() - 1;
        for (int i = start; i <= end; i++) {
            if (!signalRegion[i]) {
                startBase = i;
                break;
            }
        }
        boolean currentBaseline = true;
        bListTemp.add(startBase);
        int lastBaseline = 0;
        for (int i = startBase; i <= end; i++) {
            if (currentBaseline) {
                if (signalRegion[i]) {
                    bListTemp.add(i);
                    currentBaseline = false;
                } else {
                    lastBaseline = i;
                }
            } else if (!signalRegion[i]) {
                bListTemp.add(i);
                currentBaseline = true;
            }
        }
        if (currentBaseline) {
            bListTemp.add(lastBaseline);
        }
        bList.clear();
        for (int i = 0; i < bListTemp.size(); i += 2) {
            int reg1 = bListTemp.get(i);
            int reg2 = bListTemp.get(i + 1);
            int delta = reg2 - reg1 + 1;
            int base2 = reg1 + delta / 4;
            int sig1 = base2 + 1;
            int base3 = reg2 - delta / 4;
            int sig2 = base3 - 1;

            RegionPositions regPos = new RegionPositions(reg1, base2, sig1, sig2, base3, reg2);
            bList.add(regPos);
        }
        genEndsList(maxMode);

    }

    public final void addVector(Vec vector, final boolean maxMode, double ratio) {
        addVector(vector, maxMode, ratio, IDBaseline2.ThreshMode.SDEV);
    }

    public final void addVector(Vec vector, final boolean maxMode, double ratio, IDBaseline2.ThreshMode threshMode) {
        this.vector = vector;
        testVec = new Vec(vector.getSize());
        Vec phaseVec = new Vec(vector.getSize());
        testVec.resize(vector.getSize(), false);
        vector.copy(phaseVec);
        phaseVec.hft();
        rvec = new double[phaseVec.getSize()];
        ivec = new double[phaseVec.getSize()];
        values = new double[phaseVec.getSize()];
        for (int i = 0; i < phaseVec.getSize(); i++) {
            rvec[i] = phaseVec.getReal(i);
            ivec[i] = phaseVec.getImag(i);
        }
        dVec = new Vec(vector.getSize());
        dVec.resize(vector.getSize(), vector.isComplex());
        vector.copy(dVec);
        (new Cwtd(winSize)).eval(dVec);
        dVec.abs();
        if ((useRegion && (ppmStart != null) && (ppmEnd != null)) || (vector.getSignalRegion() != null)) {
            useRegions(vector, maxMode);
            return;
        }

        int[] limits = new int[2];
        int edgeSize = 5;
        IDBaseline2 idbase = new IDBaseline2(edgeSize, limits, ratio, threshMode);
        idbase.eval(dVec);
        hasSignal = idbase.getResult();

        if ((limits[0] == 0) && (limits[1] == 0)) {
            return;
        }
        // PAR
        start = limits[0] - 64;
        if (start < edgeSize) {
            start = edgeSize;
        }
        end = limits[1] + 64;
        if (end >= (vector.getSize() - edgeSize)) {
            end = vector.getSize() - edgeSize;
        }
        int startBase = 0;
        for (int i = start; i <= end; i++) {
            if (!hasSignal[i]) {
                startBase = i;
                break;
            }
        }
        bList.clear();
        boolean currentBaseline = true;
        ArrayList<Integer> bListTemp = new ArrayList<>();
        bListTemp.add(startBase);
        int lastBaseline = 0;
        for (int i = startBase; i <= end; i++) {
            if (currentBaseline) {
                if (hasSignal[i]) {
                    bListTemp.add(i);
                    currentBaseline = false;
                } else {
                    lastBaseline = i;
                }
            } else if (!hasSignal[i]) {
                bListTemp.add(i);
                currentBaseline = true;
            }
        }
        if (currentBaseline) {
            bListTemp.add(lastBaseline);
        }
        if (bListTemp.size() < 4) {
            return;
        }

        int maxRegionSize = testVec.getSize() / 16;

        for (int i = 1; i < (bListTemp.size() - 2); i += 2) {
            int base1 = bListTemp.get(i - 1);
            int sig1 = bListTemp.get(i);
            int sig2 = bListTemp.get(i + 1);
            int base4 = bListTemp.get(i + 2);
            int test = sig1 - maxRegionSize;
            if (test > base1) {
                base1 = test;
            }

            test = sig2 + maxRegionSize;
            if (test < base4) {
                base4 = test;
            }

            RegionPositions regPos = new RegionPositions(base1, sig1, sig1, sig2, sig2, base4);
            bList.add(regPos);
        }
        genEndsList(maxMode);
    }

    public void genTest(double p0, double p1) {

        double tol = 0.0001;

        if (Math.abs(p1) < tol) {
            if (Math.abs(p0) < tol) {
                return;
            }
            double re = Math.cos(p0 * DEGTORAD);
            double im = -Math.sin(p0 * DEGTORAD);

            for (int i = 0; i < vector.getSize(); i++) {
                testVec.set(i, (rvec[i] * re) - (ivec[i] * im));
            }
            return;
        }

        double dDelta = p1 / (vector.getSize() - 1);
        for (int i = 0; i < vector.getSize(); i++) {
            double p = p0 + i * dDelta;
            double re = Math.cos(p * DEGTORAD);
            double im = -Math.sin(p * DEGTORAD);
            testVec.set(i, (rvec[i] * re) - (ivec[i] * im));

        }
    }

    public double[] minZero(double ax, double cx) {
        double x1;
        double x2;
        double f1;
        double f2;
        double r = 0.3819660;
        double c = 1.0 - r;
        double bx = (ax + cx) / 2;
        double x0 = ax;
        double x3 = cx;

        if (Math.abs(cx - bx) > Math.abs(bx - ax)) {
            x1 = bx;
            x2 = bx + (c * (cx - bx));
        } else {
            x2 = bx;
            x1 = bx - (c * (bx - ax));
        }

        f1 = testEnds(x1);
        f2 = testEnds(x2);

        while (Math.abs(x3 - x0) > 1.0) {
            if (f2 < f1) {
                x0 = x1;
                x1 = x2;
                x2 = (r * x1) + (c * x3);
                f1 = f2;
                f2 = testEnds(x2);
            } else {
                x3 = x2;
                x2 = x1;
                x1 = (r * x2) + (c * x0);
                f2 = f1;
                f1 = testEnds(x1);
            }
        }
        double[] minPhase = new double[2];
        if (f1 < f2) {
            minPhase[0] = x1;
        } else {
            minPhase[0] = x2;
        }
        return minPhase;

    }

    public double[] doNMMin(double p0a, double p0b, double p1a, double p1b) {
        double[] startPoint = new double[2];
        startPoint[0] = (p0a + p0b) / 2.0;
        startPoint[1] = (p1a + p1b) / 2.0;
        int nSteps = 500;
        double extra = (p0b - p0a) / 2.0;
        double[] lowerBounds = {p0a - extra, p1a - extra};
        double[] upperBounds = {p0b + extra, p1b + extra};

        BOBYQAOptimizer optim = new BOBYQAOptimizer(5, 5.0, 1.0e-2);
        PointValuePair rpVP = null;
        try {
            rpVP = optim.optimize(
                    new MaxEval(nSteps),
                    new ObjectiveFunction(this), GoalType.MINIMIZE,
                    new SimpleBounds(lowerBounds, upperBounds),
                    new InitialGuess(startPoint));
        } catch (TooManyEvaluationsException e) {
            log.warn("too many evalu");
        }

        if (rpVP == null) {
            return null;
        } else {
            return rpVP.getPoint();
        }
    }

    @Override
    public double value(double[] values) {
        if (values.length == 1) {
            return testEnds(values[0]);
        } else {
            double endCost = testEnds(values[0], values[1]);
            if (Math.abs(values[1]) > 90.0) {
                endCost += (Math.abs(values[1]) - 90.0) * p1Penalty;
            }
            return endCost;
        }
    }

    double testEnds(double p0) {
        return testEnds(p0, 0.0);
    }

    record RegionPositions(int base1, int base2, int sig1, int sig2, int base3, int base4) {

        @Override
        public String toString() {
            StringBuilder sBuf = new StringBuilder();
            sBuf.append(base1);
            sBuf.append(" ");
            sBuf.append(base2);
            sBuf.append(" ");
            sBuf.append(sig1);
            sBuf.append(" ");
            sBuf.append(sig2);
            sBuf.append(" ");
            sBuf.append(base3);
            sBuf.append(" ");
            sBuf.append(base4);
            return sBuf.toString();
        }
    }

    record RegionData(double meanR, double meanI, int center, int size) {

    }

    static class BRegionData {

        final RegionData region1;
        final RegionData region2;
        final double max;
        final double centerR;
        final double centerI;
        final int centerPos;

        BRegionData(RegionData region1, RegionData region2, double max, int centerPos, double centerSumR, double centerSumI) {
            this.region1 = region1;
            this.region2 = region2;
            this.max = max;
            this.centerR = centerSumR;
            this.centerI = centerSumI;
            this.centerPos = centerPos;
        }

        @Override
        public String toString() {
            StringBuilder sBuf = new StringBuilder();
            sBuf.append(region1.size);
            sBuf.append(" ");
            sBuf.append(region1.center);
            sBuf.append(" ");
            sBuf.append(region1.meanR);
            sBuf.append(" ");
            sBuf.append(region1.meanI);
            sBuf.append(" ");
            sBuf.append(region2.size);
            sBuf.append(" ");
            sBuf.append(region2.center);
            sBuf.append(" ");
            sBuf.append(region2.meanR);
            sBuf.append(" ");
            sBuf.append(region2.meanI);
            sBuf.append(" ");
            sBuf.append(max);
            sBuf.append(" ");
            sBuf.append(centerR);
            sBuf.append(" ");
            sBuf.append(centerI);
            return sBuf.toString();
        }
    }

    void genEndsList(final boolean maxMode) {
        double max = 0.0;
        BRegionData bRDMax = null;
        for (RegionPositions rPos : bList) {
            int start1 = rPos.base1;
            int end1 = rPos.base2;
            int start2 = rPos.base3;
            int end2 = rPos.base4;
            int maxRegionSize = testVec.getSize() / 16;
            if (maxRegionSize < 4) {
                maxRegionSize = 4;
            }
            int r1Start = end1 - maxRegionSize;
            if (r1Start < 0) {
                r1Start = 0;
            }
            if (r1Start < start1) {
                r1Start = start1;
            }
            int r2End = start2 + maxRegionSize;
            if (r2End > testVec.getSize()) {
                r2End = testVec.getSize();
            }
            if (r2End > end2) {
                r2End = end2;
            }

            int regionSize = 0;
            DescriptiveStatistics rStat = new DescriptiveStatistics();
            DescriptiveStatistics iStat = new DescriptiveStatistics();
            for (int j = r1Start; j < end1; j++) {
                regionSize++;
                rStat.addValue(rvec[j]);
                iStat.addValue(ivec[j]);
            }
            if (regionSize < 4) {
                continue;
            }
            double meanR = rStat.getPercentile(50.0);
            double meanI = iStat.getPercentile(50.0);
            int regionCenter = (r1Start + end1) / 2;
            RegionData region1 = new RegionData(meanR, meanI, regionCenter, regionSize);

            double absMaxSq = 0.0;
            double centerR = 0.0;
            double centerI = 0.0;
            for (int j = (end1 + 1); j < start2; j++) {
                double real = rvec[j];
                double imag = ivec[j];
                double absValSq = real * real + imag * imag;
                if (absValSq > absMaxSq) {
                    absMaxSq = absValSq;
                    centerR = real;
                    centerI = imag;
                }
            }
            int centerPos = (start2 + end1) / 2;
            double absMax = Math.sqrt(absMaxSq);

            regionSize = 0;
            rStat.clear();
            iStat.clear();
            for (int j = start2; j < r2End; j++) {
                regionSize++;
                rStat.addValue(rvec[j]);
                iStat.addValue(ivec[j]);
            }
            if (regionSize < 4) {
                continue;
            }
            meanR = rStat.getPercentile(50.0);
            meanI = iStat.getPercentile(50.0);

            regionCenter = (start2 + r2End) / 2;
            RegionData region2 = new RegionData(meanR, meanI, regionCenter, regionSize);
            BRegionData bRD = new BRegionData(region1, region2, absMax, centerPos, centerR, centerI);
            if (maxMode) {
                if (absMax > max) {
                    max = absMax;
                    bRDMax = bRD;
                }
            } else {
                b2List.add(bRD);
            }
        }
        if (bRDMax != null) {
            b2List.add(bRDMax);
        }
    }

    public double testEnds(double p0, double p1) {
        return switch (mode) {
            case 0 -> testEndsDeltaMean(p0, p1);
            case 1 -> getDerivEntropyMeasure(p0, p1);
            default -> getDeltaMax(p0, p1);
        };
    }

    public double testEndsDeltaMean(double p0, double p1) {
        double sum = 0.0;
        for (BRegionData regData : b2List) {
            double meanR1 = regData.region1.meanR;
            double meanI1 = regData.region1.meanI;
            double meanR2 = regData.region2.meanR;
            double meanI2 = regData.region2.meanI;
            double sigMax = regData.max;
            int center1 = regData.region1.center;
            int center2 = regData.region2.center;

            double tol = 0.0001;
            double value1 = meanR1;
            double value2 = meanR2;
            if (Math.abs(p1) < tol) {
                if (Math.abs(p0) > tol) {
                    double re = Math.cos(p0 * DEGTORAD);
                    double im = -Math.sin(p0 * DEGTORAD);
                    value1 = (meanR1 * re) - (meanI1 * im);
                    value2 = (meanR2 * re) - (meanI2 * im);
                }
            } else {

                double dDelta = p1 / (vector.getSize() - 1);

                double p = p0 + center1 * dDelta;
                double re = Math.cos(p * DEGTORAD);
                double im = -Math.sin(p * DEGTORAD);
                value1 = (meanR1 * re) - (meanI1 * im);

                p = p0 + center2 * dDelta;
                re = Math.cos(p * DEGTORAD);
                im = -Math.sin(p * DEGTORAD);
                value2 = (meanR2 * re) - (meanI2 * im);
            }

            double delta = value2 - value1;
            // PAR
            sum += (delta * delta) * Math.sqrt(Math.abs(sigMax));
        }
        return sum;
    }

    public double getDeltaMax(double p0, double p1) {
        double dDelta = p1 / (vector.getSize() - 1);
        double sum = 0.0;
        for (var reg : bList) {
            int regStart = reg.base1 + (reg.base2 - reg.base1) / 2;
            int regEnd = reg.base3 + (reg.base4 - reg.base3) / 2;
            double sumBase = 0.0;
            int n = 0;
            for (int i = regStart; i < reg.sig1; i++) {
                double p = p0 + i * dDelta;
                double re = Math.cos(p * DEGTORAD);
                double im = -Math.sin(p * DEGTORAD);
                sumBase += rvec[i] * re - ivec[i] * im;
                n++;
            }
            for (int i = reg.sig2; i < regEnd; i++) {
                double p = p0 + i * dDelta;
                double re = Math.cos(p * DEGTORAD);
                double im = -Math.sin(p * DEGTORAD);
                sumBase += rvec[i] * re - ivec[i] * im;
                n++;
            }
            double avg = n == 0 ? 0.0 : sumBase / n;
            double regionSum = 0.0;
            for (int i = reg.sig1; i <= reg.sig2; i++) {
                double p = p0 + i * dDelta;
                double re = Math.cos(p * DEGTORAD);
                double im = -Math.sin(p * DEGTORAD);
                double delta = (rvec[i] * re - ivec[i] * im) - avg;
                if (delta < 0.0) {
                    regionSum += delta;
                }
            }
            sum += regionSum;
        }
        sum *= -1.0;
        return sum;
    }

    public double getDerivEntropyMeasure(double p0, double p1) {
        double dDelta = p1 / (vector.getSize() - 1);
        int k = 0;
        double maxSqr = Double.NEGATIVE_INFINITY;
        int incr = 9;
        for (var reg : bList) {
            int regStart = reg.base1 + (reg.base2 - reg.base1) / 8;
            int regEnd = reg.base3 + (reg.base4 - reg.base3) / 8;
            for (int i = regStart; i <= regEnd; i += incr) {
                double sum = 0.0;
                double sqr = 0.0;
                for (int j = 0; j < incr; j++) {
                    int ij = i + j;
                    if ((ij >= 0) && (ij < vector.getSize())) {
                        double p = p0 + ij * dDelta;
                        double re = FastMath.cos(p * DEGTORAD);
                        double im = -FastMath.sin(p * DEGTORAD);
                        sum += rvec[ij] * re - ivec[ij] * im;
                        sqr += rvec[ij] * rvec[ij] + ivec[ij] * ivec[ij];
                    }
                }
                maxSqr = Math.max(sqr, maxSqr);
                values[k++] = sum;
            }
        }

        double penalty = 0.0;
        double entropy = 0.0;
        if (k > 2) {
            double prev = values[0];
            double sumDeriv = 0.0;
            for (int i = 1; i < k - 1; i++) {
                double next = values[i + 1];
                double deriv = (next - prev) / 2.0;
                double value = values[i];
                if (value < 0.0) {
                    penalty += (value * value) / maxSqr;
                }
                prev = values[i];
                values[i] = deriv;
                sumDeriv += Math.abs(deriv);
            }
            if (sumDeriv > 0.0) {
                for (int i = 1; i < k - 1; i++) {
                    double value = values[i];
                    double h = Math.abs(value) / sumDeriv;
                    if (h > 1.0e-12) {
                        entropy += -h * Math.log(h);
                    }
                }
            }
        }

        penalty *= negativePenalty;
        return entropy + penalty;
    }

    private double regionSum(int first, int last, double p0, double p1) {
        double tol = 0.0001;
        double dDelta = p1 / (vector.getSize() - 1);
        double sum = 0.0;
        for (int i = first; i <= last; i++) {
            double real = rvec[i];
            double imag = ivec[i];
            double re;
            double im;
            if (Math.abs(p1) < tol) {
                re = Math.cos(p0 * DEGTORAD);
                im = -Math.sin(p0 * DEGTORAD);
            } else {
                double p = p0 + i * dDelta;
                re = Math.cos(p * DEGTORAD);
                im = -Math.sin(p * DEGTORAD);
            }
            sum += (real * re) - (imag * im);
        }
        return sum;
    }

    private int getSign(double p0, double p1) {
        if (useRegionSign) {
            return getRegionSign(p0, p1);
        } else {
            return getVectorSign(p0, p1);
        }
    }

    private int getVectorSign(double p0, double p1) {
        double sum = 0.0;
        for (RegionPositions regData : bList) {
            double r1 = regionSum(regData.base1, regData.base2, p0, p1);
            double r2 = regionSum(regData.base3, regData.base4, p0, p1);
            double m1 = r1 / (regData.base2 - regData.base1 + 1);
            double m2 = r2 / (regData.base4 - regData.base3 + 1);
            double c = regionSum(regData.sig1, regData.sig2, p0, p1);
            double mean = (m1 + m2) / 2;
            double c2 = c - mean * (regData.sig2 - regData.sig1 + 1);
            if (c2 < 0.0) {
                c2 = -Math.sqrt(-c2);
            } else {
                c2 = Math.sqrt(c2);
            }
            sum += c2;
        }
        if (sum < 0.0) {
            return -1;
        } else {
            return 1;
        }
    }

    public int getRegionSign(double p0, double p1) {
        double tol = 0.0001;
        double dDelta = p1 / (vector.getSize() - 1);
        double sumPlus = 0;
        double sumMinus = 0;
        for (BRegionData regData : b2List) {
            double real = regData.centerR;
            double imag = regData.centerI;
            double center = regData.centerPos;
            double value;
            if (Math.abs(p1) < tol) {
                if (Math.abs(p0) > tol) {
                    double re = Math.cos(p0 * DEGTORAD);
                    double im = -Math.sin(p0 * DEGTORAD);
                    value = (real * re) - (imag * im);
                } else {
                    value = real;
                }
            } else {
                double p = p0 + center * dDelta;
                double re = Math.cos(p * DEGTORAD);
                double im = -Math.sin(p * DEGTORAD);
                value = (real * re) - (imag * im);
            }
            // use sqrt to lower impact of super strong peaks (like h2o, which could be antiphase to rest of signals)
            if (value > 0.0) {
                sumPlus += FastMath.sqrt(value);
            } else if (value < 0.0) {
                sumMinus += FastMath.sqrt(-value);
            }
        }
        if (sumMinus > sumPlus) {
            return -1;
        } else {
            return 1;
        }
    }

}
