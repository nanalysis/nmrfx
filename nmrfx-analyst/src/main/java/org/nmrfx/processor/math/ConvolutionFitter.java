package org.nmrfx.processor.math;

import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;

import java.util.*;
import java.io.IOException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class ConvolutionFitter {

    public record CPeak(int[] position, double height) {
    }

    public record CPeakD(double[] position, double height) {
        double distance(CPeakD cPeak, double[] widths) {
            double sum = 0.0;
            for (int i = 0; i < position.length; i++) {
                double d = (position[i] - cPeak.position[i]) / widths[i];
                sum += d * d;
            }
            return Math.sqrt(sum);
        }

        CPeakD merge(CPeakD cPeakD) {
            double[] averagePosition = new double[position.length];
            double sum = height + cPeakD.height;
            for (int i = 0; i < position.length; i++) {
                averagePosition[i] = (height * position[i] + cPeakD.height * cPeakD.position[i]) / sum;
            }
            return new CPeakD(averagePosition, sum);
        }

    }


    private static int nextPowerOfTwo(int n) {
        int pow = 1;
        while (pow < n) pow *= 2;
        return pow;
    }

    private List<DatasetRegion> getRegion(Dataset dataset, int nPeakDim) {
        List<DatasetRegion> regions = new ArrayList<>();
        double[] x = new double[nPeakDim * 2];
        DatasetRegion region = new DatasetRegion(x);
        for (int iDim = 0; iDim < nPeakDim; iDim++) {
            double ppm1 = dataset.pointToPPM(iDim, 0);
            double ppm2 = dataset.pointToPPM(iDim, dataset.getSizeReal(iDim) - 1.0);
            region.setRegionStart(iDim, ppm1);
            region.setRegionEnd(iDim, ppm2);
        }
        regions.add(region);
        return regions;
    }

    private List<DatasetRegion> getBlockRegions(Dataset dataset, int[][] pt, int nPeakDim, double threshold, int[] psfDim) throws IOException {
        List<DatasetRegion> regions = new ArrayList<>();
        int[] nWins = new int[nPeakDim];
        int[] winSizes = new int[nPeakDim];
        for (int iDim = 0; iDim < nPeakDim; iDim++) {
            int size = pt[iDim][1] - pt[iDim][0] + 1;
            int psfSize = psfDim[iDim];
            int winSize = (psfSize - 1) * 16 - (psfSize - 1);
            winSize = nextPowerOfTwo(winSize) - psfSize + 1;
            if (winSize > 256) {
                winSize = 256;
            }
            winSizes[iDim] = winSize;
            nWins[iDim] = (int) Math.ceil((double) size / winSizes[iDim]);
        }
        MultidimensionalCounter winCounter = new MultidimensionalCounter(nWins);
        var iterator = winCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            double[] x = new double[nPeakDim * 2];
            int[] counts = iterator.getCounts();
            boolean ok = true;
            for (int iDim = 0; iDim < nPeakDim; iDim++) {
                int extra = (psfDim[iDim] - 1) / 2;
                int start = counts[iDim] * winSizes[iDim] - extra + pt[iDim][0];
                int end = start + winSizes[iDim] - 1 + 2 * extra;
                end = Math.min(end, pt[iDim][1] + extra);
                int size = end - start + 1;
                if (size > 0) {
                    double ppm1 = dataset.pointToPPM(iDim, start);
                    double ppm2 = dataset.pointToPPM(iDim, end);
                    x[iDim * 2] = ppm1;
                    x[iDim * 2 + 1] = ppm2;
                } else {
                    ok = false;
                }

            }
            if (ok) {
                DatasetRegion datasetRegion = new DatasetRegion(x);
                RegionData rData = dataset.analyzeRegion(datasetRegion);
                if (rData.getMax() > threshold) {
                    regions.add(datasetRegion);
                }
            }
        }
        return regions;
    }

    private void doConvolutions(Dataset dataset, List<DatasetRegion> regions, IterativeConvolutions iterativeConvolutions, List<CPeakD> allPeaks) {
        regions.parallelStream().forEach(region -> {
            int nDim = dataset.getNDim();
            int[][] pt = new int[nDim][2];
            int[] dim = new int[nDim];
            int[] sizes = new int[nDim];
            for (int iDim = 0; iDim < nDim; iDim++) {
                int pt1 = (int) Math.round(dataset.ppmToDPoint(iDim, region.getRegionStart(iDim)));
                int pt2 = (int) Math.round(dataset.ppmToDPoint(iDim, region.getRegionEnd(iDim)));
                if (pt1 > pt2) {
                    int hold = pt1;
                    pt1 = pt2;
                    pt2 = hold;
                }
                pt[iDim][0] = pt1;
                pt[iDim][1] = pt2;
                sizes[iDim] = pt2 - pt1 + 1;
                dim[iDim] = iDim;
            }
            MatrixND signalMatrix = new MatrixND(sizes);
            try {
                dataset.readMatrixND(pt, dim, signalMatrix, true);
                BooleanMatrixND skip = new BooleanMatrixND(sizes);
                List<CPeakD> peaks = new ArrayList<>();
                var unused = iterativeConvolutions.iterativeConvolutions(signalMatrix, skip, pt, true, peaks);
                allPeaks.addAll(peaks);
            } catch (IOException ioE) {
            }
        });

    }

    public void iterativeConvolutions(IterativeConvolutions iterativeConvolutions, Dataset dataset, int[][] pt,
                                      PeakList peakList, Consumer<PeakList> consumer) throws IOException {
        List<DatasetRegion> currentRegions = dataset.getReadOnlyRegions();
        List<DatasetRegion> regions = new ArrayList<>();
        if (currentRegions.isEmpty()) {
            regions.addAll(getBlockRegions(dataset, pt, peakList.getNDim(), iterativeConvolutions.threshold, iterativeConvolutions.psfDim));
        } else {
            regions.addAll(currentRegions);
        }

        List<CPeakD> allPeaks = new ArrayList<>();

        try (ForkJoinPool customThreadPool = new ForkJoinPool(4)) {
            customThreadPool.submit(() -> {
                doConvolutions(dataset, regions, iterativeConvolutions, allPeaks);
            }).join();
        }
        processPeaks(iterativeConvolutions, dataset, peakList, allPeaks);
        if (consumer != null) {
            consumer.accept(peakList);
        }
    }


    void processPeaks(IterativeConvolutions iterativeConvolutions, Dataset dataset, PeakList peakList, List<CPeakD> allPeaks) {
        peakDistances(allPeaks, iterativeConvolutions.squash, iterativeConvolutions.widths);
        allPeaks.stream().filter(Objects::nonNull).forEach(cPeakD -> buildPeak(dataset, peakList, cPeakD,
                iterativeConvolutions.threshold, iterativeConvolutions.psfMax, iterativeConvolutions.widths));
    }

    void peakDistances(List<CPeakD> allPeaks, double squash, double[] widths) {
        int nPeaks = allPeaks.size();
        for (int i = 0; i < nPeaks; i++) {
            CPeakD cPeak1 = allPeaks.get(i);
            if (cPeak1 == null) {
                continue;
            }
            for (int j = i + 1; j < nPeaks; j++) {
                CPeakD cPeak2 = allPeaks.get(j);
                if (cPeak2 != null) {
                    double distance = cPeak1.distance(cPeak2, widths);
                    if (distance < squash) {
                        allPeaks.set(j, null);
                        cPeak1 = cPeak1.merge(cPeak2);
                        allPeaks.set(i, cPeak1);
                    }
                }
            }
        }
    }


    void buildPeak(Dataset dataset, PeakList peakList, CPeakD cPeakD, double threshold, double max, double[] widths) {
        if (Double.isFinite(cPeakD.height)) {
            double intensity = cPeakD.height * max;
            if (Math.abs(intensity) < threshold) {
                return;
            }
            Peak peak = peakList.getNewPeak();
            double dxProduct = 1.0;
            for (int i = 0; i < cPeakD.position.length; i++) {
                PeakDim peakDim = peak.getPeakDim(i);
                double x1 = dataset.pointToPPM(i, cPeakD.position[i] - widths[i] / 2.0);
                double x2 = dataset.pointToPPM(i, cPeakD.position[i] + widths[i] / 2.0);
                double dx = Math.abs(x2 - x1);
                double shift = dataset.pointToPPM(i, cPeakD.position[i]);
                peakDim.setChemShiftValue((float) shift);
                peakDim.setLineWidthValue((float) dx);
                peakDim.setBoundsValue((float) (dx * 2.0));
                dxProduct *= dx;
            }
            peak.setIntensity((float) intensity);
            double volume = intensity * dxProduct * (Math.PI / 2.0) / 1.05;
            peak.setVolume1((float) volume);
        }
    }
}
