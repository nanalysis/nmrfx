/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import java.util.Arrays;

/**
 *
 * @author bajlabuser
 */
public class BasePair {
    public Residue res1;
    public Residue res2;
    
    public BasePair(Residue res1, Residue res2){
        this.res1 = res1;
        this.res2 = res2;
    }
     @Override
    public String toString() {
        return res1.iRes + ":" + res2.iRes;
    }
    public static boolean isCanonical(Residue res1, Residue res2) {
        boolean canon = false;
        if (res1.basePairType(res2) == 1) {
            canon = true;
        }
        return canon;
    }
}
