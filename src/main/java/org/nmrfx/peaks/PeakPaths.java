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
import org.nmrfx.project.ProjectBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
//import smile.interpolation.KrigingInterpolation;

public class PeakPaths implements PeakListener {

    static String[] PRESURE_NAMES = {"Ha", "Hb", "Hc", "Xa", "Xb", "Xc"};
    static String[] TITRATION_NAMES = {"K", "C"};

    static Map<String, PeakPaths> peakPaths() {
        ProjectBase project = ProjectBase.getActive();
        return project.peakPaths;
    }

    public static void purgePaths(PeakPaths peakPath) {
        Iterator<Peak> keyIter = peakPath.getPathMap().keySet().iterator();
        while (keyIter.hasNext()) {
            Peak peak = keyIter.next();
            if (peak.getStatus() < 0) {
                keyIter.remove();
            }
        }
        Iterator<Entry<Peak, PeakPath>> entryIter = peakPath.getPathMap().entrySet().iterator();
        while (entryIter.hasNext()) {
            Entry<Peak, PeakPath> entry = entryIter.next();
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

    public enum PATHMODE {
        TITRATION,
        PRESSURE;
    }
    boolean fit0 = false;
    ArrayList<PeakListBase> peakLists = new ArrayList<>();
//    ArrayList<ArrayList<PeakDistance>> filteredLists = new ArrayList<>();
    String name = "path1";
    String details = "";
    Map<Peak, PeakPath> paths = new HashMap<>();
    final PeakListBase firstList;
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
        if (source instanceof PeakListBase) {
            PeakListBase peakList = (PeakListBase) source;
            purgePaths(this);
        }
    }

    public PeakPaths(String name, final List<PeakListBase> peakLists, double[] concentrations, final double[] binderConcs, final double[] weights, PATHMODE pathMode) {
        this(name, peakLists, concentrations, binderConcs, weights, null, pathMode);
    }

    public PeakPaths(String name, final List<PeakListBase> peakLists, double[] concentrations,
                     final double[] binderConcs, final double[] weights, double[] tols, PATHMODE pathMode) {
        this.name = name;
        this.pathMode = pathMode;
        this.peakLists = new ArrayList<>();
        this.datasetNames = new ArrayList<>();
        for (PeakListBase peakList : peakLists) {
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

    public static PeakPaths loadPathData(PATHMODE pathMode, File file) throws IOException, IllegalArgumentException {
        List<String> datasetNames = new ArrayList<>();
        List<PeakListBase> peakLists = new ArrayList<>();
        PeakPaths peakPath = null;
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
                DatasetBase dataset = DatasetBase.getDataset(datasetName);
                if (dataset == null) {
                    throw new IllegalArgumentException("\"Dataset \"" + datasetName + "\" doesn't exist\"");
                }
                String peakListName = "";
                PeakListBase peakList = PeakListBase.getPeakListForDataset(datasetName);
                if (peakList == null) {
                    peakListName = PeakListBase.getNameForDataset(datasetName);
                    peakList = PeakListBase.get(peakListName);
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
            peakPath = new PeakPaths(peakPathName, peakLists, x0, x1, weights, pathMode);
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

    public List<PeakListBase> getPeakLists() {
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
        PeakListBase peakList = peakLists.get(i);
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
        for (PeakListBase peakList : peakLists) {
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
                if (peakDist != null) {
                    if (peakDist.peak.getStatus() > 0) {
                        peakDist.peak.setStatus(0);
                    }
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

    public ArrayList<ArrayList<PeakDistance>> getNearPeaks(final Peak startPeak, final double radius) {
        int iList = -1;
        ArrayList<ArrayList<PeakDistance>> filteredLists = new ArrayList<>();
        for (PeakListBase peakList : peakLists) {
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

    static void filterLists(PeakPaths peakPath) {
        for (PeakListBase peakList : peakPath.peakLists) {
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
            for (PeakListBase peakList : peakPath.peakLists) {
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

    public void dumpPaths() {
        paths.values().stream().sorted().forEach(path -> {
            System.out.println(path.toString());
        });
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
        for (Entry<Peak, PeakPath> eSet : paths.entrySet()) {
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
        PeakPath path = getPath(pathPeak);
        selPeak.setStatus(0);
        System.out.println("remove " + selPeak.getName() + " " + selPeak.getStatus());
        if ((pathPeak != null) && (path != null)) {
            int index = peakLists.indexOf(selPeak.getPeakList());
            path.peakDists.set(index, null);
            //path.confirm();
            System.out.println(path.toString());
        }
    }

    public Collection<PeakPath> getPaths() {
        return paths.values();
    }

    public Map<Peak, PeakPath> getPathMap() {
        return paths;
    }

    public PeakListBase getFirstList() {
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
