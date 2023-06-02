/*
 * NMRFx Structure : A Program for Calculating Structures
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

package org.nmrfx.structure.rna;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.Precision;
import org.nmrfx.structure.chemistry.OverlappingLines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLayoutXY implements MultivariateFunction {

    private static final Logger log = LoggerFactory.getLogger(SSLayoutXY.class);
    private final int[][] interactions;
    private final int[] basePairs;
    private final int[] basePairs2;
    private final double[] baseBondLength;
    private final double[] baseBondLengthTargets;
    private int nFreeAngles = 0;
    private int nFreeAnglesTot = 0;
    private int nFreeDis = 0;
    private final double[] values;
    private final double[] angleTargets;
    private final double[] angleValues;
    private final boolean[] angleFixed;
    private final boolean[] disFixed;
    private final int[] angleRelations;
    private final int[] nAngles;
    private final int[] nDistances;
    private int nAnglePars = 0;
    private double[] inputSigma;
    private double[][] boundaries = null;
    private final double targetSeqDistance = 1.0;
    private final double targetPairDistance = 1.0;
    private final double targetPair2Distance = Math.sqrt(targetSeqDistance * targetSeqDistance + targetPairDistance * targetPairDistance);
    private final double targetNBDistance = 1.2;
    private final int nNuc;
    private final int[] nucChain;
    private String vienna;
    int limit = 10;
    boolean useSinCos = true;

    public static final RandomGenerator DEFAULT_RANDOMGENERATOR = new MersenneTwister(1);

    public SSLayoutXY(int... nValues) {
        int n = 0;
        for (int nValue : nValues) {
            n += nValue;
        }
        nucChain = new int[n];
        int k = 0;
        for (int i = 0; i < nValues.length; i++) {
            for (int j = 0; j < nValues[i]; j++) {
                nucChain[k++] = i;
            }
        }
        nNuc = n;
        interactions = new int[n][n];
        basePairs = new int[n];
        basePairs2 = new int[n];
        baseBondLength = new double[n];
        baseBondLengthTargets = new double[n];
        disFixed = new boolean[n];
        nDistances = new int[n];
        values = new double[nNuc * 2];
        angleTargets = new double[nNuc - 2];
        angleFixed = new boolean[nNuc - 2];
        angleRelations = new int[nNuc - 2];
        angleValues = new double[nNuc - 2];
        nAngles = new int[nNuc - 2];
        for (int i = 0; i < n; i++) {
            basePairs[i] = -1;
            basePairs2[i] = -1;
            baseBondLength[i] = targetSeqDistance;
            disFixed[i] = true;
        }
        for (int i = 0; i < angleTargets.length; i++) {
            angleTargets[i] = -2000.0;
            angleRelations[i] = 0;
        }
    }

    public void addPair(int i, int j) {
        interactions[i][j] = 1;
        interactions[j][i] = 1;
        basePairs[i] = j;
        basePairs[j] = i;
    }

    public void dumpPairs() {
        if (log.isDebugEnabled()) {
            StringBuilder pairStr = new StringBuilder();
            for (int i = 0; i < basePairs.length; i++) {
                int aFix = 1;
                int dFix = 1;
                if (i > 1) {
                    aFix = angleFixed[i - 2] ? 1 : 0;
                    dFix = disFixed[i - 2] ? 1 : 0;
                }
                pairStr.append(String.format("nucxy %4d %4d %7.3f %7.3f %d %d %c%n", i, basePairs[i], values[i * 2], values[i * 2 + 1], aFix, dFix, vienna.charAt(i)));
            }
            log.debug(pairStr.toString());
        }
    }

    public void fillPairs() {
        try {
            for (int i = 0; i < (nNuc - 1); i++) {
                for (int j = i + 2; j < nNuc; j++) {
                    if (interactions[i][j] == 1) {
                        if (interactions[i + 1][j - 1] == 1) {
                            interactions[i + 1][j] = 2;
                            interactions[j][i + 1] = 2;
                            basePairs2[i + 1] = j;
                            interactions[i][j - 1] = 2;
                            interactions[j - 1][i] = 2;
                            basePairs2[j - 1] = i;
                        }
                    }
                }
            }
            for (int i = 0; i < (nNuc - 1); i++) {
                int pos = 0;
                int loopSize = 0;
                int gapSize = 0;
                int bulgeSize = 0;
                int loopStart = -1;
                if (basePairs[i] == -1) {
                    // not in base pair so search backwards for last basepair which will be start of a loop
                    for (int j = i - 1; j >= 0; j--) {
                        if (basePairs[j] != -1) {
                            pos = i - j - 1;
                            loopStart = j;
                            break;
                        }
                    }
                    // now search forward for another basepair
                    for (int j = i + 1; j < nNuc; j++) {
                        if ((basePairs[j] != -1) && (loopStart != -1)) {
                            // Check if the start of the loop is basepaired to j
                            if (basePairs[loopStart] == j) {  // it is, so we have a stemloop with loop size loopSize
                                loopSize = j - loopStart - 1;
                            } else if (basePairs[loopStart] == (basePairs[j] + 1)) {  // check for a bulge
                                bulgeSize = j - loopStart - 1;
                                if (bulgeSize == 2) {
                                    baseBondLength[i - 1] = targetSeqDistance * 0.75;
                                    if (i > 1) {
                                        baseBondLength[i - 2] = targetSeqDistance * 0.75;
                                    }
                                } else {
                                    baseBondLength[i - 1] = targetSeqDistance * 0.8;
                                    disFixed[i - 1] = false;
                                    if (i > 1) {
                                        baseBondLength[i - 2] = targetSeqDistance * 0.8;
                                        disFixed[i - 2] = false;
                                    }
                                }
                            } else {
                                gapSize = j - loopStart - 1;
                            }
                            break;
                        }
                    }
                }
                // if we have a stemloop we can calculate exactly the angles needed for loop
                if (loopSize != 0) {
                    double interiorAngle = Math.PI * loopSize / (loopSize + 2);
                    double target;
                    if (pos == 0) {
                        target = (interiorAngle - Math.PI / 2.0);
                    } else {
                        target = -(Math.PI - interiorAngle);
                    }
                    if (pos == (loopSize - 1)) {
                        double endTarget = -(Math.PI - interiorAngle);
                        angleTargets[i - 1] = endTarget;
                        angleFixed[i - 1] = true;
                        endTarget = (interiorAngle - Math.PI / 2.0);
                        if (i < angleTargets.length) {
                            angleTargets[i] = endTarget;
                            angleFixed[i] = true;
                        }
                    }
                    if (i > 1) {
                        angleTargets[i - 2] = target;
                        angleFixed[i - 2] = true;
                    }
                } else if (gapSize > 4) {
                    boolean free = false;
                    if ((pos % 3) == 0) {
                        free = true;
                    }
                    if ((pos % 3) == 1) {
                        angleTargets[i - 2] = 0.0;
                        angleFixed[i - 2] = true;
                        angleRelations[i - 2] = -1;
                    }
                    if ((pos % 3) == 2) {
                        angleTargets[i - 2] = 0.0;
                        angleFixed[i - 2] = true;
                        angleRelations[i - 2] = 1;
                    }
                    disFixed[i] = false;
                    log.info("gap {} {}", pos, free);
                }
            }
        } catch (ArrayIndexOutOfBoundsException aiE) {
            log.warn(aiE.getMessage(), aiE);
            return;
        }

        for (int i = 0; i < nNuc; i++) {
            baseBondLengthTargets[i] = baseBondLength[i];
        }
        dumpFixed();
    }

    public void dumpFixed() {
        if (log.isDebugEnabled()) {
            StringBuilder strBuilder = new StringBuilder();
            for (int i = 0; i < nNuc; i++) {
                strBuilder.append(i).append(" ").append(vienna.charAt(i)).append(" ").append(basePairs[i]);
                if (i > 1) {
                    strBuilder.append(" fix ").append(disFixed[i - 2]);
                    if ((i - 2) < angleFixed.length) {
                        strBuilder.append(" ").append(angleFixed[i - 2]);
                    }
                }
                strBuilder.append(System.lineSeparator());
            }
            log.debug(strBuilder.toString());
        }
    }

    private void setBoundaries(double sigma) {
        boundaries = new double[2][];
        int nPars = 2 * (nNuc - 1);
        boundaries[0] = new double[nPars];
        boundaries[1] = new double[nPars];
        inputSigma = new double[nPars];
        for (int i = 0; i < nPars; i++) {
            boundaries[0][i] = -300.0;
            boundaries[1][i] = 300.0;
            inputSigma[i] = 0.4;
        }
    }

    private void setSigma(double sigma) {
        for (int i = 0; i < inputSigma.length; i++) {
            inputSigma[i] = sigma;
        }
    }

    public void dumpAngles(double[] pars) {
        if (log.isDebugEnabled()) {
            log.debug("dump {} {}", pars.length, boundaries[0].length);
            StringBuilder angleStr = new StringBuilder();
            for (int i = 0; i < pars.length; i++) {
                if (useSinCos) {
                } else {
                    angleStr.append(String.format("%3d bou %.1f %.1f par %.1f sig %.1f tar %.1f%n", i, boundaries[0][i] * 180.0 / Math.PI, boundaries[1][i] * 180.0 / Math.PI, pars[i] * 180.0 / Math.PI, (inputSigma[i] * 180.0 / Math.PI), (angleTargets[i] * 180.0 / Math.PI)));
                }
            }
            log.debug(angleStr.toString());
        }
    }

    public void getFullCoordinates(double[] pars) {
        values[0] = 0.0;
        values[1] = 0.0;
        for (int i = 0; i < (nNuc - 1); i++) {
            values[i * 2 + 2] = pars[i * 2];
            values[i * 2 + 3] = pars[i * 2 + 1];
        }
    }

    public boolean nIntersections(int i, int j, boolean report) {
        boolean intersects = false;
        double xi1 = values[i * 2];
        double yi1 = values[i * 2 + 1];
        double xi2 = values[i * 2 + 2];
        double yi2 = values[i * 2 + 3];
        Vector2D pi1 = new Vector2D(xi1, yi1);
        Vector2D pi2 = new Vector2D(xi2, yi2);
        double xj1 = values[j * 2];
        double yj1 = values[j * 2 + 1];
        double xj2 = values[j * 2 + 2];
        double yj2 = values[j * 2 + 3];
        return OverlappingLines.doLinesIntersect(xi1, yi1, xi2, yi2, xj1, yj1, xj2, yj2);
    }

    public void toSinCos(double inValue, double[] outValues, int index) {
        outValues[index] = Math.sin(inValue) * 100.0;
        outValues[index + 1] = Math.cos(inValue) * 100.0;
    }

    public double fromSinCos(double[] inValues, int index) {
        double outValue = Math.atan2(inValues[index] / 100.0, inValues[index + 1] / 100.0);
        return outValue;
    }

    @Override
    public double value(final double[] pars) {
        return value(pars, false);
    }

    public double value(final double[] pars, boolean report) {
        getFullCoordinates(pars);
        double sumPairError = 0.0;
        double sumPairAbsError = 0.0;
        double sumNBError = 0.0;
        int nIntersections = 0;
        for (int i = 0; i < limit; i++) {
            if (basePairs[i] != -1) {
                int j = basePairs[i];
                if (j < limit) {
                    if (i < j) {
                        double x1 = values[i * 2];
                        double y1 = values[i * 2 + 1];
                        double x2 = values[j * 2];
                        double y2 = values[j * 2 + 1];
                        double deltaX = x2 - x1;
                        double deltaY = y2 - y1;
                        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        double delta = Math.abs(distance - targetPairDistance);
                        if (report) {
                            log.info("{} {} {}", i, j, delta);
                        }
                        sumPairError += delta * delta;
                    }
                }
            }
            if (basePairs2[i] != -1) {
                int j = basePairs2[i];
                if (j < limit) {
                    double x1 = values[i * 2];
                    double y1 = values[i * 2 + 1];
                    double x2 = values[j * 2];
                    double y2 = values[j * 2 + 1];
                    double deltaX = x2 - x1;
                    double deltaY = y2 - y1;
                    double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    double delta = Math.abs(distance - targetPair2Distance);
                    sumPairError += delta * delta;
                    sumPairAbsError += delta;
                }
            }

        }
        double xSum = 0.0;
        double ySum = 0.0;
        for (int i = 0; i < limit; i++) {
            boolean intersects = false;
            double x1 = values[i * 2];
            double y1 = values[i * 2 + 1];
            xSum += x1;
            ySum += y1;
            for (int j = i + 2; j < limit; j++) {
                double x2 = values[j * 2];
                double deltaX = x2 - x1;
                if ((j < (limit - 1)) && nIntersections(i, j, report)) {
                    nIntersections++;
                }
                if (Math.abs(deltaX) < 10.0 * targetNBDistance) {
                    double y2 = values[j * 2 + 1];
                    double deltaY = y2 - y1;
                    if (Math.abs(deltaY) < 10.0 * targetNBDistance) {
                        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        if (interactions[i][j] == 0) {
                            if (distance < targetNBDistance) {
                                if (report) {
                                    log.info("dis {} {} {}", i, j, distance);
                                }
                                double delta = Math.abs(distance - targetNBDistance);
                                sumNBError += delta * delta;
                            }
                        }
                    }
                }
            }
        }
        double sumDis = 0.0;
        for (int i = 1; i < limit; i++) {
            double x1 = values[i * 2 - 2];
            double y1 = values[i * 2 - 1];
            double x2 = values[i * 2];
            double y2 = values[i * 2 + 1];
            double deltaX = x2 - x1;
            double deltaY = y2 - y1;
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            double penalty = 0.0;
            if ((basePairs[i] != -1) && (basePairs[i - 1] != -1)) {
                penalty = (distance - targetSeqDistance) * (distance - targetSeqDistance);
            } else if (distance > targetSeqDistance * 1.2) {
                penalty = (distance - targetSeqDistance * 1.2) * (distance - targetSeqDistance * 1.2);
            } else if (distance < targetSeqDistance) {
                penalty = (distance - targetSeqDistance) * (distance - targetSeqDistance);
            }
            sumDis += penalty;
        }
        double xCenter = xSum / limit;
        double yCenter = ySum / limit;
        for (int i = 0; i < limit; i++) {
            double x1 = values[i * 2];
            double y1 = values[i * 2 + 1];
            double deltaX = x1 - xCenter;
            double deltaY = y1 - yCenter;
            double dis2 = (deltaX * deltaX + deltaY * deltaY);
        }
        double sumAngle = 0.0;
        for (int i = 1; i < (limit - 1); i++) {
            double x1 = values[i * 2 - 2];
            double y1 = values[i * 2 - 1];
            double x2 = values[i * 2];
            double y2 = values[i * 2 + 1];
            double x3 = values[i * 2 + 2];
            double y3 = values[i * 2 + 3];
            double deltaX12 = x1 - x2;
            double deltaY12 = y1 - y2;
            double deltaX32 = x3 - x2;
            double deltaY32 = y3 - y2;
            Vector2D vec12 = new Vector2D(deltaX12, deltaY12);
            Vector2D vec32 = new Vector2D(deltaX32, deltaY32);
            double angle = Vector2D.angle(vec12, vec32);
            if (report) {
                log.info("ang {}", angle * 180.0 / Math.PI);
            }
            double cosAngle = Math.cos(angle);
            double minCos = 0.1;
            if (cosAngle > minCos) {
                sumAngle += (cosAngle - minCos);
            }

        }

        double value = sumPairAbsError * 50.0 + sumNBError + sumAngle * 0.3 + nIntersections * 100.0 + sumDis * 1.0;
        String logMsg = String.format("lim %3d nNuc %3d nFree %3d pairs %7.3f nb %7.3f ang %7.3f nint %2d ndis %7.3f tot %7.3f", limit, nNuc, nFreeAnglesTot, sumPairError, sumNBError, sumAngle, nIntersections, sumDis, value);
        log.info(logMsg);
        return value;
    }

    public PointValuePair refineCMAES(int nSteps, double stopFitness, final double sigma, final double lambdaMul, final int diagOnly) {
        setBoundaries(0.1);
        double[] guess = new double[boundaries[0].length];
        for (int i = 0; i < boundaries[0].length; i++) {
            guess[i] = i + 1;
        }
        double value = value(guess);
        log.info("start value {} free angles {} freedis {}", value, nFreeAngles, nFreeDis);
        limit = nNuc;
        PointValuePair result = null;
        DEFAULT_RANDOMGENERATOR.setSeed(1);
        int lambda = (int) (lambdaMul * Math.round(4 + 3 * Math.log(guess.length)));

        CMAESOptimizer optimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                DEFAULT_RANDOMGENERATOR, true,
                new SimpleValueChecker(100 * Precision.EPSILON, 100 * Precision.SAFE_MIN));

        try {
            result = optimizer.optimize(
                    new CMAESOptimizer.PopulationSize(lambda),
                    new CMAESOptimizer.Sigma(inputSigma), new MaxEval(2000000),
                    new ObjectiveFunction(this), GoalType.MINIMIZE,
                    new SimpleBounds(boundaries[0], boundaries[1]),
                    new InitialGuess(guess));
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
        log.info("done {}", result);
        if (result != null) {
            limit = nNuc;
            log.info("do value");
            value(result.getPoint(), true);
        } else {
            result = new PointValuePair(guess, value);
        }
        return result;
    }

    public void calcLayout(int nSteps) {
        PointValuePair result = refineCMAES(nSteps, 0.2, 0.5, 1.0, 0);
        getFullCoordinates(result.getPoint());
    }

    public void dumpPars(double[] pars) {
        if (log.isDebugEnabled()) {
            StringBuilder parsString = new StringBuilder();
            for (int i = 0; i < nAnglePars; i++) {
                parsString.append(String.format("angle %2d %7.3f%n", i, pars[i] * 180.0 / Math.PI));
            }
            for (int i = nAnglePars; i < pars.length; i++) {
                parsString.append(String.format("dist  %2d %7.3f%n", i, pars[i]));
            }
            log.debug(parsString.toString());
        }
    }

    public void dumpCoordinates(double[] pars) {
        getFullCoordinates(pars);
        dumpCoordinates();
    }

    public void dumpCoordinates() {
        if (log.isDebugEnabled()) {
            double sumX = 0.0;
            double sumY = 0.0;
            for (int i = 0; i < nNuc; i++) {
                sumX += values[i * 2];
                sumY += values[i * 2 + 1];
            }
            double centerX = sumX / nNuc;
            double centerY = sumY / nNuc;
            StringBuilder coordStr = new StringBuilder();
            for (int i = 0; i < nNuc; i++) {
                coordStr.append(String.format("%.3f\t%.3f%n", values[i * 2], values[i * 2 + 1]));
            }
            log.debug(coordStr.toString());
        }
    }

    public void interpVienna(String value) {
        vienna = value;
        String leftBrackets = "([{";
        String rightBrackets = ")]}";
        int[][] levelMap = new int[vienna.length()][leftBrackets.length()];
        int[] levels = new int[leftBrackets.length()];
        for (int i = 0; i < vienna.length(); i++) {
            char curChar = vienna.charAt(i);
            try {
                boolean dot = (curChar == '.') || (Character.isLetter(curChar));
                if (!dot) {
                    int leftIndex = leftBrackets.indexOf(curChar);
                    int rightIndex = rightBrackets.indexOf(curChar);
                    if (leftIndex != -1) {
                        levelMap[levels[leftIndex]][leftIndex] = i;
                        levels[leftIndex]++;
                    } else if (rightIndex != -1) {
                        levels[rightIndex]--;
                        int start = levelMap[levels[rightIndex]][rightIndex];
                        int end = i;
                        addPair(start, end);
                    }
                }
            } catch (ArrayIndexOutOfBoundsException aiE) {
                log.warn(aiE.getMessage(), aiE);
                return;
            }
        }
    }

    public double[] getValues() {
        return values.clone();
    }

    public int[] getBasePairs() {
        return basePairs.clone();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.exit(1);
        }
        String vienna = args[0];

        SSLayoutXY ssLayout = new SSLayoutXY(vienna.length());
        ssLayout.interpVienna(vienna);
        ssLayout.dumpPairs();

        ssLayout.fillPairs();
        PointValuePair result = ssLayout.refineCMAES(1000, 1.0, 0.5, 1.0, 0);
        ssLayout.dumpCoordinates(result.getPoint());
    }
}
