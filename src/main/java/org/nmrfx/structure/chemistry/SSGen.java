/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author bajlabuser
 */
public class SSGen {

    public int tracker = 0;
    public String viennaSeq;
    public List<Residue> residues;

    public Molecule molecule;
    public static String identifier;

    public SSGen(Molecule mol, String vienna) {
        molecule = mol;
        viennaSeq = vienna;

    }
    
    public SSGen(Molecule mol){
        molecule = mol;
        char[] vSeq = mol.viennaSequence();
        String vienna = new String(vSeq);
        viennaSeq = vienna;
    }

    public void genRNAResidues() {
        List<Residue> res = new ArrayList<>();
        for (Polymer pol : molecule.getPolymers()) {
            for (Residue residue : pol.getResidues()) {
                if (pol.isRNA()) {
                    residue.pairedToRes = -1;
                    res.add(residue);
                }
            }
        }
        residues = res;
    }

    public void pairTo() {
        SSLayout ssLay = new SSLayout(viennaSeq.length());
        ssLay.interpVienna(viennaSeq, residues);
    }

    public static SecondaryStructure classifyRes(List<Residue> res) {
        if (res != null && !(res.isEmpty())) {
            if (null != identifier) {
                switch (identifier) {
                    case "junction":
                        SecondaryStructure J = new Junction("Junction ", res);
                        return J;
                    case "nonloop": {
                        SecondaryStructure L = new Loop("Nonloop ", res);
                        return L;
                    }
                    case "bulge":
                        SecondaryStructure B = new Bulge("Bulge ", res);
                        return B;
                    case "internalLoop":
                        SecondaryStructure IL = new InternalLoop("InternalLoop ", res);
                        return IL;
                    case "loop": {
                        SecondaryStructure L = new Loop("Loop ", res);
                        return L;
                    }
                    case "helix":
                        SecondaryStructure H = new Helix("Helix ", res);
                        return H;
                    default:
                        break;
                }
            }
        }
        return null;
    }

    public List<Residue> resList() {
        List<Residue> type = new ArrayList<>();
        int index;
        boolean add = false;
        if (residues.get(tracker).pairedToRes < 0) {
            List<Residue> temp = new ArrayList<>();
            while (tracker < residues.size() && residues.get(tracker).pairedToRes < 0) {
                temp.add(residues.get(tracker));
                tracker++;
            }

//            if (temp.get(0).iRes - 1 >= 0) {
//                index = residues.get(temp.get(0).iRes - 1).pairedToRes - 1; //
//            }
            index = residues.get(temp.get(0).iRes - 1).pairedToRes - 1; //
            if (temp.get(temp.size() - 1).iRes == residues.size() - 1 || temp.get(0).iRes == 0) { //last residue or first residue (string of non pairing all the way to end)
                add = true;
                identifier = "nonloop"; //instead of calling first residue, call last residue
            } else if (temp.get(0).iRes - 1 == residues.get(temp.get(temp.size() - 1).iRes + 1).pairedToRes) { //loop
                add = true;
                identifier = "loop";
            } else if (residues.get(temp.get(0).iRes - 1).pairedToRes < residues.get(temp.get(temp.size() - 1).iRes + 1).pairedToRes) { //junction
                add = true;
                identifier = "junction";
            } else if (residues.get(index).pairedToRes == -1 && (residues.get(temp.get(0).iRes - 1).iRes < residues.get(temp.get(0).iRes - 1).pairedToRes)) { //second half of internal loop
                add = true;
                identifier = "internalLoop";
                while (residues.get(index).pairedToRes == -1) {  // residues.get(index) != null  
                    temp.add((residues.get(index)));
                    index--;
                }
            } else if (temp.get(0).iRes - 1 < residues.get(temp.get(0).iRes - 1).pairedToRes) { //left side
                if (residues.get(residues.get(temp.get(0).iRes - 1).pairedToRes - 1).pairedToRes >= 0) {
                    add = true;
                    identifier = "bulge";
                }
            } else if (temp.get(0).iRes - 1 > residues.get(temp.get(0).iRes - 1).pairedToRes) { //right side
                if (residues.get(residues.get(temp.get(temp.size() - 1).iRes + 1).pairedToRes + 1).pairedToRes >= 0) {
                    add = true;
                    identifier = "bulge";
                }
            }
            if (add) {
                type.addAll(temp);
            }

            return type;

        } else if (residues.get(tracker).pairedToRes >= 0) {
            while (tracker < residues.size() && residues.get(tracker).pairedToRes >= 0) {
                if (residues.get(tracker).iRes < residues.get(residues.get(tracker).pairedToRes).iRes) {
                    type.add(residues.get(tracker));
                    type.add(residues.get(residues.get(tracker).pairedToRes));
                    tracker++;
                } else {
                    tracker++;
                }
            }
            identifier = "helix";
            return type;
        }
        return null;
    }

    public List<SecondaryStructure> secondaryStructGen() {
        List<SecondaryStructure> structures = new ArrayList<>();
        while (tracker < residues.size()) {
            SecondaryStructure ss = classifyRes(resList());

            if (ss != null) {
                ss.size = ss.secresidues.size();
                structures.add(ss);
            }
        }
        return structures;
    }

}
