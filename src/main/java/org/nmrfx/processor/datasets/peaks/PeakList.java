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

import org.nmrfx.processor.cluster.Clusters;
import org.nmrfx.processor.cluster.Datum;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.optimization.*;
import org.nmrfx.processor.utilities.Util;
import java.io.*;
import static java.lang.Double.compare;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optimization.SimplePointChecker;
import org.apache.commons.math3.optimization.univariate.BrentOptimizer;
import org.apache.commons.math3.optimization.univariate.UnivariatePointValuePair;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import static java.util.Comparator.comparing;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.processor.datasets.Nuclei;
import org.nmrfx.processor.datasets.RegionData;
import static org.nmrfx.processor.datasets.peaks.Peak.getMeasureFunction;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.CompleteLinkage;

/**
 *
 * @author brucejohnson
 */
public class PeakList {

    public enum ARRAYED_FIT_MODE {
        SINGLE,
        PLANES,
        EXP;
    }
    /**
     *
     */
    public static final int FIT_ALL = 0;

    /**
     *
     */
    public static final int FIT_AMPLITUDES = 2;

    /**
     *
     */
    public static final int FIT_LW_AMPLITUDES = 1;

    /**
     *
     */
    public static final int FIT_MAX_DEV = 3;

    /**
     *
     */
    public static final int FIT_RMS = 4;

    /**
     *
     */
    public static ObservableMap<String, PeakList> peakListTable = FXCollections.observableMap(new LinkedHashMap<>());

    /**
     *
     */
    public static PeakList clusterOrigin = null;
    private String listName;

    /**
     *
     */
    public String fileName;
    private final int listNum;

    /**
     *
     */
    public int nDim;

    /**
     *
     */
    public boolean inMem;
    private SpectralDim[] spectralDims = null;
    List<SearchDim> searchDims = new ArrayList<>();

    /**
     *
     */
    public double scale;
    private List<Peak> peaks;

    /**
     *
     */
    public int idLast;
    private final Map<Integer, Peak> indexMap = new HashMap<>();
    private String details = "";
    private String sampleLabel = "";
    private String sampleConditionLabel = "";
    static List<PeakListener> globalListeners = new ArrayList<>();
    List<PeakListener> listeners = new ArrayList<>();
    static List<FreezeListener> freezeListeners = new ArrayList<>();
    private boolean thisListUpdated = false;
    boolean changed = false;
    static boolean aListUpdated = false;
    static boolean needToFireEvent = false;
    ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);
    ScheduledFuture futureUpdate = null;
    boolean slideable = false;
    Optional<Measures> measures = Optional.empty();
    Map<String, String> properties = new HashMap<>();
    boolean requireSliderCondition = false;
    static boolean globalRequireSliderCondition = false;

    class UpdateTask implements Runnable {

        @Override
        public void run() {
            if (aListUpdated) {
                needToFireEvent = true;
                setAUpdatedFlag(false);
                startTimer();
            } else if (needToFireEvent) {
                needToFireEvent = false;
                scanListsForUpdates();
                if (aListUpdated) {
                    startTimer();
                }
            }
        }
    }

    class SearchDim {

        final int iDim;
        final double tol;

        SearchDim(int iDim, double tol) {
            this.iDim = iDim;
            this.tol = tol;
        }
    }

    synchronized void startTimer() {
        if (valid() && (schedExecutor != null)) {
            if (needToFireEvent || (futureUpdate == null) || futureUpdate.isDone()) {
                UpdateTask updateTask = new UpdateTask();
                futureUpdate = schedExecutor.schedule(updateTask, 50, TimeUnit.MILLISECONDS);
            }
        }

    }

    /**
     *
     * @param name
     * @param n
     */
    public PeakList(String name, int n) {
        listName = name;
        fileName = "";
        nDim = n;
        spectralDims = new SpectralDim[nDim];
        scale = 1.0;
        idLast = -1;

        int i;

        for (i = 0; i < nDim; i++) {
            spectralDims[i] = new SpectralDim(this, i);
        }

        peaks = new ArrayList<>();
        indexMap.clear();

        peakListTable.put(listName, this);
        listNum = peakListTable.size();
    }

    /**
     * Copies an existing peak list.
     *
     * @param name a string with the name of an existing peak list.
     * @param allLinks a boolean specifying whether or not to link peak
     * dimensions.
     * @param merge a boolean specifying whether or not to merge peak labels.
     * @return a list that is a copy of the peak list with the input name.
     * @throws IllegalArgumentException if a peak with the input name doesn't
     * exist.
     */
    public PeakList copy(final String name, final boolean allLinks, boolean merge) {
        PeakList newPeakList;
        if (merge) {
            newPeakList = get(name);
            if (newPeakList == null) {
                throw new IllegalArgumentException("Peak list " + name + " doesn't exist");
            }
        } else {
            newPeakList = new PeakList(name, nDim);
            newPeakList.searchDims.addAll(searchDims);
            newPeakList.fileName = fileName;
            newPeakList.scale = scale;
            newPeakList.details = details;
            newPeakList.sampleLabel = sampleLabel;
            newPeakList.sampleConditionLabel = sampleConditionLabel;

            for (int i = 0; i < nDim; i++) {
                newPeakList.spectralDims[i] = spectralDims[i].copy(newPeakList);
            }
        }
        for (int i = 0; i < peaks.size(); i++) {
            Peak peak = peaks.get(i);
            Peak newPeak = peak.copy(newPeakList);
            if (!merge) {
                newPeak.setIdNum(peak.getIdNum());
            }
            newPeakList.addPeak(newPeak);
            if (merge) {
                peak.copyLabels(newPeak);
            }
            if (!merge && allLinks) {
                for (int j = 0; j < peak.peakDims.length; j++) {
                    PeakDim peakDim1 = peak.peakDims[j];
                    PeakDim peakDim2 = newPeak.peakDims[j];
                    PeakList.linkPeakDims(peakDim1, peakDim2);
                }
            }
        }
        newPeakList.idLast = idLast;
        newPeakList.reIndex();
        if (!merge && !allLinks) {
            for (int i = 0; i < peaks.size(); i++) {
                Peak oldPeak = peaks.get(i);
                Peak newPeak = newPeakList.getPeak(i);
                for (int j = 0; j < oldPeak.peakDims.length; j++) {
                    List<PeakDim> linkedPeakDims = getLinkedPeakDims(oldPeak, j);
                    PeakDim newPeakDim = newPeak.peakDims[j];
                    for (PeakDim peakDim : linkedPeakDims) {
                        Peak linkPeak = peakDim.getPeak();
                        if ((linkPeak != oldPeak) && (this == linkPeak.getPeakList())) {
                            int iPeakDim = peakDim.getSpectralDim();
                            int linkNum = linkPeak.getIdNum();
                            Peak targetPeak = newPeakList.getPeak(linkNum);
                            PeakDim targetDim = targetPeak.getPeakDim(iPeakDim);
                            PeakList.linkPeakDims(newPeakDim, targetDim);
                        }
                    }
                }
            }
        }
        return newPeakList;
    }

    /**
     *
     * @return a peak list object.
     */
    public List<Peak> peaks() {
        return peaks;
    }

    /**
     *
     * @return
     */
    public static Iterator iterator() {
        return peakListTable.values().iterator();
    }

    /**
     *
     * @return the ID number of the peak list.
     */
    public int getId() {
        return listNum;
    }

    /**
     *
     * @return the name of the peak list.
     */
    public String getName() {
        return listName;
    }

    /**
     * Rename the peak list.
     *
     * @param newName
     */
    public void setName(String newName) {
        peakListTable.remove(listName);
        listName = newName;
        peakListTable.put(newName, this);
    }

    /**
     *
     * @return the number of dimensions of the peak list.
     */
    public int getNDim() {
        return nDim;
    }

    /**
     *
     * @return
     */
    public double getScale() {
        return scale;
    }

    /**
     *
     * @param sampleLabel
     */
    public void setSampleLabel(String sampleLabel) {
        this.sampleLabel = sampleLabel;
    }

    /**
     *
     * @return
     */
    public String getSampleLabel() {
        return sampleLabel;
    }

    /**
     *
     * @param sampleConditionLabel
     */
    public void setSampleConditionLabel(String sampleConditionLabel) {
        this.sampleConditionLabel = sampleConditionLabel;
    }

    /**
     *
     * @return
     */
    public String getSampleConditionLabel() {
        return sampleConditionLabel;
    }

    /**
     *
     * @param datasetName
     */
    public void setDatasetName(String datasetName) {
        this.fileName = datasetName;
    }

    /**
     *
     * @return
     */
    public String getDatasetName() {
        return fileName;
    }

    /**
     *
     * @param details
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     *
     * @return
     */
    public String getDetails() {
        return details;
    }
    
    
    public boolean isSimulated() {
        return sampleConditionLabel.contains("sim");
    }

    static void scanListsForUpdates() {
        boolean anyUpdated = false;
        Iterator iter = iterator();
        while (iter.hasNext()) {
            PeakList peakList = (PeakList) iter.next();
            if ((peakList != null) && (peakList.thisListUpdated)) {
                peakList.setUpdatedFlag(false);
                // fixme should only do if necessary
                //peakList.sortMultiplets();
                peakList.notifyListeners();
                anyUpdated = true;
            }
        }
        if (anyUpdated) {
            notifyGlobalListeners();
        }
    }
    // FIXME need to make safe

    /**
     *
     * @param oldListener
     */
    public void removeListener(PeakListener oldListener) {
        listeners.remove(oldListener);
    }

    /**
     *
     * @param newListener
     */
    public void registerListener(PeakListener newListener) {
        if (!listeners.contains(newListener)) {
            listeners.add(newListener);
        }
    }

    static void registerGlobalListener(PeakListener newListener) {
        if (!globalListeners.contains(newListener)) {
            globalListeners.add(newListener);
        }
    }

    void notifyListeners() {
        for (PeakListener listener : listeners) {
            listener.peakListChanged(new PeakEvent(this));
        }
    }

    static void notifyGlobalListeners() {
        for (PeakListener listener : globalListeners) {
            listener.peakListChanged(new PeakEvent("*"));
        }
    }

    /**
     *
     * @param freezeListener
     */
    public static void registerFreezeListener(FreezeListener freezeListener) {
        if (freezeListeners.contains(freezeListener)) {
            freezeListeners.remove(freezeListener);
        }
        freezeListeners.add(freezeListener);
    }

    /**
     *
     * @param peak
     * @param state
     */
    public static void notifyFreezeListeners(Peak peak, boolean state) {
        for (FreezeListener listener : freezeListeners) {
            listener.freezeHappened(peak, state);

        }
    }

    synchronized static void setAUpdatedFlag(boolean value) {
        aListUpdated = value;
    }

    synchronized void setUpdatedFlag(boolean value) {
        thisListUpdated = value;
        if (value) {
            setAUpdatedFlag(value);
        }
    }

    void peakListUpdated(Object object) {
        setUpdatedFlag(true);
        changed = true;
        startTimer();
    }

    /**
     *
     * @return
     */
    public boolean isChanged() {
        return changed;
    }

    /**
     *
     */
    public void clearChanged() {
        changed = false;
    }

    /**
     *
     * @return
     */
    public static boolean isAnyChanged() {
        boolean anyChanged = false;
        for (PeakList checkList : peakListTable.values()) {
            if (checkList.isChanged()) {
                anyChanged = true;
                break;

            }
        }
        return anyChanged;
    }

    /**
     *
     */
    public static void clearAllChanged() {
        for (Object checkList : peakListTable.values()) {
            ((PeakList) checkList).clearChanged();
        }
    }

    /**
     *
     * @return
     */
    public boolean hasMeasures() {
        return measures.isPresent();
    }

    /**
     *
     * @param measure
     */
    public void setMeasures(Measures measure) {
        measures = Optional.of(measure);
    }

    /**
     *
     * @return
     */
    public double[] getMeasureValues() {
        double[] values = null;
        if (hasMeasures()) {
            values = measures.get().getValues();
        }
        return values;
    }

    /**
     *
     * @param name
     * @return
     */
    public String getProperty(String name) {
        String result = "";
        if (properties.containsKey(name)) {
            result = properties.get(name);
        }
        return result;
    }

    /**
     *
     * @param name
     * @return
     */
    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    /**
     *
     * @param name
     * @param value
     */
    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    /**
     *
     * @return
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     *
     * @return
     */
    public boolean hasSearchDims() {
        return !searchDims.isEmpty();
    }

    /**
     *
     */
    public void clearSearchDims() {
        searchDims.clear();
    }

    /**
     *
     * @param s
     * @throws IllegalArgumentException
     */
    public void setSearchDims(String s) throws IllegalArgumentException {
        String[] elements = s.split(" ");
        if ((elements.length % 2) != 0) {
            throw new IllegalArgumentException("Invalid search dim string: " + s);
        }
        clearSearchDims();
        for (int i = 0; i < elements.length; i += 2) {
            double tol = Double.parseDouble(elements[i + 1]);
            addSearchDim(elements[i], tol);
        }

    }

    /**
     *
     * @param dimName
     * @param tol
     */
    public void addSearchDim(String dimName, double tol) {
        int iDim = getListDim(dimName);
        addSearchDim(iDim, tol);
    }

    /**
     *
     * @param iDim
     * @param tol
     */
    public void addSearchDim(int iDim, double tol) {
        Iterator<SearchDim> iter = searchDims.iterator();
        while (iter.hasNext()) {
            SearchDim sDim = iter.next();
            if (sDim.iDim == iDim) {
                iter.remove();
            }
        }
        SearchDim sDim = new SearchDim(iDim, tol);
        searchDims.add(sDim);
    }

    /**
     *
     */
    public void clearIndex() {
        indexMap.clear();
    }

    /**
     *
     * @param noiseLevel
     */
    public void setFOM(double noiseLevel) {
        for (int i = 0; i < peaks.size(); i++) {
            Peak peak = peaks.get(i);
            double devMul = Math.abs(peak.getIntensity() / noiseLevel);
            if (devMul > 20.0) {
                devMul = 20.0;
            }
            double erf;
            try {
                erf = org.apache.commons.math3.special.Erf.erf(devMul / Math.sqrt(2.0));
            } catch (MaxCountExceededException mathE) {
                erf = 1.0;
            }
            float fom = (float) (0.5 * (1.0 + erf));
            peak.setFigureOfMerit(fom);
        }
    }

    /**
     *
     */
    public void reNumber() {
        for (int i = 0; i < peaks.size(); i++) {
            Peak peak = peaks.get(i);
            peak.setIdNum(i);
        }

        reIndex();
    }

    /**
     *
     */
    public void reIndex() {
        int i = 0;
        indexMap.clear();
        for (Peak peak : peaks) {
            peak.setIndex(i++);
            indexMap.put(peak.getIdNum(), peak);
        }
        peakListUpdated(this);
    }

    /**
     *
     * @return
     */
    public int size() {
        if (peaks == null) {
            return 0;
        } else {
            return peaks.size();
        }
    }

    /**
     *
     * @return
     */
    public boolean valid() {
        return (peaks != null) && (get(listName) != null);
    }

    /**
     *
     * @return
     */
    public static List<PeakList> getLists() {
        List<PeakList> peakLists = PeakList.peakListTable.values().stream().collect(Collectors.toList());
        return peakLists;
    }

    /**
     *
     * @param listName
     * @return
     */
    public static PeakList get(String listName) {
        return ((PeakList) peakListTable.get(listName));
    }

    /**
     *
     * @param listID
     * @return
     */
    public static PeakList get(int listID) {
        Iterator iter = iterator();
        PeakList peakList = null;
        while (iter.hasNext()) {
            peakList = (PeakList) iter.next();
            if (listID == peakList.listNum) {
                break;
            }
        }
        return peakList;
    }

    /**
     *
     * @param listName
     */
    public static void remove(String listName) {
        PeakDim peakdim;
        PeakList peakList = (PeakList) peakListTable.get(listName);

        if (peakList != null) {
            for (Peak peak : peakList.peaks) {
                for (PeakDim peakDim : peak.peakDims) {
                    peakDim.remove();
                    if (peakDim.hasMultiplet()) {
                        Multiplet multiplet = peakDim.getMultiplet();
                    }
                }
                peak.markDeleted();
            }
            peakList.peaks.clear();
            peakList.peaks = null;
            peakList.schedExecutor.shutdown();
            peakList.schedExecutor = null;
        }
        peakListTable.remove(listName);
    }

    void swap(double[] limits) {
        double hold;

        if (limits[1] < limits[0]) {
            hold = limits[0];
            limits[0] = limits[1];
            limits[1] = hold;
        }
    }

    /**
     *
     * @return
     */
    public String getXPKHeader() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        //id  V I 
//label dataset sw sf
//HN N15
//t1setV-01.nv
//5257.86 2661.1
//750.258 76.032
//HN.L HN.P HN.W HN.B HN.E HN.J HN.U N15.L N15.P N15.W N15.B N15.E N15.J N15.U vol int stat comment flag0
//0 {89.HN} 9.60672 0.01900 0.05700 ++ {0.0} {} {89.N} 121.78692 0.14700 0.32500 ++ {0.0} {} 0.0 1.3563 0 {} 0

        result.append("label dataset sw sf\n");
        for (int i = 0; i < nDim; i++) {
            result.append(getSpectralDim(i).getDimName());
            if (i != (nDim - 1)) {
                result.append(sep);
            }
        }
        result.append('\n');
        result.append(getDatasetName()).append('\n');
        for (int i = 0; i < nDim; i++) {
            result.append(getSpectralDim(i).getSw());
            if (i != (nDim - 1)) {
                result.append(sep);
            }
        }
        result.append('\n');
        for (int i = 0; i < nDim; i++) {
            result.append(getSpectralDim(i).getSf());
            if (i != (nDim - 1)) {
                result.append(sep);
            }
        }
        result.append('\n');

        for (int i = 0; i < nDim; i++) {
            result.append(getSpectralDim(i).getDimName()).append(".L").append(sep);
            result.append(getSpectralDim(i).getDimName()).append(".P").append(sep);
            result.append(getSpectralDim(i).getDimName()).append(".W").append(sep);
            result.append(getSpectralDim(i).getDimName()).append(".B").append(sep);
        }
        result.append("vol").append(sep);
        result.append("int");
        result.append('\n');

        return (result.toString());
    }

    /**
     *
     * @return
     */
    public String getXPK2Header() {
        StringBuilder result = new StringBuilder();
        String sep = "\t";
        result.append("id").append(sep);

        for (int i = 0; i < nDim; i++) {
            SpectralDim specDim = getSpectralDim(i);
            String dimName = specDim.getDimName();
            result.append(dimName).append(".L").append(sep);
            result.append(dimName).append(".P").append(sep);
            result.append(dimName).append(".WH").append(sep);
            result.append(dimName).append(".BH").append(sep);
            result.append(dimName).append(".E").append(sep);
            result.append(dimName).append(".M").append(sep);
            result.append(dimName).append(".m").append(sep);
            result.append(dimName).append(".U").append(sep);
            result.append(dimName).append(".r").append(sep);
            result.append(dimName).append(".F").append(sep);
        }
        result.append("volume").append(sep);
        result.append("intensity").append(sep);
        result.append("type").append(sep);
        result.append("comment").append(sep);
        result.append("color").append(sep);
        result.append("flags").append(sep);
        result.append("status");

        return (result.toString());
    }

    /**
     *
     * @return
     */
    public String getSparkyHeader() {
        StringBuilder result = new StringBuilder();
        result.append("    Assignment");
        for (int i = 0; i < getNDim(); i++) {
            result.append("     w").append(i + 1);
        }
        result.append("   Data Height");
        return result.toString();
    }

    /**
     * Search peak list for peaks that match the specified chemical shifts.
     * Before using, a search template needs to be set up.
     *
     * @param ppms An array of chemical shifts to search
     * @return A list of matching peaks
     * @throws IllegalArgumentException thrown if ppm length not equal to search
     * template length or if peak labels don't match search template
     */
    public List<Peak> findPeaks(double[] ppms)
            throws IllegalArgumentException {
        if (ppms.length != searchDims.size()) {
            throw new IllegalArgumentException("Search dimensions (" + ppms.length
                    + ") don't match template dimensions (" + searchDims.size() + ")");
        }

        double[][] limits = new double[nDim][2];
        int[] searchDim = new int[nDim];

        for (int j = 0; j < nDim; j++) {
            searchDim[j] = -1;
        }

        boolean matched = true;

        int i = 0;
        for (SearchDim sDim : searchDims) {
            searchDim[i] = sDim.iDim;

            if (searchDim[i] == -1) {
                matched = false;

                break;
            }
            double tol = sDim.tol;
            limits[i][1] = ppms[i] - tol;
            limits[i][0] = ppms[i] + tol;
            i++;
        }

        if (!matched) {
            throw new IllegalArgumentException("Peak Label doesn't match template label");
        }

        return (locatePeaks(limits, searchDim));
    }

    /**
     *
     * @param dim
     * @param ascending
     * @throws IllegalArgumentException
     */
    public void sortPeaks(int dim, boolean ascending) throws IllegalArgumentException {
//        checkDim(dim);
        sortPeaks(peaks, dim, ascending);
        reIndex();
    }

    /**
     *
     * @param peaks
     * @param iDim
     * @param ascending
     */
    public static void sortPeaks(final List<Peak> peaks, int iDim, boolean ascending) {
        if (ascending) {
            peaks.sort((Peak a, Peak b) -> compare(a.peakDims[iDim].getChemShift(), b.peakDims[iDim].getChemShift()));
        } else {
            // fixme
            peaks.sort((Peak a, Peak b) -> compare(a.peakDims[iDim].getChemShift(), b.peakDims[iDim].getChemShift()));

        }
    }

    /**
     *
     * @param iDim
     * @return
     */
    public double getFoldAmount(int iDim) {
        double foldAmount = Math.abs(getSpectralDim(iDim).getSw() / getSpectralDim(iDim).getSf());
        return foldAmount;
    }

    double foldPPM(double ppm, double fDelta, double min, double max) {
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        if (min != max) {
            while (ppm > max) {
                ppm -= fDelta;
            }
            while (ppm < min) {
                ppm += fDelta;
            }
        }
        return ppm;
    }

    /**
     *
     * @param limits
     * @param dim
     * @return
     */
    public List<Peak> locatePeaks(double[][] limits, int[] dim) {
        return locatePeaks(limits, dim, null);
    }

    class PeakDistance {

        final Peak peak;
        final double distance;

        PeakDistance(Peak peak, double distance) {
            this.peak = peak;
            this.distance = distance;
        }

        double getDistance() {
            return distance;
        }
    }

    /**
     * Locate what peaks are contained within certain limits.
     *
     * @param limits A multidimensional array of chemical shift plot limits to
     * search.
     * @param dim An array of which peak list dim corresponds to dim in the
     * limit array.
     * @param foldLimits An optional multidimensional array of plot limits where
     * folded peaks should appear. Can be null.
     * @return A list of matching peaks
     */
    public List<Peak> locatePeaks(double[][] limits, int[] dim, double[][] foldLimits) {
        List<PeakDistance> foundPeaks = new ArrayList<>();
//        final Vector peakDistance = new Vector();

        int i;
        int j;
        Peak peak;
        int nSearchDim = limits.length;
        if (nSearchDim > nDim) {
            nSearchDim = nDim;
        }
        double[] lCtr = new double[nSearchDim];
        double[] width = new double[nSearchDim];

        for (i = 0; i < nSearchDim; i++) {
            //FIXME 10.0 makes no sense, need to use size of dataset
            //System.out.println(i+" "+limits[i][0]+" "+limits[i][1]);
            if (limits[i][0] == limits[i][1]) {
                limits[i][0] = limits[i][0] - (getSpectralDim(i).getSw() / getSpectralDim(i).getSf() / 10.0);
                limits[i][1] = limits[i][1] + (getSpectralDim(i).getSw() / getSpectralDim(i).getSf() / 10.0);
            }

            if (limits[i][0] < limits[i][1]) {
                double hold = limits[i][0];
                limits[i][0] = limits[i][1];
                limits[i][1] = hold;
            }

//            System.out.println(i + " " + limits[i][0] + " " + limits[i][1]);
//            System.out.println(i + " " + foldLimits[i][0] + " " + foldLimits[i][1]);
            lCtr[i] = (limits[i][0] + limits[i][1]) / 2.0;
            width[i] = Math.abs(limits[i][0] - limits[i][1]);
        }

        int nPeaks = size();

        for (i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);
            boolean ok = true;

            double sumDistance = 0.0;

            for (j = 0; j < nSearchDim; j++) {
                if ((dim.length <= j) || (dim[j] == -1)) {
                    continue;
                }

                double ctr = peak.peakDims[dim[j]].getChemShiftValue();
                if ((foldLimits != null) && (foldLimits[j] != null)) {
                    double fDelta = Math.abs(foldLimits[j][0] - foldLimits[j][1]);
                    ctr = foldPPM(ctr, fDelta, foldLimits[j][0], foldLimits[j][1]);
                }

                if ((ctr >= limits[j][0]) || (ctr < limits[j][1])) {
                    ok = false;

                    break;
                }

                sumDistance += (((ctr - lCtr[j]) * (ctr - lCtr[j])) / (width[j] * width[j]));
            }

            if (!ok) {
                continue;
            }

            double distance = Math.sqrt(sumDistance);
            PeakDistance peakDis = new PeakDistance(peak, distance);
            foundPeaks.add(peakDis);
        }

        foundPeaks.sort(comparing(PeakDistance::getDistance));
        List<Peak> sPeaks = new ArrayList<>();
        for (PeakDistance peakDis : foundPeaks) {
            sPeaks.add(peakDis.peak);
        }

        return (sPeaks);
    }

    /**
     *
     * @param matchStrings
     * @param useRegExp
     * @param useOrder
     * @return
     */
    public List<Peak> matchPeaks(final String[] matchStrings, final boolean useRegExp, final boolean useOrder) {
        int j;
        int k;
        int l;
        boolean ok = false;
        List<Peak> result = new ArrayList<>();
        Pattern[] patterns = new Pattern[matchStrings.length];
        String[] simplePat = new String[matchStrings.length];
        if (useRegExp) {
            for (k = 0; k < matchStrings.length; k++) {
                patterns[k] = Pattern.compile(matchStrings[k].toUpperCase().trim());
            }
        } else {
            for (k = 0; k < matchStrings.length; k++) {
                simplePat[k] = matchStrings[k].toUpperCase().trim();
            }
        }

        for (Peak peak : peaks) {
            if (peak.getStatus() < 0) {
                continue;
            }

            for (k = 0; k < matchStrings.length; k++) {
                ok = false;
                if (useOrder) {
                    if (useRegExp) {
                        Matcher matcher = patterns[k].matcher(peak.peakDims[k].getLabel().toUpperCase());
                        if (matcher.find()) {
                            ok = true;
                        }
                    } else if (Util.stringMatch(peak.peakDims[k].getLabel().toUpperCase(), simplePat[k])) {
                        ok = true;
                    } else if ((simplePat[k].length() == 0) && (peak.peakDims[k].getLabel().length() == 0)) {
                        ok = true;
                    }
                } else {
                    for (l = 0; l < nDim; l++) {
                        if (useRegExp) {
                            Matcher matcher = patterns[k].matcher(peak.peakDims[l].getLabel().toUpperCase());
                            if (matcher.find()) {
                                ok = true;
                                break;
                            }
                        } else if (Util.stringMatch(peak.peakDims[l].getLabel().toUpperCase(), simplePat[k])) {
                            ok = true;
                            break;
                        } else if ((simplePat[k].length() == 0) && (peak.peakDims[l].getLabel().length() == 0)) {
                            ok = true;
                            break;
                        }
                    }
                }
                if (!ok) {
                    break;
                }
            }

            if (ok) {
                result.add(peak);
            }
        }

        return (result);
    }

    /**
     *
     * @param dataset
     * @return
     */
    public int[] getDimsForDataset(Dataset dataset) {
        return getDimsForDataset(dataset, false);
    }

    /**
     *
     * @param dataset
     * @param looseMode
     * @return
     */
    public int[] getDimsForDataset(Dataset dataset, boolean looseMode) {
        int[] pdim = new int[nDim];
        int dataDim = dataset.getNDim();
        boolean[] used = new boolean[dataDim];
        for (int j = 0; j < nDim; j++) {
            boolean ok = false;
            for (int i = 0; i < dataDim; i++) {
                if (!used[i]) {
                    if (getSpectralDim(j).getDimName().equals(dataset.getLabel(i))) {
                        pdim[j] = i;
                        used[i] = true;
                        ok = true;
                        break;
                    }
                }
            }

            if (!ok && looseMode) {
                String pNuc = getSpectralDim(j).getNucleus();
                for (int i = 0; i < dataDim; i++) {
                    if (!used[i]) {
                        String dNuc = dataset.getNucleus(i).getNumberName();
                        if (dNuc.equals(pNuc)) {
                            pdim[j] = i;
                            used[i] = true;
                            ok = true;
                            break;
                        }
                    }
                }
            }
            if (!ok) {
                throw new IllegalArgumentException(
                        "Can't find match for peak dimension \""
                        + getSpectralDim(j).getDimName() + "\"");
            }
        }
        return pdim;
    }

    /**
     *
     * @param peakSpecifier
     * @return
     */
    public static Peak getAPeak(String peakSpecifier) {
        int dot = peakSpecifier.indexOf('.');

        if (dot == -1) {
            return null;
        }

        int lastDot = peakSpecifier.lastIndexOf('.');

        PeakList peakList = (PeakList) peakListTable.get(peakSpecifier.substring(
                0, dot));

        if (peakList == null) {
            return null;
        }

        if (peakList.indexMap.isEmpty()) {
            peakList.reIndex();
        }

        int idNum;

        if (lastDot == dot) {
            idNum = Integer.parseInt(peakSpecifier.substring(dot + 1));
        } else {
            idNum = Integer.parseInt(peakSpecifier.substring(dot + 1, lastDot));
        }

        Peak peak = peakList.indexMap.get(idNum);
        return peak;
    }

    /**
     *
     * @param peakSpecifier
     * @param iDimInt
     * @return
     * @throws IllegalArgumentException
     */
    public static Peak getAPeak(String peakSpecifier,
            Integer iDimInt) throws IllegalArgumentException {
        int dot = peakSpecifier.indexOf('.');

        if (dot == -1) {
            return null;
        }

        int lastDot = peakSpecifier.lastIndexOf('.');

        PeakList peakList = (PeakList) peakListTable.get(peakSpecifier.substring(
                0, dot));

        if (peakList == null) {
            return null;
        }

        int idNum;

        try {
            if (lastDot == dot) {
                idNum = Integer.parseInt(peakSpecifier.substring(dot + 1));
            } else {
                idNum = Integer.parseInt(peakSpecifier.substring(dot + 1,
                        lastDot));
            }
        } catch (NumberFormatException numE) {
            throw new IllegalArgumentException(
                    "error parsing peak " + peakSpecifier + ": " + numE.toString());
        }

        return peakList.getPeakByID(idNum);
    }

 
    /**
     *
     * @param peakSpecifier
     * @return
     * @throws IllegalArgumentException
     */
    public static PeakDim getPeakDimObject(String peakSpecifier)
            throws IllegalArgumentException {
        int dot = peakSpecifier.indexOf('.');

        if (dot == -1) {
            return null;
        }

        int lastDot = peakSpecifier.lastIndexOf('.');

        PeakList peakList = (PeakList) peakListTable.get(peakSpecifier.substring(
                0, dot));

        if (peakList == null) {
            return null;
        }

        int idNum;

        try {
            if (lastDot == dot) {
                idNum = Integer.parseInt(peakSpecifier.substring(dot + 1));
            } else {
                idNum = Integer.parseInt(peakSpecifier.substring(dot + 1,
                        lastDot));
            }
        } catch (NumberFormatException numE) {
            throw new IllegalArgumentException(
                    "error parsing peak " + peakSpecifier + ": " + numE.toString());
        }

        Peak peak = peakList.getPeakByID(idNum);
        if (peak == null) {
            return null;
        }
        int iDim = peakList.getPeakDim(peakSpecifier);

        return peak.peakDims[iDim];
    }

    /**
     *
     * @param idNum
     * @return
     * @throws IllegalArgumentException
     */
    public Peak getPeakByID(int idNum) throws IllegalArgumentException {
        if (indexMap.isEmpty()) {
            reIndex();
        }
        Peak peak = indexMap.get(idNum);
        return peak;
    }

    /**
     *
     * @param s
     * @return
     */
    public int getListDim(String s) {
        int iDim = -1;

        for (int i = 0; i < nDim; i++) {
            if (getSpectralDim(i).getDimName().equalsIgnoreCase(s)) {
                iDim = i;

                break;
            }
        }

        return iDim;
    }

    /**
     *
     * @param peakSpecifier
     * @return
     */
    public static int getPeakDimNum(String peakSpecifier) {
        int iDim = 0;
        int dot = peakSpecifier.indexOf('.');

        if (dot != -1) {
            int lastDot = peakSpecifier.lastIndexOf('.');

            if (dot != lastDot) {
                String dimString = peakSpecifier.substring(lastDot + 1);
                iDim = Integer.parseInt(dimString) - 1;
            }
        }

        return iDim;
    }

    /**
     *
     * @param peakSpecifier
     * @return
     * @throws IllegalArgumentException
     */
    public int getPeakDim(String peakSpecifier)
            throws IllegalArgumentException {
        int iDim = 0;
        int dot = peakSpecifier.indexOf('.');

        if (dot != -1) {
            int lastDot = peakSpecifier.lastIndexOf('.');

            if (dot != lastDot) {
                String dimString = peakSpecifier.substring(lastDot + 1);
                iDim = getListDim(dimString);

                if (iDim == -1) {
                    try {
                        iDim = Integer.parseInt(dimString) - 1;
                    } catch (NumberFormatException nFE) {
                        iDim = -1;
                    }
                }
            }
        }

        if ((iDim < 0) || (iDim >= nDim)) {
            throw new IllegalArgumentException(
                    "Invalid peak dimension in \"" + peakSpecifier + "\"");
        }

        return iDim;
    }

    static Peak getAPeak2(String peakSpecifier) {
        int dot = peakSpecifier.indexOf('.');

        if (dot == -1) {
            return null;
        }

        int lastDot = peakSpecifier.lastIndexOf('.');

        PeakList peakList = (PeakList) peakListTable.get(peakSpecifier.substring(
                0, dot));

        if (peakList == null) {
            return null;
        }

        int idNum;

        if (dot == lastDot) {
            idNum = Integer.parseInt(peakSpecifier.substring(dot + 1));
        } else {
            idNum = Integer.parseInt(peakSpecifier.substring(dot + 1, lastDot));
        }

        Peak peak = peakList.getPeak(idNum);

        return (peak);
    }

    /**
     *
     * @return
     */
    public Peak getNewPeak() {
        Peak peak = new Peak(this, nDim);
        addPeak(peak);
        return peak;
    }

    /**
     *
     * @param newPeak
     */
    public void addPeakWithoutResonance(Peak newPeak) {
        peaks.add(newPeak);
        clearIndex();
    }

    /**
     *
     * @param newPeak
     */
    public void addPeak(Peak newPeak) {
        newPeak.initPeakDimContribs();
        peaks.add(newPeak);
        clearIndex();
    }

    /**
     *
     * @return
     */
    public int addPeak() {
        Peak peak = new Peak(this, nDim);
        addPeak(peak);
        return (peak.getIdNum());
    }

    /**
     *
     * @param i
     * @return
     */
    public Peak getPeak(int i) {
        if (peaks == null) {
            return null;
        }
        if (indexMap.isEmpty()) {
            reIndex();
        }

        if ((i >= 0) && (i < peaks.size())) {
            return (peaks.get(i));
        } else {
            return null;
        }
    }

    /**
     *
     * @param peak
     */
    public void removePeak(Peak peak) {
        if (peaks.get(peaks.size() - 1) == peak) {
            idLast--;
        }
        peaks.remove(peak);
        reIndex();
    }

    /**
     *
     * @return
     */
    public int compress() {
        int nRemoved = 0;
        for (int i = (peaks.size() - 1); i >= 0; i--) {
            if ((peaks.get(i)).getStatus() < 0) {
                unLinkPeak(peaks.get(i));
                (peaks.get(i)).markDeleted();
                peaks.remove(i);
                nRemoved++;
            }
        }
        reIndex();
        return nRemoved;
    }

    public void removeDiagonalPeaks(double tol) {
        int iDim = -1;
        int jDim = -1;

        for (int i = 0; i < spectralDims.length; i++) {
            for (int j = i + 1; j < spectralDims.length; j++) {
                SpectralDim isDim = spectralDims[i];
                SpectralDim jsDim = spectralDims[j];
                double isf = isDim.getSf();
                double jsf = jsDim.getSf();
                // get fractional diff between sfs
                double delta = Math.abs(isf - jsf) / Math.min(isf, jsf);
                // if sf diff < 1% assume these are the two dimensions for diagonal
                if (delta < 0.01) {
                    iDim = i;
                    jDim = j;
                    break;
                }
            }
            if (iDim != -1) {
                break;
            }
        }
        if ((iDim != -1)) {
            removeDiagonalPeaks(iDim, jDim, tol);
        }
    }

    public void removeDiagonalPeaks(int iDim, int jDim, double tol) {
        if (tol < 0.0) {
            DescriptiveStatistics iStats = widthDStats(iDim);
            DescriptiveStatistics jStats = widthDStats(jDim);
            tol = 2.0 * Math.max(iStats.getMean(), jStats.getMean());
        }
        for (Peak peak : peaks) {
            double v1 = peak.getPeakDim(iDim).getChemShiftValue();
            double v2 = peak.getPeakDim(iDim).getChemShiftValue();
            double delta = Math.abs(v1 - v2);
            if (delta < tol) {
                peak.setStatus(-1);
            }
        }
        compress();
        reNumber();
    }

    /**
     *
     * @param minTol
     * @param maxTol
     * @param phaseRel
     * @param dimVal
     * @throws IllegalArgumentException
     */
    public void couple(double[] minTol, double[] maxTol,
            PhaseRelationship phaseRel, int dimVal) throws IllegalArgumentException {
        if (minTol.length != nDim) {
            throw new IllegalArgumentException("Number of minimum tolerances not equal to number of peak dimensions");
        }

        if (maxTol.length != nDim) {
            throw new IllegalArgumentException("Number of maximum tolerances not equal to number of peak dimensions");
        }

        class Match {

            int i = 0;
            int j = 0;
            double delta = 0.0;

            Match(int i, int j, double delta) {
                this.i = i;
                this.j = j;
                this.delta = delta;
            }

            double getDelta() {
                return delta;
            }
        }

        double biggestMax = 0.0;

        if (dimVal < 0) {
            for (int iDim = 0; iDim < nDim; iDim++) {
                if (maxTol[iDim] > biggestMax) {
                    biggestMax = maxTol[iDim];
                    dimVal = iDim;
                }
            }
        }

        final ArrayList matches = new ArrayList();
        for (int i = 0, n = peaks.size(); i < n; i++) {
            Peak iPeak = peaks.get(i);

            if (iPeak.getStatus() < 0) {
                continue;
            }

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }

                Peak jPeak = peaks.get(j);

                if (jPeak.getStatus() < 0) {
                    continue;
                }

                if (phaseRel != PhaseRelationship.ANYPHASE) {
                    PhaseRelationship phaseRelTest;

                    if (!phaseRel.isSigned()) {
                        phaseRelTest = PhaseRelationship.getType(iPeak.getIntensity(), jPeak.getIntensity());
                    } else {
                        phaseRelTest = PhaseRelationship.getType(iPeak.peakDims[dimVal].getChemShiftValue(),
                                iPeak.getIntensity(), jPeak.peakDims[dimVal].getChemShiftValue(), jPeak.getIntensity());
                    }

                    if (phaseRelTest != phaseRel) {
                        continue;
                    }
                }

                boolean ok = true;
                double deltaMatch = 0.0;

                for (int iDim = 0; iDim < nDim; iDim++) {
                    double delta = Math.abs(iPeak.peakDims[iDim].getChemShiftValue()
                            - jPeak.peakDims[iDim].getChemShiftValue());

                    if ((delta < minTol[iDim]) || (delta > maxTol[iDim])) {
                        ok = false;

                        break;
                    } else if (dimVal == iDim) {
                        deltaMatch = delta;
                    }
                }

                if (ok) {
                    Match match = new Match(i, j, deltaMatch);
                    matches.add(match);
                }
            }
        }

        matches.sort(comparing(Match::getDelta));

        boolean[] iUsed = new boolean[peaks.size()];

        for (int i = 0, n = matches.size(); i < n; i++) {
            Match match = (Match) matches.get(i);

            if (!iUsed[match.i] && !iUsed[match.j]) {
                iUsed[match.i] = true;
                iUsed[match.j] = true;

                Peak iPeak = peaks.get(match.i);
                Peak jPeak = peaks.get(match.j);
                float iIntensity = Math.abs(iPeak.getIntensity());
                float jIntensity = Math.abs(jPeak.getIntensity());
                for (int iDim = 0; iDim < nDim; iDim++) {
                    PeakDim iPDim = iPeak.peakDims[iDim];
                    PeakDim jPDim = jPeak.peakDims[iDim];

                    float iCenter = iPDim.getChemShiftValue();
                    float jCenter = jPDim.getChemShiftValue();
                    float newCenter = (iIntensity * iCenter + jIntensity * jCenter) / (iIntensity + jIntensity);
                    iPDim.setChemShiftValue(newCenter);

                    float iValue = iPDim.getLineWidthValue();
                    float jValue = jPDim.getLineWidthValue();
                    float newValue = (iIntensity * iValue + jIntensity * jValue) / (iIntensity + jIntensity);
                    iPDim.setLineWidthValue(newValue);

                    iValue = iPDim.getBoundsValue();
                    jValue = jPDim.getBoundsValue();

                    float[] edges = new float[4];
                    edges[0] = iCenter - (Math.abs(iValue / 2));
                    edges[1] = jCenter - (Math.abs(jValue / 2));
                    edges[2] = iCenter + (Math.abs(iValue / 2));
                    edges[3] = jCenter + (Math.abs(jValue / 2));

                    float maxDelta = 0.0f;

                    // FIXME need to calculate width
                    // FIXME should only do this if we don't store coupling and keep original bounds
                    for (int iEdge = 0; iEdge < 4; iEdge++) {
                        float delta = Math.abs(edges[iEdge] - newCenter);
                        if (delta > maxDelta) {
                            maxDelta = delta;
                            iPDim.setBoundsValue(delta * 2);
                        }
                    }
                }
                if (jIntensity > iIntensity) {
                    iPeak.setIntensity(jPeak.getIntensity());
                }

                jPeak.setStatus(-1);
            }
        }

        compress();
    }

    /**
     *
     * @param minTol
     * @param maxTol
     * @return
     */
    public DistanceMatch[][] getNeighborDistances(double[] minTol,
            double[] maxTol) {
        final ArrayList matches = new ArrayList();

        double[] deltas = new double[nDim];
        DistanceMatch[][] dMatches;
        dMatches = new DistanceMatch[peaks.size()][];

        for (int i = 0, n = peaks.size(); i < n; i++) {
            dMatches[i] = null;

            Peak iPeak = peaks.get(i);

            if (iPeak.getStatus() < 0) {
                continue;
            }

            matches.clear();

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }

                Peak jPeak = peaks.get(j);

                if (jPeak.getStatus() < 0) {
                    continue;
                }

                boolean ok = true;
                double sum = 0.0;

                for (int iDim = 0; iDim < nDim; iDim++) {
                    deltas[iDim] = (iPeak.peakDims[iDim].getChemShiftValue()
                            - jPeak.peakDims[iDim].getChemShiftValue()) / maxTol[iDim];

                    double absDelta = Math.abs(deltas[iDim]);

                    if ((absDelta < (minTol[iDim] / maxTol[iDim]))
                            || (absDelta > 10.0)) {
                        ok = false;

                        break;
                    } else {
                        sum += (deltas[iDim] * deltas[iDim]);
                    }
                }

                if (ok) {
                    double distance = Math.sqrt(sum);
                    DistanceMatch match = new DistanceMatch(iPeak.getIdNum(), jPeak.getIdNum(), deltas, distance);
                    matches.add(match);
                }
            }

            if (matches.size() > 1) {
                matches.sort(comparing(DistanceMatch::getDelta));
                dMatches[i] = new DistanceMatch[matches.size()];

                for (int k = 0; k < matches.size(); k++) {
                    dMatches[i][k] = (DistanceMatch) matches.get(k);
                }
            }
        }

        return dMatches;
    }

    /**
     *
     * @param peakListA
     * @param peakListB
     * @param minTol
     * @param maxTol
     * @throws IllegalArgumentException
     */
    public static void mapLinkPeaks(PeakList peakListA,
            PeakList peakListB, double[] minTol, double[] maxTol)
            throws IllegalArgumentException {
        if (minTol.length != peakListA.nDim) {
            throw new IllegalArgumentException(
                    "Number of minimum tolerances not equal to number of peak dimensions");
        }

        if (maxTol.length != peakListB.nDim) {
            throw new IllegalArgumentException(
                    "Number of maximum tolerances not equal to number of peak dimensions");
        }

        DistanceMatch[][] aNeighbors = peakListA.getNeighborDistances(minTol,
                maxTol);
        DistanceMatch[][] bNeighbors = peakListB.getNeighborDistances(minTol,
                maxTol);

        class Match {

            int i = 0;
            int j = 0;
            double delta = 0.0;

            Match(int i, int j, double delta) {
                this.i = i;
                this.j = j;
                this.delta = delta;
            }
        }

        final ArrayList matches = new ArrayList();

        for (int i = 0; i < aNeighbors.length; i++) {
            if (aNeighbors[i] != null) {
                Peak peakA = peakListA.peaks.get(i);

                for (int j = 0; j < bNeighbors.length; j++) {
                    Peak peakB = peakListB.peaks.get(j);
                    double distance = peakA.distance(peakB, maxTol);

                    if (distance > 10.0) {
                        continue;
                    }

                    if (bNeighbors[j] != null) {
                        double score = aNeighbors[i][0].compare(aNeighbors, i,
                                bNeighbors, j);

                        if (score != Double.MAX_VALUE) {
                            Match match = new Match(i, j, score);
                            matches.add(match);
                        }
                    }
                }
            }
        }

        matches.sort(comparing(DistanceMatch::getDelta));

        int m = (aNeighbors.length > bNeighbors.length) ? aNeighbors.length
                : bNeighbors.length;
        boolean[] iUsed = new boolean[m];

        for (int i = 0, n = matches.size(); i < n; i++) {
            Match match = (Match) matches.get(i);

            if (!iUsed[match.i] && !iUsed[match.j]) {
                iUsed[match.i] = true;
                iUsed[match.j] = true;

                // fixme don't seem to be used
                //Peak iPeak = (Peak) peakListA.peaks.elementAt(aNeighbors[match.i][0].iPeak);
                // fixme   Peak jPeak = (Peak) peakListB.peaks.elementAt(bNeighbors[match.j][0].iPeak);
            }
        }
    }

    /**
     *
     */
    public static class MatchItem {

        final int itemIndex;
        final double[] values;

        MatchItem(final int itemIndex, final double[] values) {
            this.itemIndex = itemIndex;
            this.values = values;
        }
    }

    /**
     *
     * @param dims
     * @param tol
     * @param positions
     * @param names
     */
    public void assignAtomLabels(int[] dims, double[] tol, double[][] positions, String[][] names) {
        List<MatchItem> peakItems = getMatchingItems(dims);
        List<MatchItem> atomItems = getMatchingItems(positions);
        if (tol == null) {
            tol = new double[dims.length];
            for (int iDim = 0; iDim < dims.length; iDim++) {
                tol[iDim] = widthStatsPPM(iDim).getAverage() * 2.0;
            }
        }
        double[] iOffsets = new double[dims.length];
        double[] jOffsets = new double[dims.length];
        MatchResult result = doBPMatch(peakItems, iOffsets, atomItems, jOffsets, tol);
        int[] matching = result.matching;
        for (int i = 0; i < peakItems.size(); i++) {
            MatchItem item = peakItems.get(i);
            if (item.itemIndex < peaks.size()) {
                if (matching[i] != -1) {
                    int j = matching[i];
                    if ((j < names.length) && (item.itemIndex < peakItems.size())) {
                        Peak peak = peaks.get(item.itemIndex);
                        for (int iDim = 0; iDim < dims.length; iDim++) {
                            peak.peakDims[dims[iDim]].setLabel(names[j][iDim]);
                        }
                    }
                }

            }
        }
    }

    List<MatchItem> getMatchingItems(int[] dims) {
        List<MatchItem> matchList = new ArrayList<>();
        List<Peak> searchPeaks = peaks;

        Set<Peak> usedPeaks = searchPeaks.stream().filter(p -> p.getStatus() < 0).collect(Collectors.toSet());

        int j = -1;
        for (Peak peak : searchPeaks) {
            j++;
            if (usedPeaks.contains(peak)) {
                continue;
            }
            double[] values = new double[dims.length];
            for (int iDim = 0; iDim < dims.length; iDim++) {
                List<PeakDim> linkedPeakDims = getLinkedPeakDims(peak, dims[iDim]);
                double ppmCenter = 0.0;
                for (PeakDim peakDim : linkedPeakDims) {
                    Peak peak2 = peakDim.getPeak();
                    usedPeaks.add(peak2);
                    ppmCenter += peakDim.getChemShiftValue();
                }
                values[iDim] = ppmCenter / linkedPeakDims.size();
            }
            MatchItem matchItem = new MatchItem(j, values);
            matchList.add(matchItem);
        }
        return matchList;
    }

    List<MatchItem> getMatchingItems(double[][] positions) {
        List<MatchItem> matchList = new ArrayList<>();
        for (int j = 0; j < positions.length; j++) {
            MatchItem matchItem = new MatchItem(j, positions[j]);
            matchList.add(matchItem);
        }
        return matchList;
    }

    class MatchResult {

        final double score;
        final int nMatches;
        final int[] matching;

        MatchResult(final int[] matching, final int nMatches, final double score) {
            this.matching = matching;
            this.score = score;
            this.nMatches = nMatches;
        }
    }

    private MatchResult doBPMatch(List<MatchItem> iMList, final double[] iOffsets, List<MatchItem> jMList, final double[] jOffsets, double[] tol) {
        int iNPeaks = iMList.size();
        int jNPeaks = jMList.size();
        int nPeaks = iNPeaks + jNPeaks;
        BipartiteMatcher bpMatch = new BipartiteMatcher();
        bpMatch.reset(nPeaks, true);
        // fixme should we add reciprocol match
        for (int iPeak = 0; iPeak < iNPeaks; iPeak++) {
            bpMatch.setWeight(iPeak, jNPeaks + iPeak, -1.0);
        }
        for (int jPeak = 0; jPeak < jNPeaks; jPeak++) {
            bpMatch.setWeight(iNPeaks + jPeak, jPeak, -1.0);
        }
        double minDelta = 10.0;
        int nMatches = 0;
        for (int iPeak = 0; iPeak < iNPeaks; iPeak++) {
            double minDeltaSq = Double.MAX_VALUE;
            int minJ = -1;
            for (int jPeak = 0; jPeak < jNPeaks; jPeak++) {
                double weight = Double.NEGATIVE_INFINITY;
                MatchItem matchI = iMList.get(iPeak);
                MatchItem matchJ = jMList.get(jPeak);
                double deltaSqSum = getMatchingDistanceSq(matchI, iOffsets, matchJ, jOffsets, tol);
                if (deltaSqSum < minDeltaSq) {
                    minDeltaSq = deltaSqSum;
                    minJ = jPeak;
                }
                if (deltaSqSum < minDelta) {
                    weight = Math.exp(-deltaSqSum);
                }
                if (weight != Double.NEGATIVE_INFINITY) {
                    bpMatch.setWeight(iPeak, jPeak, weight);
                    nMatches++;
                }
            }
        }
        int[] matching = bpMatch.getMatching();
        double score = 0.0;
        nMatches = 0;
        for (int i = 0; i < iNPeaks; i++) {
            MatchItem matchI = iMList.get(i);
            if ((matching[i] >= 0) && (matching[i] < jMList.size())) {
                MatchItem matchJ = jMList.get(matching[i]);
                double deltaSqSum = getMatchingDistanceSq(matchI, iOffsets, matchJ, jOffsets, tol);
                if (deltaSqSum < minDelta) {
                    score += 1.0 - Math.exp(-deltaSqSum);
                } else {
                    score += 1.0;
                }
                nMatches++;
            } else {
                score += 1.0;
            }

        }
        MatchResult matchResult = new MatchResult(matching, nMatches, score);
        return matchResult;
    }

    class UnivariateRealPointValuePairChecker implements ConvergenceChecker {

        ConvergenceChecker<PointValuePair> cCheck = new SimplePointChecker<>();

        @Override
        public boolean converged(final int iteration, final Object previous, final Object current) {
            UnivariatePointValuePair pPair = (UnivariatePointValuePair) previous;
            UnivariatePointValuePair cPair = (UnivariatePointValuePair) current;

            double[] pPoint = new double[1];
            double[] cPoint = new double[1];
            pPoint[0] = pPair.getPoint();
            cPoint[0] = cPair.getPoint();
            PointValuePair rpPair = new PointValuePair(pPoint, pPair.getValue());
            PointValuePair rcPair = new PointValuePair(cPoint, cPair.getValue());
            boolean converged = cCheck.converged(iteration, rpPair, rcPair);
            return converged;
        }
    }

    private void optimizeMatch(final ArrayList<MatchItem> iMList, final double[] iOffsets, final ArrayList<MatchItem> jMList, final double[] jOffsets, final double[] tol, int minDim, double min, double max) {
        class MatchFunction implements UnivariateFunction {

            int minDim = 0;

            MatchFunction(int minDim) {
                this.minDim = minDim;
            }

            @Override
            public double value(double x) {
                double[] minOffsets = new double[iOffsets.length];
                System.arraycopy(iOffsets, 0, minOffsets, 0, minOffsets.length);
                minOffsets[minDim] += x;
                MatchResult matchResult = doBPMatch(iMList, minOffsets, jMList, jOffsets, tol);
                return matchResult.score;
            }
        }
        MatchFunction f = new MatchFunction(minDim);
        double tolAbs = 1E-6;
        //UnivariateRealPointValuePairChecker cCheck = new UnivariateRealPointValuePairChecker();
        //BrentOptimizer brentOptimizer = new BrentOptimizer(tolAbs*10.0,tolAbs,cCheck);
        BrentOptimizer brentOptimizer = new BrentOptimizer(tolAbs * 10.0, tolAbs);
        try {
            UnivariatePointValuePair optValue = brentOptimizer.optimize(100, f, GoalType.MINIMIZE, min, max);

            iOffsets[minDim] += optValue.getPoint();
        } catch (Exception e) {
        }
    }
// fixme removed bpmatchpeaks

    /**
     *
     * @param iPeak
     * @param dimsI
     * @param iOffsets
     * @param jPeak
     * @param dimsJ
     * @param tol
     * @param jOffsets
     * @return
     */
    public double getPeakDistanceSq(Peak iPeak, int[] dimsI, double[] iOffsets, Peak jPeak, int[] dimsJ, double[] tol, double[] jOffsets) {
        double deltaSqSum = 0.0;
        for (int k = 0; k < dimsI.length; k++) {
            double iCtr = iPeak.getPeakDim(dimsI[k]).getChemShift();
            double jCtr = jPeak.getPeakDim(dimsJ[k]).getChemShift();
            double delta = ((iCtr + iOffsets[k]) - (jCtr + jOffsets[k])) / tol[k];
            deltaSqSum += delta * delta;
        }
        return deltaSqSum;
    }

    /**
     *
     * @param iItem
     * @param iOffsets
     * @param jItem
     * @param jOffsets
     * @param tol
     * @return
     */
    public double getMatchingDistanceSq(MatchItem iItem, double[] iOffsets, MatchItem jItem, double[] jOffsets, double[] tol) {
        double deltaSqSum = 0.0;
        for (int k = 0; k < iItem.values.length; k++) {
            double iCtr = iItem.values[k];
            double jCtr = jItem.values[k];
            double delta = ((iCtr + iOffsets[k]) - (jCtr + jOffsets[k])) / tol[k];
            deltaSqSum += delta * delta;
        }
        return deltaSqSum;
    }

    /**
     *
     * @return @throws IllegalArgumentException
     */
    public int clusterPeaks() throws IllegalArgumentException {
        List<PeakList> peakLists = new ArrayList<>();
        peakLists.add(this);
        return clusterPeaks(peakLists);

    }

    /**
     *
     * @param peakListNames
     * @return
     * @throws IllegalArgumentException
     */
    public static int clusterPeaks(String[] peakListNames) throws IllegalArgumentException {
        List<PeakList> peakLists = new ArrayList<>();
        for (String peakListName : peakListNames) {
            PeakList peakList = get(peakListName);
            if (peakList == null) {
                throw new IllegalArgumentException("Couldn't find peak list " + peakListName);
            }
            peakLists.add(peakList);
        }
        return clusterPeaks(peakLists);
    }

    /**
     *
     * @param peakLists
     * @return
     * @throws IllegalArgumentException
     */
    public static int clusterPeaks(List<PeakList> peakLists)
            throws IllegalArgumentException {
        Clusters clusters = new Clusters();
        List<Peak> clustPeaks = new ArrayList<>();
        double[] tol = null;

        for (PeakList peakList : peakLists) {
            peakList.peaks.stream().filter(p -> p.getStatus() >= 0).forEach(p -> p.setStatus(0));
            int fDim = peakList.searchDims.size();
            if (fDim == 0) {
                throw new IllegalArgumentException("List doesn't have search dimensions");
            }
        }

        final int nGroups = peakLists.size();

        boolean firstList = true;
        int ii = 0;
        int fDim = 0;
        for (PeakList peakList : peakLists) {

            if (firstList) {
                fDim = peakList.searchDims.size();
                tol = new double[fDim];
            } else if (fDim != peakList.searchDims.size()) {
                throw new IllegalArgumentException("Peaklists have different search dimensions");
            }

            for (Peak peak : peakList.peaks) {
                if (peak.getStatus() != 0) {
                    continue;
                }

                List<Peak> linkedPeaks = getLinks(peak);

                if (linkedPeaks == null) {
                    continue;
                }

                clustPeaks.add(peak);
                final Datum datum = new Datum(peakList.searchDims.size());
                datum.act = true;
                datum.proto[0] = ii;
                datum.idNum = ii;

                if (firstList && (nGroups > 1)) {
                    datum.group = 0;
                }

                ii++;

                for (int k = 0; k < peakList.searchDims.size(); k++) {
                    SearchDim sDim = peakList.searchDims.get(k);
                    datum.v[k] = 0.0;

                    for (int iPeak = 0; iPeak < linkedPeaks.size(); iPeak++) {
                        Peak peak2 = linkedPeaks.get(iPeak);
                        peak2.setStatus(1);
                        datum.v[k] += peak2.peakDims[sDim.iDim].getChemShiftValue();
                    }

                    datum.v[k] /= linkedPeaks.size();

                    //datum.n = linkedPeaks.size();
                    tol[k] = sDim.tol;
                }

                clusters.data.add(datum);
            }
            firstList = false;
        }

        clusters.doCluster(fDim, tol);

        int nClusters = 0;
        for (int i = 0; i < clusters.data.size(); i++) {
            Datum iDatum = (Datum) clusters.data.get(i);

            if (iDatum.act) {
                nClusters++;
                for (int j = 0; j < clusters.data.size(); j++) {
                    if (i == j) {
                        continue;
                    }

                    Datum jDatum = (Datum) clusters.data.get(j);

                    if (iDatum.proto[0] == jDatum.proto[0]) {
                        for (int iDim = 0; iDim < fDim; iDim++) {
                            Peak iPeak = clustPeaks.get(iDatum.idNum);
                            Peak jPeak = clustPeaks.get(jDatum.idNum);
                            SearchDim iSDim = iPeak.peakList.searchDims.get(iDim);
                            SearchDim jSDim = jPeak.peakList.searchDims.get(iDim);

                            linkPeaks(iPeak, iSDim.iDim, jPeak, jSDim.iDim);
                        }
                    }
                }
            }
        }
        return nClusters;
    }

    public void clusterPeakColumns(int iDim) {
        double widthScale = 0.25;
        DescriptiveStatistics dStat = widthDStats(iDim);
        double widthPPM = dStat.getPercentile(50.0) / getSpectralDim(iDim).getSf();
        System.out.println("cluster " + widthPPM * widthScale);
        clusterPeakColumns(iDim, widthPPM * widthScale);
    }

    /**
     *
     * @param iDim
     * @param limit
     */
    public void clusterPeakColumns(int iDim, double limit) {
        compress();
        reIndex();
        int n = peaks.size();
        double[][] proximity = new double[n][n];
        for (Peak peakA : peaks) {
            // PeakList.unLinkPeak(peakA, iDim);
            double shiftA = peakA.getPeakDim(iDim).getChemShiftValue();
            for (Peak peakB : peaks) {
                double shiftB = peakB.getPeakDim(iDim).getChemShiftValue();
                double dis = Math.abs(shiftA - shiftB);
                proximity[peakA.getIndex()][peakB.getIndex()] = dis;
            }
        }
        CompleteLinkage linkage = new CompleteLinkage(proximity);
        HierarchicalClustering clusterer = new HierarchicalClustering(linkage);
        int[] partition = clusterer.partition(limit);
        int nClusters = 0;
        for (int i = 0; i < n; i++) {
            if (partition[i] > nClusters) {
                nClusters = partition[i];
            }
        }
        nClusters++;
        Peak[] roots = new Peak[nClusters];
        for (int i = 0; i < n; i++) {
            int cluster = partition[i];
            if (roots[cluster] == null) {
                roots[cluster] = peaks.get(i);
            } else {
                PeakList.linkPeaks(roots[cluster], iDim, peaks.get(i), iDim);
            }
        }
    }

    /**
     *
     * @param peak
     * @param requireSameList
     * @return
     */
    public static List<Peak> getLinks(Peak peak, boolean requireSameList) {
        List<PeakDim> peakDims = getLinkedPeakDims(peak, 0);
        ArrayList<Peak> peaks = new ArrayList(peakDims.size());
        for (PeakDim peakDim : peakDims) {
            if (!requireSameList || (peakDim.myPeak.peakList == peak.peakList)) {
                peaks.add(peakDim.myPeak);
            }
        }
        return peaks;
    }

    /**
     *
     * @param peak
     * @return
     */
    public static List getLinks(Peak peak) {
        List peakDims = getLinkedPeakDims(peak, 0);
        ArrayList peaks = new ArrayList(peakDims.size());
        for (int i = 0; i < peakDims.size(); i++) {
            PeakDim peakDim = (PeakDim) peakDims.get(i);
            peaks.add(peakDim.myPeak);
        }
        return peaks;
    }

    /**
     *
     * @param peak
     * @param iDim
     * @return
     */
    public static List<Peak> getLinks(final Peak peak, final int iDim) {
        final List<PeakDim> peakDims = getLinkedPeakDims(peak, iDim);
        final List<Peak> peaks = new ArrayList<>(peakDims.size());
        for (int i = 0; i < peakDims.size(); i++) {
            PeakDim peakDim = (PeakDim) peakDims.get(i);
            peaks.add(peakDim.myPeak);
        }
        return peaks;
    }

    /**
     *
     * @param peak
     * @return
     */
    public static List<PeakDim> getLinkedPeakDims(Peak peak) {
        return getLinkedPeakDims(peak, 0);
    }

    /**
     *
     * @param peak
     * @param iDim
     * @return
     */
    public static List<PeakDim> getLinkedPeakDims(Peak peak, int iDim) {
        PeakDim peakDim = peak.getPeakDim(iDim);
        return peakDim.getLinkedPeakDims();
    }

    /**
     *
     * @param peakA
     * @param dimA
     * @param peakB
     * @param dimB
     */
    public static void linkPeaks(Peak peakA, String dimA, Peak peakB, String dimB) {
        PeakDim peakDimA = peakA.getPeakDim(dimA);
        PeakDim peakDimB = peakB.getPeakDim(dimB);
        if ((peakDimA != null) && (peakDimB != null)) {
            linkPeakDims(peakDimA, peakDimB);
        }
    }

    /**
     *
     * @param peakA
     * @param dimA
     * @param peakB
     * @param dimB
     */
    public static void linkPeaks(Peak peakA, int dimA, Peak peakB, int dimB) {
        PeakDim peakDimA = peakA.getPeakDim(dimA);
        PeakDim peakDimB = peakB.getPeakDim(dimB);
        if ((peakDimA != null) && (peakDimB != null)) {
            linkPeakDims(peakDimA, peakDimB);
        }
    }
    // FIXME should check to see that nucleus is same

    /**
     *
     * @param peakDimA
     * @param peakDimB
     */
    public static void linkPeakDims(PeakDim peakDimA, PeakDim peakDimB) {
        Resonance resonanceA = peakDimA.getResonance();
        Resonance resonanceB = peakDimB.getResonance();

        Resonance.merge(resonanceA, resonanceB);

        peakDimA.peakDimUpdated();
        peakDimB.peakDimUpdated();
    }
    // FIXME should check to see that nucleus is same

    /**
     *
     * @param peakDimA
     * @param peakDimB
     */
    public static void couplePeakDims(PeakDim peakDimA, PeakDim peakDimB) {
        Resonance resonanceA = peakDimA.getResonance();
        Resonance resonanceB = peakDimB.getResonance();

        Resonance.merge(resonanceA, resonanceB);

        Multiplet.merge(peakDimA, peakDimB);
        peakDimA.peakDimUpdated();
        peakDimB.peakDimUpdated();
    }

    /**
     *
     */
    public void unLinkPeaks() {
        int nPeaks = peaks.size();

        for (int i = 0; i < nPeaks; i++) {
            unLinkPeak(peaks.get(i));
        }
    }

    /**
     *
     * @param peak
     */
    public static void unLinkPeak(Peak peak) {
        for (int i = 0; i < peak.peakList.nDim; i++) {
            List<PeakDim> peakDims = getLinkedPeakDims(peak, i);
            unLinkPeak(peak, i);

            for (PeakDim pDim : peakDims) {
                if (pDim.getPeak() != peak) {
                    if (pDim.isCoupled()) {
                        if (peakDims.size() == 2) {
                            pDim.getMultiplet().setSinglet();
                        } else {
                            pDim.getMultiplet().setGenericMultiplet();
                        }
                    }
                }
            }
        }
    }

    /**
     *
     * @return
     */
    public boolean isSlideable() {
        return slideable;
    }

    /**
     *
     * @param state
     */
    public void setSlideable(boolean state) {
        slideable = state;
    }

    /**
     *
     * @param peak
     * @param iDim
     */
    public static void unLinkPeak(Peak peak, int iDim) {
        PeakDim peakDim = peak.getPeakDim(iDim);
        if (peakDim != null) {
            peakDim.unLink();
        }
    }

    /**
     *
     * @param peak1
     * @param dim1
     * @param peak2
     * @return
     */
    public static boolean isLinked(Peak peak1, int dim1, Peak peak2) {
        boolean result = false;
        List<PeakDim> peakDims = getLinkedPeakDims(peak1, dim1);
        for (PeakDim peakDim : peakDims) {
            if (peakDim.getPeak() == peak2) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     *
     * @param signals
     * @param nExtra
     */
    public static void trimFreqs(ArrayList signals, int nExtra) {
        while (nExtra > 0) {
            int n = signals.size();
            double min = Double.MAX_VALUE;
            int iMin = 0;

            for (int i = 0; i < (n - 1); i++) {
                SineSignal signal0 = (SineSignal) signals.get(i);
                SineSignal signal1 = (SineSignal) signals.get(i + 1);
                double delta = signal0.diff(signal1);

                if (delta < min) {
                    min = delta;
                    iMin = i;
                }
            }

            SineSignal signal0 = (SineSignal) signals.get(iMin);
            SineSignal signal1 = (SineSignal) signals.get(iMin + 1);
            signals.remove(iMin + 1);
            signal0.merge(signal1);
            nExtra--;
            n--;
        }
    }

    /**
     *
     * @param freqs
     * @param amplitudes
     * @param nExtra
     */
    public static void trimFreqs(double[] freqs, double[] amplitudes, int nExtra) {
        double min = Double.MAX_VALUE;
        int iMin = 0;
        int jMin = 0;
        int n = freqs.length;

        while (nExtra > 0) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double delta = Math.abs(freqs[i] - freqs[j]);

                    if (delta < min) {
                        min = delta;
                        iMin = i;
                        jMin = j;
                    }
                }
            }

            freqs[iMin] = (freqs[iMin] + freqs[jMin]) / 2.0;
            amplitudes[iMin] = amplitudes[iMin] + amplitudes[jMin];

            for (int i = jMin; i < (n - 1); i++) {
                freqs[i] = freqs[i + 1];
                amplitudes[i] = amplitudes[i + 1];
            }

            nExtra--;
            n--;
        }
    }

    final static class CenterRef {

        final int index;
        final int dim;

        public CenterRef(final int index, final int dim) {
            this.index = index;
            this.dim = dim;
        }
    }

    final static class GuessValue {

        final double value;
        final double lower;
        final double upper;
        final boolean floating;

        public GuessValue(final double value, final double lower, final double upper, final boolean floating) {
            this.value = value;
            this.lower = lower;
            this.upper = upper;
            this.floating = floating;
        }
    }

    /**
     *
     * @param pt
     * @param cpt
     * @param width
     * @return
     */
    public boolean inEllipse(final int pt[], final int cpt[], final double[] width) {
        double r2 = 0.0;
        boolean inEllipse = false;
        int cptDim = cpt.length;
        for (int ii = 0; ii < cptDim; ii++) {
            int delta = Math.abs(pt[ii] - cpt[ii]);
            r2 += (delta * delta) / (width[ii] * width[ii]);
        }
        if (r2 < 1.0) {
            inEllipse = true;
        }
        return inEllipse;
    }

    /**
     *
     * @param mode
     */
    public void quantifyPeaks(String mode) {
        if ((peaks == null) || peaks.isEmpty()) {
            return;
        }
        Dataset dataset = Dataset.getDataset(fileName);
        if (dataset == null) {
            throw new IllegalArgumentException("No dataset for peak list");
        }

        java.util.function.Function<RegionData, Double> f = getMeasureFunction(mode);
        if (f == null) {
            throw new IllegalArgumentException("Invalid measurment mode: " + mode);
        }
        int nDataDim = dataset.getNDim();
        if (nDim == nDataDim) {
            quantifyPeaks(dataset, f, mode);
        } else if (nDim == (nDataDim - 1)) {
            int scanDim = 2;
            int nPlanes = dataset.getSize(scanDim);
            quantifyPeaks(dataset, f, mode, nPlanes);
        } else if (nDim > nDataDim) {
            throw new IllegalArgumentException("Peak list has more dimensions than dataset");

        } else {
            throw new IllegalArgumentException("Dataset has more than one extra dimension (relative to peak list)");
        }
    }

    /**
     *
     * @param dataset
     * @param f
     * @param mode
     */
    public void quantifyPeaks(Dataset dataset, java.util.function.Function<RegionData, Double> f, String mode) {
        int[] pdim = getDimsForDataset(dataset, true);
        peaks.stream().forEach(peak -> {
            try {
                peak.quantifyPeak(dataset, pdim, f, mode);
            } catch (IOException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    /**
     *
     * @param dataset
     * @param f
     * @param mode
     * @param nPlanes
     */
    public void quantifyPeaks(Dataset dataset, java.util.function.Function<RegionData, Double> f, String mode, int nPlanes) {
        if (f == null) {
            throw new IllegalArgumentException("Unknown measurment type: " + mode);
        }
        int[] planes = new int[1];
        int[] pdim = getDimsForDataset(dataset, true);

        peaks.stream().forEach(peak -> {
            double[] values = new double[nPlanes];
            for (int i = 0; i < nPlanes; i++) {
                planes[0] = i;
                try {
                    double value = peak.measurePeak(dataset, pdim, planes, f);
                    values[i] = value;
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                }
            }
            if (mode.contains("vol")) {
                peak.setVolume1((float) values[0]);
            } else {
                peak.setIntensity((float) values[0]);
            }
            peak.setMeasures(values);
        });
        setMeasureX(dataset, nPlanes);
    }

    public void setMeasureX(Dataset dataset, int nValues) {
        double[] pValues = null;
        for (int iDim = 0; iDim < dataset.getNDim(); iDim++) {
            pValues = dataset.getValues(iDim);
            if ((pValues != null) && (pValues.length == nValues)) {
                break;
            }
        }
        if (pValues == null) {
            pValues = new double[nValues];
            for (int i = 0; i < pValues.length; i++) {
                pValues[i] = i;
            }
        }
        Measures measure = new Measures(pValues);
        measures = Optional.of(measure);
    }

    /**
     *
     * @param dataset
     * @param speaks
     * @param planes
     */
    public void tweakPeaks(Dataset dataset, Set<Peak> speaks, int[] planes) {
        int[] pdim = getDimsForDataset(dataset, true);
        speaks.stream().forEach(peak -> {
            try {
                peak.tweak(dataset, pdim, planes);
            } catch (IOException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

    }

    /**
     *
     * @param dataset
     * @param planes
     */
    public void tweakPeaks(Dataset dataset, int[] planes) {
        int[] pdim = getDimsForDataset(dataset, true);

        peaks.stream().forEach(peak -> {
            try {
                peak.tweak(dataset, pdim, planes);
            } catch (IOException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

    }

    /**
     *
     * @param theFile
     * @param peakArray
     * @return
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> peakFit(Dataset theFile, Peak... peakArray)
            throws IllegalArgumentException, IOException, PeakFitException {
        boolean doFit = true;
        int fitMode = FIT_ALL;
        boolean updatePeaks = true;
        double[] delays = null;
        double multiplier = 0.686;
        int[] rows = new int[theFile.getNDim()];
        List<Peak> peaks = Arrays.asList(peakArray);
        boolean[] fitPeaks = new boolean[peakArray.length];
        Arrays.fill(fitPeaks, true);
        return peakFit(theFile, peaks, fitPeaks, rows, doFit, fitMode, updatePeaks, delays, multiplier, false, -1, ARRAYED_FIT_MODE.SINGLE);
    }

    /**
     *
     * @param theFile
     * @param peaks
     * @return
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> simPeakFit(Dataset theFile, int[] rows, double[] delays, List<Peak> peaks,
            boolean[] fitPeaks, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        boolean doFit = true;
        int fitMode = FIT_ALL;
        boolean updatePeaks = true;
        double multiplier = 0.686;
        return peakFit(theFile, peaks, fitPeaks, rows, doFit, fitMode, updatePeaks, delays, multiplier, lsFit, constrainDim, arrayedFitMode);
    }

    /**
     *
     * @param theFile
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public void peakFit(Dataset theFile, int[] rows, double[] delays, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        peakFit(theFile, rows, delays, peaks, lsFit, constrainDim, arrayedFitMode);
    }

    /**
     *
     * @param theFile
     * @param peaks
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public void peakFit(Dataset theFile, int[] rows, double[] delays, Collection<Peak> peaks, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        Set<List<Set<Peak>>> oPeaks = null;
        if (constrainDim < 0) {
            oPeaks = getPeakLayers(peaks);
        } else {
            oPeaks = getPeakColumns(peaks, constrainDim);
        }
        oPeaks.stream().forEach(oPeakSet -> {
            try {
                List<Peak> lPeaks = new ArrayList<>();
                int nFit = 0;
                for (int i = 0; i < 3; i++) {
//                    for (Peak peak : oPeakSet.get(i)) {
//                        System.out.print(peak.getName() + " ");
//                    }
//                    System.out.println("layer " + i);
                    lPeaks.addAll(oPeakSet.get(i));
                    if (i == 1) {
                        nFit = lPeaks.size();
                    }

                }
                boolean[] fitPeaks = new boolean[lPeaks.size()];
                Arrays.fill(fitPeaks, true);
                for (int i = nFit; i < fitPeaks.length; i++) {
                    fitPeaks[i] = false;
                }
//                System.out.println("fit lpe " + lPeaks.size());
                simPeakFit(theFile, rows, delays, lPeaks, fitPeaks, lsFit, constrainDim, arrayedFitMode);
            } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        );
    }

    /**
     *
     * @param theFile
     * @param argv
     * @param start
     * @param rows
     * @param doFit
     * @param fitMode
     * @param updatePeaks
     * @param delays
     * @param multiplier
     * @return
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> peakFit(Dataset theFile, String[] argv,
            int start, int[] rows, boolean doFit, int fitMode, final boolean updatePeaks, double[] delays, double multiplier)
            throws IllegalArgumentException, IOException, PeakFitException {

        List<Peak> peaks = new ArrayList<>();

        for (int iArg = start, iPeak = 0; iArg < argv.length; iArg++, iPeak++) {
            Peak peak = getAPeak(argv[iArg]);
            if (peak == null) {
                throw new IllegalArgumentException(
                        "Couln't find peak \"" + argv[iArg] + "\"");
            }
            peaks.add(peak);
        }
        boolean[] fitPeaks = new boolean[peaks.size()];
        Arrays.fill(fitPeaks, true);

        return peakFit(theFile, peaks, fitPeaks, rows, doFit, fitMode, updatePeaks, delays, multiplier, false, -1, ARRAYED_FIT_MODE.SINGLE);
    }

    /**
     * Fit peaks by adjusting peak position (chemical shift), linewidth and
     * intensity to optimize agreement with data values. Multiple peaks are fit
     * simultaneously. These are normally a group of overlapping peaks.
     *
     * @param theFile The dataset to fit the peaks to
     * @param peaks A collection of peaks to fit simultaneously
     * @param fitPeaks A boolean array of to specify a subset of the peaks that
     * will actually be adjusted
     * @param rows An array of rows (planes etc) of the dataset to be used. This
     * is used when the number of peak dimensions is less than the number of
     * dataset dimensions.
     * @param doFit Currently unused
     * @param fitMode An int value that specifies whether to fit all parameters
     * or just amplitudes
     * @param updatePeaks If true update the peaks with the fitted parameters
     * otherwise return a list of the fit parameters
     * @param delays An array of doubles specifying relaxation delays. If not
     * null then fit peaks to lineshapes and an exponential delay model using
     * data values from different rows or planes of dataset
     * @param multiplier unused?? should multiply width of regions
     * @param lsFit If true and a lineshape catalog exists in dataset then use
     * the lineshape catalog to fit
     * @param constrainDim If this is greater than or equal to 0 then the
     * specified all positions and widths of the specified dimension will be
     * constrained to be the same value. Useful for fitting column or row of
     * peaks.
     * @return a List of alternating name/values with the parameters of the fit
     * if updatePeaks is false. Otherwise return empty list
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> peakFit(Dataset theFile, List<Peak> peaks,
            boolean[] fitPeaks,
            int[] rows, boolean doFit, int fitMode, final boolean updatePeaks,
            double[] delays, double multiplier, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        List<Object> peaksResult = new ArrayList<>();
        if (peaks.isEmpty()) {
            return peaksResult;
        }
        boolean fitC = false;
        PeakList peakList = peaks.get(0).getPeakList();
        int nPeakDim = peakList.getNDim();
        int dataDim = theFile.getNDim();
        int rowDim = dataDim - 1;

        int[] pdim = new int[nPeakDim];
        int[][] p1 = new int[dataDim][2];
        int[][] p2 = new int[dataDim][2];
        int nPeaks = peaks.size();
        int[][] cpt = new int[nPeaks][dataDim];
        double[][] width = new double[nPeaks][dataDim];
        double maxDelay = 0.0;
        int nPlanes = 1;
        if ((delays != null) && (delays.length > 0)) {
            maxDelay = StatUtils.max(delays);
        }
        int[][] syncPars = null;
        if (constrainDim != -1) {
            syncPars = new int[peaks.size() * 2][2];
        }

        //int k=0;
        for (int i = 0; i < nPeakDim; i++) {
            pdim[i] = -1;
        }

        // a list of guesses for the fitter
        ArrayList<GuessValue> guessList = new ArrayList<>();
        ArrayList<CenterRef> centerList = new ArrayList<>();
        boolean firstPeak = true;
        int iPeak = -1;
        double globalMax = 0.0;
        for (Peak peak : peaks) {
            iPeak++;
            if (dataDim < nPeakDim) {
                throw new IllegalArgumentException(
                        "Number of peak list dimensions greater than number of dataset dimensions");
            }
            for (int j = 0; j < nPeakDim; j++) {
                boolean ok = false;
                for (int i = 0; i < dataDim; i++) {
                    if (peakList.getSpectralDim(j).getDimName().equals(
                            theFile.getLabel(i))) {
                        pdim[j] = i;
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    throw new IllegalArgumentException(
                            "Can't find match for peak dimension \""
                            + peak.peakList.getSpectralDim(j).getDimName() + "\"");
                }
            }
            for (int dDim = 0, iRow = 0; dDim < dataDim; dDim++) {
                boolean gotThisDim = false;
                for (int pDim = 0; pDim < pdim.length; pDim++) {
                    if (pdim[pDim] == dDim) {
                        gotThisDim = true;
                        break;
                    }
                }
                if (!gotThisDim) {
                    p2[dDim][0] = rows[iRow];
                    p2[dDim][1] = rows[iRow];
                    rowDim = dDim;
                    iRow++;
                }
            }

            peak.getPeakRegion(theFile, pdim, p1, cpt[iPeak], width[iPeak]);
//            System.out.println("fit " + peak);
//            for (int i = 0; i < p2.length; i++) {
//                System.out.println(i + " p1 " + p1[i][0] + " " + p1[i][1]);
//                System.out.println(i + " p2 " + p2[i][0] + " " + p2[i][1]);
//            }
//            for (int i = 0; i < width.length; i++) {
//                for (int j = 0; j < width[i].length; j++) {
//                    System.out.println(i + " " + j + " wid " + width[i][j] + " cpt " + cpt[i][j]);
//                }
//            }

            double intensity = (double) peak.getIntensity();
            GuessValue gValue;
            if (intensity > 0.0) {
                gValue = new GuessValue(intensity, intensity * 0.1, intensity * 3.5, true);
            } else {
                gValue = new GuessValue(intensity, intensity * 1.5, intensity * 0.5, true);
            }
            // add intensity for this peak to guesses
            guessList.add(gValue);
            if (FastMath.abs(intensity) > globalMax) {
                globalMax = FastMath.abs(intensity);
            }
            if ((dataDim - nPeakDim) == 1) {
                if (arrayedFitMode != ARRAYED_FIT_MODE.SINGLE) {
                    nPlanes = theFile.getSize(dataDim - 1);
                }
            }
            // if rate mode add guesses for relaxation time constant 1/rate and
            // intensity at infinite delay
            if ((delays != null) && (delays.length > 0)) {
                gValue = new GuessValue(maxDelay / 2.0, maxDelay * 5.0, maxDelay * 0.02, true);
                guessList.add(gValue);
                if (fitC) {
                    gValue = new GuessValue(0.0, -0.5 * FastMath.abs(intensity), 0.5 * FastMath.abs(intensity), true);
                    guessList.add(gValue);
                }
            } else if (nPlanes != 1) {
                for (int iPlane = 1; iPlane < nPlanes; iPlane++) {
                    gValue = new GuessValue(intensity, 0.0, intensity * 3.5, true);
                    guessList.add(gValue);
                }
            }
            // loop over dimensions and add guesses for width and position

            for (int pkDim = 0; pkDim < peak.peakList.nDim; pkDim++) {
                int dDim = pdim[pkDim];
                // adding one to account for global max inserted at end
                int parIndex = guessList.size() + 2;
                // if fit amplitudes constrain width fixme
                boolean fitThis = fitPeaks[iPeak];
                if (syncPars != null) {
                    if ((iPeak == 0) && (dDim == constrainDim)) {
                        syncPars[0][0] = parIndex;
                        syncPars[0][1] = parIndex;
                        syncPars[1][0] = parIndex - 1;
                        syncPars[1][1] = parIndex - 1;
                    } else if ((iPeak > 0) && (dDim == constrainDim)) {
                        fitThis = false;
                        syncPars[iPeak * 2][0] = parIndex;
                        syncPars[iPeak * 2][1] = syncPars[0][0];
                        syncPars[iPeak * 2 + 1][0] = parIndex - 1;
                        syncPars[iPeak * 2 + 1][1] = syncPars[1][0];
                    }
                }
                if (fitMode == FIT_AMPLITUDES) {
                    gValue = new GuessValue(width[iPeak][dDim], width[iPeak][dDim] * 0.05, width[iPeak][dDim] * 1.05, false);
                } else {
                    gValue = new GuessValue(width[iPeak][dDim], width[iPeak][dDim] * 0.2, width[iPeak][dDim] * 2.0, fitThis);
                }
                guessList.add(gValue);
                centerList.add(new CenterRef(parIndex, dDim));
                // if fit amplitudes constrain cpt to near current value  fixme
                // and set floating parameter of GuessValue to false
                if (fitMode == FIT_AMPLITUDES) {
                    gValue = new GuessValue(cpt[iPeak][dDim], cpt[iPeak][dDim] - width[iPeak][dDim] / 40, cpt[iPeak][dDim] + width[iPeak][dDim] / 40, false);
                } else {
                    gValue = new GuessValue(cpt[iPeak][dDim], cpt[iPeak][dDim] - width[iPeak][dDim] / 2, cpt[iPeak][dDim] + width[iPeak][dDim] / 2, fitThis);
                }
                guessList.add(gValue);
//System.out.println(iDim + " " + p1[iDim][0] + " " +  p1[iDim][1]);

                // update p2 based on region of peak so it encompasses all peaks
                if (firstPeak) {
                    p2[dDim][0] = p1[dDim][0];
                    p2[dDim][1] = p1[dDim][1];
                } else {
                    if (p1[dDim][0] < p2[dDim][0]) {
                        p2[dDim][0] = p1[dDim][0];
                    }

                    if (p1[dDim][1] > p2[dDim][1]) {
                        p2[dDim][1] = p1[dDim][1];
                    }
                }
            }
            firstPeak = false;
        }
        if ((delays != null) && (delays.length > 0)) {
            GuessValue gValue = new GuessValue(0.0, -0.5 * globalMax, 0.5 * globalMax, false);
            guessList.add(0, gValue);
        } else {
            GuessValue gValue = new GuessValue(0.0, -0.5 * globalMax, 0.5 * globalMax, false);
            guessList.add(0, gValue);
        }
        // get a list of positions that are near the centers of each of the peaks
        ArrayList<int[]> posArray = theFile.getFilteredPositions(p2, cpt, width, pdim, multiplier);
        if (posArray.isEmpty()) {
            System.out.println("no positions");
            for (Peak peak : peaks) {
                System.out.println(peak.getName());
            }
            for (int i = 0; i < p2.length; i++) {
                System.out.println(pdim[i] + " " + p2[i][0] + " " + p2[i][1]);
            }
            for (int i = 0; i < width.length; i++) {
                for (int j = 0; j < width[i].length; j++) {
                    System.out.println(i + " " + j + " " + width[i][j] + " " + cpt[i][j]);
                }
            }

            return peaksResult;

        }
        // adjust guesses for positions so they are relative to initial point
        // position in each dimension
        for (CenterRef centerRef : centerList) {
            GuessValue gValue = guessList.get(centerRef.index);
            int offset = p2[centerRef.dim][0];
            gValue = new GuessValue(gValue.value - offset, gValue.lower - offset, gValue.upper - offset, gValue.floating);
            guessList.set(centerRef.index, gValue);
        }
        int[][] positions = new int[posArray.size()][nPeakDim];
        int i = 0;
        for (int[] pValues : posArray) {
            for (int pkDim = 0; pkDim < nPeakDim; pkDim++) {
                int dDim = pdim[pkDim];
                positions[i][pkDim] = pValues[dDim] - p2[dDim][0];
            }
            i++;
        }
        LorentzGaussND peakFit;
        if (lsFit && (theFile.getLSCatalog() != null)) {
            peakFit = new LorentzGaussNDWithCatalog(positions, theFile.getLSCatalog());
        } else {
            peakFit = new LorentzGaussND(positions);
        }
        double[] guess = new double[guessList.size()];
        double[] lower = new double[guess.length];
        double[] upper = new double[guess.length];
        boolean[] floating = new boolean[guess.length];
        i = 0;
        for (GuessValue gValue : guessList) {
            guess[i] = gValue.value;
            lower[i] = gValue.lower;
            upper[i] = gValue.upper;
            floating[i] = gValue.floating;
            i++;
        }
        int nRates = nPlanes;
        if ((delays != null) && (delays.length > 0)) {
            nRates = delays.length;
        }

        double[][] intensities = new double[nRates][];
        if (nRates == 1) {
            intensities[0] = theFile.getIntensities(posArray);
        } else {
            for (int iRate = 0; iRate < nRates; iRate++) {
                ArrayList<int[]> pos2Array = new ArrayList<>();
                for (int[] pos : posArray) {
                    pos[rowDim] = iRate;
                    pos2Array.add(pos);
                }
                intensities[iRate] = theFile.getIntensities(pos2Array);
            }
        }
        peakFit.setDelays(delays, fitC);
        peakFit.setIntensities(intensities);
        peakFit.setOffsets(guess, lower, upper, floating, syncPars);
        int nFloating = 0;
        for (boolean floats : floating) {
            if (floats) {
                nFloating++;
            }
        }
        //int nInterpolationPoints = (nFloating + 1) * (nFloating + 2) / 2;
        int nInterpolationPoints = 2 * nFloating + 1;
        int nSteps = nInterpolationPoints * 5;
        //System.out.println(guess.length + " " + nInterpolationPoints);
        PointValuePair result;
        try {
            result = peakFit.optimizeBOBYQA(nSteps, nInterpolationPoints);
        } catch (TooManyEvaluationsException tmE) {
            throw new PeakFitException(tmE.getMessage());
        }
        double[] values = result.getPoint();
        for (CenterRef centerRef : centerList) {
            int offset = p2[centerRef.dim][0];
            values[centerRef.index] += offset;
        }
        if (updatePeaks) {
            int index = 1;
            for (Peak peak : peaks) {
                peak.setIntensity((float) values[index++]);
                if ((delays != null) && (delays.length > 0)) {
                    if (fitC) {
                        peak.setComment(String.format("T %.4f %.3f", values[index++], values[index++]));
                    } else {
                        peak.setComment(String.format("T %.4f", values[index++]));
                    }
                } else if (nPlanes > 1) {
                    double[] measures = new double[nPlanes];
                    index--;
                    for (int iPlane = 0; iPlane < nPlanes; iPlane++) {
                        measures[iPlane] = values[index++];
                    }
                    peak.setMeasures(measures);
                }
                double lineWidthAll = 1.0;
                for (int pkDim = 0; pkDim < nPeakDim; pkDim++) {
                    int dDim = pdim[pkDim];
                    PeakDim peakDim = peak.getPeakDim(pkDim);
                    double lineWidth = theFile.ptWidthToPPM(dDim, values[index]);
                    //double lineWidthHz = theFile.ptWidthToHz(iDim, values[index++]);
                    double lineWidthHz = values[index++];
                    peakDim.setLineWidthValue((float) lineWidth);
                    //lineWidthAll *= lineWidthHz * (Math.PI / 2.0);
                    lineWidthAll *= lineWidthHz;
                    peakDim.setBoundsValue((float) (lineWidth * 1.5));
                    peakDim.setChemShiftValueNoCheck((float) theFile.pointToPPM(dDim, values[index++]));
                }
                peak.setVolume1((float) (peak.getIntensity() * lineWidthAll));
            }
            if (nPlanes > 1) {
                peakList.setMeasureX(theFile, nPlanes);
            }
        } else {
            int index = 1;
            for (Peak peak : peaks) {
                List<Object> peakData = new ArrayList<>();

                peakData.add("peak");
                peakData.add(peak.getName());
                peakData.add("int");
                peakData.add(values[index++]);
                if ((delays != null) && (delays.length > 0)) {
                    peakData.add("relax");
                    peakData.add(values[index++]);
                    if (fitC) {
                        peakData.add("relaxbase");
                        peakData.add(values[index++]);
                    }
                } else if (nPlanes > 1) {
                    index += nPlanes - 1;
                }
                double lineWidthAll = 1.0;
                for (int iDim = 0; iDim < peak.peakList.nDim; iDim++) {
                    //double lineWidthHz = theFile.ptWidthToHz(iDim, values[index++]);
                    double lineWidthHz = values[index++];
                    //lineWidthAll *= lineWidthHz * (Math.PI / 2.0);
                    lineWidthAll *= lineWidthHz;
                    String elem = (iDim + 1) + ".WH";
                    peakData.add(elem);
                    peakData.add(lineWidthHz);
                    elem = (iDim + 1) + ".P";
                    peakData.add(elem);
                    peakData.add(theFile.pointToPPM(iDim, values[index++]));
                }
                peakData.add("vol");
                peakData.add(peak.getIntensity() * lineWidthAll);
                peaksResult.add(peakData);
            }
        }
        return peaksResult;
    }

    /**
     *
     * @return
     */
    public Set<List<Peak>> getOverlappingPeaks() {
        Set<List<Peak>> result = new HashSet<>();
        boolean[] used = new boolean[size()];
        for (int i = 0, n = size(); i < n; i++) {
            Peak peak = getPeak(i);
            if (used[i]) {
                continue;
            }
            Set<Peak> overlaps = peak.getAllOverlappingPeaks();
            result.add(new ArrayList<Peak>(overlaps));
            for (Peak checkPeak : overlaps) {
                used[checkPeak.getIndex()] = true;
            }
        }
        return result;
    }

    public Set<List<Set<Peak>>> getPeakLayers() {
        return getPeakLayers(peaks);
    }

    /**
     *
     * @param fitPeaks
     * @return
     */
    public Set<List<Set<Peak>>> getPeakLayers(Collection<Peak> fitPeaks) {
        Set<List<Set<Peak>>> result = new HashSet<>();
        Set<Peak> used = new HashSet<>();
        for (Peak peak : fitPeaks) {
            if (used.contains(peak)) {
                continue;
            }
            List<Set<Peak>> overlaps = peak.getOverlapLayers(1.5);
            result.add(overlaps);
            Set<Peak> firstLayer = overlaps.get(1);
            Set<Peak> secondLayer = overlaps.get(2);
            if (secondLayer.isEmpty()) {
                for (Peak checkPeak : firstLayer) {
                    used.add(checkPeak);
                }
            }
        }
        return result;
    }

    /**
     *
     * @param fitPeaks
     * @return
     */
    public Set<List<Set<Peak>>> getPeakColumns(Collection<Peak> fitPeaks, int iDim) {
        Set<List<Set<Peak>>> result = new HashSet<>();
        Set<Peak> used = new HashSet<>();
        for (Peak peak : fitPeaks) {
            if (!used.contains(peak)) {
                List<PeakDim> peakDims = getLinkedPeakDims(peak, iDim);
                Set<Peak> firstLayer = new HashSet<>();
                for (PeakDim peakDim : peakDims) {
                    used.add(peakDim.getPeak());
                    firstLayer.add(peakDim.getPeak());
                }
                List<Set<Peak>> column = new ArrayList<>();
                column.add(firstLayer);
                column.add(Collections.EMPTY_SET);
                column.add(Collections.EMPTY_SET);
                result.add(column);
            }

        }
        return result;
    }

    /**
     *
     * @param fitPeaks
     * @return
     */
    public Set<Set<Peak>> getOverlappingPeaks(Collection<Peak> fitPeaks) {
        Set<Set<Peak>> result = new HashSet<>();
        Set<Peak> used = new HashSet<>();
        for (Peak peak : fitPeaks) {
            if (used.contains(peak)) {
                continue;
            }
            Set<Peak> overlaps = peak.getAllOverlappingPeaks();
            result.add(overlaps);
            for (Peak checkPeak : overlaps) {
                used.add(checkPeak);
            }
        }
        return result;
    }

    /**
     *
     */
    static public class PhaseRelationship {

        private static final TreeMap TYPES_LIST = new TreeMap();

        /**
         *
         */
        public static final PhaseRelationship ANYPHASE = new PhaseRelationship(
                "anyphase");

        /**
         *
         */
        public static final PhaseRelationship INPHASE = new PhaseRelationship(
                "inphase");

        /**
         *
         */
        public static final PhaseRelationship INPHASE_POS = new PhaseRelationship(
                "inphase_pos");

        /**
         *
         */
        public static final PhaseRelationship INPHASE_NEG = new PhaseRelationship(
                "inphase_neg");

        /**
         *
         */
        public static final PhaseRelationship ANTIPHASE = new PhaseRelationship(
                "antiphase");

        /**
         *
         */
        public static final PhaseRelationship ANTIPHASE_LEFT = new PhaseRelationship(
                "antiphase_left");

        /**
         *
         */
        public static final PhaseRelationship ANTIPHASE_RIGHT = new PhaseRelationship(
                "antiphase_right");
        private final String name;

        private PhaseRelationship(String name) {
            this.name = name;
            TYPES_LIST.put(name, this);
        }

        @Override
        public String toString() {
            return name;
        }

        /**
         *
         * @return
         */
        public boolean isSigned() {
            return (toString().contains("_"));
        }

        /**
         *
         * @param name
         * @return
         */
        public static PhaseRelationship getFromString(String name) {
            return (PhaseRelationship) TYPES_LIST.get(name);
        }

        /**
         *
         * @param intensity1
         * @param intensity2
         * @return
         */
        public static PhaseRelationship getType(double intensity1,
                double intensity2) {
            if (intensity1 > 0) {
                if (intensity2 > 0) {
                    return INPHASE;
                } else {
                    return ANTIPHASE;
                }
            } else if (intensity2 > 0) {
                return ANTIPHASE;
            } else {
                return INPHASE;
            }
        }

        /**
         *
         * @param ctr1
         * @param intensity1
         * @param ctr2
         * @param intensity2
         * @return
         */
        public static PhaseRelationship getType(double ctr1, double intensity1,
                double ctr2, double intensity2) {
            double left;
            double right;

            if (ctr1 > ctr2) {
                left = intensity1;
                right = intensity2;
            } else {
                left = intensity2;
                right = intensity1;
            }

            if (left > 0) {
                if (right > 0) {
                    return INPHASE_POS;
                } else {
                    return ANTIPHASE_LEFT;
                }
            } else if (right > 0) {
                return ANTIPHASE_RIGHT;
            } else {
                return INPHASE_NEG;
            }
        }
    }

    /**
     *
     */
    public class DistanceMatch {

        int iPeak = 0;
        int jPeak = 0;
        double delta = 0.0;
        double[] deltas;

        DistanceMatch(int iPeak, int jPeak, double[] deltas, double delta) {
            this.iPeak = iPeak;
            this.jPeak = jPeak;
            this.delta = delta;
            this.deltas = new double[deltas.length];

            System.arraycopy(deltas, 0, this.deltas, 0, deltas.length);
        }

        double getDelta() {
            return delta;
        }

        /**
         *
         * @param aNeighbors
         * @param iNeighbor
         * @param bNeighbors
         * @param jNeighbor
         * @return
         */
        public double compare(DistanceMatch[][] aNeighbors, int iNeighbor,
                DistanceMatch[][] bNeighbors, int jNeighbor) {
            double globalSum = 0.0;

            for (DistanceMatch aDis : aNeighbors[iNeighbor]) {
                double sumMin = Double.MAX_VALUE;

                for (DistanceMatch bDis : bNeighbors[jNeighbor]) {
                    double sum = 0.0;

                    for (int k = 0; k < deltas.length; k++) {
                        double dif = (aDis.deltas[k] - bDis.deltas[k]);
                        sum += (dif * dif);
                    }

                    if (sum < sumMin) {
                        sumMin = sum;
                    }
                }

                globalSum += Math.sqrt(sumMin);
            }

            return globalSum;
        }

        @Override
        public String toString() {
            StringBuilder sBuf = new StringBuilder();
            sBuf.append(iPeak);
            sBuf.append(" ");
            sBuf.append(jPeak);
            sBuf.append(" ");
            sBuf.append(delta);

            for (int j = 0; j < deltas.length; j++) {
                sBuf.append(" ");
                sBuf.append(deltas[j]);
            }

            return sBuf.toString();
        }
    }

    /**
     *
     * @param iDim
     * @return
     */
    public SpectralDim getSpectralDim(int iDim) {
        SpectralDim specDim = null;
        if (iDim < spectralDims.length) {
            specDim = spectralDims[iDim];
        }
        return specDim;
    }

    /**
     *
     * @param datasetName
     * @return
     */
    public static String getNameForDataset(String datasetName) {
        int lastIndex = datasetName.lastIndexOf(".");
        String listName = datasetName;
        if (lastIndex != -1) {
            listName = datasetName.substring(0, lastIndex);
        }
        return listName;
    }

    /**
     *
     * @param datasetName
     * @return
     */
    public static PeakList getPeakListForDataset(String datasetName) {
        for (PeakList peakList : peakListTable.values()) {
            if (peakList.fileName.equals(datasetName)) {
                return peakList;
            }
        }
        return null;
    }

    /**
     *
     * @param iDim
     * @return
     */
    public DoubleSummaryStatistics shiftStats(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getChemShift()).summaryStatistics();
        return stats;

    }

    /**
     *
     * @param iDim
     * @return
     */
    public DoubleSummaryStatistics widthStats(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getLineWidthHz()).summaryStatistics();
        return stats;
    }

    /**
     *
     * @param iDim
     * @return
     */
    public DoubleSummaryStatistics widthStatsPPM(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getLineWidth()).summaryStatistics();
        return stats;
    }

    /**
     *
     * @param iDim
     * @return
     */
    public DescriptiveStatistics widthDStats(int iDim) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getLineWidthHz()).forEach(v -> stats.addValue(v));
        return stats;
    }

    public DescriptiveStatistics intensityDStats(int iDim) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        peaks.stream().filter(p -> p.getStatus() >= 0)
                .mapToDouble(p -> p.getPeakDim(iDim).myPeak.getIntensity())
                .forEach(v -> stats.addValue(v));
        return stats;
    }

    /**
     *
     * @param iDim
     * @return
     */
    public DescriptiveStatistics shiftDStats(int iDim) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getChemShiftValue()).forEach(v -> stats.addValue(v));
        return stats;
    }

    /**
     *
     * @param iDim
     * @return
     */
    public double center(int iDim) {
        OptionalDouble avg = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getChemShift()).average();
        return avg.getAsDouble();
    }

    /**
     *
     * @param otherList
     * @param dims
     * @return
     */
    public double[] centerAlign(PeakList otherList, int[] dims) {
        double[] deltas = new double[dims.length];
        for (int i = 0; i < dims.length; i++) {
            int k = dims[i];
            if (k != -1) {
                for (int j = 0; j < otherList.nDim; j++) {
                    if (spectralDims[k].getDimName().equals(otherList.spectralDims[j].getDimName())) {
                        double center1 = center(k);
                        double center2 = otherList.center(j);
                        System.out.println(i + " " + k + " " + j + " " + center1 + " " + center2);
                        deltas[i] = center2 - center1;
                    }
                }
            }
        }
        return deltas;
    }

    /**
     *
     * @param iDim
     * @param value
     */
    public void shiftPeak(final int iDim, final double value) {
        peaks.stream().forEach(p -> {
            PeakDim pDim = p.peakDims[iDim];
            float shift = pDim.getChemShift();
            shift += value;
            pDim.setChemShiftValue(shift);
        });
    }

    /**
     *
     * @return
     */
    public Nuclei[] guessNuclei() {
        double[] sf = new double[nDim];
        for (int i = 0; i < nDim; i++) {
            SpectralDim sDim = getSpectralDim(i);
            sf[i] = sDim.getSf();
        }
        Nuclei[] nuclei = Nuclei.findNuclei(sf);
        return nuclei;
    }

    /**
     *
     * @return
     */
    public boolean requireSliderCondition() {
        return requireSliderCondition;
    }
}
