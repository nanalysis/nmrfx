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
public abstract class SecondaryStructure {

    public static int globalind = 0;
    public int gi = 0;
    public int li = 0;
    public int size;
    public String name;
    public List<Residue> secresidues = new ArrayList<>();

    @Override
    public String toString() {
        return name + getGlobalInd() + ":" + getLocalInd();
    }

    public List<Residue> getResidues() {
        return secresidues;
    }

    public int getGlobalInd() {
        return gi;
    }

    public int getLocalInd() {
        return li;
    }

    public void getInvolvedRes() {
        for (Residue residue : secresidues) {
            System.out.print(residue.getName() + residue.resNum);
        }
    }
}
