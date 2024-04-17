package org.nmrfx.analyst.peaks;

import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.math.VecBase.IndexValue;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakFitException;
import org.nmrfx.processor.datasets.peaks.PeakFitParameters;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;

/**
 * @author Bruce Johnson
 */
public class Multiplets {
    private static final Logger log = LoggerFactory.getLogger(Multiplets.class);
    public static final double DOUBLETRATIO = 3.0;

    public static PeakDim getMultipletRoot(Multiplet multiplet) throws IllegalArgumentException {
        return multiplet == null ? null : multiplet.getPeakDim();
    }

    public static List<AbsMultipletComponent> getSortedMultipletPeaks(Multiplet multiplet, String mode) {
        List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();
        if (mode.equals("int")) {
            comps.sort(comparing((p) -> p.getIntensity()));
        } else {
            comps.sort(comparing((p) -> p.getOffset()));
        }
        return comps;
    }

    public static List<AbsMultipletComponent> getSortedMultipletPeaks(PeakDim startPeakDim, String mode) {
        return getSortedMultipletPeaks(startPeakDim.getMultiplet(), mode);

    }

    public static String getCouplingPattern(Multiplet multiplet) {
        Coupling coupling = multiplet.getCoupling();
        String pattern = "";
        if (coupling instanceof Singlet) {
            pattern = "s";
        } else if (coupling instanceof ComplexCoupling) {
            pattern = "m";
        } else {
            CouplingPattern cPattern = (CouplingPattern) coupling;
            int[] nValues = cPattern.getNValues();
            StringBuilder sBuilder = new StringBuilder();
            for (int nValue : nValues) {
                sBuilder.append("sdtqphsp".charAt(nValue - 1));
            }
            pattern = sBuilder.toString();
        }
        return pattern;
    }

    public static void removeWeakPeaksInMultiplet(Multiplet multiplet, int nRemove) {
        List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();
        if (comps.size() > 1) {
            comps.sort(comparing((p) -> p.getIntensity()));
            if (nRemove < 0) {
                nRemove = comps.size() + nRemove;
            }
            comps.subList(0, nRemove).clear();
            multiplet.updateCoupling(comps);
            fitComponents(multiplet);
        }
    }

    public static List<Peak> getPeaks(List<PeakDim> peakDims) {
        return peakDims.stream().map(p -> p.getPeak()).collect(Collectors.toList());
    }

    public static void removePeakDims(List<PeakDim> peakDims) {
        removePeaks(getPeaks(peakDims));
    }

    public static void removePeaks(List<Peak> peaks) {
        PeakList peakList = peaks.get(0).peakList;
        if (peaks.isEmpty()) {
            return;
        }
        for (Peak peak : peaks) {
            peak.setStatus(-1);
            List<Peak> lPeaks = PeakList.getLinks(peak);
            for (Peak lPeak : lPeaks) {
                lPeak.setFlag(4, false);
            }
        }
        peakList.compress();
        peakList.sortPeaks(0, false);
        peakList.reNumber();
    }

    public static Optional<Multiplet> mergePeaks(List<Peak> peaks) {
        Optional<Multiplet> result = Optional.empty();
        if (!peaks.isEmpty()) {
            Set<PeakDim> peakDims = new HashSet<>();
            for (Peak peak : peaks) {
                peakDims.add(peak.getPeakDim(0).getMultiplet().getPeakDim());
            }
            List<AbsMultipletComponent> comps = new ArrayList<>();
            for (PeakDim peakDim : peakDims) {
                Multiplet multiplet = peakDim.getMultiplet();
                comps.addAll(multiplet.getAbsComponentList());
            }
            PeakDim firstPeakDim = null;
            for (PeakDim peakDim : peakDims) {
                if (firstPeakDim == null) {
                    firstPeakDim = peakDim;
                } else {
                    peakDim.getPeak().setStatus(-1);
                }
            }
            firstPeakDim.getMultiplet().updateCoupling(comps);
            PeakList peakList = firstPeakDim.getPeak().getPeakList();
            peakList.compress();
            peakList.sortPeaks(0, true);
            peakList.reNumber();
            result = Optional.of(firstPeakDim.getMultiplet());
        }
        return result;
    }

    public static Optional<Multiplet> mergeMultipletComponents(List<RelMultipletComponent> comps) {
        Optional<Multiplet> result = Optional.empty();
        if (comps.size() > 0) {
            Set<PeakDim> peakDims = new HashSet<>();
            for (RelMultipletComponent comp : comps) {
                peakDims.add(comp.getMultiplet().getPeakDim());
            }
            PeakDim firstPeakDim = null;
            for (PeakDim peakDim : peakDims) {
                if (firstPeakDim == null) {
                    firstPeakDim = peakDim;
                } else {
                    Multiplet.merge(firstPeakDim, peakDim);
                }
            }

            PeakList peakList = firstPeakDim.getPeak().getPeakList();
            peakList.compress();
            peakList.sortPeaks(0, true);
            peakList.reNumber();
            result = Optional.of(firstPeakDim.getMultiplet());
        }
        return result;
    }

    public static Optional<Multiplet> transferPeaks(Multiplet multiplet, List<Peak> peaks0) {
        Optional<Multiplet> result = Optional.empty();
        if (peaks0.size() > 0) {
            PeakDim refDim = multiplet.getPeakDim();
            Peak peak0 = peaks0.get(0);
            for (Peak peak : peaks0) {
                peak.getPeakDim(0).unLink();
            }
            for (Peak peak : peaks0) {
                multiplet.addPeakDim(peak.getPeakDim(0));
            }
            PeakList peakList = peak0.getPeakList();
            peakList.compress();
            peakList.sortPeaks(0, true);
            peakList.reNumber();
            result = Optional.of(refDim.getMultiplet());
        }
        return result;
    }

    public static void addPeaksToMultiplet(Multiplet multiplet, double... ppms) {
        PeakDim peakDim = getMultipletRoot(multiplet);
        if (peakDim != null) {
            double intensity = peakDim.getPeak().getIntensity();
            double volume = peakDim.getPeak().getVolume1();
            float width = peakDim.getLineWidth();

            List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();
            volume = volume / comps.size();
            intensity /= 4;

            for (double ppm : ppms) {
                AbsMultipletComponent comp = new AbsMultipletComponent(multiplet, ppm, intensity, volume, width);
                comps.add(comp);
            }
            multiplet.updateCoupling(comps);
            fitComponents(multiplet);
        } else {
            throw new IllegalArgumentException("Multiplet is null. Unable to add peaks.");
        }
    }

    public static Optional<Double> findMultipletMidpoint(Multiplet multiplet) {
        List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();
        Double offset = null;
        if (comps.size() > 1) {
            double sumVolume = 0.0;
            for (var comp : comps) {
                sumVolume += comp.getVolume();
            }
            double halfVolume = 0.0;
            double minDelta = Double.MAX_VALUE;
            int iMin = 0;
            for (int i = 0; i < comps.size(); i++) {
                var comp = comps.get(i);
                halfVolume += comp.getVolume();
                double delta = Math.abs(halfVolume - (sumVolume / 2.0));
                if (delta < minDelta) {
                    iMin = i;
                    minDelta = delta;
                }
            }
            if (iMin < comps.size() - 1) {
                offset = (comps.get(iMin).getOffset() + comps.get(iMin + 1).getOffset()) / 2.0;
            }
        }
        return Optional.ofNullable(offset);
    }

    public static void addOuterCoupling(int addNumber, Multiplet multiplet) {
        PeakDim peakDim = getMultipletRoot(multiplet);
        int type = peakDim.getPeak().getType();
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(peakDim, "1.P");
        PeakList refList = peakDim.getPeak().peakList;
        AbsMultipletComponent firstComp = comps.get(0);
        double firstPPM = firstComp.getOffset();
        AbsMultipletComponent lastComp = comps.get(comps.size() - 1);
        double lastPPM = lastComp.getOffset();

        double intensity = firstComp.getIntensity();
        double volume = firstComp.getVolume();
        double width = firstComp.getLineWidth();
        double bounds = width * 3.0f;
        double dppm;

        if (comps.size() == 1) {
            peakDim.getPeak().setIntensity((float) intensity);
            peakDim.getPeak().setVolume1((float) volume / 2.0f);
            width /= 3.0;
            peakDim.setLineWidthValue((float) width);
            peakDim.setBoundsValue((float) width * 3.0f);
            dppm = width;
        } else {
            double secondPPM = comps.get(1).getOffset();
            dppm = (float) Math.abs(firstPPM - secondPPM);
        }
        int addWhich = -1;
        float dSign = -1;
        if (addNumber == 1) {
            double lastIntensity = lastComp.getIntensity();
            if (lastIntensity < intensity) {
                intensity = lastIntensity;
                addWhich = 0;
                dSign = -1f;
            } else {
                addWhich = 1;
                dSign = 1f;
            }
            for (AbsMultipletComponent comp : comps) {
                double ppm = comp.getOffset();
                comp.setOffset(ppm + dSign * dppm / 2.0f);

            }

        }
        int iDim = 0;
        for (int iAdd = 0; iAdd < 2; iAdd++) {
            if ((addNumber == 2) || (addWhich == iAdd)) {
                Peak newPeak = refList.getNewPeak();
                double ppmNew;
                if (iAdd == 0) {
                    ppmNew = firstPPM - dppm;
                } else {
                    ppmNew = lastPPM + dppm;
                }
                newPeak.setIntensity((float) intensity / 2.0f);
                newPeak.setVolume1((float) volume / 2.0f);
                newPeak.peakDims[iDim].setLineWidthValue((float) width);
                newPeak.peakDims[iDim].setBoundsValue((float) bounds);
                newPeak.peakDims[iDim].setChemShiftValue((float) ppmNew);
                newPeak.setType(type);
                PeakList.couplePeakDims(peakDim, newPeak.peakDims[iDim]);
            }
        }
    }

    public static void addCoupling(Multiplet multiplet, Coupling oldCoupling, String couplingType, double couplingValue) {
        multiplet.addCoupling(oldCoupling, couplingType, couplingValue);
    }

    public static void makeSinglet(Multiplet multiplet) {
        multiplet.makeSinglet();
    }

    public static void expandCoupling(Multiplet multiplet, int limit) {
        multiplet.expandCoupling(limit);
    }

    public static void splitToMultiplicity(Multiplet multiplet, String fullCouplingType) {
        for (int i = 0; i < fullCouplingType.length(); i++) {
            String couplingType = fullCouplingType.substring(i, i + 1);
            List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "1.P");
            double width = comps.get(0).getLineWidth() * multiplet.getPeakList().getSpectralDim(0).getSf();
            int nNew = "sdtqphsp".indexOf(couplingType);
            double jValue = width / (nNew + 1);
            Coupling oldCoupling = multiplet.getCoupling();
            addCoupling(multiplet, oldCoupling, couplingType, jValue);
        }
    }

    public static void setCouplingPattern(Multiplet multiplet, String multNew) {
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "1.P");
        int nPeaks = comps.size();
        int multCount = getMultiplicityCount(multNew);
        if (nPeaks == multCount) {
            double firstPPM = comps.get(0).getOffset();
            double lastPPM = comps.get(nPeaks - 1).getOffset();
            double deltaPPM = Math.abs(lastPPM - firstPPM) / (nPeaks - 1);
            double sf = multiplet.getPeakDim().getPeak().peakList.getSpectralDim(0).getSf();
            double couplingValue = deltaPPM * sf;
            double centerPPM = (firstPPM + lastPPM) / 2.0;
            multiplet.setCenter(centerPPM);
            double[] values = {couplingValue};
            int n[] = {multCount};
            double[] sin2thetas = {0.0};
            multiplet.setCouplingValues(values, n, multiplet.getMultipletMax(), sin2thetas);
        }

    }

    public static void defineMultiplicity(Multiplet multiplet, String multNew) {
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "int");
        int nPeaks = comps.size();
        int nNew = getMultiplicityCount(multNew);
        if ((multNew.length() == 1) && (nNew > 0) && (nPeaks > nNew)) {
            removeWeakPeaksInMultiplet(multiplet, -nNew);
        }
    }

    public static void convertMultiplicity(Multiplet multiplet, String multOrig, String multNew) {
        if (multOrig.equals(multNew)) {
            return;
        }
        int origLen = multOrig.length();
        int newLen = multNew.length();
        if (newLen == 0) {
            return;
        }
        if (multNew.charAt(0) == 'm') {
            multiplet.setGenericMultiplet();
        } else if (multNew.charAt(0) == 's') {
            if (multOrig.equals("d")) {
                removeSplittingInMultiplet(multiplet, "d");
            } else {
                removeWeakPeaksInMultiplet(multiplet, -1);
                multiplet.makeSinglet();
            }
        } else if (multOrig.equals("s")) {
            splitToMultiplicity(multiplet, multNew); // fixme handle higher splits
        } else if ((multOrig + "d").equals(multNew)) {
            splitToMultiplicity(multiplet, "d");
        } else if ((multOrig + "t").equals(multNew)) {
            splitToMultiplicity(multiplet, "t");
        } else if ((multOrig + "q").equals(multNew)) {
            splitToMultiplicity(multiplet, "q");
        } else if ((origLen > 1) && multOrig.substring(0, origLen - 1).equals(multNew)) {
            multiplet.removeCoupling();
        } else if ((origLen > 2) && multOrig.substring(0, origLen - 2).equals(multNew)) {
            multiplet.removeCoupling();
            multiplet.removeCoupling();
        } else if (multOrig.equals("m")) {
            if (newLen == 1) {
                defineMultiplicity(multiplet, multNew);
                setCouplingPattern(multiplet, multNew);
            } else if (newLen == 2) {
                //defineMultiplicityFromGeneric(multiplet, multNew);
            }
        } else if ((origLen > 1) && (newLen == 1)) {
            defineMultiplicity(multiplet, multNew);
            setCouplingPattern(multiplet, multNew); // fixme old tcl code used binomial intensities
        } else if (multOrig.equals("q") && multNew.equals("ddd")) {
            expandCoupling(multiplet, 4);
        } else if (multOrig.equals("q") && multNew.equals("dd")) {
            expandCoupling(multiplet, 3);
        } else if (multOrig.equals("t") && multNew.equals("dd")) {
            expandCoupling(multiplet, 3);
        } else if (multNew.equals("ddd")) {
            expandCoupling(multiplet, 3);
        } else if (multNew.equals("dddd")) {
            expandCoupling(multiplet, 3);
        } else if (multOrig.equals("dd") && multNew.equals("q")) {
            defineMultiplicity(multiplet, multNew);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("t") && multNew.equals("d")) {
            removeWeakPeaksInMultiplet(multiplet, -2);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("q") && multNew.equals("t")) {
            removeWeakPeaksInMultiplet(multiplet, -3);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("p") && multNew.equals("t")) {
            removeWeakPeaksInMultiplet(multiplet, -3);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("p") && multNew.equals("q")) {
            removeWeakPeaksInMultiplet(multiplet, -4);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("q") && multNew.equals("d")) {
            removeWeakPeaksInMultiplet(multiplet, -2);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("d") && multNew.equals("t")) {
            addOuterCoupling(1, multiplet);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("d") && multNew.equals("q")) {
            addOuterCoupling(2, multiplet);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("q") && multNew.equals("p")) {
            addOuterCoupling(1, multiplet);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("t") && multNew.equals("q")) {
            addOuterCoupling(1, multiplet);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("q") && multNew.equals("td")) {
        } else if (multOrig.equals("t") && multNew.equals("p")) {
            addOuterCoupling(2, multiplet);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("q") && multNew.equals("h")) {
            addOuterCoupling(2, multiplet);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.equals("p") && multNew.equals("sp")) {
            addOuterCoupling(2, multiplet);
            setCouplingPattern(multiplet, multNew);
        } else if (multOrig.substring(0, origLen - 1).equals(multNew.substring(0, newLen - 1))) {
            removeSplittingInMultiplet(multiplet, multOrig.substring(origLen - 1));
            splitToMultiplicity(multiplet, multNew.substring(newLen - 1));

        } else {
            System.out.println("Unknown conversion");
        }
        updateAfterMultipletConversion(multiplet);

    }

    public static Optional<Double> rms(Multiplet multiplet) {
        PeakFitParameters fitParameters = new PeakFitParameters();
        fitParameters.fitMode(PeakFitParameters.FIT_MODE.RMS).fitJMode(PeakFitParameters.FITJ_MODE.JFIT);
        return measure(multiplet, fitParameters);
    }

    public static Optional<Double> deviation(Multiplet multiplet) {
        PeakFitParameters fitParameters = new PeakFitParameters();
        fitParameters.fitMode(PeakFitParameters.FIT_MODE.MAXDEV).fitJMode(PeakFitParameters.FITJ_MODE.JFIT);
        return measure(multiplet, fitParameters);
    }

    public static Optional<Double> measure(Multiplet multiplet, PeakFitParameters fitParameters) {
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "1.P");
        PeakDim peakDim = getMultipletRoot(multiplet);
        Peak refPeak = peakDim.getPeak();
        List<PeakDim> peakDims = new ArrayList<>();
        peakDims.add(peakDim);
        PeakList peakList = refPeak.peakList;
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        Optional<Double> result = Optional.empty();

        if (dataset != null) {
            double[] bounds = Analyzer.getRegionBounds(dataset.getReadOnlyRegions(), 0, refPeak.peakDims[0].getChemShift());
            PeakFitting peakFitting = new PeakFitting(dataset);
            try {
                double rms = peakFitting.fitPeakDims(peakDims, bounds, fitParameters);
                result = Optional.of(rms);
            } catch (IllegalArgumentException | PeakFitException | IOException ex) {
                System.out.println("error in fit " + ex.getMessage());
            }
        }
        return result;
    }

    public static void fitComponents(Multiplet multiplet) {

        PeakList peakList = multiplet.getOrigin().peakList;
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        PeakFitting peakFitting = new PeakFitting(dataset);
        PeakFitParameters fitParameters = new PeakFitParameters();
        peakFitting.fitLinkedPeak(multiplet.getOrigin(), fitParameters);
    }

    public static Optional<Double> updateAfterMultipletConversion(Multiplet multiplet) {
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "1.P");
        PeakDim peakDim = getMultipletRoot(multiplet);
        List<PeakDim> peakDims = new ArrayList<>();
        peakDims.add(peakDim);
        Peak refPeak = peakDim.getPeak();
        PeakList peakList = refPeak.peakList;
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        Optional<Double> result = Optional.empty();
        if (dataset != null) {
            double[] bounds = Analyzer.getRegionBounds(dataset.getReadOnlyRegions(), 0, refPeak.peakDims[0].getChemShift());
            for (PeakDim apeakDim : peakDims) {
                apeakDim.getPeak().setFlag(4, false);
            }

            PeakFitting peakFitting = new PeakFitting(dataset);
            PeakFitParameters fitParameters = new PeakFitParameters();
            fitParameters.fitJMode(PeakFitParameters.FITJ_MODE.JFIT);
            try {
                double rms = peakFitting.fitPeakDims(peakDims, bounds, fitParameters);
                result = Optional.of(rms);

            } catch (IllegalArgumentException | PeakFitException | IOException ex) {
                System.out.println("error in fit " + ex.getMessage());
            }
        }
        peakList.sortPeaks(0, false);
        peakList.reNumber();
        return result;
    }

    public static void removeCoupling(Multiplet multiplet) {
        multiplet.removeCoupling();
    }

    /*
                set coupling $oldCoupling
            set iPos [lsearch $coupling "i"]
            if {$iPos == -1} {
                set iPos [lsearch $coupling "s"]
            }
            set last [expr {[llength $coupling]-1}]
            if {[lindex $coupling 0] eq "m"} {
                set last [expr {[llength $coupling]-2}]
            }
            if {$iPos != -1} {
                set last [expr {$iPos -1}]
            }
            set first [expr {$last-1}]
            set odd [expr {($last+1) % 2}]
            if {$odd} {
                incr first
            }
            set coupling [lreplace $coupling $first $last]


     */
    public static void guessMultiplicity(Multiplet multiplet) {
        Coupling coupling = multiplet.getCoupling();
        if (coupling instanceof CouplingPattern) {
            multiplet.expandCoupling(10);
        } else {
            guessMultiplicityFromGeneric(multiplet);
        }

    }

    public static void dumpPeakDims(Collection<PeakDim> peakDims) {
        System.out.println("dump peak dims");
        for (PeakDim peakDim : peakDims) {
            System.out.println(peakDim.getPeak().getName() + " " + peakDim.getPeak().getIntensity() + " " + peakDim.getChemShift());
        }
    }

    public static void analyzeMultiplets(PeakList peakList) {
        List<Peak> peaks = peakList.peaks();
        for (Peak peak : peaks) {
            analyzeMultiplet(peak);
        }
    }

    public static void analyzeMultiplet(Peak peak) {
        PeakDim peakDim = peak.getPeakDim(0);
        CouplingData couplingData = determineMultiplicity(peakDim, true);
        Multiplet multiplet = peakDim.getMultiplet();
        double[] values = new double[couplingData.couplingItems.size()];
        if (values.length > 0) {
            int[] nValues = new int[couplingData.couplingItems.size()];
            int i = 0;
            for (CouplingItem item : couplingData.couplingItems) {
                values[i] = item.coupling();
                nValues[i] = item.nSplits();
                i++;
            }
            double[] sin2thetas = new double[values.length];
            multiplet.setCouplingValues(values, nValues, multiplet.getIntensity(), sin2thetas);
        }
    }

    public static CouplingData determineMultiplicity(PeakDim peakDim, boolean pow2Mode) {
        Multiplet multiplet = peakDim.getMultiplet();
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "1.P");

        int nComps = comps.size();
        double ppmFirst = comps.get(0).getOffset();
        double ppmLast = comps.get(nComps - 1).getOffset();
        double sumPPM = comps.stream().collect(Collectors.summingDouble(p -> p.getOffset()));
        double sumVol = comps.stream().collect(Collectors.summingDouble(p -> p.getVolume()));
        double ppmCenter = sumPPM / nComps;
        double sf = peakDim.getSpectralDimObj().getSf();
        for (int i = 0; i < nComps; i++) {
            AbsMultipletComponent comp = comps.get(i);
        }
        if (checkMultiplet(comps)) {
            System.out.println("check");
            return new CouplingData(ppmCenter, nComps);
        } else if (nComps == 2) {
            double coupling = Math.round(100 * Math.abs(ppmFirst - ppmLast) * sf) / 100.0;
            // doublet
            System.out.println("doublet");
            return new CouplingData(ppmCenter, coupling, 2, nComps);
        } else if (nComps == 3) {
            double coupling = Math.round(100 * Math.abs(ppmFirst - ppmLast) * sf) / 200.0;
            System.out.println("3let");
            return new CouplingData(ppmCenter, coupling, 3, nComps);
        }
        // symmetrize peak Volumes
        double[] volAvgs = new double[nComps];
        for (int i = 0; i < nComps; i++) {
            volAvgs[i] = comps.get(i).getVolume();
        }
        for (int i = 0; i < (nComps / 2); i++) {
            int j = nComps - i - 1;
            double vol1 = comps.get(i).getVolume();
            double vol2 = comps.get(j).getVolume();
            double volAvg = (vol1 + vol2) / 2.0;
            volAvgs[i] = volAvg;
            volAvgs[j] = volAvg;
        }
        for (int i = 0; i < (nComps / 2); i++) {
            int j = nComps - i - 1;
            double ppm1 = comps.get(i).getOffset();
            double vol1 = volAvgs[i];
            double ppm2 = comps.get(j).getOffset();
            double vol2 = volAvgs[j];
            double delPPM1 = ppm1 - ppmCenter;
            double delPPM2 = ppmCenter - ppm2;
            double numPPM = Math.abs(delPPM1 - delPPM2);
            double demPPM = Math.abs(delPPM1 + delPPM2);
            if ((vol1 == 0.0) || (vol2 == 0.0)) {
                System.out.println("vol");
                return new CouplingData(ppmCenter, nComps);
            }
            double ppmScore;
            if (demPPM > 1.0e-6) {
                ppmScore = numPPM / demPPM;
            } else {
                ppmScore = 0.0;
            }
            double demInt = vol1 + vol2;
            double intScore;
            if (demInt == 0.0) {
                intScore = 0.0;
            } else {
                intScore = (vol1 - vol2) / demInt / 2.0;
            }
            if ((ppmScore > 0.3) || (intScore > 0.3)) {
                System.out.println("score");
                return new CouplingData(ppmCenter, nComps);
            }

        }
        double v1 = volAvgs[0];
        double vN = volAvgs[nComps - 1];
        double vNorm = (v1 + vN) / 2.0;
        if (vNorm == 0.0) {
            System.out.println("norm");
            return new CouplingData(ppmCenter, nComps);
        }
        double pow2Val = Math.pow(2.0, Math.round(Math.log(sumVol / vNorm) / Math.log(2.0)));
        double actVal = sumVol / vNorm;
        if (pow2Mode) {
            vNorm = (v1 + vN) / 2.0 * actVal / pow2Val * 0.97;
        }
        List<Double> ppmList = new ArrayList<>();
        int totalComp = 0;
        for (int iComp = 0; iComp < nComps; iComp++) {
            double ppm = comps.get(iComp).getOffset();
            double vol = volAvgs[iComp];
            int nEst = (int) Math.round(vol / vNorm);
            if (nEst < 1.0) {
                System.out.println("comps " + iComp + " " + vol + " " + vNorm);
                return new CouplingData(ppmCenter, nComps);
            }
            for (int j = 0; j < nEst; j++) {
                ppmList.add(ppm);
            }
            totalComp += nEst;

        }
        if (ppmList.size() == 1) {
            // singlet
            System.out.println("singlet");
            return new CouplingData(ppmCenter, 1);
        }

        double tol = 0.5 / sf;
        double lastCoupling = 0.0;
        int nEquals = 1;
        List<CouplingItem> couplings = new ArrayList<>();
        while (true) {
            int n1 = ppmList.size();
            double dppm = reducePPMList(ppmList, tol);
            double coupling = Math.round(100.0 * dppm * sf) / 100.0;
            int n2 = ppmList.size();
            if ((2 * n2) != n1) {
                System.out.println("reduce");
                return new CouplingData(ppmCenter, nComps);
            }
            if (Math.abs(coupling - lastCoupling) < tol) {
                nEquals++;
            } else {
                if (lastCoupling != 0.0) {
                    couplings.add(new CouplingItem(lastCoupling, nEquals + 1));
                    nEquals = 1;
                }
            }
            lastCoupling = coupling;
            if (n2 < 2) {
                couplings.add(new CouplingItem(lastCoupling, nEquals + 1));
                break;
            }
        }
        couplings.sort(comparing(p -> p.coupling(), reverseOrder()));
        System.out.println("end");
        return new CouplingData(couplings, ppmCenter, nComps);
    }

    public static boolean checkMultiplet(List<AbsMultipletComponent> comps) {
        boolean result = false;
        int nPeaks = comps.size();
        if ((nPeaks == 2) || (nPeaks == 3)) {
            double vol0 = comps.get(0).getVolume();
            double vol1 = comps.get(1).getVolume();
            if (nPeaks == 2) {
                if ((vol0 > DOUBLETRATIO * Math.abs(vol1)) || (vol1 > DOUBLETRATIO * Math.abs(vol0))) {
                    result = true;
                }
            } else if (nPeaks == 3) {
                double ppm0 = comps.get(0).getOffset();
                double ppm1 = comps.get(1).getOffset();
                double ppm2 = comps.get(2).getOffset();
                double coup01 = Math.abs(ppm1 - ppm0);
                double coup12 = Math.abs(ppm2 - ppm1);
                double delta = Math.abs(coup01 - coup12) / (coup01 + coup12) / 2.0;
                double vol2 = comps.get(2).getVolume();
                if (delta > 0.2) {
                    result = true;
                } else if ((vol0 > vol1) || (vol2 > vol1)) {
                    result = true;
                }

            }
        }
        return result;
    }

    public static double reducePPMList(List<Double> ppmList, double tol) {
        List<Double> newList = new ArrayList<>();
        double ppm0 = ppmList.get(0);
        double ppm1 = ppmList.get(1);
        double dppm = Math.abs(ppm0 - ppm1);
        int nPeaks = ppmList.size();
        tol *= 2.0;
        double sum = 0.0;
        int n = 0;
        for (int i = 0; i < nPeaks; i++) {
            double ppmA = ppmList.get(i);
            if (ppmA == Double.NaN) {
                continue;
            }
            for (int j = (i + 1); j < nPeaks; j++) {
                double ppmB = ppmList.get(j);
                if (ppmB == Double.NaN) {
                    continue;
                }
                double dTest = Math.abs(ppmA - ppmB);
                if (Math.abs(dTest - dppm) < tol) {
                    n++;
                    sum += dTest;
                    ppmList.set(i, Double.NaN);
                    ppmList.set(j, Double.NaN);
                    double ppmNew = (ppmA + ppmB) / 2.0;
                    newList.add(ppmNew);
                    break;
                }

            }
        }
        dppm = sum / n;
        ppmList.clear();
        ppmList.addAll(newList);
        return dppm;
    }

    public static void toDoublets(Multiplet multiplet) {
        if (multiplet.isGenericMultiplet()) {
            guessMultiplicityFromGeneric(multiplet);
            updateAfterMultipletConversion(multiplet);
        } else {
            String cPat = multiplet.getMultiplicity();
            switch (cPat) {
                case "t":
                    convertMultiplicity(multiplet, "t", "dd");
                    break;
                case "q":
                    convertMultiplicity(multiplet, "q", "ddd");
                    break;
            }
        }

    }

    public static void guessMultiplicityFromGeneric(Multiplet multiplet) {
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "1.P");
        int nPeaks = comps.size();
        String pattern = "";
        int jCoup = 0;
        for (int iCoup = 0; iCoup < 5; iCoup++) {
            pattern += "d";
            int nPeaksExpected = getMultiplicityCount(pattern);
            int nPeaksExpectedNext = getMultiplicityCount(pattern + "d");
            if (nPeaks == nPeaksExpected) {
                jCoup = iCoup + 1;
                break;
            } else if ((nPeaks > nPeaksExpected) && (nPeaks < nPeaksExpectedNext)) {
                jCoup = iCoup + 2;
                break;
            }
        }
        Map<Double, Integer> deltas = new HashMap<>();
        double ppm0 = comps.get(0).getOffset();
        double ppm1 = comps.get(1).getOffset();
        double ppmN = comps.get(comps.size() - 1).getOffset();
        double sf = comps.get(0).getMultiplet().getPeakDim().getSpectralDimObj().getSf();

        double deltaSmall = Math.abs(ppm1 - ppm0) * sf - 0.5; // subtract a little to allow for small variance.  not in Tcl
        double deltaLarge = Math.abs(ppmN - ppm0) * sf - (deltaSmall * (jCoup - 1)) + 0.5; // add a little
        double deltaRange = Math.abs(ppmN - ppm0) * sf;
        double deltaInt = Math.round(2.0 * deltaSmall) / 2.0;
        deltas.put(deltaInt, 1);

        deltaInt = Math.round(2.0 * deltaLarge) / 2.0;
        deltas.put(deltaInt, 1);

        for (int i = 0; i < comps.size() - 1; i++) {
            AbsMultipletComponent peakDimI = comps.get(i);
            double ppmI = peakDimI.getOffset();
            for (int j = (i + 1); j < comps.size(); j++) {
                AbsMultipletComponent peakDimJ = comps.get(j);
                double ppmJ = peakDimJ.getOffset();
                double delta = Math.abs(ppmJ - ppmI) * sf;
                deltaInt = Math.round(2.0 * delta) / 2.0;
                if ((delta >= deltaSmall) && (delta < deltaLarge)) {
                    if (deltas.containsKey(deltaInt)) {
                        deltas.put(deltaInt, deltas.get(deltaInt) + 1);
                    } else {
                        deltas.put(deltaInt, 1);
                    }
                }
            }
        }
        List<Double> sorted = deltas.entrySet().stream().
                sorted(comparing((p) -> -p.getValue())).
                map(p -> p.getKey()).
                collect(Collectors.toList());

        double deltaF = sorted.get(0);
        List<Double> couplings = new ArrayList<>();
        for (int i = 0; i < jCoup; i++) {
            if (i < sorted.size()) {
                couplings.add(sorted.get(i));
            } else {
                deltaF += 0.5;
                couplings.add(deltaF);
            }
        }
        couplings = couplings.subList(0, couplings.size() - 1);
        double sum = couplings.stream().collect(Collectors.summingDouble(p -> p));
        double deltaMax = deltaRange - sum;
        couplings.add(deltaMax);
        couplings.sort(comparing(p -> p, reverseOrder()));
        double[] values = new double[couplings.size()];
        double[] sin2thetas = new double[couplings.size()];
        int[] nValues = new int[couplings.size()];

        int i = 0;
        for (Double coupling : couplings) {
            values[i] = coupling;
            nValues[i] = 2;
            i++;
        }
        multiplet.setCouplingValues(values, nValues, 1.0, sin2thetas);
        updateAfterMultipletConversion(multiplet);
    }

    public static int getMultiplicityCount(String pattern) {
        String patterns = "sdtqphsp";
        int nSymbols = pattern.length();
        int count = 1;
        for (int i = 0; i < nSymbols; i++) {
            char symbol = pattern.charAt(i);
            int index = patterns.indexOf(symbol);
            if (index != -1) {
                count *= (index + 1);
            } else {
                return 0;
            }
        }
        return count;
    }

    public static double[] getBoundsOfPeakDims(List<AbsMultipletComponent> absComps, double ratio, double couplingMin) {
        double min = Double.MAX_VALUE;
        double max = Double.NEGATIVE_INFINITY;
        double sf = absComps.get(0).getMultiplet().getPeakDim().getSpectralDimObj().getSf();
        for (AbsMultipletComponent comp : absComps) {
            double ppm = comp.getOffset();
            double width = Math.abs(comp.getLineWidth());
            double widthHz = comp.getLineWidth() * sf;
            if (widthHz < couplingMin) {
                width = width * couplingMin / widthHz;
            }
            double tPPM = ppm + ratio * width;
            max = Math.max(max, tPPM);
            tPPM = ppm - ratio * width;
            min = Math.min(min, tPPM);

        }
        return new double[]{min, max};
    }

    public static double[] getBoundsOfMultiplet(Multiplet multiplet, double trimRatio) {
        List<AbsMultipletComponent> absComps = multiplet.getAbsComponentList();
        double min = Double.MAX_VALUE;
        double max = Double.NEGATIVE_INFINITY;

        for (AbsMultipletComponent absComp : absComps) {
            double ppm = absComp.getOffset();
            double width = Math.abs(absComp.getLineWidth());
            double tppm = ppm + trimRatio * width;
            max = Math.max(tppm, max);
            tppm = ppm - trimRatio * width;
            min = Math.min(tppm, min);
        }
        return new double[]{min, max};
    }

    public static void removeSplittingInMultiplet(Multiplet multiplet, String couplingSymbol) {
        int mCount = getMultiplicityCount(couplingSymbol);
        if (mCount == 0) {
            return;
        }
        String oldCoupling = multiplet.getCouplingsAsSimpleString();
        List<AbsMultipletComponent> comps = getSortedMultipletPeaks(multiplet, "1.P");
        int nPeaks = comps.size();
        int j = 0;
        double ppmFirst = 0.0;
        double ppmLast = 0.0;
        AbsMultipletComponent compKeep = null;
        ArrayList<AbsMultipletComponent> removeComps = new ArrayList<>();
        while (true) {
            double ppmSum = 0.0;
            double intensitySum = 0.0;
            double widthSum = 0.0;
            if (j == nPeaks) {
                break;
            }
            for (int i = 0; i < mCount; i++) {
                AbsMultipletComponent comp = comps.get(j);
                double ppm1 = comp.getOffset();
                double intensity1 = comp.getIntensity();
                if (intensity1 < 1.0e-6) { //  fixme does this make general sense
                    intensity1 = 1.0e-6;
                }
                double width1 = Math.abs(comp.getLineWidth());
                if (i == 0) {
                    ppmFirst = ppm1;
                    compKeep = comp;

                } else {
                    removeComps.add(comp);
                    if (i == (mCount - 1)) {
                        ppmLast = ppm1;
                    }

                }
                ppmSum += ppm1 * intensity1;
                intensitySum += intensity1;
                widthSum += width1;
                j++;
            }
            double ppm = ppmSum / intensitySum;
            double intensity = intensitySum / mCount;
            double width = widthSum / mCount;
            width = Math.abs(ppmFirst - ppmLast) + width;
            compKeep.setOffset(ppm);
            compKeep.setLineWidth(width);
        }
        // fixme removePeakDims(removeComps);
        multiplet.removeCoupling();
    }

    public static List<Peak> locatePeaks(PeakList peakList, double[][] region, int[] dim) {
        List<Peak> peaks = peakList.locatePeaks(region, dim);
        return peaks;
    }

    static DatasetRegion splitRegion(PeakList peakList, Dataset dataset, Vec vec, double sf, int[] dim, DatasetRegion region) {
        double maxSplitting = 18.0;
        double[][] limits = new double[1][2];

        limits[0][0] = region.getRegionStart(0);
        limits[0][1] = region.getRegionEnd(0);
        DatasetRegion newRegion = null;
        List<Peak> peaks = locatePeaks(peakList, limits, dim);
        if (peaks.size() > 1) {
            List<PeakDim> peakDims = new ArrayList<>();
            for (Peak peak : peaks) {
                peakDims.add(peak.peakDims[0]);
            }
            peakDims.sort(comparing(PeakDim::getChemShiftValue));
            for (int i = 0; i < peakDims.size() - 1; i++) {
                double ppm0 = peakDims.get(i).getChemShift();
                double ppm1 = peakDims.get(i + 1).getChemShift();
                double delta = Math.abs(ppm1 - ppm0) * sf;
                if (delta > maxSplitting) {
                    int pt0 = dataset.ppmToPoint(0, ppm0);
                    int pt1 = dataset.ppmToPoint(0, ppm1);

                    IndexValue indexValue = vec.minIndex(Math.min(pt0, pt1), Math.max(pt0, pt1));
                    int minPt = indexValue.getIndex();
                    double minPPM0 = dataset.pointToPPM(0, minPt - 1);
                    double minPPM1 = dataset.pointToPPM(0, minPt + 1);
                    newRegion = region.split(minPPM0, minPPM1);
                    break;
                }
            }
        }
        return newRegion;
    }

    public static void splitRegionsByPeakSep(Iterable<DatasetRegion> regions, PeakList peakList, Vec vec) {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double sf = peakList.getSpectralDim(0).getSf();
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        Set<DatasetRegion> newRegions = new TreeSet<>();
        int maxSplit = 4;
        for (DatasetRegion region : regions) {
            DatasetRegion testRegion = region;
            for (int iSplit = 0; iSplit < maxSplit; iSplit++) {
                DatasetRegion newRegion = splitRegion(peakList, dataset, vec, sf, dim, testRegion);
                if (newRegion == null) {
                    break;
                } else {
                    newRegions.add(newRegion);
                    testRegion = newRegion;
                }
            }
        }

        newRegions.forEach(dataset::addRegion);
    }

    public static void splitRegionsByPeakCount(Set<DatasetRegion> regions, PeakList peakList, Vec vec, int maxPeaks) {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[][] limits = new double[1][2];
        List<DatasetRegion> newRegions = new ArrayList<>();
        while (true) {
            regions.forEach(region -> {
                limits[0][0] = region.getRegionStart(0);
                limits[0][1] = region.getRegionEnd(0);
                List<Peak> peaks = locatePeaks(peakList, limits, dim);
                if (peaks.size() > maxPeaks) {
                    int nSplits = peaks.size() / maxPeaks;
                    double ppm0 = limits[0][0];
                    double ppm1 = limits[0][1];
                    double splitIncr = Math.abs(ppm0 - ppm1) / (nSplits + 1);
                    double r0 = ppm0 + splitIncr - splitIncr / 4.0;
                    double r1 = ppm0 + splitIncr + splitIncr / 4.0;
                    int pt0 = dataset.ppmToPoint(0, r0);
                    int pt1 = dataset.ppmToPoint(0, r1);
                    IndexValue indexValue = vec.minIndex(Math.min(pt0, pt1), Math.max(pt0, pt1));
                    int minPt = indexValue.getIndex();
                    double minPPM0 = dataset.pointToPPM(0, minPt - 1.0);
                    double minPPM1 = dataset.pointToPPM(0, minPt + 1.0);
                    newRegions.add(region.split(minPPM0, minPPM1));
                }
            });
            if (!newRegions.isEmpty()) {
                newRegions.forEach(dataset::addRegion);
            } else {
                break;
            }
        }
    }

    public static void linkPeaksInRegions(PeakList peakList, Collection<DatasetRegion> regions) {
        regions.stream().forEach(region -> {
            List<PeakDim> peakDims = findPeaksInRegion(peakList, region);
            if (!peakDims.isEmpty()) {
                Multiplet.groupPeakDims(peakDims);
            }
        });
    }

    public static Multiplet linkPeaksInRegion(PeakList peakList, DatasetRegion region) {
        List<PeakDim> peakDims = findPeaksInRegion(peakList, region);
        if (!peakDims.isEmpty()) {
            Multiplet multiplet = Multiplet.groupPeakDims(peakDims);
            return multiplet;
        }
        return null;
    }

    public static List<PeakDim> findPeaksInRegion(PeakList peakList, DatasetRegion region) {
        List<PeakDim> peakDims = new ArrayList<>();
        if (peakList != null) {
            int[] dim = new int[peakList.nDim];
            for (int i = 0; i < dim.length; i++) {
                dim[i] = i;
            }
            double[][] limits = new double[1][2];
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            for (Peak peak : peaks) {
                peakDims.add(peak.getPeakDim(0));

            }
        }
        return peakDims;
    }

    public static void unlinkPeaksInRegion(PeakList peakList, DatasetRegion region) {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        limits[0][0] = region.getRegionStart(0);
        limits[0][1] = region.getRegionEnd(0);
        List<Peak> peaks = locatePeaks(peakList, limits, dim);
        for (Peak peak : peaks) {
            PeakList.unLinkPeak(peak);
        }
    }

    public static void groupPeaks(PeakList peakList, List<DatasetRegion> regions) throws IOException {
        if (peakList.size() == 0) {
            return;
        }
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        Vec vec;
        try {
            vec = dataset.readVector(0, 0);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
            return;
        }
        splitRegionsByPeakSep(regions, peakList, vec);
        peakList.unLinkPeaks();
        peakList.sortPeaks(0, false);
        peakList.reNumber();
        linkPeaksInRegions(peakList, regions);
    }

    public static List<Peak> getLinkRoots(PeakList peakList) {
        int n = peakList.size();
        for (int i = 0; i < n; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak.getStatus() >= 0) {
                peak.setStatus(1);
            }
        }
        List<Peak> peaks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Peak peak = peakList.getPeak(i);
            if (!peak.isDeleted()) {
                peaks.add(peak);
            }
        }
        return peaks;
    }
}
