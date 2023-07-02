/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.processing;

import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class LineShapeCatalog {
    private static final Logger log = LoggerFactory.getLogger(LineShapeCatalog.class);
    int[] dimSizes;
    double[] sw;
    double[][] lineWidths;
    double[][] alineWidths;
    int[] offsets;
    int[] nKeep;
    String saveFileName = null;
    List<Vec>[] simVectors = null;
    double[][][] data = null;
    int nFrac = 4;

    public LineShapeCatalog(int nDim) {
        lineWidths = new double[nDim][];
        alineWidths = new double[nDim][];
        data = new double[nDim][][];
        offsets = new int[nDim];

    }

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                if (i != 0) {
                    sBuilder.append(" ");
                }
                sBuilder.append(i).append(" ").append(data[i].length);
                sBuilder.append(" ").append(data[i][0].length);
            }
        }
        return sBuilder.toString();
    }

    public LineShapeCatalog(NMRData data, double[][] lineWidthRanges, int[] nDecay, int[] nKeep, int nFrac, String saveFileName) {
        int nDim = data.getNDim();
        sw = new double[nDim];
        dimSizes = new int[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            sw[iDim] = data.getSW(iDim);
            dimSizes[iDim] = data.getSize(iDim);
        }
        this.lineWidths = new double[nDim][];
        this.nKeep = nKeep;
        this.nFrac = nFrac;
        this.saveFileName = saveFileName;
        simVectors = new ArrayList[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            lineWidths[iDim] = new double[nDecay[iDim]];
            for (int iDecay = 0; iDecay < nDecay[iDim]; iDecay++) {
                double delta = lineWidthRanges[iDim][1] - lineWidthRanges[iDim][0];
                double frac = (double) iDecay / (nDecay[iDim] - 1.0);
                lineWidths[iDim][iDecay] = lineWidthRanges[iDim][0] + frac * delta;
            }
        }
    }

    void decayFID(Vec vec, double lw, double frac) {
        int n = vec.getSize();
        double dwell = vec.getDwellTime();
        double sw = 1.0 / dwell;
        double fOffset = frac * sw / vec.getSize();
        vec.genSignalHz(fOffset, lw, 1.0, 0.0);
        vec.set(0, vec.getReal(0) * 0.5, vec.getImag(0) * 0.5);
    }

    List<Vec> getSimVectors(int iDim) {
        int size = dimSizes[iDim];
        simVectors[iDim] = new ArrayList<>();
        double f = 0.0;
        double a = 1.0;
        double ph = 0.0;
        for (int i = 0; i < lineWidths[iDim].length; i++) {
            for (int j = 0; j < nFrac; j++) {
                double frac = 0.5 * j / nFrac;
                Vec vec = new Vec(size, true);
                vec.setFreqDomain(false);
                vec.setSW(sw[iDim]);
                decayFID(vec, lineWidths[iDim][i], frac);
                simVectors[iDim].add(vec);
            }
        }
        return simVectors[iDim];

    }

    void normalize(int iDim) {
        for (int iFreq = 0; iFreq < data[iDim].length; iFreq++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < data[iDim][iFreq].length; i++) {
                if (data[iDim][iFreq][i] > max) {
                    max = data[iDim][iFreq][i];
                }
            }
            for (int i = 0; i < data[iDim][iFreq].length; i++) {
                data[iDim][iFreq][i] /= max;
            }
        }

    }

    public double getWidth(Vec vec, int iDim) {
        double h1 = 0.0;
        double h2 = 0.0;
        double height = vec.maxIndex().getValue();
        int mid = vec.maxIndex().getIndex();
        double halfHeight = height / 2.0;
        for (int j = mid; j >= 0; j--) {
            if (vec.getReal(j) < halfHeight) {
                double v1 = vec.getReal(j);
                double v2 = vec.getReal(j + 1);
                double f = (halfHeight - v1) / (v2 - v1);
                h1 = j + f;
                break;
            }
        }
        for (int j = mid; j < vec.getSize(); j++) {
            if (vec.getReal(j) < halfHeight) {
                double v1 = vec.getReal(j);
                double v2 = vec.getReal(j - 1);
                double f = (halfHeight - v1) / (v2 - v1);
                h2 = j - f;
                break;
            }
        }
        double width = Math.abs(h2 - h1);
        return width;

    }

    public static LineShapeCatalog loadSimFids(String saveFileName, int nDim) throws IOException {
        LineShapeCatalog simVecProcessor = null;
        if ((saveFileName != null) && (saveFileName.length() > 0)) {
            File saveFile = new File(saveFileName);
            try (BufferedReader reader = Files.newBufferedReader(saveFile.toPath())) {
                simVecProcessor = new LineShapeCatalog(nDim);
                String line = reader.readLine();
                String[] fields = line.split("\t");
                simVecProcessor.nFrac = Integer.parseInt(fields[1]);

                for (int iDim = 0; iDim < nDim; iDim++) {
                    line = reader.readLine();
                    fields = line.split("\t");
                    int size = Integer.parseInt(fields[3]);
                    simVecProcessor.loadSimFids(reader, iDim, nDim, size);
                    simVecProcessor.normalize(iDim);
                }
            }
        }
        return simVecProcessor;
    }

    public void loadSimFids(BufferedReader reader, int iDim, int nDim, int size) throws IOException {
        int j = 0;

        int iLine = 0;
        while (true) {
            String line = reader.readLine();
            String[] fields = line.split("\t");
            switch (iLine) {
                case 0:
                    lineWidths[iDim] = new double[fields.length / nFrac];
                    data[iDim] = new double[fields.length][size];
                    int k = 0;
                    for (int i = 0; i < fields.length; i += nFrac) {
                        lineWidths[iDim][k++] = Double.parseDouble(fields[i]);
                    }
                    break;
                case 1:
                    alineWidths[iDim] = new double[fields.length / nFrac];
                    int ka = 0;
                    for (int i = 0; i < fields.length; i += nFrac) {
                        alineWidths[iDim][ka++] = Double.parseDouble(fields[i]);
                    }
                    break;
                case 2:
                    break;
                default:
                    for (int i = 0; i < fields.length; i++) {
                        data[iDim][i][j] = Double.parseDouble(fields[i]);
                    }
                    j++;
                    break;
            }
            if (j >= size) {
                break;
            }
            iLine++;
        }
        offsets[iDim] = data[iDim][0].length / 2;
    }

    public void saveSimFids() {
        if ((saveFileName != null) && (saveFileName.length() > 0)) {
            File saveFile = new File(saveFileName);
            try {
                PrintWriter printWriter = new PrintWriter(saveFile);
                printWriter.printf("nfrac\t%d\tndim\t%d\n", nFrac, simVectors.length);
                for (int i = 0; i < dimSizes.length; i++) {
                    saveSimFids(printWriter, i);
                }
                printWriter.close();
            } catch (FileNotFoundException ex) {
                log.warn("Unable to save sim FIDs", ex);
            }
        }
    }

    void saveSimFids(PrintWriter printWriter, int iDim) throws FileNotFoundException {
        printWriter.printf("dim\t%s\tsize\t%d\n", iDim, nKeep[iDim]);
        int n = simVectors[iDim].get(0).getSize();
        for (int j = 0; j < lineWidths[iDim].length; j++) {
            for (int k = 0; k < nFrac; k++) {
                if ((j > 0) || (k > 0)) {
                    printWriter.print("\t");
                }
                printWriter.printf("%8.2f", lineWidths[iDim][j]);
            }
        }
        printWriter.println();
        for (int k = 0; k < simVectors[iDim].size(); k++) {
            if (k > 0) {
                printWriter.print("\t");
            }
            double widthHz = getWidth(simVectors[iDim].get(k), iDim);
            printWriter.printf("%8.2f", widthHz);
        }
        printWriter.println();
        for (int j = 0; j < lineWidths[iDim].length; j++) {
            for (int k = 0; k < nFrac; k++) {
                if ((j > 0) || (k > 0)) {
                    printWriter.print("\t");
                }
                double frac = 0.5 * k / nFrac;

                printWriter.printf("%8.2f", frac);
            }
        }
        printWriter.println();
        for (int i = 0; i < nKeep[iDim]; i++) {
            int start = n / 2 - nKeep[iDim] / 2;
            for (int j = 0; j < simVectors[iDim].size(); j++) {
                if (j > 0) {
                    printWriter.print("\t");
                }
                printWriter.printf("%8.6f", simVectors[iDim].get(j).getReal(start + i));
            }
            printWriter.println();
        }
    }

    public double[] getMatrix(int[] indices) {
        int size = 1;
        int[] sizes = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            sizes[i] = data[i][indices[i]].length;
            size *= sizes[i];
        }
        double[] matrix = new double[size];
        MultidimensionalCounter mCounter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iter = mCounter.iterator();
        int i = 0;
        while (iter.hasNext()) {
            int[] pt = iter.getCounts();
            double value = 1.0;
            for (int j = 0; j < indices.length; j++) {
                value *= data[j][indices[j]][pt[j]];

            }
            matrix[i++] = value;
        }
        return matrix;
    }

    public int getIndexForWidth(int iDim, double lw) {
        double deltaMin = Double.MAX_VALUE;
        int iMin = 0;
        for (int i = 0; i < alineWidths[iDim].length; i++) {
            double delta = Math.abs(lw - alineWidths[iDim][i]);
            if (delta < deltaMin) {
                deltaMin = delta;
                iMin = i;
            }
        }
        return iMin;
    }

    public double getInterpolatedIndexForWidth(int iDim, double lw) {
        double[] lws = alineWidths[iDim];
        int n = lws.length;
        int iUpper = n;
        for (int i = 0; i < n; i++) {
            if (lws[i] > lw) {
                iUpper = i;
                break;
            }
        }
        double dIndex;
        if (iUpper == 0) {
            dIndex = 0.0;
        } else if (iUpper == n) {
            dIndex = n - 1.0 - 0.00001; // make a little smaller so floor
            // gets previous index
        } else {
            double frac = (lw - lws[iUpper - 1]) / (lws[iUpper] - lws[iUpper - 1]);
            dIndex = (iUpper - 1) + frac;
        }
        return dIndex;
    }

    public void addToDataset(Dataset dataset, PeakList peakList, double scale) throws IOException {
        for (Peak peak : peakList.peaks()) {
            addToDatasetInterpolated(dataset, peak, scale, null);
        }
    }

    public void addToDataset(Dataset dataset,
                             Peak peak, double scale) throws IOException {
        int[] center = new int[dataset.getNDim()];
        int[] indices = new int[center.length];
        for (int i = 0; i < center.length; i++) {
            double ptD = dataset.ppmToDPoint(i, peak.getPeakDim(i).getChemShiftValue());
            center[i] = (int) Math.round(ptD);
            double frac = ptD - center[i];
            int offset = (int) Math.round(frac * nFrac);
            if (offset < 0) {
                center[i] -= 1;
                offset = nFrac + offset;
            }
            double lw = peak.getPeakDim(i).getLineWidthHz();
            double lwPt = dataset.hzWidthToPoints(i, lw);
            int index = getIndexForWidth(i, lwPt);
            indices[i] = nFrac * index + offset;
            if (indices[i] < 0) {
                indices[i] = 0;
            }

        }
        addToDataset(dataset, indices, center, scale * peak.getIntensity());
    }

    public boolean addToDatasetInterpolated(Dataset dataset,
                                            Peak peak, double scale, Double lvl) throws IOException {
        int[] center = new int[dataset.getNDim()];
        double[][] values = new double[center.length][];
        for (int i = 0; i < center.length; i++) {
            double lw = peak.getPeakDim(i).getLineWidthHz();
            double lwPt = dataset.hzWidthToPoints(i, lw);
            double ptD = dataset.ppmToDPoint(i, peak.getPeakDim(i).getChemShiftValue());
            values[i] = interpolate(i, ptD, lwPt);
            center[i] = (int) Math.round(ptD);
        }
        return addToDatasetInterpolated(dataset, values, center,
                scale * peak.getIntensity(), lvl);
    }

    public double[] interpolate(int iDim, double ptD, double lw) {
        double lwIndex = getInterpolatedIndexForWidth(iDim, lw);
        int ctr = (int) Math.round(ptD);
        double frac = ptD - ctr;
        boolean reverse = false;
        if (frac < 0.0) {
            reverse = true;
        }
        frac = Math.abs(frac);
        int offset = (int) Math.floor(frac * nFrac);
        double frac2 = nFrac * frac - offset;
        return interpolate(iDim, lwIndex, offset, frac2, reverse);

    }

    private double[] interpolate(int iDim, double lwIndex, int offset,
                                 double fP, boolean reverse) {
        int lwIndex1 = (int) Math.floor(lwIndex) * nFrac;
        int lwIndex2 = lwIndex1 + nFrac;
        double fL = lwIndex - Math.floor(lwIndex);
        int index1 = lwIndex1 + offset;
        int index2 = lwIndex2 + offset;
        int index3 = lwIndex1 + 1;
        int index4 = lwIndex2 + 1;
        int n = data[iDim][index1].length;
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            int j = reverse ? n - i : i;
            if (j >= n) {
                j = 0;
            }
            double v1 = data[iDim][index1][j];
            double v2 = data[iDim][index2][j];
            double v3 = data[iDim][index3][j];
            double v4 = data[iDim][index4][j];
            double v5 = (1.0 - fL) * v1 + fL * v2;
            double v6 = (1.0 - fL) * v3 + fL * v4;
            v[i] = (1.0 - fP) * v5 + fP * v6;
        }
        return v;
    }

    public boolean addToDatasetInterpolated(Dataset dataset, double[][] values,
                                            int[] center, double scale, Double lvl) throws IOException {
        int[] regionSizes = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            regionSizes[i] = values[i].length;
        }
        MultidimensionalCounter mCounter = new MultidimensionalCounter(regionSizes);
        MultidimensionalCounter.Iterator iter = mCounter.iterator();
        int i = 0;
        int[] dpt = new int[values.length];
        int[] sizes = dataset.getSizes();
        while (iter.hasNext()) {
            int k = iter.next();
            int[] pt = iter.getCounts();
            boolean ok = true;
            for (int j = 0; j < pt.length; j++) {
                dpt[j] = pt[j] + center[j] - offsets[j];
                if ((dpt[j] < 0) || (dpt[j] >= sizes[j])) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                double dataValue = dataset.readPointRaw(dpt);
                double value = scale;
                for (int j = 0; j < values.length; j++) {
                    value *= values[j][pt[j]];
                }
                dataValue += value;
                if (lvl != null) {
                    if (dataValue > lvl) {
                        return true;
                    }

                } else {
                    dataset.writePoint(dpt, dataValue);
                }
            }
        }
        return false;
    }

    public void addToDataset(Dataset dataset, int[] indices,
                             int[] center, double scale) throws IOException {
        int[] regionSizes = new int[indices.length];

        for (int i = 0; i < indices.length; i++) {
            regionSizes[i] = data[i][indices[i]].length;
        }
        MultidimensionalCounter mCounter = new MultidimensionalCounter(regionSizes);
        MultidimensionalCounter.Iterator iter = mCounter.iterator();
        int i = 0;
        int[] dpt = new int[indices.length];
        int[] sizes = dataset.getSizes();
        while (iter.hasNext()) {
            int k = iter.next();
            int[] pt = iter.getCounts();
            boolean ok = true;
            for (int j = 0; j < pt.length; j++) {
                dpt[j] = pt[j] + center[j] - offsets[j];
                if ((dpt[j] < 0) || (dpt[j] >= sizes[j])) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                double dataValue = dataset.readPointRaw(dpt);
                double value = scale;
                for (int j = 0; j < indices.length; j++) {
                    value *= data[j][indices[j]][pt[j]];
                }
                dataValue += value;
                dataset.writePoint(dpt, dataValue);
            }
        }
    }
}
