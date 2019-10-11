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
public class Junction extends SecondaryStructure {

    public static int localind = 0;
    public int li = 0;
    public String name;

    public Junction(String id, List<Residue> Jresidue) {
        name = id;
        li = localind;
        gi = globalind;
        secresidues = Jresidue;
        globalind++;
        localind++;

    }

    @Override
    public String toString() {
        return name + getGlobalInd() + ":" + getLocalInd() ;
    }

    public int getGlobalInd() {
        return gi;
    }

    public int getLocalInd() {
        return li;
    }

    public List<Residue> getResidues() {
        return secresidues;
    }

    public void getInvolvedRes() {
        for (Residue residue : secresidues) {
            System.out.print(residue.getName() + residue.resNum + " ");
        }
    }

}
