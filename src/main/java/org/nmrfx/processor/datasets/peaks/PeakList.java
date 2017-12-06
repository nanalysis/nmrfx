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
import org.nmrfx.processor.math.*;
import org.nmrfx.processor.optimization.*;
import org.nmrfx.processor.star.*;
import org.nmrfx.processor.utilities.NvUtil;
import org.nmrfx.processor.utilities.Util;
import java.io.*;
import static java.lang.Double.compare;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.apache.commons.math3.optimization.PointValuePair;
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
import org.nmrfx.processor.datasets.Nuclei;
import org.nmrfx.processor.datasets.RegionData;
import static org.nmrfx.processor.datasets.peaks.Peak.getMeasureFunction;

public class PeakList {

    public static final int FIT_ALL = 0;
    public static final int FIT_AMPLITUDES = 2;
    public static final int FIT_LW_AMPLITUDES = 1;
    public static final int FIT_MAX_DEV = 3;
    public static final int FIT_RMS = 4;
    public static ObservableMap<String, PeakList> peakListTable = FXCollections.observableMap(new LinkedHashMap<>());
    public static PeakList clusterOrigin = null;
    private String listName;
    public String fileName;
    private final int listNum;
    public int nDim;
    public boolean inMem;
    private SpectralDim[] spectralDims = null;
    List<SearchDim> searchDims = new ArrayList<>();
    public double scale;
    private List<Peak> peaks;
    public int idLast;
    private Map<Integer, Peak> indexMap = new HashMap<>();
    private String details = "";
    private String sampleLabel = "";
    private String sampleConditionLabel = "";
    private HashSet<Multiplet> multiplets = new HashSet<Multiplet>();
    private ArrayList<Multiplet> sortedMultiplets = new ArrayList<Multiplet>();
    static Vector globalListeners = new Vector();
    Vector listeners = new Vector();
    private boolean thisListUpdated = false;
    boolean changed = false;
    static boolean aListUpdated = false;
    static boolean needToFireEvent = false;
    boolean multipletsSorted = false;
    ScheduledThreadPoolExecutor schedExecutor = new ScheduledThreadPoolExecutor(2);
    ScheduledFuture futureUpdate = null;

    class UpdateTask implements Runnable {

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
        if (needToFireEvent || (futureUpdate == null) || futureUpdate.isDone()) {
            UpdateTask updateTask = new UpdateTask();
            futureUpdate = schedExecutor.schedule(updateTask, 200, TimeUnit.MILLISECONDS);
        }

    }

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

        peaks = new Vector();
        indexMap.clear();

        peakListTable.put(listName, this);
        listNum = peakListTable.size();
    }

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
                for (int j = 0; j < peak.peakDim.length; j++) {
                    PeakDim peakDim1 = peak.peakDim[j];
                    PeakDim peakDim2 = newPeak.peakDim[j];
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
                for (int j = 0; j < oldPeak.peakDim.length; j++) {
                    ArrayList<PeakDim> linkedPeakDims = getLinkedPeakDims(oldPeak, j);
                    PeakDim newPeakDim = newPeak.peakDim[j];
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

    public List<Peak> peaks() {
        return peaks;
    }

    public static Iterator iterator() {
        return peakListTable.values().iterator();
    }

    public int getId() {
        return listNum;
    }

    public String getName() {
        return listName;
    }

    public void setName(String newName) {
        peakListTable.remove(listName);
        listName = newName;
        peakListTable.put(newName, this);
    }

    public void setSampleLabel(String sampleLabel) {
        this.sampleLabel = sampleLabel;
    }

    public String getSampleLabel() {
        return sampleLabel;
    }

    public void setSampleConditionLabel(String sampleConditionLabel) {
        this.sampleConditionLabel = sampleConditionLabel;
    }

    public String getSampleConditionLabel() {
        return sampleConditionLabel;
    }

    public void setDatasetName(String datasetName) {
        this.fileName = datasetName;
    }

    public String getDatasetName() {
        return fileName;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getDetails() {
        return details;
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

    public void removeListener(PeakListener oldListener) {
        for (int i = 0; i < listeners.size(); i++) {
            PeakListener listener = (PeakListener) listeners.get(i);
            if (listener == oldListener) {
                listeners.remove(i);
                break;
            }

        }
    }

    public void registerListener(PeakListener newListener) {
        for (int i = 0; i < listeners.size(); i++) {
            PeakListener listener = (PeakListener) listeners.get(i);
            if (listener == newListener) {
                return;
            }
        }
        listeners.add(newListener);
    }

    static void registerGlobalListener(PeakListener newListener) {
        for (int i = 0; i < globalListeners.size(); i++) {
            PeakListener listener = (PeakListener) globalListeners.get(i);
            if (listener == newListener) {
                return;
            }
        }
        globalListeners.add(newListener);
    }

    void notifyListeners() {
        for (int i = 0; i < listeners.size(); i++) {
            PeakListener listener = (PeakListener) listeners.get(i);
            listener.peakListChanged(new PeakEvent(this));
        }
    }

    static void notifyGlobalListeners() {
        for (int i = 0; i < globalListeners.size(); i++) {
            PeakListener listener = (PeakListener) globalListeners.get(i);
            listener.peakListChanged(new PeakEvent("*"));
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

    public boolean isChanged() {
        return changed;
    }

    public void clearChanged() {
        changed = false;
    }

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

    public static void clearAllChanged() {
        for (Object checkList : peakListTable.values()) {
            ((PeakList) checkList).clearChanged();
        }
    }

    private void sortMultiplets() {
        sortedMultiplets = new ArrayList<Multiplet>();
        sortedMultiplets.addAll(multiplets);
        Collections.sort(sortedMultiplets);
        int i = 0;
        for (Multiplet multiplet : sortedMultiplets) {
            multiplet.setIDNum(i);
            i++;
        }
    }

    public synchronized ArrayList<Multiplet> getMultiplets() {
        if (((sortedMultiplets.size() == 0) && (multiplets.size() > 0)) || !multipletsSorted) {
            sortMultiplets();
            multipletsSorted = true;
        }
        return sortedMultiplets;
    }

    public boolean hasSearchDims() {
        return !searchDims.isEmpty();
    }

    public void clearSearchDims() {
        searchDims.clear();
    }

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

    public void addSearchDim(String dimName, double tol) {
        int iDim = getListDim(dimName);
        addSearchDim(iDim, tol);
    }

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

    public void clearIndex() {
        indexMap.clear();
    }

    public void setFOM(double noiseLevel) {
        for (int i = 0; i < peaks.size(); i++) {
            Peak peak = peaks.get(i);
            double devMul = Math.abs(peak.getIntensity() / noiseLevel);
            if (devMul > 20.0) {
                devMul = 20.0;
            }
            double erf = 1.0;
            try {
                erf = org.apache.commons.math3.special.Erf.erf(devMul / Math.sqrt(2.0));
            } catch (MaxCountExceededException mathE) {
                erf = 1.0;
            }
            float fom = (float) (0.5 * (1.0 + erf));
            peak.setFigureOfMerit(fom);
        }
    }

    public void reNumber() {
        for (int i = 0; i < peaks.size(); i++) {
            Peak peak = peaks.get(i);
            peak.setIdNum(i);
        }

        reIndex();
    }

    public void reIndex() {
        int i = 0;
        indexMap.clear();
        for (Peak peak : peaks) {
            peak.setIndex(i++);
            indexMap.put(peak.getIdNum(), peak);
        }
        peakListUpdated(this);
    }

    public int size() {
        if (peaks == null) {
            return 0;
        } else {
            return peaks.size();
        }
    }

    public boolean valid() {
        if (get(listName) == null) {
            return false;
        } else {
            return true;
        }
    }

    public static PeakList get(String listName) {
        return ((PeakList) peakListTable.get(listName));
    }

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

    public static void remove(String listName) {
        PeakDim peakdim;
        PeakList peakList = (PeakList) peakListTable.get(listName);

        if (peakList != null) {
            for (Peak peak : peakList.peaks) {
                for (PeakDim peakDim : peak.peakDim) {
                    peakdim = peakDim;
                    Iterator iter = peakdim.getIterator();
                    while (iter.hasNext()) {
                        PeakDimContrib pdc = (PeakDimContrib) iter.next();
                        Resonance resonance = pdc.getResonance();
                        if (resonance != null) {
                            resonance.removePeakDimContrib(pdc);
                        }
                    }
                    Multiplet multiplet = peakdim.getMultiplet();
                    if (multiplet != null) {
                        multiplet.removePeakDim(peakdim);
                    }
                }
                peak.markDeleted();
            }
            peakList.multiplets.clear();
            peakList.multiplets = null;
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

    public int countMultiplets() {
        return multiplets.size();
    }

    public void addMultiplet(Multiplet multiplet) {
        multiplets.add(multiplet);
        multipletsSorted = false;
    }

    public void removeMultiplet(Multiplet multiplet) {
        multiplets.remove(multiplet);
        multipletsSorted = false;
    }
    static final String[] nefPeakDimStrings = {"_nef_spectrum_dimension.dimension_id",
        "_nef_spectrum_dimension.axis_unit",
        "_nef_spectrum_dimension.axis_code",
        "_nef_spectrum_dimension.spectrometer_frequency",
        "_nef_spectrum_dimension.spectral_width",
        "_nef_spectrum_dimension.value_first_point",
        "_nef_spectrum_dimension.folding",
        "_nef_spectrum_dimension.absolute_peak_positions",
        "_nef_spectrum_dimension.is_acquisition"};

    // //     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true
    static final String[] xpkPeakDimStrings = {
        "label",
        "code",
        "units",
        "sf",
        "sw",
        "fp",
        "idtol",
        "pattern",
        "bonded",
        "spatial",
        "folding",
        "abspos",
        "acqdim"};

    static final String[] nefPeakRowStrings = {"_nef_peak.ordinal",
        "_nef_peak.peak_id",
        "_nef_peak.volume",
        "_nef_peak.volume_uncertainty",
        "_nef_peak.height",
        "_nef_peak.height_uncertainty",
        "_nef_peak.position_1",
        "_nef_peak.position_uncertainty_1",
        "_nef_peak.position_2",
        "_nef_peak.position_uncertainty_2",
        "_nef_peak.position_3",
        "_nef_peak.position_uncertainty_3",
        "_nef_peak.chain_code_1",
        "_nef_peak.sequence_code_1",
        "_nef_peak.residue_type_1",
        "_nef_peak.atom_name_1",
        "_nef_peak.chain_code_2",
        "_nef_peak.sequence_code_2",
        "_nef_peak.residue_type_2",
        "_nef_peak.atom_name_2",
        "_nef_peak.chain_code_3",
        "_nef_peak.sequence_code_3",
        "_nef_peak.residue_type_3",
        "_nef_peak.atom_name_3"};

    public void writePeaksNEF(FileWriter chan) throws IOException, InvalidPeakException {
        Peak peak = null;
        char stringQuote = '"';

        chan.write("save_nef_nmr_spectrum_" + getName() + "\n");

        chan.write("_nef_nmr_spectrum.sf_category                 ");
        chan.write("nef_nmr_spectrum\n");

        chan.write("_nef_nmr_spectrum.sf_framecode                 ");
        chan.write("nef_nmr_spectrum_" + getName() + "\n");

        chan.write("_nef_nmr_spectrum.chemical_shift_list                          ");
        chan.write(".\n");

        chan.write("_nef_nmr_spectrum.experiment_classification               ");
        chan.write(".\n");

        chan.write("_nef_nmr_spectrum.expriment_type                   ");
        chan.write(".\n");

        chan.write("loop_\n");
        for (int j = 0; j < nefPeakDimStrings.length; j++) {
            chan.write(nefPeakDimStrings[j] + "\n");
        }
        chan.write("\n");
//     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true
//     2   ppm   1H    500.13   10.986153600089578   10.393076800044788   circular   true   false
//     3   ppm   15N   50.666   24.002901353965186   128.00145067698259   circular   true   false

        for (int j = 0; j < spectralDims.length; j++) {
            chan.write(spectralDims[j].toSTAR3LoopPeakCharString() + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");

        int nPeaks = peaks.size();
        chan.write("loop_\n");
        for (int j = 0; j < nefPeakRowStrings.length; j++) {
            chan.write(nefPeakRowStrings[j] + "\n");
        }

        for (int i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toNEFString(i) + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        chan.write("\nsave_\n\n");
    }

    public void writePeaksSTAR3(FileWriter chan) throws IOException, InvalidPeakException {
        Peak peak = null;
        char stringQuote = '"';

        chan.write("save_" + getName() + "\n");

        chan.write("_Spectral_peak_list.Sf_category                 ");
        chan.write("spectral_peak_list\n");

        chan.write("_Spectral_peak_list.Sf_framecode                 ");
        chan.write(getName() + "\n");

        chan.write("_Spectral_peak_list.ID                          ");
        chan.write(listNum + "\n");

        chan.write("_Spectral_peak_list.Data_file_name               ");
        chan.write(".\n");

        chan.write("_Spectral_peak_list.Sample_ID                   ");
        chan.write(".\n");

        chan.write("_Spectral_peak_list.Sample_label                 ");
        if (sampleLabel.length() != 0) {
            chan.write("$" + sampleLabel + "\n");
        } else {
            chan.write(".\n");
        }

        chan.write("_Spectral_peak_list.Sample_condition_list_ID     ");
        chan.write(".\n");

        chan.write("_Spectral_peak_list.Sample_condition_list_label  ");
        if (sampleConditionLabel.length() != 0) {
            chan.write("$" + sampleConditionLabel + "\n");
        } else {
            chan.write(".\n");
        }

        chan.write("_Spectral_peak_list.Experiment_ID                 ");
        chan.write(".\n");

        chan.write("_Spectral_peak_list.Experiment_name               ");
        if (fileName.length() != 0) {
            chan.write("$" + fileName + "\n");
        } else {
            chan.write(".\n");
        }

        chan.write("_Spectral_peak_list.Number_of_spectral_dimensions ");
        chan.write(String.valueOf(nDim) + "\n");

        chan.write("_Spectral_peak_list.Details                       ");
        if (details.length() != 0) {
            chan.write(stringQuote + details + stringQuote + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("\n");

        String loopStrings[] = SpectralDim.getSTAR3LoopStrings();
        chan.write("loop_\n");
        for (int j = 0; j < loopStrings.length; j++) {
            chan.write(loopStrings[j] + "\n");
        }
        chan.write("\n");
        for (int j = 0; j < spectralDims.length; j++) {
            chan.write(spectralDims[j].toSTAR3LoopPeakCharString() + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");

        int nPeaks = peaks.size();

        loopStrings = Peak.getSTAR3Strings();
        chan.write("loop_\n");
        for (int j = 0; j < loopStrings.length; j++) {
            chan.write(loopStrings[j] + "\n");
        }
        chan.write("\n");
        for (int i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toSTAR3LoopPeakString() + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        loopStrings = Peak.getSTAR3GeneralCharStrings();

        chan.write("loop_\n");
        for (int j = 0; j < loopStrings.length; j++) {
            chan.write(loopStrings[j] + "\n");
        }
        chan.write("\n");
        for (int i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);

            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toSTAR3LoopIntensityString(0) + "\n");
            chan.write(peak.toSTAR3LoopIntensityString(1) + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        loopStrings = Peak.getSTAR3CharStrings();
        chan.write("loop_\n");
        for (int j = 0; j < loopStrings.length; j++) {
            chan.write(loopStrings[j] + "\n");
        }
        chan.write("\n");
        for (int i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);

            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim peakDims[] = peak.getPeakDims();
            for (int j = 0; j < peakDims.length; j++) {
                chan.write(peakDims[j].toSTAR3LoopPeakCharString(0) + "\n");
            }
        }
        chan.write("stop_\n");
        loopStrings = Peak.assignedPeakChemShiftStrings;
        chan.write("loop_\n");
        for (int j = 0; j < loopStrings.length; j++) {
            chan.write(loopStrings[j] + "\n");
        }
        chan.write("\n");
        int iContrib = 0;
        for (int i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);

            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim peakDims[] = peak.getPeakDims();
            for (int j = 0; j < peakDims.length; j++) {
                PeakDim peakDim = peakDims[j];
                ArrayList resonanceList = peakDim.getResonances();
                for (int k = 0; k < resonanceList.size(); k++) {
                    Resonance resonance = (Resonance) resonanceList.get(k);
                    long resID = 0;
                    if (resonance != null) {
                        resID = resonance.getID();
                    }
                    chan.write(peakDim.toSTAR3LoopAssignedPeakChemShiftString(iContrib++, resID) + "\n");
                }
            }
        }
        chan.write("stop_\n");
        chan.write("\nsave_\n\n");

    }

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

    public void writePeaksXPK(FileWriter chan)
            throws IOException, IllegalArgumentException, InvalidPeakException {
        int i;
        Peak peak = null;

        if (chan == null) {
            throw new IllegalArgumentException("Channel null");
        }

        chan.write(getXPKHeader());

        int nPeaks = peaks.size();

        for (i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);

            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }

            chan.write(peak.toXPKString() + "\n");
        }
    }

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
            result.append(dimName).append(".J").append(sep);
            result.append(dimName).append(".U").append(sep);
            result.append(dimName).append(".r").append(sep);
        }
        result.append("volume").append(sep);
        result.append("intensity").append(sep);
        result.append("status").append(sep);
        result.append("comment").append(sep);
        result.append("flags").append(sep);

        return (result.toString().trim());
    }

    public void writePeaksXPK2(FileWriter chan) throws IOException, InvalidPeakException {
        chan.write("peaklist\tdataset\tndim\n");
        chan.write(getName() + "\t" + getDatasetName() + "\t" + nDim + "\n");

        for (int j = 0; j < xpkPeakDimStrings.length; j++) {
            if (j > 0) {
                chan.write("\t");
            }
            chan.write(xpkPeakDimStrings[j]);
        }
        chan.write("\n");
//     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true
//     2   ppm   1H    500.13   10.986153600089578   10.393076800044788   circular   true   false
//     3   ppm   15N   50.666   24.002901353965186   128.00145067698259   circular   true   false

        for (int j = 0; j < spectralDims.length; j++) {
            chan.write(spectralDims[j].toXPK2Dim() + "\n");
        }

        chan.write(getXPK2Header());
        chan.write("\n");

        int nPeaks = peaks.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peaks.get(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toXPK2String(i) + "\n");
        }
    }

    /*
    dataset ndim
C_nhsqcsegr_b.nv        2
id      label   units   sf      sw      fp      idtol   pattern relation        folding abspos  acqdim
1       HN"ppm  499.83770751953125      2617.1875       0.0     0.007815036643363612                    0.0     circular        true    true
2       N"ppm   50.653602600097656      2000.0  0.0     0.15423384712985722                     0.0     circular        true    false
index   id      HN.L    HN.P    HN.WH   HN.B    HN.E    HN.J    HN.U    N.L     N.P     N.WH    N.B     N.E     N.J     N.U     volume  intensity       status  comment flags
0       0               8.94238 0.03142 0.03220 ++                              132.96933       0.39033 0.40230 ++                      0.0     1.5329096       0               0

     */
    public static Map<String, Integer> headerMap(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            map.put(header[i], i);
        }
        return map;
    }

    public static PeakList readXPK2Peaks(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.lastIndexOf('.'));

        boolean gotHeader = false;
        String[] dataHeader = null;
        Map<String, Integer> dataMap = null;
        PeakList peakList = null;
        String units = "ppm";
        try (BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.charAt(0) == '#') {
                    continue;
                }
                if (peakList == null) {
                    if (line.contains("ndim")) {
                        String[] header = line.split("\t");
                        Map<String, Integer> map = headerMap(header);
                        String lineData = fileReader.readLine();
                        String[] data = lineData.split("\t");
                        int nDim = Integer.valueOf(data[map.get("ndim")]);
                        String listName = fileTail;
                        if (map.get("peaklist") != null) {
                            listName = data[map.get("peaklist")];
                        }
                        peakList = new PeakList(listName, nDim);
                        if (map.get("dataset") != null) {
                            peakList.setDatasetName(data[map.get("dataset")]);
                        }
                    }
                } else {
                    if (!gotHeader) {
                        String[] header = line.split("\t");
                        Map<String, Integer> map = headerMap(header);
                        for (int i = 0; i < peakList.nDim; i++) {
                            String lineData = fileReader.readLine();
                            String[] data = lineData.split("\t");
                            SpectralDim sDim = peakList.getSpectralDim(i);
                            for (String field : header) {
                                String value = data[map.get(field)];
                                switch (field) {
                                    case "label":
                                        sDim.setDimName(value);
                                        break;
                                    case "code":
                                        sDim.setNucleus(value);
                                        break;
                                    case "sf":
                                        sDim.setSf(Double.valueOf(value));
                                        break;
                                    case "sw":
                                        sDim.setSw(Double.valueOf(value));
                                        break;
                                    case "fp":
                                        sDim.setRef(Double.valueOf(value));
                                        break;
                                    case "idtol":
                                        sDim.setIdTol(Double.valueOf(value));
                                        break;
                                    case "pattern":
                                        sDim.setPattern(value);
                                        break;
                                    case "bonded":
                                        sDim.setRelation(value);
                                        break;
                                    case "spatial":
                                        sDim.setSpatialRelation(value);
                                        break;
                                    case "acqdim":
                                        sDim.setAcqDim(Boolean.valueOf(value));
                                        break;
                                    case "abspos":
                                        sDim.setAbsPosition(Boolean.valueOf(value));
                                        break;
                                    case "folding":
                                        sDim.setNEFAliasing(value);
                                        break;
                                    case "units":
                                        units = value;
                                        break;
                                    default:
                                        throw new IllegalArgumentException("Unknown field " + field);
                                }
                            }
                        }
                        gotHeader = true;
                    } else {
                        if (dataMap == null) {
                            dataHeader = line.split("\t");
                            dataMap = headerMap(dataHeader);
                        } else {
                            String[] data = line.split("\t");
                            Peak peak = peakList.getNewPeak();
                            for (String field : dataHeader) {
                                int dotIndex = field.indexOf('.');
                                if (dotIndex != -1) {
                                    Integer dataIndex = dataMap.get(field);
                                    String dimLabel = field.substring(0, dotIndex);
                                    field = field.substring(dotIndex + 1);
                                    PeakDim peakDim = peak.getPeakDim(dimLabel);
                                    if (dataIndex != null) {
                                        String value = data[dataIndex];
                                        switch (field) {
                                            case "L":
                                                List<String> labelList = Arrays.asList(value.split(" "));
                                                peakDim.setLabel(labelList);
                                                break;
                                            case "P":
                                                peakDim.setChemShiftValue(Float.valueOf(value));
                                                break;
                                            case "W":
                                                peakDim.setLineWidthValue(Float.valueOf(value));
                                                break;
                                            case "WH":
                                                peakDim.setLineWidthValue(Float.valueOf(value) / (float) peakDim.getSpectralDimObj().getSf());
                                                break;
                                            case "B":
                                                peakDim.setBoundsValue(Float.valueOf(value));
                                                break;
                                            case "BH":
                                                peakDim.setBoundsValue(Float.valueOf(value) / (float) peakDim.getSpectralDimObj().getSf());
                                                break;
                                            case "J":
                                                // fixme
                                                break;
                                            case "E":
                                                peakDim.setError(value);
                                                break;
                                            case "U":
                                                peakDim.setUser(value);
                                                break;
                                            case "r":
                                                long resNum = Long.valueOf(value);
                                                break;
                                            default:
                                                throw new IllegalArgumentException("Unknown field " + field);
                                        }
                                    }
                                } else {
                                    Integer dataIndex = dataMap.get(field);
                                    //   id      HN.L    HN.P    HN.WH   HN.B    HN.E    HN.J    HN.U
                                    // N.L     N.P     N.WH    N.B     N.E     N.J     N.U
                                    // volume  intensity       status  comment flags
                                    if (dataIndex != null) {
                                        String value = data[dataIndex];
                                        switch (field) {
                                            case "id":
                                                peak.setIdNum(Integer.valueOf(value));
                                                break;
                                            case "intensity":
                                                peak.setIntensity(Float.valueOf(value));
                                                break;
                                            case "volume":
                                                peak.setVolume1(Float.valueOf(value));
                                                break;
                                            case "status":
                                                peak.setStatus(Integer.valueOf(value));
                                                break;
                                            case "comment":
                                                peak.setComment(value);
                                                break;
                                            case "flags":
                                                peak.setFlag(value);
                                                break;
                                            default:
                                                throw new IllegalArgumentException("Unknown field " + field);
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
        return peakList;
    }

    public void writePeaks(FileWriter chan)
            throws IOException, IllegalArgumentException, InvalidPeakException {
        int i;
        Peak peak = null;

        if (chan == null) {
            throw new IllegalArgumentException("Channel null");
        }

        int nPeaks = peaks.size();

        for (i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);

            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }

            chan.write(peak.toMyString() + "\n");
        }
    }

    public void writePeaksToXML(FileWriter chan)
            throws IOException, IllegalArgumentException, InvalidPeakException {
        int i;
        Peak peak = null;
        if (chan == null) {
            throw new IllegalArgumentException("Channel null");
        }

        int nPeaks = peaks.size();

        for (i = 0; i < nPeaks; i++) {
            peak = peaks.get(i);

            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }

            chan.write(peak.toXMLString() + "\n");
        }
    }

    public List<Peak> findPeaks(double[] ppm)
            throws IllegalArgumentException {
        if (ppm.length != searchDims.size()) {
            throw new IllegalArgumentException("Search dimensions (" + ppm.length
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
            limits[i][1] = ppm[i] - tol;
            limits[i][0] = ppm[i] + tol;
            i++;
        }

        if (!matched) {
            throw new IllegalArgumentException("Peak Label doesn't match template label");
        }

        return (locatePeaks(limits, searchDim));
    }

    public void sortPeaks(int dim, boolean ascending) {
        sortPeaks(peaks, dim, ascending);
        reIndex();
    }

    public static void sortPeaks(final List<Peak> peaks, int iDim, boolean ascending) {
        if (ascending) {
            peaks.sort((Peak a, Peak b) -> compare(a.peakDim[iDim].getChemShift(), b.peakDim[iDim].getChemShift()));
        } else {
            // fixme
            peaks.sort((Peak a, Peak b) -> compare(a.peakDim[iDim].getChemShift(), b.peakDim[iDim].getChemShift()));

        }
    }

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

    public List<Peak> locatePeaks(double[][] limits, int[] dim, double[][] foldLimits) {
        List<PeakDistance> foundPeaks = new ArrayList<>();
//        final Vector peakDistance = new Vector();

        int i;
        int j;
        Peak peak = null;
        boolean ok = true;
        double ctr = 0.0;
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
            ok = true;

            double sumDistance = 0.0;

            for (j = 0; j < nSearchDim; j++) {
                if ((dim.length <= j) || (dim[j] == -1)) {
                    continue;
                }

                ctr = peak.peakDim[dim[j]].getChemShiftValue();
                if ((foldLimits != null) && (foldLimits[j] != null)) {
                    double fDelta = getFoldAmount(dim[j]);
                    fDelta = Math.abs(foldLimits[j][0] - foldLimits[j][1]);
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
                        Matcher matcher = patterns[k].matcher(peak.peakDim[k].getLabel().toUpperCase());
                        if (matcher.find()) {
                            ok = true;
                        }
                    } else if (Util.stringMatch(peak.peakDim[k].getLabel().toUpperCase(), simplePat[k])) {
                        ok = true;
                    } else if ((simplePat[k].length() == 0) && (peak.peakDim[k].getLabel().length() == 0)) {
                        ok = true;
                    }
                } else {
                    for (l = 0; l < nDim; l++) {
                        if (useRegExp) {
                            Matcher matcher = patterns[k].matcher(peak.peakDim[l].getLabel().toUpperCase());
                            if (matcher.find()) {
                                ok = true;
                                break;
                            }
                        } else if (Util.stringMatch(peak.peakDim[l].getLabel().toUpperCase(), simplePat[k])) {
                            ok = true;
                            break;
                        } else if ((simplePat[k].length() == 0) && (peak.peakDim[l].getLabel().length() == 0)) {
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

    public static Multiplet getAMultiplet(String peakSpecifier) throws IllegalArgumentException {
        int dot = peakSpecifier.indexOf('.');

        if (dot == -1) {
            return null;
        }

        PeakList peakList = (PeakList) peakListTable.get(peakSpecifier.substring(
                0, dot));

        if (peakList == null) {
            return null;
        }

        int idNum;

        try {

            idNum = Integer.parseInt(peakSpecifier.substring(dot + 1, peakSpecifier.length() - 1));

        } catch (NumberFormatException numE) {
            throw new IllegalArgumentException(
                    "error parsing peak " + peakSpecifier + ": " + numE.toString());
        }
        ArrayList<Multiplet> sMulti = peakList.getMultiplets();
        if (idNum >= sMulti.size()) {
            throw new IllegalArgumentException("Idnum to large for multiplets size");
        }
        return sMulti.get(idNum);
    }

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

        return peak.peakDim[iDim];
    }

    public Peak getPeakByID(int idNum) throws IllegalArgumentException {
        if (indexMap.isEmpty()) {
            reIndex();
        }
        Peak peak = indexMap.get(idNum);
        return peak;
    }

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

        Peak peak = (Peak) peakList.getPeak(idNum);

        return (peak);
    }

    public Peak getNewPeak() {
        Peak peak = new Peak(this, nDim);
        addPeak(peak);
        return peak;
    }

    public void addPeakWithoutResonance(Peak newPeak) {
        peaks.add(newPeak);
        clearIndex();
    }

    public void addPeak(Peak newPeak) {
        newPeak.initPeakDimContribs();
        peaks.add(newPeak);
        clearIndex();
    }

    public int addPeak() {
        Peak peak = new Peak(this, nDim);
        addPeak(peak);
        return (peak.getIdNum());
    }

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
            return ((Peak) null);
        }
    }

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
                    PhaseRelationship phaseRelTest = null;

                    if (!phaseRel.isSigned()) {
                        phaseRelTest = PhaseRelationship.getType(iPeak.getIntensity(), jPeak.getIntensity());
                    } else {
                        phaseRelTest = PhaseRelationship.getType(iPeak.peakDim[dimVal].getChemShiftValue(),
                                iPeak.getIntensity(), jPeak.peakDim[dimVal].getChemShiftValue(), jPeak.getIntensity());
                    }

                    if (phaseRelTest != phaseRel) {
                        continue;
                    }
                }

                boolean ok = true;
                double deltaMatch = 0.0;

                for (int iDim = 0; iDim < nDim; iDim++) {
                    double delta = Math.abs(iPeak.peakDim[iDim].getChemShiftValue()
                            - jPeak.peakDim[iDim].getChemShiftValue());

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
                    PeakDim iPDim = iPeak.peakDim[iDim];
                    PeakDim jPDim = jPeak.peakDim[iDim];

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

    public DistanceMatch[][] getNeighborDistances(double[] minTol,
            double[] maxTol) {
        final ArrayList matches = new ArrayList();

        double[] deltas = new double[nDim];
        DistanceMatch[][] dMatches = null;
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
                    deltas[iDim] = (iPeak.peakDim[iDim].getChemShiftValue()
                            - jPeak.peakDim[iDim].getChemShiftValue()) / maxTol[iDim];

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

    static class MatchItem {

        final int itemIndex;
        final double[] values;

        MatchItem(final int itemIndex, final double[] values) {
            this.itemIndex = itemIndex;
            this.values = values;
        }
    }

    ArrayList<MatchItem> getMatchingItems(PeakList peakList, int[] dims) {
        ArrayList<MatchItem> matchList = new ArrayList<MatchItem>();
        List<Peak> searchPeaks = peakList.peaks;

        Set<Peak> usedPeaks = searchPeaks.stream().filter(p -> p.getStatus() < 0).collect(Collectors.toSet());

        int j = -1;
        for (Peak peak : searchPeaks) {
            j++;
            if (usedPeaks.contains(peak)) {
                continue;
            }
            double[] values = new double[dims.length];
            for (int iDim = 0; iDim < dims.length; iDim++) {
                ArrayList<PeakDim> linkedPeakDims = getLinkedPeakDims(peak, dims[iDim]);
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

    ArrayList<MatchItem> getMatchingItems(double[][] positions) {
        ArrayList<MatchItem> matchList = new ArrayList<MatchItem>();
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

    private MatchResult doBPMatch(ArrayList<MatchItem> iMList, final double[] iOffsets, ArrayList<MatchItem> jMList, final double[] jOffsets, double[] tol) {
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
            MatchItem matchJ = null;
            if ((matching[i] >= 0) && (matching[i] < jMList.size())) {
                matchJ = jMList.get(matching[i]);
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

        ConvergenceChecker<PointValuePair> cCheck = new SimplePointChecker<PointValuePair>();

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

    public int clusterPeaks() throws IllegalArgumentException {
        List<PeakList> peakLists = new ArrayList<>();
        peakLists.add(this);
        return clusterPeaks(peakLists);

    }

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

    public static int clusterPeaks(List<PeakList> peakLists)
            throws IllegalArgumentException {
        Clusters clusters = new Clusters();
        ArrayList clustPeaks = new ArrayList();
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

                ArrayList linkedPeaks = getLinks(peak);

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
                        Peak peak2 = (Peak) linkedPeaks.get(iPeak);
                        peak2.setStatus(1);
                        datum.v[k] += peak2.peakDim[sDim.iDim].getChemShiftValue();
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
                            Peak iPeak = (Peak) clustPeaks.get(iDatum.idNum);
                            Peak jPeak = (Peak) clustPeaks.get(jDatum.idNum);
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

    public static ArrayList<Peak> getLinks(Peak peak, boolean requireSameList) {
        ArrayList<PeakDim> peakDims = getLinkedPeakDims(peak, 0);
        ArrayList<Peak> peaks = new ArrayList(peakDims.size());
        for (PeakDim peakDim : peakDims) {
            if (!requireSameList || (peakDim.myPeak.peakList == peak.peakList)) {
                peaks.add(peakDim.myPeak);
            }
        }
        return peaks;
    }

    public static ArrayList getLinks(Peak peak) {
        ArrayList peakDims = getLinkedPeakDims(peak, 0);
        ArrayList peaks = new ArrayList(peakDims.size());
        for (int i = 0; i < peakDims.size(); i++) {
            PeakDim peakDim = (PeakDim) peakDims.get(i);
            peaks.add(peakDim.myPeak);
        }
        return peaks;
    }

    public static ArrayList getLinks(final Peak peak, final int iDim) {
        final ArrayList peakDims = getLinkedPeakDims(peak, iDim);
        final ArrayList peaks = new ArrayList(peakDims.size());
        for (int i = 0; i < peakDims.size(); i++) {
            PeakDim peakDim = (PeakDim) peakDims.get(i);
            peaks.add(peakDim.myPeak);
        }
        return peaks;
    }

    public static ArrayList<PeakDim> getLinkedPeakDims(Peak peak) {
        return getLinkedPeakDims(peak, 0);
    }

    public static ArrayList<PeakDim> getLinkedPeakDims(Peak peak, int iDim) {
        PeakDim peakDim = peak.getPeakDim(iDim);
        return peakDim.getLinkedPeakDims();
    }

    public static void linkPeaks(Peak peakA, String dimA, Peak peakB, String dimB) {
        PeakDim peakDimA = peakA.getPeakDim(dimA);
        PeakDim peakDimB = peakB.getPeakDim(dimB);
        if ((peakDimA != null) && (peakDimB != null)) {
            linkPeakDims(peakDimA, peakDimB);
        }
    }

    public static void linkPeaks(Peak peakA, int dimA, Peak peakB, int dimB) {
        PeakDim peakDimA = peakA.getPeakDim(dimA);
        PeakDim peakDimB = peakB.getPeakDim(dimB);
        if ((peakDimA != null) && (peakDimB != null)) {
            linkPeakDims(peakDimA, peakDimB);
        }
    }
    // FIXME should check to see that nucleus is same

    public static void linkPeakDims(PeakDim peakDimA, PeakDim peakDimB) {
        ArrayList resonancesA = peakDimA.getResonances();
        ArrayList resonancesB = peakDimB.getResonances();
        if ((resonancesA.size() == 1) && (resonancesB.size() == 1)) {
            Resonance resonanceA = (Resonance) resonancesA.get(0);
            Resonance resonanceB = (Resonance) resonancesB.get(0);
            Resonance.merge(resonanceA, resonanceB);
        }

        peakDimA.peakDimUpdated();
        peakDimB.peakDimUpdated();
    }
    // FIXME should check to see that nucleus is same

    public static void couplePeakDims(PeakDim peakDimA, PeakDim peakDimB) {
        ArrayList resonancesA = peakDimA.getResonances();
        ArrayList resonancesB = peakDimB.getResonances();
        if ((resonancesA.size() == 1) && (resonancesB.size() == 1)) {
            Resonance resonanceA = (Resonance) resonancesA.get(0);
            Resonance resonanceB = (Resonance) resonancesB.get(0);
            Resonance.merge(resonanceA, resonanceB);
        }

        Multiplet.merge(peakDimA, peakDimB);
        peakDimA.peakDimUpdated();
        peakDimB.peakDimUpdated();
    }

    public void unLinkPeaks() {
        int nPeaks = peaks.size();

        for (int i = 0; i < nPeaks; i++) {
            unLinkPeak(peaks.get(i));
        }
    }

    public static void unLinkPeak(Peak peak) {
        for (int i = 0; i < peak.peakList.nDim; i++) {
            ArrayList<PeakDim> peakDims = getLinkedPeakDims(peak, i);
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

    public static void unLinkPeak(Peak peak, int iDim) {
        PeakDim peakDim = peak.getPeakDim(iDim);
        if (peakDim != null) {
            peakDim.unLink();
        }
    }

    public static boolean isLinked(Peak peak1, int dim1, Peak peak2) {
        // FIXME
        return false;
    }

    public static double fit(Dataset theFile, String[] argv,
            int start, int i0, int i1, int[] rows, boolean doFit, boolean linearFit)
            throws IllegalArgumentException, IOException, PeakFitException {
        int dataDim = theFile.getNDim();
        int[][] p1 = new int[dataDim][2];
        int[][] p2 = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];
        int nPeaks = argv.length - start;
        double result = 0.0;

        //double guesses[] = new double[3*nPeaks];
        double[] guesses = new double[(2 * nPeaks) + 1];

        if (i0 > i1) {
            int hold = i0;
            i0 = i1;
            i1 = hold;
        }

        //int k=0;
        int k = 1;
        int[] pdim = new int[dataDim];
        Peak[] peaks = new Peak[nPeaks];
        for (int iArg = start, iPeak = 0; iArg < argv.length;
                iArg++, iPeak++) {
            peaks[iPeak] = getAPeak(argv[iArg]);

            if (peaks[iPeak] == null) {
                throw new IllegalArgumentException(
                        "Couln't find peak \"" + argv[iArg] + "\"");
            }
        }

        double lwSum = 0.0;

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            if (dataDim < peaks[iPeak].peakList.nDim) {
                throw new IllegalArgumentException(
                        "Number of peak list dimensions greater than number of dataset dimensions");
            }

            for (int j = 0; j < peaks[iPeak].peakList.nDim; j++) {
                boolean ok = false;

                for (int i = 0; i < dataDim; i++) {
                    if (peaks[iPeak].peakList.getSpectralDim(j).getDimName().equals(
                            theFile.getLabel(i))) {
                        pdim[j] = i;
                        ok = true;

                        break;
                    }
                }

                if (!ok) {
                    throw new IllegalArgumentException(
                            "Can't find match for peak dimension \""
                            + peaks[iPeak].peakList.getSpectralDim(j).getDimName() + "\"");
                }
            }
            int nextDim = peaks[iPeak].peakList.nDim;
            for (int i = 0; i < dataDim; i++) {
                boolean gotThisDim = false;
                for (int j = 0; j < pdim.length; j++) {
                    if (pdim[j] == i) {
                        gotThisDim = true;
                        break;
                    }
                }
                if (!gotThisDim) {
                    pdim[nextDim] = i;
                    p2[nextDim][0] = rows[i];
                    p2[nextDim][1] = rows[i];
                }
            }

            peaks[iPeak].getPeakRegion(theFile, pdim, p1, cpt, width);
            int cw0 = (int) (cpt[0] - Math.abs(width[0]) - 1);
            int cw1 = (int) (cpt[0] + Math.abs(width[0]) + 1);

            if (cw0 < 0) {
                cw0 = 0;
            }

            if (cw1 >= theFile.getSize(pdim[0])) {
                cw1 = theFile.getSize(pdim[0]) - 1;
            }

            if (iPeak == 0) {
                p2[0][0] = cw0;
                p2[0][1] = cw1;
            } else {
                if (cw0 < p2[0][0]) {
                    p2[0][0] = cw0;
                }

                if (cw1 > p2[0][1]) {
                    p2[0][1] = cw1;
                }
            }

            double c = theFile.ppmToDPoint(0, peaks[iPeak].peakDim[0].getChemShiftValue());
            double c1 = theFile.ppmToDPoint(0,
                    peaks[iPeak].peakDim[0].getChemShiftValue()
                    + peaks[iPeak].peakDim[0].getLineWidthValue());
            guesses[k++] = peaks[iPeak].getIntensity();
            guesses[k++] = c;
            lwSum += Math.abs(c1 - c);
        }
//System.out.println(p2[0][0]+" "+i0+" "+p2[0][1]+" "+i1);
        if (p2[0][0] < i0) {
            p2[0][0] = i0;
        }

        if (p2[0][1] > i1) {
            p2[0][1] = i1;
        }

        guesses[0] = lwSum / nPeaks;

        //k = 1;
        k = 2;

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            guesses[k] = guesses[k] - p2[0][0];

            //k += 3;
            k += 2;
        }

        int size = p2[0][1] - p2[0][0] + 1;
        Vec fitVec = new Vec(size);
        theFile.readVectorFromDatasetFile(p2, pdim, fitVec);

        //LmdifTest_f77  lmdifTest = new LmdifTest_f77();
        Lmder_f77 lmdifTest = new Lmder_f77();
        double[] values = null;

        //int extra = size/2;
        int extra = 0;
        lmdifTest.setLengths(size + (2 * extra));
        values = lmdifTest.getArray(0);

        for (int j = 0; j < (size + (2 * extra)); j++) {
            values[j] = j - extra;
        }

        values = lmdifTest.getArray(1);

        for (int j = 0; j < (size + (2 * extra)); j++) {
            values[j] = 0.0;
        }

        for (int j = 0; j < size; j++) {
            values[j + extra] = fitVec.getReal(j);
        }

        //lmdifTest.setFunc(8);
        lmdifTest.setFunc(11);
        lmdifTest.setN(guesses.length);

        //lmdifTest.initpt();
        lmdifTest.initpt0offset(guesses);

        if (!doFit) {
            double rms = lmdifTest.rms();
            result = rms;
        } else {
            int[] map = new int[nPeaks + 1];
            map[0] = 0;

            for (int j = 0; j < nPeaks; j++) {
                map[j + 1] = (2 * j) + 2;
            }

            lmdifTest.setMap(map);

            if (linearFit) {
                lmdifTest.doLinearNN();
            } else {
                lmdifTest.doMin();
            }

            double rms = lmdifTest.rms();
            result = rms;

            double[] pars = lmdifTest.getPars();

            //k=1;
            k = 2;

            for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
                k++;

                double newC = pars[k++];

                if ((newC < 0.0) || (newC > size)) {
                    throw new PeakFitException("fit failed for peak "
                            + argv[start + iPeak].toString() + " " + iPeak + " "
                            + newC);
                }
            }

            k = 2;

            for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
                peaks[iPeak].setIntensity((float) pars[k++]);

                double newC = pars[k++];

                // double w = pars[k++];
                double w = Math.abs(pars[1]);

                if ((newC < 0.0) || (newC > size)) {
                    continue;
                }

                double c = newC + p2[0][0];
                double c1 = w + c;
                peaks[iPeak].peakDim[0].setChemShiftValueNoCheck((float) theFile.pointToPPM(
                        0, c));
                peaks[iPeak].peakDim[0].setLineWidthValue((float) Math.abs(theFile.pointToPPM(
                        0, c1) - peaks[iPeak].peakDim[0].getChemShiftValue()));
                float lineWidth = peaks[iPeak].peakDim[0].getLineWidthValue();
                peaks[iPeak].peakDim[0].setBoundsValue((float) lineWidth * 3);
                peaks[iPeak].setVolume1((float) (peaks[iPeak].getIntensity() * peaks[iPeak].peakDim[0].getLineWidthValue() * Math.PI / 2 / 1.05));

                //peaks[iPeak].flag[4] = true;
            }
        }
        return result;
    }

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

    public static double jfit(Dataset theFile, String[] argv,
            int start, int i0, int i1, int[] rows, boolean doFit, boolean rootedPeaks, int fitMode)
            throws IllegalArgumentException, IOException {
        int dataDim = theFile.getNDim();
        int[] pdim = new int[dataDim];
        int[][] p1 = new int[dataDim][2];
        int[][] p2 = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];
        int[] dim = new int[dataDim];
        double[] plane = {0.0, 0.0};
        int nPeaks = argv.length - start;

        ArrayList guessList = new ArrayList();

        //int k=0;
        Peak[] peaks = new Peak[nPeaks];

        if (i0 > i1) {
            int hold = i0;
            i0 = i1;
            i1 = hold;
        }
        boolean fixWeakDoublet = true;
        for (int iArg = start, iPeak = 0; iArg < argv.length;
                iArg++, iPeak++) {
            peaks[iPeak] = getAPeak(argv[iArg]);

            if (peaks[iPeak] == null) {
                throw new IllegalArgumentException(
                        "Couln't find peak \"" + argv[iArg] + "\"");
            }
        }

        int[][] splitCount = new int[nPeaks][];
        for (int i = 0; i < dataDim; i++) {
            pdim[i] = -1;
        }

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            if (dataDim < peaks[iPeak].peakList.nDim) {
                throw new IllegalArgumentException(
                        "Number of peak list dimensions greater than number of dataset dimensions");
            }

            for (int j = 0; j < peaks[iPeak].peakList.nDim; j++) {
                boolean ok = false;

                for (int i = 0; i < dataDim; i++) {
                    if (peaks[iPeak].peakList.getSpectralDim(j).getDimName().equals(
                            theFile.getLabel(i))) {
                        pdim[j] = i;
                        ok = true;

                        break;
                    }
                }

                if (!ok) {
                    throw new IllegalArgumentException(
                            "Can't find match for peak dimension \""
                            + peaks[iPeak].peakList.getSpectralDim(j).getDimName() + "\"");
                }
            }
            int nextDim = peaks[iPeak].peakList.nDim;
            for (int i = 0; i < dataDim; i++) {
                boolean gotThisDim = false;
                for (int j = 0; j < pdim.length; j++) {
                    if (pdim[j] == i) {
                        gotThisDim = true;
                        break;
                    }
                }
                if (!gotThisDim) {
                    pdim[nextDim] = i;
                    p2[nextDim][0] = rows[i];
                    p2[nextDim][1] = rows[i];
                }
            }

            rootedPeaks = true;

            ArrayList linkedPeaks2 = getLinks(peaks[iPeak]);

            if (rootedPeaks && (linkedPeaks2.size() < 1)) {
                continue;
            }

            peaks[iPeak].getPeakRegion(theFile, pdim, p1, cpt, width);
            double c = theFile.ppmToDPoint(0, peaks[iPeak].peakDim[0].getMultiplet().getCenter());

            //   double c = theFile.ppmToDPoint(0, peaks[iPeak].peakDim[0].getChemShiftValue());
            double c1 = theFile.ppmToDPoint(0, peaks[iPeak].peakDim[0].getMultiplet().getCenter() + peaks[iPeak].peakDim[0].getLineWidthValue());
            //System.out.println("lw "+c+" "+c1+" "+(c1-c));
            guessList.add(new Double(Math.abs(c1 - c)));
            guessList.add(new Double(c));

            int pEdge0 = (int) (c - width[0] - 1);
            int pEdge1 = (int) (c + width[0] + 1);
            splitCount[iPeak] = new int[0];

            if (rootedPeaks) {
                Coupling coupling = peaks[iPeak].peakDim[0].getMultiplet().getCoupling();
                if (coupling instanceof CouplingPattern) {
                    CouplingPattern cPat = (CouplingPattern) coupling;
                    int nCouplings = cPat.getNCouplingValues();
                    splitCount[iPeak] = cPat.getNValues();

                    for (int iCoup = 0; iCoup < nCouplings; iCoup++) {
                        double cVal = cPat.getValueAt(iCoup);
                        int nVal = cPat.getNValue(iCoup);
                        nVal++;
                        splitCount[iPeak][iCoup] = nVal;
                        pEdge0 -= (int) ((nVal * theFile.hzWidthToPoints(0, cVal)) / 2);
                        pEdge1 += (int) ((nVal * theFile.hzWidthToPoints(0, cVal)) / 2);
                        guessList.add(new Double(theFile.hzWidthToPoints(0, cVal)));
                    }
                } else if (coupling instanceof ComplexCoupling) {
                    ComplexCoupling cCoup = (ComplexCoupling) coupling;
                    Multiplet multiplet = peaks[iPeak].peakDim[0].getMultiplet();
                    int nFreqs = cCoup.getFrequencyCount();
                    splitCount[iPeak] = new int[1];
                    splitCount[iPeak][0] = -nFreqs;
                    guessList.remove(guessList.size() - 1);

                    for (int iFreq = 0; iFreq < nFreqs;
                            iFreq++) {
                        double dw = theFile.hzWidthToPoints(0, cCoup.getFrequencyOffset(iFreq));
                        int cw0 = (int) ((c + dw) - Math.abs(width[0]) - 1);
                        int cw1 = (int) (c + dw + Math.abs(width[0]) + 1);

                        if (cw0 < pEdge0) {
                            pEdge0 = cw0;
                        }

                        if (cw1 > pEdge1) {
                            pEdge1 = cw1;
                        }

                        guessList.add(new Double(c + dw));
                    }
                }
            } else {
                ArrayList linkedPeaks = getLinks(peaks[iPeak]);

                if (linkedPeaks.size() > 1) {
                    splitCount[iPeak] = new int[1];
                    splitCount[iPeak][0] = -linkedPeaks.size();

                    for (int iLink = 1; iLink < linkedPeaks.size(); iLink++) {
                        Peak lPeak = (Peak) linkedPeaks.get(iLink);
                        c = theFile.ppmToDPoint(0, lPeak.peakDim[0].getChemShiftValue());

                        int cw0 = (int) (c - Math.abs(width[0]) - 1);
                        int cw1 = (int) (c + Math.abs(width[0]) + 1);

                        if (cw0 < pEdge0) {
                            pEdge0 = cw0;
                        }

                        if (cw1 > pEdge1) {
                            pEdge1 = cw1;
                        }

                        guessList.add(new Double(c));
                    }
                }
            }

            if (pEdge0 < i0) {
                pEdge0 = i0;
            }

            if (pEdge1 > i1) {
                pEdge1 = i1;
            }

            if (iPeak == 0) {
                p2[0][0] = pEdge0;
                p2[0][1] = pEdge1;
            } else {
                if (pEdge0 < p2[0][0]) {
                    p2[0][0] = pEdge0;
                }

                if (pEdge1 > p2[0][1]) {
                    p2[0][1] = pEdge1;
                }
            }
        }
        if (fitMode == FIT_MAX_DEV) {
            if (p2[0][0] > i0) {
                p2[0][0] = i0;
            }
            if (p2[0][1] < i1) {
                p2[0][1] = i1;
            }

            if (p2[0][0] < 0) {
                p2[0][0] = 0;
            }
        }

        if (p2[0][1] >= theFile.getSize(pdim[0])) {
            p2[0][1] = theFile.getSize(pdim[0]) - 1;
        }

        int iGuess = 0;
        double[] guesses = new double[guessList.size()];

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            guesses[iGuess] = ((Double) guessList.get(iGuess)).doubleValue();
            iGuess++;

            if ((splitCount[iPeak].length == 1) && (splitCount[iPeak][0] < 0)) { // generic multiplet

                int nFreq = -splitCount[iPeak][0];
                for (int iFreq = 0; iFreq < nFreq; iFreq++) {
                    guesses[iGuess] = ((Double) guessList.get(iGuess)).doubleValue()
                            - p2[0][0];
                    iGuess++;
                }
            } else {
                guesses[iGuess] = ((Double) guessList.get(iGuess)).doubleValue()
                        - p2[0][0];
                iGuess++;

                int nCouplings = splitCount[iPeak].length;

                for (int iCoupling = 0; iCoupling < nCouplings; iCoupling++) {
                    guesses[iGuess] = ((Double) guessList.get(iGuess)).doubleValue();
                    iGuess++;
                }
            }
            ArrayList linkedPeaks = getLinks(peaks[iPeak]);
            for (int jPeak = 0; jPeak < linkedPeaks.size(); jPeak++) {
                Peak lPeak = (Peak) linkedPeaks.get(jPeak);
                if (lPeak.getFlag(5)) {
                    fixWeakDoublet = false;
                }
            }
        }

        int size = p2[0][1] - p2[0][0] + 1;

        if (size <= 0) {
            throw new IllegalArgumentException("Invalid point range in jfit");
        }

        Vec fitVec = new Vec(size);
        theFile.readVectorFromDatasetFile(p2, pdim, fitVec);

        LorentzIWJ_f77 fcn = new LorentzIWJ_f77();
        CouplingItem[][] cplItems = new CouplingItem[splitCount.length][];
        for (int iSplit = 0; iSplit < splitCount.length; iSplit++) {
            cplItems[iSplit] = new CouplingItem[splitCount[iSplit].length];
            for (int jSplit = 0; jSplit < splitCount[iSplit].length; jSplit++) {
                cplItems[iSplit][jSplit] = new CouplingItem(0.0, splitCount[iSplit][jSplit]);
            }
        }
        fcn.setSignals(cplItems);

        int extra = 10;
        int nFitPoints = (size + (2 * extra));
        fcn.xv = new double[nFitPoints];
        fcn.yv = new double[nFitPoints];

        for (int j = 0; j < (size + (2 * extra)); j++) {
            fcn.xv[j] = j - extra;
        }

        for (int j = 0; j < (size + (2 * extra)); j++) {
            fcn.yv[j] = 0.0;
        }

        for (int j = 0; j < size; j++) {
            fcn.yv[j + extra] = fitVec.getReal(j);
        }
        double rms = 0.0;
        Minimizer minimizer = new Minimizer(fcn);
        minimizer.initpt(guesses);
        fcn.setFixWeakDoublet(fixWeakDoublet);
        if (fitMode == FIT_LW_AMPLITUDES) {
            fcn.initLWAmpFit(guesses);
            minimizer.initpt0(guesses, fcn.map);
        }
        double result;
        if (fitMode == FIT_RMS) {
            fcn.fcnAmps();
            rms = fcn.rms();
            result = rms;
            return result;
        } else if (fitMode == FIT_MAX_DEV) {
            fcn.fcnAmps();
            int maxDev = fcn.maxPosDev();

            double maxDevFreq = theFile.pointToPPM(0, maxDev + p2[0][0]);
            result = rms;
            return result;
        } else if (fitMode == FIT_AMPLITUDES) {
            fcn.fcnAmps();
            rms = fcn.rms();
        } else {
            minimizer.xv = fcn.xv;
            minimizer.yv = fcn.yv;
            minimizer.doMin();
            rms = minimizer.rms();
        }

        result = rms;
        ArrayList signalGroups = fcn.getSignals();

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {

            ArrayList linkedPeaks = getLinks(peaks[iPeak]);

            if (rootedPeaks && (linkedPeaks.size() < 1)) {
                continue;
            }

            ArrayList signals = (ArrayList) signalGroups.get(iPeak);
            double w = fcn.getLinewidth(iPeak);
            double lineWidth = theFile.ptWidthToPPM(0, w);
            //System.out.println(theFile.getName());
            //System.out.println(iPeak+" "+w+" "+lineWidth+" "+signals.size());
            peaks[iPeak].peakDim[0].setLineWidthValue((float) lineWidth);
            peaks[iPeak].peakDim[0].setBoundsValue((float) lineWidth * 3);

            int nFreqs = signals.size();

            if (rootedPeaks) {
                int nExtra = nFreqs - (linkedPeaks.size());

                if (nExtra < 0) {
                    throw new IllegalArgumentException("negative nExtra in jfit nFreqs: " + nFreqs + "nPeaks: " + linkedPeaks.size());
                }

                if (nExtra > 0) {
                    trimFreqs(signals, nExtra);
                }

                nFreqs -= nExtra;
            }

            double[] amplitudes = new double[nFreqs];
            double[] freqs = new double[nFreqs];
            boolean ok = true;

            for (int i = 0; i < signals.size(); i++) {
                SineSignal signal = (SineSignal) signals.get(i);
                amplitudes[i] = signal.getAmplitude();
                freqs[i] = signal.getFreq();

                if ((freqs[i] < 0.0) || (freqs[i] > size)) {
                    ok = false;
                    System.out.println("invalid frequency");

                    break;
                }
            }

            if (!ok) {
                continue;
            }

            if (rootedPeaks) {
                if ((splitCount[iPeak].length == 1)
                        && (splitCount[iPeak][0] < 0)) { // generic multiplet

                    for (int iFreq = 0; iFreq < freqs.length; iFreq++) {
                        double delta = freqs[iFreq] - fcn.getCFreq(iPeak);
                        freqs[iFreq] = theFile.ptWidthToHz(0, delta);
                    }
                    peaks[iPeak].peakDim[0].getMultiplet().setCoupling(freqs, amplitudes);

                } else {
                    CouplingItem[] cplItems2 = fcn.getCouplings(iPeak);
                    double[] couplings = new double[cplItems2.length];
                    for (int iCoup = 0; iCoup < couplings.length; iCoup++) {
                        couplings[iCoup] = theFile.ptWidthToHz(0, cplItems2[iCoup].getCoupling());
                    }
                    peaks[iPeak].peakDim[0].getMultiplet().setCoupling(couplings, amplitudes);
                }
                peaks[iPeak].peakDim[0].getMultiplet().setMultipletComponentValues();

                peaks[iPeak].peakDim[0].getMultiplet().setCenter(theFile.pointToPPM(0,
                        fcn.getCFreq(iPeak) + p2[0][0]));
                for (int jPeak = 0; jPeak < linkedPeaks.size(); jPeak++) {
                    Peak lPeak = (Peak) linkedPeaks.get(jPeak);
                    lPeak.peakDim[0].setLineWidthValue((float) lineWidth);
                    lPeak.peakDim[0].setBoundsValue((float) lineWidth * 3);
                    lPeak.setFlag(4, true);
                }

                //peaks[iPeak].peakDim[0].setMultipletComponentValues();
            } else {
                for (int iFreq = 0; iFreq < nFreqs; iFreq++) {
                    Peak lPeak = (Peak) linkedPeaks.get(iFreq);
                    SineSignal signal = (SineSignal) signals.get(iFreq);
                    lPeak.peakDim[0].setChemShiftValueNoCheck((float) theFile.pointToPPM(
                            0, signal.getFreq() + p2[0][0]));
                    lPeak.peakDim[0].setLineWidthValue((float) lineWidth);
                    lPeak.peakDim[0].setBoundsValue((float) lineWidth * 3);
                    lPeak.setIntensity((float) signal.getAmplitude());
                    lPeak.setVolume1((float) (lPeak.getIntensity() * lineWidth * (Math.PI / 2.0) / 1.05));
                    lPeak.setFlag(4, true);
                }
            }
        }
        return result;
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

    public boolean inEllipse(final int pt[], final int cpt[], final double[] width) {
        double r2 = 0.0;
        boolean inEllipse = false;
        int nDim = cpt.length;
        for (int ii = 0; ii < nDim; ii++) {
            int delta = Math.abs(pt[ii] - cpt[ii]);
            r2 += (delta * delta) / (width[ii] * width[ii]);
        }
        if (r2 < 1.0) {
            inEllipse = true;
        }
        return inEllipse;
    }

    public void quantifyPeaks(String mode) {
        if (peaks.isEmpty()) {
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

        peaks.stream().forEach(peak -> {
            try {
                peak.quantifyPeak(dataset, f, mode);
            } catch (IOException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        });
    }

    public void measurePeaks(String mode) {
        if (peaks.isEmpty()) {
            return;
        }
        Dataset dataset = Dataset.getDataset(fileName);
        if (dataset == null) {
            throw new IllegalArgumentException("No dataset for peak list");
        }
        measurePeaks(dataset, peaks, mode);
    }

    public void measurePeaks(Dataset dataset, List<Peak> speaks, String mode) {
        if (speaks.isEmpty()) {
            return;
        }
        final java.util.function.Function<RegionData, Double> f = Peak.getMeasureFunction(mode);
        if (f == null) {
            throw new IllegalArgumentException("Unknown measurment type: " + mode);
        }
        int[] planes;
        Peak firstPeak = speaks.get(0);
        int nPeakDim = firstPeak.getPeakList().nDim;
        int nDataDim = dataset.getNDim();
        final int nPlanes;
        if (nPeakDim > nDataDim) {
            throw new IllegalArgumentException("Peak list has more dimensions than dataset");
        } else if (nPeakDim < (nDataDim - 1)) {
            throw new IllegalArgumentException("Dataset has more than one extra dimension (relative to peak list)");
        } else {
            planes = new int[nDataDim - nPeakDim];
            int scanDim = 2;
            if (planes.length == 0) {
                nPlanes = 1;
            } else {
                nPlanes = dataset.getSize(scanDim);
            }
        }
        if (nPlanes == 1) {
            List<Double> result = new ArrayList<>();
            speaks.stream().forEach(peak -> {
                for (int i = 0; i < nPlanes; i++) {
                    if (planes.length == 1) {
                        planes[0] = i;
                    }
                    try {
                        double value = peak.measurePeak(dataset, planes, f);
                        result.add(value);
                    } catch (IOException ex) {
                        result.add(null);
                    }
                }
            });
        } else {
            List<Double> result = new ArrayList<>();
            speaks.stream().forEach(peak -> {
                try {
                    double value = peak.measurePeak(dataset, planes, f);
                    result.add(value);
                } catch (IOException ex) {
                    result.add(null);
                }
            });

        }
    }

    public void tweakPeaks(Dataset dataset, List<Peak> speaks) {
        speaks.stream().forEach(peak -> {
            try {
                peak.tweak(dataset);
            } catch (IOException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        });

    }

    public void tweakPeaks(Dataset dataset) {
        peaks.stream().forEach(peak -> {
            try {
                peak.tweak(dataset);
            } catch (IOException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        });

    }

    public static List<Object> peakFit(Dataset theFile, Peak... peakArray)
            throws IllegalArgumentException, IOException, PeakFitException {
        boolean doFit = true;
        int fitMode = FIT_ALL;
        boolean updatePeaks = true;
        double[] delays = null;
        double multiplier = 0.686;
        int[] rows = new int[theFile.getNDim()];
        List<Peak> peaks = Arrays.asList(peakArray);
        return peakFit(theFile, peaks, rows, doFit, fitMode, updatePeaks, delays, multiplier);
    }

    public static List<Object> simPeakFit(Dataset theFile, Collection<Peak> peaks)
            throws IllegalArgumentException, IOException, PeakFitException {
        boolean doFit = true;
        int fitMode = FIT_ALL;
        boolean updatePeaks = true;
        double[] delays = null;
        double multiplier = 0.686;
        int[] rows = new int[theFile.getNDim()];
        return peakFit(theFile, peaks, rows, doFit, fitMode, updatePeaks, delays, multiplier);
    }

    public void peakFit(Dataset theFile)
            throws IllegalArgumentException, IOException, PeakFitException {
        Set<Set<Peak>> oPeaks = getOverlappingPeaks();
        oPeaks.stream().forEach(oPeakSet -> {
            try {
                simPeakFit(theFile, oPeakSet);
            } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        );
    }

    public void peakFit(Dataset theFile, Collection<Peak> peaks)
            throws IllegalArgumentException, IOException, PeakFitException {
        Set<Set<Peak>> oPeaks = getOverlappingPeaks(peaks);
        oPeaks.stream().forEach(oPeakSet -> {
            try {
                simPeakFit(theFile, oPeakSet);
            } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                Logger.getLogger(PeakList.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        );
    }

    public static List<Object> peakFit(Dataset theFile, String[] argv,
            int start, int[] rows, boolean doFit, int fitMode, final boolean updatePeaks, double[] delays, double multiplier)
            throws IllegalArgumentException, IOException, PeakFitException {

        List<Peak> peaks = new ArrayList<>();

        for (int iArg = start, iPeak = 0; iArg < argv.length; iArg++, iPeak++) {
            Peak peak = getAPeak(argv[iArg].toString());
            if (peak == null) {
                throw new IllegalArgumentException(
                        "Couln't find peak \"" + argv[iArg].toString() + "\"");
            }
            peaks.add(peak);
        }
        return peakFit(theFile, peaks, rows, doFit, fitMode, updatePeaks, delays, multiplier);
    }

    public static List<Object> peakFit(Dataset theFile, Collection<Peak> peaks,
            int[] rows, boolean doFit, int fitMode, final boolean updatePeaks, double[] delays, double multiplier)
            throws IllegalArgumentException, IOException, PeakFitException {
        int dataDim = theFile.getNDim();
        int[] pdim = new int[dataDim];
        int[][] p1 = new int[dataDim][2];
        int[][] p2 = new int[dataDim][2];
        int[] dim = new int[dataDim];
        double[] plane = {0.0, 0.0};
        int nPeaks = peaks.size();
        int[][] cpt = new int[nPeaks][dataDim];
        double[][] width = new double[nPeaks][dataDim];
        int nPeakDim = 0;
        double maxDelay = 0.0;
        if ((delays != null) && (delays.length > 0)) {
            maxDelay = StatUtils.max(delays);
        }

        //int k=0;
        for (int i = 0; i < dataDim; i++) {
            pdim[i] = -1;
        }

        List<Object> peaksResult = new ArrayList<>();
        ArrayList<GuessValue> guessList = new ArrayList<GuessValue>();
        ArrayList<CenterRef> centerList = new ArrayList<CenterRef>();
        boolean firstPeak = true;
        int iPeak = -1;
        double globalMax = 0.0;
        for (Peak peak : peaks) {
            iPeak++;
            if (dataDim < peak.peakList.nDim) {
                throw new IllegalArgumentException(
                        "Number of peak list dimensions greater than number of dataset dimensions");
            }
            nPeakDim = peak.peakList.nDim;
            for (int j = 0; j < peak.peakList.nDim; j++) {
                boolean ok = false;
                for (int i = 0; i < dataDim; i++) {
                    if (peak.peakList.getSpectralDim(j).getDimName().equals(
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
            int nextDim = peak.peakList.nDim;
            for (int i = 0; i < dataDim; i++) {
                boolean gotThisDim = false;
                for (int j = 0; j < pdim.length; j++) {
                    if (pdim[j] == i) {
                        gotThisDim = true;
                        break;
                    }
                }
                if (!gotThisDim) {
                    pdim[nextDim] = i;
                    p2[nextDim][0] = rows[i];
                    p2[nextDim][1] = rows[i];
                    nextDim++;
                }
            }

            peak.getPeakRegion(theFile, pdim, p1, cpt[iPeak], width[iPeak]);

            double intensity = (double) peak.getIntensity();
            GuessValue gValue;
            if (intensity > 0.0) {
                gValue = new GuessValue(intensity, intensity * 0.1, intensity * 3.5, true);
            } else {
                gValue = new GuessValue(intensity, intensity * 1.5, intensity * 0.5, true);
            }
            guessList.add(gValue);
            if (FastMath.abs(intensity) > globalMax) {
                globalMax = FastMath.abs(intensity);
            }
            if ((delays != null) && (delays.length > 0)) {
                gValue = new GuessValue(maxDelay / 2.0, maxDelay * 5.0, maxDelay * 0.02, true);
                guessList.add(gValue);
                gValue = new GuessValue(0.0, -0.5 * FastMath.abs(intensity), 0.5 * FastMath.abs(intensity), true);
                guessList.add(gValue);
            }

            for (int iDim = 0; iDim < peak.peakList.nDim; iDim++) {
                if (fitMode == FIT_AMPLITUDES) {
                    gValue = new GuessValue(width[iPeak][iDim], width[iPeak][iDim] * 0.05, width[iPeak][iDim] * 1.05, false);
                } else {
                    gValue = new GuessValue(width[iPeak][iDim], width[iPeak][iDim] * 0.2, width[iPeak][iDim] * 2.0, true);
                }
                guessList.add(gValue);
                // adding one to account for global max inserted at end
                centerList.add(new CenterRef(guessList.size() + 1, iDim));
                if (fitMode == FIT_AMPLITUDES) {
                    gValue = new GuessValue(cpt[iPeak][iDim], cpt[iPeak][iDim] - width[iPeak][iDim] / 40, cpt[iPeak][iDim] + width[iPeak][iDim] / 40, false);
                } else {
                    gValue = new GuessValue(cpt[iPeak][iDim], cpt[iPeak][iDim] - width[iPeak][iDim] / 2, cpt[iPeak][iDim] + width[iPeak][iDim] / 2, true);
                }
                guessList.add(gValue);
//System.out.println(iDim + " " + p1[iDim][0] + " " +  p1[iDim][1]);
                if (firstPeak) {
                    p2[iDim][0] = p1[iDim][0];
                    p2[iDim][1] = p1[iDim][1];
                } else {
                    if (p1[iDim][0] < p2[iDim][0]) {
                        p2[iDim][0] = p1[iDim][0];
                    }

                    if (p1[iDim][1] > p2[iDim][1]) {
                        p2[iDim][1] = p1[iDim][1];
                    }
                }
                System.out.println(iDim + " " + p2[iDim][0] + " " + p2[iDim][1]);
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
        ArrayList<int[]> posArray = theFile.getFilteredPositions(p2, cpt, width, pdim, multiplier);
        for (CenterRef centerRef : centerList) {
            GuessValue gValue = guessList.get(centerRef.index);
            int offset = p2[centerRef.dim][0];
            gValue = new GuessValue(gValue.value - offset, gValue.lower - offset, gValue.upper - offset, gValue.floating);
            guessList.set(centerRef.index, gValue);
        }
        double[][] positions = new double[posArray.size()][nPeakDim];
        int i = 0;
        for (int[] pValues : posArray) {
            for (int j = 0; j < nPeakDim; j++) {
                positions[i][j] = pValues[pdim[j]] - p2[j][0];
            }
            i++;
        }
        LorentzGaussND peakFit = new LorentzGaussND(positions);
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
        int nRates = 1;
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
                    int index = pos.length - 1;
                    pos[index] = rows[index] + iRate;
                    pos2Array.add(pos);
                }
                intensities[iRate] = theFile.getIntensities(pos2Array);
            }
        }
        peakFit.setDelays(delays);
        peakFit.setIntensities(intensities);
        peakFit.setOffsets(guess, lower, upper, floating);
        int nFloating = 0;
        for (boolean floats : floating) {
            if (floats) {
                nFloating++;
            }
        }
        int nInterpolationPoints = (nFloating + 1) * (nFloating + 2) / 2;
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
                    peak.setComment("R " + String.valueOf(values[index++]) + " " + String.valueOf(values[index++]));
                    index++;
                }
                double lineWidthAll = 1.0;
                for (int iDim = 0; iDim < peak.peakList.nDim; iDim++) {
                    PeakDim peakDim = peak.getPeakDim(iDim);
                    double lineWidth = theFile.ptWidthToPPM(iDim, values[index]);
                    //double lineWidthHz = theFile.ptWidthToHz(iDim, values[index++]);
                    double lineWidthHz = values[index++];
                    peakDim.setLineWidthValue((float) lineWidth);
                    //lineWidthAll *= lineWidthHz * (Math.PI / 2.0);
                    lineWidthAll *= lineWidthHz;
                    peakDim.setBoundsValue((float) (lineWidth * 1.5));
                    peakDim.setChemShiftValueNoCheck((float) theFile.pointToPPM(iDim, values[index++]));
                }
                peak.setVolume1((float) (peak.getIntensity() * lineWidthAll));
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
                    peakData.add("relaxbase");
                    peakData.add(values[index++]);
                }
                double lineWidthAll = 1.0;
                for (int iDim = 0; iDim < peak.peakList.nDim; iDim++) {
                    PeakDim peakDim = peak.getPeakDim(iDim);
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

    public Set<Set<Peak>> getOverlappingPeaks() {
        Set<Set<Peak>> result = new HashSet<Set<Peak>>();
        boolean[] used = new boolean[size()];
        for (int i = 0, n = size(); i < n; i++) {
            Peak peak = getPeak(i);
            if (used[i]) {
                continue;
            }
            Set<Peak> overlaps = peak.getAllOverlappingPeaks();
            result.add(overlaps);
            for (Peak checkPeak : overlaps) {
                used[checkPeak.getIndex()] = true;
            }
        }
        return result;
    }

    public Set<Set<Peak>> getOverlappingPeaks(Collection<Peak> fitPeaks) {
        Set<Set<Peak>> result = new HashSet<Set<Peak>>();
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

    static public class PhaseRelationship {

        private static final TreeMap typesList = new TreeMap();
        public static final PhaseRelationship ANYPHASE = new PhaseRelationship(
                "anyphase");
        public static final PhaseRelationship INPHASE = new PhaseRelationship(
                "inphase");
        public static final PhaseRelationship INPHASE_POS = new PhaseRelationship(
                "inphase_pos");
        public static final PhaseRelationship INPHASE_NEG = new PhaseRelationship(
                "inphase_neg");
        public static final PhaseRelationship ANTIPHASE = new PhaseRelationship(
                "antiphase");
        public static final PhaseRelationship ANTIPHASE_LEFT = new PhaseRelationship(
                "antiphase_left");
        public static final PhaseRelationship ANTIPHASE_RIGHT = new PhaseRelationship(
                "antiphase_right");
        private final String name;

        private PhaseRelationship(String name) {
            this.name = name;
            typesList.put(name, this);
        }

        @Override
        public String toString() {
            return name;
        }

        public boolean isSigned() {
            return (toString().indexOf("_") != -1);
        }

        public static PhaseRelationship getFromString(String name) {
            return (PhaseRelationship) typesList.get(name);
        }

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

        public static PhaseRelationship getType(double ctr1, double intensity1,
                double ctr2, double intensity2) {
            double left = 0.0;
            double right = 0.0;

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

    class DistanceMatch {

        int iPeak = 0;
        int jPeak = 0;
        double delta = 0.0;
        double[] deltas;

        DistanceMatch(int iPeak, int jPeak, double[] deltas, double delta) {
            this.iPeak = iPeak;
            this.jPeak = jPeak;
            this.delta = delta;
            this.deltas = new double[deltas.length];

            for (int j = 0; j < deltas.length; j++) {
                this.deltas[j] = deltas[j];
            }
        }

        double getDelta() {
            return delta;
        }

        public double compare(DistanceMatch[][] aNeighbors, int iNeighbor,
                DistanceMatch[][] bNeighbors, int jNeighbor) {
            double globalSum = 0.0;

            for (int i = 0; i < aNeighbors[iNeighbor].length; i++) {
                DistanceMatch aDis = aNeighbors[iNeighbor][i];
                double sumMin = Double.MAX_VALUE;

                for (int j = 0; j < bNeighbors[jNeighbor].length; j++) {
                    DistanceMatch bDis = bNeighbors[jNeighbor][j];
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
            StringBuffer sBuf = new StringBuffer();
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

    public SpectralDim getSpectralDim(int iDim) {
        SpectralDim specDim = null;
        if (iDim < spectralDims.length) {
            specDim = spectralDims[iDim];
        }
        return specDim;
    }

    public static void processSTAR3PeakList(Saveframe saveframe) throws ParseException {
        String listName = saveframe.getValue("_Spectral_peak_list", "Sf_framecode");
        String sampleLabel = saveframe.getLabelValue("_Spectral_peak_list", "Sample_label");
        String sampleConditionLabel = saveframe.getLabelValue("_Spectral_peak_list", "Sample_condition_list_label");
        String datasetName = saveframe.getLabelValue("_Spectral_peak_list", "Experiment_name");
        String nDimString = saveframe.getValue("_Spectral_peak_list", "Number_of_spectral_dimensions");
        String dataFormat = saveframe.getOptionalValue("_Spectral_peak_list", "Text_data_format");
        String details = saveframe.getOptionalValue("_Spectral_peak_list", "Details");
        if (dataFormat.equals("text")) {
            System.out.println("Aaaack, peak list is in text format, skipping list");
            System.out.println(details);
            return;
        }
        if (nDimString.equals("?")) {
            return;
        }
        if (nDimString.equals(".")) {
            return;
        }
        int nDim = NvUtil.toInt(nDimString);

        PeakList peakList = new PeakList(listName, nDim);

        int nSpectralDim = saveframe.loopCount("_Spectral_dim");
        if (nSpectralDim > nDim) {
            throw new IllegalArgumentException("Too many _Spectral_dim values " + listName + " " + nSpectralDim + " " + nDim);
        }

        peakList.setSampleLabel(sampleLabel);
        peakList.setSampleConditionLabel(sampleConditionLabel);
        peakList.setDatasetName(datasetName);
        peakList.setDetails(details);

        for (int i = 0; i < nSpectralDim; i++) {
            SpectralDim sDim = peakList.spectralDims[i];

            String value = null;
            value = saveframe.getValueIfPresent("_Spectral_dim", "Atom_type", i);
            if (value != null) {
                sDim.setAtomType(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Atom_isotope_number", i);
            if (value != null) {
                sDim.setAtomIsotopeValue(NvUtil.toInt(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Spectral_region", i);
            if (value != null) {
                sDim.setSpectralRegion(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Magnetization_linkage", i);
            if (value != null) {
                sDim.setMagLinkage(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Sweep_width", i);
            if (value != null) {
                sDim.setSw(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Spectrometer_frequency", i);
            if (value != null) {
                sDim.setSf(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Encoding_code", i);
            if (value != null) {
                sDim.setEncodingCode(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Encoded_source_dimension", i);
            if (value != null) {
                sDim.setEncodedSourceDim(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Dataset_dimension", i);
            if (value != null) {
                sDim.setDataDim(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Dimension_name", i);
            if (value != null) {
                sDim.setDimName(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "ID_tolerance", i);
            if (value != null) {
                sDim.setIdTol(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Pattern", i);
            if (value != null) {
                sDim.setPattern(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Relation", i);
            if (value != null) {
                sDim.setRelation(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Aliasing", i);
            if (value != null) {
                sDim.setAliasing(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Precision", i);
            if (value != null) {
                sDim.setPrecision(NvUtil.toInt(value));
            }
        }

        Loop loop = saveframe.getLoop("_Peak");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("ID");
            List<String> detailColumn = loop.getColumnAsListIfExists("Details");
            List<String> fomColumn = loop.getColumnAsListIfExists("Figure_of_merit");
            List<String> typeColumn = loop.getColumnAsListIfExists("Type");
            List<String> statusColumn = loop.getColumnAsListIfExists("Status");
            List<String> colorColumn = loop.getColumnAsListIfExists("Color");
            List<String> flagColumn = loop.getColumnAsListIfExists("Flag");
            List<String> cornerColumn = loop.getColumnAsListIfExists("Label_corner");

            for (int i = 0, n = idColumn.size(); i < n; i++) {
                int idNum = Integer.parseInt((String) idColumn.get(i));
                Peak peak = new Peak(peakList, nDim);
                peak.setIdNum(idNum);
                String value = null;
                if ((value = NvUtil.getColumnValue(fomColumn, i)) != null) {
                    float fom = NvUtil.toFloat(value);
                    peak.setFigureOfMerit(fom);
                }
                if ((value = NvUtil.getColumnValue(detailColumn, i)) != null) {
                    peak.setComment(value);
                }
                if ((value = NvUtil.getColumnValue(typeColumn, i)) != null) {
                    int type = Peak.getType(value);
                    peak.setType(type);
                }
                if ((value = NvUtil.getColumnValue(statusColumn, i)) != null) {
                    int status = NvUtil.toInt(value);
                    peak.setStatus(status);
                }
                if ((value = NvUtil.getColumnValue(colorColumn, i)) != null) {
                    peak.setColor(value);
                }
                if ((value = NvUtil.getColumnValue(flagColumn, i)) != null) {
                    for (int iFlag = 0; iFlag < Peak.NFLAGS; iFlag++) {
                        if (value.length() > iFlag) {
                            peak.setFlag(iFlag, (value.charAt(iFlag) == '1'));
                        } else {
                            peak.setFlag(iFlag, false);
                        }
                    }
                }
                if ((value = NvUtil.getColumnValue(cornerColumn, i)) != null) {
                    peak.setCorner(value);
                }
                peakList.addPeakWithoutResonance(peak);
            }

            loop = saveframe.getLoop("_Peak_general_char");
            if (loop != null) {
                List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                List<String> methodColumn = loop.getColumnAsList("Measurement_method");
                List<String> intensityColumn = loop.getColumnAsList("Intensity_val");
                //  fixme, unused: ArrayList errorColumn = loop.getColumnAsList("Intensity_val_err");
                for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                    String value = null;
                    int idNum = 0;
                    if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                        idNum = NvUtil.toInt(value);
                    } else {
                        //throw new TclException("Invalid peak id value at row \""+i+"\"");
                        continue;
                    }
                    Peak peak = peakList.getPeakByID(idNum);
                    String method = "height";
                    if ((value = NvUtil.getColumnValue(methodColumn, i)) != null) {
                        method = value;
                    }
                    if ((value = NvUtil.getColumnValue(intensityColumn, i)) != null) {
                        float iValue = NvUtil.toFloat(value);
                        if (method.equals("height")) {
                            peak.setIntensity(iValue);
                        } else if (method.equals("volume")) {
                            // FIXME should set volume/evolume 
                            peak.setVolume1(iValue);
                        } else {
                            // FIXME throw error if don't know type, or add new type dynamically?
                            peak.setIntensity(iValue);
                        }
                    }
                    // FIXME set error value
                }
            }

            loop = saveframe.getLoop("_Peak_char");
            if (loop == null) {
                throw new ParseException("No \"_Peak_char\" loop");
            }

            List<String> peakIdColumn = loop.getColumnAsList("Peak_ID");
            List<String> sdimColumn = loop.getColumnAsList("Spectral_dim_ID");

            for (int j = 0; j < Peak.peakCharStrings.length; j++) {
                String tag = Peak.peakCharStrings[j].substring(Peak.peakCharStrings[j].indexOf(".") + 1);
                if (tag.equals("Sf_ID") || tag.equals("Entry_ID") || tag.equals("Spectral_peak_list_ID")) {
                    continue;
                }
                if (tag.equals("Resonance_ID") || tag.equals("Resonance_count")) {
                    continue;
                }
                List<String> column = loop.getColumnAsList(tag);
                if (column != null) {
                    for (int i = 0, n = column.size(); i < n; i++) {
                        int idNum = Integer.parseInt((String) peakIdColumn.get(i));
                        int sDim = Integer.parseInt((String) sdimColumn.get(i)) - 1;
                        String value = (String) column.get(i);
                        if (!value.equals(".") && !value.equals("?")) {
                            Peak peak = peakList.getPeakByID(idNum);
                            PeakDim peakDim = peak.getPeakDim(sDim);
                            peakDim.setAttribute(tag, value);
                        }
                    }
                }
            }
            loop = saveframe.getLoop("_Assigned_peak_chem_shift");
            if (loop != null) {
                List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                List<String> spectralDimColumn = loop.getColumnAsList("Spectral_dim_ID");
                List<String> valColumn = loop.getColumnAsList("Val");
                List<String> resonanceColumn = loop.getColumnAsList("Resonance_ID");
                for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                    String value = null;
                    int idNum = 0;
                    if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                        idNum = NvUtil.toInt(value);
                    } else {
                        //throw new TclException("Invalid peak id value at row \""+i+"\"");
                        continue;
                    }
                    int sDim = 0;
                    long resonanceID = -1;
                    if ((value = NvUtil.getColumnValue(spectralDimColumn, i)) != null) {
                        sDim = NvUtil.toInt(value) - 1;
                    } else {
                        throw new ParseException("Invalid spectral dim value at row \"" + i + "\"");
                    }
                    if ((value = NvUtil.getColumnValue(valColumn, i)) != null) {
                        NvUtil.toFloat(value);  // fixme shouldn't we use this
                    }
                    if ((value = NvUtil.getColumnValue(resonanceColumn, i)) != null) {
                        resonanceID = NvUtil.toLong(value);
                    }
                    Peak peak = peakList.getPeakByID(idNum);
                    PeakDim peakDim = peak.getPeakDim(sDim);
                    peakDim.addResonance(resonanceID);
                }
            }
        }
    }

    public static String getNameForDataset(String datasetName) {
        int lastIndex = datasetName.lastIndexOf(".");
        String listName = datasetName;
        if (lastIndex != -1) {
            listName = datasetName.substring(0, lastIndex);
        }
        return listName;
    }

    public static PeakList getPeakListForDataset(String datasetName) {
        for (PeakList peakList : peakListTable.values()) {
            if (peakList.fileName.equals(datasetName)) {
                return peakList;
            }
        }
        return null;
    }

    public DoubleSummaryStatistics shiftStats(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDim[iDim].getChemShift()).summaryStatistics();
        return stats;

    }

    public DoubleSummaryStatistics widthStats(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDim[iDim].getLineWidthHz()).summaryStatistics();
        return stats;
    }

    public double center(int iDim) {
        OptionalDouble avg = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDim[iDim].getChemShift()).average();
        return avg.getAsDouble();
    }

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

    public void shiftPeak(final int iDim, final double value) {
        peaks.stream().forEach(p -> {
            PeakDim pDim = p.peakDim[iDim];
            float shift = pDim.getChemShift();
            shift += value;
            pDim.setChemShiftValue(shift);
        });
    }

    public Nuclei[] guessNuclei() {
        double[] sf = new double[nDim];
        for (int i = 0; i < nDim; i++) {
            SpectralDim sDim = getSpectralDim(i);
            sf[i] = sDim.getSf();
        }
        Nuclei[] nuclei = Nuclei.findNuclei(sf);
        return nuclei;
    }
}
