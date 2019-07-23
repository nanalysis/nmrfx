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
import org.apache.commons.math.geometry.Vector3D;
import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.general.LevenbergMarquardtOptimizer;
import smile.interpolation.KrigingInterpolation;
import smile.interpolation.variogram.PowerVariogram;
import smile.interpolation.variogram.Variogram;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.MercerKernel;
import smile.regression.GaussianProcessRegression;
//import smile.interpolation.KrigingInterpolation;

public class PeakPath {

    OptFunction optFunction = new Quadratic10();
    ArrayList<PeakList> peakLists = new ArrayList<>();
//    ArrayList<ArrayList<PeakDistance>> filteredLists = new ArrayList<>();
    Map<String, Path> paths = new HashMap<>();
    final PeakList firstList;
    final double[] concentrations;
    final double[] binderConcs;
    int[] peakDims = {0, 1};
    final double[] weights;
    final double[] tols;
    final double dTol;

    public class Path implements Comparable<Path> {

        Peak firstPeak;
        List<PeakDistance> peakDists = new ArrayList<>();
        double radius;
        boolean confirmed = false;

        Path(List<PeakDistance> path, double dis) {
            peakDists.addAll(path);
            firstPeak = path.get(0).getPeak();
            radius = dis;
        }

        Path(List<PeakDistance> path) {
            peakDists.addAll(path);
            firstPeak = path.get(0).getPeak();
            double maxDis = 0.0;
            for (PeakDistance peakDis : path) {
                if ((peakDis != null) && (peakDis.distance > maxDis)) {
                    maxDis = peakDis.distance;
                }
            }
            radius = maxDis;
        }

        public List<PeakDistance> getPeakDistances() {
            return peakDists;
        }

        void confirm() {
            confirmed = true;
        }

        public boolean confirmed() {
            return confirmed;
        }

        @Override
        public int compareTo(Path o) {
            if (o == null) {
                return 1;
            } else {
                return Double.compare(radius, o.radius);
            }
        }

        public boolean isComplete() {
            boolean complete = true;
            for (PeakDistance peakDis : peakDists) {
                if (peakDis == null) {
                    complete = false;
                    break;
                }
            }
            return complete;
        }

        public boolean isFree() {
            boolean free = true;
            for (PeakDistance peakDis : peakDists) {
                if (peakDis == null) {
                    if (peakDis.peak.getStatus() != 0) {
                        free = false;
                        break;
                    }
                }
            }
            return free;
        }

        public double check() {
            return checkPath(peakDists);
        }

        public Peak getFirstPeak() {
            return firstPeak;
        }

        public String toString() {
            StringBuilder sBuilder = new StringBuilder();
            for (PeakDistance peakDis : peakDists) {
                if (sBuilder.length() != 0) {
                    sBuilder.append(" ");
                }
                if (peakDis == null) {
                    sBuilder.append("empty");
                } else {
                    sBuilder.append(peakDis.peak.getName());
                    sBuilder.append(" ");
                    sBuilder.append(String.format("%.3f", peakDis.distance));
                }
            }
            sBuilder.append(" ");
            sBuilder.append(String.format("%.3f %.3f %b", radius, check(), confirmed()));
            return sBuilder.toString();
        }
    }

    public PeakPath(final List<String> peakListNames, double[] concentrations, final double[] binderConcs, final double[] weights) {
        for (String peakListName : peakListNames) {
            PeakList peakList = PeakList.get(peakListName);
            if (peakList == null) {
                throw new IllegalArgumentException("Unknown peaklist " + peakListName);
            }
            peakLists.add(peakList);
        }
        firstList = peakLists.get(0);
        tols = new double[weights.length];
        int i = 0;
        double tolSum = 0.0;
        for (int peakDim : peakDims) {
            DoubleSummaryStatistics dStat = firstList.widthStatsPPM(peakDim);
            tols[i] = dStat.getAverage() / weights[i];
            System.out.printf("tol %d %.3f\n", i, tols[i]);
            tolSum += tols[i] * tols[i];
            i++;
        }
        dTol = Math.sqrt(tolSum);

        this.concentrations = concentrations;
        this.binderConcs = binderConcs;
        this.weights = weights;
    }

    public void initPaths() {
        for (Peak peak : firstList.peaks()) {
            if (peak.getStatus() >= 0) {
                double[] deltas = new double[tols.length];
                PeakDistance peakDist = new PeakDistance(peak, 0.0, deltas);
                List<PeakDistance> peakDists = new ArrayList<>();
                peakDists.add(peakDist);
                for (int i = 1; i < peakLists.size(); i++) {
                    peakDists.add(null);
                }
                Path path = new Path(peakDists);
                if (!path.peakDists.isEmpty()) {
                    paths.put(path.getFirstPeak().toString(), path);
                }
            }
        }
    }

    public Path getPath(String name) {
        return paths.get(name);
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

    double[] calcDeltas(Peak peak1, Peak peak2) {
        double[] deltas = new double[weights.length];
        for (int i : peakDims) {
            double ppm1 = peak1.getPeakDim(i).getChemShift();
            double ppm2 = peak2.getPeakDim(i).getChemShift();
            deltas[i] = (ppm2 - ppm1) / weights[i];
        }
        return deltas;
    }

    double calcDelta(Peak peak1, Peak peak2, int iDim) {
        double ppm1 = peak1.getPeakDim(iDim).getChemShift();
        double ppm2 = peak2.getPeakDim(iDim).getChemShift();
        return (ppm2 - ppm1) / weights[iDim];
    }

    public class PeakDistance implements Comparable<PeakDistance> {

        final Peak peak;
        final double distance;
        final double[] deltas;

        PeakDistance(Peak peak, double distance, double[] deltas) {
            this.peak = peak;
            this.distance = distance;
            this.deltas = deltas;
        }

        public Peak getPeak() {
            return peak;
        }

        @Override
        public int compareTo(PeakDistance peakDis2) {
            return Double.compare(distance, peakDis2.distance);
        }
    }

    ArrayList<ArrayList<PeakDistance>> getNearPeaks(final Peak startPeak, final double radius) {
        int iList = -1;
        ArrayList<ArrayList<PeakDistance>> filteredLists = new ArrayList<>();
        for (PeakList peakList : peakLists) {
            ArrayList<PeakDistance> peakArray = new ArrayList<>();
            filteredLists.add(peakArray);
            iList++;
            if (iList == 0) {
                double[] deltas = new double[weights.length];
                peakArray.add(new PeakDistance(startPeak, 0.0, deltas));

                continue;
            }
            int nPeaks = peakList.size();
            for (int j = 0; j < nPeaks; j++) {
                Peak peak = peakList.getPeak(j);
                if (peak.getStatus() != 0) {
                    continue;
                }
                double distance = calcDistance(startPeak, peak);
                double[] deltas = calcDeltas(startPeak, peak);
                if (distance < radius) {
                    peakArray.add(new PeakDistance(peak, distance, deltas));
                }
            }
            peakArray.sort(null);
        }
        return filteredLists;
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
            ArrayList<Peak> minPeaks = new ArrayList<>();
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

    public void dumpFiltered(ArrayList<ArrayList<PeakDistance>> filteredLists) {
        int iList = 0;
        for (ArrayList<PeakDistance> peakDists : filteredLists) {
            System.out.println(iList);
            for (PeakDistance peakDist : peakDists) {
                System.out.print("  " + peakDist.peak.getName() + " " + peakDist.distance);
            }
            System.out.println("");
        }
    }

    public Path checkForUnambigous(ArrayList<ArrayList<PeakDistance>> filteredLists,
            boolean useLast) {
        // find largest first distance
        double maxDis = Double.NEGATIVE_INFINITY;
        double lastDis = 0.0;
        for (ArrayList<PeakDistance> peakDists : filteredLists) {
            if (!peakDists.isEmpty()) {
                double dis = peakDists.get(0).distance;
                lastDis = dis;
                if (dis > maxDis) {
                    maxDis = dis;
                }
            }
        }
        if (useLast) {
            maxDis = lastDis + dTol;
        }
        System.out.printf("%.3f ", maxDis);
        List<PeakDistance> newPeakDists = new ArrayList<>();
        for (ArrayList<PeakDistance> peakDists : filteredLists) {
            if (peakDists.size() > 1) {
                double dis = peakDists.get(1).distance;
                // there should only be one distance shorter than the maxDis
                if (dis < maxDis) {
                    System.out.println("skip " + dis + " " + peakDists.get(1).peak.getName());
                    newPeakDists.clear();
                    newPeakDists.add(filteredLists.get(0).get(0));
                    for (int i = 1; i < peakLists.size(); i++) {
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
        Path newPath = new Path(newPeakDists);
        return newPath;
    }

    public void dumpPaths() {
        paths.values().stream().sorted().forEach(path -> {
            System.out.println(path.toString());
        });
    }

    public void setStatus(double radiusLimit, double checkLimit) {
        paths.values().stream().sorted().forEach(path -> {
            if (path.isComplete() && path.isFree()) {
                double check = path.check();
                if ((path.radius < radiusLimit) && (check < checkLimit)) {
                    path.confirm();
                    for (PeakDistance peakDist : path.peakDists) {
                        peakDist.peak.setStatus(1);
                    }
                }
            }
        });

    }

    public void checkListsForUnambigous(double radius) {
        PeakList firstList = peakLists.get(0);
        boolean useLast = true;
        for (Path path : paths.values()) {
            if (path.peakDists.size() > 1) {
                useLast = false;
                break;
            }

        }
        for (Peak peak : firstList.peaks()) {
            if (peak.getStatus() == 0) {
                System.out.print(peak.getName() + " ");
                ArrayList<ArrayList<PeakDistance>> filteredLists
                        = getNearPeaks(peak, radius);
                Path path = checkForUnambigous(filteredLists, useLast);
                if (!path.peakDists.isEmpty()) {
                    paths.put(path.getFirstPeak().toString(), path);
                    System.out.println(path.toString());
//                    for (PeakDistance pathPeak : path.peakDists) {
//                        if (pathPeak != null) {
//                            pathPeak.peak.setStatus(1);
//                        }
//                    }
                    double delta = checkPath(path.peakDists);
                    System.out.printf(" unam %.3f\n", delta);
                } else {
                    System.out.println("");
                }
            }
        }
        dumpPaths();
    }

    public void extendPaths(double radius, double tol) {
        PeakList firstList = peakLists.get(0);
        for (Peak peak : firstList.peaks()) {
            if (peak.getStatus() == 0) {
                System.out.print(peak.getName() + " ");
                ArrayList<ArrayList<PeakDistance>> filteredLists
                        = getNearPeaks(peak, radius);
                Path path = checkPath(filteredLists, tol);
                if (!path.peakDists.isEmpty()) {
                    paths.put(path.getFirstPeak().toString(), path);
                    System.out.println(path.toString());
//                    for (PeakDistance pathPeak : path.peakDists) {
//                        if (pathPeak != null) {
//                            pathPeak.peak.setStatus(1);
//                        }
//                    }
                    double delta = checkPath(path.peakDists);
                    System.out.printf(" unam %.3f\n", delta);
                } else {
                    System.out.println("");
                }
            }
        }
        dumpPaths();
    }

    public double checkPath(List<PeakDistance> path) {
        int nElems = 0;
        for (PeakDistance peakDist : path) {
            if (peakDist != null) {
                nElems++;
            }
        }
        double maxDelta = 0.0;
        for (int iSkip = 1; iSkip < path.size(); iSkip++) {
            double deltaSum = 0.0;
            for (int iDim : peakDims) {
                double[] yValues = new double[nElems];
                double[][] xValues = new double[nElems][1];
                double[] weightValues = new double[yValues.length];
                int j = 0;
                int i = 0;
                for (PeakDistance peakDist : path) {
                    if ((i != iSkip) && (peakDist != null)) {
                        yValues[j] = peakDist.deltas[iDim];
                        xValues[j][0] = concentrations[i];
                        weightValues[j] = tols[iDim];
                        j++;
                    }
                    i++;
                }
                if (path.get(iSkip) != null) {
                    Variogram vGram = new PowerVariogram(xValues, yValues);
                    KrigingInterpolation krig = new KrigingInterpolation(xValues,
                            yValues, vGram, weightValues);
                    double iValue = krig.interpolate(concentrations[iSkip]);
                    double mValue = path.get(iSkip).deltas[iDim];
                    double delta = (iValue - mValue) / tols[iDim];
                    deltaSum += delta * delta;
                }
            }
            double delta = Math.sqrt(deltaSum);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        return maxDelta;
    }

    public Path checkPath(ArrayList<ArrayList<PeakDistance>> filteredLists, double tol) {
        int[] indices = new int[concentrations.length];
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
                    yValues[j] = peakDist.deltas[iDim];
                    xValues[j][0] = concentrations[iLevel];
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
                    double[] deltas = peakDist.deltas;
                    double sumSq = 0.0;
                    for (int iDim : peakDims) {
                        double iValue = krig[iDim].interpolate(concentrations[iLevel]);
                        double deltaDelta = iValue - deltas[iDim];
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
        return new Path(path);
    }

    public ArrayList<PeakDistance> scan(final Peak startPeak, double radius, double tolMul, int midListIndex, final Peak lastPeak, boolean requireLinear) {
        ArrayList<PeakDistance> endPeakDists = new ArrayList<>();
        ArrayList<ArrayList<PeakDistance>> filteredLists;
        if (lastPeak != null) {
            double distance = calcDistance(startPeak, lastPeak);
            double[] deltas = calcDeltas(startPeak, lastPeak);
            PeakDistance peakDis = new PeakDistance(lastPeak, distance, deltas);
            endPeakDists.add(peakDis);
            filteredLists = getNearPeaks(startPeak, distance * 1.1);
        } else {
            filteredLists = getNearPeaks(startPeak, radius);
            ArrayList<PeakDistance> lastPeaks = filteredLists.get(filteredLists.size() - 1);
            for (PeakDistance peakDis : lastPeaks) {
                endPeakDists.add(peakDis);
            }
            Collections.sort(endPeakDists);
        }
        ArrayList<PeakDistance> midPeaks = filteredLists.get(midListIndex);
        if (midPeaks.isEmpty()) {
            midListIndex--;
            midPeaks = filteredLists.get(midListIndex);
        }
        if (midPeaks.isEmpty()) {
            midListIndex += 2;
            midPeaks = filteredLists.get(midListIndex);
        }
        double firstConc = concentrations[0];
        double midConc = concentrations[midListIndex];
        double lastConc = concentrations[concentrations.length - 1];
        ArrayList<PeakDistance> bestPath = new ArrayList<>();
        double sum = 0.0;
        for (int iDim : peakDims) {
            double boundary = startPeak.getPeakDim(iDim).getBoundsValue();
            sum += boundary * boundary / (weights[iDim] * weights[iDim]);
        }
        double tol = tolMul * Math.sqrt(sum);
        double minRMS = Double.MAX_VALUE;
        double intensityScale = radius / startPeak.getIntensity();
        for (PeakDistance endPeakDist : endPeakDists) {
            Peak endPeak = endPeakDist.peak;
            System.out.println("test ############### " + endPeak.getName() + " ");
            double startToLast = endPeakDist.distance;
            //System.out.println("end " + lastPeak.getName() + " " + startToLast);
            ArrayList<PeakDistance> midDistancePeaks = new ArrayList<>();
            for (PeakDistance midPeakDistance : midPeaks) {
                System.out.println("try mid " + midPeakDistance.peak.getName());
                double linScale = 2.0;
                if (requireLinear) {
                    linScale = 1.0;
                }
                double startToMid = calcDistance(startPeak, midPeakDistance.peak);
                if (startToMid > startToLast * linScale) {
                    System.out.println("skip A");
                    continue;
                }
                double midToLast = calcDistance(midPeakDistance.peak, endPeak);
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

                PeakDistance midValue = new PeakDistance(midPeakDistance.peak,
                        midPeakDistance.distance, midPeakDistance.deltas);
                midDistancePeaks.add(midValue);
            }
            System.out.println("nmid " + midDistancePeaks.size());
            int nDim = peakDims.length + 1;
            Collections.sort(midDistancePeaks);
            KrigingInterpolation krig[] = new KrigingInterpolation[nDim];
            for (PeakDistance midPeakDistance : midDistancePeaks) {
                Peak midPeak = midPeakDistance.peak;
                System.out.println(" mid " + midPeak.getName() + " ");

                //System.out.println("mid " + midPeak.getName());
                double[] yValues = new double[3];
                double[][] xValues = new double[3][1];
                double[] weightValues = new double[yValues.length];
                for (int jDim = 0; jDim < nDim; jDim++) {
                    if (jDim < peakDims.length) {
                        int iDim = peakDims[jDim];
                        double dTol = startPeak.getPeakDim(0).getLineWidthValue();
                        double midDis = calcDelta(startPeak, midPeak, iDim);
                        double lastDis = calcDelta(startPeak, endPeak, iDim);
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
                    double testConc = concentrations[iList];
                    double minSum = Double.MAX_VALUE;
                    PeakDistance minPeakDist = null;
                    for (PeakDistance testPeakDist : testPeaks) {
                        sum = 0.0;
                        for (int jDim = 0; jDim < nDim; jDim++) {
                            double dis;
                            if (jDim < peakDims.length) {
                                int iDim = peakDims[jDim];
                                dis = calcDelta(startPeak, testPeakDist.peak, iDim);
                            } else {
                                dis = testPeakDist.peak.getIntensity() * intensityScale;
                            }
                            double estValue = krig[jDim].interpolate(testConc);
                            System.out.printf("%10s %d %7.3f %7.3f\n", testPeakDist.peak, jDim, dis, estValue);
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
                        System.out.printf(" min %10s %7.3f\n", minPeakDist.getPeak(), Math.sqrt(minSum));
                        if (Math.sqrt(minSum) > tol) {
                            pathOK = false;
                            break;
                        }
                    }
                    path.add(minPeakDist);
                    //System.out.println(minPeak.getName());
                    pathSum += minSum;
                }
                System.out.print(" nmiss " + nMissing + " " + pathOK);
                if (pathOK && (nMissing < 3)) {
                    path.add(endPeakDist);
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
        Path newPath = new Path(bestPath);
        newPath.confirm();
        paths.put(newPath.getFirstPeak().toString(), newPath);

        return bestPath;
    }

    public ArrayList<Peak> scan2(final String startPeakName, double radius, double tolMul, int midListIndex, final String lastPeakName) {
        Peak startPeak = PeakList.getAPeak(startPeakName);
        ArrayList<PeakDistance> peakDistances = new ArrayList<>();
        ArrayList<ArrayList<PeakDistance>> filteredLists;
        if (lastPeakName.length() != 0) {
            Peak lastPeak = PeakList.getAPeak(lastPeakName);
            double distance = calcDistance(startPeak, lastPeak);
            double[] deltas = calcDeltas(startPeak, lastPeak);
            PeakDistance peakDis = new PeakDistance(lastPeak, distance, deltas);
            peakDistances.add(peakDis);
            filteredLists = getNearPeaks(startPeak, distance * 1.1);
        } else {
            filteredLists = getNearPeaks(startPeak, radius);
            ArrayList<PeakDistance> lastPeaks = filteredLists.get(filteredLists.size() - 1);
            for (PeakDistance peakDis : lastPeaks) {
                peakDistances.add(peakDis);
            }
            Collections.sort(peakDistances);
        }
        ArrayList<PeakDistance> midPeaks = filteredLists.get(midListIndex);
        double firstConc = concentrations[0];
        double midConc = concentrations[midListIndex];
        double lastConc = concentrations[concentrations.length - 1];
        double firstBConc = binderConcs[0];
        double midBConc = binderConcs[midListIndex];
        double lastBConc = binderConcs[binderConcs.length - 1];
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
                maxDis = peakDist.distance;
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
                if (peakDist.distance < maxDis) {
                    bestPath.add(peakDist.peak);
                    concList.add(concentrations[iList]);
                    disList.add(peakDist.deltas);
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
        GaussianProcessRegression gRegr = new GaussianProcessRegression(xValues, yValues, mKernel, 0.001);
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

    public Collection<Path> getPaths() {
        return paths.values();
    }

}
