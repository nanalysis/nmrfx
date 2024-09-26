package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class PeakFolder {
    MixtureMultivariateNormalDistribution MVN;
    List<String> dimLabels = Arrays.asList("H","C");
    HashMap<String, MixtureMultivariateNormalDistribution> mvns = new HashMap<>();
    public PeakFolder() {
        loadComponents();
    }

    public void initialize() {
        MVN = new MixtureMultivariateNormalDistribution(weights, means, covariances);
    }

    void loadComponents() {
        InputStream iStream = this.getClass().getResourceAsStream("/data/peakClusters.txt");
        ArrayList<String> lines = new ArrayList<>();
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
        List<Double> weights = new ArrayList<>();
        List<double[]> means = new ArrayList<>();
        List<double[][]> covariances = new ArrayList<>();
        String currentGroup = null;
        for (int i = 0; i < nLines; i++) {
            String line = lines.get(i);
            String[] fields = line.split(" ");
            int indexer = 1; //skip first column with experiment name
            String[] groups = fields[indexer++].split("-");
            String residueType = groups[1];
            String atomType = groups.length > 2 ? groups[2] : "";
            String groupName = residueType + "." + atomType;
            if (currentGroup.isBlank()) {currentGroup = groupName;}
            if(groupName.equals(currentGroup)) {
                weights.add(Double.parseDouble(fields[indexer++]));
                double[] mean = new double[nDim];
                for (int j = 0; j < nDim; j++) {
                    mean[j] = Double.parseDouble(fields[indexer++]);
                }
                means.add(mean);
                double[][] covarianceMatrix = new double[nDim][nDim];
                for (int k = 0; k < nDim; k++) {
                    for (int l = 0; l < nDim; l++) {
                        covarianceMatrix[k][l] = Double.parseDouble(fields[indexer++]);
                    }
                }
                covariances.add(covarianceMatrix);
            } else {
                double[] w = weights.stream().mapToDouble(Double::doubleValue).toArray();
                MVN = new MixtureMultivariateNormalDistribution(w, means, covariances);
                mvns.put(groupName, MVN);
            }
        }
    }

    public Double getDensity(double[] peaks) {
        return MVN.density(peaks);
    }

    public void unfoldPeakList(PeakList peakList, String[] dimToFold, boolean[] alias) {
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[][] bounds = new double[dimToFold.length][2];
        int[] bondedDims = new int[dimToFold.length];

        if (dataset != null) {
            for (int i = 0; i < dimToFold.length; i++) {
                int iDim = dataset.getDim(dimToFold[i]);
                int size = dataset.getSizeReal(iDim);
                bounds[i][0] = dataset.pointToPPM(iDim, 0);
                bounds[i][1] = dataset.pointToPPM(iDim, size - 1);
                String relationDim = peakList.getSpectralDim(dimToFold[i]).getRelationDim();
                if (relationDim.isBlank()) {
                    if (peakList.getNDim() == 2 &&
                            new HashSet<>(peakList.getSpectralDims().stream()
                                    .map(SpectralDim::getNucleus)
                                    .map(s -> s.substring(s.length() - 1))
                                    .toList()).containsAll(dimLabels)) {
                        String currentDim = peakList.getSpectralDim(dimToFold[i]).getDimName();
                        relationDim = peakList.getSpectralDims().stream()
                                .filter((spectralDim -> !spectralDim.getDimName().equals(currentDim))).toList().get(0).getDimName();
                    } else {
                        throw new NullPointerException();
                    }

                }
                bondedDims[i] = peakList.getSpectralDim(relationDim).getIndex();
            }

            int[] peakListToCluster = new int[peakList.getNDim()]; //map peakList dims to mvn dims
            peakList.getSpectralDims().forEach((peakDim) -> {
                    String nucleus = peakDim.getNucleus();
                    String nucleusName = nucleus.substring(nucleus.length()-1);
                    peakListToCluster[peakDim.getIndex()] = dimLabels.indexOf(nucleusName);
                }
            );

            for (Peak peak : peakList.peaks()) { //set shifts according to dimensions of dimLabels
                double[] shifts = new double[dimLabels.size()];
                for (int i = 0; i < dimToFold.length; i++) {
                    String label = dimToFold[i];
                    int iDim = peakListToCluster[peak.getPeakDim(label).getSpectralDim()];
                    int bondedDim = peakListToCluster[peak.getPeakDim(bondedDims[i]).getSpectralDim()];
                    shifts[iDim] = peak.getPeakDim(label).getChemShiftValue();
                    shifts[bondedDim] = peak.getPeakDim(bondedDims[i]).getChemShiftValue();

                    double density = getDensity(shifts);

                    double shift = shifts[iDim];
                    double lowerLim = bounds[i][0];
                    double upperLim = bounds[i][1];
                    double bestDensity = density;
                    double bestShift = shift;
                    double[] foldedShifts = new double[2]; //two possible positions to test

                    foldedShifts[0] = alias[i] ? upperLim - (lowerLim - shift) : upperLim - (shift - upperLim);
                    foldedShifts[1] = alias[i] ? lowerLim + (shift - upperLim) : lowerLim + (lowerLim - shift);

                        String assignment = peak.getPeakDim(label).getLabel();
                        Atom atom = Molecule.getAtomByName(assignment);
                        if (atom != null) {
                            String residueName = atom.getResidueName();
                            String atomName = atom.getName();
                            }

                    for (double foldedShift : foldedShifts) {
                        shifts[iDim] = foldedShift;
                        double newDensity = getDensity(shifts);
                        if (newDensity > bestDensity) {
                            bestShift = shifts[iDim];
                            bestDensity = newDensity;
                        }
                    }

                    if (bestDensity != density) {
                        peak.getPeakDim(label).setChemShiftValue((float) bestShift);
                    }
                }
            }
        }

    }


}