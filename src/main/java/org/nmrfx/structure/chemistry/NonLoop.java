/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import java.util.List;

/**
 *
 * @author bajlabuser
 */
public class NonLoop extends SecondaryStructure{
    
    public static int localind = 0;

    public NonLoop(String id, List<Residue> NLpresidues) {
        name = id;
        li = localind;
        gi = globalind;
        secresidues = NLpresidues;
        globalind++;
        localind++;

    }
}
