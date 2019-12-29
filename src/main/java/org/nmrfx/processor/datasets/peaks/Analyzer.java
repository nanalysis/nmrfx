package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.Nuclei;
import org.nmrfx.processor.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.peaks.io.PeakWriter;
import static org.nmrfx.processor.datasets.peaks.Multiplets.locatePeaks;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.Vec.IndexValue;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.linear.RealMatrix;
import org.nmrfx.processor.operations.IDBaseline2;
import org.nmrfx.processor.operations.Util;

/**
 *
 * @author Bruce Johnson
 */
public class Analyzer {

    final Dataset dataset;
    PeakList peakList;

    double trimRatio = 2.0;
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
    double sDev = 0.0;
    double threshold = 0.0;
    double filter = 0.0;
    Optional<Double> manThreshold = Optional.empty();
    Solvents solvents;

    public Analyzer(Dataset dataset) {
        this.dataset = dataset;
        solvents = new Solvents();
        solvents.loadYaml();
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
        if (manThreshold.isPresent()) {
            return manThreshold.get();
        } else {
            return threshold;
        }
    }

    public void calculateThreshold() {
        int size = dataset.getSize(0);
        Vec vec = new Vec(size);

        int dataDim = dataset.getNDim();
        int[][] pt = new int[dataDim][2];
        pt[0][0] = 0;
        pt[0][1] = size - 1;
        int[] dim = new int[dataDim];
        dim[0] = 0;

        try {
            dataset.readVectorFromDatasetFile(pt, dim, vec);
        } catch (IOException ex) {
            System.out.println("failed to get dataset vector");
            Logger.getLogger(Analyzer.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        sDev = vec.sdev(32); // fixme correct winsize
        if (scaleToLargest) {
            int nIncr = size / nWin;
            List<Double> maxs = new ArrayList<>();
            for (int i = 0; i < size; i += nIncr) {
                int j = i + nIncr - 1;
                IndexValue indexVal = vec.maxIndex(i, j);
                double max = indexVal.getValue();
                maxs.add(max);
            }
            Collections.sort(maxs);
            int nMax = maxs.size();
            double max = maxs.get(nMax - 3);

            double min = maxs.get(0);
            threshold = max / maxRatio;
            filter = sDev / max * 2400.0;
        }
        if (threshold < sdRatio * sDev) {
            if (dataset.getNucleus(0) == Nuclei.H1) {
                threshold = sdRatio * sDev;
                System.out.println(sdRatio + " " + sDev);
            } else {
                threshold = sdRatio / 5.0 * sDev;
            }
        }
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
        System.out.println("level " + level);
        PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(level).mode("replaceif");
        peakPickPar.pos(true).neg(false);
        peakPickPar.calcRange();
        PeakPicker picker = new PeakPicker(peakPickPar);
        peakList = null;
        try {
            peakList = picker.peakPick();
            removePeaksFromNonRegions();
        } catch (IOException ex) {
            Logger.getLogger(Analyzer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(Analyzer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return peakList;
    }

    public void peakPickRegion(double ppm1, double ppm2) {
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
        PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(level).mode("appendif");
        peakPickPar.pos(true).neg(false);
        peakPickPar.calcRange();
        peakPickPar.limit(0, ppm1, ppm2);
        PeakPicker picker = new PeakPicker(peakPickPar);
        peakList = null;
        try {
            peakList = picker.peakPick();
        } catch (IOException | IllegalArgumentException ex) {
        }
    }

    public void findSolventPeaks() {
        int iDim = 0;
        String solventName = dataset.getSolvent();
        if (solventName == null) {
            solventName = "D2O";
        }

        Solvent solvent = solvents.getSolvent(solventName);
        if (solvent == null) {
            System.out.println("null solv");
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
        int n = peakList.size();
        for (int i = 0; i < n; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak.getStatus() >= 0) {
                peak.setStatus(0);
            }
        }

        int iDim = 0;
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        Set<DatasetRegion> regions = getRegions();

        Set<DatasetRegion> newRegions = new TreeSet<>();
        regions.stream().forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            for (Peak peak : peaks) {
                peak.setStatus(1);

            }
        });

        for (int i = 0; i < n; i++) {
            Peak peak = peakList.getPeak(i);
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
        for (Peak peak : peakList.peaks()) {
            peak.setStatus(0);
        }

    }

    public void trimRegionsToPeaks() {
        int iDim = 0;
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        Set<DatasetRegion> newRegions = new TreeSet<>();
        Set<DatasetRegion> regions = getRegions();

        regions.stream().forEach(region -> {
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
                        double tppm = ppm + trimRatio * width;
                        max = Math.max(tppm, max);
                        tppm = ppm - trimRatio * width;
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
        regions.clear();
        regions.addAll(newRegions);
    }

    public void purgeNonPeakRegions() {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        Set<DatasetRegion> newRegions = new TreeSet<>();
        Set<DatasetRegion> regions = getRegions();

        regions.stream().forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            if (!peaks.isEmpty()) {
                newRegions.add(region);
            }
        });
        regions.clear();
        regions.addAll(newRegions);
    }

    public void removeRegion(double shift) {
        Set<DatasetRegion> newRegions = new TreeSet<>();
        Set<DatasetRegion> regions = getRegions();
        int rDim = 0;

        regions.stream().forEach(region -> {
            double start = region.getRegionStart(rDim);
            double end = region.getRegionEnd(rDim);
            if ((shift < start) || (shift > end)) {
                newRegions.add(region);
            }
        });
        regions.clear();
        regions.addAll(newRegions);
        removePeaksFromNonRegions();
    }

    public void groupPeaks() throws IOException {
        Set<DatasetRegion> regions = getRegions();

        Multiplets.groupPeaks(peakList, regions);
    }

    public void baselineCorrect() {
        int size = dataset.getSize(0);
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
            Logger.getLogger(Analyzer.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
    }

    public void dumpRegions() {
        Set<DatasetRegion> regions = getRegions();

        for (DatasetRegion region : regions) {
            System.out.printf("%9.4f %9.4f %9.4f %9.4f\n", region.getRegionStart(0), region.getRegionEnd(0), region.getIntegral(), region.getMax());
        }
    }

    public static Optional<DatasetRegion> getRegion(Set<DatasetRegion> dRegions, int rDim, double shift) {
        Optional<DatasetRegion> found = Optional.empty();
        for (DatasetRegion region : dRegions) {
            double start = region.getRegionStart(rDim);
            double end = region.getRegionEnd(rDim);
            if ((start < shift) && (end >= shift)) {
                found = Optional.of(region);
                break;
            }
        }
        return found;
    }

    public static double[] getBounds(DatasetRegion region, int rDim) {
        double start = region.getRegionStart(rDim);
        double end = region.getRegionEnd(rDim);
        double[] bounds = {start, end};
        return bounds;
    }

    public static double[] getRegionBounds(Set<DatasetRegion> dRegions, int rDim, double shift) {
        Optional<DatasetRegion> found = getRegion(dRegions, rDim, shift);
        double[] bounds = null;
        if (found.isPresent()) {
            bounds = getBounds(found.get(), rDim);
        }
        return bounds;
    }

    public void integrate() throws IOException {
        Set<DatasetRegion> regions = getRegions();
        for (DatasetRegion region : regions) {
            region.measure(dataset);
        }
    }

    public void setVolumesFromIntegrals() {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        Set<DatasetRegion> regions = getRegions();

        regions.stream().forEach(region -> {
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

    public void normalizeIntegrals() {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        Set<DatasetRegion> regions = getRegions();

        regions.stream().forEach(region -> {
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

    public Set<DatasetRegion> getRegions() {
        Set<DatasetRegion> regions = dataset.getRegions();

        if (regions == null) {
            regions = new TreeSet<>();
            dataset.setRegions(regions);
        }
        return regions;
    }

    public void autoSetRegions() {
        int size = dataset.getSize(0);
        Vec vec = new Vec(size);

        int dataDim = dataset.getNDim();
        int[][] pt = new int[dataDim][2];
        pt[0][0] = 0;
        pt[0][1] = size - 1;
        int[] dim = new int[dataDim];
        dim[0] = 0;

        try {
            dataset.readVectorFromDatasetFile(pt, dim, vec);
        } catch (IOException ex) {
            Logger.getLogger(Analyzer.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        double sw = dataset.getSw(0);
        int region = (int) Math.round(1.0 * regionWidth / sw * size);
        int join = (int) Math.round(1.0 * joinWidth / sw * size);
        int extend = (int) Math.round(1.0 * regionExtend / sw * size);

        double minThreshold = manThreshold.isPresent() ? manThreshold.get() : -1.0;

        RealMatrix rM = vec.idIntegrals(regionWindow, regionRatio, region, join, extend, minThreshold);
        Set<DatasetRegion> regions = getRegions();
        regions.clear();
        int nRows = rM.getRowDimension();
        for (int iRow = 0; iRow < nRows; iRow++) {
            double min = rM.getEntry(iRow, 0);
            min = dataset.pointToPPM(0, min);
            double max = rM.getEntry(iRow, 1);
            max = dataset.pointToPPM(0, max);
            //  System.out.println(min + " " + max);
            DatasetRegion newRegion = new DatasetRegion(min, max);
            regions.add(newRegion);
        }
    }

    public void clearRegions() {
        Set<DatasetRegion> regions = getRegions();
        regions.clear();
    }

    public void addRegion(double min, double max) {
        Set<DatasetRegion> regions = getRegions();
        DatasetRegion newRegion = new DatasetRegion(min, max);
        regions.add(newRegion);
        peakPickRegion(min, max);
    }

    public List<Multiplet> splitRegion(double ppm) throws IOException {
        Set<DatasetRegion> regions = getRegions();
        Optional<DatasetRegion> found = getRegion(regions, 0, ppm);
        List<Multiplet> result = new ArrayList<>();
        if (found.isPresent()) {
            DatasetRegion region = found.get();
            double start = region.getRegionStart(0);
            double end = region.getRegionEnd(0);
            regions.remove(region);
            double pt1 = dataset.ppmToDPoint(0, ppm);
            double ppm1 = dataset.pointToPPM(0, pt1 + 1);
            double ppm2 = dataset.pointToPPM(0, pt1 - 1);
            if (start < end) {
                double hold = ppm1;
                ppm1 = ppm2;
                ppm2 = hold;
            }
            double[][] limits = new double[1][2];
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            int[] dim = {0};

            System.out.println(start + " " + ppm1 + " " + ppm2 + " " + end);
            DatasetRegion newRegion1 = new DatasetRegion(start, ppm1);
            DatasetRegion newRegion2 = new DatasetRegion(ppm2, end);
            regions.add(newRegion1);
            regions.add(newRegion2);
            integrate();
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            for (Peak peak : peaks) {
                peak.setFlag(4, false);
            }
            if (!peaks.isEmpty()) {
                Multiplet multiplet = peaks.get(0).getPeakDim(0).getMultiplet();
                Multiplet newMultiplet = multiplet.split(ppm);
                setVolumesFromIntegrals();
                PeakFitting peakFitting = new PeakFitting(dataset);
                peakFitting.fitLinkedPeak(multiplet.getOrigin(), true);
                peakFitting.jfitLinkedPeak(multiplet.getOrigin(), "all");
                peakFitting.fitLinkedPeak(newMultiplet.getOrigin(), true);
                peakFitting.jfitLinkedPeak(newMultiplet.getOrigin(), "all");
            }
            renumber();
        }
        return result;
    }

    public Optional<Multiplet> analyzeRegion(double ppm) throws IOException {
        Set<DatasetRegion> regions = getRegions();
        Optional<Multiplet> result = Optional.empty();
        Optional<DatasetRegion> found = getRegion(regions, 0, ppm);
        if (found.isPresent()) {
            DatasetRegion region = found.get();
            PeakFitting peakFitting = new PeakFitting(dataset);

            integrate();
            setVolumesFromIntegrals();
            Multiplets.unlinkPeaksInRegion(peakList, region);
            Multiplet multiplet = Multiplets.linkPeaksInRegion(peakList, region);
            result = Optional.of(multiplet);
            peakFitting.fitLinkedPeak(multiplet.getOrigin(), true);
            renumber();
            Multiplets.analyzeMultiplet(multiplet.getOrigin());
            peakFitting.jfitLinkedPeak(multiplet.getOrigin(), "all");
        }
        return result;
    }

    public void fitLinkedPeaks() {
        PeakFitting peakFitting = new PeakFitting(dataset);
        peakFitting.fitLinkedPeaks(peakList, true);
    }

    public void fitMultiplet(Multiplet multiplet) {
        if (multiplet != null) {
            PeakFitting peakFitting = new PeakFitting(dataset);
            Peak peak = multiplet.getPeakDim().getPeak();
            peak.setFlag(4, false);
            peakFitting.jfitLinkedPeak(peak, "all");
        }
    }

    public void jfitLinkedPeaks() {
        PeakFitting peakFitting = new PeakFitting(dataset);
        peakFitting.jfitLinkedPeaks(peakList);
    }

    public void objectiveDeconvolution(Multiplet multiplet) {
        PeakFitting peakFitting = new PeakFitting(dataset);
        int nComps = multiplet.getRelComponentList().size();
        int nAdd = 0;
        if (nComps < 2) {
            nAdd = 3;
        } else {
            nAdd = nComps;
        }
        List<AbsMultipletComponent> minList = null;
        multiplet.getOrigin().setFlag(4, false);
        double rms = peakFitting.jfitLinkedPeak(multiplet.getOrigin(), "all");
        double minBIC = peakFitting.getBIC();
        for (int i = 0; i < nAdd; i++) {
            Optional<Double> result = Multiplets.deviation(multiplet);
            if (result.isPresent()) {
                Multiplets.addPeaksToMultiplet(multiplet, result.get());
                multiplet.getOrigin().setFlag(4, false);
                rms = peakFitting.jfitLinkedPeak(multiplet.getOrigin(), "all");
                double BIC = peakFitting.getBIC();
                if (BIC < minBIC) {
                    minBIC = BIC;
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
                int minSkip = -1;
                int n = minList.size();
                for (int i = 0; i < n; i++) {
                    List<AbsMultipletComponent> newList = AbsMultipletComponent.copyList(minList, i);
                    multiplet.updateCoupling(newList);
                    multiplet.getOrigin().setFlag(4, false);
                    peakFitting.jfitLinkedPeak(multiplet.getOrigin(), "all");
                    double BIC = peakFitting.getBIC();
                    if (BIC < minSkipBIC) {
                        minSkip = i;
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
            System.out.println(multiplet.myPeakDim.myPeak.getName() + " "
                    + multiplet.myPeakDim.getChemShift() + " " + multiplet.getCouplingsAsString() + " " + Multiplets.getCouplingPattern(multiplet) + " " + multiplet.getVolume() / peakList.scale);

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
        System.out.println("norm " + norm);
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
        Set<DatasetRegion> regions = getRegions();
        regions.stream().forEach(region -> {
            intensities.add(region.getMax());
        });
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
        Set<DatasetRegion> regions = getRegions();

        regions.stream().forEach(region -> {
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
        peakList.sortPeaks(0, false);
        peakList.compress();
        peakList.reNumber();
    }

    public void writeList(String listFileName) throws IOException, InvalidPeakException {
        try (FileWriter writer = new FileWriter(listFileName)) {
            PeakWriter peakWriter = new PeakWriter();
            peakWriter.writePeaksXPK2(writer, peakList);
            writer.close();
        }
    }

    public void analyze() throws IOException {
        // clear
        //baselineCorrect();
        // autoReference

        // clearRegions
        // auto set regions
        calculateThreshold();
        double thresh = getThreshold();
        autoSetRegions();
        integrate();
        PeakList pList = peakPick();
        purgeNonPeakRegions();
        renumber();
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
    }
}
