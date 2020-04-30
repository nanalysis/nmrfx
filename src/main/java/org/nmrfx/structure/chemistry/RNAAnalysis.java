/*
 * NMRFx Structure : A Program for Calculating Structures 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.structure.chemistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author bajlabuser
 */
public class RNAAnalysis {

    /**
     * Generates a list of RNA residues in the given molecule
     *
     * @param molecule
     * @return
     */
    public static List<Residue> genRnaResidues(Molecule molecule) { //list of only rna residues 
        List<Residue> rnaResidues = new ArrayList();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isRNA()) {
                for (Residue res : polymer.getResidues()) {
                    rnaResidues.add(res);
                }
            }
        }
        return rnaResidues;
    }

    /**
     * Generates a list of type 1 (Watson-Crick) RNA basepairs for the given
     * molecule
     *
     * @param molecule The molecule to analyze for base pairing
     * @return A list of BasePairs found in the molecule
     */
    public static List<BasePair> getPairList(Molecule molecule) { //for RNA only
        return RNAAnalysis.getPairList(molecule, 1);
    }

    /**
     * Generates a list of RNA basepairs for the given molecule
     *
     * @param molecule The molecule to analyze for base pairing
     * @param typeTarget The type of basepair to return in list
     * @return A list of BasePairs found in the molecule
     */
    public static List<BasePair> getPairList(Molecule molecule, int typeTarget) { //for RNA only
        Map<String, AllBasePairs> basePairMap = AllBasePairs.bpMap;
        List<BasePair> bpList = new ArrayList();
        List<Residue> rnaResidues = genRnaResidues(molecule);
        if (basePairMap.isEmpty()) {
            AllBasePairs.genBasePairList();
        }
        for (Residue residueA : rnaResidues) {
            for (Residue residueB : rnaResidues) {
                if (residueA.getResNum() < residueB.getResNum()) {
                    int type = residueA.getBasePairType(residueB);
                    if (type == typeTarget) {
                        BasePair bp = new BasePair(residueA, residueB);
                        bpList.add(bp);

                    }
                }
            }
        }
        return bpList;
    }

    /**
     * Generates a map which determines which pairs overlap and create
     * pseudoknots
     *
     * @param molecule
     * @return
     */
    public static Map<Integer, List<BasePair>> getBasePairs(Molecule molecule) {
        Map<Integer, List<BasePair>> bpMap = new HashMap<>();
        BasePair currentBp = null;
        int i = 0;
        List<BasePair> crossedPairs = new ArrayList();
        List<BasePair> bpList = getPairList(molecule);
        for (BasePair bp1 : bpList) {
            for (BasePair bp2 : bpList) {
                if (bp1.res1.iRes < bp2.res1.iRes && bp1.res2.iRes < bp2.res2.iRes && bp1.res2.iRes > bp2.res1.iRes) {
                    if (currentBp != bp2) {
                        bpMap.put(i, crossedPairs);
                        i++;
                        crossedPairs.clear();
                        crossedPairs.add(bp1);
                        currentBp = bp2;
                        break;

                    } else {
                        crossedPairs.add(bp1);
                        currentBp = bp2;
                        break;
                    }
                }
            }
        }
        return bpMap;
    }

    /**
     * Generates vienna (dot-bracket) sequence for RNA residues within molecule
     *
     * @param molecule
     * @return
     */
    public static char[] getViennaSequence(Molecule molecule) {
        Map<Integer, List<BasePair>> bpMap = getBasePairs(molecule);
        List<BasePair> bps = getPairList(molecule);
        List<Residue> rnaResidues = genRnaResidues(molecule);
        char[] vienna = new char[rnaResidues.size()];
        String leftBrackets = "[{";
        String rightBrackets = "]}";
        for (int i = 0; i < vienna.length; i++) {
            vienna[i] = '.';
        }
        for (BasePair bp : bps) {
            vienna[bp.res1.iRes] = '(';
            vienna[bp.res2.iRes] = ')';
        }
        if (!bpMap.isEmpty()) {
            for (Map.Entry<Integer, List<BasePair>> crossMap : bpMap.entrySet()) {
                for (BasePair bp : crossMap.getValue()) {
                    vienna[bp.res1.iRes] = leftBrackets.charAt(crossMap.getKey());
                    vienna[bp.res2.iRes] = rightBrackets.charAt(crossMap.getKey());
                }

            }
        }
        return vienna;
    }

    /**
     * Generates vienna (dot-bracket) sequence for RNA residues within molecule
     * to test functionality of SSGen class
     *
     * @param molecule
     * @return
     */
    public static char[] testViennaSequence(Molecule molecule) {
        Map<Integer, List<BasePair>> bpMap = getBasePairs(molecule);
        List<Residue> rnaResidues = genRnaResidues(molecule);
        char[] vienna = new char[rnaResidues.size()];
        String leftBrackets = "[{";
        String rightBrackets = "]}";
        for (int i = 0; i < vienna.length; i++) {
            vienna[i] = '.';
        }
        for (Residue residueA : rnaResidues) {
            if (residueA.pairedTo != null && residueA.iRes < residueA.pairedTo.iRes) {
                vienna[residueA.iRes] = '(';
                vienna[residueA.pairedTo.iRes] = ')';
            }
        }
        for (Map.Entry<Integer, List<BasePair>> crossMap : bpMap.entrySet()) {
            for (BasePair bp : crossMap.getValue()) {
                vienna[bp.res1.iRes] = leftBrackets.charAt(crossMap.getKey());
                vienna[bp.res2.iRes] = rightBrackets.charAt(crossMap.getKey());
            }
        }
        return vienna;
    }
}
