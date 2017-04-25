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

package org.nmrfx.structure.chemistry;

import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.util.Precision;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.Line;

public class SSLayoutXY implements MultivariateFunction {

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
        for (int i = 0; i < basePairs.length; i++) {
            int aFix = 1;
            int dFix = 1;
            if (i > 1) {
                aFix = angleFixed[i - 2] ? 1 : 0;
                dFix = disFixed[i - 2] ? 1 : 0;
            }
            System.out.printf("nucxy %4d %4d %7.3f %7.3f %d %d %c\n",
                    i, basePairs[i], values[i * 2], values[i * 2 + 1], aFix, dFix, vienna.charAt(i));

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
            //for (int i=0;i<nNuc;i++) {
            //System.out.printf("%d %d %d\n",i,basePairs[i],basePairs2[i]);
            //}
            for (int i = 0; i < (nNuc - 1); i++) {
                int pos = 0;
                int loopSize = 0;
                int gapSize = 0;
                int bulgeSize = 0;
                int loopStart = -1;
                //System.out.println(i + " " + basePairs[i]);
                if (basePairs[i] == -1) {
                    // not in base pair so search backwards for last basepair which will be start of a loop
                    for (int j = i - 1; j >= 0; j--) {
                        if (basePairs[j] != -1) {
                            pos = i - j - 1;
                            loopStart = j;
                            break;
                        }
                    }
                    //System.out.println(loopStart);
                    // now search forward for another basepair
                    for (int j = i + 1; j < nNuc; j++) {
                        if ((basePairs[j] != -1) && (loopStart != -1)) {
                            // Check if the start of the loop is basepaired to j
                            if (basePairs[loopStart] == j) {  // it is, so we have a stemloop with loop size loopSize
                                loopSize = j - loopStart - 1;
                            } else if (basePairs[loopStart] == (basePairs[j] + 1)) {  // check for a bulge
                                bulgeSize = j - loopStart - 1;
                                //System.out.println("bulge "+bulgeSize + " " + pos + " " + i);
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
                //System.err.println("loop " + i + " " + loopSize + " " + gapSize);
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
                        //System.err.println((i-1)+" " + pos + " " + loopSize + " " + (angleTargets[i-1]*180.0/Math.PI));
                        endTarget = (interiorAngle - Math.PI / 2.0);
                        if (i < angleTargets.length) {
                            angleTargets[i] = endTarget;
                            angleFixed[i] = true;
                        }
                        //System.err.println((i)+" " + pos + " " + loopSize + " " + (angleTargets[i-1]*180.0/Math.PI));
                    }
                    if (i > 1) {
                        angleTargets[i - 2] = target;
                        angleFixed[i - 2] = true;
                    }
                    //System.err.println((i-2)+" " + pos + " " + loopSize + " " + (angleTargets[i-2]*180.0/Math.PI));
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
                    System.err.println("gap " + pos + " " + free);
                }
            }
        } catch (ArrayIndexOutOfBoundsException aiE) {
            aiE.printStackTrace();
            return;
        }

        for (int i = 0; i < nNuc; i++) {
            baseBondLengthTargets[i] = baseBondLength[i];
        }
        dumpFixed();
    }

    public void dumpFixed() {
        for (int i = 0; i < nNuc; i++) {
            System.out.print(i + " " + vienna.charAt(i) + " " + basePairs[i]);
            if (i > 1) {
                System.out.print(" fix " + disFixed[i - 2]);
                if ((i - 2) < angleFixed.length) {
                    System.out.print(" " + angleFixed[i - 2]);
                }
            }
            System.out.println("");
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
        System.out.println("dump " + pars.length + " " + boundaries[0].length);
        for (int i = 0; i < pars.length; i++) {
            if (useSinCos) {
            } else {
                System.err.printf("%3d bou %.1f %.1f par %.1f sig %.1f tar %.1f\n", i, boundaries[0][i] * 180.0 / Math.PI, boundaries[1][i] * 180.0 / Math.PI, pars[i] * 180.0 / Math.PI, (inputSigma[i] * 180.0 / Math.PI), (angleTargets[i] * 180.0 / Math.PI));
            }
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
//        Vector2D pj1 = new Vector2D(xj1, yj1);
//        Vector2D pj2 = new Vector2D(xj2, yj2);
//        Line lineI = new Line(pi1, pi2);
//        Line lineJ = new Line(pj1, pj2);
//        Vector2D vI = lineI.intersection(lineJ);
//        if (vI != null) {
//            double disi1 = vI.distance(pi1);
//            double disi2 = vI.distance(pi2);
//            double disj1 = vI.distance(pj1);
//            double disj2 = vI.distance(pj2);
//            double leni = pi1.distance(pi2)*1.2;
//            double lenj = pj1.distance(pj2)*1.2;
//            if ((disi1 < leni) && (disi2 < leni) && (disj1 < lenj) && (disj2 < lenj)) {
//                intersects = true;
//            }
//        if (report) {
//             System.out.printf("%3d %3d %7.3f %7.3f %7.3f %7.3f %7.3f %7.3f %b\n",i,j,disi1,disi2,leni, disj1,disj2,lenj,intersects);
//        }
//        }
//        return intersects;
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
                            System.out.println(i + " " + j + " " + delta);
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
//                        if ((distance < 5 * targetSeqDistance) && !intersects && (j < (limit - 1))) {
//                            if (nIntersections(i, j, report)) {
//                                intersects = true;
//                                nIntersections++;
//                            }
//                        }
                        if (interactions[i][j] == 0) {
                            if (distance < targetNBDistance) {
                                if (report) {
                                    System.out.println("dis " + i + " " + j + " " + distance);
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
            //sumNBError += 0.00000001*(1000.0-dis2);
            //sumNBError -= 0.0000001*Math.sqrt(dis2);
            //sumNBError += 0.0000001*Math.sqrt(dis2);
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
                System.out.println("ang " + angle * 180.0 / Math.PI);
            }
            double cosAngle = FastMath.cos(angle);
            double minCos = 0.1;
            if (cosAngle > minCos) {
                sumAngle += (cosAngle - minCos);
            }

        }

//        for (int i = 0; i < pars.length; i++) {
//            if (angleTargets[i] > -999.0) {
//                double delta = Math.abs(angleTargets[i] - pars[i]);
//                //System.err.printf("%3d %.1f %.1f %.1f\n",i,angleTargets[i]*180.0/Math.PI,pars[i]*180.0/Math.PI,(delta*180.0/Math.PI));
//                sumAngle += delta * delta;
//            }
//        }
        //double value = sumPairError * 1.0 + sumNBError + sumAngle * 0.3 + nIntersections * 100.0 + sumDis * 1.0;
        double value = sumPairAbsError * 50.0 + sumNBError + sumAngle * 0.3 + nIntersections * 100.0 + sumDis * 1.0;
        //double value = sumPairError+sumNBError + sumAngle + nIntersections*1.0;
        System.err.printf(" lim %3d nNuc %3d nFree %3d pairs %7.3f nb %7.3f ang %7.3f nint %2d ndis %7.3f tot %7.3f\n", limit, nNuc, nFreeAnglesTot, sumPairError, sumNBError, sumAngle, nIntersections, sumDis, value);
        return value;
    }

    public PointValuePair refineCMAES(int nSteps, double stopFitness, final double sigma, final double lambdaMul, final int diagOnly) {
        setBoundaries(0.1);
        double[] guess = new double[boundaries[0].length];
        for (int i = 0; i < boundaries[0].length; i++) {
            guess[i] = i+1;
        }
        double value = value(guess);
        System.err.println("start value " + value + " free angles " + nFreeAngles + " free dis " + nFreeDis);
        //dumpAngles(guess);
        limit = nNuc;
        PointValuePair result = null;
        DEFAULT_RANDOMGENERATOR.setSeed(1);
        int lambda = (int) (lambdaMul * FastMath.round(4 + 3 * FastMath.log(guess.length)));

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
            e.printStackTrace();
        }
        System.out.println("done " + result);
        if (result != null) {
            limit = nNuc;
            System.out.println("do value");
            value(result.getPoint(), true);
//            dumpCoordinates(result.getPoint());
//            dumpPars(result.getPoint());
        } else {
            result = new PointValuePair(guess, value);
//            dumpCoordinates(guess);
        }
        return result;
    }

    public void calcLayout(int nSteps) {
        PointValuePair result = refineCMAES(nSteps, 0.2, 0.5, 1.0, 0);
        getFullCoordinates(result.getPoint());
    }

    public void dumpPars(double[] pars) {
        for (int i = 0; i < nAnglePars; i++) {
            System.out.printf("angle %2d %7.3f\n", i, pars[i] * 180.0 / Math.PI);
        }
        for (int i = nAnglePars; i < pars.length; i++) {
            System.out.printf("dist  %2d %7.3f\n", i, pars[i]);
        }
    }

    public void dumpCoordinates(double[] pars) {
        getFullCoordinates(pars);
        dumpCoordinates();
    }

    public void dumpCoordinates() {
        double sumX = 0.0;
        double sumY = 0.0;
        for (int i = 0; i < nNuc; i++) {
            sumX += values[i * 2];
            sumY += values[i * 2 + 1];
        }
        double centerX = sumX / nNuc;
        double centerY = sumY / nNuc;
        for (int i = 0; i < nNuc; i++) {
//            values[i*2] -= centerX;
            //           values[i*2+1] -= centerY;
            System.out.printf("%.3f\t%.3f\n", values[i * 2], values[i * 2 + 1]);
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
                    //System.out.println(i + " " + curChar + "  left " + leftIndex + " right " + rightIndex);
                    if (leftIndex != -1) {
                        //System.out.println(levels[leftIndex]);
                        levelMap[levels[leftIndex]][leftIndex] = i;
                        levels[leftIndex]++;
                    } else if (rightIndex != -1) {
                        //System.out.println(levels[rightIndex]);
                        levels[rightIndex]--;
                        int start = levelMap[levels[rightIndex]][rightIndex];
                        int end = i;
                        //System.err.println(start + " <> " + end);
                        addPair(start, end);
                    }
                }
            } catch (ArrayIndexOutOfBoundsException aiE) {
                aiE.printStackTrace();
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
        /*
         ssLayout.addPair(1,20);
         ssLayout.addPair(2,19);
         ssLayout.addPair(3,18);
         ssLayout.addPair(4,17);
         ssLayout.addPair(5,16);
         ssLayout.addPair(6,15);
         ssLayout.addPair(7,14);
         ssLayout.addPair(8,13);
         */
        ssLayout.fillPairs();
//        ssLayout.refineCMAES(30000,0.01,0.5,1.0,100);
        PointValuePair result = ssLayout.refineCMAES(1000, 1.0, 0.5, 1.0, 0);
        ssLayout.dumpCoordinates(result.getPoint());
    }
}
