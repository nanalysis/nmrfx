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
package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.*;

/**
 * @author bajlabuser
 */
public class RNAAnalysis {

    private RNAAnalysis() {

    }

    /**
     * Generates a list of RNA residues in the given molecule
     *
     * @param molecule The molecule to get RNA residues from
     * @return list of RNA residues
     */
    public static List<Residue> getRNAResidues(Molecule molecule) { //list of only rna residues
        List<Residue> rnaResidues = new ArrayList<>();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isRNA()) {
                rnaResidues.addAll(polymer.getResidues());
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

    static int getResDistance(List<Residue> residues, Residue residueI, Residue residueJ) {
        int i = residues.indexOf(residueI);
        int j = residues.indexOf(residueJ);
        return j - i;
    }

    static boolean samePolymer(Residue residueI, Residue residueJ) {
        return residueI.polymer == residueJ.polymer;
    }

    static boolean pairingAllowed(List<Residue> residues, Residue residueI, Residue residueJ) {
        boolean samePolymer = samePolymer(residueI, residueJ);
        int distance = getResDistance(residues, residueI, residueJ);
        return (samePolymer && (distance > 3)) || (!samePolymer && (distance > 0));
    }

    /**
     * Generates a list of RNA basepairs for the given molecule
     *
     * @param molecule   The molecule to analyze for base pairing
     * @param typeTarget The type of basepair to return in list
     * @return A list of BasePairs found in the molecule
     */
    public static List<BasePair> getPairList(Molecule molecule, int typeTarget) { //for RNA only
        List<BasePair> bpList = new ArrayList<>();
        List<Residue> rnaResidues = getRNAResidues(molecule);
        int iResA = 0;
        for (Residue residueA : rnaResidues) {
            int iResB = 0;
            for (Residue residueB : rnaResidues) {
                if (pairingAllowed(rnaResidues, residueA, residueB)) {
                    int type = BasePair.getBasePairType(residueA, residueB);
                    if ((type == typeTarget) || ((typeTarget == -1) && (type > 0))) {
                        BasePair bp = new BasePair(residueA, iResA, residueB, iResB, type);
                        bpList.add(bp);
                    }
                }
                iResB++;
            }
            iResA++;
        }
        return bpList;
    }

    /**
     * Generates a map which determines which pairs overlap and create
     * pseudoknots
     *
     * @param molecule The molecule to analyze for base pairs
     * @return a map of overlapping base pairs
     */
    public static Map<Integer, List<BasePair>> getBasePairs(Molecule molecule) {
        Map<Integer, List<BasePair>> bpMap = new HashMap<>();
        BasePair currentBp = null;
        int i = 0;
        List<BasePair> crossedPairs = new ArrayList<>();
        List<BasePair> bpList = getPairList(molecule);
        for (BasePair bp1 : bpList) {
            for (BasePair bp2 : bpList) {
                if (bp1.getIResA() < bp2.getIResA() && bp1.getIResB() < bp2.getIResB() && bp1.getIResB() > bp2.getIResA()) {
                    if (currentBp != bp2) {
                        bpMap.put(i, crossedPairs);
                        i++;
                        crossedPairs.clear();
                        crossedPairs.add(bp1);
                        currentBp = bp2;
                        break;

                    } else {
                        crossedPairs.add(bp1);
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
     * @param molecule The molecule to get dot-bracket vienna sequence from
     * @return The dot-bracket sequence as an array of chars
     */
    public static char[] getViennaSequence(Molecule molecule) {
        Map<Integer, List<BasePair>> bpMap = getBasePairs(molecule);
        List<BasePair> bps = getPairList(molecule);
        List<Residue> rnaResidues = getRNAResidues(molecule);
        char[] vienna = new char[rnaResidues.size()];
        String leftBrackets = "[{ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String rightBrackets = "]}abcdefghijklmnopqrstuvwxyz";
        Arrays.fill(vienna, '.');
        for (BasePair bp : bps) {
            vienna[bp.getIResA()] = '(';
            vienna[bp.getIResB()] = ')';
        }
        if (!bpMap.isEmpty()) {
            for (Map.Entry<Integer, List<BasePair>> crossMap : bpMap.entrySet()) {
                for (BasePair bp : crossMap.getValue()) {
                    int key = crossMap.getKey();
                    if (key >= leftBrackets.length()) {
                        key = leftBrackets.length() - 1;
                    }
                    vienna[bp.getIResA()] = leftBrackets.charAt(key);
                    vienna[bp.getIResB()] = rightBrackets.charAt(key);
                }

            }
        }
        return vienna;
    }

    /**
     * Generates vienna (dot-bracket) sequence for RNA residues within molecule
     * to test functionality of SSGen class
     *
     * @param molecule The molecule to analyze
     * @return the dot-bracket sequence as an array of chars
     */
    public static char[] testViennaSequence(Molecule molecule) {
        Map<Integer, List<BasePair>> bpMap = getBasePairs(molecule);
        List<Residue> rnaResidues = getRNAResidues(molecule);
        char[] vienna = new char[rnaResidues.size()];
        String leftBrackets = "[{";
        String rightBrackets = "]}";
        Arrays.fill(vienna, '.');
        for (Residue residueA : rnaResidues) {
            if (residueA.pairedTo != null && residueA.iRes < residueA.pairedTo.iRes) {
                vienna[residueA.iRes] = '(';
                vienna[residueA.pairedTo.iRes] = ')';
            }
        }
        for (Map.Entry<Integer, List<BasePair>> crossMap : bpMap.entrySet()) {
            for (BasePair bp : crossMap.getValue()) {
                int key = crossMap.getKey();
                if (key >= leftBrackets.length()) {
                    key = leftBrackets.length() - 1;
                }
                vienna[bp.res1.iRes] = leftBrackets.charAt(key);
                vienna[bp.res2.iRes] = rightBrackets.charAt(key);
            }
        }
        return vienna;
    }
}
