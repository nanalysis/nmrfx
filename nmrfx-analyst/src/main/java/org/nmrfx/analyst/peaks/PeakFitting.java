package org.nmrfx.analyst.peaks;

import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.Multiplet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakFitException;
import org.nmrfx.processor.datasets.peaks.PeakFitParameters;
import org.nmrfx.processor.datasets.peaks.PeakFitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.FITJ_MODE.JFIT;
import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.FITJ_MODE.LFIT;
import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.FIT_MODE.*;

/**
 * @author Bruce Johnson
 */
public class PeakFitting {

    private static final Logger log = LoggerFactory.getLogger(PeakFitting.class);

    final Dataset dataset;
    boolean success = false;
    double rmsValue = 0.0;
    boolean anyFit = false;
    double bicValue = 0.0;

    public PeakFitting(Dataset dataset) {
        this.dataset = dataset;
    }

    public double getRMS() {
        return rmsValue;
    }

    public double getBIC() {
        return bicValue;
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

    public PeakFitParameters.FIT_MODE getFitMode(String mode) {
        PeakFitParameters.FIT_MODE fitMode = ALL;
        if (mode.startsWith("amp")) {
            fitMode = AMPLITUDES;
        } else if (mode.startsWith("lwamp")) {
            fitMode = LW_AMPLITUDES;
        } else if (mode.startsWith("rms")) {
            fitMode = RMS;
        } else if (mode.startsWith("maxdev")) {
            fitMode = MAXDEV;
        }
        return fitMode;
    }

    public List<PeakDim> getLinkedSelectedPeakDims(List<PeakDim> peakDims) {
        Set<PeakDim> linkedDims = new HashSet<>();
        peakDims.forEach((peakDim) -> {
            // fixme linkedDims.addAll(peakDim.getCoupledPeakDims());
        });
        List<PeakDim> linkedPeakDims = new ArrayList<>();
        linkedPeakDims.addAll(linkedDims);
        return linkedPeakDims;
    }

    public double fitPeak(Peak peak, PeakFitParameters fitParameters) {
        double value = 0.0;
        try {
            List<PeakDim> peakDims = new ArrayList<>();
            peakDims.add(peak.peakDims[0]);
            double[] peakBounds = Multiplets.getBoundsOfPeakDims(peak.getPeakDim(0).getMultiplet().getAbsComponentList(), 2.0, 16.0);
            value = fitPeakDims(peakDims, peakBounds, fitParameters);
        } catch (PeakFitException | IOException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    public double fitLinkedPeak(Peak peak, PeakFitParameters fitParameters) {
        double value = 0.0;

        try {
            List<PeakDim> peakDims = new ArrayList<>();
            peakDims.add(peak.peakDims[0]);
            double[] bounds = Analyzer.getRegionBounds(dataset.getReadOnlyRegions(), 0, peak.peakDims[0].getChemShift());
            value = fitPeakDims(peakDims, bounds, fitParameters);
        } catch (PeakFitException | IOException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    public List<Peak> fitLinkedPeaks(PeakList peakList, PeakFitParameters fitParameters) {
        List<Peak> fitRoots = new ArrayList<>();
        List<Peak> peaks = peakList.peaks();
        for (Peak peak : peaks) {
            fitLinkedPeak(peak, fitParameters);
            if (anyFit) {
                fitRoots.add(peak);
            }
        }
        return fitRoots;
    }

    public List<Peak> jfitLinkedPeaks(PeakList peakList, PeakFitParameters fitParameters) {
        fitParameters.fitMode(ALL);
        List<Peak> fitRoots = new ArrayList<>();
        List<Peak> peaks = peakList.peaks();
        for (Peak peak : peaks) {
            jfitLinkedPeak(peak, fitParameters);
            if (anyFit) {
                fitRoots.add(peak);
            }
        }
        return fitRoots;
    }

    public double jfitLinkedPeak(Peak peak, PeakFitParameters fitParameters) {
        double value = 0.0;
        fitParameters.fitJMode(JFIT);
        Multiplet multiplet = peak.getPeakDim(0).getMultiplet();
        double regionShift = peak.peakDims[0].getChemShift();
        if (multiplet != null) {
            regionShift = multiplet.getCenter();
        }

        try {
            double[] bounds = Analyzer.getRegionBounds(dataset.getReadOnlyRegions(), 0, regionShift);
            if ((bounds == null) && (multiplet != null)) {
                bounds = Multiplets.getBoundsOfPeakDims(multiplet.getAbsComponentList(), 1.5, regionShift);
            }
            if (bounds != null) {
                int[] dims = {0};
                double[][] limits = new double[1][2];
                limits[0][0] = bounds[0];
                limits[0][1] = bounds[1];
                List<Peak> peaks = peak.peakList.locatePeaks(limits, dims);
                List<PeakDim> peakDims = new ArrayList<>();
                for (Peak peakA : peaks) {
                    peakDims.add(peakA.getPeakDim(0));
                }
                value = fitPeakDims(peakDims, bounds, fitParameters);
            } else {
                log.warn("Unable to get bounds to fit peaks.");
            }
        } catch (PeakFitException | IOException | IllegalArgumentException e) {
            log.warn(e.getMessage());
        }
        return value;
    }

    public double jfitRegion(DatasetRegion region, List<PeakDim> peakDims, PeakFitParameters fitParameters, Double positionRestraint) throws Exception {
        boolean fitShape = fitParameters.shapeParameters().fitShape();
        double value = 0.0;
        List<Peak> peaks = new ArrayList<>();
        for (PeakDim peakDim : peakDims) {
            Peak peak = peakDim.getPeak();
            if (!peak.isDeleted()) {
                peak.setFlag(4, false);
                peaks.add(peak);
            }
        }

        int i1 = dataset.ppmToPoint(0, region.getRegionStart(0));
        int i2 = dataset.ppmToPoint(0, region.getRegionEnd(0));
        PeakFitter peakFitter = new PeakFitter(dataset, false, fitParameters);
        peakFitter.setPositionRestraint(positionRestraint);
        int[] rows = new int[dataset.getNDim()];
        peakFitter.setup(peaks);
        int nTries = 1;
        for (int i = 0; i < nTries; i++) {
            value = peakFitter.simpleFit(i1, i2, rows, true, fitShape);
        }
        bicValue = peakFitter.getBIC();

        return value;
    }

    public double fitPeakDims(List<PeakDim> peakDims, double[] winRegions, PeakFitParameters fitParameters) throws IllegalArgumentException, PeakFitException, IOException {
        if (peakDims.isEmpty()) {
            success = false;
            return 0.0;
        }
        boolean fitShape = true;
        success = true;
        anyFit = false;
        double[] peakBounds = Multiplets.getBoundsOfPeakDims(Multiplet.getAllComps(peakDims), 2.0, 16.0);
        List<Peak> allPeaks = new ArrayList<>();
        List<Double> ppmRegions = new ArrayList<>();
        boolean allFit = true;
        for (PeakDim peakDim : peakDims) {
            if (peakDim.getPeak().isDeleted()) {
                continue;
            }
            if (!peakDim.getPeak().getFlag(4)) {
                allFit = false;
            }
            allPeaks.add(peakDim.getPeak());
            double[] bound;
            if (winRegions != null) {
                bound = getBounds(winRegions, peakDim.getChemShift());
            } else {
                bound = getBounds(peakBounds, peakDim.getChemShift());
            }
            if (bound != null) {
                ppmRegions.add(bound[0]);
                ppmRegions.add(bound[1]);
            }
        }
        int i1 = 0;
        int i2 = dataset.getSizeTotal(0) - 1;
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

        int[] rows = new int[dataset.getNDim()];
        if (allPeaks.size() > 32) {
            fitParameters.fitJMode(LFIT);
        }
        PeakFitter peakFitter = new PeakFitter(dataset, false, fitParameters);
        double value = 0.0;
        bicValue = 0.0;
        if ((fitParameters.fitMode() == RMS) || (fitParameters.fitMode() == MAXDEV)) {
            if (fitParameters.fitJMode() == JFIT) {
                peakFitter.setup(allPeaks);
                value = peakFitter.doJFit(i1, i2, rows, true);
                bicValue = peakFitter.getBIC();

            } else {
                peakFitter.setup(allPeaks);
                value = peakFitter.doFit(i1, i2, rows, false, false);
            }
        } else {
            if (!allFit) {
                anyFit = true;
                peakFitter.setup(allPeaks);
                if (fitParameters.fitJMode() == LFIT) {
                    value = peakFitter.doFit(i1, i2, rows, true, true);
                } else {
                    if (fitParameters.fitJMode() == JFIT) {
                        peakFitter.setup(allPeaks);
                        value = peakFitter.doJFit(i1, i2, rows, true);
                    } else {
                        value = peakFitter.doFit(i1, i2, rows, true, true);
                    }

                    try {
                        int nTries = 10;
                        double previous = 0.0;
                        for (int iTry = 0; iTry < nTries; iTry++) {
                            if (fitParameters.fitJMode() == JFIT) {
                                value = peakFitter.doJFit(i1, i2, rows, true);
                            } else {
                                value = peakFitter.doFit(i1, i2, rows, true, false);

                            }

                            if (iTry > 0) {
                                if (previous == 0.0) {
                                    break;
                                }
                                double delta = (Math.abs(value - previous) / previous);
                                if (delta < 1.0e-3) {
                                    break;
                                }
                            }
                            previous = value;

                        }
                        bicValue = peakFitter.getBIC();
                    } catch (IllegalArgumentException iaE) {
                        success = false;
                    }
                }
            }
        }
        rmsValue = value;
        return value;
    }
}
