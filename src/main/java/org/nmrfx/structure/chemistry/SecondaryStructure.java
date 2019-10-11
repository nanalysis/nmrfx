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
    public List<Residue> secresidues = new ArrayList<>();
    public abstract List<Residue> getResidues();
    public abstract int getGlobalInd();
    public abstract int getLocalInd();
    public abstract void getInvolvedRes();
}   
