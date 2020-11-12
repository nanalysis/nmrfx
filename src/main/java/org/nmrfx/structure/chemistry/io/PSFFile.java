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

package org.nmrfx.structure.chemistry.io;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;

/**
 *
 * @author brucejohnson
 */
public class PSFFile {

    public static void read(String fileName)
            throws MoleculeIOException {
        int i;
        String string;
        LineNumberReader lineReader;
        System.out.println("open " + fileName);
        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bf);
        } catch (IOException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }

        String lastRes = "";

        File file = new File(fileName);
        int dotPos = file.getName().lastIndexOf('.');
        String molName;
        if (dotPos >= 0) {
            molName = file.getName().substring(0, dotPos);
        } else {
            molName = file.getName();
        }
        Molecule molecule = new Molecule(molName);
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
        ArrayList<Atom> atoms = new ArrayList<Atom>();
        int natoms = 0;
        int nbonds = 0;

        while (true) {
            try {
                string = lineReader.readLine();
                if (string == null) {
                    lineReader.close();
                    System.out.println("read null");
                    return;
                }
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());

                return;
            }

            int index = string.indexOf("!NATOM");
            if (index != -1) {
                natoms = Integer.parseInt(string.substring(0, index).trim());
                break;
            }
        }
        //natoms = 0;
        for (i = 0; i < natoms; i++) {
            try {
                string = lineReader.readLine();
                if (string == null) {
                    lineReader.close();
                    return;
                }
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());

                return;
            }

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
            Point3 pt = atom.getPoint(structureNumber);
            pt = new Point3(0.0, 0.0, 0.0);
            atom.setPoint(structureNumber, pt);
            atom.setOccupancy(1.0f);
            atom.setBFactor(1.0f);

            residue.addAtom(atom);
            atoms.add(atom);
        }
        while (true) {
            try {
                string = lineReader.readLine();
                if (string == null) {
                    lineReader.close();
                    return;
                }
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());

                return;
            }
            int index = string.indexOf("!NBOND");
            if (index != -1) {
                nbonds = Integer.parseInt(string.substring(0, index).trim());
                break;
            }
        }

        i = 0;
        while (i < nbonds) {
            try {
                string = lineReader.readLine();
                if (string == null) {
                    lineReader.close();
                    return;
                }
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
                return;
            }
            string = string.trim();
            String[] atomPairs = string.split("\\s+");
            for (int j = 0; j < atomPairs.length; j += 2) {
                int ii = Integer.parseInt(atomPairs[j]);
                int jj = Integer.parseInt(atomPairs[j + 1]);
                Atom atom1 = atoms.get(ii - 1);
                Atom atom2 = atoms.get(jj - 1);
                Order order = Order.SINGLE;
                boolean recordBondInPolymer = false;
                if (atom1.entity != atom2.entity) {
                    recordBondInPolymer = true;
                }
                if (atom1.getName().startsWith("H")) {
                    Atom.addBond(atom2, atom1, order, recordBondInPolymer);
                } else {
                    Atom.addBond(atom1, atom2, order, recordBondInPolymer);
                }

                i++;
            }

        }

        Molecule.makeAtomList();
        molecule.structures.add(Integer.valueOf(structureNumber));
        molecule.resetActiveStructures();

    }
}
