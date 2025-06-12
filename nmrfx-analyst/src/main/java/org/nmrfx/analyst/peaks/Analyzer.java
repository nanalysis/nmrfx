package org.nmrfx.analyst.peaks;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.Precision;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.io.PeakWriter;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.*;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.IDBaseline2;
import org.nmrfx.processor.operations.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import static java.util.Comparator.comparing;
import static org.nmrfx.analyst.peaks.Multiplets.locatePeaks;
import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.FIT_MODE.MAXDEV;
import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.FIT_MODE.RMS;

/**
 * @author Bruce Johnson
 */
@PluginAPI("parametric")
public class Analyzer {
    private static final Logger log = LoggerFactory.getLogger(Analyzer.class);

    PeakFitParameters peakFitParameters = null;
    ConvolutionPickPar convolutionPickPar = null;
    Dataset dataset;
    PeakList peakList;
    boolean analyzed = false;

    private double trimRatio = 2.0;
    boolean scaleToLargest = true;
    int nWin = 32;
    double maxRatio = 20.0;
    double sdRatio = 30.0;

    int regionWindow = 20;
    double regionRatio = 50.0;
    double regionWidth = 1.0;
    double joinWidth = 1.0;
    double regionExtend = 9.0;

    double artifactRatio = 50;
    double threshold = 0.0;
    Double positionRestraint = null;
    Optional<Double> manThreshold = Optional.empty();
    Solvents solvents;

    public Analyzer(Dataset dataset) {
        this.dataset = dataset;
        solvents = new Solvents();
        Solvents.loadYaml();
    }

    public static Analyzer getAnalyzer(Dataset dataset) {
        Object analyzerObject = dataset.getAnalyzerObject();
        Analyzer analyzer;
        if (analyzerObject == null) {
            analyzer = new Analyzer(dataset);
            dataset.setAnalyzerObject(analyzer);
        } else {
            analyzer = (Analyzer) analyzerObject;
        }
        return analyzer;
    }

    public PeakFitParameters getFitParameters(boolean reset) {
        if (reset || (peakFitParameters == null)) {
            peakFitParameters = new PeakFitParameters();
        }
        return peakFitParameters;
    }

    public void setConvolutionPickPar(ConvolutionPickPar par) {
        this.convolutionPickPar = par;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public void setPeakList(PeakList peakList) {
        this.peakList = peakList;
    }

    public PeakList getPeakList() {
        return peakList;
    }

    public void setRegionRatio(double value) {
        regionRatio = value;
    }

    public void clearThreshold() {
        manThreshold = Optional.empty();
    }

    public void setThreshold(double value) {
        manThreshold = Optional.of(value);
    }

    public double getThreshold() {
        return manThreshold.orElseGet(() -> threshold);
    }

    void updateThreshold() {
        if (manThreshold.isPresent()) {
            threshold = manThreshold.get();
        } else {
            calculateThreshold();
        }

    }

    public void calculateThreshold() {
        threshold = PeakPicker.calculateThreshold(dataset, scaleToLargest, nWin, maxRatio, sdRatio);
    }

    public PeakList peakPick() {
        Nuclei nuc = dataset.getNucleus(0);
        if (nuc == Nuclei.H1) {

        }
        if (manThreshold.isPresent()) {
            threshold = manThreshold.get();
        } else {
            calculateThreshold();
        }
        String datasetName = dataset.getName();
        String listName = PeakList.getNameForDataset(datasetName);
        double level = threshold;
        PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(level).mode(PeakPickParameters.PickMode.REPLACEIF);
        peakPickPar.pos(true).neg(false);
        peakPickPar.calcRange();
        peakPickPar.convolvePar(convolutionPickPar);
        PeakPicker picker = new PeakPicker(peakPickPar);
        peakList = null;
        try {
            peakList = picker.peakPick();
            removePeaksFromNonRegions();
        } catch (IOException | IllegalArgumentException ex) {
            log.error(ex.getMessage(), ex);
        }
        return peakList;
    }

    public void peakPickRegions() {
        updateThreshold();
        List<DatasetRegion> regions = getReadOnlyRegions();
        for (DatasetRegion region : regions) {

            List<PeakDim> peakDims = Collections.emptyList();
            if (peakList != null) {
                peakDims = Multiplets.findPeaksInRegion(peakList, region);
            }
            if (peakDims.isEmpty()) {
                peakPickRegion(region.getRegionStart(0), region.getRegionEnd(0), threshold);
            }
        }
        setVolumesFromIntegrals();
    }

    public void peakPickRegion(DatasetRegion region) {
        updateThreshold();
        peakPickRegion(region.getRegionStart(0), region.getRegionEnd(0), threshold);
    }

    public void peakPickRegion(double ppm1, double ppm2) {
        updateThreshold();
        peakPickRegion(ppm1, ppm2, threshold);
    }

    public void peakPickRegion(double ppm1, double ppm2, double level) {
        Nuclei nuc = dataset.getNucleus(0);
        if (nuc == Nuclei.H1) {

        }
        String datasetName = dataset.getName();
        String listName = PeakList.getNameForDataset(datasetName);
        PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(level).mode(PeakPickParameters.PickMode.APPENDIF);
        peakPickPar.pos(true).neg(false);
        peakPickPar.calcRange();
        peakPickPar.limit(0, ppm1, ppm2);
        peakPickPar.convolvePar(convolutionPickPar);
        PeakPicker picker = new PeakPicker(peakPickPar);
        peakList = null;
        try {
            peakList = picker.peakPick();
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Unable to peak pick.", ex);
        }
    }

    public Optional<Double> measureRegion(DatasetRegion region, PeakFitParameters fitParameters) throws Exception {
        List<PeakDim> peakDims = Multiplets.findPeaksInRegion(peakList, region);
        Optional<Double> result = Optional.empty();
        if (peakDims.isEmpty()) {
            region.measure(dataset);
            int maxLoc = region.getMaxLocation()[0];
            double ppmLoc = dataset.pointToPPM(0, maxLoc);
            result = Optional.of(ppmLoc);
        } else {
            PeakFitting peakFitting = new PeakFitting(dataset);
            try {
                double value = peakFitting.jfitRegion(region, peakDims, fitParameters, positionRestraint);
                result = Optional.of(value);
            } catch (IllegalArgumentException | PeakFitException | IOException ex) {
                log.error("error in fit ", ex);
            }
        }
        return result;
    }

    public void removeWeakPeaksInRegion(DatasetRegion region, int nRemove) throws Exception {
        List<PeakDim> peakDims = Multiplets.findPeaksInRegion(peakList, region);
        if (peakDims.size() > 1) {
            peakDims.sort(comparing(p -> p.getPeak().getIntensity()));
            if (nRemove < 0) {
                nRemove = peakDims.size() + nRemove;
            }
            for (int i = 0; i < nRemove; i++) {
                peakDims.get(i).getPeak().setStatus(-1);
            }
            renumber();
            fitRegion(region);
        }
    }

    public void addPeaksToRegion(DatasetRegion region, double... ppms) throws Exception {
        updateThreshold();
        String datasetName = dataset.getName();
        String listName;
        if (peakList != null) {
            listName = peakList.getName();
        } else {
            listName = PeakList.getNameForDataset(datasetName);
        }
        PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(threshold).mode(PeakPickParameters.PickMode.APPENDIF);
        peakPickPar.region("point").fixed(true);
        peakPickPar.convolvePar(convolutionPickPar);
        for (double ppm : ppms) {
            peakPickPar.limit(0, ppm, ppm);
            PeakPicker picker = new PeakPicker(peakPickPar);
            try {
                peakList = picker.peakPick();
            } catch (IOException | IllegalArgumentException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        renumber();
        fitRegion(region);
    }

    public void findSolventPeaks() {
        int iDim = 0;
        String solventName = dataset.getSolvent();
        if (solventName == null) {
            solventName = "D2O";
        }

        Solvent solvent = solvents.getSolvent(solventName);
        if (solvent == null) {
            log.info("Solvent not found: {} ", solventName);
        } else {
            double sf = dataset.getSf(iDim);
            Nuclei nuc = dataset.getNucleus(iDim);
            double tmsTol = (3 * 3.3 / sf + 0.05) / 2.0;

            String nucleus = "H";
            if (nuc != null) {
                nucleus = nuc.getName();
            }
            for (Peak peak : peakList.peaks()) {
                boolean isSolvent = false;
                double shift = peak.getPeakDim(iDim).getChemShift();
                int type = 0;
                if (solvent.overlaps(nucleus, sf, shift)) {
                    type = Peak.SOLVENT;
                    isSolvent = true;
                }
                if (solvent.overlapsH2O(nucleus, sf, shift)) {
                    type = Peak.WATER;
                    isSolvent = true;
                }
                // check tms
                if (Math.abs(shift) < tmsTol) {
                    type = Peak.CHEMSHIFT_REF;
                    isSolvent = true;
                }
                if (isSolvent) {
                    List<Peak> peaks = PeakList.getLinks(peak);
                    for (Peak lPeak : peaks) {
                        lPeak.setType(type);
                    }
                }
            }
        }
    }

    public void removePeaksFromNonRegions() {
        if (peakList == null) {
            return;
        }
        int n = peakList.size();
        for (int i = 0; i < n; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak.getStatus() >= 0) {
                peak.setStatus(0);
            }
        }

        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        List<DatasetRegion> regions = getReadOnlyRegions();

        regions.forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            for (Peak peak : peaks) {
                peak.setStatus(1);

            }
        });
        deletePeaks(peakList.peaks());
        for (Peak peak : peakList.peaks()) {
            peak.setStatus(0);
        }
    }

    /**
     * Locates any peaks within region and deletes them/
     *
     * @param region The DatasetRegion to search
     */
    public void removePeaksFromRegion(DatasetRegion region) {
        if (peakList == null) {
            return;
        }
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        limits[0][0] = region.getRegionStart(0);
        limits[0][1] = region.getRegionEnd(0);
        List<Peak> peaks = locatePeaks(peakList, limits, dim);
        for (Peak peak : peaks) {
            peak.setStatus(0);
        }
        deletePeaks(peaks);
    }

    /**
     * Deletes from peakList any peaks in peaksToDelete that have status of 0. The peak list will be compressed
     * and renumbered.
     *
     * @param peaks List of peaks to check deletion status for.
     */
    private void deletePeaks(List<Peak> peaks) {
        for (Peak peak : peaks) {
            if (peak.getStatus() == 0) {
                List<Peak> lPeaks = PeakList.getLinks(peak);
                for (Peak lPeak : lPeaks) {
                    lPeak.setStatus(-1);
                }
                peak.setStatus(-1);
            }
        }
        peakList.compress();
        peakList.reNumber();
    }

    public void trimRegionsToPeaks() {
        int iDim = 0;
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        List<DatasetRegion> newRegions = new ArrayList<>();
        List<DatasetRegion> regions = getReadOnlyRegions();

        regions.forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            if (!peaks.isEmpty()) {
                double min = Double.MAX_VALUE;
                double max = Double.NEGATIVE_INFINITY;
                for (Peak peak : peaks) {
                    PeakDim peakDim = peak.getPeakDim(0);
                    Multiplet multiplet = peakDim.getMultiplet();
                    List<AbsMultipletComponent> absComps = multiplet.getAbsComponentList();

                    for (AbsMultipletComponent absComp : absComps) {
                        double ppm = absComp.getOffset();
                        double width = Math.abs(absComp.getLineWidth());
                        double tppm = ppm + getTrimRatio() * width;
                        max = Math.max(tppm, max);
                        tppm = ppm - getTrimRatio() * width;
                        min = Math.min(tppm, min);
                    }
                }
                double rStart = region.getRegionStart(iDim);
                double rEnd = region.getRegionEnd(iDim);
                if (min < rStart) {
                    min = rStart;
                }
                if (max > rEnd) {
                    max = rEnd;
                }
                DatasetRegion newRegion = new DatasetRegion(min, max);

                newRegions.add(newRegion);
            } else {
                newRegions.add(region);
            }
        });
        getDataset().clearRegions();
        getDataset().setRegions(newRegions);
    }

    public void purgeNonPeakRegions() {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        List<DatasetRegion> newRegions = new ArrayList<>();
        List<DatasetRegion> regions = getReadOnlyRegions();

        regions.forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            if (!peaks.isEmpty()) {
                newRegions.add(region);
            }
        });
        getDataset().clearRegions();
        getDataset().setRegions(newRegions);
    }

    public void removeRegion(double shift) {
        List<DatasetRegion> newRegions = new ArrayList<>();
        List<DatasetRegion> regions = getReadOnlyRegions();
        int rDim = 0;

        regions.forEach(region -> {
            double start = region.getRegionStart(rDim);
            double end = region.getRegionEnd(rDim);
            if ((shift < start) || (shift > end)) {
                newRegions.add(region);
            }
        });
        getDataset().clearRegions();
        getDataset().setRegions(newRegions);
        if (peakList != null) {
            removePeaksFromNonRegions();
        }
    }

    public void removeRegion(double ppm1, double ppm2, boolean removePeaks) {
        List<DatasetRegion> newRegions = new ArrayList<>();
        List<DatasetRegion> regions = getReadOnlyRegions();
        int rDim = 0;
        double ppmStart = Math.min(ppm1, ppm2);
        double ppmEnd = Math.max(ppm2, ppm1);
        regions.forEach(region -> {
            double start = region.getRegionStart(rDim);
            double end = region.getRegionEnd(rDim);
            if ((ppmEnd < start) || (ppmStart > end)) {
                newRegions.add(region);
            }
        });
        getDataset().clearRegions();
        getDataset().setRegions(newRegions);
        if (removePeaks && (peakList != null)) {
            removePeaksFromNonRegions();
        }
    }

    public void groupPeaks() throws IOException {
        List<DatasetRegion> regions = getReadOnlyRegions();

        Multiplets.groupPeaks(peakList, regions);
    }

    public void baselineCorrect() {
        int size = dataset.getSizeTotal(0);
        Vec vec = new Vec(size);
        int winSize = 32;
        int minBase = 32;
        double ratio = 10.0;
        double lambda = 5000.0;
        int order = 1;
        int dataDim = dataset.getNDim();
        int[][] pt = new int[dataDim][2];
        pt[0][0] = 0;
        pt[0][1] = size - 1;
        int[] dim = new int[dataDim];
        dim[0] = 0;
        try {
            dataset.readVectorFromDatasetFile(pt, dim, vec);
            boolean[] isInSignalRegion = Util.getSignalRegionByCWTD(vec, winSize, minBase, ratio, IDBaseline2.ThreshMode.SDEV);
            vec.setSignalRegion(isInSignalRegion);
            vec.bcWhit(lambda, order, false);
            dataset.writeVecToDatasetFile(pt, dim, vec);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    public void dumpRegions() {
        List<DatasetRegion> regions = getReadOnlyRegions();

        for (DatasetRegion region : regions) {
            System.out.printf("%9.4f %9.4f %9.4f %9.4f\n", region.getRegionStart(0), region.getRegionEnd(0), region.getIntegral(), region.getMax());
        }
    }

    public Optional<DatasetRegion> getRegion(double shift) {
        return getRegion(getReadOnlyRegions(), 0, shift);
    }

    public static Optional<DatasetRegion> getRegion(List<DatasetRegion> dRegions, int rDim, double shift) {
        Optional<DatasetRegion> found = Optional.empty();
        if (dRegions != null) {
            for (DatasetRegion region : dRegions) {
                double start = region.getRegionStart(rDim);
                double end = region.getRegionEnd(rDim);
                if ((start < shift) && (end >= shift)) {
                    found = Optional.of(region);
                    break;
                }
            }
        }
        return found;
    }

    public static double[] getBounds(DatasetRegion region, int rDim) {
        double start = region.getRegionStart(rDim);
        double end = region.getRegionEnd(rDim);
        return new double[]{start, end};
    }

    public static double[] getRegionBounds(List<DatasetRegion> dRegions, int rDim, double shift) {
        Optional<DatasetRegion> found = getRegion(dRegions, rDim, shift);
        double[] bounds = null;
        if (found.isPresent()) {
            bounds = getBounds(found.get(), rDim);
        }
        return bounds;
    }

    public void integrate() throws IOException {
        List<DatasetRegion> regions = getReadOnlyRegions();
        for (DatasetRegion region : regions) {
            region.measure(dataset);
        }
    }
    public void integrate(Dataset integrateDataset) throws IOException {
        List<DatasetRegion> regions = integrateDataset.getReadOnlyRegions();
        for (DatasetRegion region : regions) {
            region.measure(integrateDataset);
        }
    }

    public void setVolumesFromIntegrals() {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        List<DatasetRegion> regions = getReadOnlyRegions();

        regions.forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            double integral = region.getIntegral();
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            double sum = 0.0;
            for (Peak peak : peaks) {
                sum += peak.getIntensity();
            }
            for (Peak peak : peaks) {
                if (peak.getFlag(4)) {
                    continue;
                }
                double intensity = peak.getIntensity();
                double peakVol = integral * intensity / sum;
                peak.setVolume1((float) peakVol);
            }

        });
    }

    public void normalizePeaks(DatasetRegion region, double value) {
        List<PeakDim> peakDims = Multiplets.findPeaksInRegion(peakList, region);
        double sum = 0.0;
        for (PeakDim peakDim : peakDims) {
            double vol = peakDim.getPeak().getVolume1();
            sum += vol;
        }
        double scale = sum / value;
        if (scale < 1.0e-6) {
            scale = 1.0;
        }
        peakList.scale = scale;

    }

    public void normalizeIntegrals() {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        List<DatasetRegion> regions = getReadOnlyRegions();

        regions.forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            double integral = region.getIntegral();
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            for (Peak peak : peaks) {
                double vol = peak.getPeakDim(0).getMultiplet().getVolume();
                double nInt = vol / peak.getPeakList().getScale();
                // find the first peak normalized volume near at least 1.0
                if (nInt > 0.8) {
                    dataset.setNorm(dataset.getScale() * integral / nInt);
                    break;
                }
            }
        });
    }

    public List<DatasetRegion> getReadOnlyRegions() {
        return dataset.getReadOnlyRegions();
    }

    public void autoSetRegions() {
        Vec vec;
        try {
            vec = dataset.readVector(0, 0);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
            return;
        }
        int size = vec.getSize();
        double sw = dataset.getSw(0);
        int region = (int) Math.round(regionWidth / sw * size);
        int join = (int) Math.round(joinWidth / sw * size);
        int extend = (int) Math.round(regionExtend / sw * size);

        double minThreshold = manThreshold.orElseGet(() -> threshold);

        RealMatrix rM = vec.idIntegrals(regionWindow, regionRatio, region, join, extend, minThreshold);
        Dataset datasetToAdjust = getDataset();
        datasetToAdjust.clearRegions();
        int nRows = rM.getRowDimension();
        for (int iRow = 0; iRow < nRows; iRow++) {
            double min = rM.getEntry(iRow, 0);
            min = datasetToAdjust.pointToPPM(0, min);
            double max = rM.getEntry(iRow, 1);
            max = datasetToAdjust.pointToPPM(0, max);
            DatasetRegion newRegion = new DatasetRegion(min, max);
            newRegion.setAuto(true);
            datasetToAdjust.addRegion(newRegion);
        }
    }

    public void clearRegions() {
        getDataset().clearRegions();
    }

    public void addRegion(double min, double max, boolean pick) {
        DatasetRegion newRegion = new DatasetRegion(min, max);
        getDataset().addRegion(newRegion);
        if (pick) {
            peakPickRegion(min, max);
        }
    }

    public List<Multiplet> splitRegion(double ppm) throws IOException {
        List<DatasetRegion> regions = getReadOnlyRegions();
        Optional<DatasetRegion> found = getRegion(regions, 0, ppm);
        List<Multiplet> result = new ArrayList<>();
        PeakFitParameters fitParameters = getFitParameters(false);
        if (found.isPresent()) {
            DatasetRegion region = found.get();
            double start = region.getRegionStart(0);
            double end = region.getRegionEnd(0);
            if (start > end) {
                double hold = start;
                start = end;
                end = hold;
            }
            getDataset().removeRegion(region);
            double pt1 = dataset.ppmToDPoint(0, ppm);
            double ppm1 = dataset.pointToPPM(0, pt1 + 1);
            double ppm2 = dataset.pointToPPM(0, pt1 - 1);
            double[][] limits = new double[1][2];
            limits[0][0] = start;
            limits[0][1] = end;
            int[] dim = {0};

            DatasetRegion newRegion1 = new DatasetRegion(start, ppm1);
            DatasetRegion newRegion2 = new DatasetRegion(ppm2, end);
            getDataset().addRegion(newRegion1);
            getDataset().addRegion(newRegion2);
            integrate();
            if (peakList != null) {
                List<Peak> peaks = locatePeaks(peakList, limits, dim);
                for (Peak peak : peaks) {
                    peak.setFlag(4, false);
                }
                if (!peaks.isEmpty()) {
                    Multiplet multiplet = peaks.get(0).getPeakDim(0).getMultiplet();
                    Optional<Multiplet> splitResult = multiplet.split(ppm);
                    setVolumesFromIntegrals();
                    PeakFitting peakFitting = new PeakFitting(dataset);
                    peakFitting.fitLinkedPeak(multiplet.getOrigin(), fitParameters);
                    peakFitting.jfitLinkedPeak(multiplet.getOrigin(), fitParameters);
                    if (splitResult.isPresent()) {
                        Multiplet newMultiplet = splitResult.get();
                        peakFitting.fitLinkedPeak(newMultiplet.getOrigin(), fitParameters);
                        peakFitting.jfitLinkedPeak(newMultiplet.getOrigin(), fitParameters);
                    }
                    renumber();
                }
            }
        }
        return result;
    }

    public Optional<Multiplet> analyzeRegion(double ppm) throws IOException {
        List<DatasetRegion> regions = getReadOnlyRegions();
        Optional<Multiplet> result = Optional.empty();
        Optional<DatasetRegion> found = getRegion(regions, 0, ppm);
        if (found.isPresent()) {
            DatasetRegion region = found.get();
            PeakFitting peakFitting = new PeakFitting(dataset);

            integrate();
            setVolumesFromIntegrals();
            Multiplets.unlinkPeaksInRegion(peakList, region);
            Multiplet multiplet = Multiplets.linkPeaksInRegion(peakList, region);
            result = Optional.ofNullable(multiplet);
            if (multiplet != null) {
                PeakFitParameters fitParameters = getFitParameters(false);
                peakFitting.fitLinkedPeak(multiplet.getOrigin(), fitParameters);
                Multiplets.analyzeMultiplet(multiplet.getOrigin());
                peakFitting.jfitLinkedPeak(multiplet.getOrigin(), fitParameters);
            }
            renumber();
        }
        return result;
    }

    public void fitLinkedPeaks() {
        PeakFitting peakFitting = new PeakFitting(dataset);
        PeakFitParameters fitParameters = getFitParameters(false);
        fitParameters.fitJMode(PeakFitParameters.FITJ_MODE.JFIT);
        peakFitting.fitLinkedPeaks(peakList, fitParameters);
    }

    public Optional<Double> fitMultiplet(Multiplet multiplet) {
        Optional<Double> result = Optional.empty();
        if (multiplet != null) {
            PeakFitting peakFitting = new PeakFitting(dataset);
            Peak peak = multiplet.getPeakDim().getPeak();
            peak.setFlag(4, false);
            PeakFitParameters fitParameters = getFitParameters(false);
            double rms = peakFitting.jfitLinkedPeak(peak, fitParameters);
            result = Optional.of(rms);
        }
        return result;
    }

    public void setPositionRestraint(Double restraint) {
        this.positionRestraint = restraint;
    }

    public void fitRegions() throws Exception {
        for (DatasetRegion region : getReadOnlyRegions()) {
            fitRegion(region);
        }
    }

    public Optional<Double> fitRegion(DatasetRegion region) throws Exception {
        Optional<Double> result = Optional.empty();
        if (region != null) {
            List<PeakDim> peakDims = Multiplets.findPeaksInRegion(peakList, region);
            if (!peakDims.isEmpty()) {
                PeakFitting peakFitting = new PeakFitting(dataset);
                for (PeakDim peakDim : peakDims) {
                    peakDim.getPeak().setFlag(4, false);
                }
                PeakFitParameters fitParameters = getFitParameters(false);
                double rms = peakFitting.jfitRegion(region, peakDims, fitParameters, positionRestraint);
                result = Optional.of(rms);
            }
        }
        return result;
    }

    public void jfitLinkedPeaks() {
        PeakFitting peakFitting = new PeakFitting(dataset);
        peakFitting.jfitLinkedPeaks(peakList, getFitParameters(false));
    }

    private List<Peak> getPeaks(List<PeakDim> peakDims) {
        List<Peak> peaks = new ArrayList<>();
        for (PeakDim peakDim : peakDims) {
            peaks.add(peakDim.getPeak());
        }
        return peaks;
    }

    private Map<Peak, Peak> savePeaks(List<Peak> peaks) {
        Map<Peak, Peak> map = new HashMap<>();
        for (Peak peak : peaks) {
            if (!peak.isDeleted()) {
                map.put(peak, peak.copy());
            }
        }
        return map;
    }

    private void restorePeaks(Map<Peak, Peak> map) {
        for (Entry<Peak, Peak> entry : map.entrySet()) {
            entry.getValue().copyTo(entry.getKey());
        }
    }

    public void objectiveDeconvolution(DatasetRegion region) throws Exception {
        if (peakList == null) {
            peakPickRegion(region);
        }
        List<PeakDim> peakDims = Multiplets.findPeaksInRegion(peakList, region);
        if (peakDims.isEmpty()) {
            peakPickRegion(region);
            peakDims = Multiplets.findPeaksInRegion(peakList, region);
        }
        PeakFitting peakFitting = new PeakFitting(dataset);
        int nComps = peakDims.size();
        int nAdd;
        if (nComps < 2) {
            nAdd = 3;
        } else {
            nAdd = nComps;
        }
        Map<Peak, Peak> minList = savePeaks(getPeaks(peakDims));
        for (PeakDim peakDim : peakDims) {
            peakDim.getPeak().setFlag(4, false);
        }
        double rms = Double.MAX_VALUE;
        double minBIC = Double.MAX_VALUE;
        PeakFitParameters objDeconvFitParameters = new PeakFitParameters();
        if (!peakDims.isEmpty()) {
            rms = peakFitting.jfitRegion(region, peakDims, objDeconvFitParameters, positionRestraint);
            minBIC = peakFitting.getBIC();
        }


        for (int i = 0; i < nAdd; i++) {
            objDeconvFitParameters.fitMode(MAXDEV);
            Optional<Double> result = measureRegion(region, objDeconvFitParameters);
            if (result.isPresent()) {
                addPeaksToRegion(region, result.get());
                peakDims = Multiplets.findPeaksInRegion(peakList, region);
                rms = peakFitting.jfitRegion(region, peakDims, objDeconvFitParameters, positionRestraint);
                double BIC = peakFitting.getBIC();
                if (BIC < minBIC) {
                    minBIC = BIC;
                    minList = savePeaks(getPeaks(Multiplets.findPeaksInRegion(peakList, region)));
                }
            }
        }
        peakDims = Multiplets.findPeaksInRegion(peakList, region);
        for (PeakDim peakDim : peakDims) {
            peakDim.getPeak().setStatus(-1);
        }
        restorePeaks(minList);
        renumber();
        peakDims = Multiplets.findPeaksInRegion(peakList, region);
        double limit = 15.0;
        if (peakDims.size() > nComps) {
            objDeconvFitParameters.fitMode(PeakFitParameters.FIT_MODE.ALL);
            int n = peakDims.size();
            for (int j = 0; j < n - 1; j++) {

                // fixme need to restore peaks to original position
                double minSkipBIC = Double.MAX_VALUE;
                int minSkip = -1;
                Map<Peak, Peak> tempList = savePeaks(getPeaks(peakDims));
                Map<Peak, Peak> bestList = null;
                for (int i = 0; i < n; i++) {
                    restorePeaks(tempList);
                    if (!peakDims.get(i).getPeak().isDeleted()) {
                        peakDims.get(i).getPeak().setStatus(-1);
                        rms = peakFitting.jfitRegion(region, peakDims, objDeconvFitParameters, positionRestraint);
                        double BIC = peakFitting.getBIC();
                        peakDims.get(i).getPeak().setStatus(0);
                        if (BIC < minSkipBIC) {
                            minSkip = i;
                            minSkipBIC = BIC;
                            bestList = savePeaks(getPeaks(peakDims));
                        }
                    }
                }
                if (minSkipBIC < (minBIC + limit)) {
                    if (bestList != null) {
                        restorePeaks(bestList);
                        objDeconvFitParameters.fitMode(RMS);
                        measureRegion(region, objDeconvFitParameters);
                        peakDims.get(minSkip).getPeak().setStatus(-1);
                        if (minSkipBIC < minBIC) {
                            minBIC = minSkipBIC;
                        }
                    }
                } else {
                    restorePeaks(tempList);
                    objDeconvFitParameters.fitMode(RMS);
                    measureRegion(region, objDeconvFitParameters);
                    break;
                }
            }
        }
        objDeconvFitParameters.fitMode(RMS);
        Optional<Double> rmsOpt = measureRegion(region, objDeconvFitParameters);
        renumber();
        peakDims = Multiplets.findPeaksInRegion(peakList, region);
        rms = peakFitting.jfitRegion(region, peakDims, objDeconvFitParameters, positionRestraint);
        double BIC = peakFitting.getBIC();
    }

    public void objectiveDeconvolution(Multiplet multiplet) {
        PeakFitting peakFitting = new PeakFitting(dataset);
        PeakFitParameters fitParameters = new PeakFitParameters();
        int nComps = multiplet.getRelComponentList().size();
        int nAdd;
        if (nComps < 2) {
            nAdd = 3;
        } else {
            nAdd = nComps;
        }
        List<AbsMultipletComponent> minList = null;
        multiplet.getOrigin().setFlag(4, false);
        double rms = peakFitting.jfitLinkedPeak(multiplet.getOrigin(), fitParameters);
        double minBIC = peakFitting.getBIC();
        for (int i = 0; i < nAdd; i++) {
            Optional<Double> result = Multiplets.deviation(multiplet);
            if (result.isPresent()) {
                Multiplets.addPeaksToMultiplet(multiplet, result.get());
                multiplet.getOrigin().setFlag(4, false);
                rms = peakFitting.jfitLinkedPeak(multiplet.getOrigin(), fitParameters);
                double bic = peakFitting.getBIC();
                if (bic < minBIC) {
                    minBIC = bic;
                    minList = multiplet.getAbsComponentList();
                }
            }
        }
        List<AbsMultipletComponent> minSkipList = null;
        double limit = 15.0;

        if (minList != null) {
            multiplet.updateCoupling(minList);
            while (true) {
                double minSkipBIC = Double.MAX_VALUE;
                int n = minList.size();
                for (int i = 0; i < n; i++) {
                    List<AbsMultipletComponent> newList = AbsMultipletComponent.copyList(minList, i);
                    multiplet.updateCoupling(newList);
                    multiplet.getOrigin().setFlag(4, false);
                    peakFitting.jfitLinkedPeak(multiplet.getOrigin(), fitParameters);
                    double BIC = peakFitting.getBIC();
                    if (BIC < minSkipBIC) {
                        minSkipBIC = BIC;
                        minSkipList = multiplet.getAbsComponentList();
                    }
                }
                //fixme save components after fitting System
                //.out.println("min skip " + minSkip + " " + minSkipBIC);
                if (minSkipBIC < (minBIC + limit)) {
                    multiplet.updateCoupling(minSkipList);
                    minList = AbsMultipletComponent.copyList(minSkipList);
                    if (minSkipBIC < minBIC) {
                        minBIC = minSkipBIC;
                    }
                } else {
                    multiplet.updateCoupling(minList);
                    break;
                }
            }
        }
    }

    public void analyzeMultiplets() {
        Multiplets.analyzeMultiplets(peakList);
    }

    public void dumpMultiplets() {
        for (Peak peak : peakList.peaks()) {
            if (peak.isDeleted()) {
                continue;
            }
            Multiplet multiplet = peak.getPeakDim(0).getMultiplet();
            System.out.println(multiplet.getPeakDim().getPeak().getName() + " "
                    + multiplet.getPeakDim().getChemShift() + " " + multiplet.getCouplingsAsString() + " " + Multiplets.getCouplingPattern(multiplet) + " " + multiplet.getVolume() / peakList.scale);

        }
    }

    public void normalizeMultiplets() {
        List<Double> aromaticValues = new ArrayList<>();
        List<Double> aliphaticValues = new ArrayList<>();
        for (Peak peak : peakList.peaks()) {
            if (peak.isDeleted()) {
                continue;
            }
            Multiplet multiplet = peak.getPeakDim(0).getMultiplet();
            if (peak.getType() == Peak.COMPOUND) {
                double shift = multiplet.getCenter();
                double volume = multiplet.getVolume();
                if (shift > 5.5) {
                    aromaticValues.add(volume);
                } else if (shift < 4.0) {
                    aliphaticValues.add(volume);
                }
            }
        }
        double norm = 1.0;
        if (aromaticValues.size() > 1) {
            norm = calculateNormalization(aromaticValues);
        } else if (aliphaticValues.size() > 1) {
            norm = calculateNormalization(aliphaticValues);
        }
        peakList.scale = norm;
    }

    public double calculateNormalization(List<Double> values) {
        int maxProtons = 6;
        double bestScore = Double.MAX_VALUE;
        double bestNorm = 1.0;
        for (Double refValue : values) {
            int goodNorm = 0;
            double tScore = 0.0;
            for (Double value : values) {
                double norm = value / refValue;
                int rnorm = (int) Math.round(norm);
                double dnom = norm;
                if (rnorm == 1) {
                    goodNorm = 1;

                }
                if (rnorm > maxProtons) {
                    rnorm = maxProtons;
                    dnom = maxProtons;
                }
                if (rnorm < 1) {
                    rnorm = 0;
                    dnom = 1;
                }
                tScore += Math.abs(rnorm - norm) / dnom;
            }
            tScore = tScore - 1.0 * goodNorm;
            if (tScore < bestScore) {
                bestNorm = refValue;
                bestScore = tScore;
            }

        }
        return bestNorm;
    }

    double getSmallPeakThreshold() {
        List<Double> intensities = new ArrayList<>();
        List<DatasetRegion> regions = getReadOnlyRegions();
        regions.forEach(region -> intensities.add(region.getMax()));
        Collections.sort(intensities);
        int n = intensities.size();
        // fixme rethink this
        int index = n - 10;
        if (index < 0) {
            index = 0;
        }
        return intensities.get(index);
    }

    public void purgeSmallPeaks() {
        int n = peakList.size();
        if (n == 0) {
            return;
        }
        double representativePeakHeight = getSmallPeakThreshold();
        double smThreshold = representativePeakHeight / artifactRatio;
        double globalMin = smThreshold;
        double[][] limits = new double[1][2];
        int[] dim = new int[1];
        List<DatasetRegion> regions = getReadOnlyRegions();

        regions.forEach(region -> {
            double localThreshold = globalMin;
            double localMax = Double.NEGATIVE_INFINITY;
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            List<Peak> peaks = locatePeaks(peakList, limits, dim);

            for (Peak peak : peaks) {
                double intensity = peak.getIntensity();
                if (intensity > localMax) {
                    localMax = intensity;
                }
            }
            boolean autoRegion = region.isAuto();
            if (!autoRegion || (localMax > globalMin)) {
                localThreshold = localMax / artifactRatio;
            }
            for (Peak peak : peaks) {
                if (peak.getIntensity() < localThreshold) {
                    peak.setStatus(-1);
                }
            }
        });
        peakList.compress();
        // don't renumber here as it will mess up dcsProcessNewPeak

    }

    public void renumber() {
        peakList.sortPeaks(0, true);
        peakList.compress();
        peakList.reNumber();
    }

    public void writeList(String listFileName) throws IOException, InvalidPeakException {
        try (FileWriter writer = new FileWriter(listFileName)) {
            PeakWriter peakWriter = new PeakWriter();
            peakWriter.writePeaksXPK2(writer, peakList);
        }
    }

    public void analyze() throws IOException {
        // clear
        //baselineCorrect();
        // autoReference

        // clearRegions
        // auto set regions
        calculateThreshold();
        if (getReadOnlyRegions().isEmpty()) {
            autoSetRegions();
            integrate();
            peakList = null;
        }
        if (peakList == null) {
            PeakList pList = peakPick();
            purgeNonPeakRegions();
            renumber();
        }
        groupPeaks();
        renumber();
        setVolumesFromIntegrals();
        fitLinkedPeaks();
        renumber();
        purgeSmallPeaks();
        purgeNonPeakRegions();
        analyzeMultiplets();
        jfitLinkedPeaks();
        trimRegionsToPeaks();
        dumpMultiplets();
        dumpRegions();
        removePeaksFromNonRegions();
        integrate();
        dumpRegions();
        findSolventPeaks();
        renumber();
        normalizeMultiplets();
        normalizeIntegrals();
        analyzed = true;
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public void resetAnalyzed() {
        analyzed = false;
    }

    public void clearAnalysis() {
        analyzed = false;
        PeakList peakList = getPeakList();
        if (peakList != null) {
            PeakList.remove(peakList.getName());
            setPeakList(null);
        }
        clearRegions();
    }

    public void loadRegions(File regionFile) throws IOException {
        if (regionFile.canRead()) {
            List<DatasetRegion> regions = DatasetRegion.loadRegions(regionFile);
            if (!DatasetRegion.isLongRegionFile(regionFile)) {
                for (DatasetRegion region : regions) {
                    region.measure(getDataset());
                }
            }
            // Assuming a that a value of 1.0 means the norm hasn't been set
            if (Precision.equals(dataset.getNorm(), 1.0, 1e-9)) {
                dataset.setNormFromRegions(regions);
            }
            dataset.setRegions(regions);
        }
    }

    public void saveRegions(File regionFile) {
        List<DatasetRegion> regions = dataset.getReadOnlyRegions();
        if (!regions.isEmpty()) {
            DatasetRegion.saveRegions(regionFile, dataset.getReadOnlyRegions());
        }
    }

    public double getTrimRatio() {
        return trimRatio;
    }

    public void setTrimRatio(double trimRatio) {
        this.trimRatio = trimRatio;
    }
}
