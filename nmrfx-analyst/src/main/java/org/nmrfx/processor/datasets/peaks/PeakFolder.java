package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.processor.datasets.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class PeakFolder {
    private static final Logger log = LoggerFactory.getLogger(PeakFolder.class);
    public List<String> DIMS = Arrays.asList("H", "C");
    HashMap<String, MixtureMultivariateNormalDistribution> MMVNs = new HashMap<>();

    public PeakFolder() {
        loadComponents();
    }

    private String getGroupName(String residueName, String atomName) {
        return residueName + '.' + atomName;
    }

    private void loadComponents() {
        InputStream iStream = this.getClass().getResourceAsStream("/data/C13HSQC_clusters.txt");
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
            String[] groups = fields[nameCol].split("-");
            String residueType = groups[2];
            String atomType = groups[3];
            String groupName = getGroupName(residueType, atomType);
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
    }

    private void createMixtureModel(String groupName, ArrayList<String[]> clusterLines) {
        int nDim = DIMS.size();
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

    public void unfoldPeakList(PeakList peakList, SpectralDim dimToFold, boolean useAssign, Peak peak) {
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[] bounds = new double[2];
        boolean alias = dimToFold.getFoldMode() == 'a';

        if (dataset != null) {
            int iDim = dataset.getDim(dimToFold.getDimName());
            int size = dataset.getSizeReal(iDim);
            bounds[0] = dataset.pointToPPM(iDim, 0);
            bounds[1] = dataset.pointToPPM(iDim, size - 1);
            String relationDim = peakList.getSpectralDim(dimToFold.getDimName()).getRelationDim();
            int bondedDim = peakList.getSpectralDim(relationDim).getIndex();


            int[] peakListToCluster = new int[peakList.getNDim()]; //map peakList dims to mvn dims
            peakList.getSpectralDims().forEach(peakDim -> {
                        String nucleus = peakDim.getNucleus();
                        String nucleusName = nucleus.substring(nucleus.length() - 1);
                        peakListToCluster[peakDim.getIndex()] = DIMS.indexOf(nucleusName);
                    }
            );
            if (peak != null) {
                unfoldPeak(peak, peakListToCluster, dimToFold, bondedDim, bounds, alias, useAssign, true);

            } else {
                for (Peak foldPeak : peakList.peaks()) { //set shifts according to dimensions of dimLabels
                    unfoldPeak(foldPeak, peakListToCluster, dimToFold, bondedDim, bounds, alias, useAssign, false);
                }
            }
        }
    }

    void unfoldPeak(Peak peak, int[] peakListToCluster, SpectralDim dimToFold, int bondedDim, double[] bounds, boolean alias, boolean useAssign, boolean debugMode) {
        double[] shifts = new double[DIMS.size()];
        int pDim = peakListToCluster[peak.getPeakDim(dimToFold.getDimName()).getSpectralDim()];
        int pBonded = peakListToCluster[peak.getPeakDim(bondedDim).getSpectralDim()];
        shifts[pDim] = peak.getPeakDim(dimToFold.getDimName()).getChemShiftValue();
        shifts[pBonded] = peak.getPeakDim(bondedDim).getChemShiftValue();

        MixtureMultivariateNormalDistribution mvn = null;
        if (useAssign) {
            String assignment = peak.getPeakDim(dimToFold.getDimName()).getLabel();
            Atom atom = MoleculeBase.getAtomByName(assignment);
            if (atom != null) {
                String residueName = atom.getResidueName();
                String atomName = atom.getName();
                String groupName = getGroupName(residueName, atomName);
                if (MMVNs.containsKey(groupName)) {
                    mvn = MMVNs.get(groupName);
                }
            }
        }
        double density = 1e-10;
        if (mvn != null) {
            density = mvn.density(shifts);
        } else {
            for (var entry : MMVNs.entrySet()) {
                double newDensity = entry.getValue().density(shifts);
                if (debugMode) {
                    log.info("density {} {} {} {}", entry.getKey(), newDensity, shifts[0], shifts[1]);
                }
                if (newDensity > density) {
                    density = newDensity;
                }
            }
        }

        double shift = shifts[pDim];
        double lowerLim = bounds[0];
        double upperLim = bounds[1];
        double bestDensity = density;
        double bestShift = shift;
        double[] foldedShifts = new double[2]; //two possible positions to test

        foldedShifts[0] = alias ? upperLim - (lowerLim - shift) : upperLim - (shift - upperLim);
        foldedShifts[1] = alias ? lowerLim + (shift - upperLim) : lowerLim + (lowerLim - shift);

        for (double foldedShift : foldedShifts) {
            shifts[pDim] = foldedShift;
            if (mvn != null) {
                double newDensity = mvn.density(shifts);
                if (newDensity > bestDensity) {
                    bestShift = shifts[pDim];
                    bestDensity = newDensity;
                }
            } else {
                for (var entry : MMVNs.entrySet()) {
                    double newDensity = entry.getValue().density(shifts);
                    if (debugMode) {
                        log.info("density {} {} {} {}", entry.getKey(), newDensity, shifts[0], shifts[1]);
                    }

                    if (newDensity > bestDensity) {
                        bestShift = shifts[pDim];
                        bestDensity = newDensity;
                    }
                }
            }
        }

        if (bestDensity != density) {
            peak.getPeakDim(dimToFold.getDimName()).setChemShiftValue((float) bestShift);
        }
    }
}

