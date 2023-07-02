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

    private static final Logger log = LoggerFactory.getLogger(PPMFiles.class);

    public static void writePPM(MoleculeBase molecule, FileWriter chan, int ppmSet, boolean refMode) throws IOException {
        int i;
        String result = null;

        molecule.updateAtomArray();

        i = 0;
        List<Atom> atoms = molecule.getAtomArray();

        for (Atom atom : atoms) {
            PPMv ppmV;
            if (refMode) {
                ppmV = atom.getRefPPM(ppmSet);
            } else {
                ppmV = atom.getPPM(ppmSet);
            }
            if ((ppmV != null) && ppmV.isValid()) {
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
        }
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
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.charAt(0) == '#') {
                    continue;
                }
                if (separator.equals("")) {
                    if (sline.contains("\t")) {
                        separator = "\t";
                    } else {
                        separator = "\\s+";
                    }
                }
                String[] sfields = sline.split(separator, -1);
                if (sfields.length > 1) {
                    String atomRef = sfields[0];
                    Atom atom = MoleculeBase.getAtomByName(atomRef);
                    if (atom == null) {
                        System.out.println("no atom " + atomRef);
                    } else {
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
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
    }
}
/*
        foreach atom $atoms {
        set ppm [nv_atom elem ppm ${atom}_$ppmSet]
        set stereo [nv_atom elem stereo $atom]
        if {![string is double -strict $stereo]} {
            set stereo 1
        }
        if {($ppm != {}) && ($ppm> -990.0)} {
            set aname [nv_atom elem aname $atom]
            set seq [nv_atom elem seq $atom]
            if {!$fullFormat} {
                if {$ppmSet eq "R"} {
                    set eppm [nv_atom elem eppm ${atom}_$ppmSet]
                    puts $f1 [format "%3d.%-4s %9.3f %9.3f" $seq $aname $ppm $eppm]
                } else {
                    puts $f1 [format "%3d.%-4s %9.3f %1d" $seq $aname $ppm $stereo]
                }
            } else {
                if {$ppmSet eq "R"} {
                    set eppm [nv_atom elem eppm ${atom}_$ppmSet]
                    puts $f1 [format "%15s %9.3f %9.3f" $atom $ppm $eppm]
                } else {
                    puts $f1 [format "%15s %9.3f %1d" $atom $ppm $stereo]
                }
            }
        }
    }

 */
