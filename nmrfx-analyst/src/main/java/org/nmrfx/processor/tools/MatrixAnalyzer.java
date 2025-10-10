package org.nmrfx.processor.tools;

import org.apache.commons.math3.util.MultidimensionalCounter;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.processor.datasets.Dataset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class MatrixAnalyzer {

    List<LigandScannerInfo> scannerRows;
    String[] dimNames;
    int nDim = 1;
    int[] dims;
    int[][] pt;
    int[] nElems;
    double[][] ppms;
    int[] deltas;
    SimpleMatrix dataMatrix;
    double[][] pcValues = null;
    List<int[]> indices = new ArrayList<>();
    boolean centerData = true;
    boolean standardizeData = false;
    boolean transposeData = false;

    public void setScannerRows(List<LigandScannerInfo> scannerRows) {
        this.scannerRows = scannerRows;
    }
    public void setCenterData(boolean mode) {
        centerData = mode;
    }

    public boolean getCenterData() {
        return centerData;
    }

    public void setStandardizeData(boolean mode) {
        standardizeData = mode;
    }

    public boolean getStandardizeData() {
        return standardizeData;
    }

    public void setTransposeData(boolean value) {
        transposeData = value;
    }

    public boolean getTransposeData() {
        return transposeData;
    }


    public void setup(Dataset dataset, String[] dimNames, double[][] ppms, int[] deltas) {
        this.dimNames = dimNames;
        this.ppms = ppms;
        this.deltas = deltas;
        getLimits(dataset);

    }

    private void getLimits(Dataset dataset) {
        nDim = dataset.getNDim();
        dims = new int[nDim];
        pt = new int[nDim][2];
        nElems = new int[dimNames.length];
        int j = 0;
        for (String dimName : dimNames) {
            dims[j] = dataset.getDim(dimName);
            if (dims[j] == -1) {
                throw new IllegalArgumentException("Invalid dimName " + dimName);
            }
            pt[j][0] = dataset.ppmToPoint(dims[j], ppms[j][0]);
            pt[j][1] = dataset.ppmToPoint(dims[j], ppms[j][1]);
            if (pt[j][0] > pt[j][1]) {
                int hold = pt[j][0];
                pt[j][0] = pt[j][1];
                pt[j][1] = hold;
            }
            int npt = pt[j][1] - pt[j][0] + 1;
            nElems[j] = npt / deltas[j];
            if (nElems[j] * deltas[j] < npt) {
                nElems[j]++;
            }
            j++;
        }
        dims[dims.length - 1] = dims.length -1 ;
        dims[1] = 1;
    }

    public void bucket2(double threshold) throws IOException {

        MultidimensionalCounter counter = new MultidimensionalCounter(nElems);
        MultidimensionalCounter.Iterator iter = counter.iterator();
        int[][] bpt = new int[nDim][2];
        List<double[]> columnData = new ArrayList<>();
        indices.clear();
        int nSamples = scannerRows.size();


        while (iter.hasNext()) {
            iter.next();
            int[] elems = iter.getCounts();
            for (int k = 0; k < elems.length; k++) {
                bpt[k][0] = pt[k][0] + deltas[k] * elems[k];
                bpt[k][1] = bpt[k][0] + deltas[k];
            }
            double[] width = new double[nDim];
            double max = Double.NEGATIVE_INFINITY;
            double[] values = new double[nSamples];
            int iRow = 0;
            for (LigandScannerInfo scannerInfo : scannerRows) {
                Dataset dataset = scannerInfo.getDataset();
                if (nDim > dimNames.length) {
                    bpt[nDim - 1][0] = scannerInfo.getIndex();
                    bpt[nDim - 1][1] = scannerInfo.getIndex();
                }
                RegionData region = dataset.analyzeRegion(bpt, dims, width, dims);
                max = Math.max(max, region.getMax());
                double vol = region.getVolume_r();
                values[iRow++] = vol;

            }
            if (max > threshold) {
                columnData.add(values);
                int[] corners = new int[bpt.length];
                for (int k = 0; k < bpt.length; k++) {
                    corners[k] = bpt[k][0];
                }
                indices.add(corners);
            }
        }

        int nFeatures = columnData.size();
        dataMatrix = new SimpleMatrix(nSamples, nFeatures);

        int iCol= 0;
        for (double[] column : columnData) {
            dataMatrix.setColumn(iCol++, 0, column);
        }
    }

    public void setDataMatrix(double[][] data) {
        dataMatrix = new SimpleMatrix(data);
    }

    private static SimpleMatrix centerColumns(SimpleMatrix X) {
        int nRows = X.numRows();
        int nCols = X.numCols();
        SimpleMatrix Xc = new SimpleMatrix(nRows, nCols);

        for (int j = 0; j < nCols; j++) {
            double mean = 0;
            for (int i = 0; i < nRows; i++) {
                mean += X.get(i, j);
            }
            mean /= nRows;
            for (int i = 0; i < nRows; i++) {
                Xc.set(i, j, X.get(i, j) - mean);
            }
        }
        return Xc;
    }
    private static SimpleMatrix centerRows(SimpleMatrix X) {
        int nRows = X.numRows();
        int nCols = X.numCols();
        SimpleMatrix Xc = new SimpleMatrix(nRows, nCols);

        for (int j = 0; j < nRows; j++) {
            double mean = 0;
            for (int i = 0; i < nCols; i++) {
                mean += X.get(j, i);
            }
            mean /= nCols;
            for (int i = 0; i < nCols; i++) {
                Xc.set(j, i, X.get(j, i) - mean);
            }
        }
        return Xc;
    }

    /**
     * Standardizes columns: subtract mean and divide by standard deviation.
     * Returns a new matrix with standardized columns.
     */
    private static SimpleMatrix standardizeColumns(SimpleMatrix X) {
        int nRows = X.numRows();
        int nCols = X.numCols();
        SimpleMatrix Xs = new SimpleMatrix(nRows, nCols);

        for (int j = 0; j < nCols; j++) {
            double mean = 0;
            for (int i = 0; i < nRows; i++) {
                mean += X.get(i, j);
            }
            mean /= nRows;

            // compute standard deviation
            double var = 0;
            for (int i = 0; i < nRows; i++) {
                double diff = X.get(i, j) - mean;
                var += diff * diff;
            }
            double std = Math.sqrt(var / (nRows - 1));

            // avoid divide by zero
            if (std < 1.0e-6) {
                std = 1e-12;
            }

            // standardize column j
            for (int i = 0; i < nRows; i++) {
                double value = (X.get(i, j) - mean) / std;
                Xs.set(i, j, value);
            }
        }
        return Xs;
    }

    /**
     * Standardizes columns: subtract mean and divide by standard deviation.
     * Returns a new matrix with standardized columns.
     */
    private static SimpleMatrix standardizeRows(SimpleMatrix X) {
        int nRows = X.numRows();
        int nCols = X.numCols();
        SimpleMatrix Xs = new SimpleMatrix(nRows, nCols);

        for (int j = 0; j < nRows; j++) {
            double mean = 0;
            for (int i = 0; i < nCols; i++) {
                mean += X.get(j, i);
            }
            mean /= nCols;

            // compute standard deviation
            double variance = 0;
            for (int i = 0; i < nCols; i++) {
                double diff = X.get(j, i) - mean;
                variance += diff * diff;
            }
            double std = Math.sqrt(variance / (nCols - 1));

            // avoid divide by zero
            if (std < 1.0e-6) {
                std = 1e-12;
            }

            // standardize column j
            for (int i = 0; i < nCols; i++) {
                double value = (X.get(j, i) - mean) / std;
                Xs.set(j, i, value);
            }
        }
        return Xs;
    }
    public List<int[]> getIndices() {
        return indices;
    }

    public double[][] doPCA2(int nPC) {
        SimpleMatrix adjustedData;
        boolean transpose = getTransposeData();
        if (transpose) {
            if (centerData && !standardizeData) {
                adjustedData = centerRows(dataMatrix.transpose());
            } else if (standardizeData) {
                adjustedData = standardizeRows(dataMatrix.transpose());
            } else {
                adjustedData = dataMatrix.transpose();
            }
        } else {
            if (centerData && !standardizeData) {
                adjustedData = centerColumns(dataMatrix);
            } else if (standardizeData) {
                adjustedData = standardizeColumns(dataMatrix);
            } else {
                adjustedData = dataMatrix;
            }

        }

        SimpleSVD<SimpleMatrix> svd = adjustedData.svd();

        SimpleMatrix U = svd.getU();
        SimpleMatrix W = svd.getW(); // singular values (diag matrix)
        SimpleMatrix scores;
        SimpleMatrix Vt = svd.getV().transpose();
        if (transpose) {
            scores = W.mult(Vt);
        } else {
            scores = U.mult(W);
        }

        int nSamples = dataMatrix.numRows();
        pcValues = new double[nPC][nSamples];

        int n = Math.min(nPC, scores.numRows());
        for (int iPC = 0; iPC < n; iPC++) {
            for (int iSample = 0; iSample < nSamples; iSample++) {
                pcValues[iPC][iSample] = transpose ? scores.get(iPC, iSample) : scores.get(iSample, iPC);
            }
        }
        return pcValues;
    }

    public double[] getPCADelta(int ref, int nPC) {
        int nRows = Math.min(nPC, pcValues.length);
        int nSamples = pcValues[0].length;
        double[] result = new double[nSamples];
        for (int i = 0; i < nSamples; i++) {
            double sum = 0.0;
            for (int j = 0; j < nRows; j++) {
                double delta = pcValues[j][i] - pcValues[j][ref];
                sum += delta * delta;
            }
            result[i] = Math.sqrt(sum);
        }
        return result;
    }
}
