/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.miner;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Bruce Johnson
 */
public interface NodeValidatorInterface {

    public boolean checkAtom(int aNum, boolean visited, final int[] currentPath, final int patternIndex, final int pathIndex, final int atomIndex, HashMap bondMap);

    public boolean checkBond(int order, final int[] currentPath, final int patternIndex, final int pathIndex, final int bondIndex);

    public ArrayList getParams(ArrayList path, final int patternIndex);

    public String[] getPropertyNames();

    public boolean[][] getProperties();

    public void init(int nAtoms);

    public int patternCount();

    public int getMode(int index);

    public int pathSize(int patternIndex);

    public void dumpProps();

    public int getJump(int patternIndex, final int pathIndex);
    void assignProps(ArrayList path, final int patternIndex);

}
