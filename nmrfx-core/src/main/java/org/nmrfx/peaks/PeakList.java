package org.nmrfx.peaks;

import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.math.Clusters;
import org.nmrfx.peaks.events.*;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.STAR3;
import org.nmrfx.star.STAR3Base;
import org.nmrfx.utilities.Updater;
import org.nmrfx.utilities.Util;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Double.compare;
import static java.util.Comparator.comparing;

@PythonAPI({"dscript", "pscript"})
@PluginAPI("ring")
public class PeakList {

    /**
     *
     */
    public static PeakList clusterOrigin = null;
    /**
     *
     */
    private static final List<FreezeListener> freezeListeners = new ArrayList<>();
    public int idLast;
    /**
     *
     */
    public String fileName;
    protected String listName;
    protected final int listID;
    protected String details = "";
    protected String sampleLabel = "";
    protected String sampleConditionLabel = "";
    protected String experimentType = "";
    protected List<Peak> peaks;
    protected final Map<Integer, Peak> indexMap = new HashMap<>();
    boolean slideable = false;
    boolean requireSliderCondition = false;
    protected List<SearchDim> searchDims = new ArrayList<>();
    Optional<Measures> measures = Optional.empty();
    Map<String, String> properties = new HashMap<>();
    List<PeakListener> peakChangeListeners = new ArrayList<>();
    List<PeakListener> peakListChangeListeners = new ArrayList<>();
    List<PeakListener> peakCountChangeListeners = new ArrayList<>();
    protected boolean changed = false;
    public AtomicBoolean peakUpdated = new AtomicBoolean(false);
    public AtomicBoolean peakListUpdated = new AtomicBoolean(false);
    public AtomicBoolean peakCountUpdated = new AtomicBoolean(false);
    public AtomicBoolean assignmentStatusValid = new AtomicBoolean(false);

    Map<Peak.AssignmentLevel, Integer> assignmentMap = new HashMap<>();
    Updater updater = null;

    /**
     * @param name
     * @param n
     * @param listNum
     */
    public PeakList(String name, int n, Integer listNum) {
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
        ProjectBase.getActive().addPeakList(this, listName);
        if (listNum == null) {
            listNum = 1;
            while (get(listNum).isPresent()) {
                listNum++;
            }
        }
        this.listID = listNum;
    }

    /**
     * @param name
     * @param n
     */
    public PeakList(String name, int n) {
        this(name, n, null);
    }

    @Override
    public String toString() {
        return listName;
    }

    /**
     * Returns an Optional containing the PeakList that has the specified id
     * number or empty value if no PeakList with that id exists.
     *
     * @param listID the id of the peak list
     * @return the Optional containing the PeaKlist or an empty value if no
     * PeakList with that id exists
     */
    public static Optional<PeakList> get(int listID) {
        return ProjectBase.getActive().getPeakList(listID);
    }

    /**
     * @return
     */
    public static Collection<PeakList> peakLists() {
        return ProjectBase.getActive().getPeakLists();
    }

    /**
     * Return an Optional containing the PeakList with lowest id number or an
     * empty value if no PeakLists are present.
     *
     * @return Optional containing first peakList if any peak lists present or
     * empty if no peak lists.
     */
    public static Optional<PeakList> getFirst() {
        return ProjectBase.getActive().getFirstPeakList();
    }

    /**
     *
     */
    public int nDim;
    /**
     *
     */
    public double scale = 1.0;
    protected SpectralDim[] spectralDims = null;

    /**
     * @param peak
     */
    public static void unLinkPeak(Peak peak) {
        for (int i = 0; i < peak.peakList.nDim; i++) {
            List<PeakDim> peakDims = getLinkedPeakDims(peak, i);
            PeakList.unLinkPeak(peak, i);

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
     * @param peak
     * @return
     */
    public static List<Peak> getLinks(Peak peak) {
        List<Peak> peaks = new ArrayList<>();
        for (int iDim = 0; iDim < peak.getNDim(); iDim++) {
            List<PeakDim> peakDims = getLinkedPeakDims(peak, iDim);
            for (int i = 0; i < peakDims.size(); i++) {
                PeakDim peakDim = peakDims.get(i);
                Peak lPeak = peakDim.getPeak();
                if (!peaks.contains(lPeak)) {
                    peaks.add(lPeak);
                }
            }
        }
        return peaks;
    }

    /**
     * @param peak
     * @param iDim
     * @return
     */
    public static List<Peak> getLinks(final Peak peak, final int iDim) {
        final List<PeakDim> peakDims = getLinkedPeakDims(peak, iDim);
        final List<Peak> peaks = new ArrayList<>(peakDims.size());
        for (int i = 0; i < peakDims.size(); i++) {
            PeakDim peakDim = (PeakDim) peakDims.get(i);
            peaks.add((Peak) peakDim.getPeak());
        }
        return peaks;
    }

    /**
     * @param peakA
     * @param dimA
     * @param peakB
     * @param dimB
     */
    public static void linkPeaks(Peak peakA, String dimA, Peak peakB, String dimB) {
        PeakDim peakDimA = peakA.getPeakDim(dimA);
        PeakDim peakDimB = peakB.getPeakDim(dimB);
        if ((peakDimA != null) && (peakDimB != null)) {
            PeakList.linkPeakDims(peakDimA, peakDimB);
        }
    }

    /**
     * @param peakA
     * @param dimA
     * @param peakB
     * @param dimB
     */
    public static void linkPeaks(Peak peakA, int dimA, Peak peakB, int dimB) {
        PeakDim peakDimA = peakA.getPeakDim(dimA);
        PeakDim peakDimB = peakB.getPeakDim(dimB);
        if ((peakDimA != null) && (peakDimB != null)) {
            PeakList.linkPeakDims(peakDimA, peakDimB);
        }
    }

    /**
     * @param peakDimA
     * @param peakDimB
     */
    public static void linkPeakDims(PeakDim peakDimA, PeakDim peakDimB) {
        AtomResonance resonanceA = peakDimA.getResonance();
        AtomResonance resonanceB = peakDimB.getResonance();

        AtomResonance.merge(resonanceA, resonanceB);

        peakDimA.peakDimUpdated();
        peakDimB.peakDimUpdated();
    }

    /**
     * @param peakDimA
     * @param peakDimB
     */
    public static void couplePeakDims(PeakDim peakDimA, PeakDim peakDimB) {
        AtomResonance resonanceA = peakDimA.getResonance();
        AtomResonance resonanceB = peakDimB.getResonance();

        AtomResonance.merge(resonanceA, resonanceB);

        Multiplet.merge(peakDimA, peakDimB);
        peakDimA.peakDimUpdated();
        peakDimB.peakDimUpdated();
    }

    /**
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
     * @param datasetName
     * @return
     */
    public static PeakList getPeakListForDataset(String datasetName) {
        ProjectBase project = ProjectBase.getActive();
        for (PeakList peakList : project.getPeakLists()) {
            if (peakList.fileName.equals(datasetName)) {
                return peakList;
            }
        }
        return null;
    }

    /**
     * @param peakListNames
     * @return
     * @throws IllegalArgumentException
     */
    public static int clusterPeaks(String[] peakListNames) throws IllegalArgumentException {
        List<PeakList> peakLists = new ArrayList<>();
        for (String peakListName : peakListNames) {
            PeakList peakList = (PeakList) get(peakListName);
            if (peakList == null) {
                throw new IllegalArgumentException("Couldn't find peak list " + peakListName);
            }
            peakLists.add(peakList);
        }
        return PeakList.clusterPeaks(peakLists);
    }

    /**
     * @param peakLists
     * @return
     * @throws IllegalArgumentException
     */
    public static int clusterPeaks(List<PeakList> peakLists) {
        return clusterPeaks(peakLists, null);
    }

    public static int clusterPeaks(List<PeakList> peakLists, PeakList refList)
            throws IllegalArgumentException {
        Clusters clusters = new Clusters();
        List<Peak> clustPeaks = new ArrayList<>();
        double[] tol = null;
        List<PeakList> useLists = new ArrayList<>();
        if (refList != null) {
            useLists.add(refList);
            for (var pList : peakLists) {
                if (pList != refList) {
                    useLists.add(pList);
                }
            }
        } else {
            useLists.addAll(peakLists);
        }

        for (PeakList peakList : useLists) {
            // fixme  should remove unlink and properly support links below
            peakList.unLinkPeaks();
            peakList.peaks.stream().filter(p -> p.getStatus() >= 0).forEach(p -> p.setStatus(0));
            int fDim = peakList.searchDims.size();
            if (fDim == 0) {
                throw new IllegalArgumentException("List doesn't have search dimensions");
            }
        }

        boolean firstList = true;
        int ii = 0;
        int fDim = 0;
        int iList = 0;
        for (PeakList peakList : useLists) {

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

                ii++;
                double[] v = new double[peakList.searchDims.size()];
                for (int k = 0; k < peakList.searchDims.size(); k++) {
                    SearchDim sDim = peakList.searchDims.get(k);
                    v[k] = 0.0;

                    for (int iPeak = 0; iPeak < linkedPeaks.size(); iPeak++) {
                        Peak peak2 = linkedPeaks.get(iPeak);
                        peak2.setStatus(1);
                        v[k] += peak2.peakDims[sDim.getDim()].getChemShiftValue();
                    }

                    v[k] /= linkedPeaks.size();
                    tol[k] = sDim.getTol();
                }
                Clusters.ClusterItem clusterItem = new Clusters.ClusterItem(peak, v, iList);

                clusters.addDatum(clusterItem);
            }
            iList++;
            firstList = false;
        }
        clusters.doCluster(fDim, tol);
        clusters.testDuplicates();

        int nClusters = 0;
        for (int i = 0; i < clusters.data.size(); i++) {
            Clusters.ClusterItem iDatum = clusters.data.get(i);
            if (iDatum.isActive()) {
                List<Object> objs = iDatum.getObjects();
                Peak iPeak = (Peak) objs.get(0);
                PeakList.unLinkPeak(iPeak);
                for (int iObj = 1; iObj < objs.size(); iObj++) {
                    Peak jPeak = (Peak) objs.get(iObj);
                    PeakList.unLinkPeak(jPeak);
                    for (int iDim = 0; iDim < fDim; iDim++) {
                        SearchDim iSDim = iPeak.getPeakList().searchDims.get(iDim);
                        SearchDim jSDim = jPeak.getPeakList().searchDims.get(iDim);
                        linkPeaks(iPeak, iSDim.getDim(), jPeak, jSDim.getDim());
                    }
                }
                nClusters++;
            }

        }
        return nClusters;
    }

    /**
     * @return
     */
    public String getDatasetName() {
        return fileName;
    }

    public boolean isSimulated() {
        return getSampleConditionLabel().contains("sim");
    }

    /**
     * Rename the peak list.
     *
     * @param newName
     */
    public void setName(String newName) {
        ProjectBase project = ProjectBase.getActive();
        project.removePeakList(listName);
        listName = newName;
        project.addPeakList(this, newName);
    }

    /**
     * @param sampleLabel
     */
    public void setSampleLabel(String sampleLabel) {
        this.sampleLabel = sampleLabel;
    }

    /**
     * @param datasetName
     */
    public void setDatasetName(String datasetName) {
        this.fileName = datasetName;
    }

    public List<SearchDim> getSearchDims() {
        return searchDims;
    }

    /**
     * @param oldListener
     */
    public void removePeakCountChangeListener(PeakListener oldListener) {
        peakCountChangeListeners.remove(oldListener);
    }

    /**
     * @param newListener
     */
    public void registerPeakCountChangeListener(PeakListener newListener) {
        if (!peakCountChangeListeners.contains(newListener)) {
            peakCountChangeListeners.add(newListener);
        }
    }

    public void notifyPeakCountChangeListeners() {
        for (PeakListener listener : peakCountChangeListeners) {
            listener.peakListChanged(new PeakCountEvent(this, size()));
        }
    }

    /**
     * @param oldListener
     */
    public void removePeakListChangeListener(PeakListener oldListener) {
        peakListChangeListeners.remove(oldListener);
    }

    /**
     * @param newListener
     */
    public void registerPeakListChangeListener(PeakListener newListener) {
        if (!peakListChangeListeners.contains(newListener)) {
            peakListChangeListeners.add(newListener);
        }
    }

    public void notifyPeakListChangeListeners() {
        for (PeakListener listener : peakListChangeListeners) {
            listener.peakListChanged(new PeakListEvent(this));
        }
    }

    /**
     * @param oldListener
     */
    public void removePeakChangeListener(PeakListener oldListener) {
        peakChangeListeners.remove(oldListener);
    }

    /**
     * @param newListener
     */
    public void registerPeakChangeListener(PeakListener newListener) {
        if (!peakChangeListeners.contains(newListener)) {
            peakChangeListeners.add(newListener);
        }
    }

    public void notifyPeakChangeListeners() {
        for (PeakListener listener : peakChangeListeners) {
            listener.peakListChanged(new PeakEvent(this));
        }
    }

    public void registerUpdater(Updater updater) {
        this.updater = updater;
    }

    public void removeUpdater() {
        this.updater = null;
    }

    /**
     * Copies an existing peak list.
     *
     * @param name     a string with the name of the new peak list.  If merge is true
     *                 then this must be an existing peak list that will be merged.
     * @param allLinks a boolean specifying whether or not to link peak
     *                 dimensions.
     * @param merge    a boolean specifying whether or not to merge peak labels.
     * @return a list that is a copy of the peak list with the input name.
     * @throws IllegalArgumentException if a peak with the input name doesn't
     *                                  exist.
     */
    public PeakList copy(final String name, final boolean allLinks, boolean merge, boolean copyLabels) {
        PeakList newPeakList;
        if (merge) {

            newPeakList = PeakList.get(name);
            if (newPeakList == null) {
                throw new IllegalArgumentException("Peak list " + name + " doesn't exist");
            }
        } else {
            newPeakList = new PeakList(name, nDim);
        }
        copy(newPeakList, allLinks, merge, copyLabels);
        return newPeakList;
    }

    public PeakList copy(PeakList newPeakList, final boolean allLinks, boolean merge, boolean copyLabels) {

        if (!merge) {
            newPeakList.searchDims.addAll(searchDims);
            newPeakList.fileName = fileName;
            newPeakList.scale = scale;
            newPeakList.setDetails(details);
            newPeakList.sampleLabel = sampleLabel;
            newPeakList.sampleConditionLabel = getSampleConditionLabel();

            for (int i = 0; i < nDim; i++) {
                newPeakList.spectralDims[i] = spectralDims[i].copy(newPeakList);
            }
        }
        for (int i = 0; i < peaks.size(); i++) {
            Peak peak = (Peak) peaks.get(i);
            Peak newPeak = peak.copy(newPeakList);
            if (!merge) {
                newPeak.setIdNum(peak.getIdNum());
            }
            newPeakList.addPeak(newPeak);
            if (merge || copyLabels) {
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
                        Peak linkPeak = (Peak) peakDim.getPeak();
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
     * @param freezeListener
     */
    public static void registerFreezeListener(FreezeListener freezeListener) {
        if (freezeListeners.contains(freezeListener)) {
            freezeListeners.remove(freezeListener);
        }
        freezeListeners.add(freezeListener);
    }

    /**
     * @param peak
     * @param state
     */
    public static void notifyFreezeListeners(Peak peak, boolean state) {
        for (FreezeListener listener : freezeListeners) {
            listener.freezeHappened(peak, state);

        }
    }

    /**
     * @return
     */
    public static Iterator<PeakList> iterator() {
        return ProjectBase.getActive().getPeakLists().iterator();
    }

    public synchronized void setUpdatedFlag(boolean value) {
    }

    public void peakListUpdated(Object object) {
        changed = true;
        assignmentStatusValid.set(false);
        if (updater != null) {
            updater.update(object);
        }
    }

    /**
     *
     */
    public void clearIndex() {
        indexMap.clear();
    }

    /**
     * @return the number of dimensions of the peak list.
     */
    public int getNDim() {
        return nDim;
    }

    /**
     * @return
     */
    public double getScale() {
        return scale;
    }

    /**
     *
     */
    public void setScale(double scale) {
        this.scale = scale;
    }

    /**
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
     * @param name
     * @return
     */
    public SpectralDim getSpectralDim(String name) {
        SpectralDim specDim = null;
        for (SpectralDim sDim : spectralDims) {
            if (sDim.getDimName().equals(name)) {
                specDim = sDim;
                break;
            }
        }
        return specDim;
    }

    public SpectralDim getFoldedDim() {
        List<SpectralDim> foldedDim = Arrays.stream(spectralDims).filter((spectralDim) -> spectralDim.getFoldMode() != 'n').toList();
        return foldedDim.isEmpty() ? null : foldedDim.getFirst();
    }

    public List<SpectralDim> getSpectralDims() {
        return Arrays.asList(spectralDims);
    }

    /**
     * @return
     */
    public String getSampleLabel() {
        return sampleLabel;
    }

    /**
     * @param sampleConditionLabel
     */
    public void setSampleConditionLabel(String sampleConditionLabel) {
        this.sampleConditionLabel = sampleConditionLabel;
    }

    /**
     * @return
     */
    public String getSampleConditionLabel() {
        return sampleConditionLabel;
    }

    /**
     * @param details
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * @return
     */
    public String getExperimentType() {
        return experimentType;
    }

    /**
     * @param type
     */
    public void setExperimentType(String type) {
        this.experimentType = type;
        peakListUpdated(this);
    }

    /**
     * @return
     */
    public String getDetails() {
        return details;
    }

    /**
     * @return the ID number of the peak list.
     */
    public int getId() {
        return listID;
    }

    /**
     * @return the name of the peak list.
     */
    public String getName() {
        return listName;
    }

    /**
     * @return a peak list object.
     */
    public List<Peak> peaks() {
        return peaks;
    }

    /**
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
     * @param newPeak
     */
    public void addPeakWithoutResonance(Peak newPeak) {
        newPeak.setIndex(peaks.size());
        peaks.add(newPeak);
        peakListUpdated(peaks);
        clearIndex();
    }

    /**
     * @param newPeak
     */
    public Peak addPeak(Peak newPeak) {
        newPeak.initPeakDimContribs();
        newPeak.setIndex(peaks.size());
        peaks.add(newPeak);
        peakListUpdated(peaks);
        clearIndex();
        return newPeak;
    }

    /**
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

    public static double foldPPM(double ppm, double fDelta, double min, double max) {
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

    public void reassignResonanceFactoryMap() {
        for (Peak peak : peaks) {
            for (PeakDim peakDim : peak.getPeakDims()) {
                AtomResonance resonance = peakDim.getResonance();
                ProjectBase.activeResonanceFactory().reassignResonanceFactoryMap(resonance);
            }
        }
    }

    /**
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
     * @param dataset
     * @param looseMode
     * @return
     */
    public int[] getDimsForDataset(DatasetBase dataset, boolean looseMode) {
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
     * @param dataset
     * @return
     */
    public int[] getDimsForDataset(DatasetBase dataset) {
        return getDimsForDataset(dataset, false);
    }

    public static PeakList getPeakListWithSpecifier(String peakSpecifier) {
        int dot = peakSpecifier.indexOf('.');

        if (dot == -1) {
            return null;
        }

        ProjectBase project = ProjectBase.getActive();
        PeakList peakList = project.
                getPeakList(peakSpecifier.substring(0, dot));
        return peakList;
    }

    /**
     * @param peakSpecifier
     * @return
     */
    public static Peak getAPeak(String peakSpecifier) {
        PeakList peakList = getPeakListWithSpecifier(peakSpecifier);
        if (peakList == null) {
            return null;
        }
        int dot = peakSpecifier.indexOf('.');

        if (dot == -1) {
            return null;
        }

        int lastDot = peakSpecifier.lastIndexOf('.');

        if (peakList.indexMap.isEmpty()) {
            peakList.reIndex();
        }

        int idNum;

        if (lastDot == dot) {
            idNum = Integer.parseInt(peakSpecifier.substring(dot + 1));
        } else {
            idNum = Integer.parseInt(peakSpecifier.substring(dot + 1, lastDot));
        }

        Peak peak = (Peak) peakList.indexMap.get(idNum);
        return peak;
    }

    /**
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

        ProjectBase project = ProjectBase.getActive();
        PeakList peakList = project.
                getPeakList(peakSpecifier.substring(0, dot));

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

        ProjectBase project = ProjectBase.getActive();
        PeakList peakList = project.
                getPeakList(peakSpecifier.substring(0, dot));

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

        ProjectBase project = ProjectBase.getActive();
        PeakList peakList = project.
                getPeakList(peakSpecifier.substring(0, dot));

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
     * @param dimName
     * @param tol
     */
    public void addSearchDim(String dimName, double tol) {
        int iDim = getListDim(dimName);
        addSearchDim(iDim, tol);
    }

    /**
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
        idLast = peaks.size() - 1;
        reIndex();
    }

    /**
     * @return
     */
    public boolean hasMeasures() {
        return measures.isPresent();
    }

    /**
     * @param measure
     */
    public void setMeasures(Measures measure) {
        measures = Optional.of(measure);
    }

    /**
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
     * @param name
     * @return
     */
    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    /**
     * @param name
     * @param value
     */
    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    /**
     * @return
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Returns the PeakList that has the specified name.
     *
     * @param listName the name of the peak list
     * @return the PeaKlist or null if no PeakList of that name exists
     */
    public static PeakList get(String listName) {
        ProjectBase project = ProjectBase.getActive();
        return project.getPeakList(listName);
    }

    /**
     * Check if peak lists exists.
     *
     * @param listName the name of the peak list
     * @return true if a peak list exists that has the specified name
     */
    public static boolean exists(String listName) {
        ProjectBase project = ProjectBase.getActive();
        return project.getPeakList(listName) != null;
    }

    /**
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
        result.append("volume_err").append(sep);
        result.append("intensity").append(sep);
        result.append("intensity_err").append(sep);
        result.append("type").append(sep);
        result.append("comment").append(sep);
        result.append("color").append(sep);
        result.append("flags").append(sep);
        result.append("status");

        return (result.toString());
    }

    /**
     * Search peak list for peaks that match the specified chemical shifts.
     * Before using, a search template needs to be set up.
     *
     * @param ppms An array of chemical shifts to search
     * @return A list of matching peaks
     * @throws IllegalArgumentException thrown if ppm length not equal to search
     *                                  template length or if peak labels don't match search template
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
            searchDim[i] = sDim.getDim();

            if (searchDim[i] == -1) {
                matched = false;

                break;
            }
            double tol = sDim.getTol();
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
     * @param limits
     * @param dim
     * @return
     */
    public List<Peak> locatePeaks(double[][] limits, int[] dim) {
        return locatePeaks(limits, dim, null);
    }

    /**
     * Locate what peaks are contained within certain limits.
     *
     * @param limits     A multidimensional array of chemical shift plot limits to
     *                   search.
     * @param dim        An array of which peak list dim corresponds to dim in the
     *                   limit array.
     * @param foldLimits An optional multidimensional array of plot limits where
     *                   folded peaks should appear. Can be null.
     * @return A list of matching peaks
     */
    public List<Peak> locatePeaks(double[][] limits, int[] dim, double[][] foldLimits) {
        List<org.nmrfx.peaks.PeakDistance> foundPeaks = new ArrayList<>();

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
            if (limits[i][0] == limits[i][1]) {
                limits[i][0] = limits[i][0] - (getSpectralDim(i).getSw() / getSpectralDim(i).getSf() / 10.0);
                limits[i][1] = limits[i][1] + (getSpectralDim(i).getSw() / getSpectralDim(i).getSf() / 10.0);
            }

            if (limits[i][0] < limits[i][1]) {
                double hold = limits[i][0];
                limits[i][0] = limits[i][1];
                limits[i][1] = hold;
            }

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
            org.nmrfx.peaks.PeakDistance peakDis = new org.nmrfx.peaks.PeakDistance(peak, distance);
            foundPeaks.add(peakDis);
        }

        foundPeaks.sort(comparing(org.nmrfx.peaks.PeakDistance::getDistance));
        List<Peak> sPeaks = new ArrayList<>();
        for (org.nmrfx.peaks.PeakDistance peakDis : foundPeaks) {
            sPeaks.add(peakDis.peak);
        }

        return (sPeaks);
    }

    /**
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
     * @return
     */
    public static boolean isAnyChanged() {
        boolean anyChanged = false;
        ProjectBase project = ProjectBase.getActive();
        for (PeakList checkList : project.getPeakLists()) {
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
        ProjectBase project = ProjectBase.getActive();
        for (PeakList checkList : project.getPeakLists()) {
            checkList.clearChanged();
        }
    }

    /**
     * @return
     */
    public boolean valid() {
        return (peaks != null) && (get(listName) != null);
    }

    /**
     * @param dim
     * @param ascending
     * @throws IllegalArgumentException
     */
    public void sortPeaks(int dim, boolean ascending) throws IllegalArgumentException {
//        checkDim(dim);
        PeakList.sortPeaks(peaks, dim, ascending);
        reIndex();
    }

    /**
     * @param peaks
     * @param iDim
     * @param ascending
     */
    public static void sortPeaks(final List<Peak> peaks, int iDim, boolean ascending) {
        if (ascending) {
            peaks.sort((Peak a, Peak b) -> compare(a.peakDims[iDim].getChemShift(), b.peakDims[iDim].getChemShift()));
        } else {
            peaks.sort((Peak a, Peak b) -> compare(b.peakDims[iDim].getChemShift(), a.peakDims[iDim].getChemShift()));
        }
    }

    /**
     * @param listName
     */
    public static void remove(String listName) {
        ProjectBase project = ProjectBase.getActive();
        PeakList peakList = project.getPeakList(listName);
        if (peakList != null) {
            peakList.remove();
        }
    }

    public void remove() {
        if (peaks != null) {
            for (Peak peak : peaks) {
                for (PeakDim peakDim : peak.peakDims) {
                    peakDim.remove();
                    if (peakDim.hasMultiplet()) {
                        Multiplet multiplet = peakDim.getMultiplet();
                    }
                }
                peak.markDeleted();
            }
            peaks.clear();
        }
        peaks = null;
        if (updater != null) {
            updater.shutdown();
            updater = null;
        }
        ProjectBase.getActive().removePeakList(listName);
    }

    /**
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
     * @return
     */
    public Peak getNewPeak() {
        Peak peak = new Peak(this, nDim);
        addPeak(peak);
        return peak;
    }

    /**
     * @return
     */
    public int addPeak() {
        Peak peak = new Peak(this, nDim);
        addPeak(peak);
        return (peak.getIdNum());
    }

    /**
     * @param peak
     */
    public void removePeak(Peak peak) {
        if (peaks.get(peaks.size() - 1) == peak) {
            idLast--;
        }
        peaks.remove(peak);
        peakListUpdated(peaks);
        reIndex();
    }

    /**
     * @param peak
     * @param requireSameList
     * @return
     */
    public static List<Peak> getLinks(Peak peak, boolean requireSameList) {
        List<PeakDim> peakDims = getLinkedPeakDims(peak, 0);
        ArrayList<Peak> peaks = new ArrayList(peakDims.size());
        for (PeakDim peakDim : peakDims) {
            if (!requireSameList || (peakDim.getPeak().peakList == peak.peakList)) {
                peaks.add((Peak) peakDim.getPeak());
            }
        }
        return peaks;
    }

    /**
     * @param peak
     * @return
     */
    public static List<PeakDim> getLinkedPeakDims(Peak peak) {
        return getLinkedPeakDims(peak, 0);
    }

    /**
     * @param peak
     * @param iDim
     * @return
     */
    public static List<PeakDim> getLinkedPeakDims(Peak peak, int iDim) {
        PeakDim peakDim = peak.getPeakDim(iDim);
        return peakDim.getLinkedPeakDims();
    }

    /**
     * @return
     */
    public boolean isSlideable() {
        return slideable;
    }

    /**
     * @param state
     */
    public void setSlideable(boolean state) {
        slideable = state;
    }

    /**
     * @return
     */
    public boolean requireSliderCondition() {
        return requireSliderCondition;
    }

    /**
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
     * @param iDim
     * @return
     */
    public DoubleSummaryStatistics widthStatsPPM(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getLineWidth()).summaryStatistics();
        return stats;
    }

    /**
     * @return
     */
    public int compress() {
        int nRemoved = 0;
        for (int i = (peaks.size() - 1); i >= 0; i--) {
            if ((peaks.get(i)).getStatus() < 0) {
                PeakList.unLinkPeak(peaks.get(i));
                (peaks.get(i)).markDeleted();
                peaks.remove(i);
                peakListUpdated(peaks);
                nRemoved++;
            }
        }
        reIndex();
        return nRemoved;
    }

    /**
     *
     */
    public void unLinkPeaks() {
        int nPeaks = peaks.size();

        for (int i = 0; i < nPeaks; i++) {
            PeakList.unLinkPeak(peaks.get(i));
        }
    }

    public void writeSTAR3Header(Writer chan) throws IOException {
        chan.write(STAR3Base.SAVE + getName() + "\n");
        chan.write("_Spectral_peak_list.Sf_category                 ");
        chan.write("spectral_peak_list\n");
        chan.write("_Spectral_peak_list.Sf_framecode                 ");
        chan.write(getName() + "\n");
        chan.write("_Spectral_peak_list.ID                          ");
        chan.write(getId() + "\n");
        chan.write("_Spectral_peak_list.Data_file_name               ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Sample_ID                   ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Sample_label                 ");
        if (getSampleLabel().length() != 0) {
            chan.write("$" + getSampleLabel() + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Sample_condition_list_ID     ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Sample_condition_list_label  ");
        String sCond = getSampleConditionLabel();
        if ((sCond.length() != 0) && !sCond.equals(".")) {
            chan.write("$" + sCond + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Experiment_type              ");
        String expType = getExperimentType().isBlank() ? "." : getExperimentType();
        chan.write(expType + "\n");

        chan.write("_Spectral_peak_list.Slidable                      ");
        String slidable = isSlideable() ? "yes" : "no";
        chan.write(slidable + "\n");
        chan.write("_Spectral_peak_list.Scale ");
        chan.write(String.valueOf(getScale()) + "\n");

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
        if (getDetails().length() != 0) {
            chan.write(STAR3.quote(getDetails()) + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("\n");
    }

    /**
     * @return @throws IllegalArgumentException
     */
    public int clusterPeaks() throws IllegalArgumentException {
        List<PeakList> peakLists = new ArrayList<>();
        peakLists.add(this);
        return PeakList.clusterPeaks(peakLists);

    }

    /**
     * @param iDim
     * @return
     */
    public DescriptiveStatistics shiftDStats(int iDim) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        peaks.stream().filter((p) -> p.getStatus() >= 0).mapToDouble((p) -> p.peakDims[iDim].getChemShiftValue()).forEach((v) -> stats.addValue(v));
        return stats;
    }

    /**
     * @param iDim
     * @return
     */
    public DoubleSummaryStatistics shiftStats(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter((p) -> p.getStatus() >= 0).mapToDouble((p) -> p.peakDims[iDim].getChemShift()).summaryStatistics();
        return stats;
    }

    /**
     * @param iDim
     * @return
     */
    public DescriptiveStatistics widthDStats(int iDim) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        peaks.stream().filter((p) -> p.getStatus() >= 0).mapToDouble((p) -> p.peakDims[iDim].getLineWidthHz()).forEach((v) -> stats.addValue(v));
        return stats;
    }

    /**
     * @param iDim
     * @return
     */
    public DescriptiveStatistics widthDStatsPPM(int iDim) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        peaks.stream().filter((p) -> p.getStatus() >= 0).mapToDouble((p) -> p.peakDims[iDim].getLineWidth()).forEach((v) -> stats.addValue(v));
        return stats;
    }

    /**
     * @param iDim
     * @return
     */
    public DoubleSummaryStatistics widthStats(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter((p) -> p.getStatus() >= 0).mapToDouble((p) -> p.peakDims[iDim].getLineWidthHz()).summaryStatistics();
        return stats;
    }

    public DescriptiveStatistics intensityDStats(int iDim) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        peaks.stream().filter((p) -> p.getStatus() >= 0).mapToDouble((p) -> p.getPeakDim(iDim).getPeak().getIntensity()).forEach((v) -> stats.addValue(v));
        return stats;
    }

    /**
     * @param iDim
     * @param value
     */
    public void shiftPeak(final int iDim, final double value) {
        peaks.stream().forEach((p) -> {
            PeakDim pDim = p.peakDims[iDim];
            float shift = pDim.getChemShift();
            shift += value;
            pDim.setChemShiftValue(shift);
        });
    }

    /**
     * @param iDim
     * @return
     */
    public double getFoldAmount(int iDim) {
        double foldAmount = Math.abs(getSpectralDim(iDim).getSw() / getSpectralDim(iDim).getSf());
        return foldAmount;
    }

    /**
     * @param iDim
     * @return
     */
    public double center(int iDim) {
        OptionalDouble avg = peaks.stream().filter((p) -> p.getStatus() >= 0).mapToDouble((p) -> p.peakDims[iDim].getChemShift()).average();
        return avg.getAsDouble();
    }

    /**
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

    public void clearAtomLabels() {
        for (Peak peak : peaks) {
            for (PeakDim peakDim : peak.peakDims) {
                peakDim.setLabel("");
            }
        }
    }

    public class SearchDim {

        final int iDim;
        final double tol;

        public SearchDim(int iDim, double tol) {
            this.iDim = iDim;
            this.tol = tol;
        }

        public int getDim() {
            return iDim;
        }

        public double getTol() {
            return tol;
        }
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

    void countStatus() {
        assignmentMap.clear();
        for (var assignLevel : Peak.AssignmentLevel.values()) {
            assignmentMap.put(assignLevel, 0);
        }
        for (Peak peak : peaks) {
            Peak.AssignmentLevel assignLevel = peak.getAssignmentLevel();
            assignmentMap.computeIfPresent(assignLevel, (k, v) -> v + 1);
        }
        assignmentStatusValid.set(true);
    }

    public Map<Peak.AssignmentLevel, Integer> getAssignmentStatus() {
        if (!assignmentStatusValid.get()) {
            countStatus();
        }
        return assignmentMap;
    }

    public int getNumberAssigned() {
        return getAssignmentStatus().get(Peak.AssignmentLevel.AVU) + getAssignmentStatus().get(Peak.AssignmentLevel.AVM);
    }

    public int getNumberPartialAssigned() {
        return getAssignmentStatus().get(Peak.AssignmentLevel.SVU) + getAssignmentStatus().get(Peak.AssignmentLevel.SVM);
    }

    public int getNumberUnAssigned() {
        return getAssignmentStatus().get(Peak.AssignmentLevel.UNASSIGNED);
    }
}
