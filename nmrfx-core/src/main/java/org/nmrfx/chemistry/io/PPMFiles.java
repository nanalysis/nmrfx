/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chemistry.io;

import org.nmrfx.chemistry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class PPMFiles {

    private PPMFiles() {

    }

    private static final Logger log = LoggerFactory.getLogger(PPMFiles.class);

    public static void writePPM(MoleculeBase molecule, FileWriter chan, int ppmSet, boolean refMode) throws IOException {

        molecule.updateAtomArray();

        List<Atom> atoms = molecule.getAtomArray();

        for (Atom atom : atoms) {
            PPMv ppmV;
            if (refMode) {
                ppmV = atom.getRefPPM(ppmSet);
            } else {
                ppmV = atom.getPPM(ppmSet);
            }
            if ((ppmV != null) && ppmV.isValid()) {
                write(atom, ppmV, chan, refMode);

            }
        }
    }

    static void write(Atom atom, PPMv ppmV, FileWriter chan, boolean refMode) throws IOException {
        String result;
        Entity entity = atom.getEntity();
        if (entity instanceof Compound) {
            String shortName = atom.getShortName();
            String fullName = atom.getFullName();
            String name;
            if (atom.getEntity().molecule.entities.size() == 1) {
                name = shortName;
            } else {
                name = fullName;
            }
            double ppm = ppmV.getValue();
            double errValue = ppmV.getError();
            if (refMode) {
                result = String.format("%s\t%.4f\t%.4f\n", name, ppm, errValue);
            } else {
                int stereo = ppmV.getAmbigCode();
                atom.getBMRBAmbiguity();
                result = String.format("%s\t%.3f\t%d\n", name, ppm, stereo);

            }
            chan.write(result);
        }
    }
    static String getSeparator(String sline) {
        String separator;
        if (sline.contains("\t")) {
            separator = "\t";
        } else {
            separator = "\\s+";
        }

        return separator;
    }

    public static void readPPM(MoleculeBase molecule, Path path, int ppmSet, boolean refMode) {
        String separator = "";

        try (BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.isEmpty() || (sline.charAt(0) == '#')) {
                    continue;
                }
                if (separator.isEmpty()) {
                    separator = getSeparator(sline);
                }
                String[] sfields = sline.split(separator, -1);
                if (sfields.length > 4) {
                    parseXEASYLine(molecule, sfields, refMode, ppmSet);
                } else {
                    parseLine(sfields, refMode, ppmSet);
                }
            }
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
    }

    public static void parseLine(String[] sfields, boolean refMode, int ppmSet) {
        if (sfields.length > 1) {
            String atomRef = sfields[0];
            Atom atom = MoleculeBase.getAtomByName(atomRef);
            if (atom != null) {
                double ppm = Double.parseDouble(sfields[1]);
                if (refMode) {
                    atom.setRefPPM(ppmSet, ppm);
                    if (sfields.length > 2) {
                        double errValue = Double.parseDouble(sfields[2]);
                        atom.setRefError(errValue);
                    }
                } else {
                    atom.setPPM(ppmSet, ppm);

                }
            }
        }
    }

    public static void parseXEASYLine(MoleculeBase molecule, String[] sfields, boolean refMode, int ppmSet) {
        if (sfields.length > 1) {
            String aName = sfields[3];
            String residue = sfields[4];
            String atomRef = residue + "." + aName;
            var atoms = molecule.getAtoms(atomRef);

            for (Atom atom : atoms) {
                if (atom == null) {
                    log.error("no atom {}", atomRef);
                } else {
                    double ppm = Double.parseDouble(sfields[1]);
                    if (refMode) {
                        atom.setRefPPM(ppmSet, ppm);
                    } else {
                        atom.setPPM(ppmSet, ppm);
                    }
                }
            }
        }

    }
}
