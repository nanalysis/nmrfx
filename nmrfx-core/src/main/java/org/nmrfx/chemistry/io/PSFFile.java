/*
 * NMRFx Structure : A Program for Calculating Structures
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.nmrfx.chemistry.io;

import org.nmrfx.chemistry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;

/**
 * @author brucejohnson
 */
public class PSFFile {

    private static final Logger log = LoggerFactory.getLogger(PSFFile.class);

    public static void read(String fileName)
            throws MoleculeIOException {
        String string;
        String lastRes = "";

        File file = new File(fileName);
        int dotPos = file.getName().lastIndexOf('.');
        String molName;
        if (dotPos >= 0) {
            molName = file.getName().substring(0, dotPos);
        } else {
            molName = file.getName();
        }
        MoleculeBase molecule = MoleculeFactory.newMolecule(molName);
        String coordSetName = "A";
        String polymerName = "A";

        Polymer polymer = new Polymer(polymerName);
        polymer.setNomenclature("PSF");
        polymer.setCapped(false);
        polymer.molecule = molecule;
        polymer.assemblyID = molecule.entityLabels.size() + 1;
        molecule.addEntity(polymer, coordSetName);

        int structureNumber = 0;

        Residue residue = null;
        ArrayList<Atom> atoms = new ArrayList<>();
        int natoms = 0;
        int nbonds = 0;
        int i;
        try (BufferedReader bufReader = new BufferedReader(new FileReader(fileName))) {
            while ((string = bufReader.readLine()) != null) {
                int index = string.indexOf("!NATOM");
                if (index != -1) {
                    natoms = Integer.parseInt(string.substring(0, index).trim());
                    break;
                }
            }
            i = 0;
            while (i < natoms && (string = bufReader.readLine()) != null) {
                PSFAtomParser atomParse = new PSFAtomParser(string);
                if (!lastRes.equals(atomParse.resNum)) {
                    lastRes = atomParse.resNum;
                    residue = polymer.getResidue(atomParse.resNum);
                    if (residue == null) {
                        residue = new Residue(atomParse.resNum, atomParse.resName);
                        polymer.addResidue(residue);
                        residue.molecule = molecule;
                    }
                    residue.label = atomParse.resName;
                }

                Atom atom = new Atom(atomParse);

                atom.entity = residue;

                atom.setPointValidity(structureNumber, true);
                Point3 pt = new Point3(0.0, 0.0, 0.0);
                atom.setPoint(structureNumber, pt);
                atom.setOccupancy(1.0f);
                atom.setBFactor(1.0f);

                residue.addAtom(atom);
                atoms.add(atom);
                i++;
            }
            while ((string = bufReader.readLine()) != null) {
                int index = string.indexOf("!NBOND");
                if (index != -1) {
                    nbonds = Integer.parseInt(string.substring(0, index).trim());
                    break;
                }
            }

            i = 0;
            while (i < nbonds && (string = bufReader.readLine()) != null) {
                string = string.trim();
                String[] atomPairs = string.split("\\s+");
                for (int j = 0; j < atomPairs.length; j += 2) {
                    int ii = Integer.parseInt(atomPairs[j]);
                    int jj = Integer.parseInt(atomPairs[j + 1]);
                    Atom atom1 = atoms.get(ii - 1);
                    Atom atom2 = atoms.get(jj - 1);
                    Order order = Order.SINGLE;
                    boolean recordBondInPolymer = atom1.entity != atom2.entity;
                    if (atom1.getName().startsWith("H")) {
                        Atom.addBond(atom2, atom1, order, recordBondInPolymer);
                    } else {
                        Atom.addBond(atom1, atom2, order, recordBondInPolymer);
                    }
                    i++;
                }
            }
        } catch (FileNotFoundException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        } catch (IOException ioe) {
            log.warn(ioe.getMessage(), ioe);
            return;
        }
        molecule.updateAtomArray();
        molecule.structures.add(structureNumber);
        molecule.resetActiveStructures();

    }
}
