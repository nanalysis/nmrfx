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

    public Bulge(String id, List<Residue> Bresidues) {
        name = id;
        locali = localind;
        globali = globalind;
        secresidues = Bresidues;
        globalind++;
        localind++;
    }

}
