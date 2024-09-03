package org.nmrfx.analyst.peaks;
import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class PeakClusterer {
    MixtureMultivariateNormalDistribution MVN;
    double[] weights;
    double[][] means;
    double[][][] covariances;
    int nDim = 3;
    public PeakClusterer() {
        loadComponents();
        MVN = new MixtureMultivariateNormalDistribution(weights,means,covariances);
    }

    void loadComponents() {
        InputStream iStream = this.getClass().getResourceAsStream("/data/predict/protein/peakClusters.txt");
        ArrayList<String []> lines = new ArrayList();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String[] fields = line.split("\t");
                lines.add(fields);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        int nLines = lines.size();
        weights = new double[nLines];
        means = new double[nLines][nDim];
        covariances = new double[nLines][nDim][nDim];
        int i = 0; //line number
        for(String[] line : lines) {
                int indexer = 2; //skip first two columns which are identifiers
                weights[i] = Double.parseDouble(line[indexer++]);
                for (int j = 0; j < nDim; j++) {
                    means[i][j] = Double.parseDouble(line[indexer++]);
                }
                for (int k = 0; k < nDim; k++) {
                    for(int l = 0; l < nDim; l++) {
                        covariances[i][k][l] = Double.parseDouble(line[indexer++]);
                    }
                }
                i++;
            }
    }

    public Double getDensity(double[] peaks) {
        return MVN.density(peaks);
    }

    public void checkPeakDensities(PeakList peakList) {
        for(Peak peak : peakList.peaks()) {
            double[] shifts = new double[nDim];
            for (PeakDim dim : peak.peakDims) {
                shifts[dim.getSpectralDim()] = dim.getChemShiftValue();
            }
        }
    }


}