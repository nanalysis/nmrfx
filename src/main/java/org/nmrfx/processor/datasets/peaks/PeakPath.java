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
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.optimization.VecID;
import org.nmrfx.processor.optimization.equations.OptFunction;
import org.nmrfx.processor.optimization.equations.Quadratic10;
import java.util.*;
import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.general.LevenbergMarquardtOptimizer;

public class PeakPath {

    OptFunction optFunction = new Quadratic10();
    ArrayList<PeakList> peakLists = new ArrayList<PeakList>();
    ArrayList<ArrayList<Peak>> filteredLists = new ArrayList<ArrayList<Peak>>();
    final PeakList firstList;
    final double[] concentrations;
    final double[] binderConcs;
    int[] peakDims = {0, 1};
    final double[] weights;

    public PeakPath(final ArrayList<String> peakListNames, double[] concentrations, final double[] binderConcs, final double[] weights) {
        for (String peakListName : peakListNames) {
            PeakList peakList = PeakList.get(peakListName);
            if (peakList == null) {
                throw new IllegalArgumentException("Unknown peaklist " + peakListName);
            }
            peakLists.add(peakList);
        }
        firstList = peakLists.get(0);
        this.concentrations = concentrations;
        this.binderConcs = binderConcs;
        this.weights = weights;
    }

    double calcDistance(Peak peak1, Peak peak2) {
        double sum = 0.0;
        for (int i : peakDims) {
            double ppm1 = peak1.getPeakDim(i).getChemShift();
            double ppm2 = peak2.getPeakDim(i).getChemShift();
            sum += (ppm1 - ppm2) * (ppm1 - ppm2) / (weights[i] * weights[i]);
        }
        return Math.sqrt(sum);
    }

    double calcDelta(Peak peak1, Peak peak2, int iDim) {
        double ppm1 = peak1.getPeakDim(iDim).getChemShift();
        double ppm2 = peak2.getPeakDim(iDim).getChemShift();
        return (ppm2 - ppm1) / weights[iDim];
    }

    class PeakDistance implements Comparable<PeakDistance> {

        final Peak peak;
        final double distance;

        PeakDistance(Peak peak, double distance) {
            this.peak = peak;
            this.distance = distance;
        }

        public int compareTo(PeakDistance peakDis2) {
            return Double.compare(distance, peakDis2.distance);
        }
    }

    void getNearPeaks(final Peak startPeak, final double radius) {
        int iList = -1;
        filteredLists.clear();
        for (PeakList peakList : peakLists) {
            ArrayList<Peak> peakArray = new ArrayList<Peak>();
            filteredLists.add(peakArray);
            iList++;
            if (iList == 0) {
                continue;
            }
            int nPeaks = peakList.size();
            for (int j = 0; j < nPeaks; j++) {
                Peak peak = peakList.getPeak(j);
                if (peak.getStatus() != 0) {
                    continue;
                }
                double distance = calcDistance(startPeak, peak);
                if (distance < radius) {
                    peakArray.add(peak);
                }
            }
        }
    }

    void filterLists() {
        for (PeakList peakList : peakLists) {
            int nPeaks = peakList.size();
            for (int j = 0; j < nPeaks; j++) {
                Peak peak = peakList.getPeak(j);
                if (peak.getStatus() >= 0) {
                    peak.setStatus(0);
                }
            }
        }
        int nPeaks = firstList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak1 = firstList.getPeak(i);
            if (peak1.getStatus() < 0) {
                continue;
            }
            double sum = 0.0;
            for (int iDim : peakDims) {
                double boundary = peak1.getPeakDim(iDim).getBoundsValue();
                sum += boundary * boundary / (weights[iDim] * weights[iDim]);
            }
            double tol = Math.sqrt(sum / peakDims.length);
            int iList = -1;
            boolean ok = true;
            ArrayList<Peak> minPeaks = new ArrayList<Peak>();
            for (PeakList peakList : peakLists) {
                iList++;
                if (iList == 0) {
                    continue;
                }
                int nPeaks2 = peakList.size();
                double minDis = Double.MAX_VALUE;
                Peak minPeak = null;
                for (int j = 0; j < nPeaks2; j++) {
                    Peak peak2 = peakList.getPeak(j);
                    if (peak2.getStatus() != 0) {
                        continue;
                    }
                    double distance = calcDistance(peak1, peak2);
                    if (distance < minDis) {
                        minDis = distance;
                        minPeak = peak2;
                    }
                }
                if (minDis < tol) {
                    minPeaks.add(minPeak);
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                peak1.setStatus(1);
                for (Peak minPeak : minPeaks) {
                    minPeak.setStatus(1);
                }
            }
        }
    }

    public double[] quadFitter(double[] xValues, double[] pValues, double[] yValues) {

        VecID[] params = optFunction.getAllParamNames();
        VecID[] vars;
        vars = optFunction.getAllVarNames();
        for (VecID v : params) {
            optFunction.updateParamPendingStatus(v, true);
        }

        optFunction.updateParam(VecID.A, true);
        optFunction.loadData(VecID.X, xValues);
        optFunction.loadData(VecID.Y, yValues);
        optFunction.loadData(VecID.P, pValues);
        optFunction.calcGuessParams();
        PointVectorValuePair v;
        double[] retVal = null;
        double[] fitWeights = new double[yValues.length];
        for (int i = 0; i < fitWeights.length; i++) {
            fitWeights[i] = 1.0;
        }

        try {
            LevenbergMarquardtOptimizer estimator = new LevenbergMarquardtOptimizer();

            v = estimator.optimize(500, optFunction,
                    optFunction.target(),
                    fitWeights,
                    optFunction.startpoint());
            retVal = v.getPoint();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
        return retVal;

    }

    public double[] poly(double x2, double x3, double y2, double y3) {
        /*
         * A= (y3 -y2)/((x3 -x2)(x3 -x1)) - (y1 -y2)/((x1 -x2)(x3 -x1))
         B = (y1 -y2 +A(x2^2 -x1^2)) /(x1 - x2)
         *           C=y1 - Ax1^2 -Bx1.
         */
        double A = (y3 - y2) / ((x3 - x2) * x3) - (-y2) / ((-x2) * x3);
        double B = (-y2 + A * (x2 * x2)) / (-x2);
        double C = 0.0;
        double[] result = {A, B};
        return result;
    }

    public ArrayList<Peak> scan(final String startPeakName, double radius, double tolMul, int midListIndex, final String lastPeakName) {
        Peak startPeak = PeakList.getAPeak(startPeakName);
        ArrayList<PeakDistance> peakDistances = new ArrayList<PeakDistance>();
        if (lastPeakName.length() != 0) {
            Peak lastPeak = PeakList.getAPeak(lastPeakName);
            double distance = calcDistance(startPeak, lastPeak);
            PeakDistance peakDis = new PeakDistance(lastPeak, distance);
            peakDistances.add(peakDis);
            getNearPeaks(startPeak, distance * 1.1);
        } else {
            getNearPeaks(startPeak, radius);
            ArrayList<Peak> lastPeaks = filteredLists.get(filteredLists.size() - 1);
            for (Peak peak : lastPeaks) {
                double distance = calcDistance(startPeak, peak);
                PeakDistance peakDis = new PeakDistance(peak, distance);
                peakDistances.add(peakDis);
            }
            Collections.sort(peakDistances);
        }
        ArrayList<Peak> midPeaks = filteredLists.get(midListIndex);
        double firstConc = concentrations[0];
        double midConc = concentrations[midListIndex];
        double lastConc = concentrations[concentrations.length - 1];
        double firstBConc = binderConcs[0];
        double midBConc = binderConcs[midListIndex];
        double lastBConc = binderConcs[binderConcs.length - 1];
        ArrayList<Peak> bestPath = new ArrayList<Peak>();
        double sum = 0.0;
        for (int iDim : peakDims) {
            double boundary = startPeak.getPeakDim(iDim).getBoundsValue();
            sum += boundary * boundary / (weights[iDim] * weights[iDim]);
        }
        double tol = tolMul * Math.sqrt(sum);
        double minRMS = Double.MAX_VALUE;
        for (PeakDistance peakDistance : peakDistances) {
            Peak lastPeak = peakDistance.peak;
            double startToLast = peakDistance.distance;
            //System.out.println("end " + lastPeak.getName() + " " + startToLast);
            ArrayList<PeakDistance> midDistancePeaks = new ArrayList<PeakDistance>();
            for (Peak midPeak : midPeaks) {
                double startToMid = calcDistance(startPeak, midPeak);
                if (startToMid > startToLast) {
                    continue;
                }
                double midToLast = calcDistance(midPeak, lastPeak);
                if (midToLast > startToLast) {
                    continue;
                }
                // Heron's formula for area, then use area = 1/2 base*height to get height
                // where height will be deviatin of midpoint from line between start and last
                double s = (startToMid + startToLast + midToLast) / 2.0;
                double area = Math.sqrt(s * (s - startToMid) * (s - startToLast) * (s - midToLast));
                double height = 2.0 * area / startToLast;
                // if peak too far off line between start and end skip it
                //System.out.println(midPeak.getName() + " " + tol + " " + height);
                if (height > tol) {
                    continue;
                }

                PeakDistance midValue = new PeakDistance(midPeak, height);
                midDistancePeaks.add(midValue);
            }
            Collections.sort(midDistancePeaks);
            double[][] polyCoef = new double[peakDims.length][2];
            double[][] fitCoef = new double[peakDims.length][2];
            for (PeakDistance midPeakDistance : midDistancePeaks) {
                Peak midPeak = midPeakDistance.peak;
                //System.out.println("mid " + midPeak.getName());
                int jDim = 0;
                double[] yValues = new double[3];
                double[] xValues = new double[3];
                double[] pValues = new double[3];
                for (int iDim : peakDims) {
                    double midDis = calcDelta(startPeak, midPeak, iDim);
                    double lastDis = calcDelta(startPeak, lastPeak, iDim);
                    yValues[0] = 0.0;
                    yValues[1] = midDis;
                    yValues[2] = lastDis;
                    xValues[0] = firstConc;
                    xValues[1] = midConc;
                    xValues[2] = lastConc;
                    pValues[0] = firstBConc;
                    pValues[1] = midBConc;
                    pValues[2] = lastBConc;
                    fitCoef[jDim] = quadFitter(xValues, pValues, yValues);
                    polyCoef[jDim++] = poly(midConc, lastConc, midDis, lastDis);
                }
                double pathSum = 0.0;
                ArrayList<Peak> path = new ArrayList<Peak>();
                path.add(startPeak);
                boolean pathOK = true;
                for (int iList = 1; iList < filteredLists.size() - 1; iList++) {
                    if (iList == midListIndex) {
                        path.add(midPeak);
                        continue;
                    }
                    ArrayList<Peak> testPeaks = filteredLists.get(iList);
                    double testConc = concentrations[iList];
                    double testBConc = binderConcs[iList];
                    double minSum = Double.MAX_VALUE;
                    Peak minPeak = null;
                    for (Peak testPeak : testPeaks) {
                        jDim = 0;
                        sum = 0.0;
                        for (int iDim : peakDims) {
                            double dis = calcDelta(startPeak, testPeak, iDim);
                            double polyDis = polyCoef[jDim][0] * testConc * testConc + polyCoef[jDim][1] * testConc;
                            double[] xpValues = {testConc, testBConc};
                            if (fitCoef[iDim] != null) {
                                polyDis = optFunction.value(fitCoef[iDim], xpValues);
                            }

                            jDim++;
                            sum += (dis - polyDis) * (dis - polyDis);
                        }
                        //System.out.println(testPeak.getName() + " " + sum + " " + minSum);
                        if (sum < minSum) {
                            minSum = sum;
                            minPeak = testPeak;
                        }
                    }
                    if (minPeak == null) {
                        pathOK = false;
                        break;
                    }
                    if (Math.sqrt(minSum) > tol) {
                        pathOK = false;
                        break;
                    }
                    path.add(minPeak);
                    //System.out.println(minPeak.getName());
                    pathSum += minSum;
                }
                if (pathOK) {
                    path.add(lastPeak);
                    double rms = Math.sqrt(pathSum / (filteredLists.size() - 3));
                    //System.out.println(rms);
                    if (rms < minRMS) {
                        minRMS = rms;
                        bestPath = path;
                    }
                }
            }
        }
        return bestPath;
    }
}
