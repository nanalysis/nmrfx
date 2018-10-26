package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.datasets.Dataset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.nmrfx.processor.datasets.peaks.PeakList.FIT_ALL;
import static org.nmrfx.processor.datasets.peaks.PeakList.FIT_AMPLITUDES;
import static org.nmrfx.processor.datasets.peaks.PeakList.FIT_LW_AMPLITUDES;
import static org.nmrfx.processor.datasets.peaks.PeakList.FIT_MAX_DEV;
import static org.nmrfx.processor.datasets.peaks.PeakList.FIT_RMS;

/**
 *
 * @author Bruce Johnson
 */
public class PeakFitting {

    final Dataset dataset;
    boolean success = false;
    double rmsValue = 0.0;
    boolean anyFit = false;

    public PeakFitting(Dataset dataset) {
        this.dataset = dataset;
    }

    public double getRMS() {
        return rmsValue;
    }

    public boolean anyFit() {
        return anyFit;
    }

    public double[] getBounds(double[] regions, double ppm) {
        for (int i = 0; i < regions.length; i += 2) {
            double r1 = regions[i];
            double r2 = regions[i + 1];
            if ((r1 > ppm) && (r2 < ppm)) {
                double[] result = {r2, r1};
                return result;
            } else if ((r2 > ppm) && (r1 < ppm)) {
                double[] result = {r1, r2};
                return result;
            }
        }
        return null;
    }

    public int getFitMode(String mode) {
        int fitMode = FIT_ALL;
        if (mode.startsWith("amp")) {
            fitMode = FIT_AMPLITUDES;
        } else if (mode.startsWith("lwamp")) {
            fitMode = FIT_LW_AMPLITUDES;
        } else if (mode.startsWith("rms")) {
            fitMode = FIT_RMS;
        } else if (mode.startsWith("maxdev")) {
            fitMode = FIT_MAX_DEV;
        }
        return fitMode;
    }

    public List<PeakDim> getLinkedSelectedPeakDims(List<PeakDim> peakDims) {
        Set<PeakDim> linkedDims = new HashSet<>();
        peakDims.forEach((peakDim) -> {
            linkedDims.addAll(peakDim.getCoupledPeakDims());
        });
        List<PeakDim> linkedPeakDims = new ArrayList<>();
        linkedPeakDims.addAll(linkedDims);
        return linkedPeakDims;
    }

    public double fitPeak(Peak peak, String mode, String fitMode) {
        double value = 0.0;
        try {
            List<PeakDim> peakDims = new ArrayList<>();
            peakDims.add(peak.peakDim[0]);
            double[] peakBounds = Multiplets.getBoundsOfPeakDims(peakDims, 2.0, 16.0);
            value = fitPeakDims(peakDims, mode, peakBounds, fitMode);
        } catch (PeakFitException | IOException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    public double fitLinkedPeak(Peak peak, boolean doFit) {
        double value = 0.0;
        String mode = "fit";

        String fitMode = doFit ? "all" : "rms";
        try {
            List<PeakDim> peakDims = new ArrayList<>();
            peakDims.add(peak.peakDim[0]);
            double[] bounds = Analyzer.getRegionBounds(dataset.getRegions(), 0, peak.peakDim[0].getChemShift());
            System.out.println(peak.peakDim[0].getChemShift() + " " + bounds);
            value = fitPeakDims(peakDims, mode, bounds, fitMode);
        } catch (PeakFitException | IOException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    public List<Peak> fitLinkedPeaks(PeakList peakList, boolean doFit) {
        List<Peak> fitRoots = new ArrayList<>();

        List<Peak> peaks = Multiplets.getLinkRoots(peakList);
        for (Peak peak : peaks) {
            fitLinkedPeak(peak, doFit);
            if (anyFit) {
                fitRoots.add(peak);
            }
        }
        return fitRoots;
    }

    public List<Peak> jfitLinkedPeaks(PeakList peakList, boolean doFit) {
        List<Peak> fitRoots = new ArrayList<>();
        List<Peak> peaks = Multiplets.getLinkRoots(peakList);
        for (Peak peak : peaks) {
            jfitLinkedPeak(peak, doFit);
            if (anyFit) {
                fitRoots.add(peak);
            }
        }
        return fitRoots;
    }

    public double jfitLinkedPeak(Peak peak, boolean doFit) {
        double value = 0.0;
        String mode = "jfit";
        Multiplet multiplet = peak.getPeakDim(0).getMultiplet();
        double regionShift = peak.peakDim[0].getChemShift();
        if (multiplet != null) {
            regionShift = multiplet.measureCenter();
        }

        String fitMode = doFit ? "all" : "rms";
        try {
            List<PeakDim> peakDims = new ArrayList<>();
            peakDims.add(peak.peakDim[0]);
            double[] bounds = Analyzer.getRegionBounds(dataset.getRegions(), 0, regionShift);
            if ((bounds == null) && (multiplet != null)) {
                bounds = Multiplets.getBoundsOfPeakDims(multiplet.getPeakDims(), 1.5, regionShift);
            }
            value = fitPeakDims(peakDims, mode, bounds, fitMode);
        } catch (PeakFitException | IOException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    public double fitPeakDims(List<PeakDim> peakDims, String mode, double[] winRegions, String fitModeString) throws IllegalArgumentException, PeakFitException, IOException {
        if (peakDims.isEmpty()) {
            success = false;
            return 0.0;
        }
        success = true;
        anyFit = false;
        int fitMode = getFitMode(fitModeString);
        PeakDim firstPeak = peakDims.get(0);
        PeakList firstList = firstPeak.myPeak.peakList;
        List<PeakDim> linkedPeakDims = getLinkedSelectedPeakDims(peakDims);
        double[] peakBounds = Multiplets.getBoundsOfPeakDims(linkedPeakDims, 2.0, 16.0);
        Set<Long> resonances = new HashSet<>();
        List<Peak> allPeaks = new ArrayList<>();
        boolean allFit = true;
        for (PeakDim peakDim : linkedPeakDims) {
            if (!peakDim.myPeak.getFlag(4)) {
                allFit = false;
            }
            allPeaks.add(peakDim.myPeak);
            Resonance resonance = peakDim.getResonance();
            resonances.add(resonance.getID());
        }
        List<PeakDim> rootDims = new ArrayList<>();
        ResonanceFactory resFactory = PeakDim.resFactory;
        for (Long resID : resonances) {
            SimpleResonance atomResonance = (SimpleResonance) resFactory.get(resID);
            List<PeakDim> peakDims2 = atomResonance.getPeakDims();
            for (PeakDim peakDim : peakDims2) {
                if (peakDim.myPeak.peakList == firstList) {
                    rootDims.add(peakDim);
                    break;
                }
            }
        }
        List<Double> ppmRegions = new ArrayList<>();
        List<Peak> complexPeaks = new ArrayList<>();
        rootDims.forEach((peakDim) -> {
            double[] bound = null;
            if (winRegions != null) {
                bound = getBounds(winRegions, peakDim.getChemShift());
            } else {
                bound = getBounds(peakBounds, peakDim.getChemShift());
            }
            if (bound != null) {
                ppmRegions.add(bound[0]);
                ppmRegions.add(bound[1]);
            }
            complexPeaks.add(peakDim.myPeak);
        });
        int i1 = 0;
        int i2 = dataset.getSize(0) - 1;
        if (!ppmRegions.isEmpty()) {
            ppmRegions.sort(null);
            double rMin = ppmRegions.get(0);
            double rMax = ppmRegions.get(ppmRegions.size() - 1);
            if (peakBounds[0] < rMin) {
                peakBounds[0] = rMin;
            }
            if (peakBounds[1] > rMax) {
                peakBounds[1] = rMax;
            }
            i1 = dataset.ppmToPoint(0, rMax);
            i2 = dataset.ppmToPoint(0, rMin);
        }

        PeakFitter peakFitter = new PeakFitter(dataset, false, fitMode);
        int[] rows = new int[dataset.getNDim()];
        if (complexPeaks.size() > 32) {
            mode = "lfit";
        }
        double value = 0.0;
        if ((fitMode == FIT_RMS) || (fitMode == FIT_MAX_DEV)) {
            if (mode.equals("jfit")) {
                peakFitter.setup(complexPeaks);
                value = peakFitter.doJFit(i1, i2, rows, true);

            } else {
                peakFitter.setup(allPeaks);
                value = peakFitter.doFit(i1, i2, rows, false, false);
            }
        } else {
            if (!allFit) {
                anyFit = true;
                peakFitter.setup(allPeaks);
                value = peakFitter.doFit(i1, i2, rows, true, true);
                if (!mode.equals("lfit")) {
                    if (mode.equals("jfit")) {
                        peakFitter.setup(complexPeaks);
                    }

                    try {
                        int nTries = 10;
                        double previous = 0.0;
                        for (int iTry = 0; iTry < nTries; iTry++) {
                            if (mode.equals("jfit")) {
                                value = peakFitter.doJFit(i1, i2, rows, true);
                            } else {
                                value = peakFitter.doFit(i1, i2, rows, true, false);

                            }

                            if (iTry > 0) {
                                if (previous == 0.0) {
                                    break;
                                }
                                double delta = (Math.abs(value - previous) / previous);
//                                System.out.printf("%d %10.6f %10.6f %10.6f\n", iTry, previous, value, delta);
                                if (delta < 1.0e-3) {
                                    break;
                                }
                            }
                            previous = value;

                        }
                    } catch (IllegalArgumentException iaE) {
                        success = false;
                    }
                }
            }
        }
        rmsValue = value;
//        System.out.println("fit to " + value);
        return value;
    }
}
