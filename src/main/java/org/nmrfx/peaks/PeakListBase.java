package org.nmrfx.peaks;

import java.util.*;

import org.nmrfx.datasets.Nuclei;

public class PeakListBase<T extends PeakBase> {

    static ResonanceFactory resFactory = new ResonanceFactory();
    public int idLast;
    protected String listName;
    protected final int listID;
    protected String details = "";
    protected String sampleLabel = "";
    protected String sampleConditionLabel = "";
    protected List<T> peaks;
    protected final Map<Integer, T> indexMap = new HashMap<>();
    boolean slideable = false;
    boolean requireSliderCondition = false;
    static boolean globalRequireSliderCondition = false;

    public static ResonanceFactory resFactory() {
        return resFactory;
    }
    /**
     *
     */
    public int nDim;
    /**
     *
     */
    public double scale;
    protected SpectralDim[] spectralDims = null;

    public PeakListBase(int n, Integer listNum) {
        nDim = n;
        spectralDims = new SpectralDim[nDim];
        scale = 1.0;
        this.listID = listNum == null ? 0 : listNum;
    }

    public void peakListUpdated(Object object) {
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

    /**
     *
     * @return the ID number of the peak list.
     */
    public int getId() {
        return listID;
    }

    /**
     *
     * @return the name of the peak list.
     */
    public String getName() {
        return listName;
    }

    /**
     *
     * @return a peak list object.
     */
    public List<T> peaks() {
        return peaks;
    }

    /**
     *
     * @param i
     * @return
     */
    public T getPeak(int i) {
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
     * @param newPeak
     */
    public T addPeak(T newPeak) {
        newPeak.initPeakDimContribs();
        peaks.add(newPeak);
        clearIndex();
        return newPeak;
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
        for (T peak : peaks) {
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
    public T getNewPeak() {
        PeakBase peak = new PeakBase(this, nDim);
        addPeak((T) peak);
        return (T) peak;
    }

    /**
     *
     * @param peak
     * @return
     */
    public static List getLinks(PeakBase peak) {
        List peakDims = getLinkedPeakDims(peak, 0);
        List peaks = new ArrayList(peakDims.size());
        for (int i = 0; i < peakDims.size(); i++) {
            PeakDim peakDim = (PeakDim) peakDims.get(i);
            peaks.add(peakDim.getPeak());
        }
        return peaks;
    }

    /**
     *
     * @param peak
     * @return
     */
    public static List<PeakDim> getLinkedPeakDims(PeakBase peak) {
        return getLinkedPeakDims(peak, 0);
    }

    /**
     *
     * @param peak
     * @param iDim
     * @return
     */
    public static List<PeakDim> getLinkedPeakDims(PeakBase peak, int iDim) {
        PeakDim peakDim = peak.getPeakDim(iDim);
        return peakDim.getLinkedPeakDims();
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
     * @return
     */
    public boolean requireSliderCondition() {
        return requireSliderCondition;
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
     * @param iDim
     * @return
     */
    public DoubleSummaryStatistics widthStatsPPM(int iDim) {
        DoubleSummaryStatistics stats = peaks.stream().filter(p -> p.getStatus() >= 0).mapToDouble(p -> p.peakDims[iDim].getLineWidth()).summaryStatistics();
        return stats;
    }
}
