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

import org.nmrfx.processor.math.*;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.FastMath;

/**
 *
 * @author brucejohnson
 */
public class TestBasePoints implements MultivariateFunction {

    int winSize = 0;
    double totalPhase = 0.0;
    double negativePenalty = 1.0e-5;
    Vec testVec = null;
    double[] rvec = null;
    double[] ivec = null;
    double[] values = null;

    Vec dVec = null;
    Vec vector = null;
    int netSign = 0;
    int start = 0;
    int end = 0;
    boolean[] hasSignal = null;
    double mean = 0.0;
    double avgDev = 0.0;
    double p1Penalty = 0.0;
    double p1PenaltyWeight = 0.02;
    static public double TOL_REL = 1.0e-4;
    static public double TOL_ABS = 1.0e-5;
    static double DEGTORAD = Math.PI / 180.0;
    int mode = 0;

    // list of start and end of baseline regions
    ArrayList<RegionPositions> bList = new ArrayList<>();
    ArrayList<BRegionData> b2List = new ArrayList<>();
    static HashMap<String, TestBasePoints> tbMap = new HashMap<>();

    public TestBasePoints(Vec vector, int winSize, double ratio, int mode, double negativePenalty) {
        this.winSize = winSize;
        this.mode = mode;
        this.negativePenalty = negativePenalty;
        addVector(vector, false, ratio);
    }

    public TestBasePoints(int winSize) {
        this.winSize = winSize;
    }

    public TestBasePoints(int winSize, String name) {
        this.winSize = winSize;
        tbMap.put(name, this);
    }

    public static TestBasePoints get(String name) {
        return tbMap.get(name);
    }

    public static void remove(String name) {
        tbMap.remove(name);
    }

    public int getRegionCount() {
        return b2List.size();
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
        //System.out.println(minPhase0);//+minPhase1);
        double phases[] = minZero(minPhase0 - stepSize0, minPhase0 + stepSize0);
        //   System.out.println(phases[0]+" "+phases[1]);
//        genTest(phases[0], 0.0);
//        int checkSign = getNetSign();
        int checkSign = getSign(phases[0], 0.0);

        //System.out.println(netSign);
        if (checkSign < 0) {
            //System.out.println("0.001");
            phases[0] += 180.0;
        }

        while (phases[0] > 180) {
            phases[0] -= 360.0;
        }
        while (phases[0] < -180) {
            phases[0] += 360.0;
        }
        //for (int i = 0; i < phases.length; ++i)
        //    System.out.println(phases[i]);
        return phases;

    }

    public void setP1PenaltyWeight(double value) {
        p1PenaltyWeight = value;
    }

    public double[] autoPhase(double ph1Limit) {
        if (bList.size() == 1) {
            return autoPhaseZero();
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
//         System.out.println("test "+aVal + " " + phase0+" "+phase1);

                if (aVal < minRMSD) {
                    minRMSD = aVal;
                    minPhase0 = phase0;
                    minPhase1 = phase1;
                }
            }
        }
        // add penalty of 5% per degree to value of p1
        p1Penalty = Math.abs(minRMSD) * p1PenaltyWeight / 360.0;
//         System.out.println("gridMin "+minRMSD + " " + minPhase0+" "+minPhase1);
        double phases[] = doNMMin(minPhase0 - stepSize0 / 4, minPhase0 + stepSize0 / 4, minPhase1 - stepSize1 / 4, minPhase1 + stepSize1 / 4, ph1Limit);
//          System.out.println(phases[0]+" "+phases[1]);
        int checkSign = getSign(phases[0], phases[1]);
        //System.out.println(netSign);
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

    void useRegions(Vec vector, boolean maxMode) {
        boolean[] signalRegion = vector.getSignalRegion();
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
        // System.out.println(startBase+" base");
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
            int base1 = reg1;
            int base2 = reg1 + delta / 4;
            int sig1 = base2 + 1;
            int base3 = reg2 - delta / 4;
            int sig2 = base3 - 1;
            int base4 = reg2;

            RegionPositions regPos = new RegionPositions(base1, base2, sig1, sig2, base3, base4);
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
        if (vector.getSignalRegion() != null) {
            useRegions(vector, maxMode);
            return;
        }

        int[] limits = new int[2];
        int edgeSize = 5;
        IDBaseline2 idbase = new IDBaseline2(edgeSize, limits, ratio, threshMode);
        idbase.eval(dVec);
        hasSignal = idbase.getResult();

        //hasSignal = dVec.idBaseline2(5, limits, ratio);
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
        //System.out.println(start+" "+end);
        //dumpBase();

        //   dumpBase();
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
        // System.out.println(startBase+" base");
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
            // System.out.println("blistsize "+bList.size());
            return;
        }

        int maxRegionSize = testVec.getSize() / 16;

        for (int i = 1; i < (bListTemp.size() - 2); i += 2) {
            int base1 = bListTemp.get(i - 1);
            int sig1 = bListTemp.get(i);
            int sig2 = bListTemp.get(i + 1);
            int base4 = bListTemp.get(i + 2);
            int base2 = sig1;
            int test = base2 - maxRegionSize;
            if (test > base1) {
                base1 = test;
            }

            int base3 = sig2;
            test = base3 + maxRegionSize;
            if (test < base4) {
                base4 = test;
            }

            RegionPositions regPos = new RegionPositions(base1, base2, sig1, sig2, base3, base4);
            bList.add(regPos);
        }
        genEndsList(maxMode);
    }

    void dumpBase() {
        boolean currentBaseline = !hasSignal[start - 1];
        for (int i = start; i <= end; i++) {
            if (currentBaseline) {
                if (hasSignal[i]) {
                    System.out.println(i + " toSig");
                    currentBaseline = false;
                }
            } else if (!hasSignal[i]) {
                currentBaseline = true;
                System.out.println(i + " toBase");
            }
        }
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
            //System.out.println(testVec.get(i));

        }
    }

    double test(double newPhase) {
        genTest(newPhase, 0.0);
        double sum = 0.0;
        int nValues = 0;
        double a = 0.0;
        double b = 0.0;
        double c;
        for (int i = start; i <= end; i++) {
            if (!hasSignal[i]) {
                c = testVec.getReal(i);
                if (nValues == 2) {
                    double delta = c + a - 2 * b;
                    sum += delta * delta;
                } else {
                    nValues++;
                }
                a = b;
                b = c;
            }
        }
        return sum;
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
            //System.out.println(x1+" "+f1+ " "+x2+" "+f2);
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

    public double[] doNMMin(double p0a, double p0b, double p1a, double p1b, double ph1Limit) {
        double[][] vertices = new double[3][2];
        vertices[0][0] = p0a;
        vertices[0][1] = p1a;
        vertices[1][0] = p0b;
        vertices[1][1] = p1b;
        vertices[2][0] = p0a;
        vertices[2][1] = p1b;
        double[] startPoint = new double[2];
        startPoint[0] = (p0a + p0b) / 2.0;
        startPoint[1] = (p1a + p1b) / 2.0;
        int nSteps = 500;
        double[] lowerBounds = {-360.0, -ph1Limit};
        double[] upperBounds = {360.0, ph1Limit};

        BOBYQAOptimizer optim = new BOBYQAOptimizer(5, 10.0, 1.0e-2);
        PointValuePair rpVP = null;
        try {
            rpVP = optim.optimize(
                    new MaxEval(nSteps),
                    new ObjectiveFunction(this), GoalType.MINIMIZE,
                    new SimpleBounds(lowerBounds, upperBounds),
                    new InitialGuess(startPoint));
        } catch (TooManyEvaluationsException e) {
            System.out.println("too many evalu");
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
        if (mode == 0) {
            return testEnds(p0, 0.0);
        } else {
            return getEntropyMeasure(p0, 0.0);
        }
    }

    class RegionPositions {

        final int base1;
        final int base2;
        final int sig1;
        final int sig2;
        final int base3;
        final int base4;

        RegionPositions(int base1, int base2, int sig1, int sig2, int base3, int base4) {
            this.base1 = base1;
            this.base2 = base2;
            this.sig1 = sig1;
            this.sig2 = sig2;
            this.base3 = base3;
            this.base4 = base4;
        }

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

    class RegionData {

        final double meanR;
        final double meanI;
        final int center;
        final int size;

        RegionData(final double meanR, final double meanI, final int center, final int size) {
            this.meanR = meanR;
            this.meanI = meanI;
            this.size = size;
            this.center = center;
        }
    }

    class BRegionData {

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
            return sBuf.toString();
        }
    }

    int[] getBaseRegion() {
        int[] bounds = new int[2];
        for (int i = start; i <= end; i++) {
            if (!hasSignal[i]) {
                bounds[0] = i;
                break;
            }
        }
        for (int i = bounds[0]; i <= end; i++) {
            if (hasSignal[i]) {
                bounds[1] = i - 1;
                break;
            }

        }
        return bounds;

    }

    void genEndsList(final boolean maxMode) {
        int n = bList.size();
        double max = 0.0;
        BRegionData bRDMax = null;
        // PAR
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
            //int r2End = oneThird2;
            int r2End = start2 + maxRegionSize;
            if (r2End > testVec.getSize()) {
                r2End = testVec.getSize();
            }
            if (r2End > end2) {
                r2End = end2;
            }
//            System.out.println(start1 + " " + r1Start + " " + end1 + " " + start2 + " " + r2End + " " + end2);

            int regionSize = 0;
            double regionSumR = 0.0;
            double regionSumI = 0.0;
            DescriptiveStatistics rStat = new DescriptiveStatistics();
            DescriptiveStatistics iStat = new DescriptiveStatistics();
            for (int j = r1Start; j < end1; j++) {
                regionSumR += rvec[j];
                regionSumI += ivec[j];
                regionSize++;
                rStat.addValue(rvec[j]);
                iStat.addValue(ivec[j]);
            }
            if (regionSize < 4) {
                continue;
            }
            double meanR = rStat.getPercentile(50.0);
            double meanI = iStat.getPercentile(50.0);
            //double meanR = regionSumR / regionSize;
//            double meanI = regionSumI / regionSize;
            int regionCenter = (r1Start + end1) / 2;
            RegionData region1 = new RegionData(meanR, meanI, regionCenter, regionSize);

            double absMaxSq = 0.0;
            regionSumR = 0.0;
            regionSumI = 0.0;
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
            regionSumR = 0.0;
            regionSumI = 0.0;
            rStat.clear();
            iStat.clear();
            for (int j = start2; j < r2End; j++) {
                regionSumR += rvec[j];
                regionSumI += ivec[j];
                regionSize++;
                rStat.addValue(rvec[j]);
                iStat.addValue(ivec[j]);
            }
            if (regionSize < 4) {
                continue;
            }
            meanR = rStat.getPercentile(50.0);
            meanI = iStat.getPercentile(50.0);

//            meanR = regionSumR / regionSize;
//            meanI = regionSumI / regionSize;
            regionCenter = (start2 + r2End) / 2;
            RegionData region2 = new RegionData(meanR, meanI, regionCenter, regionSize);
            BRegionData bRD = new BRegionData(region1, region2, absMax, centerPos, centerR, centerI);
            //    System.out.println(bRD);
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

    public String dumpEnds() {
        StringBuilder sBuf = new StringBuilder();
        for (BRegionData regData : b2List) {
            sBuf.append(regData.toString());
            sBuf.append("\n");
        }
        return sBuf.toString();

    }

    public double testEnds(double p0, double p1) {
        if (mode == 0) {
            return testEndsDeltaMean(p0, p1);
        } else {
            return getEntropyMeasure(p0, p1);
        }
    }

    public double testEndsDeltaMean(double p0, double p1) {
        int nValues = 0;
        double a = 0.0;
        double b = 0.0;
        double c = 0.0;
        double sum = 0.0;
        int nRegions = b2List.size();
        int iRegion = -1;
        for (BRegionData regData : b2List) {
            iRegion++;
//            if ((iRegion > 6) && (iRegion < (nRegions-6))) {
//                continue;
//            }
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
                //    System.out.println(center1+" "+center2+" "+dDelta+" "+value1+" "+value2);
            }

            double delta = value2 - value1;
            // PAR
            sum += (delta * delta) * Math.sqrt(Math.abs(sigMax));
        }
        //System.out.println(nRegions + " " + p0 + " " + p1 + " " + sum);
        return sum;
    }

    double projectPoint(RegionData region, double p0, double p1, int projectionPoint) {
        SimpleRegression sRegr = new SimpleRegression();
        double tol = 0.0001;
        int regionStart = region.center - region.size / 2;
        int regionEnd = region.center + region.size / 2;

        if (Math.abs(p1) < tol) {
            double re = Math.cos(p0 * DEGTORAD);
            double im = -Math.sin(p0 * DEGTORAD);
            for (int i = regionStart; i <= regionEnd; i++) {
                double value = rvec[i] * re - ivec[i] * im;
                sRegr.addData(i, value);
            }

        } else {
            double dDelta = p1 / (vector.getSize() - 1);
            for (int i = regionStart; i <= regionEnd; i++) {
                double p = p0 + i * dDelta;
                double re = Math.cos(p * DEGTORAD);
                double im = -Math.sin(p * DEGTORAD);
                double value = rvec[i] * re - ivec[i] * im;
                sRegr.addData(i, value);
            }

        }
        double value = sRegr.predict(projectionPoint);
        return value;
    }

    public double testEndsProjection(double p0, double p1) {
        double sum = 0.0;
        for (BRegionData regData : b2List) {
            RegionData region1 = regData.region1;
            RegionData region2 = regData.region2;
            int center = ((region1.center + region1.size / 2) + (region2.center - region2.size / 2)) / 2;

            double value1 = projectPoint(region1, p0, p1, center);
            double value2 = projectPoint(region2, p0, p1, center);

            double delta = value2 - value1;
            // PAR
            sum += (delta * delta) * Math.sqrt(Math.abs(regData.max));
        }
        //  System.out.println(p0+" "+p1+" "+sum);
        return sum;
    }

    public double testMax2(double p0, double p1) {
        double tol = 0.0001;
        double sum = 0.0;
        int n = dVec.getSize();
        double dDelta = p1 / (vector.getSize() - 1);
        for (int i = 0; i < n; i++) {
            if (!hasSignal[i]) {
                final double value;
                if (Math.abs(p1) < tol) {
                    double re = Math.cos(p0 * DEGTORAD);
                    double im = -Math.sin(p0 * DEGTORAD);
                    value = dVec.getReal(i) * re - dVec.getImag(i) * im;
                } else {
                    double p = p0 + i * dDelta;
                    double re = Math.cos(p * DEGTORAD);
                    double im = -Math.sin(p * DEGTORAD);
                    value = dVec.getReal(i) * re - dVec.getImag(i) * im;
                }
                sum += Math.abs(value);
            }
        }
        return sum;
    }

    public double testMax(double p0, double p1) {
        double tol = 0.0001;
        double sum = 0.0;
        int n = dVec.getSize();
        double dDelta = p1 / (vector.getSize() - 1);
        for (BRegionData regData : b2List) {
            for (int j = 0; j < 2; j++) {

                RegionData region;
                if (j == 0) {
                    region = regData.region1;
                } else {
                    region = regData.region2;
                }
                int pt1 = region.center - region.size / 2;
                int pt2 = region.center + region.size / 2;
                for (int i = pt1; i < pt2; i++) {

                    final double value;
                    if (Math.abs(p1) < tol) {
                        double re = Math.cos(p0 * DEGTORAD);
                        double im = -Math.sin(p0 * DEGTORAD);
                        value = dVec.getReal(i) * re - dVec.getImag(i) * im;
                    } else {
                        double p = p0 + i * dDelta;
                        double re = Math.cos(p * DEGTORAD);
                        double im = -Math.sin(p * DEGTORAD);
                        value = dVec.getReal(i) * re - dVec.getImag(i) * im;
                    }
                    sum += Math.abs(value);
                }

            }
        }
        return sum;
    }

    public double getEntropyMeasure(double p0, double p1) {
        int n = bList.size();
        double sumAbs = 0.0;
        double dDelta = p1 / (vector.getSize() - 1);
        RegionPositions rPos1 = bList.get(0);
        RegionPositions rPos2 = bList.get(n - 1);
        int start1 = rPos1.base1;
        int end1 = rPos1.base2;

        double sum = 0.0;
        int totalPoints = 0;
        for (int j = start1; j < end1; j++) {
            double p = p0 + j * dDelta;
            double re = FastMath.cos(p * DEGTORAD);
            double im = -FastMath.sin(p * DEGTORAD);
            double value = rvec[j] * re - ivec[j] * im;
            sum += value;
            totalPoints++;
        }
        int start2 = rPos2.base3;
        int end2 = rPos2.base4;
        for (int j = start2; j < end2; j++) {
            double p = p0 + j * dDelta;
            double re = FastMath.cos(p * DEGTORAD);
            double im = -FastMath.sin(p * DEGTORAD);
            double value = rvec[j] * re - ivec[j] * im;
            sum += value;
            totalPoints++;
        }
        double meanBase = sum / totalPoints;
        int incr = 4;

        int k = 0;
        for (int j = end1; j < start2; j += incr) {
            double p = p0 + j * dDelta;
            double re = FastMath.cos(p * DEGTORAD);
            double im = -FastMath.sin(p * DEGTORAD);
            double value = rvec[j] * re - ivec[j] * im;

            double delta = value - meanBase;
            double adelta = FastMath.abs(delta);
            sumAbs += adelta;
            values[k++] = delta;
        }

        double penalty = 0.0;
        double entropy = 0.0;
        for (int i = 0; i < k; i++) {
            double value = values[i];

            if (value == 0.0) {
                continue;
            }
            double h = value / sumAbs;
            if (h < 0.0) {
                penalty += h * h;
                h = -h;
            }
            entropy -= h * FastMath.log(h);
        }
        penalty *= negativePenalty * 1.0e7;
        double result = entropy + penalty;
        //System.out.println(n + " " + p0 + " " + p1 + " " + penalty + " " + entropy + " " + result);

        return result;

    }

    public int getNetSign() {
        int n = bList.size();
        ArrayList<Double> valuesP = new ArrayList<>();
        ArrayList<Double> valuesM = new ArrayList<>();
        //double sdev = VecMat.sdev(testVec.getRvec(), testVec.getSize(), 16, 4);
        double sdev = Util.sdev(testVec, 16, 4);
        //System.out.println(sdev);
        double threshold = 50.0 * sdev;
        for (RegionPositions rPos : bList) {
            int start1 = rPos.base1;
            int end1 = rPos.base2;
            int start2 = rPos.base3;
            int end2 = rPos.base4;

            int twoThird1 = 2 * (end1 - start1) / 3 + start1;
            double sum = 0.0;
            int totalPoints = 0;
            for (int j = twoThird1; j < end1; j++) {
                sum += testVec.getReal(j);
                totalPoints++;
            }
            int oneThird2 = (end2 - start2) / 3 + start2;
            for (int j = start2; j < oneThird2; j++) {
                sum += testVec.getReal(j);
                totalPoints++;
                //System.out.println(sum);
            }
            //System.out.println("end sum");
            //System.out.println("total Points: " + totalPoints);
            if (totalPoints < 2) {
                //System.out.println("continue");
                continue;
            }

            double mean = sum / totalPoints;
            //System.out.println(mean);
            //System.out.println(sum);

            double max = 0.0;
            for (int j = end1; j < start2; j++) {
                double delta = (testVec.getReal(j) - mean);
                if (Math.abs(delta) > Math.abs(max)) {
                    max = delta;
                }
            }
            //System.out.println(end1+" "+start2+" "+max+" "+threshold);
            if (Math.abs(max) > threshold) {
                if (max > 0.0) {
                    valuesP.add(max);
                } else {
                    valuesM.add(max);
                }
            }
        }
        int sizeP = valuesP.size();
        int sizeM = valuesM.size();
        System.out.println(sizeM + " " + sizeP);
        if (sizeM > sizeP) {
            return -1;
        } else {
            return 1;
        }
    }

    public int getSign(double p0, double p1) {
        int nValues = 0;
        double a = 0.0;
        double b = 0.0;
        double c = 0.0;
        double sum = 0.0;
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
//        System.out.println(sumPlus + " " + sumMinus);
        if (sumMinus > sumPlus) {
            return -1;
        } else {
            return 1;
        }

    }
}
