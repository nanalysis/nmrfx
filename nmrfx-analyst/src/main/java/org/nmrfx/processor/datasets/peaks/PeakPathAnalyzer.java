package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.general.LevenbergMarquardtOptimizer;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.optimization.VecID;
import org.nmrfx.processor.optimization.equations.OptFunction;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.MercerKernel;
import smile.regression.GaussianProcessRegression;

import java.util.*;

public class PeakPathAnalyzer {

    private PeakPathAnalyzer() {

    }

    public static double[] quadFitter(OptFunction optFunction, double[] xValues, double[] pValues, double[] yValues) {

        VecID[] params = optFunction.getAllParamNames();
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
        Arrays.fill(fitWeights, 1.0);

        try {
            LevenbergMarquardtOptimizer estimator = new LevenbergMarquardtOptimizer();

            v = estimator.optimize(500, optFunction,
                    optFunction.target(),
                    fitWeights,
                    optFunction.startpoint());
            retVal = v.getPoint();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return retVal;

    }

    public static PeakPath checkForUnambigous(PeakPaths peakPath, List<List<PeakDistance>> filteredLists,
                                              boolean useLast) {
        // find largest first distance
        double maxDis = Double.NEGATIVE_INFINITY;
        double lastDis = 0.0;
        for (List<PeakDistance> peakDists : filteredLists) {
            if (!peakDists.isEmpty()) {
                double dis = peakDists.get(0).getDistance();
                lastDis = dis;
                if (dis > maxDis) {
                    maxDis = dis;
                }
            }
        }
        if (useLast) {
            maxDis = lastDis + peakPath.getDTol();
        }
        List<PeakDistance> newPeakDists = new ArrayList<>();
        for (List<PeakDistance> peakDists : filteredLists) {
            if (peakDists.size() > 1) {
                double dis = peakDists.get(1).getDistance();
                // there should only be one distance shorter than the maxDis
                if (dis < maxDis) {
                    newPeakDists.clear();
                    newPeakDists.add(filteredLists.get(0).get(0));
                    for (int i = 1; i < peakPath.getPeakLists().size(); i++) {
                        newPeakDists.add(null);
                    }

                    break;
                }
            }
            if (peakDists.isEmpty()) {
                newPeakDists.add(null);
            } else {
                newPeakDists.add(peakDists.get(0));
            }
        }
        return new PeakPath(peakPath, newPeakDists);
    }

    public static void setStatus(Map<Peak, PeakPath> paths, double radiusLimit, double checkLimit) {
        //     public static double checkPath(PeakPaths peakPaths, List<PeakDistance> path) {
        paths.values().stream().sorted().forEach(path -> {
            if (path.isComplete() && path.isFree()) {
                double check = checkPath(path.getPeakPaths(), path.getPeakDistances());
                if ((path.getRadius() < radiusLimit) && (check < checkLimit)) {
                    path.confirm();
                    for (PeakDistance peakDist : path.getPeakDistances()) {
                        peakDist.getPeak().setStatus(1);
                    }
                }
            }
        });

    }

    public static void checkListsForUnambigous(PeakPaths peakPaths, double radius) {
        PeakList firstList = peakPaths.getPeakLists().get(0);
        boolean useLast = true;
        for (PeakPath path : peakPaths.getPathMap().values()) {
            if (path.getPeakDistances().size() > 1) {
                useLast = false;
                break;
            }

        }
        for (Peak peak : firstList.peaks()) {
            if (peak.getStatus() == 0) {
                List<List<PeakDistance>> filteredLists
                        = peakPaths.getNearPeaks(peak, radius);
                PeakPath path = checkForUnambigous(peakPaths, filteredLists, useLast);
                double delta = checkPath(peakPaths, path.getPeakDistances());
                if (delta < 1.0) {
                    peakPaths.getPathMap().put(path.getFirstPeak(), path);
                }
            }
        }
    }

    public static void extendPath(PeakPaths peakPaths, Peak peak, double radius, double tol) {
        if (peak.getStatus() == 0) {
            List<List<PeakDistance>> filteredLists
                    = peakPaths.getNearPeaks(peak, radius);
            PeakPath path = extendPath(peakPaths, filteredLists, tol);
            if (!path.getPeakDistances().isEmpty()) {
                peakPaths.getPathMap().put(path.getFirstPeak(), path);
                double delta = checkPath(peakPaths, path.getPeakDistances());
            }
        }
    }

    public static void extendPaths(PeakPaths peakPath, double radius, double tol) {
        PeakList firstList = peakPath.getPeakLists().get(0);
        for (Peak peak : firstList.peaks()) {
            extendPath(peakPath, peak, radius, tol);
        }
    }

    public static double checkPath(PeakPaths peakPaths, List<PeakDistance> path) {
        double[][] indVars = peakPaths.getXValues();
        int[] peakDims = peakPaths.getPeakDims();
        double[] tols = peakPaths.getTols();
        int nElems = 0;
        for (PeakDistance peakDist : path) {
            if (peakDist != null) {
                nElems++;
            }
        }
        nElems--;  // account for skip entry
        double maxDelta = 0.0;
        for (int iSkip = 1; iSkip < path.size(); iSkip++) {
            if (path.get(iSkip) != null) {
                double deltaSum = 0.0;
                for (int iDim : peakDims) {
                    double[] yValues = new double[nElems];
                    double[][] xValues = new double[nElems][1];
                    double[] weightValues = new double[yValues.length];
                    int j = 0;
                    int i = 0;
                    for (PeakDistance peakDist : path) {
                        if ((i != iSkip) && (peakDist != null)) {
                            yValues[j] = peakDist.getDelta(iDim);
                            xValues[j][0] = indVars[0][i];
                            weightValues[j] = tols[iDim];
                            j++;
                        }
                        i++;
                    }
                    if (j > 2) {
                        MercerKernel mKernel = new GaussianKernel(2000.0);
                        GaussianProcessRegression gaussianProcessRegression = new GaussianProcessRegression(xValues, yValues, mKernel, 0.001);
                        double[] testPoint = {indVars[0][iSkip]};
                        double iValue = gaussianProcessRegression.predict(testPoint);
                        double mValue = path.get(iSkip).getDelta(iDim);
                        double delta = (iValue - mValue) / tols[iDim];
                        deltaSum += delta * delta;
                    } else {
                        deltaSum = 1000.0;
                    }
                }

                double delta = Math.sqrt(deltaSum);
                if (delta > maxDelta) {
                    maxDelta = delta;
                }
            }
        }
        return maxDelta;
    }

    public static PeakPath checkPath(PeakPaths peakPaths, List<List<PeakDistance>> filteredLists, double tol) {

        double[][] indVars = peakPaths.getXValues();
        double[] tols = peakPaths.getTols();
        int[] peakDims = peakPaths.getPeakDims();
        int[] indices = new int[indVars[0].length];
        int nUseLevel = 0;

        for (int iLevel = 0; iLevel < filteredLists.size(); iLevel++) {
            if ((filteredLists.get(iLevel).size() == 1) || ((iLevel < 3) && !filteredLists.get(iLevel).isEmpty())) {
                nUseLevel++;
                indices[iLevel] = 0;
            } else {
                indices[iLevel] = -1;
            }
        }
        GaussianProcessRegression[] gaussianProcessRegressions = new GaussianProcessRegression[peakDims.length];
        for (int iDim : peakDims) {
            double[] yValues = new double[nUseLevel];
            double[][] xValues = new double[nUseLevel][1];
            double[] weightValues = new double[nUseLevel];
            int j = 0;

            for (int iLevel = 0; iLevel < filteredLists.size(); iLevel++) {
                if (indices[iLevel] >= 0) {
                    PeakDistance peakDist = filteredLists.get(iLevel).get(indices[iLevel]);
                    yValues[j] = peakDist.getDelta(iDim);
                    xValues[j][0] = indVars[0][iLevel];
                    weightValues[j] = tols[iDim];
                    j++;
                }
            }
            MercerKernel mKernel = new GaussianKernel(2000.0);
            gaussianProcessRegressions[iDim] =  new GaussianProcessRegression(xValues, yValues, mKernel, 0.001);
        }
        for (int iLevel = 0; iLevel < filteredLists.size(); iLevel++) {
            if (indices[iLevel] < 0) {
                List<PeakDistance> peakDists = filteredLists.get(iLevel);
                int j = 0;
                double minDis = Double.MAX_VALUE;
                for (PeakDistance peakDist : peakDists) {
                    double sumSq = 0.0;
                    for (int iDim : peakDims) {
                        double[] testValue = {indVars[0][iLevel]};
                        double iValue = gaussianProcessRegressions[iDim].predict(testValue);
                        double deltaDelta = iValue - peakDist.getDelta(iDim);
                        sumSq += deltaDelta * deltaDelta;
                    }
                    double delta = Math.sqrt(sumSq);
                    if ((delta < tol) && (delta < minDis)) {
                        minDis = delta;
                        indices[iLevel] = j;
                    }
                    j++;
                }
            }
        }
        List<PeakDistance> path = new ArrayList<>();

        for (int iLevel = 0; iLevel < filteredLists.size(); iLevel++) {
            if (indices[iLevel] >= 0) {
                PeakDistance peakDist = filteredLists.get(iLevel).get(indices[iLevel]);
                path.add(peakDist);
            } else {
                path.add(null);

            }
        }
        return new PeakPath(peakPaths, path);
    }

    public static PeakPath extendPath(PeakPaths peakPaths, List<List<PeakDistance>> filteredLists, double tol) {
        double[][] indVars = peakPaths.getXValues();
        double[] tols = peakPaths.getTols();
        int[] peakDims = peakPaths.getPeakDims();

        int[] indices = new int[peakPaths.getXValues()[0].length];
        int nUseLevel = 0;

        Arrays.fill(indices, -1);
        for (int iLevel = 0; iLevel < 2; iLevel++) {
            if ((!filteredLists.get(iLevel).isEmpty())) {
                nUseLevel++;
                indices[iLevel] = 0;
            } else {
                indices[iLevel] = -1;
            }
        }
        GaussianProcessRegression[] gaussianProcessRegressions = new GaussianProcessRegression[peakDims.length];
        for (int jLevel = 2; jLevel < indices.length; jLevel++) {
            nUseLevel = 0;
            for (int iLevel = 0; iLevel < jLevel; iLevel++) {
                if (indices[iLevel] >= 0) {
                    nUseLevel++;
                }
            }
            for (int iDim : peakDims) {
                double[] yValues = new double[nUseLevel];
                double[][] xValues = new double[nUseLevel][1];
                double[] weightValues = new double[nUseLevel];
                int j = 0;

                for (int iLevel = 0; iLevel < jLevel; iLevel++) {
                    if (indices[iLevel] >= 0) {
                        PeakDistance peakDist = filteredLists.get(iLevel).get(indices[iLevel]);
                        yValues[j] = peakDist.getDelta(iDim);
                        xValues[j][0] = indVars[0][iLevel];
                        weightValues[j] = tols[iDim];
                        j++;
                    }
                }
                MercerKernel mKernel = new GaussianKernel(2000.0);
                gaussianProcessRegressions[iDim] =  new GaussianProcessRegression(xValues, yValues, mKernel, 0.001);
            }
            if (indices[jLevel] < 0) {
                List<PeakDistance> peakDists = filteredLists.get(jLevel);
                int j = 0;
                double minDis = Double.MAX_VALUE;
                for (PeakDistance peakDist : peakDists) {
                    double sumSq = 0.0;
                    for (int iDim : peakDims) {
                        double[] testValue = {indVars[0][jLevel]};
                        double iValue = gaussianProcessRegressions[iDim].predict(testValue);
                        double deltaDelta = iValue - peakDist.getDelta(iDim);
                        sumSq += deltaDelta * deltaDelta;
                    }
                    double delta = Math.sqrt(sumSq);
                    if ((delta < tol) && (delta < minDis)) {
                        minDis = delta;
                        indices[jLevel] = j;
                    }
                    j++;
                }
            }
        }
        List<PeakDistance> path = new ArrayList<>();

        for (int iLevel = 0; iLevel < filteredLists.size(); iLevel++) {
            if (indices[iLevel] >= 0) {
                PeakDistance peakDist = filteredLists.get(iLevel).get(indices[iLevel]);
                path.add(peakDist);
            } else {
                path.add(null);

            }
        }
        return new PeakPath(peakPaths, path);
    }

    public static List<PeakDistance> scan(PeakPaths peakPaths, final Peak startPeak, double radius, double tolMul, int midListIndex, final Peak lastPeak, boolean requireLinear) {
        List<PeakDistance> endPeakDists = new ArrayList<>();
        List<List<PeakDistance>> filteredLists;
        if ((lastPeak != null)
                && (lastPeak.getPeakList() == peakPaths.getPeakLists().get(peakPaths.getPeakLists().size() - 1))) {
            double distance = peakPaths.calcDistance(startPeak, lastPeak);
            double[] deltas = peakPaths.calcDeltas(startPeak, lastPeak);
            PeakDistance peakDis = new PeakDistance(lastPeak, distance, deltas);
            endPeakDists.add(peakDis);
            filteredLists = peakPaths.getNearPeaks(startPeak, distance * 1.1);
        } else {
            filteredLists = peakPaths.getNearPeaks(startPeak, radius);
            List<PeakDistance> lastPeaks = filteredLists.get(filteredLists.size() - 1);
            endPeakDists.addAll(lastPeaks);
            Collections.sort(endPeakDists);
        }
        double[][] indVars = peakPaths.getXValues();
        double[] tols = peakPaths.getTols();
        double[] weights = peakPaths.getWeights();
        int[] peakDims = peakPaths.getPeakDims();
        List<PeakDistance> midPeaks = filteredLists.get(midListIndex);
        if (midPeaks.isEmpty()) {
            midListIndex--;
            midPeaks = filteredLists.get(midListIndex);
        }
        if (midPeaks.isEmpty()) {
            midListIndex += 2;
            midPeaks = filteredLists.get(midListIndex);
        }
        double firstConc = indVars[0][0];
        double midConc = indVars[0][midListIndex];
        double lastConc = indVars[0][indVars[0].length - 1];
        ArrayList<PeakDistance> bestPath = new ArrayList<>();
        double sum = 0.0;
        for (int iDim : peakDims) {
            double boundary = startPeak.getPeakDim(iDim).getBoundsValue();
            sum += boundary * boundary / (weights[iDim] * weights[iDim]);
        }
        double tol = tolMul * Math.sqrt(sum);
        double minRMS = Double.MAX_VALUE;
        double intensityScale = peakPaths.getDTol() / startPeak.getIntensity();
        for (PeakDistance endPeakDist : endPeakDists) {
            Peak endPeak = endPeakDist.getPeak();
            System.out.println("test ############### " + endPeak.getName() + " ");
            double startToLast = endPeakDist.getDistance();
            ArrayList<PeakDistance> midDistancePeaks = new ArrayList<>();
            for (PeakDistance midPeakDistance : midPeaks) {
                System.out.println("try mid " + midPeakDistance.getPeak().getName());
                double linScale = 2.0;
                if (requireLinear) {
                    linScale = 1.0;
                }
                double startToMid = peakPaths.calcDistance(startPeak, midPeakDistance.getPeak());
                if (startToMid > startToLast * linScale) {
                    System.out.println("skip A");
                    continue;
                }
                double midToLast = peakPaths.calcDistance(midPeakDistance.getPeak(), endPeak);
                if (midToLast > startToLast * linScale) {
                    System.out.println("skip B");
                    continue;
                }
                if (requireLinear) {
                    // Heron's formula for area, then use area = 1/2 base*height to get height
                    // where height will be deviatin of midpoint from line between start and last
                    double s = (startToMid + startToLast + midToLast) / 2.0;
                    double area = Math.sqrt(s * (s - startToMid) * (s - startToLast) * (s - midToLast));
                    double height = 2.0 * area / startToLast;
                    // if peak too far off line between start and end skip it
                    if (height > tol) {
                        continue;
                    }
                }

                PeakDistance midValue = new PeakDistance(midPeakDistance.getPeak(),
                        midPeakDistance.getDistance(), midPeakDistance.getDeltas());
                midDistancePeaks.add(midValue);
            }
            System.out.println("nmid " + midDistancePeaks.size());
            int nDim = peakPaths.getPeakDims().length + 1;
            Collections.sort(midDistancePeaks);
            GaussianProcessRegression[] gaussianProcessRegressions = new GaussianProcessRegression[nDim];
            for (PeakDistance midPeakDistance : midDistancePeaks) {
                Peak midPeak = midPeakDistance.getPeak();
                System.out.println(" mid " + midPeak.getName() + " ");

                double[] yValues = new double[3];
                double[][] xValues = new double[3][1];
                double[] weightValues = new double[yValues.length];
                for (int jDim = 0; jDim < nDim; jDim++) {
                    if (jDim < peakDims.length) {
                        int iDim = peakDims[jDim];
                        double dTol = startPeak.getPeakDim(0).getLineWidthValue();
                        double midDis = peakPaths.calcDelta(startPeak, midPeak, iDim);
                        double lastDis = peakPaths.calcDelta(startPeak, endPeak, iDim);
                        yValues[0] = 0.0;
                        yValues[1] = midDis;
                        yValues[2] = lastDis;
                        weightValues[0] = tols[iDim];
                        weightValues[1] = tols[iDim];
                        weightValues[2] = tols[iDim];
                    } else {
                        yValues[0] = startPeak.getIntensity() * intensityScale;
                        yValues[1] = midPeak.getIntensity() * intensityScale;
                        yValues[2] = endPeak.getIntensity() * intensityScale;
                        weightValues[0] = yValues[0] * 0.05;
                        weightValues[1] = yValues[0] * 0.05;
                        weightValues[2] = yValues[0] * 0.05;
                    }
                    xValues[0][0] = firstConc;
                    xValues[1][0] = midConc;
                    xValues[2][0] = lastConc;
                    MercerKernel mKernel = new GaussianKernel(2000.0);
                    gaussianProcessRegressions[jDim] =  new GaussianProcessRegression(xValues, yValues, mKernel, 0.001);
                }
                double pathSum = 0.0;
                ArrayList<PeakDistance> path = new ArrayList<>();
                int nMissing = 0;
                List<PeakDistance> testPeaks = new ArrayList<>();
                for (int iList = 0; iList < filteredLists.size(); iList++) {
                    testPeaks.clear();
                    if (iList == 0) {
                        testPeaks.add(filteredLists.get(0).get(0));
                    } else if (iList == filteredLists.size() - 1) {
                        testPeaks.add(endPeakDist);
                    } else if (iList == midListIndex) {
                        testPeaks.add(midPeakDistance);
                    } else {
                        testPeaks.addAll(filteredLists.get(iList));
                    }
                    double[] testConc = {indVars[0][iList]};
                    double minSum = Double.MAX_VALUE;
                    PeakDistance minPeakDist = null;
                    for (PeakDistance testPeakDist : testPeaks) {
                        sum = 0.0;
                        for (int jDim = 0; jDim < nDim; jDim++) {
                            double dis;
                            if (jDim < peakDims.length) {
                                int iDim = peakDims[jDim];
                                dis = peakPaths.calcDelta(startPeak, testPeakDist.getPeak(), iDim);
                            } else {
                                dis = testPeakDist.getPeak().getIntensity() * intensityScale;
                            }
                            double estValue = gaussianProcessRegressions[jDim].predict(testConc);
                            System.out.printf("%10s %d %7.3f %7.3f\n", testPeakDist.getPeak(), jDim, dis, estValue);
                            sum += (dis - estValue) * (dis - estValue);
                        }
                        if (sum < minSum) {
                            minSum = sum;
                            minPeakDist = testPeakDist;
                        }
                    }
                    if (minPeakDist == null) {
                        nMissing++;
                        minSum = tol * tol;
                    } else {
                        System.out.printf(" min %10s %7.3f %7.3f\n", minPeakDist.getPeak(), Math.sqrt(minSum), peakPaths.getDTol());
                        if (Math.sqrt(minSum) > peakPaths.getDTol()) {
                            minSum = tol * tol;
                            minPeakDist = null;
                            nMissing++;
                        }
                    }
                    path.add(minPeakDist);
                    pathSum += minSum;
                }
                if (nMissing < 4) {
                    if (path.size() < indVars[0].length) {
                        path.add(endPeakDist);
                    }
                    double rms = Math.sqrt(pathSum / (filteredLists.size() - 3));
                    if (rms < minRMS) {
                        minRMS = rms;
                        bestPath = path;
                    }
                }
            }
        }
        PeakPath newPath;
        if (bestPath.isEmpty()) {
            newPath = new PeakPath(peakPaths, startPeak);
        } else {
            newPath = new PeakPath(peakPaths, bestPath);
            newPath.confirm();
        }
        peakPaths.getPathMap().put(newPath.getFirstPeak(), newPath);

        return bestPath;
    }

    static double predictWithPoly(double[] coefs, double x) {
        double y = 0.0;
        for (int i = 0; i < coefs.length; i++) {
            y += coefs[i] * Math.pow(x, i + 1);
        }
        return y;
    }

    public static double[] poly(double x2, double x3, double y2, double y3) {
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
}
