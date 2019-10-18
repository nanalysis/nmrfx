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
public class Loop extends SecondaryStructure {

    public static int localind = 0;

    public Loop(String id, List<Residue> Lpresidues) {
        name = id;
        li = localind;
        gi = globalind;
        secresidues = Lpresidues;
        globalind++;
        localind++;

    }

}
