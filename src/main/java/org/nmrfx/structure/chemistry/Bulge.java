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
public class Bulge extends SecondaryStructure {

    public static int localind = 0;
    public int li = 0;
    public String name;

    public Bulge(String id, List<Residue> Bresidues) {
        name = id;
        li = localind;
        gi = globalind;
        secresidues = Bresidues;
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
            System.out.print(residue.getName() + residue.resNum + " lll");
        }
    }
}
