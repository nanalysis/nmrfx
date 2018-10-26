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

/**
 *
 * @author Bruce Johnson
 */
public class Analyzer {

    final Dataset dataset;
    PeakList peakList;

    double trimRatio = 2.0;
    boolean scaleToLargest = false;
    int nWin = 32;
    double maxRatio = 20.0;
    double sdRatio = 40.0;

    int regionWindow = 20;
    double regionRatio = 50.0;
    double regionWidth = 1.0;
    double joinWidth = 1.0;
    double regionExtend = 9.0;
    double minThreshold = -1.0;

    double artifactRatio = 50;
//        public RealMatrix idIntegrals(int winSize, double ratio,
//            int regionWidth, int joinWidth, int extend, double minThreshold)

//        set regionWindow    $::dcs::analysis::prefs(regions,windowSize,value)
//    set noiseRatio $::dcs::analysis::prefs(regions,noiseRatio,value)
//    set minWidth   $::dcs::analysis::prefs(regions,minimumWidth,value)
//    set joinSize   $::dcs::analysis::prefs(regions,joinSize,value)
//    set extendAmount  $::dcs::analysis::prefs(regions,extendAmount,value)
    double sDev = 0.0;
    double threshold = 0.0;
    double filter = 0.0;
    Optional<Double> manThreshold = Optional.empty();
    Solvents solvents;

    public Analyzer(Dataset dataset) {
        this.dataset = dataset;
//        PeakDim.setResonanceFactory(new ResonanceFactory());
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
            dataset.readVecFromDatasetFile(pt, dim, vec);
        } catch (IOException ex) {
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
            } else {
                threshold = sdRatio / 5.0 * sDev;
            }
        }
    }

    public void peakPick(boolean saveFile) {
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
        PeakPick peakPickPar = (new PeakPick(dataset, listName)).level(level).mode("replaceif");
        peakPickPar.pos(true).neg(false);
        peakPickPar.calcRange();
        PeakPicker picker = new PeakPicker(peakPickPar);
        String canonFileName = dataset.getCanonicalFile();
        String listFileName = canonFileName.substring(0, canonFileName.lastIndexOf(".")) + ".xpk2";
        peakList = null;
        try {
            peakList = picker.peakPick();
            removePeaksFromNonRegions();
            if (saveFile) {
                try (FileWriter writer = new FileWriter(listFileName)) {
                    PeakWriter peakWriter = new PeakWriter();
                    peakWriter.writePeaksXPK2(writer, peakList);
                    writer.close();
                }
            }
        } catch (IOException | InvalidPeakException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

    /*
    


    if {$dim == "1D" && $::dcs::peaks::pars(_threshold_) == ""} {
        if {$::dcs::peaks::pars(_manThreshold_) != ""} {
            set ::dcs::peaks::pars(_threshold_) $::dcs::peaks::pars(_manThreshold_)
            set pkvar(sdevn) 0.0
            set pkvar(width) 0.0
        } else {
            foreach "threshold filter sdev" [::dcs::regions::calculateThreshold $dataset] {}
            set setWidthEtc 1
            if {$::dcs::regions::pars(_regionsContinue_)} {
                set regionMin [dcs::regions::getRegionsMin]
                set regionThreshold [expr {$regionMin*0.5}]
                if {$regionThreshold < (5.0*$sdev)} {
                    set regionThreshold [expr {5.0*$sdev}]
                }
                if {$regionThreshold < $threshold} {
                    set threshold $regionThreshold
                    set pkvar(sdevn) 0.0
                    set pkvar(width) 0.0
                    set setWidthEtc 0
                }
            }
            set sdRatio $::dcs::analysis::prefs(peaks,noiseRatio,value)
            if {$setWidthEtc} {
                if {[string equal $nucType "X"]} {
                    set pkvar(width) 0.05
                    set pkvar(sdevn) [expr $sdRatio/5.0*$sdev]
                    set pkvar(sign) 3
                } else {
                    set pkvar(width) 0.0
                    set pkvar(sdevn) [expr $sdRatio*$sdev]
                    set pkvar(sign) 1
                }
            }
            set ::dcs::peaks::pars(_threshold_) [format %1.1e $threshold]
            nv_win cross1y $threshold
        }

     */
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
                    double ppm = peak.peakDim[iDim].getChemShift();
                    double width = Math.abs(peak.peakDim[iDim].getLineWidth());
                    double tppm = ppm + trimRatio * width;
                    max = Math.max(tppm, max);
                    tppm = ppm - trimRatio * width;
                    min = Math.min(tppm, min);
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

    public void groupPeaks() throws IOException {
        Set<DatasetRegion> regions = getRegions();

        Multiplets.groupPeaks(peakList, regions);
    }

    public void baselineCorrect() {

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
        int[] pt = new int[1];
        Set<DatasetRegion> regions = getRegions();

        for (DatasetRegion region : regions) {
            double start = region.getRegionStart(0);
            double end = region.getRegionEnd(0);
            int istart = dataset.ppmToPoint(0, start);
            int iend = dataset.ppmToPoint(0, end);
            if (istart > iend) {
                int hold = istart;
                istart = iend;
                iend = hold;
            }
            double sum = 0.0;
            double min = Double.MAX_VALUE;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = istart; i <= iend; i++) {
                pt[0] = i;
                double value = dataset.readPoint(pt);
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
            }
            region.setIntegral(sum);
            region.setMax(max);
            region.setMin(min);
        }
    }

    public void setVolumesFromIntegrals() {
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
            dataset.readVecFromDatasetFile(pt, dim, vec);
        } catch (IOException ex) {
            Logger.getLogger(Analyzer.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        double sw = dataset.getSw(0);
        int region = (int) Math.round(1.0 * regionWidth / sw * size);
        int join = (int) Math.round(1.0 * joinWidth / sw * size);
        int extend = (int) Math.round(1.0 * regionExtend / sw * size);
        System.out.println(region + " " + join + " " + extend);
        /*
   puts "autoreg $winSize $noiseRatio $minWidth $joinSize $extendAmount"
        autoreg 20 26 0.9 0.9 9.1
         */

        RealMatrix rM = vec.idIntegrals(regionWindow, regionRatio, region, join, extend, minThreshold);
        Set<DatasetRegion> regions = getRegions();
        regions.clear();
        int nRows = rM.getRowDimension();
        for (int iRow = 0; iRow < nRows; iRow++) {
            double min = rM.getEntry(iRow, 0);
            min = dataset.pointToPPM(0, min);
            double max = rM.getEntry(iRow, 1);
            max = dataset.pointToPPM(0, max);
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
    }
    
    public void fitLinkedPeaks() {
        PeakFitting peakFitting = new PeakFitting(dataset);
        peakFitting.fitLinkedPeaks(peakList, true);
    }

    public void fitMultiplet(String mSpec) {
        Multiplet multiplet = Multiplets.getMultiplet(mSpec);
        fitMultiplet(multiplet);
    }

    public void fitMultiplet(Multiplet multiplet) {
        PeakFitting peakFitting = new PeakFitting(dataset);
        Peak peak = multiplet.getPeakDim().getPeak();
        peak.setFlag(4, false);
        peakFitting.jfitLinkedPeak(peak, true);
    }

    public void jfitLinkedPeaks() {
        PeakFitting peakFitting = new PeakFitting(dataset);
        peakFitting.jfitLinkedPeaks(peakList, true);
    }

    public void analyzeMultiplets() {
        Multiplets.analyzeMultiplets(peakList);
    }

    public void dumpMultiplets() {
        ArrayList<Multiplet> multiplets = peakList.getMultiplets();
        for (Multiplet multiplet : multiplets) {
            System.out.println(multiplet.myPeakDim.myPeak.getName() + " "
                    + multiplet.myPeakDim.getChemShift() + " " + multiplet.getCouplingsAsString() + " " + Multiplets.getCouplingPattern(multiplet) + " " + multiplet.getVolume() / peakList.scale);

        }
    }

    public void normalizeMultiplets() {
        ArrayList<Multiplet> multiplets = peakList.getMultiplets();
        List<Double> aromaticValues = new ArrayList<>();
        List<Double> aliphaticValues = new ArrayList<>();
        for (Multiplet multiplet : multiplets) {
            if (multiplet.myPeakDim.getPeak().getType() == Peak.COMPOUND) {
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

    /*
        foreach peak $peaks {
        set type [nv_peak elem type $peak]
        if {![string equal compound $type]} {
            continue
        }
        set ppm [nv_peak elem $label.P $peak]
        if {$ppm < 5.5} {
            continue
        }
        if {$useMultiplets} {
            set vol [nv_peak elem 1.mv $peak]
        } else {
            set vol [nv_peak elem vol $peak]
        }
        if {$vol != 0.0} {
            lappend Aromatics "$vol $peak"
        }
    }

     */

 /*
    proc ::dcs::regions::autoSetRegions {} {
    variable pars
    #fixmegui
    global ExpTable

    set ::dcs::regions::pars(_regionsSet_) 1
    if {![info exists ::dcs::regions::pars(_manualNormalized_)] || !$::dcs::regions::pars(_manualNormalized_)} {
        set ::dcs::regions::pars(_normalized_) 0
    }
    set minThreshold [::nv::util::getIfExists ::dcs::peaks::pars(_manThreshold_) -1.0]
    if {![string is double -strict $minThreshold]} {
        set minThreshold -1.0
    }
    set regionWindow    $::dcs::analysis::prefs(regions,windowSize,value)
    set noiseRatio $::dcs::analysis::prefs(regions,noiseRatio,value)
    set minWidth   $::dcs::analysis::prefs(regions,minimumWidth,value)
    set joinSize   $::dcs::analysis::prefs(regions,joinSize,value)
    set extendAmount  $::dcs::analysis::prefs(regions,extendAmount,value)
    ::nv::spectrum::regions::setRegions $winSize $noiseRatio $minWidth $joinSize $extendAmount $minThreshold
    
    
        set dataset [lindex [nv_win dataset] 0]
    vecmat resize pwork [nv_dataset size $dataset 1]
    set complex [nv_dataset complex $dataset 1]
    if {$complex} {
        vecmat complex pwork
    } else {
        vecmat real pwork
    }
    vecmat real pwork
    set nDim [nv_dataset ndim $dataset]
    if {$nDim == 1} {
        nv_dataset get $dataset -obj pwork
    } else {
        nv_dataset get $dataset -d2 1 1 -obj pwork
    }
    if {$complex} {
        vecmat real pwork
    }
    set size [vecmat size pwork]
    set sw [vecmat sw pwork]
    # convert from Hz to pts
    set regionWindow [expr {round(1.0*$winSize/$sw*$size)}]
    if {$winSize < 8} {
        set regionWindow 8
    }
    set regionWidth [expr {round(1.0*$regionWidth/$sw*$size)}]
    set joinWidth [expr {round(1.0*$joinWidth/$sw*$size)}]
    set regionExtend [expr {round(1.0*$extend/$sw*$size)}]
    set regions [vecmat idintegrals pwork $winSize $ratio $regionWidth $joinWidth $extend $minThreshold]

    
    
    
    
    
    set ignoreRegions [::nv::util::getIfExists pars(ignoreRegions) [list]]
    foreach "x1 x2" $ignoreRegions {
        nv_win region clear $x1 $x2
    }
    ::dcs::regions::purgeBadRegions
    set manualRegions [::nv::util::getIfExists pars(manualRegions) [list]]
    foreach "x1 x2" $manualRegions {
        nv_win region add $x1 $x2
    }
}

     */
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
        // baselineCorrect
        // autoReference

        // clearRegions
        // auto set regions
        calculateThreshold();
        double thresh = getThreshold();
        autoSetRegions();
        integrate();
        peakPick(true);
        purgeNonPeakRegions();
        groupPeaks();
        setVolumesFromIntegrals();
        fitLinkedPeaks();
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

//        ::dcs::regions::restorePurgedRegions
//        ::dcs::peaks::gui::clearPeaks
//        ::dcs::peaks::gui::pickPeaks All nodraw
// purgeNonPeakRegions
// groupPeaks
// setVolumesFromIntegrals
// fitLinkedPeaks
// purgeSmallPeaks
// purgeNonPeakRegions
// analyzeMultiplets
// jfitLinkedPeaks
// purgeSolventPeaks
// trimRegionsToPeaks
// normalizeIntensities
// report
    }
}
