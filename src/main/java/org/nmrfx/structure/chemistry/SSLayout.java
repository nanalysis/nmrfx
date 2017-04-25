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

public class SSLayout implements MultivariateFunction {

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

    public SSLayout(int... nValues) {
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
        for (int i = 1; i < (nNuc - 1); i++) {
            for (int j = i + 2; j < nNuc - 1; j++) {
                if (interactions[i][j] == 1) {
                    if ((interactions[i + 1][j - 1] == 1) && (interactions[i - 1][j + 1] == 1)) {
                        angleTargets[i - 1] = 0.0;
                        angleFixed[i - 1] = true;
                        angleTargets[j - 1] = 0.0;
                        angleFixed[j - 1] = true;
                    }
                    break;
                }
            }
        }
        for (int i = 0; i < angleFixed.length; i++) {
            disFixed[i] = angleFixed[i];
            if (angleRelations[i] == 1) {
                disFixed[i] = false;
            }
        }
        dumpFixed();
        nFreeDis = 0;
        int j = 0;
        boolean lastFixed = true;
        for (boolean fixed : disFixed) {
            if (!fixed) {
                if (lastFixed) {
                    nFreeDis++;
                }
            }
            nDistances[j++] = nFreeDis;
            lastFixed = fixed;
        }

        nFreeAngles = 0;
        j = 0;
        for (boolean fixed : angleFixed) {
            if (!fixed) {
                nFreeAngles++;
            }
            nAngles[j++] = nFreeAngles;
        }
        System.out.println("nag " + angleFixed.length + " " + nFreeAngles);
        int[] iNucs = new int[nFreeAngles];
        j = 0;
        int k = 0;
        for (boolean fixed : angleFixed) {
            if (!fixed) {
                iNucs[k++] = j;
            }
            j++;
        }
        int nAngleMul = 1;
        if (useSinCos) {
            nAngleMul = 2;
        }
        nFreeAnglesTot = nFreeAngles * nAngleMul;
        boundaries = new double[2][nFreeAnglesTot + nFreeDis];
        inputSigma = new double[nFreeAnglesTot + nFreeDis];
        if (useSinCos) {
            for (int i = 0; i < nFreeAnglesTot; i++) {
                boundaries[0][i] = -100.0;
                boundaries[1][i] = 100.0;
                inputSigma[i] = 2.0;
            }
        } else {
            for (int i = 0; i < nFreeAngles; i++) {
                boolean sBreak = false;
                if (i > 1) {
                    int iNuc = iNucs[i - 2];
                    if (nucChain[iNuc] != nucChain[iNuc + 1]) {
                        sBreak = true;
                    }
                    //System.out.println(i + " iNuc " + iNuc + " " + nucChain[iNuc] + " " + nucChain[iNuc+1]);
                }
                boundaries[0][i] = -Math.PI / 3.5;
                boundaries[1][i] = Math.PI / 3.5;
                inputSigma[i] = 0.1;
                if (sBreak) {
                    int nAng = 9;
                    for (int kk = 0; kk < nAng; kk++) {
                        boundaries[0][i + kk - nAng / 2] = -0.4;
                        boundaries[1][i + kk - nAng / 2] = 2.0;
                        inputSigma[i + kk - nAng / 2] = 0.3;
                    }
                }
            }
        }
        for (int i = nFreeAnglesTot; i < boundaries[0].length; i++) {
            boundaries[0][i] = 0.50;
            boundaries[1][i] = 1.5;
            inputSigma[i] = 0.1;
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
        int anglePar = 0;
        for (int i = 0; i < angleValues.length; i++) {
            angleValues[i] = angleTargets[i];
            if (!angleFixed[i]) {
                if (anglePar < nAnglePars) {
                    if (useSinCos) {
                        angleValues[i] = fromSinCos(pars, anglePar);
                        anglePar += 2;
                    } else {
                        angleValues[i] = pars[anglePar++];
                    }
                }
            }
        }
        for (int i = 0; i < angleValues.length; i++) {
            if (angleFixed[i]) {
                if (angleRelations[i] == -1) {
                    angleValues[i] = (2.0 * angleValues[i - 1] + angleValues[i + 2]) / 3.0;
                } else if (angleRelations[i] == 1) {
                    angleValues[i] = (angleValues[i - 2] + 2.0 * angleValues[i + 1]) / 3.0;
                }
            }
        }
        int disPar = nAnglePars - 1;
        boolean lastFixed = true;
        for (int i = 0; i < baseBondLength.length; i++) {
            baseBondLength[i] = baseBondLengthTargets[i];
            if (!disFixed[i]) {
                if (lastFixed) {
                    disPar++;
                }
                if (disPar < pars.length) {
                    baseBondLength[i] = pars[disPar];
                }
            }
            lastFixed = disFixed[i];

        }

        values[0] = 0.0;
        values[1] = 0.0;
        values[2] = targetSeqDistance;
        values[3] = 0.0;
        int distPar = 0;
        for (int i = 0; i < (nNuc - 2); i++) {
            double dx = values[i * 2 + 2] - values[i * 2];
            double dy = values[i * 2 + 3] - values[i * 2 + 1];
            double angle = Math.atan2(dy, dx);
            angle += angleValues[i];
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            values[i * 2 + 4] = values[i * 2 + 2] + cos * baseBondLength[i];
            values[i * 2 + 5] = values[i * 2 + 3] + sin * baseBondLength[i];
        }
    }

    public boolean nIntersections(int i, int j) {
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
                if ((j < (limit - 1)) && nIntersections(i, j)) {
                    nIntersections++;
                }

                if (Math.abs(deltaX) < 10.0 * targetNBDistance) {
                    double y2 = values[j * 2 + 1];
                    double deltaY = y2 - y1;
                    if (Math.abs(deltaY) < 10.0 * targetNBDistance) {
                        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
//                        if ((distance < 2 * targetSeqDistance) && !intersects && (j < (limit - 1))) {
//                            if (nIntersections(i, j)) {
//                                intersects = true;
//                                nIntersections++;
//                            }
//                        }
                        if (interactions[i][j] == 0) {
                            if (distance < targetNBDistance) {
                                double delta = Math.abs(distance - targetNBDistance);
                                sumNBError += delta * delta;
                            }
                        }
                    }
                }
            }
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
        /*
         for (int i=0;i<pars.length;i++) {
         if (angleTargets[i] > -999.0) {
         double delta = Math.abs(angleTargets[i]-pars[i]);
         //System.err.printf("%3d %.1f %.1f %.1f\n",i,angleTargets[i]*180.0/Math.PI,pars[i]*180.0/Math.PI,(delta*180.0/Math.PI));
         sumAngle += delta*delta;
         }
         }
         */
        double value = sumPairError + sumNBError*1.0 + sumAngle + nIntersections * 100.0;
        //double value = sumPairError+sumNBError + sumAngle + nIntersections*1.0;
        System.err.printf(" lim %3d nNuc %3d nFree %3d pairs %7.3f nb %7.3f ang %7.3f nint %2d tot %7.3f\n", limit, nNuc, nFreeAnglesTot, sumPairError, sumNBError, sumAngle, nIntersections, value);
        return value;
    }

    public PointValuePair refineCMAES(int nSteps, double stopFitness, final double sigma, final double lambdaMul, final int diagOnly) {
        setBoundaries(0.1);
        double[] guess = new double[nFreeAnglesTot + nFreeDis];
        if (useSinCos) {
            for (int i = 0; i < nFreeAngles; i++) {
                double aGuess = -1.0 * Math.PI / angleValues.length / 5;
                toSinCos(aGuess, guess, i * 2);
            }
        } else {
            for (int i = 0; i < nFreeAngles; i++) {
                guess[i] = -1.0 * Math.PI / angleValues.length / 5;
            }
        }
        for (int i = nFreeAnglesTot; i < guess.length; i++) {
            guess[i] = targetSeqDistance;
        }
//        for (int k = 0; k < guess.length; k++) {
//            System.out.println(guess[k] + " " + boundaries[0][k] + " " + boundaries[1][k] + " " + inputSigma[k]);
//        }
        double value = value(guess);
        System.err.println("start value " + value + " free angles " + nFreeAngles + " free dis " + nFreeDis);
        //dumpAngles(guess);
        PointValuePair result = null;
        int limIncr = 2;
        if (nFreeAngles > 0) {
            int startLimit = ((nNuc % 2) == 1) ? 5 : 6;
            double lastValue = 0.0;
            //startLimit = nNuc;
            for (limit = startLimit; limit <= nNuc; limit += limIncr) {
                System.out.println("lim " + limit);
                if (limit > (nNuc - limIncr)) {
                    limit = nNuc;
                }
                if (limit == nNuc) {
                    stopFitness = stopFitness / 12.0;
                    nSteps = nSteps * 4;
                }
                DEFAULT_RANDOMGENERATOR.setSeed(1);
                if ((limit != nNuc) && (nAngles[limit - 3] == 0) && (nDistances[limit - 3] == 0)) {
                   //continue;
                }
                if ((limit != startLimit) && (nAngles[limit - 3] == nAngles[limit - 3 - 2]) && (nDistances[limit - 3] == nDistances[limit - 3 - 2])) {
                    setSigma(0.02);
                } else {
                    setSigma(0.10);
                }
                nAnglePars = nAngles[limit - 3];
                if (useSinCos) {
                    nAnglePars *= 2;
                }
                int nDisPars = nDistances[limit - 3];
                int nPars = nAnglePars + nDisPars;
                System.out.println(" npars " + nPars + " nang " + nAnglePars + " ndis " + nDisPars);
                double[] lguess = new double[nPars];
                double[][] lboundaries = new double[2][nPars];
                double[] lSigma = new double[nPars];
                System.arraycopy(guess, 0, lguess, 0, nAnglePars);
                System.arraycopy(boundaries[0], 0, lboundaries[0], 0, nAnglePars);
                System.arraycopy(boundaries[1], 0, lboundaries[1], 0, nAnglePars);
                System.arraycopy(inputSigma, 0, lSigma, 0, nAnglePars);

                System.arraycopy(guess, nFreeAnglesTot, lguess, nAnglePars, nDisPars);
                System.arraycopy(boundaries[0], nFreeAnglesTot, lboundaries[0], nAnglePars, nDisPars);
                System.arraycopy(boundaries[1], nFreeAnglesTot, lboundaries[1], nAnglePars, nDisPars);
                System.arraycopy(inputSigma, nFreeAnglesTot, lSigma, nAnglePars, nDisPars);
                for (int k = 0; k < lguess.length; k++) {
                    System.out.println(lguess[k] + " " + lboundaries[0][k] + " " + lboundaries[1][k] + " " + lSigma[k]);
                }
                value = value(lguess);
                if ((limit != nNuc) && ((value < 1.0e-6) || (((value - lastValue) / value) < 0.3))) {
                    continue;
                }

                //suggested default value for population size represented by variable 'lambda'
                //anglesValue.length represents the number of parameters
                int lambda = (int) (lambdaMul * FastMath.round(4 + 3 * FastMath.log(lguess.length)));
                CMAESOptimizer optimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                        DEFAULT_RANDOMGENERATOR, true,
                        new SimpleValueChecker(100 * Precision.EPSILON, 100 * Precision.SAFE_MIN));

                try {
                    result = optimizer.optimize(
                            new CMAESOptimizer.PopulationSize(lambda),
                            new CMAESOptimizer.Sigma(lSigma), new MaxEval(2000000),
                            new ObjectiveFunction(this), GoalType.MINIMIZE,
                            new SimpleBounds(lboundaries[0], lboundaries[1]),
                            new InitialGuess(lguess));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //System.err.println(limit + " " + optimizer.getIterations() + " " + result.getValue());
                dumpAngles(result.getPoint());
                System.arraycopy(result.getPoint(), 0, guess, 0, nAnglePars);
                System.arraycopy(result.getPoint(), nAnglePars, guess, nFreeAnglesTot, nDisPars);
                lastValue = result.getValue();
                System.out.println(limit + " " + nNuc + " " + lastValue);
            }
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
        PointValuePair result = refineCMAES(nSteps, 0.00, 0.5, 1.0, 0);
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

        SSLayout ssLayout = new SSLayout(vienna.length());
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
