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

    public static int localind = 0;
    public List<BasePair> basePairs = new ArrayList<BasePair>();

    public Helix(String id, List<Residue> HXresidue) {
        name = id;
        locali = localind;
        globali = globalind;
        secresidues = HXresidue;
        globalind++;
        localind++;
        setBasePairs();

    }

//    public boolean isPaired(Residue resA, Residue resB) {
//        
//    }
    @Override
    public void getInvolvedRes() {
        int i = 0;
        while (i < secresidues.size()) {
            Residue res1 = secresidues.get(i);
            Residue res2 = secresidues.get(i + 1);
            System.out.print(res1.getName() + res1.resNum + ":" + res2.getName() + res2.resNum + " ");
            i += 2;
        }
    }

    public void setBasePairs() {
        int i = 0;
        while (i < secresidues.size()) {
            Residue res1 = secresidues.get(i);
            Residue res2 = secresidues.get(i + 1);
            BasePair bp = new BasePair(res1, res2);
            basePairs.add(bp);
            i += 2;
        }
    }
}
