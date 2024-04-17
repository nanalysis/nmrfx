/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.miner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public interface NodeValidatorInterface {

    public boolean checkAtom(int aNum, boolean visited, final int[] currentPath, final int patternIndex, final int pathIndex, final int atomIndex, Map bondMap);

    public boolean checkBond(int order, final int[] currentPath, final int patternIndex, final int pathIndex, final int bondIndex);

    public List getParams(List path, final int patternIndex);

    public String[] getPropertyNames();

    public boolean[][] getProperties();

    public void init(int nAtoms);

    public int patternCount();

    public int getMode(int index);

    public int pathSize(int patternIndex);

    public default List<List<String>> dumpProps() {
        String[] propertyNames = getPropertyNames();
        boolean[][] p = getProperties();
        List<List<String>> result = new ArrayList<>();
        List<String> header = new ArrayList<>();
        for (int i = 0; i < propertyNames.length; i++) {
            if (!propertyNames[i].contains("temp")) {
                header.add(propertyNames[i]);
            }
        }
        result.add(header);
        for (int i = 0; i < p.length; i++) {
            List<String> line = new ArrayList<>();
            for (int j = 0; j < p[i].length; j++) {
                if (p[i][j]) {
                    if (!propertyNames[j].contains("temp")) {
                        line.add(propertyNames[j]);
                    }
                }
            }
            result.add(line);
        }
        return result;

    }

    public int getJump(int patternIndex, final int pathIndex);

    void assignProps(List path, final int patternIndex);

}
