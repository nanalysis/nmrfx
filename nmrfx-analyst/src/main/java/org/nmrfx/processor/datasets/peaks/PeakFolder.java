package org.nmrfx.processor.datasets.peaks;
import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PeakFolder {
    MixtureMultivariateNormalDistribution MVN;
    double[] weights;
    double[][] means;
    double[][][] covariances;
    List<String> dimLabels = Arrays.asList("H","C");
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
        int nDim = dimLabels.size();
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

    public void unfoldPeakList(PeakList peakList, String[] labels, boolean alias) {
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[][] bounds = new double[labels.length][2];
        if (dataset != null) {
            for (int i = 0; i < labels.length; i++) {
                int size = dataset.getSizeReal(i);
                bounds[i][0] = dataset.pointToPPM(i, 0);
                bounds[i][1] = dataset.pointToPPM(i, size - 1);
            }
            Pattern pattern = Pattern.compile("([CH])");
            for (Peak peak : peakList.peaks()) {
                double[] shifts = new double[peak.getNDim()];
                for (PeakDim peakDim : peak.getPeakDims()) {
                    Matcher matcher = pattern.matcher(peakDim.getDimName());
                    if (matcher.find()) {
                        String group = matcher.group(1);
                        int index = dimLabels.indexOf(group);
                        shifts[index] = peakDim.getChemShiftValue();
                    }
                }
                double density = getDensity(shifts);
                for (int i = 0; i < labels.length; i++) {
                    String label = labels[i];
                    Matcher matcher = pattern.matcher(label);
                    if (matcher.find()) {
                        String group = matcher.group(1);
                        int iDim = dimLabels.indexOf(group);

                    double shift = shifts[iDim];
                    double lowerLim = bounds[i][0];
                    double upperLim = bounds[i][1];
                    if (shift < lowerLim) {
                        shifts[iDim] = alias ? upperLim - (lowerLim - shift) : lowerLim + (lowerLim - shift);
                    } else if (shift > upperLim) {
                        shifts[iDim] = alias ? lowerLim + (shift - upperLim) : upperLim - (shift - upperLim);
                    }
                    double foldDensity = getDensity(shifts);
                    if (foldDensity > density) {
                        peak.getPeakDim(i).setChemShiftValue((float) shifts[peak.getPeakDim(label).getSpectralDim()]);
                        System.out.println(peak.getIndex() + " " + density + " " + foldDensity);
                    }}
                }
            }
        }
    }


}