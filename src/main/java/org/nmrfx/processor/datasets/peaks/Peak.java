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

import org.nmrfx.peaks.Coupling;
import org.nmrfx.peaks.CouplingPattern;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.processor.datasets.Dataset;
import java.io.IOException;

import java.util.*;
import static java.util.Comparator.comparing;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.peaks.PeakListBase;
import org.nmrfx.processor.datasets.RegionData;

public class Peak extends org.nmrfx.peaks.PeakBase {

    static {
        int j = 1;

        for (int i = 0; i < N_TYPES; i++) {
            peakTypes[i] = typeToString(j);
            j *= 2;
        }
    }
    

    public Peak(int nDim) {
        super(nDim);
        flag = new boolean[NFLAGS];
        setComment("");
        for (int i = 0; i < NFLAGS; i++) {
            setFlag(i, false);
        }
        for (int i = 0; i < nDim; i++) {
            peakDims[i] = new PeakDim(this, i);
        }
        setStatus(0);
    }

    public Peak(PeakList peakList, int nDim) {
        this(nDim);
        this.peakList = peakList;
        idNum = peakList.idLast + 1;
        peakList.idLast += 1;
    }

    public Peak copy() {
        Peak newPeak = new Peak(peakDims.length);
        newPeak.figureOfMerit = figureOfMerit;
        newPeak.valid = valid;
        newPeak.volume1 = volume1;
        newPeak.intensity = intensity;
        newPeak.volume2 = volume2;
        newPeak.volume1Err = volume1Err;
        newPeak.intensityErr = intensityErr;
        newPeak.volume2Err = volume2Err;
        newPeak.type = type;
        newPeak.status = status;
        newPeak.comment = comment;
        newPeak.flag = flag.clone();
        newPeak.corner = new Corner(corner.getCornerChars());
        for (int i = 0; i < peakDims.length; i++) {
            peakDims[i].copyTo(newPeak.peakDims[i]);
        }
        return newPeak;
    }

    public Peak copyTo(Peak targetPeak) {
        targetPeak.figureOfMerit = figureOfMerit;
        targetPeak.valid = valid;
        targetPeak.volume1 = volume1;
        targetPeak.intensity = intensity;
        targetPeak.volume2 = volume2;
        targetPeak.volume1Err = volume1Err;
        targetPeak.intensityErr = intensityErr;
        targetPeak.volume2Err = volume2Err;
        targetPeak.type = type;
        targetPeak.status = status;
        targetPeak.comment = comment;
        targetPeak.flag = flag.clone();
        targetPeak.corner = new Corner(corner.getCornerChars());
        for (int i = 0; i < peakDims.length; i++) {
            peakDims[i].copyTo(targetPeak.peakDims[i]);
        }
        return targetPeak;
    }

    public Peak copy(PeakList peakList) {
        Peak newPeak = new Peak(peakList, peakDims.length);
        newPeak.figureOfMerit = figureOfMerit;
        newPeak.valid = valid;
        newPeak.volume1 = volume1;
        newPeak.intensity = intensity;
        newPeak.volume2 = volume2;
        newPeak.volume1Err = volume1Err;
        newPeak.intensityErr = intensityErr;
        newPeak.volume2Err = volume2Err;
        newPeak.type = type;
        newPeak.status = status;
        newPeak.comment = comment;
        newPeak.flag = flag.clone();
        newPeak.corner = new Corner(corner.getCornerChars());
        for (int i = 0; i < peakDims.length; i++) {
            newPeak.peakDims[i] = peakDims[i].copy(newPeak);
        }
        return newPeak;
    }

    public void copyLabels(Peak newPeak) {
        for (int i = 0; i < peakDims.length; i++) {
            peakDims[i].copyLabels(newPeak.peakDims[i]);
        }
    }

    public void peakUpdated(Object object) {
        if (peakList != null) {
            peakList.peakListUpdated(this);
        }
    }

    @Override
    public PeakList getPeakList() {
        return (PeakList) peakList;
    }

    public String getName() {
        return peakList.getName() + "." + getIdNum();
    }


    /**
     * Get the boundaries, center and widths of the region of a peak in a
     * specified dataset. The indices of the arrays containing this information
     * are the dataset dimensions. So p[0][0] and p[0][1] will contain borders
     * of the peak along dimension 0 of the dataset, which may be a different
     * dimension than dimension 0 of the peak.
     *
     * @param theFile The dataset to use for translating ppm to pts
     * @param pdim An integer mapping of peak dimension to dataset dimension.
     * For example, pdim[0] contains the dataset dimension that corresponds to
     * peak dimension 0.
     * @param p Two-dimensional pre-allocated array of int that will contain the
     * boundaries of the peak dimension. The boundaries are determined by the
     * peak foot print (bounds).
     * @param cpt Array of ints specifying the center of the peak region.
     * @param width Array of doubles containing the widths of the peak in units
     * of dataset points. The width is determined by the peak linewidth
     */
    public void getPeakRegion(Dataset theFile, int[] pdim, int[][] p,
            int[] cpt, double[] width) {
        double p1;
        double p2;
        double p1d;
        double p2d;

        for (int i = 0; i < peakList.nDim; i++) {
            double pc = peakDims[i].getChemShiftValue();

            p1 = pc + Math.abs(peakDims[i].getBoundsValue()) / 2;
            p[pdim[i]][0] = theFile.ppmToFoldedPoint(pdim[i], p1);

            p2 = pc - Math.abs(peakDims[i].getBoundsValue()) / 2;
            p[pdim[i]][1] = theFile.ppmToFoldedPoint(pdim[i], p2);
//            System.out.println(i + " " + pdim[i] + " " + p1 + " " + p[pdim[i]][0] + " " + p2 + " " + p[pdim[i]][1]);
            cpt[pdim[i]] = theFile.ppmToFoldedPoint(pdim[i], pc);

            p1 = peakDims[i].getChemShiftValue() + (Math.abs(peakDims[i].getLineWidthValue()) / 2.0);
            p1d = theFile.ppmToDPoint(pdim[i], p1);
            p2 = peakDims[i].getChemShiftValue() - (Math.abs(peakDims[i].getLineWidthValue()) / 2.0);
            p2d = theFile.ppmToDPoint(pdim[i], p2);
            width[pdim[i]] = Math.abs(p2d - p1d);
        }
    }

    void fold(int iDim, String foldDir)
            throws IllegalArgumentException {
        int iUpDown = 0;

        switch (foldDir) {
            case "up":
                iUpDown = 1;
                break;
            case "down":
                iUpDown = -1;
                break;
            default:
                throw new IllegalArgumentException(
                        "nv_peak fold: Invalid direction " + foldDir);
        }

        peakDims[iDim].setChemShiftValueNoCheck((float) (peakDims[iDim].getChemShiftValue()
                + ((iUpDown * peakList.getSpectralDim(iDim).getSw()) / peakList.getSpectralDim(iDim).getSf())));
    }

    public double[] measurePeak(Dataset dataset, int[] pdim, int[] planes, Function<RegionData, Double> f, String mode) throws IOException {
        RegionData regionData = analyzePeakRegion(dataset, planes, pdim);
        double value = f.apply(regionData);
        Double noise = dataset.getNoiseLevel();
        double err = 0.0;
        if (noise != null) {
            int nPoints = regionData.getNpoints(mode);
            err = nPoints == 1 ? noise.floatValue() : Math.sqrt(nPoints) * noise.floatValue();
        }
        double[] result = {value, err};
        return result;
    }

    public void setMeasures(double[][] values) {
        measures = Optional.of(values);
    }

    public Optional<double[][]> getMeasures() {
        return measures;
    }

    public void quantifyPeak(Dataset dataset, int[] pdim, Function<RegionData, Double> f, String mode) throws IOException, IllegalArgumentException {
        int[] planes = new int[0];
        RegionData regionData = analyzePeakRegion(dataset, planes, pdim);
        double value = f.apply(regionData);
        if (mode.contains("volume")) {
            volume1 = (float) value;
            Double noise = dataset.getNoiseLevel();
            if (noise != null) {
                int nPoints = mode.equals("evolume") ? regionData.getNEllipticalPoints() : regionData.getNpoints();
                volume1Err = (float) Math.sqrt(nPoints) * noise.floatValue();
            }
        } else {
            intensity = (float) value;
            Double noise = dataset.getNoiseLevel();
            if (noise != null) {
                intensityErr = noise.floatValue();
            }
        }
    }

    public static Function<RegionData, Double> getMeasureFunction(String mode) {
        Function<RegionData, Double> f;
        switch (mode) {
            case "center":
                f = RegionData::getCenter;
                break;
            case "jitter":
                f = RegionData::getJitter;
                break;
            case "max":
                f = RegionData::getMax;
                break;
            case "min":
                f = RegionData::getMin;
                break;
            case "extreme":
                f = RegionData::getExtreme;
                break;
            case "volume":
                f = RegionData::getVolume_r;
                break;
            case "evolume":
                f = RegionData::getVolume_e;
                break;
            case "tvolume":
                f = RegionData::getVolume_t;
                break;
            default:
                f = null;
        }
        return f;
    }

    public void tweak(Dataset dataset, int[] pdim, int[] planes) throws IOException {
        RegionData regionData = analyzePeakRegion(dataset, planes, pdim);
        double[] maxPoint = regionData.getMaxDPoint();
        for (int i = 0; i < peakDims.length; i++) {
            PeakDim tweakDim = peakDims[i];
            if (!tweakDim.isFrozen()) {
                //int iDim = regionData
                double position = dataset.pointToPPM(pdim[i], maxPoint[pdim[i]]);
                tweakDim.setChemShiftValue((float) position);
            }
        }
    }

    public RegionData analyze() throws IOException {
        Dataset dataset = Dataset.getDataset(getPeakList().getDatasetName());
        if (dataset == null) {
            throw new IllegalArgumentException("No dataset");
        }
        return analyze(dataset);
    }

    public RegionData analyze(Dataset dataset) throws IOException {
        int[] planes = new int[0];
        return analyzePeakRegion(dataset, planes);
    }

    public RegionData analyzePeakRegion(Dataset theFile, int[] planes)
            throws IOException {
        int dataDim = theFile.getNDim();
        if (dataDim != (peakList.nDim + planes.length)) {
            throw new IllegalArgumentException("Number of peak list dimensions not equal to number of dataset dimensions");
        }
        int[] pdim = ((PeakList) peakList).getDimsForDataset(theFile);
        return analyzePeakRegion(theFile, planes, pdim);
    }

    public RegionData analyzePeakRegion(Dataset theFile, int[] planes, int[] pdim)
            throws IOException {
        int dataDim = theFile.getNDim();
        int[][] p = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];
        int[] dim = new int[dataDim];

        if (dataDim != (peakList.nDim + planes.length)) {
            throw new IllegalArgumentException("Number of peak list dimensions not equal to number of dataset dimensions");
        }

        int k = 0;
        getPeakRegion(theFile, pdim, p, cpt, width);

        for (int i = 0; i < dataDim; i++) {
            dim[i] = i;

            boolean ok = false;

            for (int j = 0; j < peakList.nDim; j++) {
                if (pdim[j] == i) {
                    ok = true;
                }
            }

            if (!ok) {
                cpt[i] = p[i][1] = p[i][0] = planes[k];
                width[i] = 0.0;
                k++;
            }
        }

        RegionData regionData = theFile.analyzeRegion(p, cpt, width, dim);
        return regionData;
    }

    public int getClusterOriginPeakID(int searchDim) {
        int origin = -1;
        int nDim = getNDim();
        int startDim = searchDim;
        int lastDim = searchDim;
        if (searchDim < 0) {
            startDim = 0;
            lastDim = nDim - 1;
        }
        if (PeakList.clusterOrigin != null) {
            for (int iDim = startDim; iDim <= lastDim; iDim++) {
                List<PeakDim> linkedPeakDims = PeakList.getLinkedPeakDims(this, iDim);
                for (int i = 0, n = linkedPeakDims.size(); i < n; i++) {
                    PeakDim linkedDim = (PeakDim) linkedPeakDims.get(i);
                    if (linkedDim.getPeak().peakList == PeakList.clusterOrigin) {
                        origin = linkedDim.getPeak().getIdNum();
                        break;
                    }
                }
                if (origin != -1) {
                    break;
                }
            }
        }
        return origin;
    }

    public String toSparkyString() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
//      ?-?-?  125.395   55.758    8.310      2164733.500
        result.append("   ");
        for (int i = 0; i < getNDim(); i++) {
            String label = peakDims[i].getLabel();
            if (label.equals("")) {
                label = "?";
            }
            if (i > 0) {
                result.append("-");
            }
            result.append(label);
        }
        result.append(sep);
        for (int i = 0; i < getNDim(); i++) {
            result.append(String.format("%8.4f", peakDims[i].getChemShiftValue())).append(sep);
        }
        result.append(String.format("%14.3f", 1.0e6 * getIntensity()));
        return (result.toString());
    }

    public static Peak LinkStringToPeak(String string) {
        Peak peak = null;

        if (string.length() > 3) {
            int start;
            // System.out.println(string);
            if (string.charAt(1) == ':') {
                start = 2;
            } else {
                start = 0;
            }

            peak = PeakList.getAPeak(string.substring(start));
        }

        return (peak);
    }

    public static int LinkStringToDim(String string) {
        int start;

        if (string.charAt(2) == ':') {
            start = 2;
        } else {
            start = 0;
        }

        if (string.length() < 3) {
            return (0);
        }

        return (PeakList.getPeakDimNum(string.substring(start)));
    }

    public double distance(Peak bPeak, double[] scale)
            throws IllegalArgumentException {
        double sum = 0.0;

        if (peakDims.length != bPeak.peakDims.length) {
            throw new IllegalArgumentException(
                    "peaks don't have same number of dimensions");
        }

        for (int i = 0; i < peakDims.length; i++) {
            double dif = peakDims[i].getChemShiftValue() - bPeak.peakDims[i].getChemShiftValue();
            sum += ((dif / scale[i]) * (dif / scale[i]));
        }

        return Math.sqrt(sum);
    }

    public boolean isLinked(int dim) {
        if ((dim < 0) || (dim >= peakDims.length)) {
            return false;
        } else {
            return peakDims[dim].isLinked();
        }
    }

    class CouplingUpdate {

        double origValue;
        int direction;
        int iCoupling;
    }

    private CouplingUpdate startUpdateCouplings(int iDim) {
        CouplingUpdate cUpdate;
        PeakDim pDim = peakDims[iDim];

        List<PeakDim> links = pDim.getLinkedPeakDims();
        links.sort(comparing(PeakDim::getChemShift));

        int iPos = 0;
        for (PeakDim itDim : links) {
            if (itDim == pDim) {
                break;
            }
            iPos++;
        }
        Coupling coupling = pDim.getMultiplet().getCoupling();
        double[] values = null;
        if (coupling != null) {
            if (coupling instanceof CouplingPattern) {
                values = ((CouplingPattern) coupling).getValues();
            }
        }
        cUpdate = new CouplingUpdate();
        int nLinks = links.size();
        int iCoupling;
        if (iPos < (nLinks / 2)) {
            iCoupling = iPos;
            cUpdate.direction = 1;
        } else {
            iCoupling = nLinks - iPos - 1;
            cUpdate.direction = -1;
        }
        if (values != null) {
            if (iCoupling < values.length) {
                cUpdate.iCoupling = values.length - iCoupling - 1;
                cUpdate.origValue = values[cUpdate.iCoupling];
            } else {
                cUpdate.iCoupling = -1;
                cUpdate.origValue = 0.0;
            }

        }

        return cUpdate;
    }

    public int getDerivationSet() {
        return 0;
    }

    public boolean inRegion(double[][] limits, double[][] foldLimits, int[] dim) {
        int nSearchDim = limits.length;
        boolean ok = true;
        for (int j = 0; j < nSearchDim; j++) {
            if ((dim.length <= j) || (dim[j] == -1) || (dim[j] >= peakDims.length)) {
                continue;
            }
            double ctr = peakDims[dim[j]].getChemShiftValue();
            if ((foldLimits != null) && (foldLimits[j] != null)) {
                double fDelta = Math.abs(foldLimits[j][0] - foldLimits[j][1]);
                ctr = peakList.foldPPM(ctr, fDelta, foldLimits[j][0], foldLimits[j][1]);
            }

            if ((ctr < limits[j][0]) || (ctr > limits[j][1])) {
                ok = false;
//                System.out.println(j + " " + limits[j][0] + " " + limits[j][1] + " " + ctr);
                break;
            }

        }
        return ok;

    }

    public boolean overlaps(final Peak peak, final int dim) {
        return overlaps(peak, dim, 1.0);
    }

    public boolean overlaps(final Peak peak, final int dim, final double scale) {
        boolean result = false;
        PeakDim pdimA = getPeakDim(dim);
        PeakDim pdimB = peak.getPeakDim(dim);
        double ctrA = pdimA.getChemShiftValue();
        double bouA = pdimA.getBoundsValue();
        double ctrB = pdimB.getChemShiftValue();
        double bouB = pdimB.getBoundsValue();
        if (ctrA > ctrB) {
            if ((ctrA - scale * bouA / 2.0) < (ctrB + scale * bouB / 2.0)) {
                result = true;
            }
        } else if ((ctrA + scale * bouA / 2.0) > (ctrB - scale * bouB / 2.0)) {
            result = true;
        }
        return result;
    }

    public boolean overlapsLineWidth(final Peak peak, final int dim, final double scale) {
        boolean result = false;
        PeakDim pdimA = getPeakDim(dim);
        PeakDim pdimB = peak.getPeakDim(dim);
        double ctrA = pdimA.getChemShiftValue();
        double widA = pdimA.getLineWidthValue();
        double ctrB = pdimB.getChemShiftValue();
        double widB = pdimB.getLineWidthValue();
        if (ctrA > ctrB) {
            if ((ctrA - scale * widA / 2.0) < (ctrB + scale * widB / 2.0)) {
                result = true;
            }
        } else if ((ctrA + scale * widA / 2.0) > (ctrB - scale * widB / 2.0)) {
            result = true;
        }
        return result;
    }

    public List<Set<Peak>> getOverlapLayers(double scale) {
        List<Set<Peak>> result = new ArrayList<>();
        Set<Peak> firstLayer = getOverlappingPeaks(scale);
        Set<Peak> secondLayer = new HashSet<>();
        for (Peak peak : firstLayer) {
            Set<Peak> overlaps = peak.getOverlappingPeaks(scale);
            for (Peak peak2 : overlaps) {
                if ((peak2 != this) && !firstLayer.contains(peak2)) {
                    secondLayer.add(peak2);
                }
            }
        }
        Set<Peak> centerLayer = new HashSet<>();
        centerLayer.add(this);
        result.add(centerLayer);
        result.add(firstLayer);
        result.add(secondLayer);
        return result;
    }

    public Set<Peak> getOverlappingPeaks(Set<Peak> overlaps) {
        Set<Peak> result = new HashSet<>();
        result.addAll(overlaps);
        for (Peak peak : overlaps) {
            Set<Peak> newSet = peak.getOverlappingPeaks();
            result.addAll(newSet);
        }
        return result;
    }

    public Set<Peak> getAllOverlappingPeaks() {
        Set<Peak> overlaps = new HashSet<>();
        overlaps.add(this);
        int size = 0;
        while (overlaps.size() > size) {
            size = overlaps.size();
            overlaps = getOverlappingPeaks(overlaps);
        }
        return overlaps;
    }

    public Set<Peak> getOverlappingPeaks() {
        return getOverlappingPeaks(1.0);
    }

    public Set<Peak> getOverlappingPeaks(double scale) {
        Set<Peak> overlaps = new HashSet<>();
        int nDim = peakList.nDim;
        for (int i = 0; i < peakList.size(); i++) {
            Peak peak = getPeakList().getPeak(i);
            if ((peak.getStatus() < 0) || (this == peak)) {
                continue;
            }
            boolean ok = true;
            for (int iDim = 0; iDim < nDim; iDim++) {
                if (!overlapsLineWidth(peak, iDim, scale)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                overlaps.add(peak);
            }
        }
        return overlaps;
    }

    public void fit() {
        Dataset dataset = Dataset.getDataset(this.getPeakList().fileName);
        try {
            PeakList.peakFit(dataset, this);
        } catch (IllegalArgumentException | IOException | PeakFitException ex) {
            Logger.getLogger(Peak.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
