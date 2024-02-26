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
package org.nmrfx.peaks;

import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.project.ProjectBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;

public class PeakPaths implements PeakListener {

    private static final String[] PRESSURE_NAMES = {"Ha", "Hb", "Hc", "Xa", "Xb", "Xc"};
    private static final String[] TITRATION_NAMES = {"K", "C"};

    boolean fit0 = false;
    ArrayList<PeakList> peakLists;
    String name;
    String details = "";
    final PeakList firstList;
    final double[][] indVars;
    int[] peakDims = {0, 1};
    final double[] weights;
    final double[] tols;
    final double dTol;
    PATHMODE pathMode;
    String[] parNames;
    List<String> datasetNames;
    Comparator<Peak> peakComparator = Comparator.comparingInt(Peak::getIdNum);
    Map<Peak, PeakPath> paths = new TreeMap<>(peakComparator);

    static Map<String, PeakPaths> peakPaths() {
        ProjectBase project = ProjectBase.getActive();
        return project.getPeakPaths();
    }

    public static void purgePaths(PeakPaths peakPath) {
        peakPath.getPathMap().keySet().removeIf(peak -> peak.getStatus() < 0);
        for (Entry<Peak, PeakPath> entry : peakPath.getPathMap().entrySet()) {
            List<PeakDistance> newDists = new ArrayList<>();
            boolean changed = false;
            for (PeakDistance peakDist : entry.getValue().getPeakDistances()) {
                if ((peakDist != null) && (peakDist.getPeak().getStatus() <= 0)) {
                    peakDist = null;
                    changed = true;
                }
                newDists.add(peakDist);
            }
            if (changed) {
                entry.setValue(new PeakPath(peakPath, newDists));
            }
        }
        for (Peak peak : peakPath.getFirstList().peaks()) {
            if (!peak.isDeleted() && !peakPath.getPathMap().containsKey(peak)) {
                peakPath.initPath(peak);
            }
        }
    }

    public enum PATHMODE {
        TITRATION("Concentration", "Shift Delta", TITRATION_NAMES),
        PRESSURE("Pressure", "Shift Delta", PRESSURE_NAMES);

        private final String xAxisLabel;
        private final String yAxisLabel;
        private final String[] names;

        PATHMODE(String xAxisLabel, String yAxisLabel, String[] names) {
            this.xAxisLabel = xAxisLabel;
            this.yAxisLabel = yAxisLabel;
            this.names = names;
        }

        public String[] names() {
            return names;
        }

        public String xAxisLabel() {
            return xAxisLabel;
        }

        public String yAxisLabel() {
            return yAxisLabel;
        }
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        Object source = peakEvent.getSource();
        if (source instanceof PeakList) {
            purgePaths(this);
        }
    }

    public PeakPaths(String name, final List<PeakList> peakLists, double[] concentrations, final double[] binderConcs, final double[] weights, PATHMODE pathMode) {
        this(name, peakLists, concentrations, binderConcs, weights, null, pathMode);
    }

    public PeakPaths(String name, final List<PeakList> peakLists, double[] concentrations,
                     final double[] binderConcs, final double[] weights, double[] tols, PATHMODE pathMode) {
        this.name = name;
        this.pathMode = pathMode;
        this.peakLists = new ArrayList<>();
        this.datasetNames = new ArrayList<>();
        for (PeakList peakList : peakLists) {
            peakList.registerPeakChangeListener(this);
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
                i++;
            }
        }
        double tolSum = 0.0;
        for (int i = 0; i < peakDims.length; i++) {
            tolSum += tols[i] * tols[i];
        }
        dTol = Math.sqrt(tolSum);

        this.tols = tols;
        parNames = pathMode.names();

        this.indVars = new double[2][];
        this.indVars[0] = concentrations;
        this.indVars[1] = binderConcs;
        this.weights = weights;
    }

    public static PeakPaths loadPathData(PATHMODE pathMode, File file) throws IOException, IllegalArgumentException {
        PeakPaths peakPath = null;
        if (file != null) {
            List<Double> x0List = new ArrayList<>();
            List<Double> x1List = new ArrayList<>();
            List<String> datasetNames = new ArrayList<>();
            String sepChar = " +";
            List<String> lines = Files.readAllLines(file.toPath());
            if (!lines.isEmpty()) {
                if (lines.get(0).contains("\t")) {
                    sepChar = "\t";
                }
                for (String line : lines) {
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
            String peakPathName = file.getName();
            peakPath = loadPathData(pathMode, datasetNames, x0List, x1List, peakPathName);
        }
        return peakPath;
    }

    record X2WithData(Double x0, Double x1, String dataName) {
    }

    public static PeakPaths loadPathData(PATHMODE pathMode, List<String> datasetNames,
                                         List<Double> x0List, List<Double> x1List, String peakPathName) {
        double[] x0 = new double[x0List.size()];
        double[] x1 = new double[x0List.size()];
        List<X2WithData> x2WithDataList = new ArrayList<>();
        for (int i = 0; i < datasetNames.size(); i++) {
            var x2d = new X2WithData(x0List.get(i), x1List.isEmpty() ? null : x1List.get(i), datasetNames.get(i));
            x2WithDataList.add(x2d);
        }
        x2WithDataList.sort(Comparator.comparing(X2WithData::x0));

        List<PeakList> peakLists = new ArrayList<>();

        for (int i = 0; i < x2WithDataList.size(); i++) {
            var x2d = x2WithDataList.get(i);
            String datasetName = x2d.dataName;
            DatasetBase dataset = DatasetBase.getDataset(datasetName);
            if (dataset == null) {
                throw new IllegalArgumentException("\"Dataset \"" + datasetName + "\" doesn't exist\"");
            }
            PeakList peakList = PeakList.getPeakListForDataset(datasetName);
            if (peakList == null) {
                String peakListName = PeakList.getNameForDataset(datasetName);
                peakList = PeakList.get(peakListName);
            }
            if (peakList == null) {
                throw new IllegalArgumentException("\"PeakList for dataset \"" + datasetName + "\" doesn't exist\"");
            }
            peakLists.add(peakList);
            x0[i] = x2d.x0;
            if (x2d.x1 != null) {
                x1[i] = x2d.x1;
            } else {
                x1[i] = 100.0;
            }
        }
        double[] weights = {1.0, 1.0};
        if (peakLists.get(0).getSpectralDim(1).getNucleus().contains("N")) {
            weights[1] = 5.0;
        }

        if (peakPathName.contains(".")) {
            peakPathName = peakPathName.substring(0, peakPathName.indexOf("."));
        }
        PeakPaths peakPath = new PeakPaths(peakPathName, peakLists, x0, x1, weights, pathMode);
        peakPath.store();
        peakPath.initPaths();
        peakPath.datasetNames = datasetNames;

        return peakPath;
    }

    public String getUnits() {
        return pathMode == PATHMODE.PRESSURE ? "bar" : "scaled_ppm";
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

    public static Collection<PeakPaths> get() {
        return peakPaths().values();
    }

    public static Collection<String> getNames() {
        return peakPaths().keySet();
    }

    public static PeakPaths get(String name) {
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
        List<String> baseParNames = new ArrayList<>();
        if (pathMode == PATHMODE.PRESSURE) {
            baseParNames.add("A");
            baseParNames.add("B");
            baseParNames.add("C");
        } else {
            if (fit0) {
                baseParNames.add("A");
            }
            baseParNames.add("K");
            baseParNames.add("C");
        }
        return baseParNames;
    }

    public List<String> getSTAR3ParLoopStrings() {
        List<String> strings = new ArrayList<>();
        strings.add("_Par.ID");
        strings.add("_Par.Path_ID");
        strings.add("_Par.Dim");
        strings.add("_Par.Confirmed");
        strings.add("_Par.Active");
        List<String> baseParNames = getBaseParNames();
        for (String parName : baseParNames) {
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
        return (i + 1) +
                " " + String.format("%.3f", weights[i]) +
                " " + String.format("%.3f", tols[i]);

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
        PeakPath path = paths.get(startPeak);
        if (path != null) {
            for (PeakDistance peakDist : path.peakDists) {
                if ((peakDist != null) && (peakDist.peak.getStatus() > 0)) {
                    peakDist.peak.setStatus(0);
                }
            }
        }
    }

    public PeakPath addPath(List<Peak> peaks) {
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
        PeakPath path = new PeakPath(this, peakDists);
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
        PeakPath path = new PeakPath(this, peakDists);
        paths.put(path.getFirstPeak(), path);

    }

    public void initPaths() {
        for (Peak peak : firstList.peaks()) {
            if (peak.getStatus() >= 0) {
                initPath(peak);
            }
        }
    }

    public PeakPath getPath(Peak peak) {
        return paths.get(peak);
    }

    public double calcDistance(Peak peak1, Peak peak2) {
        double sum = 0.0;
        for (int i : peakDims) {
            double ppm1 = peak1.getPeakDim(i).getChemShift();
            double ppm2 = peak2.getPeakDim(i).getChemShift();
            sum += (ppm1 - ppm2) * (ppm1 - ppm2) / (weights[i] * weights[i]);
        }
        return Math.sqrt(sum);
    }

    public double[] calcDeltas(Peak peak1, Peak peak2) {
        double[] deltas = new double[weights.length];
        for (int i : peakDims) {
            double ppm1 = peak1.getPeakDim(i).getChemShift();
            double ppm2 = peak2.getPeakDim(i).getChemShift();
            deltas[i] = (ppm2 - ppm1) / weights[i];
        }
        return deltas;
    }

    public double calcDelta(Peak peak1, Peak peak2, int iDim) {
        double ppm1 = peak1.getPeakDim(iDim).getChemShift();
        double ppm2 = peak2.getPeakDim(iDim).getChemShift();
        return (ppm2 - ppm1) / weights[iDim];
    }

    public List<List<PeakDistance>> getNearPeaks(final Peak startPeak, final double radius) {
        int iList = -1;
        List<List<PeakDistance>> filteredLists = new ArrayList<>();
        for (PeakList peakList : peakLists) {
            List<PeakDistance> peakArray = new ArrayList<>();
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

    static void filterLists(PeakPaths peakPath) {
        for (PeakList peakList : peakPath.peakLists) {
            int nPeaks = peakList.size();
            for (int j = 0; j < nPeaks; j++) {
                Peak peak = peakList.getPeak(j);
                if (peak.getStatus() >= 0) {
                    peak.setStatus(0);
                }
            }
        }
        int nPeaks = peakPath.firstList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak1 = peakPath.firstList.getPeak(i);
            if (peak1.getStatus() < 0) {
                continue;
            }
            double sum = 0.0;
            for (int iDim : peakPath.peakDims) {
                double boundary = peak1.getPeakDim(iDim).getBoundsValue();
                sum += boundary * boundary / (peakPath.weights[iDim] * peakPath.weights[iDim]);
            }
            double tol = Math.sqrt(sum / peakPath.peakDims.length);
            int iList = -1;
            boolean ok = true;
            ArrayList<Peak> minPeaks = new ArrayList<>();
            for (PeakList peakList : peakPath.peakLists) {
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
                    double distance = peakPath.calcDistance(peak1, peak2);
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

    public void refreshPaths() {
        for (Peak peak : paths.keySet()) {
            refreshPath(peak);
        }
    }

    public void refreshPath(Peak startPeak) {
        PeakPath path = getPath(startPeak);
        if (path != null) {
            path.refresh();
        }
    }

    public void addPeak(Peak startPeak, Peak selPeak) {
        PeakPath path = getPath(startPeak);
        removePeak(selPeak);
        if ((selPeak.getStatus() == 0) && (path != null)) {
            double distance = calcDistance(startPeak, selPeak);
            double[] deltas = calcDeltas(startPeak, selPeak);
            PeakDistance peakDist = new PeakDistance(selPeak, distance, deltas);
            int index = peakLists.indexOf(selPeak.getPeakList());
            path.peakDists.set(index, peakDist);
            startPeak.setStatus(1);
            selPeak.setStatus(1);
        }

    }

    public Peak findPathPeak(Peak peak) {
        for (Entry<Peak, PeakPath> eSet : paths.entrySet()) {
            for (PeakDistance peakDist : eSet.getValue().peakDists) {
                if ((peakDist != null) && (peakDist.peak == peak)) {
                    return eSet.getValue().firstPeak;
                }
            }

        }
        return null;
    }

    public void removePeak(Peak selPeak) {
        Peak pathPeak = findPathPeak(selPeak);
        if (pathPeak != null) {
            PeakPath path = getPath(pathPeak);
            selPeak.setStatus(0);
            if (path != null) {
                int index = peakLists.indexOf(selPeak.getPeakList());
                path.peakDists.set(index, null);
            }
        }
    }

    public Collection<PeakPath> getPaths() {
        return paths.values();
    }

    public Map<Peak, PeakPath> getPathMap() {
        return paths;
    }

    public PeakList getFirstList() {
        return firstList;
    }

    public double[][] getXValues() {
        return indVars;
    }

    public double getDTol() {
        return dTol;
    }

    public double[] getTols() {
        return tols;
    }

    public double[] getWeights() {
        return weights;
    }

    public int[] getPeakDims() {
        return peakDims;
    }

}
