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
    public static final List<String> DIMS = Arrays.asList("H", "C");
    static final HashMap<String, MixtureMultivariateNormalDistribution> MMVNs = new HashMap<>();

    private String getGroupName(String residueName, String atomName1, String atomName2) {
        return residueName + '.' + atomName1 + '.' + atomName2;
    }

    private void loadComponents() throws IOException {
        InputStream iStream = this.getClass().getResourceAsStream("/data/C13HSQC_clusters.txt");
        if (iStream == null) {
            throw new IOException("Couldn't read cluster file");
        }
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            lines = reader.lines().toList();
        }

        HashMap<String, ArrayList<String[]>> clusters = new HashMap<>();
        for (String line : lines) {
            String[] fields = line.split(" ");
            int nameCol = 0;
            String[] groups = fields[nameCol].split("-");
            String residueType = groups[0];
            String atomType1 = groups[1];
            String atomType2 = groups[2];
            String groupName = getGroupName(residueType, atomType1, atomType2);
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

    private static void createMixtureModel(String groupName, ArrayList<String[]> clusterLines) {
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
        MixtureMultivariateNormalDistribution mixtureMultivariateNormalDistribution = new MixtureMultivariateNormalDistribution(weights, means, covariances);
        MMVNs.put(groupName, mixtureMultivariateNormalDistribution);
    }

    public void unfoldPeakList(PeakList peakList, SpectralDim dimToFold, boolean useAssign, Peak peak) throws IOException {
        if (MMVNs.isEmpty()) {
            loadComponents();
        }
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double[] bounds = new double[2];
        boolean alias = dimToFold.getFoldMode() == 'a';

        if (dataset != null) {
            int iDim = dataset.getDim(dimToFold.getDimName());
            int size = dataset.getSizeReal(iDim);
            bounds[0] = dataset.pointToPPM(iDim, 0);
            bounds[1] = dataset.pointToPPM(iDim, size - 1.0);
            double deltaPPM = Math.abs(dataset.pointToPPM(iDim, 0) - dataset.pointToPPM(iDim, size));
            String relationDim = peakList.getSpectralDim(dimToFold.getDimName()).getRelationDim();
            int bondedDim = peakList.getSpectralDim(relationDim).getIndex();


            if (peak != null) {
                unfoldPeak(peak, dimToFold, bondedDim, bounds, deltaPPM, alias, useAssign, true);
            } else {
                for (Peak foldPeak : peakList.peaks()) { //set shifts according to dimensions of dimLabels
                    unfoldPeak(foldPeak, dimToFold, bondedDim, bounds, deltaPPM, alias, useAssign, false);
                }
            }
        }
    }

    MixtureMultivariateNormalDistribution getMMND(Peak peak, boolean useAssign, SpectralDim dimToFold, int pDim, int pBonded ) {
        MixtureMultivariateNormalDistribution mvn = null;
        if (useAssign) {
            String assignment1 = peak.getPeakDim(dimToFold.getDimName()).getLabel();
            String assignment2 = peak.getPeakDim(dimToFold.getRelationDim()).getLabel();

            String[] atomNames = new String[DIMS.size()];

            Atom atom1 = MoleculeBase.getAtomByName(assignment1);
            Atom atom2 = MoleculeBase.getAtomByName(assignment2);
            if (atom1 != null && atom2 != null) {
                String residueName = atom1.getResidueName();
                atomNames[pDim] = atom1.isMethyl() ? atom1.getName().substring(0, atom1.getName().length() - 1) : atom1.getName();
                atomNames[pBonded] = atom2.isMethyl() ? atom2.getName().substring(0, atom2.getName().length() - 1) : atom2.getName();
                String groupName = getGroupName(residueName, atomNames[0], atomNames[1]);
                if (MMVNs.containsKey(groupName)) {
                    mvn = MMVNs.get(groupName);
                }
            }
        }
        return  mvn;
    }
    void unfoldPeak(Peak peak,  SpectralDim dimToFold, int bondedDim, double[] bounds, double deltaPPM, boolean alias, boolean useAssign, boolean debugMode) {
        double[] shifts = new double[DIMS.size()];
        int pDim = 1;
        int pBonded = 0;
        shifts[pDim] = peak.getPeakDim(dimToFold.getDimName()).getChemShiftValue();
        shifts[pBonded] = peak.getPeakDim(bondedDim).getChemShiftValue();

        MixtureMultivariateNormalDistribution mvn = getMMND(peak, useAssign,dimToFold, pDim, pBonded);

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
        boolean updatePeak = false;

        foldedShifts[0] = alias ? shift - deltaPPM : upperLim - (shift - upperLim);
        foldedShifts[1] = alias ? shift + deltaPPM : lowerLim + (lowerLim - shift);

        for (double foldedShift : foldedShifts) {
            shifts[pDim] = foldedShift;
            if (mvn != null) {
                double newDensity = mvn.density(shifts);
                if (newDensity > bestDensity) {
                    bestShift = shifts[pDim];
                    bestDensity = newDensity;
                    updatePeak = true;
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
                        updatePeak = true;
                    }
                }
            }
        }

        if (updatePeak) {
            peak.getPeakDim(dimToFold.getDimName()).setChemShiftValue((float) bestShift);
        }
    }
}

