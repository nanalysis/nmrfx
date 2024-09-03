package org.nmrfx.processor.datasets.peaks;
import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class PeakFolder {
    MixtureMultivariateNormalDistribution MVN;
    double[] weights;
    double[][] means;
    double[][][] covariances;
    int nDim = 3;
    public PeakFolder() {
        loadComponents();
        MVN = new MixtureMultivariateNormalDistribution(weights,means,covariances);
    }

    void loadComponents() {
        InputStream iStream = this.getClass().getResourceAsStream("/data/peakClusters.txt");
        ArrayList<String> lines = new ArrayList();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                lines.add(line);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        int nLines = lines.size();
        weights = new double[nLines];
        means = new double[nLines][nDim];
        covariances = new double[nLines][nDim][nDim];
        for (int i = 0; i < nLines; i++) {
            String line = lines.get(i);
            String[] fields = line.split(" ");
            int indexer = 2; //skip first two columns which are identifiers
            weights[i] = Double.parseDouble(fields[indexer++]);
            for (int j = 0; j < nDim; j++) {
                means[i][j] = Double.parseDouble(fields[indexer++]);
            }
            for (int k = 0; k < nDim; k++) {
                for (int l = 0; l < nDim; l++) {
                    covariances[i][k][l] = Double.parseDouble(fields[indexer++]);
                }
            }
        }
    }

    public Double getDensity(double[] peaks) {
        return MVN.density(peaks);
    }

    public void checkPeakDensity(PeakList peakList) {
        for(Peak peak : peakList.peaks()) {
            double[] shifts = new double[nDim];
            for (PeakDim dim : peak.peakDims) {
                int iDim = dim.getSpectralDim();
                shifts[iDim] = dim.getChemShiftValue();
            }
            getDensity(shifts);
        }
    }


}