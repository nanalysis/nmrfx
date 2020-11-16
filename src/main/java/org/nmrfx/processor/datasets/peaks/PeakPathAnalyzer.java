package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.general.LevenbergMarquardtOptimizer;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.optimization.VecID;
import org.nmrfx.processor.optimization.equations.OptFunction;
import org.nmrfx.processor.optimization.equations.Quadratic10;
import smile.interpolation.KrigingInterpolation;
import smile.interpolation.variogram.PowerVariogram;
import smile.interpolation.variogram.Variogram;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.Matrix;
import smile.regression.GaussianProcessRegression;
import smile.regression.KernelMachine;

import java.util.*;

public class PeakPathAnalyzer {
    OptFunction optFunction = new Quadratic10();
    public static void purgePaths(PeakPaths peakPath) {
        Iterator<Peak> keyIter = peakPath.getPathMap().keySet().iterator();
        while (keyIter.hasNext()) {
            Peak peak = keyIter.next();
            if (peak.getStatus() < 0) {
                keyIter.remove();
            }
        }
        Iterator<Map.Entry<Peak, PeakPath>> entryIter = peakPath.getPathMap().entrySet().iterator();
        while (entryIter.hasNext()) {
            Map.Entry<Peak, PeakPath> entry = entryIter.next();
            List<PeakDistance> newDists = new ArrayList<>();
            boolean changed = false;
            for (PeakDistance peakDist : entry.getValue().getPeakDistances()) {
                if (peakDist == null) {
                    newDists.add(null);
                } else {
                    if (peakDist.getPeak().getStatus() <= 0) {
                        newDists.add(null);
                        changed = true;
                    } else {
                        newDists.add(peakDist);
                    }
                }
            }
            if (changed) {
                entry.setValue(new PeakPath(peakPath, newDists));
            }
        }
        for (Peak peak : peakPath.getFirstList().peaks()) {
            if (!peak.isDeleted()) {
                if (!peakPath.getPathMap().containsKey(peak)) {
                    peakPath.initPath(peak);
                }
            }

        }

    }

    public static double[] quadFitter(OptFunction optFunction, double[] xValues, double[] pValues, double[] yValues) {

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
        }
        return retVal;

    }

    public static PeakPath checkForUnambigous(PeakPaths peakPath, ArrayList<ArrayList<PeakDistance>> filteredLists,
                                              boolean useLast) {
        // find largest first distance
        double maxDis = Double.NEGATIVE_INFINITY;
        double lastDis = 0.0;
        for (ArrayList<PeakDistance> peakDists : filteredLists) {
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
        System.out.printf("%.3f ", maxDis);
        List<PeakDistance> newPeakDists = new ArrayList<>();
        for (ArrayList<PeakDistance> peakDists : filteredLists) {
            if (peakDists.size() > 1) {
                double dis = peakDists.get(1).getDistance();
                // there should only be one distance shorter than the maxDis
                if (dis < maxDis) {
                    System.out.println("skip " + dis + " " + peakDists.get(1).getPeak().getName());
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
        PeakPath newPath = new PeakPath(peakPath, newPeakDists);
        return newPath;
    }

    public static void setStatus(Map<Peak, PeakPath> paths, double radiusLimit, double checkLimit) {
        paths.values().stream().sorted().forEach(path -> {
            if (path.isComplete() && path.isFree()) {
                double check = path.check();
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
//                System.out.print(peak.getName() + " ");
                ArrayList<ArrayList<PeakDistance>> filteredLists
                        = peakPaths.getNearPeaks(peak, radius);
                PeakPath path = checkForUnambigous(peakPaths, filteredLists, useLast);
                double delta = checkPath(peakPaths, path.getPeakDistances());
                if (delta < 1.0) {
                    peakPaths.getPathMap().put(path.getFirstPeak(), path);
//                    System.out.println(path.toString());
//                    System.out.printf(" unam %.3f\n", delta);
                } else {
//                    System.out.println("");
                }
            }
        }
        peakPaths.dumpPaths();
    }

    public static void extendPath(PeakPaths peakPaths, Peak peak, double radius, double tol) {
        if (peak.getStatus() == 0) {
            System.out.print(peak.getName() + " ");
            ArrayList<ArrayList<PeakDistance>> filteredLists
                    = peakPaths.getNearPeaks(peak, radius);
            PeakPath path = extendPath(peakPaths, filteredLists, tol);
            if (!path.getPeakDistances().isEmpty()) {
                peakPaths.getPathMap().put(path.getFirstPeak(), path);
                System.out.println(path.toString());
//                    for (PeakDistance pathPeak : path.peakDists) {
//                        if (pathPeak != null) {
//                            pathPeak.peak.setStatus(1);
//                        }
//                    }
                double delta = checkPath(peakPaths, path.getPeakDistances());
                System.out.printf(" unam %.3f\n", delta);
            } else {
                System.out.println("");
            }
        }

    }

    public static void extendPaths(PeakPaths peakPath, double radius, double tol) {
        PeakList firstList = peakPath.getPeakLists().get(0);
        for (Peak peak : firstList.peaks()) {
            extendPath(peakPath, peak, radius, tol);
        }
        peakPath.dumpPaths();
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
                    Variogram vGram = new PowerVariogram(xValues, yValues);
                    KrigingInterpolation krig = new KrigingInterpolation(xValues,
                            yValues, vGram, weightValues);
                    double iValue = krig.interpolate(indVars[0][iSkip]);
                    double mValue = path.get(iSkip).getDelta(iDim);
                    double delta = (iValue - mValue) / tols[iDim];
                    deltaSum += delta * delta;
                }

                double delta = Math.sqrt(deltaSum);
                if (delta > maxDelta) {
                    maxDelta = delta;
                }
            }
        }
        return maxDelta;
    }

    public static PeakPath checkPath(PeakPaths peakPaths, ArrayList<ArrayList<PeakDistance>> filteredLists, double tol) {

        double[][] indVars = peakPaths.getXValues();
        double[] tols = peakPaths.getTols();
        double[] weights = peakPaths.getWeights();
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
        KrigingInterpolation krig[] = new KrigingInterpolation[peakDims.length];
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
            Variogram vGram = new PowerVariogram(xValues, yValues);
            krig[iDim] = new KrigingInterpolation(xValues,
                    yValues, vGram, weightValues);
        }
        for (int iLevel = 0; iLevel < filteredLists.size(); iLevel++) {
            if (indices[iLevel] < 0) {
                ArrayList<PeakDistance> peakDists = filteredLists.get(iLevel);
                int j = 0;
                double minDis = Double.MAX_VALUE;
                for (PeakDistance peakDist : peakDists) {
                    double sumSq = 0.0;
                    for (int iDim : peakDims) {
                        double iValue = krig[iDim].interpolate(indVars[0][iLevel]);
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

    public static PeakPath extendPath(PeakPaths peakPaths, ArrayList<ArrayList<PeakDistance>> filteredLists, double tol) {
        double[][] indVars = peakPaths.getXValues();
        double[] tols = peakPaths.getTols();
        double[] weights = peakPaths.getWeights();
        int[] peakDims = peakPaths.getPeakDims();

        int[] indices = new int[peakPaths.getXValues()[0].length];
        int nUseLevel = 0;

        for (int iLevel = 0; iLevel < indices.length; iLevel++) {
            indices[iLevel] = -1;

        }
        for (int iLevel = 0; iLevel < 2; iLevel++) {
            if ((filteredLists.get(iLevel).size() != 0)) {
                nUseLevel++;
                indices[iLevel] = 0;
            } else {
                indices[iLevel] = -1;
            }
        }
        KrigingInterpolation krig[] = new KrigingInterpolation[peakDims.length];
        //    double[][] coefs = new double[peakDims.length][];
        for (int jLevel = 2; jLevel < indices.length; jLevel++) {
            nUseLevel = 0;
            for (int iLevel = 0; iLevel < jLevel; iLevel++) {
                if (indices[iLevel] >= 0) {
                    nUseLevel++;
                }
            }
            System.out.println("level " + jLevel + " " + nUseLevel);
            for (int iDim : peakDims) {
                double[] yValues = new double[nUseLevel];
                double[][] xValues = new double[nUseLevel][1];
                //     double[] xValues = new double[nUseLevel];
                double[] weightValues = new double[nUseLevel];
                int j = 0;

                for (int iLevel = 0; iLevel < jLevel; iLevel++) {
                    if (indices[iLevel] >= 0) {
                        PeakDistance peakDist = filteredLists.get(iLevel).get(indices[iLevel]);
                        yValues[j] = peakDist.getDelta(iDim);
                        // xValues[j] = indVars[0][iLevel];
                        xValues[j][0] = indVars[0][iLevel];
                        weightValues[j] = tols[iDim];
                        j++;
                    }
                }
                //    coefs[iDim] = fitPoly(xValues, yValues, 2);
                Variogram vGram = new PowerVariogram(xValues, yValues);
                krig[iDim] = new KrigingInterpolation(xValues,
                        yValues, vGram, weightValues);
            }
            if (indices[jLevel] < 0) {
                ArrayList<PeakDistance> peakDists = filteredLists.get(jLevel);
                int j = 0;
                double minDis = Double.MAX_VALUE;
                for (PeakDistance peakDist : peakDists) {
                    double sumSq = 0.0;
                    for (int iDim : peakDims) {
                        double iValue = krig[iDim].interpolate(indVars[0][jLevel]);
                        //          double iValue = predictWithPoly(coefs[iDim], indVars[0][jLevel]);
                        double deltaDelta = iValue - peakDist.getDelta(iDim);
                        sumSq += deltaDelta * deltaDelta;
                    }
                    double delta = Math.sqrt(sumSq);
                    System.out.println(jLevel + " " + peakDist.getPeak().getName() + " " + delta);
                    if ((delta < tol) && (delta < minDis)) {
                        minDis = delta;
                        indices[jLevel] = j;
                    }
                    j++;
                }
                System.out.println("best " + indices[jLevel]);
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

    public static ArrayList<PeakDistance> scan(PeakPaths peakPaths, final Peak startPeak, double radius, double tolMul, int midListIndex, final Peak lastPeak, boolean requireLinear) {
        ArrayList<PeakDistance> endPeakDists = new ArrayList<>();
        ArrayList<ArrayList<PeakDistance>> filteredLists;
        if ((lastPeak != null)
                && (lastPeak.getPeakList() == peakPaths.getPeakLists().get(peakPaths.getPeakLists().size() - 1))) {
            double distance = peakPaths.calcDistance(startPeak, lastPeak);
            double[] deltas = peakPaths.calcDeltas(startPeak, lastPeak);
            PeakDistance peakDis = new PeakDistance(lastPeak, distance, deltas);
            endPeakDists.add(peakDis);
            filteredLists = peakPaths.getNearPeaks(startPeak, distance * 1.1);
        } else {
            filteredLists = peakPaths.getNearPeaks(startPeak, radius);
            ArrayList<PeakDistance> lastPeaks = filteredLists.get(filteredLists.size() - 1);
            for (PeakDistance peakDis : lastPeaks) {
                endPeakDists.add(peakDis);
            }
            Collections.sort(endPeakDists);
        }
        double[][] indVars = peakPaths.getXValues();
        double[] tols = peakPaths.getTols();
        double[] weights = peakPaths.getWeights();
        int[] peakDims = peakPaths.getPeakDims();
        ArrayList<PeakDistance> midPeaks = filteredLists.get(midListIndex);
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
            //System.out.println("end " + lastPeak.getName() + " " + startToLast);
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
                    //System.out.println(midPeak.getName() + " " + tol + " " + height);
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
            KrigingInterpolation krig[] = new KrigingInterpolation[nDim];
            for (PeakDistance midPeakDistance : midDistancePeaks) {
                Peak midPeak = midPeakDistance.getPeak();
                System.out.println(" mid " + midPeak.getName() + " ");

                //System.out.println("mid " + midPeak.getName());
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
                    Variogram vGram = new PowerVariogram(xValues, yValues);
                    krig[jDim] = new KrigingInterpolation(xValues, yValues, vGram, weightValues);
                }
                double pathSum = 0.0;
                ArrayList<PeakDistance> path = new ArrayList<>();
                boolean pathOK = true;
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
                    double testConc = indVars[0][iList];
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
                            double estValue = krig[jDim].interpolate(testConc);
                            System.out.printf("%10s %d %7.3f %7.3f\n", testPeakDist.getPeak(), jDim, dis, estValue);
                            sum += (dis - estValue) * (dis - estValue);
                        }
                        //System.out.println(testPeak.getName() + " " + sum + " " + minSum);
                        if (sum < minSum) {
                            minSum = sum;
                            minPeakDist = testPeakDist;
                        }
                    }
                    if (minPeakDist == null) {
                        System.out.println(" no min ");
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
                    //System.out.println(minPeak.getName());
                    pathSum += minSum;
                }
                System.out.print(" nmiss " + nMissing + " " + pathOK);
                if (pathOK && (nMissing < 4)) {
                    if (path.size() < indVars[0].length) {
                        path.add(endPeakDist);
                    }
                    double rms = Math.sqrt(pathSum / (filteredLists.size() - 3));
                    //System.out.println(rms);
                    System.out.println(" " + rms);
                    if (rms < minRMS) {
                        minRMS = rms;
                        bestPath = path;
                    }
                } else {
                    System.out.println("");
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

    public static ArrayList<Peak> scan2(PeakPaths peakPaths, final String startPeakName, double radius, double tolMul, int midListIndex, final String lastPeakName) {
        double[][] indVars = peakPaths.getXValues();
        double[] tols = peakPaths.getTols();
        double[] weights = peakPaths.getWeights();
        int[] peakDims = peakPaths.getPeakDims();

        Peak startPeak = PeakListBase.getAPeak(startPeakName);
        ArrayList<PeakDistance> peakDistances = new ArrayList<>();
        ArrayList<ArrayList<PeakDistance>> filteredLists;
        if (lastPeakName.length() != 0) {
            Peak lastPeak = PeakListBase.getAPeak(lastPeakName);
            double distance = peakPaths.calcDistance(startPeak, lastPeak);
            double[] cDeltas = peakPaths.calcDeltas(startPeak, lastPeak);
            PeakDistance peakDis = new PeakDistance(lastPeak, distance, cDeltas);
            peakDistances.add(peakDis);
            filteredLists = peakPaths.getNearPeaks(startPeak, distance * 1.1);
        } else {
            filteredLists = peakPaths.getNearPeaks(startPeak, radius);
            ArrayList<PeakDistance> lastPeaks = filteredLists.get(filteredLists.size() - 1);
            for (PeakDistance peakDis : lastPeaks) {
                peakDistances.add(peakDis);
            }
            Collections.sort(peakDistances);
        }
        ArrayList<PeakDistance> midPeaks = filteredLists.get(midListIndex);
        double firstConc = indVars[0][0];
        double midConc = indVars[0][midListIndex];
        double lastConc = indVars[0][indVars[0].length - 1];
        double firstBConc = indVars[1][0];
        double midBConc = indVars[1][midListIndex];
        double lastBConc = indVars[1][indVars[1].length - 1];
        ArrayList<Peak> bestPath = new ArrayList<>();
        double sum = 0.0;

        for (int iDim : peakDims) {
            double boundary = startPeak.getPeakDim(iDim).getBoundsValue();
            sum += boundary * boundary / (weights[iDim] * weights[iDim]);
        }
        double tol = tolMul * Math.sqrt(sum);
        double minRMS = Double.MAX_VALUE;
        int lastList = 0;
        double maxDis = 0.0;
        for (int iList = filteredLists.size() - 1; iList > 0; iList--) {
            List<PeakDistance> peakDists = filteredLists.get(iList);
            if (!peakDists.isEmpty()) {
                PeakDistance peakDist = peakDists.get(0);
                maxDis = peakDist.getDistance();
                lastList = iList;
                break;
            }
        }
        maxDis += tol;
        System.out.printf("last %2d %7.3f\n", lastList, maxDis);
        List<Double> concList = new ArrayList<>();
        List<double[]> disList = new ArrayList<>();
        for (int iList = 1; iList < filteredLists.size(); iList++) {
            List<PeakDistance> peakDists = filteredLists.get(iList);
            for (PeakDistance peakDist : peakDists) {
                if (peakDist.getDistance() < maxDis) {
                    bestPath.add(peakDist.getPeak());
                    concList.add(indVars[0][iList]);
                    disList.add(peakDist.getDeltas());
                } else {
                    bestPath.add(null);
                }

            }
        }
        double[][] xValues = new double[disList.size()][1];
        double[] yValues = new double[disList.size()];
        double[] weightValues = new double[yValues.length];
        double dTol = startPeak.getPeakDim(0).getLineWidthValue();
        // Variogram vGram = new GaussianVariogram(400.0, 10.0, 10.0);
        for (int i = 0; i < yValues.length; i++) {
            yValues[i] = disList.get(i)[0];
            xValues[i][0] = concList.get(i);
            weightValues[i] = dTol;
        }
        MercerKernel mKernel = new GaussianKernel(2000.0);
//        GaussianProcessRegression gRegr = new GaussianProcessRegression(xValues, yValues, mKernel, 0.001);
        KernelMachine gRegr = GaussianProcessRegression.fit(xValues, yValues, mKernel, 0.001);
        Variogram vGram = new PowerVariogram(xValues, yValues);
        KrigingInterpolation krig = new KrigingInterpolation(xValues, yValues, vGram, weightValues);
        //    KrigingInterpolation krig = new KrigingInterpolation(xValues, yValues);
        for (int i = 0; i < yValues.length; i++) {
            double v = krig.interpolate(xValues[i][0]);
            System.out.println(i + " " + xValues[i][0] + " " + yValues[i] + " " + v + " " + gRegr.predict(xValues[i]));
        }
        for (int i = 0; i < 10; i++) {
            double x0 = i * 150.0;
            double[] xx = {i * 150.0};
            System.out.println(" " + x0 + " " + krig.interpolate(x0) + " " + gRegr.predict(xx));

        }

        return bestPath;
    }

    static double[] fitPoly(double[] x, double[] y, int order) {
        System.out.println(x.length + " " + y.length);
        if (x.length == 1) {
            double[] coef = new double[1];
            coef[0] = y[0] / x[0];
            return coef;
        }
        Matrix mat = new Matrix(x.length, order);
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < order; j++) {
                mat.set(i, j, Math.pow(x[i], order + 1));
            }
        }
        Matrix.SVD svd = mat.svd();
        double[] s = svd.s;
        System.out.println(s.length + " " + svd.V.nrows());
        double[] coef = svd.solve(y);
        return coef;
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
