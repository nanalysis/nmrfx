package org.nmrfx.peaks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmrfx.processor.datasets.peaks.Peak;

public class PeakListBase {

    static ResonanceFactory resFactory = new ResonanceFactory();
    public int idLast;
    protected String listName;
    protected final int listID;
    protected String details = "";
    protected String sampleLabel = "";
    protected String sampleConditionLabel = "";
    protected List<PeakBase> peaks;
    protected final Map<Integer, PeakBase> indexMap = new HashMap<>();
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
        this.listID = listNum;
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
     * @param newPeak
     */
    public PeakBase addPeak(PeakBase newPeak) {
        newPeak.initPeakDimContribs();
        peaks.add(newPeak);
        clearIndex();
        return newPeak;
    }

    /**
     *
     * @return
     */
    public PeakBase getNewPeak() {
        PeakBase peak = new PeakBase(this, nDim);
        addPeak(peak);
        return peak;
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
}
