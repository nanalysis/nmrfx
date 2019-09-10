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

    public int globalind;
    public int ind;
    public List<Residue> residues = new ArrayList<>();

    /*
    List of Helix and Loop objects
    */
    
    
    public SecondaryStructure() {

    }

    public abstract List<Residue> residues(Molecule mol);
    
//    public String resType(Residue residue) {
//        
//    }

}
