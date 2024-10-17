package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class PeakFolder {
    List<String> dimLabels = Arrays.asList("H","C");
    HashMap<String, MixtureMultivariateNormalDistribution> MMVNs = new HashMap<>();
    public PeakFolder() {
        loadComponents();
    }

    private void loadComponents() {
        InputStream iStream = this.getClass().getResourceAsStream("/data/HSQC_13C_clusters.txt");
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

        HashMap<String, ArrayList<String[]>> clusters = new HashMap<>();
        for (String line : lines) {
            String[] fields = line.split(" ");
            int nameCol = 0;
            String groupName = fields[nameCol];
            if (clusters.containsKey(groupName)) {
                clusters.get(groupName).add(fields);
            } else {
                clusters.put(groupName, new ArrayList<>());
                clusters.get(groupName).add(fields);
            }
        }

        for (Map.Entry<String, ArrayList<String[]>> entry : clusters.entrySet()) {
            createMixtureModel(entry.getKey(), entry.getValue());
        }
        ArrayList<String[]> allLines = clusters.values().stream().flatMap(Collection::stream).collect(Collectors.toCollection(ArrayList::new));
        createMixtureModel("all", allLines);
    }

    private void createMixtureModel(String groupName, ArrayList<String[]> clusterLines) {
        int nDim = dimLabels.size();
        int nClusters = clusterLines.size();
        double[] weights = new double[nClusters];
        double[][] means = new double[nClusters][nDim];
        double[][][] covariances = new double[nClusters][nDim][nDim];
        for (int i = 0; i < nClusters; i++) {
            int indexer = 1;
            String[] fields = clusterLines.get(i);
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
        MixtureMultivariateNormalDistribution MVN = new MixtureMultivariateNormalDistribution(weights, means, covariances);
        MMVNs.put(groupName, MVN);
    }

    public void unfoldPeakList(PeakList peakList, String[] dimToFold, boolean[] alias) {
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[][] bounds = new double[dimToFold.length][2];

        if (dataset != null) {
            for (int i = 0; i < dimToFold.length; i++) {
                int iDim = dataset.getDim(dimToFold[i]);
                int size = dataset.getSizeReal(iDim);
                bounds[i][0] = dataset.pointToPPM(iDim, 0);
                bounds[i][1] = dataset.pointToPPM(iDim, size - 1);
            }

            int[] peakListToCluster = new int[peakList.getNDim()]; //map peakList dims to mvn dims
            peakList.getSpectralDims().forEach((peakDim) -> {
                    String nucleus = peakDim.getNucleus();
                    String nucleusName = nucleus.substring(nucleus.length()-1);
                    peakListToCluster[peakDim.getIndex()] = dimLabels.indexOf(nucleusName);
                }
            );

            for (Peak peak : peakList.peaks()) {
                double[] shifts = new double[peakList.getNDim()];
                String groupName = "";

                for (PeakDim dim : peak.getPeakDims()) { //set shifts according to dimensions of dimLabels
                    int iDim = peakListToCluster[dim.getSpectralDim()];
                    shifts[iDim] = dim.getChemShiftValue();

                    String assignment = dim.getLabel();
                    String residueName = ".";
                    String atomName = ".";
                    if (MoleculeFactory.getActive() != null) {
                        Atom atom = Molecule.getAtomByName(assignment);
                        if (atom != null) {
                            residueName = atom.getResidueName();
                            atomName = atom.getName();
                        }
                    }
                    if (!groupName.isBlank()) { groupName += "-";}
                    groupName += residueName + "-" + atomName;
                }

                MixtureMultivariateNormalDistribution mvn = MMVNs.get("all");
                if (MMVNs.containsKey(groupName)) {
                    mvn = MMVNs.get(groupName);
                }

                for (int i = 0; i < dimToFold.length; i++) {
                    String label = dimToFold[i];
                    int iDim = peakListToCluster[peak.getPeakDim(label).getSpectralDim()];

                    double density = mvn.density(shifts);

                    double shift = shifts[iDim];
                    double lowerLim = bounds[i][0];
                    double upperLim = bounds[i][1];
                    double bestDensity = density;
                    double bestShift = shift;
                    double[] foldedShifts = new double[2]; //two possible positions to test

                    foldedShifts[0] = alias[i] ? upperLim - (lowerLim - shift) : upperLim - (shift - upperLim);
                    foldedShifts[1] = alias[i] ? lowerLim + (shift - upperLim) : lowerLim + (lowerLim - shift);

                    for (double foldedShift : foldedShifts) {
                        shifts[iDim] = foldedShift;
                        double newDensity = mvn.density(shifts);
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