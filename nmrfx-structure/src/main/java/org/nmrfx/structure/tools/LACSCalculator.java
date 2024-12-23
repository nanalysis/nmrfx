package org.nmrfx.structure.tools;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class LACSCalculator {
    private static final Logger log = LoggerFactory.getLogger(LACSCalculator.class);

    private static Map<String, Double> refMap = new HashMap<>();

    record PPMDiff(double ppmDDCA, double ppmDDCB, double ppmDDC) {
    }

    public record LACSResult(List<Double> shiftsX, List<Double> shiftsY, double xMin, double xMax, double nMedian,
                             double pMedian, double allMedian, double nSlope, double pSlope) {
    }

    private static void loadRPPMs() throws IOException {
        InputStream iStream = LACSCalculator.class.getResourceAsStream("/data/lacsref.txt");
        if (iStream == null) {
            throw new IOException("Coudn't not get /data/lacsref.txt input stream");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String[] fields = line.split("\t");
                String key = fields[0] + ":" + fields[1];
                double value = Double.parseDouble(fields[2].toUpperCase());
                refMap.put(key, value);
            }
        }
    }

    public Optional<Double> getRef(Atom atom) {
        String aaName = atom.getEntity().getName();
        String atomName = atom.getName();
        return getRef(aaName, atomName);
    }

    public static Optional<Double> getRef(String aaName, String atomName) {
        if (refMap == null) {
            return Optional.empty();
        }
        String key = aaName.toUpperCase() + ":" + atomName.toUpperCase();
        if (refMap.isEmpty()) {
            try {
                loadRPPMs();
            } catch (IOException e) {
                log.error("Failed to load ref ppms", e);
                refMap = null;
                return Optional.empty();
            }
        }
        return Optional.ofNullable(refMap.get(key));
    }

    public Optional<LACSResult> calculateLACS(String calcAtomName, int iGroup) {
        Molecule molecule = Molecule.getActive();
        final double pSlope;
        final double nSlope;
        if (calcAtomName.equalsIgnoreCase("CA")) {
            nSlope = 0.4167;
            pSlope = 0.7699;
        } else {
            nSlope = -0.655;
            pSlope = -0.254;
        }
        DescriptiveStatistics nStat = new DescriptiveStatistics();
        DescriptiveStatistics pStat = new DescriptiveStatistics();
        DescriptiveStatistics allStat = new DescriptiveStatistics();
        DescriptiveStatistics deltaStat = new DescriptiveStatistics();
        List<Double> shiftsX = new ArrayList<>();
        List<Double> shiftsY = new ArrayList<>();
        LACSResult lacsResult = null;
        if (molecule != null) {
            molecule.getPolymers().forEach(polymer -> polymer.getResidues().forEach(residue -> {
                if (!residue.getName().equalsIgnoreCase("pro")
                        && !residue.getName().equalsIgnoreCase("gly")
                        && !residue.getName().equalsIgnoreCase("cys")) {
                    var ppmOpt = getDeltas(residue, iGroup, calcAtomName);
                    ppmOpt.ifPresent(ppmDiff -> {
                        double delta = ppmDiff.ppmDDCA - ppmDiff.ppmDDCB;
                        deltaStat.addValue(delta);
                        double value;
                        shiftsX.add(delta);
                        shiftsY.add(ppmDiff.ppmDDC);
                        if (delta > 0.0) {
                            value = ppmDiff.ppmDDC - delta * pSlope;
                            pStat.addValue(value);
                        } else {
                            value = ppmDiff.ppmDDC - delta * nSlope;
                            nStat.addValue(value);
                        }
                        allStat.addValue(value);
                    });
                }
            }));
            double xMin = deltaStat.getMin();
            double xMax = deltaStat.getMax();
            double nMedian = nStat.getPercentile(50);
            double pMedian = pStat.getPercentile(50);
            double allMedian = allStat.getPercentile(50);
            lacsResult = new LACSResult(shiftsX, shiftsY, xMin, xMax, nMedian, pMedian, allMedian, nSlope, pSlope);
        }
        return Optional.ofNullable(lacsResult);
    }

    Optional<PPMDiff> getDeltas(Residue residue, int iGroup, String calcAtomName) {
        Atom caAtom = residue.getAtom("CA");
        Atom cbAtom = residue.getAtom("CB");
        Atom calcAtom = residue.getAtom(calcAtomName);
        if ((caAtom != null) && (cbAtom != null) && (calcAtom != null)) {
            var caOpt = getRef(caAtom);
            var cbOpt = getRef(cbAtom);
            var cOpt = getRef(calcAtom);

            PPMv caPPMv = caAtom.getPPM(iGroup);
            PPMv cbPPMv = cbAtom.getPPM(iGroup);
            PPMv calcPPMv = calcAtom.getPPM(iGroup);

            if ((caPPMv != null) && caPPMv.isValid() && (cbPPMv != null) && cbPPMv.isValid()
                    && (calcPPMv != null) && calcPPMv.isValid() &&
                    caOpt.isPresent() && cbOpt.isPresent() && cOpt.isPresent()) {
                double caPPM = caPPMv.getValue();
                double cbPPM = cbPPMv.getValue();
                double ppm = calcPPMv.getValue();
                double ppmDDCA = caPPM - caOpt.get();
                double ppmDDCB = cbPPM - cbOpt.get();
                double ppmDDC = ppm - cOpt.get();
                PPMDiff ppmDiff = new PPMDiff(ppmDDCA, ppmDDCB, ppmDDC);
                return Optional.of(ppmDiff);
            }
        }
        return Optional.empty();
    }
}
