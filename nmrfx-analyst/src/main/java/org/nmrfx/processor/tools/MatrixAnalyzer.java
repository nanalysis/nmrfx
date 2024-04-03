package org.nmrfx.processor.tools;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.processor.datasets.Dataset;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    RealMatrix A;
    SingularValueDecomposition svd = null;
    double[][] pcValues = null;

    public MatrixAnalyzer() {

    }

    public void setDatasets(List<Dataset> datasets) {
        this.scannerRows = new ArrayList<>();
        for (Dataset dataset : datasets) {
            int nIncr = getNIncr(dataset);
            for (int i = 0; i < nIncr; i++) {
                LigandScannerInfo scannerInfo = new LigandScannerInfo(dataset, i, "", "", 0.0);
                scannerRows.add(scannerInfo);
            }

        }
    }

    public void setup(String[] dimNames, double[][] ppms, int[] deltas) {
        this.dimNames = dimNames;
        this.ppms = ppms;
        this.deltas = deltas;
        getLimits(scannerRows.get(0).getDataset());

    }

    // proc ::nv::spectrum::scanner3d::bucket {datasets ppmx1 ppmy1 ppmx2 ppmy2 dx dy threshold} {
    int getNIncr(Dataset dataset) {
        int nIncr = 1;
        int nDim = dataset.getNDim();
        if (nDim > dimNames.length + 1) {
            throw new IllegalArgumentException("dataset has too many dimensions");
        }
        if (nDim > dimNames.length) {
            for (int i = 0; i < nDim; i++) {
                boolean match = false;
                for (String dimName : dimNames) {
                    if (dataset.getLabel(i).equals(dimName)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    nIncr = dataset.getSizeTotal(i);
                }
            }

        }

        return nIncr;
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
            System.out.println(j + " " + npt + " " + deltas[j] + " " + nElems[j]);
            if (nElems[j] * deltas[j] < npt) {
                nElems[j]++;
            }
            j++;
        }
    }

    public void bucket(double threshold) throws IOException {
        MultidimensionalCounter counter = new MultidimensionalCounter(nElems);
        MultidimensionalCounter.Iterator iter = counter.iterator();
        int[][] bpt = new int[nDim][2];
        int nCols = scannerRows.size();
        List<double[]> rows = new ArrayList<>();
        List<int[]> indices = new ArrayList<>();
        while (iter.hasNext()) {
            int kk = iter.next();
            int[] elems = iter.getCounts();
            for (int k = 0; k < dims.length; k++) {
                bpt[k][0] = pt[k][0] + deltas[k] * elems[k];
                bpt[k][1] = bpt[k][0] + deltas[k];
            }
            double[] width = new double[nDim];
            double max = Double.NEGATIVE_INFINITY;
            double[] values = new double[nCols];
            int iCol = 0;
            for (LigandScannerInfo scannerInfo : scannerRows) {
                Dataset dataset = scannerInfo.getDataset();
                int nIncr = getNIncr(dataset);
                if (nIncr > 1) {
                    bpt[nDim - 1][0] = scannerInfo.getIndex();
                    bpt[nDim - 1][1] = scannerInfo.getIndex();
                }
                RegionData region = dataset.analyzeRegion(bpt, dims, width, dims);
                max = Math.max(max, region.getMax());
                double vol = region.getVolume_r();
                values[iCol++] = vol;

            }
            if (max > threshold) {
                rows.add(values);
                int[] corners = new int[dimNames.length];
                for (int k = 0; k < dims.length; k++) {
                    corners[k] = bpt[k][0];
                }
                indices.add(corners);
            }
        }
        System.out.println("elems " + counter.getSize() + " active " + rows.size() + " cols " + nCols);

        double[][] matrix = new double[rows.size()][nCols];
        int iRow = 0;
        for (double[] rowData : rows) {
            matrix[iRow++] = rowData;
        }
        A = new Array2DRowRealMatrix(matrix);
    }

    public void subtractMean() {
        int nRows = A.getRowDimension();
        for (int iRow = 0; iRow < nRows; iRow++) {
            RealVector vec = A.getRowVector(iRow);
            DescriptiveStatistics dStat = new DescriptiveStatistics(vec.toArray());
            double mean = dStat.getMean();
            vec.mapSubtractToSelf(mean);
            A.setRowVector(iRow, vec);
        }

    }

    public void svd() {
        svd = new SingularValueDecomposition(A);
    }

    public double[][] doPCA(int nPC) {
        subtractMean();
        svd();
        RealMatrix V = svd.getV();
        double[] sVals = svd.getSingularValues();
        double s0 = sVals[0];
        int nCols = A.getColumnDimension();
        pcValues = new double[nPC][nCols];

        for (int iPC = 0; iPC < nPC; iPC++) {
            RealVector vec = V.getColumnVector(iPC);
            double s = sVals[iPC];
            for (int iCol = 0; iCol < nCols; iCol++) {
                double v = vec.getEntry(iCol);
                double pcV = v * s / s0;
                pcValues[iPC][iCol] = pcV;
                System.out.printf("%7.4f ", pcV);
            }
            System.out.println("");
        }
        return pcValues;
    }

    public SingularValueDecomposition getSVD() {
        if (svd == null) {
            svd();
        }
        return svd;
    }

    public double[] getPCADelta(int ref, int nPC) {
        int nRows = pcValues.length;
        int nCols = pcValues[0].length;
        double[] result = new double[nCols];
        for (int i = 0; i < nCols; i++) {
            double sum = 0.0;
            for (int j = 0; j < nRows; j++) {
                double delta = pcValues[j][i] - pcValues[j][ref];
                sum += delta * delta;
            }
            result[i] = Math.sqrt(sum);
        }
        return result;
    }

    public List<LigandScannerInfo> getScannerRows() {
        return scannerRows;
    }

    static String getStringValue(String[] fields, Map<String, Integer> headerMap, String colName, String defaultValue) {
        String value = defaultValue;
        Integer index = headerMap.get(colName);
        if (index != null) {
            value = fields[index];
        }
        return value;
    }

    static Double getDoubleValue(String[] fields, Map<String, Integer> headerMap, String colName, Double defaultValue) {
        Double value = defaultValue;
        Integer index = headerMap.get(colName);
        if (index != null) {
            value = Double.parseDouble(fields[index]);
        }
        return value;
    }

    static Integer getIntegerValue(String[] fields, Map<String, Integer> headerMap, String colName, Integer defaultValue) {
        Integer value = defaultValue;
        Integer index = headerMap.get(colName);
        if (index != null) {
            value = Integer.parseInt(fields[index]);
        }
        return value;
    }

    public void readScannerFile(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        FileSystem fileSystem = path.getFileSystem();
        String dir = path.toAbsolutePath().getParent().toString();
        Map<String, Integer> headerMap = null;
        scannerRows = new ArrayList<>();
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.charAt(0) == '#') {
                    continue;
                }
                String[] data = line.split("\t", -1);
                if (headerMap == null) {
                    headerMap = PeakReader.headerMap(data);
                } else {
                    String datasetName = getStringValue(data, headerMap, "dataset", null);
                    if (datasetName == null) {
                        throw new IllegalArgumentException("No dataset column");
                    }
                    File datasetFile = fileSystem.getPath(dir, datasetName).toFile();
                    datasetName = datasetFile.getName();
                    Dataset dataset = Dataset.getDataset(datasetName);
                    if (dataset == null) {
                        dataset = new Dataset(datasetFile.toString(), datasetName, false, false);
                    }
                    Integer index = getIntegerValue(data, headerMap, "index", null);
                    if (index == null) {
                        throw new IllegalArgumentException("No dataset index");
                    }
                    String groupName = getStringValue(data, headerMap, "group", "");
                    String sampleName = getStringValue(data, headerMap, "sample", "");
                    double conc = getDoubleValue(data, headerMap, "conc", 0.0);
                    LigandScannerInfo scannerInfo = new LigandScannerInfo(dataset, index, groupName, sampleName, conc);
                    scannerRows.add(scannerInfo);
                }
            }
        }
    }
}
