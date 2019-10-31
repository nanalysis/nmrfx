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
public class InternalLoop extends SecondaryStructure {

    public static int localind = 0;

    public InternalLoop(String id, List<Residue> ILresidue) {
        name = id;
        locali = localind;
        globali = globalind;
        secresidues = ILresidue;
        globalind++;
        localind++;

    }

}
