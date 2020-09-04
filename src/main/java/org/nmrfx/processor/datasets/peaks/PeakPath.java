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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.nmrfx.processor.optimization.VecID;
import org.nmrfx.processor.optimization.equations.OptFunction;
import org.nmrfx.processor.optimization.equations.Quadratic10;
import java.util.*;
import java.util.Map.Entry;
import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.general.LevenbergMarquardtOptimizer;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.project.Project;
import smile.interpolation.KrigingInterpolation;
import smile.interpolation.variogram.PowerVariogram;
import smile.interpolation.variogram.Variogram;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.JMatrix;
import smile.math.matrix.SVD;
import smile.regression.GaussianProcessRegression;
import smile.regression.KernelMachine;
//import smile.interpolation.KrigingInterpolation;

public class PeakPath implements PeakListener {

    static String[] PRESURE_NAMES = {"Ha", "Hb", "Hc", "Xa", "Xb", "Xc"};
    static String[] TITRATION_NAMES = {"K", "C"};

    static Map<String, PeakPath> peakPaths () {
        return Project.getActive().peakPaths;
    }

    public enum PATHMODE {
        TITRATION,
        PRESSURE;
    }
    boolean fit0 = false;
    OptFunction optFunction = new Quadratic10();
    ArrayList<PeakList> peakLists = new ArrayList<>();
//    ArrayList<ArrayList<PeakDistance>> filteredLists = new ArrayList<>();
    String name = "path1";
    String details = "";
    Map<Peak, Path> paths = new HashMap<>();
    final PeakList firstList;
    final double[][] indVars;
    int[] peakDims = {0, 1};
    final double[] weights;
    final double[] tols;
    final double dTol;
    String[] units;
    PATHMODE pathMode = PATHMODE.TITRATION;
    String[] parNames;
    List<String> datasetNames;

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        Object source = peakEvent.getSource();
        if (source instanceof PeakList) {
            PeakList peakList = (PeakList) source;
            purgePaths();
        }
    }

    public class Path implements Comparable<Path> {

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.firstPeak);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Path other = (Path) obj;
            if (!Objects.equals(this.firstPeak, other.firstPeak)) {
                return false;
            }
            return true;
        }

        Peak firstPeak;
        List<PeakDistance> peakDists = new ArrayList<>();
        double radius;
        boolean confirmed = false;
        boolean active = false;
        double[] pars = null;
        double[] parErrs = null;

        Path(List<PeakDistance> path, double dis) {
            peakDists.addAll(path);
            firstPeak = path.get(0).getPeak();
            radius = dis;
        }

        Path(Peak peak) {
            firstPeak = peak;
            double[] deltas = new double[tols.length];
            PeakDistance peakDist = new PeakDistance(peak, 0.0, deltas);

            peakDists.add(peakDist);
            for (int i = 1; i < peakLists.size(); i++) {
                peakDists.add(null);
            }
            radius = 0.0;
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

        public void refresh() {
            for (int i = 0; i < peakDists.size(); i++) {
                PeakDistance peakDis = peakDists.get(i);
                if (peakDis != null) {
                    double[] deltas = calcDeltas(firstPeak, peakDis.peak);
                    double distance = calcDistance(firstPeak, peakDis.peak);
                    PeakDistance newPeakDis = new PeakDistance(peakDis.peak, distance, deltas);
                    peakDists.set(i, newPeakDis);
                }
            }

        }

        public List<PeakDistance> getPeakDistances() {
            return peakDists;
        }

        public void confirm() {
            confirmed = true;
        }

        public boolean confirmed() {
            return confirmed;
        }

        public void setActive(boolean state) {
            active = state;
        }

        public boolean isActive() {
            return active;
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
                if (peakDis != null) {
                    if (peakDis.peak.getStatus() != 0) {
                        free = false;
                        break;
                    }
                }
            }
            return free;
        }

        public int getNValid() {
            int nValid = 0;
            for (PeakDistance peakDis : peakDists) {
                if (peakDis != null) {
                    nValid++;
                }
            }
            return nValid;
        }

        public double check() {
            return checkPath(peakDists);
        }

        public int getId() {
            return getFirstPeak().getIdNum();
        }

        public Peak getFirstPeak() {
            return firstPeak;
        }

        public void setFitPars(double[] pars) {
            this.pars = pars != null ? pars.clone() : null;
        }

        public void setFitErrs(double[] parErrs) {
            this.parErrs = parErrs != null ? parErrs.clone() : null;
        }

        public int getPeak() {
            return firstPeak.getIdNum();
        }

        public double getPar(int i) {
            return pars[i];
        }

        public double getErr(int i) {
            return parErrs[i];
        }

        public boolean hasPars() {
            return pars != null;
        }

        public double[] getFitPars() {
            return pars;
        }

        public double[] getFitErrs() {
            return parErrs;
        }

        public Double getA() {
            if (pars == null) {
                return null;
            } else {
                if (pars.length == 3) {
                    return pars[0];
                } else {
                    return 0.0;
                }
            }
        }

        public Double getADev() {
            if (parErrs == null) {
                return null;
            } else {
                if (parErrs.length == 3) {
                    return parErrs[0];
                } else {
                    return 0.0;
                }
            }
        }

        public Double getK() {
            if (pars == null) {
                return null;
            } else {
                if (pars.length == 3) {
                    return pars[1];
                } else {
                    return pars[0];
                }
            }
        }

        public Double getKDev() {
            if (parErrs == null) {
                return null;
            } else {
                if (parErrs.length == 3) {
                    return parErrs[1];
                } else {
                    return parErrs[0];
                }
            }
        }

        public Double getC() {
            if (pars == null) {
                return null;
            } else {
                if (pars.length == 3) {
                    return pars[2];
                } else {
                    return pars[1];
                }
            }
        }

        public Double getCDev() {
            if (parErrs == null) {
                return null;
            } else {
                if (parErrs.length == 3) {
                    return parErrs[2];
                } else {
                    return parErrs[1];
                }
            }
        }

        public String toSTAR3ParString(int id, int pathID, int dim) {
            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append(String.format("%4d %4d %d %3s %3s", id, pathID, dim + 1,
                    (confirmed ? "yes" : "no"), (active ? "yes" : "no")));
            int nPars = pathMode == PATHMODE.PRESSURE ? 2 : 2;

            int start = dim * nPars;
            for (int i = 0; i < nPars; i++) {
                sBuilder.append(" ");
                if (pars == null) {
                    sBuilder.append(String.format("%10s %10s", "?", "?"));
                } else {
                    sBuilder.append(String.format("%10.4f %10.4f", pars[i + start], parErrs[i + start]));
                }
            }
            return sBuilder.toString();
        }

        public String toSTAR3String(int i) {
            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append(i);
            for (PeakDistance peakDis : peakDists) {
                sBuilder.append(" ");
                if (peakDis == null) {
                    sBuilder.append("?");
                } else {
                    sBuilder.append(peakDis.peak.getIdNum());
                }
            }
            return sBuilder.toString();
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

    public PeakPath(String name, final List<PeakList> peakLists, double[] concentrations, final double[] binderConcs, final double[] weights, PATHMODE pathMode) {
        this(name, peakLists, concentrations, binderConcs, weights, null, pathMode);
    }

    public PeakPath(String name, final List<PeakList> peakLists, double[] concentrations,
            final double[] binderConcs, final double[] weights, double[] tols, PATHMODE pathMode) {
        this.name = name;
        this.pathMode = pathMode;
        this.peakLists = new ArrayList<>();
        this.datasetNames = new ArrayList<>();
        for (PeakList peakList : peakLists) {
            peakList.registerListener(this);
            this.peakLists.add(peakList);
            this.datasetNames.add(peakList.getDatasetName());
        }
        firstList = peakLists.get(0);
        if (tols == null) {
            tols = new double[weights.length];
            int i = 0;
            for (int peakDim : peakDims) {
                DoubleSummaryStatistics dStat = firstList.widthStatsPPM(peakDim);
                tols[i] = dStat.getAverage() / weights[i];
                System.out.printf("tol %d %.3f\n", i, tols[i]);
                i++;
            }
        }
        double tolSum = 0.0;
        int i = 0;
        for (int peakDim : peakDims) {
            tolSum += tols[i] * tols[i];
            i++;
        }
        dTol = Math.sqrt(tolSum);

        this.tols = tols;
        parNames = pathMode == PATHMODE.PRESSURE ? PRESURE_NAMES : TITRATION_NAMES;

        this.indVars = new double[2][];
        this.indVars[0] = concentrations;
        this.indVars[1] = binderConcs;
        this.weights = weights;
    }

    public static PeakPath loadPathData(PATHMODE pathMode, File file) throws IOException, IllegalArgumentException {
        List<String> datasetNames = new ArrayList<>();
        List<PeakList> peakLists = new ArrayList<>();
        PeakPath peakPath = null;
        if (file != null) {
            List<Double> x0List = new ArrayList<>();
            List<Double> x1List = new ArrayList<>();
            String sepChar = " +";
            List<String> lines = Files.readAllLines(file.toPath());
            if (lines.size() > 0) {
                if (lines.get(0).contains("\t")) {
                    sepChar = "\t";
                }
                for (String line : lines) {
                    System.out.println("line is " + line);
                    String[] fields = line.split(sepChar);
                    if ((fields.length > 1) && !fields[0].startsWith("#")) {
                        datasetNames.add(fields[0]);
                        x0List.add(Double.parseDouble(fields[1]));
                        if (fields.length > 2) {
                            x1List.add(Double.parseDouble(fields[2]));
                        }
                    }
                }
            }
            double[] x0 = new double[x0List.size()];
            double[] x1 = new double[x0List.size()];
            System.out.println("do data");
            for (int i = 0; i < datasetNames.size(); i++) {
                String datasetName = datasetNames.get(i);
                Dataset dataset = Dataset.getDataset(datasetName);
                if (dataset == null) {
                    throw new IllegalArgumentException("\"Dataset \"" + datasetName + "\" doesn't exist\"");
                }
                String peakListName = "";
                PeakList peakList = PeakList.getPeakListForDataset(datasetName);
                if (peakList == null) {
                    peakListName = PeakList.getNameForDataset(datasetName);
                    peakList = PeakList.get(peakListName);
                } else {
                    peakListName = peakList.getName();
                }
                if (peakList == null) {
                    throw new IllegalArgumentException("\"PeakList \"" + peakList + "\" doesn't exist\"");
                }
                peakLists.add(peakList);
                x0[i] = x0List.get(i);
                if (!x1List.isEmpty()) {
                    x1[i] = x1List.get(i);
                } else {
                    x1[i] = 100.0;
                }
            }
            double[] weights = {1.0, 5.0};  // fixme  need to figure out from nuclei
            System.out.println("do data1");
            String peakPathName = file.getName();
            if (peakPathName.contains(".")) {
                peakPathName = peakPathName.substring(0, peakPathName.indexOf("."));
            }
            peakPath = new PeakPath(peakPathName, peakLists, x0, x1, weights, pathMode);
            peakPath.store();
            peakPath.initPaths();
            peakPath.datasetNames = datasetNames;
        }
        return peakPath;
    }

    public String getUnits() {
        String units = pathMode == PATHMODE.PRESSURE ? "bar" : "scaled_ppm";
        return units;
    }

    public List<String> getDatasetNames() {
        return datasetNames;
    }

    public String[] getParNames() {
        return parNames;
    }

    public void store() {
        peakPaths().put(name, this);
    }

    public void store(String name) {
        this.name = name;
        peakPaths().put(name, this);
    }

    public static Collection<PeakPath> get() {
        return peakPaths().values();
    }

    public static Collection<String> getNames() {
        return peakPaths.keySet();
    }

    public static PeakPath get(String name) {
        return peakPaths().get(name);
    }

    public String getName() {
        return name;
    }

    public List<PeakList> getPeakLists() {
        return peakLists;
    }

    public String getDetails() {
        return details;
    }

    public List<String> getSTAR3PathLoopStrings() {
        List<String> strings = new ArrayList<>();
        strings.add("_Path.Index_ID");
        strings.add("_Path.Path_ID");
        strings.add("_Path.Spectral_peak_list_ID");
        strings.add("_Path.Peak_ID");
        return strings;
    }

    public List<String> getSTAR3LoopStrings() {
        List<String> strings = new ArrayList<>();
        strings.add("_Peak_list.Spectral_peak_list_ID");
        strings.add("_Peak_list.Spectral_peak_list_label");
        if (pathMode == PATHMODE.PRESSURE) {
            strings.add("_Peak_list.Pressure");
        } else {
            strings.add("_Peak_list.Ligand_conc");
            strings.add("_Peak_list.Macromolecule_conc");
        }
        return strings;
    }

    public List<String> getBaseParNames() {
        List<String> parNames = new ArrayList<>();
        if (pathMode == PATHMODE.PRESSURE) {
            parNames.add("A");
            parNames.add("B");
        } else {
            if (fit0) {
                parNames.add("A");
            }
            parNames.add("K");
            parNames.add("C");
        }
        return parNames;
    }

    public List<String> getSTAR3ParLoopStrings() {
        List<String> strings = new ArrayList<>();
        strings.add("_Par.ID");
        strings.add("_Par.Path_ID");
        strings.add("_Par.Dim");
        strings.add("_Par.Confirmed");
        strings.add("_Par.Active");
        List<String> parNames = getBaseParNames();
        for (String parName : parNames) {
            strings.add("_Par." + parName + "_val");
            strings.add("_Par." + parName + "_val_err");
        }
        return strings;
    }

    public String getSTAR3String(int i) {
        StringBuilder sBuilder = new StringBuilder();
        PeakList peakList = peakLists.get(i);
        int id = peakList.getId();
        sBuilder.append(id).append(" $").append(peakLists.get(i).getName());
        int nVars = pathMode == PATHMODE.PRESSURE ? 1 : fit0 ? 3 : 2;
        String format = pathMode == PATHMODE.PRESSURE ? "%.1f" : "%.3f";
        for (int j = 0; j < nVars; j++) {
            sBuilder.append(" ").append(String.format(format, indVars[j][i]));
        }
        return sBuilder.toString();

    }

    public String getSTAR3DimString(int i) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append((i + 1));
        sBuilder.append(" ").append(String.format("%.3f", weights[i]));
        sBuilder.append(" ").append(String.format("%.3f", tols[i]));
        return sBuilder.toString();

    }

    public PATHMODE getPathMode() {
        return pathMode;
    }

    public void clearPaths() {
        for (PeakList peakList : peakLists) {
            for (Peak peak : peakList.peaks()) {
                if (peak.getStatus() > 0) {
                    peak.setStatus(0);
                }
            }
        }
        paths.clear();
        initPaths();
    }

    public void clearPath(Peak startPeak) {
        Path path = paths.get(startPeak);
        if (path != null) {
            for (PeakDistance peakDist : path.peakDists) {
                if (peakDist != null) {
                    if (peakDist.peak.getStatus() > 0) {
                        peakDist.peak.setStatus(0);
                    }
                }
            }
        }

    }

    public Path addPath(List<Peak> peaks) {
        Peak startPeak = peaks.get(0);
        List<PeakDistance> peakDists = new ArrayList<>();
        for (Peak peak : peaks) {
            PeakDistance peakDist = null;
            if (peak != null) {
                double distance = calcDistance(startPeak, peak);
                double[] deltas = calcDeltas(startPeak, peak);
                peakDist = new PeakDistance(peak, distance, deltas);
            }
            peakDists.add(peakDist);
        }
        Path path = new Path(peakDists);
        paths.put(path.getFirstPeak(), path);
        return path;
    }

    public void initPath(Peak peak) {
        double[] deltas = new double[tols.length];
        PeakDistance peakDist = new PeakDistance(peak, 0.0, deltas);
        List<PeakDistance> peakDists = new ArrayList<>();
        peakDists.add(peakDist);
        for (int i = 1; i < peakLists.size(); i++) {
            peakDists.add(null);
        }
        Path path = new Path(peakDists);
        paths.put(path.getFirstPeak(), path);

    }

    public void initPaths() {
        for (Peak peak : firstList.peaks()) {
            if (peak.getStatus() >= 0) {
                initPath(peak);
            }
        }
    }

    public void purgePaths() {
        Iterator<Peak> keyIter = paths.keySet().iterator();
        while (keyIter.hasNext()) {
            Peak peak = keyIter.next();
            if (peak.getStatus() < 0) {
                keyIter.remove();
            }
        }
        Iterator<Map.Entry<Peak, Path>> entryIter = paths.entrySet().iterator();
        while (entryIter.hasNext()) {
            Map.Entry<Peak, Path> entry = entryIter.next();
            List<PeakDistance> newDists = new ArrayList<>();
            boolean changed = false;
            for (PeakDistance peakDist : entry.getValue().peakDists) {
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
                entry.setValue(new Path(newDists));
            }
        }
        for (Peak peak : firstList.peaks()) {
            if (!peak.isDeleted()) {
                if (!paths.containsKey(peak)) {
                    initPath(peak);
                }
            }

        }

    }

    public Path getPath(Peak peak) {
        return paths.get(peak);
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

    double[] fitPoly(double[] x, double[] y, int order) {
        System.out.println(x.length + " " + y.length);
        if (x.length == 1) {
            double[] coef = new double[1];
            coef[0] = y[0] / x[0];
            return coef;
        }
        DenseMatrix mat = new JMatrix(x.length, order);
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < order; j++) {
                mat.set(i, j, Math.pow(x[i], order + 1));
            }
        }
        SVD svd = mat.svd();
        double[] coef = new double[order];
        double[] s = svd.getSingularValues();
        System.out.println(s.length + " " + svd.getV().nrows());
        svd.solve(y, coef);
        return coef;
    }

    double predictWithPoly(double[] coefs, double x) {
        double y = 0.0;
        for (int i = 0; i < coefs.length; i++) {
            y += coefs[i] * Math.pow(x, i + 1);
        }
        return y;
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
//                System.out.print(peak.getName() + " ");
                ArrayList<ArrayList<PeakDistance>> filteredLists
                        = getNearPeaks(peak, radius);
                Path path = checkForUnambigous(filteredLists, useLast);
                double delta = checkPath(path.peakDists);
                if (delta < 1.0) {
                    paths.put(path.getFirstPeak(), path);
//                    System.out.println(path.toString());
//                    System.out.printf(" unam %.3f\n", delta);
                } else {
//                    System.out.println("");
                }
            }
        }
        dumpPaths();
    }

    public void extendPath(Peak peak, double radius, double tol) {
        if (peak.getStatus() == 0) {
            System.out.print(peak.getName() + " ");
            ArrayList<ArrayList<PeakDistance>> filteredLists
                    = getNearPeaks(peak, radius);
            Path path = extendPath(filteredLists, tol);
            if (!path.peakDists.isEmpty()) {
                paths.put(path.getFirstPeak(), path);
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

    public void extendPaths(double radius, double tol) {
        PeakList firstList = peakLists.get(0);
        for (Peak peak : firstList.peaks()) {
            extendPath(peak, radius, tol);
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
                            yValues[j] = peakDist.deltas[iDim];
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
                    double mValue = path.get(iSkip).deltas[iDim];
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

    public Path checkPath(ArrayList<ArrayList<PeakDistance>> filteredLists, double tol) {
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
                    yValues[j] = peakDist.deltas[iDim];
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
                    double[] deltas = peakDist.deltas;
                    double sumSq = 0.0;
                    for (int iDim : peakDims) {
                        double iValue = krig[iDim].interpolate(indVars[0][iLevel]);
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

    public Path extendPath(ArrayList<ArrayList<PeakDistance>> filteredLists, double tol) {
        int[] indices = new int[indVars[0].length];
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
                        yValues[j] = peakDist.deltas[iDim];
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
                    double[] deltas = peakDist.deltas;
                    double sumSq = 0.0;
                    for (int iDim : peakDims) {
                        double iValue = krig[iDim].interpolate(indVars[0][jLevel]);
                        //          double iValue = predictWithPoly(coefs[iDim], indVars[0][jLevel]);
                        double deltaDelta = iValue - deltas[iDim];
                        sumSq += deltaDelta * deltaDelta;
                    }
                    double delta = Math.sqrt(sumSq);
                    System.out.println(jLevel + " " + peakDist.peak.getName() + " " + delta);
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
        return new Path(path);
    }

    public ArrayList<PeakDistance> scan(final Peak startPeak, double radius, double tolMul, int midListIndex, final Peak lastPeak, boolean requireLinear) {
        ArrayList<PeakDistance> endPeakDists = new ArrayList<>();
        ArrayList<ArrayList<PeakDistance>> filteredLists;
        if ((lastPeak != null)
                && (lastPeak.getPeakList() == peakLists.get(peakLists.size() - 1))) {
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
        double intensityScale = dTol / startPeak.getIntensity();
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
                    double testConc = indVars[0][iList];
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
                        System.out.printf(" min %10s %7.3f %7.3f\n", minPeakDist.getPeak(), Math.sqrt(minSum), dTol);
                        if (Math.sqrt(minSum) > dTol) {
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
        Path newPath;
        if (bestPath.isEmpty()) {
            newPath = new Path(startPeak);
        } else {
            newPath = new Path(bestPath);
            newPath.confirm();
        }
        paths.put(newPath.getFirstPeak(), newPath);

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
                    concList.add(indVars[0][iList]);
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

    public void refreshPaths() {
        for (Peak peak : paths.keySet()) {
            refreshPath(peak);
        }
    }

    public void refreshPath(Peak startPeak) {
        Path path = getPath(startPeak);
        if (path != null) {
            path.refresh();
        }
    }

    public void addPeak(Peak startPeak, Peak selPeak) {
        Path path = getPath(startPeak);
        System.out.println("add " + selPeak.getName() + " " + selPeak.getStatus());
        removePeak(startPeak, selPeak);
        if ((selPeak.getStatus() == 0) && (path != null)) {
            double distance = calcDistance(startPeak, selPeak);
            double[] deltas = calcDeltas(startPeak, selPeak);
            PeakDistance peakDist = new PeakDistance(selPeak, distance, deltas);
            int index = peakLists.indexOf(selPeak.getPeakList());
            path.peakDists.set(index, peakDist);
            startPeak.setStatus(1);
            selPeak.setStatus(1);
            //path.confirm();
            System.out.println(path.toString());
        }

    }

    public Peak findPathPeak(Peak peak) {
        for (Entry<Peak, Path> eSet : paths.entrySet()) {
            for (PeakDistance peakDist : eSet.getValue().peakDists) {
                if ((peakDist != null) && (peakDist.peak == peak)) {
                    return eSet.getValue().firstPeak;
                }
            }

        }
        return null;
    }

    public void removePeak(Peak startPeak, Peak selPeak) {
        Peak pathPeak = findPathPeak(selPeak);
        Path path = getPath(pathPeak);
        selPeak.setStatus(0);
        System.out.println("remove " + selPeak.getName() + " " + selPeak.getStatus());
        if ((pathPeak != null) && (path != null)) {
            int index = peakLists.indexOf(selPeak.getPeakList());
            path.peakDists.set(index, null);
            //path.confirm();
            System.out.println(path.toString());
        }
    }

    public Collection<Path> getPaths() {
        return paths.values();
    }

    public double[][] getXValues() {
        return indVars;
    }
}
