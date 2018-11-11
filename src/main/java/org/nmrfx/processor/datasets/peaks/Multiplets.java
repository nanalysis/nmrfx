package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetRegion;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.Vec.IndexValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 *
 * @author Bruce Johnson
 */
public class Multiplets {

    public static double DOUBLETRATIO = 3.0;

    public static Multiplet getMultiplet(String mSpec) throws IllegalArgumentException {
        Multiplet multiplet;
        if (mSpec.charAt(mSpec.length() - 1) == 'm') {
            multiplet = PeakList.getAMultiplet(mSpec);
        } else {
            //throw new IllegalArgumentException("Multiplier specifier \"" + mSpec + "\" not valid");
            PeakDim peakDim = PeakList.getPeakDimObject(mSpec);
            multiplet = peakDim.getMultiplet();
        }
        return multiplet;
    }

    public static PeakDim getPeakOrMultipletRoot(String specifier) throws IllegalArgumentException {
        if (specifier.charAt(specifier.length() - 1) == 'm') {
            Multiplet multiplet = getMultiplet(specifier);
            return getMultipletRoot(multiplet);
        } else {
            PeakDim peakDim = PeakList.getPeakDimObject(specifier);
            return peakDim;
        }

    }

    public static PeakDim getMultipletRoot(Multiplet multiplet) throws IllegalArgumentException {
        if (multiplet == null) {
            return null;
        } else {
            PeakDim rootPeakDim = multiplet.myPeakDim;
            return rootPeakDim;
        }

    }

    public static ArrayList<PeakDim> getSortedMultipletPeaks(String mSpec, String mode) {
        PeakDim peakDim = getPeakOrMultipletRoot(mSpec);
        return getSortedMultipletPeaks(peakDim, mode);
    }

    public static ArrayList<PeakDim> getSortedMultipletPeaks(Multiplet multiplet, String mode) {
        PeakDim startPeakDim = getMultipletRoot(multiplet);
        return getSortedMultipletPeaks(startPeakDim, mode);
    }

    public static ArrayList<PeakDim> getSortedMultipletPeaks(PeakDim startPeakDim, String mode) {
        ArrayList<PeakDim> peakDimList = new ArrayList<>();
        List<PeakDim> peakDims = startPeakDim.getCoupledPeakDims();
        PeakList refList = startPeakDim.myPeak.peakList;
        for (PeakDim peakDim : peakDims) {
            if (peakDim.myPeak.peakList == refList) {
                peakDimList.add(peakDim);
            }
        }
        if (mode.equals("int")) {
            peakDimList.sort(comparing((p) -> p.myPeak.getIntensity()));
        } else {
            peakDimList.sort(comparing(PeakDim::getChemShiftValue));
        }
        return peakDimList;
    }

    public static List<PeakDim> getWeakestPeaksInMultiplet(String mSpec, int nRemove) {
        Multiplet multiplet = getMultiplet(mSpec);
        return getWeakestPeaksInMultiplet(multiplet, nRemove);
    }

    public static List<PeakDim> getWeakestPeaksInMultiplet(Multiplet multiplet, int nRemove) {
        ArrayList<PeakDim> peakDimList = getSortedMultipletPeaks(multiplet, "int");
        if (nRemove < 0) {
            nRemove = peakDimList.size() + nRemove;
        }
        List<PeakDim> weakPeaks = peakDimList.stream().limit(nRemove).collect(Collectors.toList());
        return weakPeaks;

    }

    public static String getCouplingPattern(String mSpec) {
        Multiplet multiplet = getMultiplet(mSpec);
        return getCouplingPattern(multiplet);
    }

    public static String getCouplingPattern(Multiplet multiplet) {
//        return multiplet.getMultiplicity();
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

    /*
    proc ::dcs::multiplets::getCouplingPattern {couplings} {
    set patterns "s d t q p"
    set pattern {}
    if {[llength $couplings] == 0} {
        set pattern ""
    } elseif {[llength $couplings] == 1} {
        set pattern s
    } else {
        foreach "j n" $couplings {
            set symbol [lindex $patterns $n]
            append pattern $symbol
        }
        if {$pattern == ""} {
            set pattern m
        }
    }
    return $pattern
}

     */
    public static void removeWeakPeaksInMultiplet(String mSpec, int nRemove) {
        List<PeakDim> weakPeaks = getWeakestPeaksInMultiplet(mSpec, nRemove);
        removePeaks(getPeaks(weakPeaks));
    }

    public static void removeWeakPeaksInMultiplet(Multiplet multiplet, int nRemove) {
        List<PeakDim> weakPeaks = getWeakestPeaksInMultiplet(multiplet, nRemove);
        removePeaks(getPeaks(weakPeaks));
    }

    public static List<Peak> getPeaks(List<PeakDim> peakDims) {
        return peakDims.stream().map(p -> p.myPeak).collect(Collectors.toList());
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

    public static void addPeaksToMutliplet(Multiplet multiplet, double... ppms) {
        int iDim = 0;
        PeakDim peakDim = getMultipletRoot(multiplet);
        PeakList refList = peakDim.myPeak.peakList;
        int type = peakDim.myPeak.getType();
        double intensity = peakDim.myPeak.getIntensity();
        double volume = peakDim.myPeak.getVolume1();
        float width = peakDim.getLineWidth();
        float bounds = width * 3.0f;
        for (double ppm : ppms) {
            Peak peak = refList.getNewPeak();
            peak.setIntensity((float) intensity / 2.0f);
            peak.setVolume1((float) volume / 2.0f);
            peak.peakDim[iDim].setLineWidthValue((float) width);
            peak.peakDim[iDim].setBoundsValue((float) bounds);
            peak.peakDim[iDim].setChemShiftValue((float) ppm);
            peak.setType(type);
            PeakList.couplePeakDims(peakDim, peak.peakDim[iDim]);
        }
        updateAfterMultipletConversion(multiplet);
        //analyzeMultiplet(multiplet.getOrigin());
    }

    public static void addOuterCoupling(int addNumber, String mSpec) {
        Multiplet multiplet = getMultiplet(mSpec);
        addOuterCoupling(addNumber, multiplet);
    }

    public static void addOuterCoupling(int addNumber, Multiplet multiplet) {
        PeakDim peakDim = getMultipletRoot(multiplet);
        int type = peakDim.myPeak.getType();
        ArrayList<PeakDim> peakDims = getSortedMultipletPeaks(peakDim, "1.P");
        PeakList refList = peakDim.myPeak.peakList;
        PeakDim firstPeakDim = peakDims.get(0);
        double firstPPM = firstPeakDim.getChemShift();
        PeakDim lastPeakDim = peakDims.get(peakDims.size() - 1);
        double lastPPM = lastPeakDim.getChemShift();

        double intensity = firstPeakDim.myPeak.getIntensity();
        double volume = firstPeakDim.myPeak.getVolume1();
        float width = firstPeakDim.getLineWidth();
        float bounds = width * 3.0f;
        float dppm;

        if (peakDims.size() == 1) {
            peakDim.myPeak.setIntensity((float) intensity);
            peakDim.myPeak.setVolume1((float) volume / 2.0f);
            width /= 3.0;
            peakDim.setLineWidthValue((float) width);
            peakDim.setBoundsValue((float) width * 3.0f);
            dppm = width;
        } else {
            double secondPPM = peakDims.get(1).getChemShift();
            dppm = (float) Math.abs(firstPPM - secondPPM);
        }
        int addWhich = -1;
        float dSign = -1;
        if (addNumber == 1) {
            double lastIntensity = lastPeakDim.myPeak.getIntensity();
            double lastVolume = lastPeakDim.myPeak.getVolume1();
            if (lastIntensity < intensity) {
                intensity = lastIntensity;
                volume = lastVolume;
                addWhich = 0;
                dSign = -1f;
            } else {
                addWhich = 1;
                dSign = 1f;
            }
            for (PeakDim peakDim2 : peakDims) {
                float ppm = peakDim2.getChemShift();
                peakDim2.setChemShiftValue(ppm + dSign * dppm / 2.0f);

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
                newPeak.peakDim[iDim].setLineWidthValue((float) width);
                newPeak.peakDim[iDim].setBoundsValue((float) bounds);
                newPeak.peakDim[iDim].setChemShiftValue((float) ppmNew);
                newPeak.setType(type);
                PeakList.couplePeakDims(peakDim, newPeak.peakDim[iDim]);
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

    public static void convertMultiplicity(String mSpec, String multOrig, String multNew) {
        Multiplet multiplet = getMultiplet(mSpec);
        convertMultiplicity(multiplet, multOrig, multNew);
    }

    public static void splitToMultiplicity(Multiplet multiplet, String couplingType) {
        List<PeakDim> peakDims = getSortedMultipletPeaks(multiplet, "1.P");
        Coupling oldCoupling = multiplet.getCoupling();

        double couplingValue = 0.0;
        for (PeakDim peakDim : peakDims) {
            couplingValue = doMultipletSplit(peakDim, couplingType);
        }
        Coupling coupling = multiplet.getCoupling();
        addCoupling(multiplet, oldCoupling, couplingType, couplingValue);
        coupling = multiplet.getCoupling();

        PeakList peakList = multiplet.myPeakDim.myPeak.peakList;
        peakList.sortPeaks(0, false);
        peakList.reNumber();

    }

    public static double doMultipletSplit(PeakDim peakDim, String couplingType) {
        float intensity = peakDim.myPeak.getIntensity();
        float volume = peakDim.myPeak.getVolume1();
        float width = peakDim.getLineWidth();
        float ppm = peakDim.getChemShift();
        int type = peakDim.myPeak.getType();
        int nNew = "sdtqphsp".indexOf(couplingType);
        float widthNew = width / (nNew + 1);
        float boundsNew = widthNew * 2.0f; // fixme
        float intensityNew = intensity / (nNew + 1);   // fixme should get intensities from binomial
        float volumeNew = volume / (nNew + 1);

        ArrayList<PeakDim> peakDims = new ArrayList<>();
        peakDims.add(peakDim);
        float jValue = widthNew;
        for (int i = 0; i < nNew; i++) {
            Peak newPeak = peakDim.myPeak.peakList.getNewPeak();
            PeakDim newPeakDim = newPeak.peakDim[0];
            peakDims.add(newPeakDim);
        }
        int i = 0;
        for (PeakDim newPeakDim : peakDims) {
            Peak newPeak = newPeakDim.myPeak;
            newPeak.setType(type);
            newPeak.setIntensity(intensityNew);
            newPeak.setVolume1(volumeNew);
            newPeakDim.setChemShiftValue(ppm - (0.5f * nNew * jValue) + i * jValue);
            newPeakDim.setLineWidthValue(widthNew);
            newPeakDim.setBoundsValue(boundsNew);
            i++;
        }
        peakDims.stream().filter(p -> p != peakDim).forEach((newPeakDim) -> {
            PeakList.couplePeakDims(peakDim, newPeakDim);
        });
        double couplingGuess = jValue * peakDim.myPeak.peakList.getSpectralDim(0).getSf();
        return couplingGuess;

    }

    public static void setCouplingPattern(Multiplet multiplet, String multNew) {
        List<PeakDim> peakDims = getSortedMultipletPeaks(multiplet, "1.P");
        int nPeaks = peakDims.size();
        int multCount = getMultiplicityCount(multNew);
        if (nPeaks == multCount) {
            float firstPPM = peakDims.get(0).getChemShift();
            float lastPPM = peakDims.get(nPeaks - 1).getChemShift();
            float deltaPPM = Math.abs(lastPPM - firstPPM) / (nPeaks - 1);
            double sf = multiplet.myPeakDim.myPeak.peakList.getSpectralDim(0).getSf();
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
        List<PeakDim> peakDims = getSortedMultipletPeaks(multiplet, "int");
        int nPeaks = peakDims.size();
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
        if (multNew.equals("m")) {
            // multiplet.setCouplingValues("0.0");
        } else if (multNew.equals("s")) {
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
            //guessMultiplicityFromGeneric(multiplet);
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
        return measure(multiplet, "rms");
    }

    public static Optional<Double> deviation(Multiplet multiplet) {
        return measure(multiplet, "maxdev");
    }

    public static Optional<Double> measure(Multiplet multiplet, String mode) {
        List<PeakDim> peakDims = getSortedMultipletPeaks(multiplet, "1.P");
        PeakDim peakDim = getMultipletRoot(multiplet);
        Peak refPeak = peakDim.myPeak;
        PeakList peakList = refPeak.peakList;
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        Optional<Double> result = Optional.empty();

        if (dataset != null) {
            double[] bounds = Analyzer.getRegionBounds(dataset.getRegions(), 0, refPeak.peakDim[0].getChemShift());
            PeakFitting peakFitting = new PeakFitting(dataset);
            try {
                double rms = peakFitting.fitPeakDims(peakDims, "jfit", bounds, mode);
                result = Optional.of(rms);
            } catch (IllegalArgumentException | PeakFitException | IOException ex) {
                System.out.println("error in fit " + ex.getMessage());
            }
        }
        return result;
    }

    public static Optional<Double> updateAfterMultipletConversion(Multiplet multiplet) {
        List<PeakDim> peakDims = getSortedMultipletPeaks(multiplet, "1.P");
        PeakDim peakDim = getMultipletRoot(multiplet);
        Peak refPeak = peakDim.myPeak;
        PeakList peakList = refPeak.peakList;
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        Optional<Double> result = Optional.empty();
        if (dataset != null) {
            double[] bounds = Analyzer.getRegionBounds(dataset.getRegions(), 0, refPeak.peakDim[0].getChemShift());

            PeakFitting peakFitting = new PeakFitting(dataset);
            try {
                double rms = peakFitting.fitPeakDims(peakDims, "jfit", bounds, "all");
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
            System.out.println(peakDim.myPeak.getName() + " " + peakDim.myPeak.getIntensity() + " " + peakDim.getChemShift());
        }
    }

    public static void analyzeMultiplets(PeakList peakList) {
        List<Peak> lPeaks = getLinkRoots(peakList);
        for (Peak peak : lPeaks) {
            analyzeMultiplet(peak);
        }
    }

    public static void analyzeMultiplet(Peak peak) {
        CouplingData couplingData = determineMultiplicity(peak, true);
        List<PeakDim> peakSet = peak.peakDim[0].getCoupledPeakDims();
        System.out.println(peak.getName() + " " + couplingData.toString() + " " + peakSet.size());
        dumpPeakDims(peakSet);
        Multiplet multiplet = peak.peakDim[0].getMultiplet();
        dumpPeakDims(peakSet);
        if (peakSet.size() == 1) {
            multiplet.setSinglet();
        }
        multiplet.setCenter(couplingData.centerPPM);
        dumpPeakDims(peakSet);
        double[] values = new double[couplingData.couplingItems.size()];
        System.out.println("val " + values.length);
        if (values.length > 0) {
            int[] nValues = new int[couplingData.couplingItems.size()];
            int i = 0;
            for (CouplingItem item : couplingData.couplingItems) {
                values[i] = item.getCoupling();
                nValues[i] = item.getNSplits();
                i++;
            }
            double[] sin2thetas = new double[values.length];
            multiplet.setCouplingValues(values, nValues, 1.0, sin2thetas);
        }
        peakSet = peak.peakDim[0].getCoupledPeakDims();
        dumpPeakDims(peakSet);

    }

    public static CouplingData determineMultiplicity(Peak peak, boolean pow2Mode) {
        PeakList peakList = peak.peakList;
        PeakDim refPeakDim = peak.peakDim[0];
        List<PeakDim> peakSet = refPeakDim.getCoupledPeakDims();

        List<PeakDim> peakDims = peakSet.stream().
                filter(p -> p.myPeak.peakList == peakList).
                collect(Collectors.toList());
        peakDims.sort(comparing(p -> p.getChemShift(), reverseOrder()));
        int nPeaks = peakDims.size();
        double ppmFirst = peakDims.get(0).getChemShift();
        double ppmLast = peakDims.get(nPeaks - 1).getChemShift();
        double sumPPM = peakDims.stream().collect(Collectors.summingDouble(p -> p.getChemShift()));
        double sumVol = peakDims.stream().collect(Collectors.summingDouble(p -> p.myPeak.getVolume1()));
        double ppmCenter = sumPPM / nPeaks;
        double sf = refPeakDim.getSpectralDimObj().getSf();
        if (checkMultiplet(peakDims)) {
            System.out.println("check");
            return new CouplingData(ppmCenter, nPeaks);
        } else if (nPeaks == 2) {
            double coupling = Math.round(100 * Math.abs(ppmFirst - ppmLast) * sf) / 100.0;
            // doublet
            System.out.println("doublet");
            return new CouplingData(ppmCenter, coupling, 2, nPeaks);
        } else if (nPeaks == 3) {
            double coupling = Math.round(100 * Math.abs(ppmFirst - ppmLast) * sf) / 200.0;
            System.out.println("3let");
            return new CouplingData(ppmCenter, coupling, 3, nPeaks);
        }
        // symmetrize peak Volumes
        double[] volAvgs = new double[nPeaks];
        for (int i = 0; i < nPeaks; i++) {
            volAvgs[i] = peakDims.get(i).myPeak.getVolume1();
        }
        for (int i = 0; i < (nPeaks / 2); i++) {
            int j = nPeaks - i - 1;
            double vol1 = peakDims.get(i).myPeak.getVolume1();
            double vol2 = peakDims.get(j).myPeak.getVolume1();
            double volAvg = (vol1 + vol2) / 2.0;
            volAvgs[i] = volAvg;
            volAvgs[j] = volAvg;
        }
        for (int i = 0; i < (nPeaks / 2); i++) {
            int j = nPeaks - i - 1;
            double ppm1 = peakDims.get(i).getChemShift();
            double vol1 = volAvgs[i];
            double ppm2 = peakDims.get(j).getChemShift();
            double vol2 = volAvgs[j];
            double delPPM1 = ppm1 - ppmCenter;
            double delPPM2 = ppmCenter - ppm2;
            double numPPM = Math.abs(delPPM1 - delPPM2);
            double demPPM = Math.abs(delPPM1 + delPPM2);
            if ((vol1 == 0.0) || (vol2 == 0.0)) {
                System.out.println("vol");
                return new CouplingData(ppmCenter, nPeaks);
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
                return new CouplingData(ppmCenter, nPeaks);
            }

        }
        double v1 = volAvgs[0];
        double vN = volAvgs[nPeaks - 1];
        double vNorm = (v1 + vN) / 2.0;
        if (vNorm == 0.0) {
            System.out.println("norm");
            return new CouplingData(ppmCenter, nPeaks);
        }
        double pow2Val = Math.pow(2.0, Math.round(Math.log(sumVol / vNorm) / Math.log(2.0)));
        double actVal = sumVol / vNorm;
        if (pow2Mode) {
            vNorm = (v1 + vN) / 2.0 * actVal / pow2Val * 0.97;
        }
        List<Double> ppmList = new ArrayList<>();
        int totalComp = 0;
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            double ppm = peakDims.get(iPeak).getChemShift();
            double vol = volAvgs[iPeak];
            int comps = (int) Math.round(vol / vNorm);
            if (comps < 1.0) {
                System.out.println("comps " + peakDims.get(iPeak).getPeak().getName() + " " + vol + " " + vNorm);
                return new CouplingData(ppmCenter, nPeaks);
            }
            for (int j = 0; j < comps; j++) {
                ppmList.add(ppm);
            }
            totalComp += comps;

        }
        if (ppmList.size() == 1) {
            // singlet
            System.out.println("singlet");
            return new CouplingData(ppmCenter, 1);
        }
//        System.out.println("c " + ppmList.size() + " " + ppmList.toString());

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
                return new CouplingData(ppmCenter, nPeaks);
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
        couplings.sort(comparing(p -> p.getCoupling(), reverseOrder()));
        System.out.println("end");
        return new CouplingData(couplings, ppmCenter, nPeaks);
    }

    public static boolean checkMultiplet(List<PeakDim> peakDims) {
        boolean result = false;
        int nPeaks = peakDims.size();
        if ((nPeaks == 2) || (nPeaks == 3)) {
            double vol0 = peakDims.get(0).myPeak.getVolume1();
            double vol1 = peakDims.get(1).myPeak.getVolume1();
            if (nPeaks == 2) {
                if ((vol0 > DOUBLETRATIO * Math.abs(vol1)) || (vol1 > DOUBLETRATIO * Math.abs(vol0))) {
                    result = true;
                }
            } else if (nPeaks == 3) {
                double ppm0 = peakDims.get(0).getChemShift();
                double ppm1 = peakDims.get(1).getChemShift();
                double ppm2 = peakDims.get(2).getChemShift();
                double coup01 = Math.abs(ppm1 - ppm0);
                double coup12 = Math.abs(ppm2 - ppm1);
                double delta = Math.abs(coup01 - coup12) / (coup01 + coup12) / 2.0;
                double vol2 = peakDims.get(2).myPeak.getVolume1();
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
        List<PeakDim> peakDims = getSortedMultipletPeaks(multiplet, "1.P");
        int nPeaks = peakDims.size();
        String pattern = "";
        int jCoup = 0;
        for (int iCoup = 0; iCoup < 5; iCoup++) {
            pattern += "d";
            int nPeaksExpected = getMultiplicityCount(pattern);
            int nPeaksExpectedNext = getMultiplicityCount(pattern + "d");
//            System.out.println(pattern + " " + nPeaksExpected + " " + nPeaksExpectedNext + " " + nPeaks);
            if (nPeaks == nPeaksExpected) {
                jCoup = iCoup + 1;
                break;
            } else if ((nPeaks > nPeaksExpected) && (nPeaks < nPeaksExpectedNext)) {
                jCoup = iCoup + 2;
                break;
            }
        }
        Map<Double, Integer> deltas = new HashMap<>();
        double ppm0 = peakDims.get(0).getChemShift();
        double ppm1 = peakDims.get(1).getChemShift();
        double ppmN = peakDims.get(peakDims.size() - 1).getChemShift();
        double sf = peakDims.get(0).getSpectralDimObj().getSf();

        double deltaSmall = Math.abs(ppm1 - ppm0) * sf - 0.5; // subtract a little to allow for small variance.  not in Tcl
        double deltaLarge = Math.abs(ppmN - ppm0) * sf - (deltaSmall * (jCoup - 1)) + 0.5; // add a little
        double deltaRange = Math.abs(ppmN - ppm0) * sf;
        double deltaInt = Math.round(2.0 * deltaSmall) / 2.0;
        deltas.put(deltaInt, 1);

        deltaInt = Math.round(2.0 * deltaLarge) / 2.0;
        deltas.put(deltaInt, 1);

        for (int i = 0; i < peakDims.size() - 1; i++) {
            PeakDim peakDimI = peakDims.get(i);
            double ppmI = peakDimI.getChemShift();
            for (int j = (i + 1); j < peakDims.size(); j++) {
                PeakDim peakDimJ = peakDims.get(j);
                double ppmJ = peakDimJ.getChemShift();
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

    public static double[] getBoundsOfPeakDims(List<PeakDim> peakDims, double ratio, double couplingMin) {
        double min = Double.MAX_VALUE;
        double max = Double.NEGATIVE_INFINITY;
        double sf = peakDims.get(0).getSpectralDimObj().getSf();
        for (PeakDim peakDim : peakDims) {
            double ppm = peakDim.getChemShift();
            double width = Math.abs(peakDim.getLineWidth());
            double widthHz = peakDim.getLineWidth() * sf;
            if (widthHz < couplingMin) {
                width = width * couplingMin / widthHz;
            }
            double tPPM = ppm + ratio * width;
            max = Math.max(max, tPPM);
            tPPM = ppm - ratio * width;
            min = Math.min(min, tPPM);

        }
        double[] result = {min, max};
        return result;
    }

    public static void removeSplittingInMultiplet(Multiplet multiplet, String couplingSymbol) {
        int mCount = getMultiplicityCount(couplingSymbol);
        if (mCount == 0) {
            return;
        }
        String oldCoupling = multiplet.getCouplingsAsSimpleString();
        List<PeakDim> peakDims = getSortedMultipletPeaks(multiplet, "1.P");
        int nPeaks = peakDims.size();
        int j = 0;
        double ppmFirst = 0.0;
        double ppmLast = 0.0;
        PeakDim peakKeep = null;
        ArrayList<PeakDim> removePeaks = new ArrayList<>();
        while (true) {
            double ppmSum = 0.0;
            double intensitySum = 0.0;
            double widthSum = 0.0;
            if (j == nPeaks) {
                break;
            }
            for (int i = 0; i < mCount; i++) {
                PeakDim peakDim = peakDims.get(j);
                double ppm1 = peakDim.getChemShift();
                double intensity1 = peakDim.myPeak.getIntensity();
                if (intensity1 < 1.0e-6) { //  fixme does this make general sense
                    intensity1 = 1.0e-6;
                }
                double width1 = Math.abs(peakDim.getLineWidth());
                if (i == 0) {
                    ppmFirst = ppm1;
                    peakKeep = peakDim;

                } else {
                    removePeaks.add(peakDim);
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
            peakKeep.setChemShiftValue((float) ppm);
            peakKeep.setLineWidthValue((float) width);
        }
        removePeakDims(removePeaks);
        multiplet.removeCoupling();
    }

    public static List<Peak> locatePeaks(PeakList peakList, double[][] region, int[] dim) {
        List<Peak> peaks = peakList.locatePeaks(region, dim);
        return peaks;
    }

    public static void splitRegionsByPeakSep(Set<DatasetRegion> regions, PeakList peakList, Vec vec) {
        double maxSplitting = 18.0;
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double sf = peakList.getSpectralDim(0).getSf();
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[][] limits = new double[1][2];
        Set<DatasetRegion> newRegions = new TreeSet<>();
        regions.stream().forEach(region -> {
            limits[0][0] = region.getRegionStart(0);
            limits[0][1] = region.getRegionEnd(0);
            DatasetRegion newRegion = null;
            List<Peak> peaks = locatePeaks(peakList, limits, dim);
            if (peaks.size() > 1) {
                List<PeakDim> peakDims = new ArrayList<>();
                for (Peak peak : peaks) {
                    peakDims.add(peak.peakDim[0]);
                }
                peakDims.sort(comparing(PeakDim::getChemShiftValue));
                for (int i = 0; i < peakDims.size() - 1; i += 2) {
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
                    }
                }
            }
            if (newRegion != null) {
                newRegions.add(newRegion);
            }
        });
        if (!newRegions.isEmpty()) {
            regions.addAll(newRegions);
        }
    }

    public static void splitRegionsByPeakCount(Set<DatasetRegion> regions, PeakList peakList, Vec vec, int maxPeaks) {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[][] limits = new double[1][2];
        Set<DatasetRegion> newRegions = new TreeSet<>();
        while (true) {
            regions.stream().forEach(region -> {
                limits[0][0] = region.getRegionStart(0);
                limits[0][1] = region.getRegionEnd(0);
                DatasetRegion newRegion = null;
                List<Peak> peaks = locatePeaks(peakList, limits, dim);
                if (peaks.size() > maxPeaks) {
                    List<PeakDim> peakDims = new ArrayList<>();
                    peaks.forEach((peak) -> {
                        peakDims.add(peak.peakDim[0]);
                    });
                    int nSplits = peaks.size() / maxPeaks;
                    peakDims.sort(comparing(PeakDim::getChemShiftValue));
                    double ppm0 = limits[0][0];
                    double ppm1 = limits[0][1];
                    double splitIncr = Math.abs(ppm0 - ppm1) / (nSplits + 1);
                    double r0 = ppm0 + splitIncr - splitIncr / 4.0;
                    double r1 = ppm0 + splitIncr + splitIncr / 4.0;
                    int pt0 = dataset.ppmToPoint(0, r0);
                    int pt1 = dataset.ppmToPoint(0, r1);
                    IndexValue indexValue = vec.minIndex(Math.min(pt0, pt1), Math.max(pt0, pt1));
                    int minPt = indexValue.getIndex();

                    double minPPM0 = dataset.pointToPPM(0, minPt - 1);
                    double minPPM1 = dataset.pointToPPM(0, minPt + 1);
                    newRegion = region.split(minPPM0, minPPM1);
                }
                if (newRegion != null) {
                    newRegions.add(newRegion);
                }
            });
            if (!newRegions.isEmpty()) {
                regions.addAll(newRegions);
            } else {
                break;
            }
        }
    }

    public static void linkPeaksInRegion(PeakList peakList, Set<DatasetRegion> regions) {
        Set<DatasetRegion> newRegions = new TreeSet<>();
        regions.stream().forEach(region -> {
            linkPeaksInRegion(peakList, region);
        });
    }

    public static PeakDim linkPeaksInRegion(PeakList peakList, DatasetRegion region) {
        int[] dim = new int[peakList.nDim];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        double[][] limits = new double[1][2];
        limits[0][0] = region.getRegionStart(0);
        limits[0][1] = region.getRegionEnd(0);
        DatasetRegion newRegion = null;
        PeakDim rootPeak = null;
        List<Peak> peaks = locatePeaks(peakList, limits, dim);
        if (peaks.size() > 0) {
            List<PeakDim> needsLinking = new ArrayList<>();
            List<PeakDim> possibleRoots = new ArrayList<>();
            List<PeakDim> rootPeaks = new ArrayList<>();
            rootPeak = peaks.get(0).getPeakDim(0);
            List<PeakDim> peakDims = new ArrayList<>();
            for (Peak peak : peaks) {
                peakDims.add(peak.peakDim[0]);
            }
            peakDims.sort(comparing(PeakDim::getChemShiftValue));
            for (PeakDim peakDim : peakDims) {
                List<PeakDim> peakDims2 = peakDim.getCoupledPeakDims();
                if (peakDims2.size() == 1) {
                    needsLinking.add(peakDim);
                } else {
                    rootPeak = peakDim;
                    possibleRoots.add(peakDim);
                }
            }
            if (!needsLinking.isEmpty()) {
                rootPeaks.add(rootPeak);
            }
//                System.out.printf("roots %d poss %d need %d %s\n", rootPeaks.size(), possibleRoots.size(), needsLinking.size(), rootPeak.getName());
            for (PeakDim peakDim : needsLinking) {
                double ppm = peakDim.getChemShift();
                double min = Double.MAX_VALUE;
                for (PeakDim root : possibleRoots) {
                    double rppm = root.getChemShift();
                    double delta = Math.abs(ppm - rppm);
                    if (delta < min) {
                        min = delta;
                        rootPeak = root;
                    }
                }
                if (rootPeak != peakDim) {
                    PeakList.couplePeakDims(rootPeak, peakDim);
                }
            }
        }
        return rootPeak;

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

    public static void groupPeaks(PeakList peakList, Set<DatasetRegion> regions) throws IOException {
        if (peakList.size() == 0) {
            return;
        }
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        Vec vec = new Vec(32);
        dataset.readVector(vec, 0, 0);
        System.out.println(dataset + " " + vec);

        splitRegionsByPeakSep(regions, peakList, vec);
        //splitRegionsByPeakCount(regions, peakList, vec, 24);
        peakList.unLinkPeaks();
        peakList.sortPeaks(0, false);
        peakList.reNumber();
        linkPeaksInRegion(peakList, regions);

    }

    /*
    set nPeaks [llength $pkData]
    set peaks {}
    set j 0
    while {1} {
        set ppmSum 0.0
        set intSum 0.0
        set wSum 0.0
        if {$j == $nPeaks} {
            break
        }
        for {set i 0} {$i < $mCount} {incr i} {
            set pkData1 [lindex $pkData $j]
            set peak1 [lindex $pkData1 0]
            set ppm1 [lindex $pkData1 1]
            set int1 [expr {abs([nv_peak elem int $peak1])}]
            if {$int1  < 1.0e-6} {
                set int1 1.0e-6
            }
            set w1 [expr {abs([nv_peak elem 1.W $peak1])}]
            if {$i == 0} {
                set ppmFirst $ppm1
                set peakKeep $peak1
            } else {
                lappend peaks $peak1
                if {$i == ($mCount-1)} {
                    set ppmLast $ppm1
                }
            }
            set ppmSum [expr {$ppmSum+$ppm1*$int1}]
            set intSum [expr {$intSum+$int1}]
            set wSum [expr {$wSum+$w1}]
            incr j
        }
        set ppm [expr {$ppmSum/$intSum}]
        set int [expr {$intSum/$mCount}]
        set w [expr {$wSum/$mCount}]
        set w [expr {abs($ppmFirst-$ppmLast)+$w}]
        nv_peak elem 1.P $peakKeep $ppm
        nv_peak elem 1.W $peakKeep $w
    }
    set list [::nv::peak::getListFromPeakSpec $peak]
    ::dcs::peaks::gui::removeThesePeaks nv_win $peaks
    ::dcs::multiplets::convertCoupling $multiplet remove {} $oldCoupling


     */
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
            if (peak.getStatus() <= 0) {
                continue;
            }
            peaks.add(peak);
            List<PeakDim> cpeakDims = peak.peakDim[0].getCoupledPeakDims();
            for (PeakDim peakDim : cpeakDims) {
                if (peakDim.myPeak.getStatus() > 0) {
                    peakDim.myPeak.setStatus(0);
                }
            }
        }
        return peaks;
    }
}
