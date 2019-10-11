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
    public int li = 0;
    public String name;

    public Helix(String id, List<Residue> HXresidue) {
        name = id;
        li = localind;
        gi = globalind;
        secresidues = HXresidue;
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
    
    public void getInvolvedRes(){
        int i = 0;
        while( i < secresidues.size()){
            Residue res1 = secresidues.get(i);
            Residue res2 = secresidues.get(i+1);
            System.out.print(res1.getName()+ res1.resNum + ":" + res2.getName()+ res2.resNum+ " ");
            i += 2;
        }
    }

//    public static List<Helix> getHelicies() {
//        List<Helix> helicies = new ArrayList<>();
//        List<Residue> res = new ArrayList<>();
//        for (HashMap.Entry<Integer, List<BasePair>> stem : HX.entrySet()) {
//            List<BasePair> bps = stem.getValue();
//            for (BasePair bp : bps) {
//                res.add(bp.res1);
//                res.add(bp.res2);
//            }
//            Helix Hx = new Helix(globalind, localind, res);
//            helicies.add(Hx);
//        }
//        return helicies;
//    }
}

//    public List<Residue> hresidues(Molecule mol) {
//        int g = 0;
//        int i = 0;
//        List<Residue> HXresidue = new ArrayList<>();
//        List<Residue> h = SecondaryStructure.residues(mol);
//        for (Polymer pol : mol.getPolymers()) {
//            for (Residue residueA : h) {
//                for (Residue residueB : h) {
//                    if (residueA.getResNum() > residueB.getResNum()) {
//                        int type = residueA.basePairType(residueB);
//                        if (type == 1) {
//                            i++;
//                            HXresidue.add(residueA);
//                        }
//                    }
//                }
//                if (i == 0) {
//                    g++;
//                    i=0;
//                }
//            }
//
//        }
//
//        return residues;
//    }
//    public List<Helix> hresidues(Molecule mol){
//        
//    }

