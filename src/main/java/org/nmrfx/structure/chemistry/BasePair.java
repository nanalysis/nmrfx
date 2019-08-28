/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

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
    
}
