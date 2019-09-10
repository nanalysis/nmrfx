/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import java.util.*;

/**
 *
 * @author bajlabuser
 */
public class Helix extends SecondaryStructure {

    public int globalind;
    public int ind;

    public Helix(int globali, int i) {

        globalind = globali;
        ind = i;
    }

    public int getStrand() {
        return globalind;
    }

    public int getStrandInd() {
        return ind;
    }

    public List<Residue> residues(Molecule mol) {
        for (Polymer pol : mol.getPolymers()) {
            
            for (Residue residueA : residues) {
                for (Residue residueB : residues) {

                    int type = residueA.basePairType(residueB);
                    if (type == 1) {
                        residues.add(residueA);
                    }
                }
            }
        }

        return residues;
    }

}
