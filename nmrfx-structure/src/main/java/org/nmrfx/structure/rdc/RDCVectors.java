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
package org.nmrfx.structure.rdc;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.RDC;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author brucejohnson
 */
public class RDCVectors {

    String aName1;
    String aName2;

    List<RDC> rdcVectors = new ArrayList<>();

    public RDCVectors(Molecule molecule, String aName1, String aName2) {
        this.aName1 = aName1;
        this.aName2 = aName2;
        for (Polymer polymer : molecule.getPolymers()) {
            for (Residue residue : polymer.getResidues()) {
                Atom atom1 = residue.getAtom(aName1);
                Atom atom2 = residue.getAtom(aName2);
                if ((atom1 != null) && (atom2 != null)) {
                    Vector3D v1 = atom1.getPoint();
                    Vector3D v2 = atom2.getPoint();
                    double r = Vector3D.distance(v1, v2) * 1e-10;
                    Vector3D vector = null;
                    if (r != 0.0) {
                        vector = v1.subtract(v2);
                        vector.scalarMultiply(1.0e-10);
                        RDC rdcVector = new RDC(atom1, atom2, vector);
                        rdcVectors.add(rdcVector);
                    }
                }
            }
        }
    }

    public RDCVectors(Molecule molecule, List<String> atomKeys) {
        for (String atomKey : atomKeys) {
            String[] keys = atomKey.split(":");
            Atom atom1 = molecule.findAtom(keys[0]);
            Atom atom2 = molecule.findAtom(keys[1]);
            if ((atom1 != null) && (atom2 != null)) {
                System.out.println(atom1.getFullName() + " " + atom2.getFullName());
                Vector3D v1 = atom1.getPoint();
                Vector3D v2 = atom2.getPoint();
                double r = Vector3D.distance(v1, v2) * 1e-10;
                Vector3D vector = null;
                if (r != 0.0) {
                    vector = v1.subtract(v2);
                    vector.scalarMultiply(1.0e-10);
                    RDC rdcVector = new RDC(atom1, atom2, vector);
                    rdcVectors.add(rdcVector);
                }
            }
        }
    }

    public List<RDC> getRDCVectors() {
        return rdcVectors;
    }

    public List<RDC> getExpRDCVectors() {
        return rdcVectors.stream().filter(r -> r.getExpRDC() != null).collect(Collectors.toList());
    }
}
